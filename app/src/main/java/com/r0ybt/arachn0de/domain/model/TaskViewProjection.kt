package com.r0ybt.arachn0de.domain.model

/** Read-only task view; callers supply IDs matching every non-status filter. */
data class TaskViewProjection(val rows:List<FilteredNodeRow>,val counts:Map<TaskStatus,Int>,val matchingIds:Set<String>,val containerCounts:Map<String,Int>,val verificationAvailable:Boolean) {
    val total get()=counts.values.sum()
    companion object {
        fun build(snapshot:NodeTreeSnapshot,project:Project,parent:String?,eligible:Set<String>,status:TaskStatus?):TaskViewProjection {
            val rows=ScopedNodeFilter.apply(snapshot,project.id,parent,NodeFilter(),emptyMap(),emptyMap())
            val tasks=rows.map {it.node}.filter {it.isCompletable}.distinctBy {it.id}
            val candidates=tasks.filter {it.id in eligible}
            val counts=TaskStatus.entries.associateWith {state->candidates.count {it.taskStatus==state}}
            val matching=candidates.filter {status==null || it.taskStatus==status}.mapTo(hashSetOf()) {it.id}
            val totals=mutableMapOf<String,Int>()
            // Scoped rows are preorder; aggregate each subtree once, then its outer ancestors.
            rows.asReversed().forEach {row->
                val amount=(totals[row.node.id] ?: 0)+(if(row.node.id in matching)1 else 0)
                if(amount>0)row.node.parentId?.let {totals[it]=(totals[it] ?: 0)+amount}
            }
            var ancestor=parent
            while(ancestor!=null) {
                val next=snapshot.nodesById[ancestor]?.parentId
                val amount=totals[ancestor] ?: 0
                if(next!=null && amount>0)totals[next]=(totals[next] ?: 0)+amount
                ancestor=next
            }
            val modes=mutableMapOf<String,Int>()
            val inherited=parent?.let {snapshot.nodesById[it]}?.let {TaskStatePolicy.count(it,snapshot.nodesById,project)} ?: if(project.verificationEnabled)3 else 2
            val verificationAvailable=rows.any {row->
                val node=row.node
                val mode=node.taskStateCount ?: modes[node.parentId] ?: inherited
                modes[node.id]=mode
                node.isCompletable && node.workState==null && mode==3
            }
            return TaskViewProjection(rows.filter {it.node.isStructural || it.node.hasChildren || it.node.id in matching || (status==null && it.node.id in eligible)}.map {it.copy(isMatch=it.node.id in matching)},counts,matching,totals,
                verificationAvailable)
        }
    }
}
