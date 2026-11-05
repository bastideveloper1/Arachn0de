package com.r0ybt.arachn0de.domain.model

import java.util.TimeZone

/** Read-only scope of the existing calendar. No recursion or per-visible-day queries. */
internal class ContextCalendarIndex(val tree:NodeTreeSnapshot, zone:TimeZone, projectId:String?=null, parentId:String?=null) {
    val tasksByDay:Map<CalendarDay,List<Node>>
    private val scopedNodes:List<Node>
    init {
        val scope=if(parentId==null) tree.nodes.filter {it.projectId!=null && (projectId==null || it.projectId==projectId)}.mapTo(hashSetOf()) {it.id}
        else {
            val children=tree.nodes.groupBy {it.parentId}
            val ids=hashSetOf<String>();val queue=java.util.ArrayDeque<String>()
            if(projectId!=null && tree.nodesById[parentId]?.projectId==projectId) queue.add(parentId)
            while(queue.isNotEmpty()) {
                val id=queue.removeFirst()
                if(ids.add(id)) children[id].orEmpty().forEach {if(it.projectId==projectId) queue.add(it.id)}
            }
            ids
        }
        scopedNodes=tree.nodes.filter {it.id in scope}
        val groups=CalendarSnapshot(tree,zone).tasksByDay.mapValues {(_,nodes)->nodes.filter {it.id in scope}}.toMutableMap()
        // A task with only a scheduled start must not disappear. With a due date, retain
        // the existing Calendar semantics: one occurrence on its due day, never on creation.
        tree.nodes.filter {it.id in scope && it.isCompletable && it.dueAt==null && it.startAt!=null}.forEach {node->
            val day=CalendarDates.localDay(requireNotNull(node.startAt),zone)
            groups[day]=groups[day].orEmpty()+node
        }
        tasksByDay=groups.filterValues {it.isNotEmpty()}.mapValues {(_,nodes)->nodes.distinctBy {it.id}.sortedWith(compareBy<Node> {it.dueAt ?: it.startAt}.thenBy {it.id})}
    }
    fun results(day:CalendarDay?,attention:Boolean,favoritesOnly:Boolean,favorites:Set<String>,now:Long,zone:TimeZone,conditions:CardConditionFilter=CardConditionFilter()):List<Node> {
        val candidates=if(day!=null) tasksOn(day) else scopedNodes
        return candidates.filter {node->
            (!attention || AttentionPolicy.reasons(node,now,zone).isNotEmpty()) &&
                (!favoritesOnly || "node:${node.id}" in favorites) && conditions.matches(node,now,zone)
        }
    }
    fun tasksOn(day:CalendarDay)=tasksByDay[day].orEmpty()
    fun location(node:Node,projects:Map<String,Project>):String {
        val names=mutableListOf<String>();var cursor=node.parentId?.let(tree.nodesById::get)
        while(cursor!=null) {names.add(cursor.title);cursor=cursor.parentId?.let(tree.nodesById::get)}
        node.projectId?.let {projects[it]?.name}?.let(names::add)
        return names.asReversed().joinToString(" / ")
    }
}
