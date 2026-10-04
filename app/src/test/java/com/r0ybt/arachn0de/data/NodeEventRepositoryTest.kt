package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.*
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [24,28])
class NodeEventRepositoryTest {
    private lateinit var db: Arachn0deDatabase
    private lateinit var nodes: NodeRepository
    private var now = 1001L
    @Before fun setup() = runBlocking {
        val context = RuntimeEnvironment.getApplication(); context.deleteDatabase("arachn0de.db")
        db = Arachn0deDatabase.create(context); nodes = NodeRepository(db) { now }
        db.projectDao().insert(ProjectEntity("p","Project","",0,1,1))
    }
    @After fun close() { db.close(); RuntimeEnvironment.getApplication().deleteDatabase("arachn0de.db") }
    private suspend fun history(id: String) = nodes.observeHistory(id).first()
    private fun failEvent(type: String) = db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_event BEFORE INSERT ON node_events WHEN NEW.type='$type' BEGIN SELECT RAISE(ABORT,'injected event failure'); END")
    @Test fun createdAndRetryExactlyOnceWithActualTimestamp() = runBlocking {
        val node = nodes.createNode("p",null,"Task",creationId="stable")
        now = 9999; assertEquals(node,nodes.createNode("p",null,"Task",creationId="stable"))
        val event = history(node.id).single(); assertEquals(NodeEventType.CREATED,event.type); assertEquals(1001L,event.occurredAt)
        assertEquals(node.createdAt,event.occurredAt); assertEquals(node.id,event.nodeId); assertTrue(event.id.isNotBlank())
    }
    @Test fun exactTransitionSequenceNoOpsAndSameMillisecondOrdering() = runBlocking {
        val node = nodes.createNode("p",null,"Task")
        assertTrue(nodes.setCompleted(node.id,false)); assertEquals(1,history(node.id).size)
        now = 2002; assertTrue(nodes.setCompleted(node.id,true)); assertTrue(nodes.setCompleted(node.id,true))
        assertTrue(nodes.setCompleted(node.id,false)); assertTrue(nodes.setCompleted(node.id,false)); assertTrue(nodes.setCompleted(node.id,true))
        val events = history(node.id)
        assertEquals(listOf(NodeEventType.COMPLETED,NodeEventType.REOPENED,NodeEventType.COMPLETED,NodeEventType.CREATED),events.map { it.type })
        assertEquals(listOf(2002L,2002L,2002L,1001L),events.map { it.occurredAt })
        assertEquals(4,events.map { it.id }.toSet().size); assertTrue(nodes.getNode(node.id)!!.isCompleted)
    }
    @Test fun concurrentIdenticalIntentsAndRetryProduceOneTransition() = runBlocking {
        val node = nodes.createNode("p",null,"Task")
        coroutineScope { (0 until 20).map { async(Dispatchers.IO) { nodes.setCompleted(node.id,true) } }.awaitAll() }
        assertEquals(1,history(node.id).count { it.type == NodeEventType.COMPLETED })
        coroutineScope { (0 until 20).map { async(Dispatchers.IO) { nodes.setCompleted(node.id,false) } }.awaitAll() }
        assertEquals(1,history(node.id).count { it.type == NodeEventType.REOPENED }); assertFalse(nodes.getNode(node.id)!!.isCompleted)
    }
    @Test fun toggleUsesSameAtomicTransitionOperation() = runBlocking {
        val node = nodes.createNode("p",null,"Task"); nodes.toggleCompleted(node.id); nodes.toggleCompleted(node.id)
        assertEquals(listOf(NodeEventType.REOPENED,NodeEventType.COMPLETED,NodeEventType.CREATED),history(node.id).map { it.type })
    }
    @Test fun eventFailureRollsBackStateAndUpdatedAtAndRetry() = runBlocking {
        val node = nodes.createNode("p",null,"Task"); failEvent("COMPLETED"); now = 3003
        assertTrue(runCatching { nodes.setCompleted(node.id,true) }.isFailure)
        assertEquals(node,nodes.getNode(node.id)); assertEquals(1,history(node.id).size)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_event")
        nodes.setCompleted(node.id,true); assertEquals(2,history(node.id).size)
    }
    @Test fun nodeFailureLeavesNoTransitionEvent() = runBlocking {
        val node = nodes.createNode("p",null,"Task")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_node BEFORE UPDATE OF isCompleted ON nodes BEGIN SELECT RAISE(ABORT,'injected node failure'); END")
        assertTrue(runCatching { nodes.setCompleted(node.id,true) }.isFailure)
        assertEquals(node,nodes.getNode(node.id)); assertEquals(listOf(NodeEventType.CREATED),history(node.id).map { it.type })
    }
    @Test fun failedCreatedRollsBackNodeAndParentReopening() = runBlocking {
        val parent = nodes.createNode("p",null,"Parent"); nodes.setCompleted(parent.id,true)
        val before = history(parent.id); failEvent("CREATED")
        assertTrue(runCatching { db.withTransaction { nodes.convertPurpose(parent.id,NodePurpose.LAYER); nodes.createNode("p",parent.id,"Child") } }.isFailure)
        assertTrue(nodes.getNode(parent.id)!!.isCompleted); assertEquals(before,history(parent.id))
        assertEquals(1,nodes.getProjectNodes("p").size)
    }
    @Test fun noteLayerAndMissingNodeCannotCompleteOrReopen() = runBlocking {
        val root = nodes.createNode("p",null,"Root", purpose = com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER); val note = nodes.createNode("p",root.id,"Note",purpose=NodePurpose.NOTE)
        for (id in listOf(root.id,note.id,"missing")) { assertFalse(nodes.setCompleted(id,true)); assertFalse(nodes.setCompleted(id,false)); assertFalse(nodes.toggleCompleted(id)) }
        assertEquals(2,db.nodeEventDao().all().size); assertTrue(db.nodeEventDao().all().all { it.type == "CREATED" })
    }
    @Test fun nonCompletionEditsDoNotCreateEvents() = runBlocking {
        val node = nodes.createNode("p",null,"Task"); val before = history(node.id)
        nodes.updateNode(node.id,"New","Description"); nodes.updateNodeWithDates(node.id,"New","Description",1,2)
        val tag = nodes.tags.create("Tag"); nodes.tags.assignNode(node.id,setOf(tag.id)); nodes.reorderNode(node.id,null,true)
        nodes.convertPurpose(node.id,NodePurpose.NOTE); nodes.convertPurpose(node.id,NodePurpose.ACTION)
        assertEquals(before,history(node.id))
    }
    @Test fun existingCompletionResetsAreRecordedBeforeNoteOrLayerConversion() = runBlocking {
        val note = nodes.createNode("p",null,"Becomes note"); nodes.setCompleted(note.id,true); nodes.convertPurpose(note.id,NodePurpose.NOTE)
        assertEquals(NodeEventType.REOPENED,history(note.id).first().type); assertFalse(nodes.getNode(note.id)!!.isCompleted)
        val parent = nodes.createNode("p",null,"Becomes layer"); nodes.setCompleted(parent.id,true); now=2002; nodes.convertPurpose(parent.id,NodePurpose.LAYER); nodes.createNode("p",parent.id,"Child")
        assertEquals(NodeEventType.REOPENED,history(parent.id).first().type); assertTrue(nodes.getNode(parent.id)!!.hasChildren); assertEquals(2002L,nodes.getNode(parent.id)!!.updatedAt); assertEquals(2002L,history(parent.id).first().occurredAt)
        val target = nodes.createNode("p",null,"Move destination"); nodes.setCompleted(target.id,true)
        nodes.convertPurpose(target.id,NodePurpose.LAYER)
        val child = nodes.createNode("p",null,"Moved"); nodes.moveNode(child.id,target.id)
        assertEquals(NodeEventType.REOPENED,history(target.id).first().type); assertEquals(1,history(child.id).size)
    }
    @Test fun ignoredConversionOrMoveRollsBackImplicitReopening() = runBlocking {
        val target=nodes.createNode("p",null,"Target",purpose=NodePurpose.LAYER)
        val child=nodes.createNode("p",null,"Child"); nodes.setCompleted(child.id,true)
        val targetHistory=history(target.id); val childHistory=history(child.id)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER ignore_move BEFORE UPDATE OF parentId ON nodes BEGIN SELECT RAISE(IGNORE); END")
        assertTrue(runCatching { nodes.moveNode(child.id,target.id) }.isFailure)
        assertFalse(nodes.getNode(target.id)!!.isCompleted); assertEquals(targetHistory,history(target.id)); assertNull(nodes.getNode(child.id)!!.parentId)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER ignore_move")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER ignore_conversion BEFORE UPDATE OF purpose ON nodes BEGIN SELECT RAISE(IGNORE); END")
        assertTrue(runCatching { nodes.convertPurpose(child.id,NodePurpose.NOTE) }.isFailure)
        assertTrue(nodes.getNode(child.id)!!.isCompleted); assertEquals(NodePurpose.ACTION,nodes.getNode(child.id)!!.purpose); assertEquals(childHistory,history(child.id))
    }
    @Test fun obligationPaidReopenedPaidPreservesMoney() = runBlocking {
        val money = Obligation(15000,"CLP"); val node = nodes.createNode("p",null,"Bill",obligation=money)
        nodes.setCompleted(node.id,true); nodes.setCompleted(node.id,false); nodes.setCompleted(node.id,true)
        assertEquals(money,nodes.getNode(node.id)!!.obligation)
        assertEquals(listOf(NodeEventType.COMPLETED,NodeEventType.REOPENED,NodeEventType.COMPLETED,NodeEventType.CREATED),history(node.id).map { it.type })
    }
    @Test fun nodeSubtreeAndProjectDeletionCascadeEventsWithoutOrphans() = runBlocking {
        val root = nodes.createNode("p",null,"Root", purpose = com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER); val child = nodes.createNode("p",root.id,"Child"); nodes.setCompleted(child.id,true)
        val other = nodes.createNode("p",null,"Other"); nodes.deleteNode(root.id)
        assertEquals(listOf(other.id),db.nodeEventDao().all().map { it.nodeId })
        db.projectDao().delete("p"); assertTrue(db.nodeEventDao().all().isEmpty())
        assertTrue(runCatching { db.nodeEventDao().insert(NodeEventEntity("orphan","absent","CREATED",1)) }.isFailure)
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }
    @Test fun queryIsScopedAndOrderedByTimestampThenAppendOrder() = runBlocking {
        val a = nodes.createNode("p",null,"A"); val b = nodes.createNode("p",null,"B")
        now = 3003; nodes.setCompleted(a.id,true); now = 4004; nodes.setCompleted(a.id,false)
        assertEquals(listOf(4004L,3003L,1001L),history(a.id).map { it.occurredAt }); assertEquals(1,history(b.id).size)
    }
    @Test fun batchCreatedAtomicRetriesAndTagsPeoplePreserved() = runBlocking {
        val tag = nodes.tags.create("Tag"); db.personDao().insert(PersonEntity("roy","Roy",null))
        val specs = (1..20).map { GeneratedNodeSpec("Task $it","",NodePurpose.ACTION,null) }
        val batch = nodes.createBatch("p",null,"batch",specs,setOf("roy"),setOf(tag.id))
        assertEquals(20,db.nodeEventDao().all().size)
        nodes.createBatch("p",null,"batch",specs,setOf("roy"),setOf(tag.id)); assertEquals(20,db.nodeEventDao().all().size)
        batch.forEach { assertEquals("CREATED",db.nodeEventDao().forNode(it.id).single().type); assertEquals(listOf(tag.id),db.tagDao().nodeIds(it.id)); assertEquals(listOf("roy"),db.personDao().assignmentIds(it.id)) }
    }
    @Test fun lateBatchEventFailureRollsBackWholeBatch() = runBlocking {
        val specs = (1..20).map { GeneratedNodeSpec("Task $it","",NodePurpose.ACTION,null) }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_batch BEFORE INSERT ON node_events WHEN (SELECT COUNT(*) FROM node_events) = 5 BEGIN SELECT RAISE(ABORT,'injected'); END")
        assertTrue(runCatching { nodes.createBatch("p",null,"batch",specs) }.isFailure)
        assertTrue(nodes.getProjectNodes("p").isEmpty()); assertTrue(db.nodeEventDao().all().isEmpty())
    }
}
