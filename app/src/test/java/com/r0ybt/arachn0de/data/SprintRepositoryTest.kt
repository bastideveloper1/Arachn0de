package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.export.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[24,28])
class SprintRepositoryTest {
    private lateinit var db: Arachn0deDatabase
    private lateinit var nodes: NodeRepository
    private var clock = 1000L
    @Before fun setup() = runBlocking {
        val context=RuntimeEnvironment.getApplication();context.deleteDatabase("arachn0de.db")
        db=Arachn0deDatabase.create(context);nodes=NodeRepository(db) { clock++ }
        db.projectDao().insert(ProjectEntity("p","P","",0,1,1))
    }
    @After fun close() { db.close() }
    private suspend fun layer(name: String="Sprint", parent: String?=null, sprint: Boolean=true): Node {
        val node=nodes.createNode("p",parent,name,purpose=NodePurpose.LAYER)
        if(sprint) assertTrue(nodes.setSprintMode(node.id,true))
        return nodes.getNode(node.id)!!
    }
    private suspend fun state(id: String)=nodes.getNode(id)!!.workState
    @Test fun emptyAndExistingLayersActivateDirectChildrenAndNormalizeOnDisable()=runBlocking {
        val root=layer(sprint=false)
        assertEquals(NodeProgressState.NO_WORK,nodes.calculateProgress(root.id)!!.state)
        val pending=nodes.createNode("p",root.id,"Pending")
        val complete=nodes.createNode("p",root.id,"Complete");nodes.setCompleted(complete.id,true)
        val note=nodes.createNode("p",root.id,"Note",purpose=NodePurpose.NOTE)
        val inner=layer("Inner",root.id,false);val nested=nodes.createNode("p",inner.id,"Nested")
        val history=db.nodeEventDao().all()
        assertTrue(nodes.setSprintMode(root.id,true))
        assertEquals(WorkState.UNPLANNED,state(pending.id));assertEquals(WorkState.DONE,state(complete.id))
        assertNull(state(note.id));assertNull(state(inner.id));assertNull(state(nested.id));assertFalse(nodes.getNode(inner.id)!!.sprintMode)
        assertEquals(history,db.nodeEventDao().all())
        nodes.setWorkState(pending.id,WorkState.DOING);nodes.setWorkState(complete.id,WorkState.VALIDATED)
        nodes.setSprintMode(root.id,false)
        assertNull(state(pending.id));assertFalse(nodes.getNode(pending.id)!!.isCompleted)
        assertNull(state(complete.id));assertTrue(nodes.getNode(complete.id)!!.isCompleted)
        nodes.setSprintMode(root.id,true);assertEquals(WorkState.UNPLANNED,state(pending.id));assertEquals(WorkState.DONE,state(complete.id))
        val empty=layer("Empty");assertTrue(empty.sprintMode);assertEquals(NodeProgressState.NO_WORK,nodes.calculateProgress(empty.id)!!.state)
    }
    @Test fun everyPhaseAndCompletionAreIndependent()=runBlocking {
        val root=layer();val task=nodes.createNode("p",root.id,"Task")
        for (phase in WorkState.entries) {
            assertTrue(nodes.setWorkState(task.id,phase))
            assertFalse(nodes.getNode(task.id)!!.isCompleted)
            assertTrue(nodes.setCompleted(task.id,true));assertEquals(phase,state(task.id))
            for (next in WorkState.entries) {
                assertTrue(nodes.setWorkState(task.id,next))
                assertTrue(nodes.getNode(task.id)!!.isCompleted)
            }
            nodes.setWorkState(task.id,phase)
            assertTrue(nodes.toggleCompleted(task.id));assertEquals(phase,state(task.id))
            assertFalse(nodes.getNode(task.id)!!.isCompleted)
        }
        assertEquals(5,db.nodeEventDao().forNode(task.id).count { it.type=="COMPLETED" })
        assertEquals(5,db.nodeEventDao().forNode(task.id).count { it.type=="REOPENED" })
        nodes.setWorkState(task.id,WorkState.UNPLANNED)
        for(next in WorkState.entries.drop(1)) {
            nodes.advanceWorkState(task.id);assertEquals(next,state(task.id))
            assertFalse(nodes.getNode(task.id)!!.isCompleted)
        }
        val before=nodes.getNode(task.id)!!;nodes.advanceWorkState(task.id);assertEquals(before,nodes.getNode(task.id))
    }
    @Test fun confirmingSprintPhasePreservesCompletionBoundaryAndRollsBackOnFailure()=runBlocking {
        val root=layer();val task=nodes.createNode("p",root.id,"Task")
        nodes.confirmWorkState(task.id);nodes.confirmWorkState(task.id)
        assertEquals(WorkState.DOING,state(task.id));assertFalse(nodes.getNode(task.id)!!.isCompleted)
        val before=nodes.getNode(task.id)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_event BEFORE INSERT ON node_events BEGIN SELECT RAISE(ABORT,'fail'); END")
        assertTrue(runCatching { nodes.confirmWorkState(task.id) }.isFailure)
        assertEquals(before,nodes.getNode(task.id))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_event")
        nodes.confirmWorkState(task.id)
        assertEquals(WorkState.DONE,state(task.id));assertTrue(nodes.getNode(task.id)!!.isCompleted)
        nodes.confirmWorkState(task.id)
        assertEquals(WorkState.VALIDATED,state(task.id));assertTrue(nodes.getNode(task.id)!!.isCompleted)
        val final=nodes.getNode(task.id)
        nodes.confirmWorkState(task.id);assertEquals(final,nodes.getNode(task.id))
        assertEquals(1,db.nodeEventDao().forNode(task.id).count { it.type=="COMPLETED" })
        val normal=nodes.createNode("p",null,"Normal")
        assertFalse(nodes.confirmWorkState(normal.id));assertFalse(nodes.getNode(normal.id)!!.isCompleted)
    }
    @Test fun normalTasksRemainBinaryAndSqlKeepsContextGuards()=runBlocking {
        val normal=layer(sprint=false);val task=nodes.createNode("p",normal.id,"Simple")
        assertNull(task.workState);assertTrue(nodes.toggleCompleted(task.id));assertTrue(nodes.getNode(task.id)!!.isCompleted)
        assertFalse(nodes.advanceWorkState(task.id));assertFalse(nodes.setWorkState(task.id,WorkState.DOING))
        val sprint=layer();val child=nodes.createNode("p",sprint.id,"Sprint task")
        db.openHelper.writableDatabase.execSQL("UPDATE nodes SET isCompleted=1 WHERE id=?",arrayOf(child.id))
        db.openHelper.writableDatabase.execSQL("UPDATE nodes SET workState='DOING' WHERE id=?",arrayOf(child.id))
        for(sql in listOf("UPDATE nodes SET sprintMode=1 WHERE id=?", "UPDATE nodes SET workState='UNKNOWN' WHERE id=?"))
            assertTrue(runCatching { db.openHelper.writableDatabase.execSQL(sql,arrayOf(child.id)) }.isFailure)
        val note=nodes.createNode("p",sprint.id,"Note",purpose=NodePurpose.NOTE)
        assertFalse(nodes.setSprintMode(note.id,true));assertFalse(nodes.setWorkState(note.id,WorkState.PLANNED))
        assertTrue(runCatching { db.openHelper.writableDatabase.execSQL("UPDATE nodes SET workState='UNPLANNED' WHERE id=?",arrayOf(note.id)) }.isFailure)
    }
    @Test fun completionProgressAttentionCalendarAndFiltersShareSameTruth()=runBlocking {
        val root=layer();val tasks=WorkState.entries.map { state ->
            val node=nodes.createNode("p",root.id,state.name,dueAt=10,priority=Priority.HIGH)
            nodes.setWorkState(node.id,state);nodes.setCompleted(node.id,state.completed);nodes.getNode(node.id)!!
        }
        val tree=NodeTreeSnapshot(nodes.getProjectNodes("p"));val progress=tree.progressById.getValue(root.id)
        assertEquals(2,progress.completed);assertEquals(5,progress.total);assertEquals(NodeProgressState.PARTIAL,progress.state)
        assertEquals(tasks.take(3).map { it.id }.toSet(),AttentionSnapshot(tree,1000).tasks.map { it.id }.toSet())
        val calendar=CalendarSnapshot(tree,java.util.TimeZone.getTimeZone("UTC"))
        assertEquals(5,calendar.tasksByDay.values.flatten().size)
        assertEquals(3,NodeFilter(completion=CompletionFilter.PENDING).apply(tasks,emptyMap()).size)
        assertEquals(2,FilteredCalendar(calendar,NodeFilter(completion=CompletionFilter.COMPLETED),emptyMap()).tasks.size)
        val copied = NodeMarkdownRenderer.render(NodeExportSnapshot.capture(tree,tasks.last().id,false))
        assertTrue(copied.contains("[x]"));assertTrue(copied.contains("Validada"))
        assertTrue(NodeMarkdownRenderer.render(NodeExportSnapshot.capture(tree,root.id,true)).contains("Modo Sprint"))
        assertTrue(PendingChatRenderer.render(tree,root.id).contains("Estado: Haciendo"))
        assertFalse(PendingChatRenderer.render(tree,root.id).contains("VALIDATED"))
    }
    @Test fun movesAndBulkMovesNormalizeOnlyActionsKeepLayersIndependent()=runBlocking {
        val normal=layer("Normal",sprint=false);val a=layer("A");val b=layer("B")
        val pending=nodes.createNode("p",normal.id,"Pending");val done=nodes.createNode("p",normal.id,"Done");nodes.setCompleted(done.id,true)
        nodes.moveSelected("p",setOf(pending.id,done.id),a.id)
        assertEquals(WorkState.UNPLANNED,state(pending.id));assertEquals(WorkState.DONE,state(done.id))
        nodes.setWorkState(pending.id,WorkState.DOING);nodes.setWorkState(done.id,WorkState.VALIDATED)
        nodes.moveSelected("p",setOf(pending.id,done.id),b.id)
        assertEquals(WorkState.DOING,state(pending.id));assertEquals(WorkState.VALIDATED,state(done.id))
        nodes.moveSelected("p",setOf(pending.id,done.id),normal.id)
        assertNull(state(pending.id));assertNull(state(done.id));assertTrue(nodes.getNode(done.id)!!.isCompleted)
        nodes.moveNode(a.id,b.id);assertTrue(nodes.getNode(a.id)!!.sprintMode)
        val note=nodes.createNode("p",normal.id,"Note",purpose=NodePurpose.NOTE);nodes.moveNode(note.id,b.id);assertNull(state(note.id))
    }
    @Test fun creationBatchRecurrenceAndUndoUseUnplanned()=runBlocking {
        val root=layer();val specs=listOf(GeneratedNodeSpec("One","",NodePurpose.ACTION,null,null,Priority.NONE),GeneratedNodeSpec("Note","",NodePurpose.NOTE,null,null,Priority.NONE))
        val batch=nodes.createBatch("p",root.id,"batch",specs)
        assertEquals(WorkState.UNPLANNED,batch[0].workState);assertNull(batch[1].workState)
        val token=nodes.captureCreation(batch.map { it.id });nodes.advanceWorkState(batch[0].id);assertFalse(nodes.undoCreation(token))
        val simple=nodes.createNode("p",root.id,"Undo");assertTrue(nodes.undoCreation(nodes.captureCreation(listOf(simple.id))))
        val rule=RecurrenceRuleEntity("rule","p",root.id,"Occurrence","",null,null,0,"DAILY",1,null,0,"ACTIVE","UTC",0,null)
        nodes.recurrence.create(rule);nodes.recurrence.materializeBatch(0)
        val occurrence=nodes.getProjectNodes("p").single { it.title=="Occurrence" }
        assertEquals(WorkState.UNPLANNED,occurrence.workState);assertEquals(1L,db.recurrenceDao().get("rule")!!.nextIndex)
        nodes.recurrence.materializeBatch(0);assertEquals(1,db.recurrenceDao().occurrences().size)
    }
    @Test fun failedEventAndFailedModeSwitchRollBackEveryWrite()=runBlocking {
        val root=layer();val task=nodes.createNode("p",root.id,"Task");nodes.setWorkState(task.id,WorkState.DOING)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_event BEFORE INSERT ON node_events BEGIN SELECT RAISE(ABORT,'fail'); END")
        assertTrue(nodes.setWorkState(task.id,WorkState.DONE))
        nodes.setWorkState(task.id,WorkState.DOING)
        val before=nodes.getNode(task.id)
        assertTrue(runCatching { nodes.setCompleted(task.id,true) }.isFailure);assertEquals(before,nodes.getNode(task.id))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_event")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_mode BEFORE UPDATE OF sprintMode ON nodes BEGIN SELECT RAISE(ABORT,'fail'); END")
        assertTrue(runCatching { nodes.setSprintMode(root.id,false) }.isFailure);assertEquals(before,nodes.getNode(task.id));assertTrue(nodes.getNode(root.id)!!.sprintMode)
    }
    @Test fun conversionsAndProjectTransferPreserveActiveContexts()=runBlocking {
        val root=layer();val task=nodes.createNode("p",root.id,"Task");nodes.setWorkState(task.id,WorkState.VALIDATED)
        nodes.convertPurpose(task.id,NodePurpose.NOTE);assertNull(state(task.id))
        nodes.convertPurpose(task.id,NodePurpose.ACTION);assertEquals(WorkState.UNPLANNED,state(task.id))
        val empty=layer("Empty");nodes.convertPurpose(empty.id,NodePurpose.ACTION);assertFalse(nodes.getNode(empty.id)!!.sprintMode)
        db.projectDao().insert(ProjectEntity("target","Target","",1,1,1))
        val wrapper=ProjectNestingRepository(db).move("p","target",null)
        assertFalse(nodes.getNode(wrapper)!!.sprintMode);assertTrue(nodes.getNode(root.id)!!.sprintMode)
        assertEquals(WorkState.UNPLANNED,state(task.id));assertNull(state(empty.id))
    }
}
