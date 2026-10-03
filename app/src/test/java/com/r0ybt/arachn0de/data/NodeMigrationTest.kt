package com.r0ybt.arachn0de.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.local.NodeEntity
import com.r0ybt.arachn0de.data.repository.NodeRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
class NodeMigrationTest {
    private lateinit var context: Context
    private var database: Arachn0deDatabase? = null

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("arachn0de.db")
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase("arachn0de.db")
    }

    @Test
    fun migratesVersionOneThroughTwoToThreeWithoutLosingProjects() = runBlocking {
        legacyDatabase(1).use { insertProject(it, "project") }
        val db = openCurrent()
        val project = db.projectDao().getById("project")!!
        assertEquals("Project project", project.name)
        assertEquals("Original description", project.description)
        assertEquals(100L, project.createdAt)
        assertEquals(200L, project.updatedAt)
        val repo = NodeRepository(db)
        val root = repo.createNode("project", null, "Root")
        repo.createNode("project", root.id, "Child")
        assertFalse(repo.setCompleted(root.id, true))
        assertEquals(12, db.openHelper.readableDatabase.version)
    }

    @Test
    fun migratesVersionTwoPreservingContentAndNormalizingStructureAndCompletion() = runBlocking {
        legacyDatabase(2).use { old ->
            insertProject(old, "project")
            insertNode(old, "container", null, structural = false, completed = true, position = 4)
            insertNode(old, "child", "container", structural = false, completed = true, position = 7)
            insertNode(old, "empty", null, structural = true, position = 12)
        }
        val db = openCurrent() // Opening Room validates the complete migrated schema.
        val repo = NodeRepository(db)
        val container = repo.getNode("container")!!
        val child = repo.getNode("child")!!
        val empty = repo.getNode("empty")!!
        assertTrue(container.hasChildren)
        assertFalse(container.isCompleted)
        assertTrue(child.isCompleted)
        assertTrue(empty.isCompletable)
        assertFalse(empty.isCompleted)
        assertEquals("Title child", child.title)
        assertEquals("Description child", child.description)
        assertEquals("project", child.projectId)
        assertEquals("container", child.parentId)
        assertEquals(7, child.position)
        assertEquals(100L, child.createdAt)
        assertEquals(200L, child.updatedAt)
        assertEquals(100, repo.calculateProgress("container")!!.percentage)
        assertEquals(listOf(4, 7, 12), repo.getProjectNodes("project").map { it.position })
        val columns = mutableSetOf<String>()
        db.openHelper.readableDatabase.query("PRAGMA table_info(nodes)").use { c ->
            while (c.moveToNext()) columns.add(c.getString(c.getColumnIndexOrThrow("name")))
        }
        assertFalse("isStructural" in columns)
        assertFalse("isCompletable" in columns)
        assertForeignKeysAndTriggers(db)
        db.close()
        database = null
        val reopened = NodeRepository(openCurrent())
        assertEquals(container, reopened.getNode(container.id))
        assertEquals(child, reopened.getNode(child.id))
        assertTrue(reopened.deleteNode(child.id))
        assertTrue(reopened.getNode(container.id)!!.isCompletable)
        assertFalse(reopened.getNode(container.id)!!.isCompleted)
    }

    @Test
    fun repairsLegacyCyclesCrossProjectAndMissingParentLinksWithoutDeletingNodes() = runBlocking {
        legacyDatabase(2).use { old ->
            insertProject(old, "project")
            insertProject(old, "other")
            insertNode(old, "a", null)
            insertNode(old, "b", "a")
            old.execSQL("UPDATE nodes SET parentId = 'b' WHERE id = 'a'")
            insertNode(old, "self", null)
            old.execSQL("UPDATE nodes SET parentId = 'self' WHERE id = 'self'")
            insertNode(old, "cross", "b", projectId = "other")
            insertNode(old, "valid-leaf", null, structural = false, completed = true)
            insertNode(old, "cross-to-leaf", "valid-leaf", projectId = "other")
            old.setForeignKeyConstraintsEnabled(false)
            insertNode(old, "missing-parent", "absent")
        }
        val db = openCurrent()
        val repo = NodeRepository(db)
        assertNull(repo.getNode("a")!!.parentId)
        assertEquals("a", repo.getNode("b")!!.parentId)
        assertNull(repo.getNode("self")!!.parentId)
        assertNull(repo.getNode("cross")!!.parentId)
        assertNull(repo.getNode("missing-parent")!!.parentId)
        assertTrue(repo.getNode("valid-leaf")!!.isCompleted)
        assertNull(repo.getNode("cross-to-leaf")!!.parentId)
        assertEquals(5, repo.getProjectNodes("project").size)
        assertEquals(2, repo.getProjectNodes("other").size)
        assertForeignKeysAndTriggers(db)
    }

    @Test
    fun failedMigrationRollsBackWithoutDestroyingLegacyData() {
        legacyDatabase(2).use { old ->
            old.setForeignKeyConstraintsEnabled(false)
            insertNode(old, "orphan", null, projectId = "missing-project")
        }
        try {
            openCurrent().openHelper.writableDatabase
            fail("Expected a missing project to block migration")
        } catch (_: IllegalStateException) {
            database?.close()
            database = null
        }
        context.openOrCreateDatabase("arachn0de.db", Context.MODE_PRIVATE, null).use { old ->
            assertEquals(2, old.version)
            old.rawQuery("SELECT id, title, isStructural FROM nodes", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("orphan", c.getString(0))
                assertEquals("Title orphan", c.getString(1))
                assertEquals(1, c.getInt(2))
            }
        }
    }

    private suspend fun assertForeignKeysAndTriggers(db: Arachn0deDatabase) {
        val sql = db.openHelper.writableDatabase
        sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        sql.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'nodes_%'").use {
            assertTrue(it.moveToFirst())
            assertEquals(15, it.getInt(0))
        }
        val repo = NodeRepository(db)
        val root = repo.createNode("project", null, "SQL guard root")
        repo.setCompleted(root.id, true)
        val child = repo.createNode("project", root.id, "SQL guard child")
        assertFalse(repo.getNode(root.id)!!.isCompleted)
        try {
            sql.execSQL("UPDATE nodes SET parentId = ? WHERE id = ?", arrayOf<Any?>(child.id, root.id))
            fail("Expected cycle rejection after migration")
        } catch (_: SQLiteConstraintException) { }
        db.projectDao().getById("other") ?: sql.execSQL(
            "INSERT INTO projects (id, name, description, position, createdAt, updatedAt) VALUES ('other', 'Other', '', 0, 0, 0)",
        )
        try {
            db.nodeDao().insert(NodeEntity("invalid", "other", root.id, "Invalid", "", false, 0, 0, 0))
            fail("Expected composite foreign key rejection after migration")
        } catch (_: SQLiteConstraintException) { }
        repo.deleteNode(root.id)
    }

    private fun openCurrent(): Arachn0deDatabase = Arachn0deDatabase.create(context).also { database = it }

    private fun legacyDatabase(version: Int): SQLiteDatabase {
        val path = "com.r0ybt.arachn0de.data.local.Arachn0deDatabase/$version.json"
        val schema = javaClass.classLoader!!.getResourceAsStream(path)!!.bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("database")
        }
        val db = context.openOrCreateDatabase("arachn0de.db", Context.MODE_PRIVATE, null)
        db.setForeignKeyConstraintsEnabled(true)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            val indices = entity.optJSONArray("indices")
            if (indices != null) for (j in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = version
        return db
    }

    private fun insertProject(db: SQLiteDatabase, id: String) {
        db.execSQL("INSERT INTO projects VALUES (?, ?, ?, 100, 200)", arrayOf<Any?>(id, "Project $id", "Original description"))
    }

    private fun insertNode(
        db: SQLiteDatabase, id: String, parentId: String?, projectId: String = "project",
        structural: Boolean = true, completed: Boolean = false, position: Int = 0,
    ) {
        db.execSQL(
            "INSERT INTO nodes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 100, 200)",
            arrayOf<Any?>(id, projectId, parentId, "Title $id", "Description $id",
                if (structural) 1 else 0, if (structural) 0 else 1, if (completed) 1 else 0, position),
        )
    }
}
