package com.r0ybt.arachn0de.slots

import com.r0ybt.arachn0de.bank.BankAccount
import com.r0ybt.arachn0de.bank.BankOperation

/** Historical voided operations are retained; runtime reconciliation then blocks play. */
internal fun validateSavingsLinks(state:SavingsState,accounts:List<BankAccount>,operations:List<BankOperation>) {
    val b=state.book()
    state.configs.forEach {c->require(accounts.any {it.id==c.account && it.rootId==c.root}) {"Falta una cuenta asociada a Ahorro."}}
    val byId=state.events.associateBy {it.id}
    state.events.filter {it.kind in setOf("BUY","TOPUP","WITHDRAW")}.forEach {e->
        val c=b.configs.getValue(e.config);val op=requireNotNull(operations.find {it.id==e.id}) {"Falta un movimiento de Ahorro."}
        val expense=e.kind=="WITHDRAW"
        require(op.rootId==c.root && op.contextAccountId==c.account && op.amount==savingsMoney(e.units) && op.correctsId==null)
        require(if(expense)op.kind=="EXPENSE" && op.sourceAccountId==c.account && op.destinationAccountId==null else op.kind=="INCOME" && op.destinationAccountId==c.account && op.sourceAccountId==null)
        val concept=if(expense)"Tragaperras — Premio ${savingsName(c.table,byId.getValue(requireNotNull(e.reference)).result.first())}" else savingsConcept(e)
        require(op.concept==concept) {"El movimiento no corresponde al registro de Ahorro."}
    }
}
