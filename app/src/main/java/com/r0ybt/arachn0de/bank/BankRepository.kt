package com.r0ybt.arachn0de.bank

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import java.util.UUID

internal data class BankRequest(val id:String=UUID.randomUUID().toString(),val rootId:String,val contextAccountId:String?,
    val kind:String,val amount:Long,val day:Long,val concept:String,val sourceAccountId:String?=null,val destinationAccountId:String?=null,
    val sourcePersonId:String?=null,val personId:String?=null,val correctsId:String?=null,val createRoot:Boolean=false)
internal data class BankPreview(val request:BankRequest,val original:BankState,val effective:BankState,val suggested:Map<String,Long>)

internal class BankRepository(private val db:Arachn0deDatabase,private val now:()->Long=System::currentTimeMillis,private val nodeRepository:NodeRepository=NodeRepository(db,now)) {
    suspend fun previewAccountDeletion(id:String)=BankAccountDeletionService(db,nodeRepository).preview(id)
    suspend fun deleteAccount(confirmation:BankAccountDeletion)=BankAccountDeletionService(db,nodeRepository).confirm(confirmation)
    suspend fun state():BankState=db.withTransaction {
        BankState(db.bankDao().roots(),db.bankDao().accounts(),db.bankDao().operations(),db.bankDao().allocations(),db.backupDao().projects(),db.backupDao().nodes(),db.backupDao().persons(),db.backupDao().assignments(),db.recurrenceDao().occurrences())
    }
    suspend fun createRoot(name:String,description:String,id:String)=db.withTransaction {
        ProjectRepository(db.projectDao(),db).createProject(name,description,id)
        if(db.bankDao().roots().none {it.id==id})db.bankDao().insertRoots(listOf(BankRoot(id)))
    }
    suspend fun createAccount(rootId:String,title:String,personId:String)=db.withTransaction {
        require(db.bankDao().roots().any {it.id==rootId});require(db.personDao().get(personId)!=null) {"Selecciona un responsable."}
        val id=UUID.randomUUID().toString()
        db.bankDao().insertAccounts(listOf(BankAccount(id,rootId,personId)))
        nodeRepository.createNode(rootId,null,title,"",creationId=id,purpose=NodePurpose.LAYER,responsibleIds=setOf(personId))
    }
    internal fun effectiveState(s:BankState,correctsId:String?):BankState = correctsId?.let {reversed(s,it)} ?: s
    private fun reversed(s:BankState,id:String):BankState {
        val operation=requireNotNull(s.operations.find {it.id==id});require(!operation.voided) {"La operación ya está anulada."}
        val ops=s.operations.map {if(it.id==id)it.copy(voided=true) else it}
        val candidate=s.copy(operations=ops)
        require(s.accounts.all {candidate.balance(it.id)>=0}) {"Anular produciría saldo negativo; corrige primero los movimientos posteriores."}
        require(s.nodes.none {n->n.isCompleted && n.amountMinor!=null && candidate.remaining(n)>s.remaining(n)}) {
            "La anulación dejaría deuda en una tarjeta Completada. Cambia primero su estado explícitamente."
        }
        return candidate
    }

