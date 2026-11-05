package com.r0ybt.arachn0de.bank

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.*

internal data class BankState(
    val roots:List<BankRoot>,val accounts:List<BankAccount>,val operations:List<BankOperation>,val allocations:List<BankAllocation>,
    val projects:List<ProjectEntity>,val nodes:List<NodeEntity>,val people:List<PersonEntity>,val assignments:List<NodePersonEntity>,val occurrences:List<RecurrenceOccurrenceEntity>
) {
    private fun sum(values:List<Long>)=values.fold(0L,Math::addExact)
    fun balance(id:String):Long=operations.filter {!it.voided}.fold(java.math.BigInteger.ZERO) {total,o->
        val incoming=if(o.destinationAccountId==id)o.amount else 0L
        val outgoing=if(o.sourceAccountId==id)o.amount else 0L
        total.add(java.math.BigInteger.valueOf(incoming)).subtract(java.math.BigInteger.valueOf(outgoing))
    }.longValueExact()
    fun paid(id:String):Long {val valid=operations.filter {!it.voided}.map {it.id}.toSet();return sum(allocations.filter {it.nodeId==id && it.operationId in valid}.map {it.amount})}
    fun remaining(n:NodeEntity):Long=Math.subtractExact(requireNotNull(n.amountMinor),paid(n.id))
    fun owners(id:String)=assignments.filter {it.nodeId==id}.map {it.personId}
    fun payable(n:NodeEntity)=n.purpose=="ACTION" && n.workState==null && n.amountMinor!=null &&
        n.currencyCode=="CLP" && !n.isCompleted && nodes.none {it.parentId==n.id} && remaining(n)>0
    fun candidates(personId:String?):List<NodeEntity> = nodes.filter {payable(it) && (personId==null || personId in owners(it.id) || owners(it.id).isEmpty())}
        .sortedWith(compareBy<NodeEntity> {it.dueAt ?: Long.MAX_VALUE}.thenBy {it.id})
    // Kept for historical callers; new payments always use an explicit single-card allocation.
    fun suggest(personId:String,amount:Long):Map<String,Long> {
        require(amount>0);var left=amount
        return buildMap {candidates(personId).filter {owners(it.id).size==1}.forEach {n->val pay=minOf(left,remaining(n));if(pay>0) {put(n.id,pay);left-=pay}}}
    }
    fun eligible(n:NodeEntity,personId:String?)=payable(n) && owners(n.id).size<=1 && owners(n.id).singleOrNull()==personId
    fun history(rootId:String,accountId:String?):List<BankOperation> = operations.filter {it.rootId==rootId &&
        if(accountId==null) it.kind=="DIRECT" else it.kind!="DIRECT" && (it.sourceAccountId==accountId || it.destinationAccountId==accountId)}
    fun validate() {
        fun ids(values:List<String>) {require(values.all {it.isNotBlank() && it.length<=256} && values.toSet().size==values.size) {"Identidades de Banca inválidas."}}
        ids(roots.map {it.id});ids(accounts.map {it.id});ids(operations.map {it.id});ids(allocations.map {it.id})
        val rootIds=roots.map {it.id}.toSet();val accountIds=accounts.map {it.id}.toSet();val personIds=people.map {it.id}.toSet()
        require(rootIds.all {r->projects.any {it.id==r}})
        require(nodes.filter {it.projectId in rootIds}.all {n->accounts.any {it.id==n.id}}) {"Banca contiene un elemento que no es una cuenta."}
        accounts.forEach {a->require(a.rootId in rootIds && a.personId in personIds && nodes.any {it.id==a.id && it.purpose=="LAYER" && it.projectId==a.rootId && it.parentId==null});require(balance(a.id)>=0) {"Saldo negativo."}}
        operations.forEach {o->
            require(o.rootId in rootIds && (if(o.contextAccountId==null) o.kind=="DIRECT" else accounts.any {it.id==o.contextAccountId && it.rootId==o.rootId}))
            require(o.amount>0 && o.concept.isNotBlank() && o.concept.length<=1000 && o.createdAt>=0)
            java.time.LocalDate.ofEpochDay(o.day)
            require(o.kind in setOf("INCOME","EXPENSE","TRANSFER","PAYMENT","DIRECT"))
            require(o.sourceAccountId==null || o.sourceAccountId in accountIds)
            require(o.destinationAccountId==null || o.destinationAccountId in accountIds)
            when(o.kind) {
                "INCOME"->require(o.sourceAccountId==null && o.destinationAccountId!=null)
                "EXPENSE","PAYMENT"->require(o.sourceAccountId!=null && o.destinationAccountId==null)
                "TRANSFER"->require(o.sourceAccountId!=null && o.destinationAccountId!=null && o.sourceAccountId!=o.destinationAccountId)
                "DIRECT"->require(o.sourceAccountId==null && o.destinationAccountId==null)
            }
            require(o.sourceAccountId==null || accounts.any {it.id==o.sourceAccountId && it.rootId==o.rootId})
            require(o.destinationAccountId==null || accounts.any {it.id==o.destinationAccountId && it.rootId==o.rootId})
            val parts=allocations.filter {it.operationId==o.id}
            if(o.kind in setOf("PAYMENT","DIRECT")) {
                require(parts.isNotEmpty() && (o.kind=="DIRECT" || o.sourcePersonId!=null))
                require(sum(parts.map {it.amount})==o.amount && parts.map {it.nodeId}.distinct().size==parts.size)
                require(parts.all {it.personId==o.obligationPersonId && it.sourcePersonId==o.sourcePersonId})
            } else require(parts.isEmpty())
            o.correctsId?.let {old->require(old!=o.id && operations.any {it.id==old && it.voided && it.createdAt<=o.createdAt})}
        }
        val corrected=operations.mapNotNull {it.correctsId}
        require(corrected.distinct().size==corrected.size) {"Una operación no puede corregirse dos veces."}
        val operationIndex=operations.associateBy {it.id}
        val visited=hashSetOf<String>()
        operations.forEach {start->
            val path=hashSetOf<String>();var cursor:BankOperation?=start
            while(cursor!=null && cursor.id !in visited) {
                require(path.add(cursor.id)) {"Ciclo en el historial de correcciones."}
                cursor=cursor.correctsId?.let {operationIndex[it]}
            }
            visited.addAll(path)
        }
        allocations.forEach {a->
            require(a.amount>0 && operations.any {it.id==a.operationId} && nodes.any {it.id==a.nodeId})
            require((a.personId==null)==(a.personName==null) && (a.sourcePersonId==null)==(a.sourcePersonName==null))
            require(listOfNotNull(a.personId,a.personName,a.sourcePersonId,a.sourcePersonName).all {it.isNotBlank()} && a.title.isNotBlank())
            require(a.stateCount in 2..3 && (a.stateAt==null || a.stateAt>=0))
            require((a.ruleId==null)==(a.occurrenceDay==null))
            if(a.ruleId!=null) require(occurrences.any {it.nodeId==a.nodeId && it.ruleId==a.ruleId && it.day==a.occurrenceDay})
        }
        nodes.filter {paid(it.id)>0}.forEach {require(it.currencyCode=="CLP" && it.amountMinor!=null && remaining(it)>=0)}
    }
}
