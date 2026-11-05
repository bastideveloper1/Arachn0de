package com.r0ybt.arachn0de.slots

import androidx.room.withTransaction
import com.r0ybt.arachn0de.bank.*
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

internal val legacySavingsSymbolNames=listOf("BAR","Diamante","Estrella","Campana","Limón","Cereza","Siete")
internal val savingsSymbolNames=listOf("Cerezas","Plátanos","Guacamayo","Lingotes de plata","Lingotes de oro","Rubí rojo","Esmeralda verde","Máscara dorada")
internal fun savingsName(table:SavingsPayTable,index:Int)=(if(table.symbolCount==7)legacySavingsSymbolNames else savingsSymbolNames)[index]
internal fun savingsConcept(e:SavingsEvent)=when(e.kind) {
    "BUY"->"Tragaperras — Compra de tiradas"
    "TOPUP"->"Tragaperras — Recarga de reserva"
    "WITHDRAW"->"Tragaperras — Premio ${savingsSymbolNames[e.result.firstOrNull() ?: 0]}"
    else->error("No es un movimiento físico.")
}
internal data class SavingsView(val state:SavingsState,val bank:BankState)

internal class SavingsRepository(private val db:Arachn0deDatabase,private val now:()->Long=System::currentTimeMillis,
    private val draw:(SavingsPayTable)->List<Int> = {it.draw()}) {
    private val bank=BankRepository(db)
    /** The selected account is the cash box. Adopt its unassigned whole-CLP-1000 balance,
     * never a second Bank income. Existing protected funds and prizes remain excluded. */
    suspend fun read():SavingsState=db.withTransaction {
        val s=db.privatePreferenceDao().get(SavingsCodec.NAME)?.payload?.let(SavingsCodec::decode) ?: SavingsState()
        val c=s.active ?: return@withTransaction s
        if(s.animation!=null)return@withTransaction s
        val b=s.book();val bs=bank.state()
        if(b.reconciliation(bs)!=null)return@withTransaction s
        val extra=(bs.balance(c.account)/1000-b.funds).coerceAtLeast(0)
        if(extra==0L)return@withTransaction s
        val adopted=s.copy(events=s.events+SavingsEvent(java.util.UUID.randomUUID().toString(),"ASSIGN",c.id,now(),extra))
        put(adopted);adopted
    }
    fun observe()=db.invalidationTracker.createFlow("private_preferences","bank_operations","bank_accounts","bank_roots","nodes","projects","persons").map {db.withTransaction {SavingsView(read(),bank.state())}}.distinctUntilChanged()
    suspend fun bankState()=bank.state()
    private suspend fun put(s:SavingsState,growing:Boolean=true) {
        val book=s.book();val payload=SavingsCodec.encode(s)
        require(payload.toByteArray().size.toLong()+(if(growing)book.pending.size*1024L+2048 else 0)<=SavingsCodec.MAX_BYTES) {"El historial alcanzó el límite de respaldo. Se conservan los datos y los premios pendientes."}
        db.privatePreferenceDao().save(PrivatePreferenceEntity(SavingsCodec.NAME,payload))
    }
    suspend fun configure(config:SavingsConfig)=db.withTransaction {
        config.validate();val s=read();val b=s.book();require(s.animation==null) {"Espera a que termine la tirada."}
        s.configs.find {it.id==config.id}?.let {require(it.copy(fromEvent=0)==config.copy(fromEvent=0));return@withTransaction}
        val bs=bank.state();require(bs.accounts.any {it.id==config.account && it.rootId==config.root}) {"Selecciona Banco y cuenta."}
        if(b.funds>0 || b.credits>0)require(s.active?.account==config.account && s.active?.root==config.root) {"Resuelve créditos, premios y reservas antes de cambiar de cuenta."}
        // Unused configuration versions have no financial history to retain.
        val used=s.events.mapTo(hashSetOf()) {it.config}
        put(s.copy(configs=s.configs.filter {it.id in used}+config.copy(fromEvent=s.events.size)))
        read() // Make the existing account balance available at association, atomically.
    }
    suspend fun buy(id:String,count:Long,configId:String)=db.withTransaction {
        val s=read();s.events.find {it.id==id}?.let {require(it.kind=="BUY" && it.count==count && it.config==configId);return@withTransaction}
        require(s.animation==null);require(count>0)
        val c=requireNotNull(s.active);require(c.id==configId) {"La configuración cambió; revisa la compra."}
        require(s.book().reconciliation(bank.state())==null) {"Se requiere conciliación con Banco antes de comprar."}
        val e=SavingsEvent(id,"BUY",c.id,now(),c.table.purchaseUnits(count),count)
        physical(s,e,c);put(s.copy(events=s.events+e))
    }
    /** One explicit deposit: inspect existing history, create only a future pack configuration
     * if necessary, and register cash + credits atomically. Never rewrite an older purchase. */
    suspend fun depositAndBuy(id:String,amountClp:Long,root:String,account:String)=db.withTransaction {
        val s=read()
        s.events.find {it.id==id}?.let {e->
            val c=s.configs.single {it.id==e.config}
            require(e.kind=="BUY" && savingsMoney(e.units)==amountClp && c.root==root && c.account==account)
            return@withTransaction
        }
        val c=s.active
        val selected=if(c!=null && c.root==root && c.account==account && c.table.creditsPerPack==5 && c.table.priceUnits==1L) {
            require(c.targetBps==3000) {"Revisa la configuración: el RTP debe ser del 30 %."};c
        } else {
            SavingsConfig(java.util.UUID.randomUUID().toString(),root,account,savingsBps=c?.savingsBps ?: 7000).also {configure(it)}
        }
        buyAmount(id,amountClp,selected.id)
    }
    suspend fun buyAmount(id:String,amountClp:Long,configId:String)=db.withTransaction {
        val c=requireNotNull(read().active);require(c.id==configId) {"La configuración cambió; revisa el depósito."}
        val count=savingsCreditsForDeposit(amountClp,c.table)
        buy(id,count,configId)
    }
    suspend fun topup(id:String,units:Long,configId:String)=db.withTransaction {
        val s=read();s.events.find {it.id==id}?.let {require(it.kind=="TOPUP" && it.units==units && it.config==configId);return@withTransaction}
        require(s.animation==null && units>0);val c=requireNotNull(s.active);require(c.id==configId)
        require(s.book().reconciliation(bank.state())==null) {"Revisa la conciliación en Banco antes de recargar."}
        val e=SavingsEvent(id,"TOPUP",c.id,now(),units);physical(s,e,c);put(s.copy(events=s.events+e))
    }
    suspend fun spin(id:String):SavingsEvent=db.withTransaction {
        val s=read();s.events.find {it.id==id}?.let {require(it.kind=="SPIN");return@withTransaction it}
        s.animation?.let {pending->return@withTransaction s.events.single {it.id==pending}}
        val b=s.book();require(b.canSpin) {if(b.credits==0L)"Compra tiradas primero." else "Fondo insuficiente: ingresa ${savingsMoney(b.deficit)} CLP."}
        require(b.reconciliation(bank.state())==null) {"Se requiere conciliación con Banco. No se ha consumido crédito."}
        val buy=requireNotNull(b.nextPurchase);val c=b.configs.getValue(buy.config)
        val result=draw(c.table).toList();require(result.size==3 && result.all {it in 0 until c.table.symbolCount})
        val e=SavingsEvent(id,"SPIN",c.id,now(),c.table.prize(result),result=result,reference=buy.id)
        put(s.copy(events=s.events+e,animation=e.id));e
    }
    suspend fun acknowledge(id:String)=db.withTransaction {
        val s=read();if(s.animation==id)put(s.copy(animation=null),false)
    }
    suspend fun resolve(id:String,prize:String,withdraw:Boolean)=db.withTransaction {
        val s=read();val kind=if(withdraw)"WITHDRAW" else "KEEP"
        s.events.find {it.id==id}?.let {require(it.kind==kind && it.reference==prize);return@withTransaction}
        require(s.animation==null);val b=s.book();val win=requireNotNull(b.pending[prize]) {"El premio ya fue resuelto."}
        val c=b.configs.getValue(win.config)
        val e=SavingsEvent(id,kind,c.id,now(),win.units,reference=prize)
        require(b.reconciliation(bank.state())==null) {"Revisa la conciliación antes de resolver el premio."}
        if(withdraw)physical(s,e,c,win)
        put(s.copy(events=s.events+e),false)
    }
    private suspend fun physical(s:SavingsState,e:SavingsEvent,c:SavingsConfig,win:SavingsEvent?=null) {
        val expense=e.kind=="WITHDRAW"
        val concept=if(expense)"Tragaperras — Premio ${savingsName(c.table,requireNotNull(win).result.first())}" else savingsConcept(e)
        val r=BankRequest(e.id,c.root,c.account,if(expense)"EXPENSE" else "INCOME",savingsMoney(e.units),LocalDate.now().toEpochDay(),concept,
            sourceAccountId=c.account.takeIf {expense},destinationAccountId=c.account.takeUnless {expense})
        bank.confirm(bank.preview(r))
    }
}

internal fun savingsCreditsForDeposit(amountClp:Long,table:SavingsPayTable):Long {
    val price=savingsMoney(table.priceUnits)
    require(amountClp>0 && amountClp%price==0L) {"Introduce un múltiplo de ${savingsCash(table.priceUnits)}."}
    return Math.multiplyExact(amountClp/price,table.creditsPerPack.toLong())
}
