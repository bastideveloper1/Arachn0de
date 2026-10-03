package com.r0ybt.arachn0de.domain.model

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/** Instant interval bounded by local civil midnights, never an assumed 24-hour duration. */
data class TemporalRange(val startInclusive: Long, val endExclusive: Long) {
    init { require(startInclusive <= endExclusive) }
    operator fun contains(instant: Long): Boolean = instant >= startInclusive && instant < endExclusive
}

enum class TimeFilter { TODAY, TOMORROW, THIS_WEEK, NEXT_WEEK, THIS_MONTH, PREVIOUS_MONTH, NEXT_MONTH, ALL }
enum class CompletionFilter { ALL, PENDING, COMPLETED }

/** Membership only: each consuming view defines eligible Nodes and its temporal field. */
data class NodeFilter(val range: TemporalRange? = null, val personId: String? = null, val completion: CompletionFilter = CompletionFilter.ALL, val tagId: String? = null, val priority: Priority? = null) {
    fun matches(node: Node, responsibleIds: Set<String>, temporalInstant: Long? = node.dueAt, tagIds: Set<String> = emptySet()): Boolean =
        (priority == null || (node.isCompletable && node.effectivePriority == priority)) &&
        (tagId == null || tagId in tagIds) &&
        (range == null || (temporalInstant != null && temporalInstant in range)) &&
            (personId == null || personId in responsibleIds) &&
            when (completion) {
                CompletionFilter.ALL -> true
                CompletionFilter.PENDING -> !node.isCompleted
                CompletionFilter.COMPLETED -> node.isCompleted
            }

    fun apply(nodes: List<Node>, responsibleIdsByNode: Map<String, Set<String>>,
        tagIdsByNode: Map<String, Set<String>> = emptyMap(),
        temporalInstant: (Node) -> Long? = { it.dueAt }): List<Node> =
        nodes.filter { matches(it, responsibleIdsByNode[it.id].orEmpty(), temporalInstant(it), tagIdsByNode[it.id].orEmpty()) }
}

object TemporalRanges {
    fun shift(day: CalendarDay, days: Int): CalendarDay = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply {
        timeInMillis = CalendarDates.labelInstant(day); add(Calendar.DAY_OF_MONTH, days)
    }.let(CalendarDates::dayOf)

    fun weekStart(day: CalendarDay, firstDayOfWeek: Int): CalendarDay {
        require(firstDayOfWeek in Calendar.SUNDAY..Calendar.SATURDAY)
        val weekday = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply {
            timeInMillis = CalendarDates.labelInstant(day)
        }.get(Calendar.DAY_OF_WEEK)
        return shift(day, -((weekday - firstDayOfWeek + 7) % 7))
    }

    private fun midnight(day: CalendarDay, zone: TimeZone): Long {
        val calendar = GregorianCalendar(zone, Locale.ROOT).apply {
            clear(); set(day.year, day.month - 1, day.day, 0, 0, 0)
        }
        val guess = calendar.timeInMillis
        // Calendar chooses the later offset for repeated midnight. Find the first instant
        // entering this civil date instead; a skipped date shares the next date's boundary.
        var low = Math.subtractExact(guess, 2 * 86_400_000L)
        var high = Math.addExact(guess, 2 * 86_400_000L)
        while (low < high) {
            val middle = low + (high - low) / 2
            calendar.timeInMillis = middle
            val date = CalendarDates.dayOf(calendar)
            val compared = if (calendar.get(Calendar.ERA) == GregorianCalendar.BC) -1 else
                compareValuesBy(date, day, { it.year }, { it.month }, { it.day })
            if (compared >= 0) high = middle else low = middle + 1
        }
        return low
    }

    fun between(start: CalendarDay, end: CalendarDay, zone: TimeZone) = TemporalRange(midnight(start, zone), midnight(end, zone))
    fun day(day: CalendarDay, zone: TimeZone) = between(day, shift(day, 1), zone)
    fun week(day: CalendarDay, zone: TimeZone, firstDayOfWeek: Int): TemporalRange {
        val start = weekStart(day, firstDayOfWeek)
        return between(start, shift(start, 7), zone)
    }
    fun month(month: CalendarMonth, zone: TimeZone): TemporalRange {
        val first = month.selecting(1)
        // CalendarMonth's navigation bounds remain 1..9999; a range can end at year 10000.
        return between(first, shift(first, month.days), zone)
    }
    fun resolve(filter: TimeFilter, now: Long, zone: TimeZone, locale: Locale): TemporalRange? {
        val today = CalendarDates.localDay(now, zone)
        return when (filter) {
            TimeFilter.TODAY -> day(today, zone)
            TimeFilter.TOMORROW -> day(shift(today, 1), zone)
            TimeFilter.THIS_WEEK -> week(today, zone, GregorianCalendar(zone, locale).firstDayOfWeek)
            TimeFilter.NEXT_WEEK -> week(shift(today, 7), zone, GregorianCalendar(zone, locale).firstDayOfWeek)
            TimeFilter.THIS_MONTH -> month(today.calendarMonth, zone)
            TimeFilter.PREVIOUS_MONTH -> month(today.calendarMonth.shifted(-1), zone)
            TimeFilter.NEXT_MONTH -> month(today.calendarMonth.shifted(1), zone)
            TimeFilter.ALL -> null
        }
    }
}

/** Filter the existing Calendar index, preserving eligibility, IDs, order, currencies and tree. */
class FilteredCalendar(val source: CalendarSnapshot, val filter: NodeFilter, responsibleIds: Map<String, Set<String>>, tagIds: Map<String, Set<String>> = emptyMap()) {
    val tasksByDay: Map<CalendarDay, List<Node>> = source.tasksByDay.mapValues { (_, nodes) -> filter.apply(nodes, responsibleIds, tagIds) }
        .filterValues { it.isNotEmpty() }
    val tasks: List<Node> = tasksByDay.values.flatten().sortedWith(compareBy<Node> { it.dueAt }.thenBy { it.createdAt }.thenBy { it.id })
    fun tasksOn(day: CalendarDay): List<Node> = tasksByDay[day].orEmpty()
}
