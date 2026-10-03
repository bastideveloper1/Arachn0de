package com.r0ybt.arachn0de.domain

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class NodeFiltersTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun time(day: CalendarDay, hour: Int = 0, zone: TimeZone = utc) = GregorianCalendar(zone).apply {
        clear(); set(day.year, day.month - 1, day.day, hour, 0)
    }.timeInMillis
    private val today = CalendarDay(2026, 12, 31)
    private val now = time(today, 12)
    private fun range(value: TimeFilter, locale: Locale = Locale.FRANCE) = TemporalRanges.resolve(value, now, utc, locale)
    private fun expected(start: CalendarDay, end: CalendarDay) = TemporalRange(time(start), time(end))
    private fun node(id: String, due: Long? = now, completed: Boolean = false) = Node(id, "p", null, id, "", completed, 0, 1, 1, false, dueAt = due)
    private val assignments = mapOf("a" to setOf("roy", "ana"), "b" to setOf("ana"))

    @Test fun todayIsCivilMidnightToNextMidnight() { assertEquals(expected(today, CalendarDay(2027,1,1)), range(TimeFilter.TODAY)) }
    @Test fun tomorrowCrossesYear() { assertEquals(expected(CalendarDay(2027,1,1), CalendarDay(2027,1,2)), range(TimeFilter.TOMORROW)) }
    @Test fun thisWeekUsesLocaleAndCanCrossYear() {
        assertEquals(expected(CalendarDay(2026,12,28), CalendarDay(2027,1,4)), range(TimeFilter.THIS_WEEK))
        assertEquals(expected(CalendarDay(2026,12,27), CalendarDay(2027,1,3)), range(TimeFilter.THIS_WEEK, Locale.US))
    }
    @Test fun nextWeekUsesCivilDaysAndLocale() { assertEquals(expected(CalendarDay(2027,1,4), CalendarDay(2027,1,11)), range(TimeFilter.NEXT_WEEK)) }
    @Test fun thisMonthIsExact() { assertEquals(expected(CalendarDay(2026,12,1), CalendarDay(2027,1,1)), range(TimeFilter.THIS_MONTH)) }
    @Test fun previousMonthUsesActualLength() { assertEquals(expected(CalendarDay(2026,11,1), CalendarDay(2026,12,1)), range(TimeFilter.PREVIOUS_MONTH)) }
    @Test fun nextMonthCrossesYear() { assertEquals(expected(CalendarDay(2027,1,1), CalendarDay(2027,2,1)), range(TimeFilter.NEXT_MONTH)) }
    @Test fun allHasNoRangeAndGenericFilterCanIncludeUndatedNodes() {
        assertNull(range(TimeFilter.ALL)); assertTrue(NodeFilter().matches(node("none", null), emptySet()))
        assertFalse(NodeFilter(range(TimeFilter.TODAY)).matches(node("none", null), emptySet()))
    }
    @Test fun februaryAndLeapYear() {
        for ((year, days) in listOf(2026 to 28, 2028 to 29, 2100 to 28, 2000 to 29)) {
            val r = TemporalRanges.month(CalendarMonth(year,2), utc)
            assertEquals(days * 86_400_000L, r.endExclusive - r.startInclusive)
            assertEquals(time(CalendarDay(year,3,1)), r.endExclusive)
        }
    }
    @Test fun boundariesAreInclusiveExclusiveToTheMillisecond() {
        val r = range(TimeFilter.TODAY)!!
        assertFalse(r.startInclusive - 1 in r); assertTrue(r.startInclusive in r)
        assertTrue(r.endExclusive - 1 in r); assertFalse(r.endExclusive in r)
        assertTrue(runCatching { TemporalRange(2,1) }.isFailure)
    }
    @Test fun localZoneChangesMidnightsWithoutChangingPersistedInstants() {
        val zone = TimeZone.getTimeZone("America/Santiago")
        val instant = time(CalendarDay(2026,10,3), 1)
        val local = TemporalRanges.resolve(TimeFilter.TODAY, instant, zone, Locale.FRANCE)!!
        assertEquals(CalendarDay(2026,10,2), CalendarDates.localDay(local.startInclusive, zone))
        assertTrue(instant in local)
        assertTrue(local.startInclusive != TemporalRanges.resolve(TimeFilter.TODAY, instant, utc, Locale.FRANCE)!!.startInclusive)
    }
    @Test fun daylightSavingDaysAndWeeksAreNotFixedDurations() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val spring = TemporalRanges.day(CalendarDay(2026,3,8), zone)
        val autumn = TemporalRanges.day(CalendarDay(2026,11,1), zone)
        assertEquals(23 * 3600000L, spring.endExclusive - spring.startInclusive)
        assertEquals(25 * 3600000L, autumn.endExclusive - autumn.startInclusive)
        val week = TemporalRanges.week(CalendarDay(2026,3,8), zone, Calendar.MONDAY)
        assertEquals(167 * 3600000L, week.endExclusive - week.startInclusive)
        assertEquals(TemporalRanges.day(CalendarDay(2026,3,9), zone).startInclusive, spring.endExclusive)
    }
    @Test fun repeatedMidnightIncludesBothOffsetsAndSkippedCivilDayIsEmpty() {
        val zone=TimeZone.getTimeZone("America/Havana")
        val day=CalendarDay(2026,11,1)
        val r=TemporalRanges.day(day,zone)
        assertEquals(day,CalendarDates.localDay(r.startInclusive,zone))
        assertEquals(day,CalendarDates.localDay(r.startInclusive+3600000,zone))
        assertTrue(r.startInclusive+3600000 in r)
        assertEquals(25 * 3600000L,r.endExclusive-r.startInclusive)
        val skipped=TemporalRanges.day(CalendarDay(2011,12,30),TimeZone.getTimeZone("Pacific/Apia"))
        assertEquals(skipped.startInclusive,skipped.endExclusive)
    }
    @Test fun personaAllSpecificMultipleUnassignedAndEmpty() {
        val list = listOf(node("a"), node("b"), node("unassigned"))
        assertEquals(3, NodeFilter().apply(list, assignments).size)
        assertEquals(listOf("a"), NodeFilter(personId="roy").apply(list, assignments).map { it.id })
        assertEquals(listOf("a", "b"), NodeFilter(personId="ana").apply(list, assignments).map { it.id })
        assertTrue(NodeFilter(personId="missing").apply(list, assignments).isEmpty())
    }
    @Test fun statesReuseCompletionFlag() {
        val list = listOf(node("a"), node("b", completed=true))
        assertEquals(2, NodeFilter().apply(list, assignments).size)
        assertEquals(listOf("a"), NodeFilter(completion=CompletionFilter.PENDING).apply(list, assignments).map { it.id })
        assertEquals(listOf("b"), NodeFilter(completion=CompletionFilter.COMPLETED).apply(list, assignments).map { it.id })
    }
    @Test fun timePersonaAndStateCombineWithAnd() {
        val list = listOf(node("a"), node("b", completed=true), node("outside", now+8*86400000), node("none", null))
        assertEquals(listOf("a"), NodeFilter(range(TimeFilter.TODAY), "roy").apply(list, assignments).map { it.id })
        assertEquals(listOf("a"), NodeFilter(range(TimeFilter.THIS_WEEK), completion=CompletionFilter.PENDING).apply(list, assignments).map { it.id })
        assertEquals(listOf("a"), NodeFilter(range(TimeFilter.THIS_MONTH), "ana", CompletionFilter.PENDING).apply(list, assignments).map { it.id })
        assertEquals(listOf("b"), NodeFilter(personId="ana", completion=CompletionFilter.COMPLETED).apply(list, assignments).map { it.id })
        assertTrue(NodeFilter(range(TimeFilter.TODAY), "roy", CompletionFilter.COMPLETED).apply(list, assignments).isEmpty())
    }
    @Test fun callerCanChooseTemporalFieldWithoutSecondEngine() {
        val task = node("a", now + 86400000).copy(startAt=now)
        assertTrue(NodeFilter(range(TimeFilter.TODAY)).apply(listOf(task), assignments).isEmpty())
        assertEquals(listOf(task), NodeFilter(range(TimeFilter.TODAY)).apply(listOf(task), assignments) { it.startAt })
    }
    @Test fun calendarKeepsDueOnlyEligibilityAndNeverExpandsStartIntervals() {
        val layer = node("layer")
        val task = node("a", now+86400000).copy(parentId=layer.id, startAt=now-86400000)
        val note = node("note").copy(purpose=NodePurpose.NOTE)
        val undated = node("no-due", null).copy(startAt=now)
        val source = CalendarSnapshot(NodeTreeSnapshot(listOf(layer, task, note, undated)), utc)
        assertTrue(FilteredCalendar(source, NodeFilter(range(TimeFilter.TODAY)), assignments).tasks.isEmpty())
        assertEquals(listOf(task), FilteredCalendar(source, NodeFilter(), assignments).tasks)
    }
    @Test fun obligationsPreserveCurrenciesAndNodeIdentityAndOriginalProjection() {
        val list = listOf(node("a").copy(obligation=Obligation(15000,"CLP")), node("b").copy(obligation=Obligation(1050,"USD")))
        val source = CalendarSnapshot(NodeTreeSnapshot(list), utc)
        val result = FilteredCalendar(source, NodeFilter(range(TimeFilter.THIS_MONTH), "ana"), assignments)
        assertEquals(list, result.tasks); assertEquals(setOf("CLP","USD"),result.tasks.map { it.obligation!!.currencyCode }.toSet())
        assertSame(source, result.source); assertEquals(list, source.tree.nodes)
    }
    @Test fun largeListsUseOneAssignmentIndexAndStableOrder() {
        val list = List(10000) { node("n$it", now+it, completed=it%2==0) }
        val ids = list.associate { it.id to setOf("roy", "ana") }
        val filtered = FilteredCalendar(CalendarSnapshot(NodeTreeSnapshot(list.reversed()), utc), NodeFilter(range(TimeFilter.TODAY), "roy", CompletionFilter.PENDING), ids)
        assertEquals(5000, filtered.tasks.size)
        assertEquals(list.filter { !it.isCompleted }, filtered.tasks)
    }
}
