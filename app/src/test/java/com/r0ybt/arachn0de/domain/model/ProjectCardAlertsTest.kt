package com.r0ybt.arachn0de.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ProjectCardAlertsTest {
    private val today = CalendarDay(2026, 10, 8)
    private val zone = TimeZone.getTimeZone("America/Santiago")
    private fun instant(day: Int, hour: Int = 12, targetZone: TimeZone = zone) = Calendar.getInstance(targetZone).apply {
        clear(); set(2026, Calendar.OCTOBER, day, hour, 0, 0)
    }.timeInMillis
    private fun task(id: String, project: String = "p", parent: String? = null) = Node(id, project, parent, id, "", false, 0, 0, 0, false)
    private fun alerts(vararg nodes: Node) = projectCardAlerts(NodeTreeSnapshot(nodes.toList()), today, zone)

    @Test fun emptyAndOrdinaryProjectsHaveNoAlerts() {
        assertTrue(alerts().isEmpty())
        assertTrue(alerts(task("ordinary"), task("low").copy(priority = Priority.LOW), task("medium").copy(priority = Priority.MEDIUM)).isEmpty())
    }
    @Test fun highPriorityAndTodayCountIndependentlyIncludingElapsedHoursAndFutureStarts() {
        assertEquals(ProjectCardAlerts(2, 3), alerts(
            task("both").copy(priority = Priority.HIGH, dueAt = instant(8, 0)),
            task("high").copy(priority = Priority.HIGH),
            task("due").copy(dueAt = instant(8, 23)),
            task("scheduled").copy(startAt = instant(8, 20), dueAt = instant(8, 23)),
            task("yesterday").copy(dueAt = instant(7, 23)),
            task("tomorrow").copy(dueAt = instant(9, 0))) ["p"])
    }
    @Test fun completedNotesAndLayersNeverCountAndProjectsStaySeparate() {
        val layer = task("layer").copy(purpose = NodePurpose.LAYER, priority = Priority.HIGH, dueAt = instant(8))
        assertEquals(mapOf("other" to ProjectCardAlerts(1, 1)), alerts(layer,
            task("done", parent = layer.id).copy(isCompleted = true, priority = Priority.HIGH, dueAt = instant(8)),
            task("note").copy(purpose = NodePurpose.NOTE, priority = Priority.HIGH, dueAt = instant(8)),
            task("otherTask", project = "other").copy(priority = Priority.HIGH, dueAt = instant(8))))
    }
    @Test fun allDepthsCountWithoutPropagationOrDoubleCounting() {
        val layers = (0 until 400).map { index -> task("layer$index", parent = if (index == 0) null else "layer${index - 1}")
            .copy(purpose = NodePurpose.LAYER, hasChildren = true) }
        val nodes = layers + listOf(task("root").copy(priority = Priority.HIGH),
            task("middle", parent = "layer150").copy(dueAt = instant(8)),
            task("deep", parent = "layer399").copy(priority = Priority.HIGH, dueAt = instant(8)))
        assertEquals(ProjectCardAlerts(2, 2), projectCardAlerts(NodeTreeSnapshot(nodes), today, zone)["p"])
    }
    @Test fun localDateBoundariesAndDayChangesUseTheDeviceZone() {
        val nodes = NodeTreeSnapshot(listOf(task("late").copy(dueAt = instant(8, 23))))
        assertEquals(ProjectCardAlerts(0, 1), projectCardAlerts(nodes, today, zone)["p"])
        assertTrue(projectCardAlerts(nodes, CalendarDay(2026, 10, 9), zone).isEmpty())
        // 23:00 in Santiago is already the next UTC calendar date.
        assertTrue(projectCardAlerts(nodes, today, TimeZone.getTimeZone("UTC")).isEmpty())
        assertEquals(ProjectCardAlerts(0, 1), projectCardAlerts(nodes, CalendarDay(2026, 10, 9), TimeZone.getTimeZone("UTC"))["p"])
        val range = TemporalRanges.day(today, zone)
        assertEquals(ProjectCardAlerts(0, 2), alerts(
            task("before").copy(dueAt = range.startInclusive - 1),
            task("first").copy(dueAt = range.startInclusive),
            task("last").copy(dueAt = range.endExclusive - 1),
            task("after").copy(dueAt = range.endExclusive))["p"])
    }
    @Test fun manyProjectsAndDescendantsAreAggregatedInOneProjection() {
        val nodes = (0 until 2_000).flatMap { index ->
            val project = "p$index"
            val layer = task("layer$index", project).copy(purpose = NodePurpose.LAYER, hasChildren = true)
            listOf(layer) + (0 until 5).map { task("t${index}_$it", project, layer.id).copy(priority = Priority.HIGH, dueAt = instant(8)) }
        }
        val result = projectCardAlerts(NodeTreeSnapshot(nodes), today, zone)
        assertEquals(2_000, result.size)
        assertTrue(result.values.all { it == ProjectCardAlerts(5, 5) })
    }
}
