package com.r0ybt.arachn0de.data

import android.content.Context
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.domain.model.Project
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NodePersistenceTest {
    private lateinit var context: Context
    private lateinit var database: Arachn0deDatabase
    private lateinit var projectRepository: ProjectRepository
    private lateinit var nodeRepository: NodeRepository
    private var now = 1_000L
    private lateinit var project: Project

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("arachn0de.db")
        database = Arachn0deDatabase.create(context)
        projectRepository = ProjectRepository(database.projectDao()) { now }
        nodeRepository = NodeRepository(database.nodeDao()) { now }
        project = projectRepository.createProject("Project")
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase("arachn0de.db")
    }

    @Test
    fun rootAndChildNodesFollowStableProjectAndParentRelations() = runBlocking {
        val rootA = nodeRepository.createNode(project.id, null, "Root A")
        val rootB = nodeRepository.createNode(project.id, null, "Root B")
        val childA1 = nodeRepository.createNode(project.id, rootA.id, "Child A1")
        val childA2 = nodeRepository.createNode(project.id, rootA.id, "Child A2")

        assertEquals(listOf(rootA, rootB), nodeRepository.observeRootNodes(project.id).first())
        assertEquals(listOf(childA1, childA2), nodeRepository.observeChildren(project.id, rootA.id).first())
        assertEquals(project.id, nodeRepository.getNode(childA1.id)?.projectId)
        assertEquals(rootA.id, nodeRepository.getNode(childA1.id)?.parentId)
    }

    @Test
    fun deletingARootNodeRemovesItsEntireSubtreeAndKeepsSiblings() = runBlocking {
        val root = nodeRepository.createNode(project.id, null, "Root")
        val child = nodeRepository.createNode(project.id, root.id, "Child")
        val grandChild = nodeRepository.createNode(project.id, child.id, "Grandchild")
        val otherRoot = nodeRepository.createNode(project.id, null, "Other")

        assertTrue(nodeRepository.deleteNode(root.id))

        assertNull(nodeRepository.getNode(root.id))
        assertNull(nodeRepository.getNode(child.id))
        assertNull(nodeRepository.getNode(grandChild.id))
        assertEquals(listOf(otherRoot), nodeRepository.observeRootNodes(project.id).first())
    }

    @Test
    fun deepHierarchyPersistsAcrossDatabaseReopen() = runBlocking {
        val root = nodeRepository.createNode(project.id, null, "Root")
        val child = nodeRepository.createNode(project.id, root.id, "Child", isStructural = false, isCompletable = true)
        val nested = nodeRepository.createNode(project.id, child.id, "Nested")

        database.close()
        database = Arachn0deDatabase.create(context)
        nodeRepository = NodeRepository(database.nodeDao()) { now }

        assertEquals(root, nodeRepository.getNode(root.id))
        assertEquals(child, nodeRepository.getNode(child.id))
        assertEquals(nested, nodeRepository.getNode(nested.id))
        assertEquals(listOf(child), nodeRepository.observeChildren(project.id, root.id).first())
        assertEquals(listOf(nested), nodeRepository.observeChildren(project.id, child.id).first())
    }
}
