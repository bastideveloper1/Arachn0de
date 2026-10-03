package com.r0ybt.arachn0de.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersonRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: Arachn0deDatabase
    private lateinit var people: PersonRepository
    private lateinit var nodes: NodeRepository
    private lateinit var project: String
    @Before fun setup() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("arachn0de.db")
        open()
        project = ProjectRepository(db.projectDao()).createProject("Project").id
    }
    private fun open() {
        db = Arachn0deDatabase.create(context)
        people = PersonRepository(db, AvatarStore(context))
        nodes = NodeRepository(db)
    }
    @After fun cleanup() { db.close(); context.deleteDatabase("arachn0de.db"); File(context.filesDir, "avatars").deleteRecursively() }

    @Test fun crudMultipleAssignmentsRenameMoveAndPersistence() = runBlocking {
        assertTrue(people.save("roy", " Roy ", null))
        assertTrue(people.save("juan", "Juan", null))
        try { people.save("blank", "  ", null); fail("Blank name") } catch (_: IllegalArgumentException) {}
        val layer = nodes.createNode(project, null, "Layer")
        val task = nodes.createNode(project, null, "Task")
        assertTrue(people.setResponsiblePeople(layer.id, setOf("roy")))
        assertTrue(people.setResponsiblePeople(task.id, setOf("roy", "juan")))
        val progressBefore = nodes.calculateProgress(task.id)
        assertTrue(people.save("roy", "María", null, isNew = false))
        assertEquals("María", people.observeAssignments(project).first().getValue(layer.id).single().name)
        assertEquals(2, people.observeAssignments(project).first().getValue(task.id).size)
        assertEquals(progressBefore, nodes.calculateProgress(task.id))
        assertTrue(nodes.moveNode(task.id, layer.id))
        assertEquals(setOf("roy", "juan"), people.observeAssignments(project).first().getValue(task.id).map { it.id }.toSet())
        db.close(); open()
        assertEquals(2, people.observePeople().first().size)
        assertEquals(layer.id, nodes.getNode(task.id)!!.parentId)
        assertEquals(2, people.observeAssignments(project).first().getValue(task.id).size)
        assertTrue(people.setResponsiblePeople(task.id, emptySet()))
        assertFalse(task.id in people.observeAssignments(project).first())
        assertEquals(listOf("roy"), people.observeAssignments(project).first().getValue(layer.id).map { it.id })
    }

    @Test fun deletionsCascadeOnlyAssociationsAndInvalidAssignmentRollsBack() = runBlocking {
        people.save("p", "Person", null)
        val layer = nodes.createNode(project, null, "Layer")
        val child = nodes.createNode(project, layer.id, "Child")
        people.setResponsiblePeople(layer.id, setOf("p"))
        people.setResponsiblePeople(child.id, setOf("p"))
        assertFalse(people.setResponsiblePeople(child.id, setOf("missing")))
        assertEquals(listOf("p"), people.observeAssignments(project).first().getValue(child.id).map { it.id })
        assertTrue(people.delete("p"))
        assertNotNull(nodes.getNode(child.id))
        assertTrue(people.observeAssignments(project).first().isEmpty())
        people.save("p2", "Second", null)
        people.setResponsiblePeople(layer.id, setOf("p2")); people.setResponsiblePeople(child.id, setOf("p2"))
        nodes.deleteNode(layer.id)
        assertEquals("p2", people.observePeople().first().single().id)
        assertTrue(people.observeAssignments(project).first().isEmpty())
        assertFalse(people.save("deleted", "No resurrection", null, isNew = false))
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun avatarCopySurvivesSourceRemovalReopenAndRemoval() = runBlocking {
        val input = File(context.cacheDir, "photo.png")
        val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val file = people.importAvatar(Uri.fromFile(input))
        assertTrue(people.save("p", "Person", file))
        input.delete()
        db.close(); open()
        assertEquals(file, people.observePeople().first().single().avatarFile)
        val loaded = AvatarStore(context).read(file)!!
        assertTrue(loaded.width <= 512 && loaded.height <= 512)
        loaded.recycle()
        assertTrue(people.save("p", "Person renamed", null, isNew = false))
        assertFalse(AvatarStore(context).exists(file))
        val invalid = File(context.cacheDir, "bad-image").apply { writeText("not an image") }
        try { people.importAvatar(Uri.fromFile(invalid)); fail("Invalid image") } catch (_: IllegalArgumentException) {}
        assertFalse(File(context.filesDir, "avatars").listFiles().orEmpty().any { it.extension == "tmp" })
    }

    @Test fun migratesRealVersionFourSchemaPreservingProjectsNodesOrderAndTriggers() = runBlocking {
        db.close(); context.deleteDatabase("arachn0de.db")
        val schema = JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/4.json")!!.bufferedReader().use { it.readText() }).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object : SupportSQLiteOpenHelper.Callback(4) {
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
        helper.writableDatabase.execSQL("INSERT INTO projects VALUES ('project', 'Original', 'Description', 8, 100, 200)")
        helper.writableDatabase.execSQL("INSERT INTO nodes VALUES ('node', 'project', NULL, 'Original task', 'Task description', 1, 9, 100, 200)")
        helper.close()
        open()
        val oldProject = db.projectDao().getById("project")!!
        val oldNode = nodes.getNode("node")!!
        assertEquals(9, db.openHelper.readableDatabase.version)
        assertEquals("Original", oldProject.name)
        assertEquals(8, oldProject.position)
        assertEquals(100L, oldProject.createdAt)
        assertEquals(200L, oldProject.updatedAt)
        assertEquals("Original task", oldNode.title)
        assertEquals("Task description", oldNode.description)
        assertTrue(oldNode.isCompleted)
        assertEquals(9, oldNode.position)
        assertEquals(100L, oldNode.createdAt)
        assertEquals(200L, oldNode.updatedAt)
        people.save("p", "Person", null)
        people.setResponsiblePeople("node", setOf("p"))
        nodes.createNode("project", "node", "Child")
        assertTrue(nodes.getNode("node")!!.hasChildren)
        assertFalse(nodes.getNode("node")!!.isCompleted)
    }
}
