package com.r0ybt.arachn0de.domain

import com.r0ybt.arachn0de.domain.model.*
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Test
import org.junit.Assert.*

class NodeBatchGeneratorTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun date(year: Int, month: Int, day: Int, hour: Int = 10, zone: TimeZone = utc): Long = GregorianCalendar(zone).apply {
        clear(); set(year, month - 1, day, hour, 0)
    }.timeInMillis
    private fun generate(p: NodeBatchParameters, zone: TimeZone = utc) = NodeBatchGenerator.generate(p, zone)
    private fun rejected(p: NodeBatchParameters) { try { generate(p); fail("Expected rejection") } catch (_: IllegalArgumentException) {} }

    @Test fun quantitiesNumberingAndFinalTitlesAreValidated() {
        assertEquals(listOf("Episode"), generate(NodeBatchParameters(" Episode ", 1)).map { it.title })
        assertEquals(listOf("8 Episode", "9 Episode", "10 Episode"), generate(NodeBatchParameters("Episode", 3, NumberingMode.PREFIX, 8)).map { it.title })
        assertEquals(listOf("Episode 0", "Episode 1"), generate(NodeBatchParameters("Episode", 2, NumberingMode.SUFFIX, 0)).map { it.title })
        assertEquals(500, generate(NodeBatchParameters("Same", NodeBatchGenerator.MAX_BATCH_SIZE)).size)
        rejected(NodeBatchParameters("Same", 501)); rejected(NodeBatchParameters("Same", 0))
        rejected(NodeBatchParameters("  ", 1)); rejected(NodeBatchParameters("x".repeat(101), 1))
        rejected(NodeBatchParameters("x".repeat(98), 2, NumberingMode.SUFFIX, 9))
        rejected(NodeBatchParameters("Same", 2, NumberingMode.SUFFIX, Int.MAX_VALUE))
        rejected(NodeBatchParameters("Same", 1, NumberingMode.PREFIX, -1))
        val emoji = "😀".repeat(98)
        assertEquals(100, TitleLimits.count(generate(NodeBatchParameters(emoji, 1, NumberingMode.SUFFIX)).single().title))
    }

    @Test fun purposeDescriptionAndNoDatesRespectNoteSemantics() {
        val specs = generate(NodeBatchParameters("Note", 3, purpose = NodePurpose.NOTE, description = "Content", firstDueAt = 123))
        assertTrue(specs.all { it.description == "Content" && it.purpose == NodePurpose.NOTE && it.dueAt == null })
        rejected(NodeBatchParameters("Note", 1, purpose = NodePurpose.NOTE, temporalRule = BatchTemporalRule.DAILY, firstDueAt = 123))
        rejected(NodeBatchParameters("Task", 1, temporalRule = BatchTemporalRule.DAILY))
    }

    @Test fun monthlyAndYearlyUseOriginalAnchorWithoutDrift() {
        val monthly = generate(NodeBatchParameters("Month", 5, temporalRule = BatchTemporalRule.MONTHLY, firstDueAt = date(2026, 1, 31)))
        assertEquals(listOf(date(2026,1,31), date(2026,2,28), date(2026,3,31), date(2026,4,30), date(2026,5,31)), monthly.map { it.dueAt })
        val leap = generate(NodeBatchParameters("Month", 3, temporalRule = BatchTemporalRule.MONTHLY, firstDueAt = date(2028,1,31)))
        assertEquals(date(2028,2,29), leap[1].dueAt); assertEquals(date(2028,3,31), leap[2].dueAt)
        val annual = generate(NodeBatchParameters("Year", 5, temporalRule = BatchTemporalRule.YEARLY, firstDueAt = date(2024,2,29)))
        assertEquals(listOf(date(2024,2,29), date(2025,2,28), date(2026,2,28), date(2027,2,28), date(2028,2,29)), annual.map { it.dueAt })
    }

    @Test fun dailyWeeklyPreserveLocalCalendarAcrossDstAndYearBoundary() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val daily = generate(NodeBatchParameters("Day", 4, temporalRule = BatchTemporalRule.DAILY, firstDueAt = date(2026,3,7,9,zone)), zone)
        assertEquals((7..10).map { date(2026,3,it,9,zone) }, daily.map { it.dueAt })
        assertEquals(23L * 60 * 60 * 1000, daily[1].dueAt!! - daily[0].dueAt!!)
        val weekly = generate(NodeBatchParameters("Week", 3, temporalRule = BatchTemporalRule.WEEKLY, firstDueAt = date(2026,3,2,10,zone)), zone)
        assertEquals(listOf(2,9,16).map { date(2026,3,it,10,zone) }, weekly.map { it.dueAt })
        val yearEnd = generate(NodeBatchParameters("Day", 2, temporalRule = BatchTemporalRule.DAILY, firstDueAt = date(2026,12,31)))
        assertEquals(date(2027,1,1), yearEnd.last().dueAt)
    }

    @Test fun nonexistentWallTimeRejectsWholeGenerationAndOverlapUsesStandardOffset() {
        val zone = TimeZone.getTimeZone("America/New_York")
        try {
            generate(NodeBatchParameters("Gap", 2, temporalRule = BatchTemporalRule.DAILY, firstDueAt = date(2026,3,7,2,zone) + 30*60*1000), zone)
            fail("Gap must be rejected")
        } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("hora inexistente")) }
        try {
            val samoa = TimeZone.getTimeZone("Pacific/Apia")
            generate(NodeBatchParameters("Skipped day", 2, temporalRule = BatchTemporalRule.DAILY, firstDueAt = date(2011,12,29,9,samoa)), samoa)
            fail("Skipped local date must be rejected")
        } catch (_: IllegalArgumentException) {}
        val first = date(2026,10,31,1,zone) + 30*60*1000
        val overlap = generate(NodeBatchParameters("Overlap", 3, temporalRule = BatchTemporalRule.DAILY, firstDueAt = first), zone)
        val middle = GregorianCalendar(zone).apply { timeInMillis = overlap[1].dueAt!! }
        assertEquals(1, middle.get(Calendar.HOUR_OF_DAY)); assertEquals(30, middle.get(Calendar.MINUTE))
        assertEquals(0, middle.get(Calendar.DST_OFFSET))
        assertEquals(date(2026,11,2,1,zone) + 30*60*1000, overlap.last().dueAt)
    }
}
