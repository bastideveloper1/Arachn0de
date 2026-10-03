package com.r0ybt.arachn0de.domain.model

enum class TaskTemporalState { SCHEDULED, ACTIVE, UPCOMING, OVERDUE, COMPLETED }

/** Pure instant-based policy. Containers retain dates but have no operational temporal state. */
object TaskTemporal {
    const val UPCOMING_WINDOW_MILLIS = 24L * 60 * 60 * 1000

    fun validateDates(startAt: Long?, dueAt: Long?) {
        require(startAt == null || dueAt == null || dueAt >= startAt) { "Due date precedes start" }
    }

    fun state(node: Node, now: Long): TaskTemporalState? {
        if (node.hasChildren) return null
        if (node.isCompleted) return TaskTemporalState.COMPLETED
        if (node.startAt == null && node.dueAt == null) return null
        if (node.startAt != null && now < node.startAt) return TaskTemporalState.SCHEDULED
        if (node.dueAt != null) {
            if (now > node.dueAt) return TaskTemporalState.OVERDUE
            val windowEnd = if (now > Long.MAX_VALUE - UPCOMING_WINDOW_MILLIS) Long.MAX_VALUE else now + UPCOMING_WINDOW_MILLIS
            if (node.dueAt <= windowEnd) return TaskTemporalState.UPCOMING
        }
        return TaskTemporalState.ACTIVE
    }

    /** Earliest strictly future change; dueAt itself is still upcoming, dueAt + 1 is overdue. */
    fun nextTransition(node: Node, now: Long): Long? {
        if (node.hasChildren || node.isCompleted) return null
        if (node.startAt != null && node.startAt > now) return node.startAt
        return buildList {
            node.dueAt?.let {
                val near = if (it < Long.MIN_VALUE + UPCOMING_WINDOW_MILLIS) Long.MIN_VALUE else it - UPCOMING_WINDOW_MILLIS
                if (near > now) add(near)
                if (it < Long.MAX_VALUE && it + 1 > now) add(it + 1)
            }
        }.minOrNull()
    }
}
