package com.r0ybt.arachn0de.data

import android.content.Context
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CalendarRepositoryTest {
    private lateinit var db: Arachn0deDatabase
    private lateinit var context: Context
    private lateinit var nodes: NodeRepository
    private lateinit var projects: ProjectRepository
    private val utc = TimeZone.getTimeZone("UTC")
    @Before fun setup() { context = RuntimeEnvironment.getApplication(); context.deleteDatabase("arachn0de.db"); db = Arachn0deDatabase.create(context); nodes = NodeRepository(db) { 100L }; projects = ProjectRepository(db.projectDao()) { 100L } }
    @After fun cleanup() { db.close(); context.deleteDatabase("arachn0de.db") }
    private suspend fun calendar() = CalendarSnapshot(nodes.observeAllState().first(), utc)
    private fun CalendarSnapshot.ids() = tasksByDay.values.flatten().map { it.id }.toSet()

    @Test fun purposeStructureCompletionDatesMovesAndDeletionUseSameRowsAndPreserveLatentDates() = runBlocking {
        val project = projects.createProject("Project")
        val parent = nodes.createNode(project.id,null,"Parent")
        val task = nodes.createNode(project.id,parent.id,"Task",dueAt=200)
        assertEquals(setOf(task.id),calendar().ids())
        nodes.setCompleted(task.id,true)
        assertTrue(calendar().tasksByDay.values.flatten().single().isCompleted)
        nodes.setCompleted(task.id,false)
        nodes.convertPurpose(task.id,NodePurpose.NOTE); assertTrue(calendar().ids().isEmpty())
        assertEquals(200L,nodes.getNode(task.id)!!.dueAt)
        nodes.convertPurpose(task.id,NodePurpose.ACTION); assertEquals(setOf(task.id),calendar().ids())
        val child = nodes.createNode(project.id,task.id,"Child")
        assertTrue(calendar().ids().isEmpty())
        nodes.deleteNode(child.id); assertEquals(setOf(task.id),calendar().ids())
        nodes.moveNode(task.id,null)
        assertEquals(listOf(task.id),calendar().pathTo(task.id).map { it.id })
        nodes.updateNodeWithDates(task.id,"Task","",null,null); assertTrue(calendar().ids().isEmpty())
        nodes.updateNodeWithDates(task.id,"Task","",null,300); assertEquals(300L,calendar().tasksByDay.values.flatten().single().dueAt)
        nodes.deleteNode(task.id); assertTrue(calendar().ids().isEmpty())
        assertEquals(9,db.openHelper.readableDatabase.version)
    }

    @Test fun datedBatchProducesIndependentCalendarEntriesAndProjectDeletionRemovesThem() = runBlocking {
        val a = projects.createProject("A"); val b = projects.createProject("B")
        val specs = NodeBatchGenerator.generate(NodeBatchParameters("Day",3,temporalRule=BatchTemporalRule.DAILY,firstDueAt=100),utc)
        val batch = nodes.createBatch(a.id,null,"batch",specs)
        val other = nodes.createNode(b.id,null,"Other",dueAt=100)
        val snapshot = calendar()
        assertEquals(batch.map { it.id }.toSet()+other.id,snapshot.ids())
        assertEquals(listOf(100L,86_400_100L,172_800_100L),batch.map { it.dueAt })
        assertEquals(3,snapshot.tasksByDay.size)
        assertEquals(listOf(0,1,2),nodes.getProjectNodes(a.id).map { it.position })
        projects.deleteProject(a.id)
        assertEquals(setOf(other.id),calendar().ids())
        assertEquals(100L,nodes.getNode(other.id)!!.dueAt)
    }
}
