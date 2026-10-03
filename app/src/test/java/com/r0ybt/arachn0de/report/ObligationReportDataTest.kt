package com.r0ybt.arachn0de.report

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ObligationReportDataTest {
    @Test fun periodsPersonsTotalsAndOrderComeFromTheCurrentProjection() {
        FinancialPeriod.entries.forEach { period ->
            listOf<String?>(null, "r", "s").forEach { person ->
                val snapshot = ReportFixture.snapshot(period, person)
                if (snapshot.tasks.isEmpty()) return@forEach
                val report = ReportFixture.data(snapshot)
                assertEquals(snapshot.tasks.map { it.title }, report.obligations.map { it.title })
                assertEquals(snapshot.summary.byCurrency.keys.toList(), report.currencies.map { it.code })
                snapshot.summary.byCurrency.forEach { (code, totals) ->
                    val section = report.currencies.single { it.code == code }
                    assertEquals(Money.format(totals.totalMinor, code, ReportFixture.locale), section.total)
                    assertEquals(Money.format(totals.pendingMinor, code, ReportFixture.locale), section.pending)
                    assertEquals(Money.format(totals.completedMinor, code, ReportFixture.locale), section.completed)
                }
                assertEquals(if (person == null) null else "Roy", report.person)
                assertEquals(snapshot.zoneId, report.zoneId)
            }
        }
        assertEquals("octubre 2026", ReportFixture.data(ReportFixture.snapshot(FinancialPeriod.THIS_MONTH)).period)
        assertEquals("septiembre 2026", ReportFixture.data(ReportFixture.snapshot(FinancialPeriod.PREVIOUS_MONTH)).period)
        assertEquals("noviembre 2026", ReportFixture.data(ReportFixture.snapshot(FinancialPeriod.NEXT_MONTH)).period)
        assertEquals("Todo el período", ReportFixture.data().period)
    }
    @Test fun responsibilitiesHaveNamesFullAmountsAndNoDuplication() {
        val roy = ReportFixture.data(ReportFixture.snapshot(person = "r"))
        val scarlett = ObligationReportData.from(ReportFixture.snapshot(person = "s"),
            ReportFixture.projects, "Scarlett", ReportFixture.now, ReportFixture.locale)
        assertEquals(roy.currencies, scarlett.currencies)
        assertEquals(1, roy.obligations.size)
        assertEquals("Roy · Scarlett", roy.obligations.single().responsibleNames)
        assertEquals("Scarlett", scarlett.person)
        assertEquals("Compras", roy.obligations.single().project)
    }
    @Test fun undatedCompletedCountsAndGenerationUseFrozenLocalTexts() {
        val data = ReportFixture.data()
        assertEquals("Sin vencimiento", data.obligations.last().due)
        assertEquals("Completada", data.obligations.single { it.title == "paid" }.status)
        assertTrue(data.obligations.first().due.startsWith("Vence 15"))
        assertEquals("5 obligaciones · 4 pendientes · 1 completada", data.counts)
        assertTrue(data.generatedLabel.contains("2026 · 12:00"))
        assertEquals(ReportFixture.now, data.generatedAt)
    }
    @Test fun dataIsImmutableAndUnchangedBySubsequentDataEdits() {
        val source = ReportFixture.nodes.toMutableList()
        val assignments = mutableMapOf("oct" to listOf(ReportFixture.roy))
        val snapshot = ReportFixture.snapshot(source = source, assignments = assignments)
        val report = ReportFixture.data(snapshot)
        source.clear(); assignments.clear()
        val changed = ReportFixture.data(ReportFixture.snapshot(source = listOf(ReportFixture.node("new", completed = true))))
        assertEquals(5, report.obligations.size)
        assertEquals(1, changed.obligations.size)
        assertEquals("Roy", report.obligations.single { it.title == "Cuota notebook" }.responsibleNames)
        assertThrows(UnsupportedOperationException::class.java) { (report.obligations as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (report.currencies as MutableList).clear() }
    }
    @Test fun emptySelectionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ReportFixture.data(ReportFixture.snapshot(person = "missing")) }
    }
}
