package com.r0ybt.arachn0de.slots

import com.r0ybt.arachn0de.bank.BankState
import java.math.BigInteger

internal data class SavingsConfig(val id:String,val root:String,val account:String,val table:SavingsPayTable=SavingsPayTable(),val savingsBps:Int=7000,val targetBps:Int=3000,val fromEvent:Int=0) {
    fun validate() {
        savingsUuid(id);require(root.isNotBlank() && root.length<=256 && account.isNotBlank() && account.length<=256)
        require(fromEvent>=0);table.validate();require(savingsBps in 0..9999 && targetBps in 1..(10000-savingsBps)) {"El RTP debe ser positivo y no superar el porcentaje destinado a premios."}
        require(table.rtp==SavingsFraction(targetBps.toLong(),10000)) {"El RTP calculado no coincide exactamente con el objetivo."}
    }
}
internal data class SavingsEvent(val id:String,val kind:String,val config:String,val at:Long,
    val units:Long=0,val count:Long=0,val result:List<Int> = emptyList(),val reference:String?=null)
internal data class SavingsState(val configs:List<SavingsConfig> = emptyList(),val events:List<SavingsEvent> = emptyList(),val animation:String?=null) {
    val active:SavingsConfig? get()=configs.lastOrNull()
    fun book():SavingsBook= SavingsBook(this)
}
internal fun savingsUuid(id:String) {require(java.util.UUID.fromString(id).toString()==id)}
internal fun savingsAdd(a:Long,b:Long)=Math.addExact(a,b)
internal fun savingsMoney(units:Long)=Math.multiplyExact(units,1000L)

