package com.r0ybt.arachn0de.ui
import com.r0ybt.arachn0de.domain.model.*
import org.junit.Test
import org.junit.Assert.*
class RecurrencePresentationTest {
    @Test fun monthlyAnchorAndIntervalAreUnambiguous() {
        val day=RecurrenceSchedule.parse("2026-10-15")
        assertEquals("Cada 1 mes, el día 15",recurrenceSummary(RecurrenceFrequency.MONTHLY,"1",day))
        assertEquals("Cada 2 meses, el día 15",recurrenceSummary(RecurrenceFrequency.MONTHLY,"2",day))
        assertEquals("2026-12-15",RecurrenceSchedule.format(RecurrenceSchedule.date(day,RecurrenceFrequency.MONTHLY,2,1)))
    }
}
