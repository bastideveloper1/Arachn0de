package com.r0ybt.arachn0de.data

import android.content.Context
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AttentionRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: Arachn0deDatabase
    private lateinit var nodes: NodeRepository
    private lateinit var people: PersonRepository
    private lateinit var project: String
    private val now = 100_000L
    @Before fun setup() = runBlocking {
        context = RuntimeEnvironment.getApplication(); context.deleteDatabase("arachn0de.db")
        db = Arachn0deDatabase.create(context)
        nodes = NodeRepository(db) { 200L }
        people = PersonRepository(db, AvatarStore(context))
        project = ProjectRepository(db.projectDao()) { 100L }.createProject("Personal").id
    }
    @After fun cleanup() { db.close(); context.deleteDatabase("arachn0de.db") }
    private suspend fun attention() = AttentionSnapshot(nodes.observeAllState().first(), now)

    @Test fun moveCompletionShapeAndDeletionReactWithoutMutatingDatesOrResponsibilities() = runBlocking {
        val a = nodes.createNode(project, null, "Old branch", purpose = com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val b = nodes.createNode(project, a.id, "Old inner", purpose = com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val target = nodes.createNode(project, null, "New branch", purpose = com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val task = nodes.createNode(project, b.id, "Urgent", dueAt = now - 1)
        people.save("p", "Roy", null); people.setResponsiblePeople(task.id, setOf("p"))
        val before = attention()
        assertEquals(AttentionSummary(0, 1), before.byNodeId.getValue(a.id))
        assertEquals(AttentionSummary(0, 1), before.byNodeId.getValue(b.id))
        val dates = nodes.getNode(task.id)!!.dueAt
        val progress = nodes.calculateProjectProgress(project)
        assertTrue(nodes.moveNode(task.id, target.id))
        val moved = attention()
        assertEquals(AttentionSummary(), moved.byNodeId.getValue(a.id))
        assertEquals(AttentionSummary(), moved.byNodeId.getValue(b.id))
        assertEquals(AttentionSummary(0, 1), moved.byNodeId.getValue(target.id))
        assertEquals(listOf(target.id, task.id), moved.pathTo(task.id).map { it.id })
        assertEquals(progress?.completed, nodes.calculateProjectProgress(project)?.completed)
        assertEquals(dates, nodes.getNode(task.id)!!.dueAt)
        assertEquals("p", people.observeAllAssignments().first().getValue(task.id).single().id)
        nodes.setCompleted(task.id, true)
        assertTrue(attention().tasks.isEmpty())
        nodes.setCompleted(task.id, false)
        assertEquals(listOf(task.id), attention().tasks.map { it.id })
        assertTrue(nodes.convertPurpose(task.id, com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER))
        val child = nodes.createNode(project, task.id, "Child")
        assertTrue(attention().tasks.isEmpty())
        assertEquals(dates, nodes.getNode(task.id)!!.dueAt)
        nodes.deleteNode(child.id)
        assertTrue(nodes.getNode(task.id)!!.isStructural)
        assertTrue(nodes.convertPurpose(task.id, NodePurpose.ACTION))
        assertEquals(listOf(task.id), attention().tasks.map { it.id })
        nodes.deleteNode(task.id)
        assertTrue(attention().tasks.isEmpty())
        assertTrue(people.observeAllAssignments().first().isEmpty())
        assertEquals(1, people.observePeople().first().size)
        assertEquals(16, db.openHelper.readableDatabase.version)
    }

    @Test fun globalProjectionAndAssignmentsObserveMultipleProjectsAndNameChanges() = runBlocking {
        val otherProject = ProjectRepository(db.projectDao()).createProject("Work").id
        val layer = nodes.createNode(project, null, "Layer", purpose = com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val task = nodes.createNode(project, layer.id, "Same title", dueAt = now)
        val other = nodes.createNode(otherProject, null, "Same title", dueAt = now - 1)
        people.save("p", "Roy", null); people.setResponsiblePeople(task.id, setOf("p"))
        people.setResponsiblePeople(other.id, setOf("p"))
        val original = attention()
        assertEquals(listOf(other.id, task.id), original.tasks.map { it.id })
        assertEquals(AttentionSummary(1, 0), original.byProjectId.getValue(project))
        assertEquals(AttentionSummary(0, 1), original.byProjectId.getValue(otherProject))
        people.save("p", "María", null, isNew = false)
        val assignments = people.observeAllAssignments().first()
        assertEquals(setOf(task.id, other.id), assignments.keys)
        assertTrue(assignments.values.all { it.single().name == "María" })
        assertEquals(original.byNodeId, attention().byNodeId)
        nodes.updateNode(layer.id, "Renamed", "")
        assertEquals("Renamed", attention().pathTo(task.id).first().title)
        nodes.updateNodeWithDates(task.id, "Same title", "", null, now + TaskTemporal.UPCOMING_WINDOW_MILLIS + 1)
        assertEquals(listOf(other.id), attention().tasks.map { it.id })
    }

    @Test fun existingGlobalFlowEmitsConfirmedUpdatesWithoutResubscription() = runBlocking {
        val task = nodes.createNode(project, null, "Task", dueAt = now - 1)
        val emissions = Channel<AttentionSnapshot>(Channel.UNLIMITED)
        val observer = launch(Dispatchers.Default) { nodes.observeAllState().collect { emissions.send(AttentionSnapshot(it, now)) } }
        suspend fun awaitTotal(total: Int) = withTimeout(10_000) {
            while (true) {
                val snapshot = emissions.receive()
                if (snapshot.byProjectId.getValue(project).total == total) break
            }
        }
        try {
            awaitTotal(1)
            nodes.setCompleted(task.id, true); awaitTotal(0)
            nodes.setCompleted(task.id, false); awaitTotal(1)
        } finally { observer.cancelAndJoin(); emissions.close() }
    }
}
