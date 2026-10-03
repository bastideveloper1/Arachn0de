package com.r0ybt.arachn0de.ui

import androidx.compose.runtime.saveable.SaverScope
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.*
import org.junit.Test
import org.junit.Assert.*

class CreationDraftStateTest {
    private val scope=object:SaverScope { override fun canBeSaved(value:Any)=true }
    private fun restore(store:EditorDraftStore):EditorDraftStore {
        val encoded=with(EditorDraftStore.Saver) { scope.save(store) }
        return checkNotNull(EditorDraftStore.Saver.restore(checkNotNull(encoded)))
    }
    @Test fun closedCompleteDraftRoundTripsWithoutMixingDestinationsOrNodes() {
        val store=EditorDraftStore()
        store.open("parent") { EditorDraft(null,"parent","New","Desc").apply {
            financialEnabled=true;amountText="25000";currencyCode="CLP";startAt=100;dueAt=200
            priority=Priority.HIGH;tagIds=listOf("tag");responsibleIds=listOf("person")
            toggleRecurrence(true);recurrenceFrequency="MONTHLY";recurrenceInterval="3";recurrenceStart="2090-01-31";recurrenceEnd="2091-01-31"
            toggleRecurrence(false);batchEnabled=true;batchQuantity="12";batchNumbering=NumberingMode.SUFFIX;batchStartNumber="4";batchTemporal=BatchTemporalRule.MONTHLY
            startEnabled=false;dueEnabled=false
        } }
        val identity=store.active!!.creationId;store.close()
        val recovered=restore(store);assertNull(recovered.active)
        recovered.open("other") { EditorDraft(null,"other","Other","") };assertEquals("Other",recovered.active!!.title);recovered.close()
        recovered.open("parent","x") { EditorDraft("x","parent","X","") };assertEquals("X",recovered.active!!.title);recovered.close()
        recovered.open("parent") { error("Must recover original") }
        with(recovered.active!!) {
            assertEquals(identity,creationId);assertEquals("New",title);assertEquals("Desc",description);assertTrue(financialEnabled)
            assertEquals("25000",amountText);assertEquals("CLP",currencyCode);assertEquals(100L,startAt);assertEquals(200L,dueAt);assertNull(activeStart);assertNull(activeDue)
            assertEquals(Priority.HIGH,priority);assertEquals(listOf("tag"),tagIds);assertEquals(listOf("person"),responsibleIds)
            assertEquals("3",recurrenceInterval);assertEquals("2090-01-31",recurrenceStart);assertEquals("2091-01-31",recurrenceEnd)
            assertEquals("MONTHLY",latentFrequency);assertEquals("NONE",recurrenceFrequency)
            assertTrue(batchEnabled);assertEquals("12",batchQuantity);assertEquals("4",batchStartNumber);assertEquals(NumberingMode.SUFFIX,batchNumbering);assertEquals(BatchTemporalRule.MONTHLY,batchTemporal)
        }
        recovered.clear();recovered.open("parent") { EditorDraft(null,"parent","","") };assertFalse(recovered.active!!.hasWork)
    }
    @Test fun previousSavedEditorFormatRemainsReadable() {
        val d=EditorDraft(null,"p","Title","Desc",startAt=100,dueAt=200,priority=Priority.HIGH).apply {
            recurrenceFrequency="YEARLY";recurrenceStart="2090-01-01";tagIds=listOf("tag");responsibleIds=listOf("person")
        }
        val current=(with(EditorDraft.Saver) { scope.save(d) } as List<*>).map { it as String }
        val old=current.drop(9)
        val restored=checkNotNull(EditorDraft.Saver.restore(old))
        assertEquals(d.creationId,restored.creationId);assertEquals(Priority.HIGH,restored.priority)
        assertEquals(100L,restored.activeStart);assertEquals(200L,restored.activeDue)
        assertEquals("YEARLY",restored.latentFrequency);assertEquals(listOf("tag"),restored.tagIds);assertEquals(listOf("person"),restored.responsibleIds)
    }
    @Test fun batchAdapterUsesOneSetOfCommonFieldsAndNotesKeepLatentActionValues() {
        val d=EditorDraft(null,null,"Cuota","Description",startAt=100,dueAt=200,priority=Priority.HIGH).apply {
            financialEnabled=true;amountText="25000";tagIds=listOf("tag");responsibleIds=listOf("person")
            batchEnabled=true;batchQuantity="3";batchNumbering=NumberingMode.SUFFIX;batchStartNumber="4";batchTemporal=BatchTemporalRule.DAILY
        }
        val batch=d.batchDraft();val specs=NodeBatchGenerator.generate(batch.parameters(),java.util.TimeZone.getTimeZone("UTC"))
        assertEquals(listOf("Cuota 4","Cuota 5","Cuota 6"),specs.map { it.title });assertTrue(specs.all { it.priority==Priority.HIGH && it.obligation==Obligation(25000,"CLP") })
        assertEquals(listOf("tag"),batch.dates.tagIds);assertEquals(listOf("person"),batch.responsibleIds)
        d.purpose=NodePurpose.NOTE
        val notes=NodeBatchGenerator.generate(d.batchDraft().parameters(),java.util.TimeZone.getTimeZone("UTC"));assertTrue(notes.all { it.dueAt==null && it.obligation==null && it.priority==Priority.NONE })
        d.purpose=NodePurpose.ACTION;assertEquals(batch.parameters(),d.batchDraft().parameters())
    }
}
