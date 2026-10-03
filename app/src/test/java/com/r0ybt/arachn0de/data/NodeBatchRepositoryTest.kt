package com.r0ybt.arachn0de.data

import android.content.Context
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
class NodeBatchRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: Arachn0deDatabase
    private lateinit var nodes: NodeRepository
    private lateinit var people: PersonRepository
    private lateinit var project: String
    @Before fun setup() = runBlocking {
        context = RuntimeEnvironment.getApplication(); context.deleteDatabase("arachn0de.db")
        open()
        project = ProjectRepository(db.projectDao()) { 100L }.createProject("Project").id
    }
    private fun open() { db = Arachn0deDatabase.create(context); nodes = NodeRepository(db) { 200L }; people = PersonRepository(db, AvatarStore(context)) }
    @After fun cleanup() { db.close(); context.deleteDatabase("arachn0de.db") }
    private fun specs(quantity: Int = 3, purpose: NodePurpose = NodePurpose.ACTION) = NodeBatchGenerator.generate(
        NodeBatchParameters("Episode", quantity, NumberingMode.SUFFIX, 4, purpose, "Details",
            if (purpose == NodePurpose.ACTION) BatchTemporalRule.DAILY else BatchTemporalRule.NONE, 100L), TimeZone.getTimeZone("UTC"))

    @Test fun exactPreviewSpecificationsAppendAsSiblingsAndFeedProgressAttentionAndPeople() = runBlocking {
        val parent = nodes.createNode(project, null, "Layer")
        val previous = nodes.createNode(project, parent.id, "Existing")
        val completed = nodes.createNode(project, parent.id, "Done"); nodes.setCompleted(completed.id, true)
        val before = nodes.getNode(previous.id)
        people.save("p", "Person", null); people.save("q", "Other", null)
        val preview = specs()
        val result = nodes.createBatch(project, parent.id, "stable", preview, setOf("p", "q"))
        assertEquals(preview.map { it.title }, result.map { it.title })
        assertEquals(preview.map { it.dueAt }, result.map { it.dueAt })
        assertTrue(result.all { it.description == "Details" && it.parentId == parent.id && it.startAt == null && !it.isCompleted && it.isCompletable })
        assertEquals(listOf(2,3,4), result.map { it.position })
        assertEquals(before, nodes.getNode(previous.id))
        val snapshot = nodes.observeProjectState(project).first()
        assertEquals(listOf(previous.id) + result.map { it.id } + completed.id, snapshot.childrenOf(parent.id).map { it.id })
        assertEquals(5, snapshot.progressById.getValue(parent.id).total)
        assertEquals(1, snapshot.progressById.getValue(parent.id).completed)
        assertEquals(1, AttentionSnapshot(snapshot, 200).byProjectId.getValue(project).overdue)
        assertEquals(1, AttentionSnapshot(snapshot, 200).byProjectId.getValue(project).upcoming)
        result.forEach { assertEquals(setOf("p", "q"), people.observeAssignments(project).first().getValue(it.id).map { p -> p.id }.toSet()) }
        assertEquals(11, db.openHelper.readableDatabase.version)
        val notes = nodes.createBatch(project, parent.id, "notes", specs(purpose = NodePurpose.NOTE))
        assertTrue(notes.all { !it.isCompletable && it.dueAt == null })
        assertEquals(5, nodes.calculateProjectProgress(project)!!.total)
    }

    @Test fun nodeAndAssignmentFailuresRollBackEntireBatchAndRetryDoesNotDuplicate() = runBlocking {
        people.save("p", "Person", null)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_batch BEFORE INSERT ON nodes WHEN NEW.title='Episode 5' BEGIN SELECT RAISE(ABORT,'fail'); END")
        try { nodes.createBatch(project, null, "retry", specs(), setOf("p")); fail() } catch (_: android.database.sqlite.SQLiteConstraintException) {}
        assertTrue(nodes.getProjectNodes(project).isEmpty()); assertTrue(people.observeAssignments(project).first().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_batch")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_assignment BEFORE INSERT ON node_person WHEN (SELECT title FROM nodes WHERE id=NEW.nodeId)='Episode 5' BEGIN SELECT RAISE(ABORT,'fail'); END")
        try { nodes.createBatch(project, null, "retry", specs(), setOf("p")); fail() } catch (_: android.database.sqlite.SQLiteConstraintException) {}
        assertTrue(nodes.getProjectNodes(project).isEmpty()); assertTrue(people.observeAssignments(project).first().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_assignment")
        val expected = nodes.createBatch(project, null, "retry", specs(), setOf("p"))
        db.close(); open()
        val simultaneous = coroutineScope { (1..2).map { async { nodes.createBatch(project, null, "retry", specs(), setOf("p")) } }.awaitAll() }
        assertTrue(simultaneous.all { it == expected }); assertEquals(3, nodes.getProjectNodes(project).size)
        try { nodes.createBatch(project, null, "retry", specs(2), setOf("p")); fail() } catch (_: IllegalStateException) {}
        try { nodes.createBatch(project, null, "retry", specs().map { it.copy(description = "Changed") }, setOf("p")); fail() } catch (_: IllegalStateException) {}
        assertEquals(expected, nodes.getProjectNodes(project))
    }

    @Test fun destinationPersonsValidationAndPositionSaturationAreTransactional() = runBlocking {
        val note = nodes.createNode(project, null, "Note", purpose = NodePurpose.NOTE)
        try { nodes.createBatch(project, note.id, "bad", specs()); fail() } catch (_: IllegalArgumentException) {}
        try { nodes.createBatch(project, "missing", "bad", specs()); fail() } catch (_: IllegalArgumentException) {}
        try { nodes.createBatch(project, null, "bad", specs(), setOf("missing")); fail() } catch (_: IllegalArgumentException) {}
        assertEquals(listOf(note), nodes.getProjectNodes(project))
        db.nodeDao().updateOrder(note.id, Int.MAX_VALUE - 1, note.updatedAt)
        val result = nodes.createBatch(project, null, "saturation", specs())
        assertEquals(listOf(1,2,3), result.map { it.position }); assertEquals(0, nodes.getNode(note.id)!!.position)
        assertEquals(note.updatedAt, nodes.getNode(note.id)!!.updatedAt)
        try { nodes.createBatch(project, null, "oversize", List(501) { specs(1).single() }); fail() } catch (_: IllegalArgumentException) {}
        assertEquals(4, nodes.getProjectNodes(project).size)
    }
}
