package com.r0ybt.arachn0de.bank

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.domain.model.TaskStatus
import com.r0ybt.arachn0de.slots.*

/** A volatile confirmation snapshot: stale confirmations never delete newer financial data. */
internal data class BankAccountDeletion(val accountId:String,val original:BankState,val savingsPayload:String?,val operationIds:Set<String>,val removesSavings:Boolean)

internal class BankAccountDeletionService(private val db:Arachn0deDatabase,private val nodes:NodeRepository) {
    suspend fun preview(id:String):BankAccountDeletion=db.withTransaction {
        val s=BankRepository(db).state();require(s.accounts.any {it.id==id}) {"La cuenta ya no existe."}
        val ids=s.operations.filter {it.contextAccountId==id || it.sourceAccountId==id || it.destinationAccountId==id}.mapTo(hashSetOf()) {it.id}
        // Corrections form a single history; do not leave dangling correction references.
        var changed=true
        while(changed) {changed=false;s.operations.forEach {o->if(o.id in ids || o.correctsId in ids) {if(ids.add(o.id))changed=true;o.correctsId?.let {if(ids.add(it))changed=true}}}}
        val payload=db.privatePreferenceDao().get(SavingsCodec.NAME)?.payload
        val savings=payload?.let(SavingsCodec::decode)
        val linked=savings?.configs?.any {it.account==id}==true
        if(linked)require(savings!!.configs.all {it.account==id}) {"Ahorro conserva historial de varias cuentas. No se puede eliminar esta cuenta sin separar primero ese historial."}
        val remaining=s.copy(accounts=s.accounts.filterNot {it.id==id},nodes=s.nodes.filterNot {it.id==id},operations=s.operations.filterNot {it.id in ids},allocations=s.allocations.filterNot {it.operationId in ids})
        require(remaining.accounts.all {remaining.balance(it.id)>=0}) {"Eliminar la cuenta dejaría otra cuenta con saldo negativo por transferencias históricas. Revisa esos movimientos primero."}
        require(remaining.nodes.none {n->n.isCompleted && n.amountMinor!=null && remaining.remaining(n)>s.remaining(n)}) {
            "Eliminar la cuenta dejaría deuda en una tarjeta Completada. Cambia primero su estado explícitamente."
        }
        remaining.validate()
        BankAccountDeletion(id,s,payload,ids,linked)
    }
    suspend fun confirm(expected:BankAccountDeletion)=db.withTransaction {
        val fresh=preview(expected.accountId)
        require(fresh==expected) {"Los datos cambiaron. Vuelve a revisar la eliminación de la cuenta."}
        val sql=db.openHelper.writableDatabase
        val s=fresh.original
        fresh.operationIds.forEach {sql.execSQL("DELETE FROM bank_allocations WHERE operationId=?",arrayOf(it))}
        // Self-referencing corrections are deferred; account and allocation references are not.
        fresh.operationIds.forEach {sql.execSQL("DELETE FROM bank_operations WHERE id=?",arrayOf(it))}
        if(fresh.removesSavings)db.privatePreferenceDao().delete(SavingsCodec.NAME)
        sql.execSQL("DELETE FROM bank_accounts WHERE id=?",arrayOf(fresh.accountId))
        check(nodes.deleteNode(fresh.accountId))
        BankRepository(db).state().validate()
        db.privatePreferenceDao().get(SavingsCodec.NAME)?.payload?.let {validateSavingsLinks(SavingsCodec.decode(it),db.bankDao().accounts(),db.bankDao().operations())}
    }
}
