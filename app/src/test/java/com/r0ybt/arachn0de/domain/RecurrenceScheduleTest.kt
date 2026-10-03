package com.r0ybt.arachn0de.domain

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class RecurrenceScheduleTest {
    private fun date(start: String, frequency: RecurrenceFrequency, interval: Int, index: Long) =
        RecurrenceSchedule.format(RecurrenceSchedule.date(RecurrenceSchedule.parse(start), frequency, interval, index))
    @Test fun daily() { assertEquals("2026-10-04", date("2026-10-03", RecurrenceFrequency.DAILY, 1, 1)) }
    @Test fun weekly() { assertEquals("2026-10-12", date("2026-10-05", RecurrenceFrequency.WEEKLY, 1, 1)) }
    @Test fun monthly() { assertEquals("2026-11-10", date("2026-10-10", RecurrenceFrequency.MONTHLY, 1, 1)) }
    @Test fun yearly() { assertEquals("2027-10-10", date("2026-10-10", RecurrenceFrequency.YEARLY, 1, 1)) }
    @Test fun intervals() {
        assertEquals("2026-10-07", date("2026-10-03", RecurrenceFrequency.DAILY, 2, 2))
        assertEquals("2026-11-02", date("2026-10-05", RecurrenceFrequency.WEEKLY, 2, 2))
        assertEquals("2027-04-10", date("2026-10-10", RecurrenceFrequency.MONTHLY, 3, 2))
        assertEquals("2030-10-10", date("2026-10-10", RecurrenceFrequency.YEARLY, 2, 2))
    }
    @Test fun day31ClampsWithoutDrift() {
        assertEquals("2027-02-28", date("2027-01-31", RecurrenceFrequency.MONTHLY, 1, 1))
        assertEquals("2027-03-31", date("2027-01-31", RecurrenceFrequency.MONTHLY, 1, 2))
        assertEquals("2027-04-30", date("2027-01-31", RecurrenceFrequency.MONTHLY, 1, 3))
    }
    @Test fun leapYearsKeepOriginalAnchor() {
        assertEquals("2025-02-28", date("2024-02-29", RecurrenceFrequency.YEARLY, 1, 1))
        assertEquals("2028-02-29", date("2024-02-29", RecurrenceFrequency.YEARLY, 1, 4))
        assertEquals("2024-02-29", date("2024-01-31", RecurrenceFrequency.MONTHLY, 1, 1))
    }
    @Test fun indefiniteHasNoArtificialHorizon() {
        assertEquals("2526-10-10", date("2026-10-10", RecurrenceFrequency.YEARLY, 1, 500))
        assertEquals("10000-10-10", date("2026-10-10", RecurrenceFrequency.YEARLY, 1, 7974))
    }
    @Test fun resumeFindsFirstValidDateFromAnchor() {
        val start = RecurrenceSchedule.parse("2026-10-31")
        for ((day, index) in listOf("2027-01-01" to 3L, "2027-01-31" to 3L, "2027-02-01" to 4L))
            assertEquals(index, RecurrenceSchedule.indexOnOrAfter(start, RecurrenceFrequency.MONTHLY, 1, RecurrenceSchedule.parse(day)))
        assertEquals(2L, RecurrenceSchedule.indexOnOrAfter(RecurrenceSchedule.parse("2026-10-05"), RecurrenceFrequency.WEEKLY, 2, RecurrenceSchedule.parse("2026-10-20")))
    }
    @Test fun invalidInputIsRejected() {
        for (bad in listOf("2026-02-29", "2026-04-31", "garbage", "0000-01-01")) {
            assertTrue(runCatching { RecurrenceSchedule.parse(bad) }.isFailure)
        }
        assertTrue(runCatching { RecurrenceSchedule.date(0, RecurrenceFrequency.DAILY, 0, 0) }.isFailure)
        assertTrue(runCatching { RecurrenceSchedule.date(0, RecurrenceFrequency.DAILY, 1, -1) }.isFailure)
        assertTrue(runCatching { RecurrenceSchedule.date(0, RecurrenceFrequency.DAILY, 2, Long.MAX_VALUE) }.isFailure)
    }
    @Test fun daylightSavingGapMovesForwardAndNextPeriodKeepsWallTime() {
        val gap = RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2026-03-08"), "America/New_York", 150)
        val normal = RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2026-03-09"), "America/New_York", 150)
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/New_York"))
        cal.timeInMillis = gap; assertEquals(3, cal.get(java.util.Calendar.HOUR_OF_DAY)); assertEquals(30, cal.get(java.util.Calendar.MINUTE))
        cal.timeInMillis = normal; assertEquals(2, cal.get(java.util.Calendar.HOUR_OF_DAY))
    }
    @Test fun timeZoneDoesNotDependOnCurrentDefault() {
        val day = RecurrenceSchedule.parse("2026-10-10")
        val due = RecurrenceSchedule.timestamp(day, "America/Santiago", 600)
        assertEquals(day, RecurrenceSchedule.localDay(due, "America/Santiago"))
        assertTrue(RecurrenceSchedule.timestamp(day, "UTC", 600) != due)
    }
}
