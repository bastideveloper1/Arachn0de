package com.r0ybt.arachn0de.data

import android.content.Context
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
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
class NodePurposeTest {
    private lateinit var context: Context
    private lateinit var db: Arachn0deDatabase
    private lateinit var nodes: NodeRepository
    private lateinit var people: PersonRepository
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("arachn0de.db")
        open()
    }
    private fun open() {
        db = Arachn0deDatabase.create(context)
        nodes = NodeRepository(db) { 100L }
        people = PersonRepository(db, AvatarStore(context))
    }
    @After fun cleanup() { db.close(); context.deleteDatabase("arachn0de.db") }

    @Test fun conversionRetainsIdentityAndLatentDataAndUpdatesDerivedWork() = runBlocking {
        val project = ProjectRepository(db.projectDao()).createProject("Project").id
        val parent = nodes.createNode(project, null, "Layer")
        val a = nodes.createNode(project, parent.id, "A", "Details", startAt = 1, dueAt = 20)
        val b = nodes.createNode(project, parent.id, "B")
        val note = nodes.createNode(project, parent.id, "Note", purpose = NodePurpose.NOTE)
        assertEquals(NodePurpose.ACTION, a.purpose)
        assertFalse(note.isCompletable)
        nodes.setCompleted(a.id, true)
        assertEquals(50, nodes.calculateProgress(parent.id)!!.percentage)
        assertEquals(2, nodes.calculateProjectProgress(project)!!.total)
        people.save("p", "Person", null)
        people.setResponsiblePeople(a.id, setOf("p"))
        val before = nodes.getNode(a.id)!!
        assertTrue(nodes.convertPurpose(a.id, NodePurpose.NOTE))
        val converted = nodes.getNode(a.id)!!
        assertEquals(before.copy(purpose = NodePurpose.NOTE, isCompleted = false), converted)
        assertFalse(nodes.toggleCompleted(a.id)); assertFalse(nodes.setCompleted(a.id, true))
        assertNull(TaskTemporal.state(converted, 100)); assertNull(TaskTemporal.nextTransition(converted, 0))
        assertFalse(nodes.updateNodeWithDates(a.id, "Lost", "", null, null))
        assertTrue(nodes.updateNode(a.id, "Edited", "Content"))
        assertEquals(20L, nodes.getNode(a.id)!!.dueAt)
        assertEquals("p", people.observeAssignments(project).first().getValue(a.id).single().id)
        nodes.convertPurpose(b.id, NodePurpose.NOTE)
        assertEquals(NodeProgressState.NO_WORK, nodes.calculateProgress(parent.id)!!.state)
        assertEquals(NodeProgressState.NO_WORK, nodes.calculateProjectProgress(project)!!.state)
        assertTrue(nodes.convertPurpose(a.id, NodePurpose.ACTION))
        assertFalse(nodes.getNode(a.id)!!.isCompleted)
        val snapshot = nodes.observeProjectState(project).first()
        assertEquals(1, snapshot.projectProgressById.getValue(project).total)
        assertEquals(listOf(a.id), AttentionSnapshot(snapshot, 100).tasks.map { it.id })
        nodes.convertPurpose(a.id, NodePurpose.NOTE)
        assertTrue(AttentionSnapshot(nodes.observeProjectState(project).first(), 100).tasks.isEmpty())
        assertFalse(nodes.convertPurpose(parent.id, NodePurpose.NOTE))
    }

    @Test fun notesRejectChildrenButMoveReorderDeleteAndCreationRetryKeepIdentity() = runBlocking {
        val project = ProjectRepository(db.projectDao()).createProject("Project").id
        val note = nodes.createNode(project, null, "Note", creationId = "stable", purpose = NodePurpose.NOTE)
        assertEquals(note, nodes.createNode(project, null, "Note", creationId = "stable", purpose = NodePurpose.NOTE))
        try { nodes.createNode(project, null, "Note", creationId = "stable"); fail() } catch (_: IllegalStateException) {}
        val task = nodes.createNode(project, null, "Task")
        try { nodes.createNode(project, note.id, "Child"); fail() } catch (_: IllegalArgumentException) {}
        try { nodes.moveNode(task.id, note.id); fail() } catch (_: IllegalArgumentException) {}
        val parent = nodes.createNode(project, null, "Parent")
        people.save("p", "Person", null); people.setResponsiblePeople(note.id, setOf("p"))
        assertTrue(nodes.reorderNodeTo(note.id, null, task.id))
        assertTrue(nodes.moveNode(note.id, parent.id))
        assertEquals(NodePurpose.NOTE, nodes.getNode(note.id)!!.purpose)
        assertEquals("p", people.observeAssignments(project).first().getValue(note.id).single().id)
        try { db.nodeDao().insert(NodeEntity("illegal", project, note.id, "Child", "", false, 0, 1, 1)); fail() } catch (_: android.database.sqlite.SQLiteConstraintException) {}
        try { db.openHelper.writableDatabase.execSQL("UPDATE nodes SET isCompleted=1 WHERE id='stable'"); fail() } catch (_: android.database.sqlite.SQLiteConstraintException) {}
        nodes.deleteNode(note.id)
        assertNotNull(db.personDao().get("p")); assertTrue(people.observeAssignments(project).first().isEmpty())
        assertEquals(NodePurpose.ACTION, nodes.getNode(parent.id)!!.purpose)
    }

    @Test fun realV6MigrationPreservesAllDataAndDefaultsAllNodesToAction() = runBlocking {
        db.close(); context.deleteDatabase("arachn0de.db")
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db")
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected")
                }).build())
        val sql = helper.writableDatabase
        val schema = JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/6.json")!!.bufferedReader().use { it.readText() }).getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
            sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
            for (j in 0 until indices.length()) sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        sql.execSQL("INSERT INTO projects VALUES ('old','Project','Description',8,100,150)")
        sql.execSQL("INSERT INTO nodes VALUES ('root','old',NULL,'Layer','Content',0,7,100,150,10,20)")
        sql.execSQL("INSERT INTO nodes VALUES ('leaf','old','root','Task','Details',1,9,110,160,11,21)")
        sql.execSQL("INSERT INTO persons VALUES ('p','Person','avatar.png')")
        sql.execSQL("INSERT INTO node_person VALUES ('leaf','p')")
        NodeInvariants.install(sql)
        helper.close()
        open()
        assertEquals(12, db.openHelper.readableDatabase.version)
        assertEquals(NodeEntity("leaf","old","root","Task","Details",true,9,110,160,11,21), db.nodeDao().getById("leaf"))
        assertEquals(NodeEntity("root","old",null,"Layer","Content",false,7,100,150,10,20), db.nodeDao().getById("root"))
        assertEquals(8, db.projectDao().getById("old")!!.position)
        assertEquals("avatar.png", db.personDao().get("p")!!.avatarFile)
        assertEquals("p", people.observeAssignments("old").first().getValue("leaf").single().id)
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }
}
