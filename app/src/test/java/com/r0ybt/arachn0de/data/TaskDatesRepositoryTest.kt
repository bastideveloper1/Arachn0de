package com.r0ybt.arachn0de.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.TaskTemporal
import com.r0ybt.arachn0de.domain.model.TaskTemporalState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
class TaskDatesRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: Arachn0deDatabase
    private lateinit var nodes: NodeRepository
    private lateinit var people: PersonRepository
    private lateinit var project: String
    @Before fun setup() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("arachn0de.db")
        open()
        project = ProjectRepository(db.projectDao()) { 100L }.createProject("Project").id
    }
    private fun open() {
        db = Arachn0deDatabase.create(context)
        nodes = NodeRepository(db) { 200L }
        people = PersonRepository(db, AvatarStore(context))
    }
    @After fun cleanup() { db.close(); context.deleteDatabase("arachn0de.db") }

    @Test fun datesCreateEditRemovePersistMoveAndKeepPeopleOrderAndProgress() = runBlocking {
        val parent = nodes.createNode(project, null, "Parent", purpose = com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val task = nodes.createNode(project, null, "Task", startAt = 1_000, dueAt = 2_000)
        people.save("p", "Person", null)
        people.setResponsiblePeople(task.id, setOf("p"))
        val before = nodes.getNode(task.id)!!
        val progress = nodes.calculateProjectProgress(project)
        assertTrue(nodes.updateNodeWithDates(task.id, "Edited", "Details", 1_500, 3_000))
        val edited = nodes.getNode(task.id)!!
        assertEquals(1_500L, edited.startAt); assertEquals(3_000L, edited.dueAt)
        assertEquals(before.position, edited.position); assertEquals(before.createdAt, edited.createdAt)
        assertEquals(progress, nodes.calculateProjectProgress(project))
        assertEquals("p", people.observeAssignments(project).first().getValue(task.id).single().id)
        people.setResponsiblePeople(task.id, emptySet())
        assertEquals(edited, nodes.getNode(task.id))
        people.setResponsiblePeople(task.id, setOf("p"))
        assertTrue(nodes.moveNode(task.id, parent.id))
        db.close(); open()
        val moved = nodes.getNode(task.id)!!
        assertEquals(parent.id, moved.parentId); assertEquals(1_500L, moved.startAt); assertEquals(3_000L, moved.dueAt)
        assertEquals("p", people.observeAssignments(project).first().getValue(task.id).single().id)
        assertTrue(nodes.updateNodeWithDates(task.id, "Edited", "", null, 3_000))
        assertNull(nodes.getNode(task.id)!!.startAt)
        assertTrue(nodes.updateNodeWithDates(task.id, "Edited", "", 1_500, null))
        assertNull(nodes.getNode(task.id)!!.dueAt)
        assertTrue(nodes.updateNodeWithDates(task.id, "Edited", "", null, null))
        assertNull(nodes.getNode(task.id)!!.startAt); assertNull(nodes.getNode(task.id)!!.dueAt)
    }

    @Test fun leafLayerLeafRetainsDatesAndContentOnlyEditsDoNotEraseThem() = runBlocking {
        val task = nodes.createNode(project, null, "Task", startAt = 1_000, dueAt = 2_000)
        nodes.setCompleted(task.id, true)
        assertTrue(nodes.convertPurpose(task.id, com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER))
        val child = nodes.createNode(project, task.id, "Child")
        val layer = nodes.getNode(task.id)!!
        assertTrue(layer.hasChildren); assertFalse(layer.isCompleted)
        assertEquals(1_000L, layer.startAt); assertEquals(2_000L, layer.dueAt)
        assertNull(TaskTemporal.state(layer, 3_000))
        assertFalse(nodes.updateNodeWithDates(task.id, "Discard", "", null, null))
        assertTrue(nodes.updateNode(task.id, "Layer", "Content only"))
        assertEquals(2_000L, nodes.getNode(task.id)!!.dueAt)
        nodes.deleteNode(child.id)
        assertTrue(nodes.getNode(task.id)!!.isStructural)
        assertTrue(nodes.convertPurpose(task.id, com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION))
        val leaf = nodes.getNode(task.id)!!
        assertFalse(leaf.hasChildren); assertFalse(leaf.isCompleted)
        assertEquals(TaskTemporalState.OVERDUE, TaskTemporal.state(leaf, 3_000))
        nodes.setCompleted(task.id, true)
        assertEquals(TaskTemporalState.COMPLETED, TaskTemporal.state(nodes.getNode(task.id)!!, 3_000))
        nodes.setCompleted(task.id, false)
        assertEquals(TaskTemporalState.OVERDUE, TaskTemporal.state(nodes.getNode(task.id)!!, 3_000))
    }

    @Test fun invalidDatesNeverWriteAndCreationRetriesIncludeDates() = runBlocking {
        val task = nodes.createNode(project, null, "Task", creationId = "stable", startAt = 1_000, dueAt = 2_000)
        try { nodes.updateNodeWithDates(task.id, "Bad", "Lost", 3_000, 2_000); fail("Invalid edit") } catch (_: IllegalArgumentException) {}
        assertEquals(task, nodes.getNode(task.id))
        try { nodes.createNode(project, null, "Bad", startAt = 3_000, dueAt = 2_000); fail("Invalid create") } catch (_: IllegalArgumentException) {}
        assertEquals(1, nodes.getProjectNodes(project).size)
        assertEquals(task, nodes.createNode(project, null, "Task", creationId = "stable", startAt = 1_000, dueAt = 2_000))
        try { nodes.createNode(project, null, "Task", creationId = "stable", startAt = 1_000, dueAt = 3_000); fail("Changed retry") } catch (_: IllegalStateException) {}
        assertEquals(task, nodes.getNode(task.id))
    }

    @Test fun migratesRealV5KeepingAllTablesRelationsPositionsAndTriggers() = runBlocking {
        db.close(); context.deleteDatabase("arachn0de.db")
        val schema = JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/5.json")!!.bufferedReader().use { it.readText() }).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object : SupportSQLiteOpenHelper.Callback(5) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                    for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
                NodeInvariants.install(db)
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected upgrade")
        }).build())
        helper.writableDatabase.execSQL("INSERT INTO projects VALUES ('old', 'Original', 'Description', 8, 100, 150)")
        helper.writableDatabase.execSQL("INSERT INTO nodes VALUES ('root', 'old', NULL, 'Layer', 'Details', 0, 7, 100, 150)")
        helper.writableDatabase.execSQL("INSERT INTO nodes VALUES ('leaf', 'old', 'root', 'Task', 'Details', 1, 9, 110, 160)")
        helper.writableDatabase.execSQL("INSERT INTO persons VALUES ('p', 'Person', 'original-avatar.png')")
        helper.writableDatabase.execSQL("INSERT INTO node_person VALUES ('leaf', 'p')")
        helper.close(); open()
        assertEquals(27, db.openHelper.readableDatabase.version)
        val original = db.projectDao().getById("old")!!
        assertEquals("Original", original.name); assertEquals("Description", original.description)
        assertEquals(8, original.position); assertEquals(100L, original.createdAt); assertEquals(150L, original.updatedAt)
        assertEquals(NodeEntity("leaf", "old", "root", "Task", "Details", true, 9, 110, 160), db.nodeDao().getById("leaf"))
        assertEquals(NodeEntity("root", "old", null, "Layer", "Details", false, 7, 100, 150, purpose="LAYER"), db.nodeDao().getById("root"))
        assertEquals(PersonEntity("p", "Person", "original-avatar.png"), db.personDao().get("p"))
        assertEquals("p", people.observeAssignments("old").first().getValue("leaf").single().id)
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name LIKE 'nodes_%'").use { assertTrue(it.moveToFirst()); assertEquals(17, it.getInt(0)) }
        assertTrue(nodes.convertPurpose("leaf", com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER))
        val child = nodes.createNode("old", "leaf", "Child")
        assertFalse(nodes.getNode("leaf")!!.isCompleted)
        assertFalse(nodes.setCompleted("leaf", true))
        nodes.deleteNode(child.id)
        assertTrue(nodes.convertPurpose("leaf", com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION))
        assertTrue(nodes.updateNodeWithDates("leaf", "Task", "Details", 1_000, 2_000))
        assertEquals("p", people.observeAssignments("old").first().getValue("leaf").single().id)
    }
}
