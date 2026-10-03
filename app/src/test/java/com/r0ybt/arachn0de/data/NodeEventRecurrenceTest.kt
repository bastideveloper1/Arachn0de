package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[24,28])
class NodeEventRecurrenceTest {
    private lateinit var db: Arachn0deDatabase
    private lateinit var nodes: NodeRepository
    private var now = RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2026-10-10"),"UTC",720)
    private fun rule() = RecurrenceRuleEntity("rule","p",null,"Bill","",15000,"CLP",RecurrenceSchedule.parse("2026-10-10"),"MONTHLY",1,null,0,"ACTIVE","UTC",0,null)
    @Before fun setup() = runBlocking {
        val context = RuntimeEnvironment.getApplication(); context.deleteDatabase("arachn0de.db"); db=Arachn0deDatabase.create(context); nodes=NodeRepository(db) { now }
        db.projectDao().insert(ProjectEntity("p","P","",0,1,1))
    }
    @After fun close() { db.close(); RuntimeEnvironment.getApplication().deleteDatabase("arachn0de.db") }
    @Test fun occurrencesHaveIndependentHistoryAndMaterializationRetriesDoNotDuplicate() = runBlocking {
        nodes.recurrence.create(rule()); nodes.recurrence.materializeDue(); nodes.recurrence.materializeDue()
        val october = nodes.getProjectNodes("p").single(); assertEquals(now,db.nodeEventDao().forNode(october.id).single().occurredAt)
        nodes.setCompleted(october.id,true); nodes.setCompleted(october.id,false); nodes.setCompleted(october.id,true)
        val before = db.nodeEventDao().forNode(october.id)
        nodes.recurrence.editTemplate("rule","p",null,"Future","",Obligation(20000,"CLP"),emptySet())
        now=RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2026-11-10"),"UTC",720); nodes.recurrence.materializeDue()
        assertEquals(before,db.nodeEventDao().forNode(october.id))
        val november = nodes.getProjectNodes("p").single { it.id != october.id }; assertEquals("CREATED",db.nodeEventDao().forNode(november.id).single().type)
        nodes.recurrence.setStatus("rule",RecurrenceStatus.PAUSED); nodes.recurrence.setStatus("rule",RecurrenceStatus.FINISHED)
        assertEquals(5,db.nodeEventDao().all().size); assertEquals(2,db.recurrenceDao().occurrences().size)
        nodes.deleteNode(november.id); nodes.recurrence.materializeDue(); assertTrue(db.nodeEventDao().forNode(november.id).isEmpty()); assertEquals(2,db.recurrenceDao().occurrences().size)
    }
    @Test fun eventFailureRollsBackOccurrenceReceiptAndCursor() = runBlocking {
        nodes.recurrence.create(rule())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_event BEFORE INSERT ON node_events BEGIN SELECT RAISE(ABORT,'injected'); END")
        assertTrue(runCatching { nodes.recurrence.materializeDue() }.isFailure)
        assertTrue(nodes.getProjectNodes("p").isEmpty()); assertTrue(db.nodeEventDao().all().isEmpty()); assertTrue(db.recurrenceDao().occurrences().isEmpty()); assertEquals(0L,nodes.recurrence.get("rule")!!.nextIndex)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_event"); nodes.recurrence.materializeDue(); nodes.recurrence.materializeDue()
        assertEquals(1,db.nodeEventDao().all().size); assertEquals(1,db.recurrenceDao().occurrences().size)
    }
}