    private suspend fun rejectSavingsChange(id:String) {
        val operation=db.bankDao().operations().find {it.id==id} ?: return
        if(!operation.concept.startsWith("Tragaperras —"))return
        val payload=db.privatePreferenceDao().get(com.r0ybt.arachn0de.slots.SavingsCodec.NAME)?.payload ?: return
        val state=com.r0ybt.arachn0de.slots.SavingsCodec.decode(payload)
        require(state.events.none {it.id==id && it.kind in setOf("BUY","TOPUP","WITHDRAW")}) {"Este movimiento respalda créditos o reservas de Ahorro y no puede anularse ni corregirse aisladamente desde Banco."}
    }
    suspend fun preview(request:BankRequest):BankPreview=db.withTransaction {
        request.correctsId?.let {rejectSavingsChange(it)}
        val normalized=request.copy(concept=request.concept.trim().ifEmpty {defaultBankName(request.kind)})
        val original=state()
        require(!request.createRoot || (original.roots.isEmpty() && request.kind=="DIRECT" && request.contextAccountId==null && request.correctsId==null))
        val effective=effectiveState(original,normalized.correctsId).let {s->
            if(!request.createRoot)s else s.copy(roots=listOf(BankRoot(request.rootId)),
                projects=s.projects+ProjectEntity(request.rootId,"Banco","",0,now(),now()))
        }
        require(original.operations.none {it.id==request.id}) {"La operación ya fue registrada."}
        checkRequest(effective,normalized)
        BankPreview(normalized,original,effective,emptyMap())
    }
    private fun checkRequest(s:BankState,r:BankRequest) {
        require(r.amount>0 && r.concept.isNotBlank() && r.concept.length<=1000) {"Introduce una cantidad positiva y un nombre de hasta 1000 caracteres."}
        java.time.LocalDate.ofEpochDay(r.day)
        require(s.roots.any {it.id==r.rootId} && (if(r.contextAccountId==null) r.kind=="DIRECT" else s.accounts.any {it.id==r.contextAccountId && it.rootId==r.rootId}))
        require(r.kind in setOf("INCOME","EXPENSE","TRANSFER","PAYMENT","DIRECT"))
        fun account(id:String?)=requireNotNull(s.accounts.find {it.id==id && it.rootId==r.rootId}) {"Cuenta inválida."}
        if(r.kind in setOf("EXPENSE","TRANSFER","PAYMENT")) {
            account(r.sourceAccountId);require(s.balance(requireNotNull(r.sourceAccountId))>=r.amount) {"Saldo insuficiente. Puedes elegir Pago directo si el dinero no está en una cuenta."}
        }
        if(r.kind in setOf("INCOME","TRANSFER")) {
            account(r.destinationAccountId)
            require(r.amount<=Long.MAX_VALUE-s.balance(requireNotNull(r.destinationAccountId))) {"La cantidad supera el límite de la cuenta."}
        }
        if(r.kind=="DIRECT")require(r.sourceAccountId==null && r.destinationAccountId==null)
        val corrected=r.correctsId?.let {id->requireNotNull(s.operations.find {it.id==id})}
        if(corrected!=null) require(corrected.kind==r.kind && corrected.rootId==r.rootId && corrected.contextAccountId==r.contextAccountId) {
            "La corrección debe conservar el tipo y contexto históricos."
        }
        if(r.kind=="PAYMENT")require(r.destinationAccountId==null && (r.contextAccountId==r.sourceAccountId ||
            corrected?.let {it.contextAccountId==r.contextAccountId && it.sourceAccountId==r.sourceAccountId}==true))
        if(r.kind=="TRANSFER")require(r.sourceAccountId!=r.destinationAccountId) {"Elige cuentas diferentes."}
        if(r.kind in setOf("PAYMENT","DIRECT")) {
            require(r.personId==null || s.people.any {it.id==r.personId}) {"Selecciona quién paga."}
            require(r.sourcePersonId==null || s.people.any {it.id==r.sourcePersonId}) {"Selecciona quién paga."}
        }
    }
    suspend fun confirm(preview:BankPreview,distribution:Map<String,Long> = preview.suggested):String=db.withTransaction {
        val r=preview.request;val current=state()
        r.correctsId?.let {rejectSavingsChange(it)}
        // A repeated tap with the exact same confirmed preview is an idempotent success.
        current.operations.find {it.id==r.id}?.let {existing->
            require(existing.amount==r.amount) {"La confirmación no coincide con el importe registrado."}
            require(existing.rootId==r.rootId && existing.contextAccountId==r.contextAccountId && existing.kind==r.kind && existing.day==r.day && existing.amount==(if(r.kind in setOf("PAYMENT","DIRECT"))distribution.values.fold(0L,Math::addExact) else r.amount) && existing.sourceAccountId==r.sourceAccountId && existing.destinationAccountId==r.destinationAccountId && existing.obligationPersonId==r.personId && (r.kind!="DIRECT" || existing.sourcePersonId==r.sourcePersonId) && existing.correctsId==r.correctsId && existing.concept==r.concept.trim() && current.allocations.filter {it.operationId==r.id}.associate {it.nodeId to it.amount}==distribution.filterValues {it>0})
            return@withTransaction existing.id
        }
        require(current==preview.original) {"Los datos cambiaron. Revisa el pago y vuelve a confirmar."}
        val s=preview.effective;checkRequest(s,r)
        val parts=distribution.filterValues {it>0}
        require(distribution.values.all {it>=0}) {"No se admiten asignaciones negativas."}
        val payment=r.kind in setOf("PAYMENT","DIRECT")
        val total=if(payment)parts.values.fold(0L,Math::addExact) else r.amount
        require(total>0 && total<=r.amount) {"Revisa el total distribuido."}
        val oldParts=r.correctsId?.let {old->current.allocations.filter {it.operationId==old}}
        val correctingLegacySplit=oldParts!=null && oldParts.size>1 && parts.keys==oldParts.map {it.nodeId}.toSet()
        if(!payment)require(parts.isEmpty()) else require((parts.size==1 || correctingLegacySplit) && total==r.amount) {"Sélecciona una sola tarjeta y un importe que no exceda su pendiente."}
        parts.forEach {(id,amount)->val n=requireNotNull(s.nodes.find {it.id==id});require(s.eligible(n,r.personId) && amount<=s.remaining(n)) {"Pago no disponible o cantidad superior al pendiente."}}
        if(r.createRoot)createRoot("Banco","",r.rootId)
        if(r.correctsId!=null)voidInternal(current,r.correctsId)
        val source=if(r.kind=="DIRECT")r.sourcePersonId else r.sourceAccountId?.let {id->s.accounts.single {it.id==id}.personId}
        val at=maxOf(now(),(s.nodes.maxOfOrNull {it.updatedAt} ?: 0)+1)
        val op=BankOperation(r.id,r.rootId,r.contextAccountId,r.kind,total,r.day,r.concept.trim(),r.sourceAccountId,r.destinationAccountId,source,r.personId,r.correctsId,false,at)
        db.bankDao().insertOperations(listOf(op))
        val repo=nodeRepository
        val allocations=parts.map {(id,amount)->
            val n=s.nodes.single {it.id==id};val count=repo.taskStateCount(id)
            val occurrence=s.occurrences.find {it.nodeId==id}
            BankAllocation(UUID.randomUUID().toString(),r.id,id,amount,r.personId,r.personId?.let {id->s.people.single {it.id==id}.name},
                source,source?.let {id->s.people.single {it.id==id}.name},n.title,occurrence?.ruleId,occurrence?.day,null,count)
        }
        db.bankDao().insertAllocations(allocations)
        // Complete only after the payment exists, in the same transaction as money and allocations.
        parts.forEach {(id,amount)->
            val n=s.nodes.single {it.id==id}
            if(amount==s.remaining(n)) {
                check(repo.setTaskStatus(id,TaskStatus.COMPLETED))
                // The existing state trigger clears ownership markers. Preserve historical allocations
                // verbatim when this new payment, rather than a manual state edit, completes the task.
                s.allocations.filter {it.nodeId==id && it.stateAt!=null}.forEach {old->
                    db.openHelper.writableDatabase.execSQL("UPDATE bank_allocations SET stateAt=? WHERE id=?",arrayOf<Any?>(old.stateAt,old.id))
                }
            }
        }
        state().validate()
        r.id
    }
    /** New single-person panel: exact allocation, revalidated inside the existing transaction. */
    suspend fun confirmPayment(preview:BankPreview,distribution:Map<String,Long>):String=db.withTransaction {
        val r=preview.request
        require(r.kind in setOf("PAYMENT","DIRECT"))
        require(distribution.values.all {it>=0} && distribution.values.fold(0L,Math::addExact)==r.amount) {"Distribuye exactamente la cantidad del pago."}
        confirm(preview,distribution)
    }
    private suspend fun voidInternal(s:BankState,id:String) {
        reversed(s,id)
        db.bankDao().annul(id)
    }
    suspend fun annul(id:String)=db.withTransaction {rejectSavingsChange(id);voidInternal(state(),id);state().validate()}
}
