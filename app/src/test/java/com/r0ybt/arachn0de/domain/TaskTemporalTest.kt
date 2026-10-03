package com.r0ybt.arachn0de.domain

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class TaskTemporalTest {
    private val task = Node("n", "p", null, "Task", "", false, 0, 0, 0, false)
    private val day = TaskTemporal.UPCOMING_WINDOW_MILLIS

    @Test fun exactStartDueAndProximityBoundaries() {
        val node = task.copy(startAt = day * 2, dueAt = day * 4)
        assertEquals(TaskTemporalState.SCHEDULED, TaskTemporal.state(node, day * 2 - 1))
        assertEquals(TaskTemporalState.ACTIVE, TaskTemporal.state(node, day * 2))
        assertEquals(TaskTemporalState.ACTIVE, TaskTemporal.state(node, day * 3 - 1))
        assertEquals(TaskTemporalState.UPCOMING, TaskTemporal.state(node, day * 3))
        assertEquals(TaskTemporalState.UPCOMING, TaskTemporal.state(node, day * 4))
        assertEquals(TaskTemporalState.OVERDUE, TaskTemporal.state(node, day * 4 + 1))
        assertEquals(day * 2, TaskTemporal.nextTransition(task.copy(startAt = day * 2, dueAt = day * 2), 0))
        val same = task.copy(startAt = day, dueAt = day)
        assertEquals(TaskTemporalState.SCHEDULED, TaskTemporal.state(same, day - 1))
        assertEquals(TaskTemporalState.UPCOMING, TaskTemporal.state(same, day))
        assertEquals(TaskTemporalState.OVERDUE, TaskTemporal.state(same, day + 1))
    }

    @Test fun completionAndStructureOverrideUrgencyAndMissingDatesAreNeutral() {
        assertNull(TaskTemporal.state(task, 10))
        assertEquals(TaskTemporalState.COMPLETED, TaskTemporal.state(task.copy(dueAt = 1, isCompleted = true), 10))
        assertEquals(TaskTemporalState.COMPLETED, TaskTemporal.state(task.copy(startAt = 100, isCompleted = true), 10))
        assertNull(TaskTemporal.state(task.copy(startAt = 1, dueAt = 2, hasChildren = true), 10))
        assertEquals(TaskTemporalState.ACTIVE, TaskTemporal.state(task.copy(startAt = 1), 1))
        assertEquals(TaskTemporalState.UPCOMING, TaskTemporal.state(task.copy(dueAt = 100), 10))
    }

    @Test fun nextTransitionSupportsOneScreenTimerAndSafeLongBoundaries() {
        val node = task.copy(startAt = day * 2, dueAt = day * 4)
        assertEquals(day * 2, TaskTemporal.nextTransition(node, 0))
        assertEquals(day * 3, TaskTemporal.nextTransition(node, day * 2))
        assertEquals(day * 4 + 1, TaskTemporal.nextTransition(node, day * 3))
        assertNull(TaskTemporal.nextTransition(node, day * 4 + 1))
        assertNull(TaskTemporal.nextTransition(node.copy(isCompleted = true), 0))
        assertNull(TaskTemporal.nextTransition(node.copy(hasChildren = true), 0))
        assertEquals(TaskTemporalState.ACTIVE, TaskTemporal.state(task.copy(dueAt = Long.MAX_VALUE), Long.MIN_VALUE))
        assertEquals(TaskTemporalState.UPCOMING, TaskTemporal.state(task.copy(dueAt = Long.MAX_VALUE), Long.MAX_VALUE))
        assertNull(TaskTemporal.nextTransition(task.copy(dueAt = Long.MAX_VALUE), Long.MAX_VALUE))
    }

    @Test fun optionalDatesAreValidButReversedDatesAreRejected() {
        TaskTemporal.validateDates(null, null); TaskTemporal.validateDates(2, null)
        TaskTemporal.validateDates(null, 1); TaskTemporal.validateDates(2, 2)
        try { TaskTemporal.validateDates(2, 1); fail("Reversed dates") } catch (_: IllegalArgumentException) {}
    }
}
