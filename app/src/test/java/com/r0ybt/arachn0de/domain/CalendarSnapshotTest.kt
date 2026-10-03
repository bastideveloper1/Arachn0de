package com.r0ybt.arachn0de.domain

import com.r0ybt.arachn0de.domain.model.*
import java.util.*
import org.junit.Test
import org.junit.Assert.*

class CalendarSnapshotTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun instant(year: Int, month: Int, day: Int, hour: Int = 10, minute: Int = 0, zone: TimeZone = utc) = GregorianCalendar(zone).apply {
        clear(); set(year, month - 1, day, hour, minute)
    }.timeInMillis
    private fun node(id: String, due: Long? = null, parent: String? = null, project: String = "p", purpose: NodePurpose = NodePurpose.ACTION, completed: Boolean = false, created: Long = 1) =
        Node(id, project, parent, id, "", completed, 99, created, 1, false, dueAt = due, purpose = purpose)

    @Test fun onlyDatedActionLeavesIncludingCompletedAreGlobalAndSortedWithoutWrites() {
        val due = instant(2026,10,15)
        val nodes = listOf(node("layer", due), node("done", due, "layer", completed = true),
            node("b", due, "layer", created = 2), node("a", due, "layer", created = 2),
            node("early", due - 1, project = "other"), node("note", due, purpose = NodePurpose.NOTE), node("undated"))
        val tree = NodeTreeSnapshot(nodes)
        val progress = tree.projectProgressById
        val snapshot = CalendarSnapshot(tree, utc)
        assertEquals(listOf("early", "done", "a", "b"), snapshot.tasksOn(CalendarDay(2026,10,15)).map { it.id })
        assertEquals(listOf("layer", "done"), snapshot.pathTo("done").map { it.id })
        assertTrue(snapshot.pathTo("missing").isEmpty())
        assertEquals(progress, tree.projectProgressById); assertEquals(nodes, tree.nodes)
        assertEquals(snapshot.tasksByDay, CalendarSnapshot(NodeTreeSnapshot(nodes.reversed()), utc).tasksByDay)
    }

    @Test fun midnightUsesLocalDateAndZoneChangeOnlyChangesProjection() {
        val local = TimeZone.getTimeZone("GMT-03:00")
        val due = instant(2026,10,15,23,30,local)
        val tree = NodeTreeSnapshot(listOf(node("task", due)))
        assertEquals(CalendarDay(2026,10,15), CalendarDates.localDay(due, local))
        assertEquals(CalendarDay(2026,10,16), CalendarDates.localDay(due, utc))
        assertEquals(listOf("task"), CalendarSnapshot(tree,local).tasksOn(CalendarDay(2026,10,15)).map { it.id })
        assertEquals(listOf("task"), CalendarSnapshot(tree,utc).tasksOn(CalendarDay(2026,10,16)).map { it.id })
        assertEquals(due, tree.nodes.single().dueAt)
    }

    @Test fun dstInstantsAndRepeatedHourStillBelongToTheirLocalDate() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val spring = instant(2026,3,8,3,30,zone)
        val autumn = instant(2026,11,1,1,30,zone)
        val tree = NodeTreeSnapshot(listOf(node("spring",spring), node("standard",autumn), node("summer",autumn-3_600_000)))
        val snapshot = CalendarSnapshot(tree, zone)
        assertEquals(listOf("spring"), snapshot.tasksOn(CalendarDay(2026,3,8)).map { it.id })
        assertEquals(listOf("summer", "standard"), snapshot.tasksOn(CalendarDay(2026,11,1)).map { it.id })
    }

    @Test fun gridDerivesFourFiveSixWeeksAndAlignsWeekHeadersAndMonthNavigation() {
        assertEquals(28, CalendarMonth(2021,2).cells(Calendar.MONDAY).size)
        assertEquals(35, CalendarMonth(2021,1).cells(Calendar.MONDAY).size)
        assertEquals(42, CalendarMonth(2020,8).cells(Calendar.MONDAY).size)
        val sunday = CalendarMonth(2026,11).cells(Calendar.SUNDAY)
        val monday = CalendarMonth(2026,11).cells(Calendar.MONDAY)
        assertEquals(CalendarDay(2026,11,1), sunday[0]); assertEquals(CalendarDay(2026,11,1), monday[6])
        assertEquals((1..30).toList(), monday.filterNotNull().map { it.day })
        assertEquals(29, CalendarMonth(2028,2).days)
        assertEquals(CalendarDay(2026,2,28), CalendarMonth(2026,1).shifted(1).selecting(31))
        assertEquals(CalendarMonth(2027,1), CalendarMonth(2026,12).shifted(1))
        assertEquals(Calendar.SUNDAY, GregorianCalendar(utc, Locale.US).firstDayOfWeek)
        assertEquals(Calendar.MONDAY, GregorianCalendar(utc, Locale.FRANCE).firstDayOfWeek)
    }

    @Test fun veryDeepPathsResolveIterativelyOnlyWhenRequested() {
        val nodes = List(10_000) { index -> node("n$index", if (index == 9_999) instant(2026,10,15) else null, if (index == 0) null else "n${index-1}") }
        val snapshot = CalendarSnapshot(NodeTreeSnapshot(nodes), utc)
        assertEquals(1, snapshot.tasksByDay.values.sumOf { it.size })
        assertEquals(10_000, snapshot.pathTo("n9999").size)
    }
}
