package com.r0ybt.arachn0de.data

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.local.NodeEntity
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import kotlinx.coroutines.runBlocking
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
class DeepDeletionTest {
    private lateinit var db: Arachn0deDatabase
    private lateinit var nodes: NodeRepository
    private lateinit var projects: ProjectRepository

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("arachn0de.db")
        db = Arachn0deDatabase.create(context)
        nodes = NodeRepository(db)
        projects = ProjectRepository(db.projectDao())
    }

    @After
    fun tearDown() {
        db.close()
        RuntimeEnvironment.getApplication().deleteDatabase("arachn0de.db")
    }

    private suspend fun deepTree(projectId: String, parentId: String? = null): String {
        db.withTransaction {
            repeat(1200) { index ->
                db.nodeDao().insert(NodeEntity(
                    id = "deep-$index", projectId = projectId,
                    parentId = if (index == 0) parentId else "deep-${index - 1}",
                    title = "Level $index", description = "", isCompleted = false,
                    position = 0, createdAt = 1, updatedAt = 1,
                ))
            }
        }
        return "deep-0"
    }

    @Test
    fun deletesDeepSubtreeAndPreservesOtherBranches() = runBlocking {
        val project = projects.createProject("Deep")
        val parent = nodes.createNode(project.id, null, "Parent")
        val sibling = nodes.createNode(project.id, null, "Sibling")
        nodes.setCompleted(sibling.id, true)
        val root = deepTree(project.id, parent.id)
        assertTrue(nodes.deleteNode(root))
        assertFalse(nodes.deleteNode(root))
        assertEquals(setOf(parent.id, sibling.id), nodes.getProjectNodes(project.id).map { it.id }.toSet())
        assertTrue(nodes.getNode(parent.id)!!.isCompletable)
        assertFalse(nodes.getNode(parent.id)!!.isCompleted)
        assertTrue(nodes.getNode(sibling.id)!!.isCompleted)
    }

    @Test
    fun deletesDeepProjectAndPreservesOtherProjects() = runBlocking {
        val project = projects.createProject("Deep")
        deepTree(project.id)
        val other = projects.createProject("Other")
        val survivor = nodes.createNode(other.id, null, "Survivor")
        assertTrue(projects.deleteProject(project.id))
        assertFalse(projects.deleteProject(project.id))
        assertTrue(nodes.getProjectNodes(project.id).isEmpty())
        assertEquals(survivor, nodes.getNode(survivor.id))
    }

    @Test
    fun failedDeletionRollsBackDetachedRelationships() = runBlocking {
        val project = projects.createProject("Rollback")
        val parent = nodes.createNode(project.id, null, "Parent")
        val root = deepTree(project.id, parent.id)
        val before = nodes.getProjectNodes(project.id)
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_test_delete BEFORE DELETE ON nodes
            WHEN OLD.id = 'deep-600'
            BEGIN SELECT RAISE(ABORT, 'Injected deletion failure'); END
        """.trimIndent())
        for (delete in listOf<suspend () -> Boolean>(
            { nodes.deleteNode(root) }, { projects.deleteProject(project.id) },
        )) {
            try {
                delete()
                fail("Deletion should fail")
            } catch (_: android.database.sqlite.SQLiteException) {
                assertEquals(before, nodes.getProjectNodes(project.id))
                assertNotNull(projects.getProject(project.id))
            }
        }
    }
}
