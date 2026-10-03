package com.r0ybt.arachn0de.ui.state

import com.r0ybt.arachn0de.domain.defaults.*
import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class CreationDefaultsDraftTest {
    private val zone=TimeZone.getTimeZone("UTC")
    private val now=RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2026-10-03"),"UTC",720)
    private fun seed(defaults:EffectiveCreationDefaults)=CreationDefaultsDraftFactory.create("layer",defaults,now,zone)
    @Test fun actionUsesCommonValuesWithoutTitleAmountRecurrenceBatchOrGroup() {
        val d=seed(EffectiveCreationDefaults(obligation=true,currency="USD",priority=Priority.HIGH,tags=setOf("t"),people=setOf("p"),due=DefaultDate(DefaultDateKind.DAY_OF_MONTH,15)))
        assertTrue(d.financialEnabled);assertEquals("USD",d.currencyCode);assertEquals(Priority.HIGH,d.priority);assertEquals(listOf("t"),d.tagIds);assertEquals(listOf("p"),d.responsibleIds)
        assertEquals("",d.title);assertEquals("",d.amountText);assertEquals(null,d.creationGroupId);assertFalse(d.batchEnabled)
        assertEquals(RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2026-10-15"),"UTC",0),d.activeDue)
    }
    @Test fun noteRetainsLatentFieldsAndRestoresThemWhenChangedToAction() {
        val d=seed(EffectiveCreationDefaults(purpose=NodePurpose.NOTE,obligation=true,priority=Priority.HIGH,start=DefaultDate(DefaultDateKind.TODAY)))
        assertNull(d.obligation());assertTrue(d.financialEnabled);assertEquals(Priority.HIGH,d.priority)
        d.purpose=NodePurpose.ACTION;assertNotNull(d.activeStart);assertTrue(d.financialEnabled)
    }
    @Test fun batchUsesCommonDefaultsAndItsOwnTemporalGeneration() {
        val d=seed(EffectiveCreationDefaults(obligation=true,currency="CLP",priority=Priority.HIGH,tags=setOf("t"),people=setOf("p"),due=DefaultDate(DefaultDateKind.DAY_OF_MONTH,15)))
        d.title="Cuota";d.amountText="100";d.batchEnabled=true;d.batchQuantity="2";d.batchTemporal=BatchTemporalRule.MONTHLY
        val batch=d.batchDraft();val specs=NodeBatchGenerator.generate(batch.parameters(),zone)
        assertEquals(listOf("p"),batch.responsibleIds);assertEquals(listOf("t"),batch.dates.tagIds)
        assertTrue(specs.all { it.priority==Priority.HIGH && it.obligation?.currencyCode=="CLP" })
        assertEquals(listOf("2026-10-15","2026-11-15"),specs.map { RecurrenceSchedule.format(RecurrenceSchedule.localDay(it.dueAt!!,"UTC")) })
        assertEquals("NONE",d.recurrenceFrequency);assertNull(d.creationGroupId)
    }
    @Test fun closedDraftKeepsEditsAndDiscardAllowsCurrentDefaults() {
        val store=EditorDraftStore();store.open("layer") { seed(EffectiveCreationDefaults(currency="USD")) }
        store.active!!.currencyCode="EUR";store.active!!.title="Trabajo";store.close()
        assertTrue(store.hasNew("layer"));store.open("layer") { error("Must not recreate") }
        assertEquals("EUR",store.active!!.currencyCode);assertEquals("Trabajo",store.active!!.title)
        store.clear();assertFalse(store.hasNew("layer"));store.open("layer") { seed(EffectiveCreationDefaults(currency="CLP")) };assertEquals("CLP",store.active!!.currencyCode)
    }
}
