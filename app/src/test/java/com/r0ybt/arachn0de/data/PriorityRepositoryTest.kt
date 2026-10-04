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
class PriorityRepositoryTest {
    private lateinit var db:Arachn0deDatabase; private lateinit var nodes:NodeRepository
    private var now=RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2026-10-10"),"UTC",720)
    @Before fun setup()=runBlocking { val c=RuntimeEnvironment.getApplication(); c.deleteDatabase("arachn0de.db"); db=Arachn0deDatabase.create(c); nodes=NodeRepository(db) { now }; db.projectDao().insert(ProjectEntity("p","P","",0,1,1)) }
    @After fun close() { db.close(); RuntimeEnvironment.getApplication().deleteDatabase("arachn0de.db") }
    @Test fun allValuesCreationEditingAndStableRetry()=runBlocking {
        assertEquals(Priority.NONE,nodes.createNode("p",null,"Default").priority)
        for (priority in Priority.entries) {
            val n=nodes.createNode("p",null,priority.name,creationId=priority.name,priority=priority)
            assertEquals(priority,nodes.getNode(n.id)!!.priority); assertEquals(n,nodes.createNode("p",null,priority.name,creationId=priority.name,priority=priority))
            assertTrue(nodes.setPriority(n.id,Priority.HIGH)); assertEquals(Priority.HIGH,nodes.getNode(n.id)!!.priority)
        }
    }
    @Test fun noteAndLayerLatentPolicyAndNoInheritance()=runBlocking {
        assertTrue(runCatching { nodes.createNode("p",null,"Note",purpose=NodePurpose.NOTE,priority=Priority.HIGH) }.isFailure)
        val n=nodes.createNode("p",null,"Task",priority=Priority.HIGH); nodes.convertPurpose(n.id,NodePurpose.NOTE)
        assertEquals(Priority.HIGH,nodes.getNode(n.id)!!.priority); assertEquals(Priority.NONE,nodes.getNode(n.id)!!.effectivePriority); assertFalse(nodes.setPriority(n.id,Priority.LOW))
        nodes.convertPurpose(n.id,NodePurpose.ACTION); assertEquals(Priority.HIGH,nodes.getNode(n.id)!!.effectivePriority)
        nodes.convertPurpose(n.id,NodePurpose.LAYER)
        val child=nodes.createNode("p",n.id,"Child"); assertEquals(Priority.NONE,child.priority); assertFalse(nodes.setPriority(n.id,Priority.LOW))
        assertEquals(Priority.NONE,nodes.getNode(n.id)!!.effectivePriority); nodes.deleteNode(child.id); assertEquals(Priority.NONE,nodes.getNode(n.id)!!.effectivePriority); nodes.convertPurpose(n.id,NodePurpose.ACTION); assertEquals(Priority.HIGH,nodes.getNode(n.id)!!.effectivePriority)
    }
    @Test fun obligationAndHistoryAreIndependentOfPriority()=runBlocking {
        val money=Obligation(15000,"CLP"); val n=nodes.createNode("p",null,"Bill",obligation=money,priority=Priority.MEDIUM)
        val events=db.nodeEventDao().all(); nodes.setPriority(n.id,Priority.HIGH); nodes.setPriority(n.id,Priority.LOW)
        assertEquals(events,db.nodeEventDao().all()); assertEquals(money,nodes.getNode(n.id)!!.obligation)
        nodes.setCompleted(n.id,true); nodes.setCompleted(n.id,false)
        assertEquals(listOf("REOPENED","COMPLETED","CREATED"),db.nodeEventDao().forNode(n.id).map { it.type })
    }
    @Test fun editorPriorityFailureRollsBackContentAndTags()=runBlocking {
        val n=nodes.createNode("p",null,"Original"); val events=db.nodeEventDao().all()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_priority BEFORE UPDATE OF priority ON nodes BEGIN SELECT RAISE(ABORT,'injected'); END")
        assertTrue(runCatching { nodes.updateEditor(n.id,"Changed","",null,null,null,false,true,emptySet(),Priority.HIGH) }.isFailure)
        assertEquals(n,nodes.getNode(n.id)); assertEquals(events,db.nodeEventDao().all())
    }
    @Test fun recurrenceSnapshotsFutureOnlyAndStatusDoesNotRewriteHistory()=runBlocking {
        val rule=RecurrenceRuleEntity("r","p",null,"Plan","",null,null,RecurrenceSchedule.parse("2026-10-10"),"MONTHLY",1,null,0,"ACTIVE","UTC",0,null,priority="HIGH")
        nodes.recurrence.create(rule); nodes.recurrence.materializeDue(); nodes.recurrence.materializeDue()
        val old=nodes.getProjectNodes("p").single(); assertEquals(Priority.HIGH,old.priority); val oldEvents=db.nodeEventDao().forNode(old.id)
        nodes.recurrence.editTemplate("r","p",null,"Plan","",null,emptySet(),priority=Priority.LOW)
        now=RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2026-11-10"),"UTC",720); nodes.recurrence.materializeDue()
        assertEquals(Priority.LOW,nodes.getProjectNodes("p").single { it.id!=old.id }.priority); assertEquals(Priority.HIGH,nodes.getNode(old.id)!!.priority)
        nodes.recurrence.setStatus("r",RecurrenceStatus.PAUSED); nodes.recurrence.setStatus("r",RecurrenceStatus.FINISHED)
        assertEquals(oldEvents,db.nodeEventDao().forNode(old.id)); assertEquals(2,db.nodeEventDao().all().size)
    }
    @Test fun batchSharedDefaultsAndHighWithRetryAndAtomicFailure()=runBlocking {
        for (priority in listOf(Priority.NONE,Priority.HIGH)) {
            val specs=NodeBatchGenerator.generate(NodeBatchParameters("Batch",5,priority=priority),java.util.TimeZone.getTimeZone("UTC"))
            val made=nodes.createBatch("p",null,priority.name,specs); assertTrue(made.all { it.priority==priority }); assertEquals(made,nodes.createBatch("p",null,priority.name,specs))
        }
        val before=nodes.getProjectNodes("p"); val events=db.nodeEventDao().all()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_batch BEFORE INSERT ON node_events BEGIN SELECT RAISE(ABORT,'injected'); END")
        val specs=listOf(GeneratedNodeSpec("Fail","",NodePurpose.ACTION,null,priority=Priority.HIGH))
        assertTrue(runCatching { nodes.createBatch("p",null,"fail",specs) }.isFailure); assertEquals(before,nodes.getProjectNodes("p")); assertEquals(events,db.nodeEventDao().all())
    }
}