/** Replayable logical book; physical cash changes only through linked Bank operations. */
internal class SavingsBook(val state:SavingsState) {
    val configs=state.configs.associateBy {it.id}
    val bought=linkedMapOf<String,Long>()
    val used=linkedMapOf<String,Long>()
    val pending=linkedMapOf<String,SavingsEvent>()
    private var allocationNumerator=BigInteger.ZERO
    private val purchases=java.util.ArrayDeque<SavingsEvent>()
    var reserved=0L;private set
    var protected=0L;private set
    var free=0L;private set
    var deposited=0L;private set
    var topups=0L;private set
    var assignedCapital=0L;private set
    var withdrawn=0L;private set
    var kept=0L;private set
    var purchased=0L;private set
    var spins=0L;private set
    var awarded=0L;private set
    var awardCount=0L;private set
    var withdrawnCount=0L;private set
    var keptCount=0L;private set
    var playedCostMinor=0L;private set
    val credits:Long get()=purchased-spins
    val funds:Long get()=savingsAdd(savingsAdd(protected,free),reserved)
    val nextPurchase:SavingsEvent? get()=purchases.peekFirst()
    val nextConfig:SavingsConfig? get()=nextPurchase?.let {configs.getValue(it.config)} ?: state.active
    val maximum:Long get()=nextConfig?.table?.maximumPrizeUnits ?: 0
    val deficit:Long get()=(maximum-free).coerceAtLeast(0)
    val canSpin:Boolean get()=state.animation==null && credits>0 && deficit==0L
    init {
        require(state.configs.size<=10000 && state.events.size<=100000)
        require(configs.size==state.configs.size);state.configs.forEach {it.validate()}
        require(state.events.map {it.id}.toSet().size==state.events.size)
        val resolved=hashSetOf<String>()
        require(state.configs.zipWithNext().all {(a,b)->a.fromEvent<=b.fromEvent} && state.configs.all {it.fromEvent<=state.events.size})
        var configIndex=0;var live:SavingsConfig?=null
        fun activate(position:Int) {
            while(configIndex<state.configs.size && state.configs[configIndex].fromEvent<=position) {
                val next=state.configs[configIndex++]
                if(live!=null && (funds>0 || credits>0))require(live!!.account==next.account && live!!.root==next.root) {"No se puede cambiar de cuenta con fondos o créditos."}
                live=next
            }
        }
        state.events.forEachIndexed {position,e->
            activate(position)
            savingsUuid(e.id);require(e.at>=0);val c=requireNotNull(configs[e.config])
            require(e.units>=0 && e.count>=0 && e.result.all {it in 0 until c.table.symbolCount});e.reference?.let(::savingsUuid)
            when(e.kind) {
                "BUY"->{
                    require(live?.id==e.config)
                    require(e.count>0 && e.reference==null && e.result.isEmpty() && e.units==c.table.purchaseUnits(e.count))
                    val previous=allocationNumerator/BigInteger.valueOf(10000)
                    allocationNumerator+=BigInteger.valueOf(e.units)*BigInteger.valueOf(c.savingsBps.toLong())
                    val delta=(allocationNumerator/BigInteger.valueOf(10000)-previous).longValueExact()
                    protected=savingsAdd(protected,delta);free=savingsAdd(free,e.units-delta)
                    deposited=savingsAdd(deposited,e.units);purchased=savingsAdd(purchased,e.count);bought[e.id]=e.count;purchases.add(e)
                }
                "ASSIGN"->{require(live?.id==e.config);require(e.units>0 && e.count==0L && e.result.isEmpty() && e.reference==null);free=savingsAdd(free,e.units);assignedCapital=savingsAdd(assignedCapital,e.units)}
                "TOPUP"->{require(live?.id==e.config);require(e.units>0 && e.count==0L && e.result.isEmpty() && e.reference==null);free=savingsAdd(free,e.units);topups=savingsAdd(topups,e.units)}
                "SPIN"->{
                    val buy=requireNotNull(nextPurchase);require(e.reference==buy.id && e.config==buy.config && e.count==0L)
                    require(e.result.size==3 && e.units==c.table.prize(e.result) && free>=c.table.maximumPrizeUnits)
                    used[buy.id]=savingsAdd(used[buy.id] ?: 0,1);if(used[buy.id]==buy.count)purchases.removeFirst();spins=savingsAdd(spins,1);playedCostMinor=savingsAdd(playedCostMinor,c.table.costPerSpinMinor)
                    free-=e.units;awarded=savingsAdd(awarded,e.units)
                    if(e.units>0) {pending[e.id]=e;reserved=savingsAdd(reserved,e.units);awardCount=savingsAdd(awardCount,1)}
                }
                "WITHDRAW","KEEP"->{
                    val prize=requireNotNull(pending.remove(e.reference));reserved-=prize.units;require(resolved.add(prize.id) && e.units==prize.units && e.config==prize.config && e.count==0L && e.result.isEmpty())
                    if(e.kind=="WITHDRAW") {withdrawn=savingsAdd(withdrawn,e.units);withdrawnCount++}
                    else {protected=savingsAdd(protected,e.units);kept=savingsAdd(kept,e.units);keptCount++}
                }
                else->error("Registro Ahorro desconocido.")
            }
            require(protected>=0 && free>=0 && credits>=0)
            require(funds==savingsAdd(savingsAdd(deposited,topups),assignedCapital)-withdrawn) {"Reservas de Ahorro inconsistentes."}
            savingsMoney(funds) // Same integer CLP range as Bank.
        }
        activate(state.events.size)
        state.animation?.let {id->require(state.events.lastOrNull()?.let {it.id==id && it.kind=="SPIN"}==true)}
    }
    /** Broken or voided links freeze play, but remain valid historical data for backup. */
    fun reconciliation(bank:BankState):String? {
        for(e in state.events.filter {it.kind in setOf("BUY","TOPUP","WITHDRAW")}) {
            val c=configs.getValue(e.config);val op=bank.operations.find {it.id==e.id}
            if(op==null || op.voided || op.rootId!=c.root || op.contextAccountId!=c.account || op.amount!=savingsMoney(e.units) ||
                (if(e.kind=="WITHDRAW")op.kind!="EXPENSE" || op.sourceAccountId!=c.account else op.kind!="INCOME" || op.destinationAccountId!=c.account))
                return "Hay movimientos de Ahorro anulados, corregidos o ausentes. Se requiere conciliación con Banco."
        }
        val current=state.active ?: return null
        if(bank.accounts.none {it.id==current.account && it.rootId==current.root})return "La cuenta asociada ya no está disponible."
        if(bank.balance(current.account)<savingsMoney(funds))return "El saldo de la cuenta no respalda todos los fondos de la máquina. Revisa los movimientos externos en Banco."
        return null
    }
}
