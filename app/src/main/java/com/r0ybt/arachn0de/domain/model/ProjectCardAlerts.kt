package com.r0ybt.arachn0de.domain.model

import java.util.TimeZone

/** Independent counts for the dashboard, derived from confirmed tasks; never persisted. */
data class ProjectCardAlerts(val highPriority: Int = 0, val dueToday: Int = 0, val futureDue:Int = 0)

/** One pass across all depths, using project identity instead of walking each project's tree. */
fun projectCardAlerts(tree: NodeTreeSnapshot, today: CalendarDay, zone: TimeZone): Map<String, ProjectCardAlerts> {
    val day = TemporalRanges.day(today, zone)
    val result = mutableMapOf<String, ProjectCardAlerts>()
    tree.nodes.forEach { node ->
        if (!node.isCompletable || node.isCompleted) return@forEach
        val high = node.effectivePriority == Priority.HIGH
        val due = node.dueAt?.let { it in day } == true
        val future=node.dueAt?.let {it>=day.endExclusive} == true
        if (high || due || future) {
            val previous = result[node.projectId] ?: ProjectCardAlerts()
            result[node.projectId] = ProjectCardAlerts(previous.highPriority + if (high) 1 else 0,
                previous.dueToday + (if (due) 1 else 0), previous.futureDue + (if(future) 1 else 0))
        }
    }
    return result
}
