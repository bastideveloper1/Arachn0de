package com.r0ybt.arachn0de.domain.model

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/** Civil date, independent of a clock/offset. Month is 1..12. Never persisted in Room. */
data class CalendarDay(val year: Int, val month: Int, val day: Int) {
    val key: String get() = "$year-$month-$day"
    val calendarMonth: CalendarMonth get() = CalendarMonth(year, month)
}

data class CalendarMonth(val year: Int, val month: Int) {
    init { require(year in 1..9999 && month in 1..12) }
    private fun first() = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply {
        clear(); set(year, month - 1, 1, 12, 0)
    }
    val days: Int get() = first().getActualMaximum(Calendar.DAY_OF_MONTH)
    fun shifted(offset: Int): CalendarMonth = first().apply { add(Calendar.MONTH, offset) }.let {
        CalendarMonth(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1)
    }
    /** Leading/trailing placeholders; only as many complete weeks as this month needs. */
    fun cells(firstDayOfWeek: Int): List<CalendarDay?> {
        require(firstDayOfWeek in Calendar.SUNDAY..Calendar.SATURDAY)
        val offset = (first().get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
        val count = days
        return List(((offset + count + 6) / 7) * 7) { index ->
            (index - offset + 1).takeIf { it in 1..count }?.let { CalendarDay(year, month, it) }
        }
    }
    fun selecting(day: Int): CalendarDay = CalendarDay(year, month, day.coerceIn(1, days))
}

object CalendarDates {
    fun localDay(instant: Long, zone: TimeZone): CalendarDay =
        GregorianCalendar(zone, Locale.ROOT).apply { timeInMillis = instant }.let(::dayOf)

    internal fun dayOf(calendar: Calendar): CalendarDay = CalendarDay(
        calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH),
    )

    /** Formatting civil dates uses UTC noon so midnight DST gaps never change their label. */
    fun labelInstant(day: CalendarDay): Long = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply {
        clear(); set(day.year, day.month - 1, day.day, 12, 0)
    }.timeInMillis
}

/** Read-only global projection. Completion is deliberately not a membership filter. */
class CalendarSnapshot(val tree: NodeTreeSnapshot, zone: TimeZone) {
    val zoneId: String = zone.id
    val tasksByDay: Map<CalendarDay, List<Node>>

    init {
        val parents = tree.nodes.mapNotNull { it.parentId }.toHashSet()
        val calendar = GregorianCalendar(zone, Locale.ROOT)
        val groups = mutableMapOf<CalendarDay, MutableList<Node>>()
        tree.nodes.forEach { node ->
            if (node.purpose == NodePurpose.ACTION && node.id !in parents && node.dueAt != null) {
                calendar.timeInMillis = node.dueAt
                groups.getOrPut(CalendarDates.dayOf(calendar)) { mutableListOf() }.add(node)
            }
        }
        val order = compareBy<Node> { it.dueAt }.thenBy { it.createdAt }.thenBy { it.id }
        tasksByDay = groups.mapValues { (_, tasks) -> tasks.sortedWith(order) }
    }

    fun tasksOn(day: CalendarDay): List<Node> = tasksByDay[day].orEmpty()

    /** Resolve only composed rows, without copying all deep paths into the index. */
    fun pathTo(id: String): List<Node> {
        val path = mutableListOf<Node>()
        var node = tree.nodesById[id]
        while (node != null) {
            path.add(node)
            node = node.parentId?.let(tree.nodesById::getValue)
        }
        return path.asReversed()
    }
}
