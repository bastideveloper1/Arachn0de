package com.r0ybt.arachn0de.data

import android.content.Context
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.domain.model.NodeProgressState
import com.r0ybt.arachn0de.domain.model.Project
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        nodeRepository = NodeRepository(database) { now }
        project = projectRepository.createProject("Project")
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase("arachn0de.db")
    }

    @Test
    fun rootAndChildNodesFollowStableProjectAndParentRelations() = runBlocking {
        val rootA = nodeRepository.createNode(project.id, null, "Root A",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val rootB = nodeRepository.createNode(project.id, null, "Root B")
        val childA1 = nodeRepository.createNode(project.id, rootA.id, "Child A1")
        val childA2 = nodeRepository.createNode(project.id, rootA.id, "Child A2")

        assertEquals(listOf(rootA.copy(hasChildren = true), rootB), nodeRepository.observeRootNodes(project.id).first())
        assertEquals(listOf(childA1, childA2), nodeRepository.observeChildren(project.id, rootA.id).first())
        assertEquals(project.id, nodeRepository.getNode(childA1.id)?.projectId)
        assertEquals(rootA.id, nodeRepository.getNode(childA1.id)?.parentId)
    }

    @Test
    fun deletingARootNodeRemovesItsEntireSubtreeAndKeepsSiblings() = runBlocking {
        val root = nodeRepository.createNode(project.id, null, "Root",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val child = nodeRepository.createNode(project.id, root.id, "Child",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
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
        val root = nodeRepository.createNode(project.id, null, "Root",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val child = nodeRepository.createNode(project.id, root.id, "Child",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val nested = nodeRepository.createNode(project.id, child.id, "Nested")

        database.close()
        database = Arachn0deDatabase.create(context)
        nodeRepository = NodeRepository(database) { now }

        assertEquals(root.copy(hasChildren = true), nodeRepository.getNode(root.id))
        assertEquals(child.copy(hasChildren = true), nodeRepository.getNode(child.id))
        assertEquals(nested, nodeRepository.getNode(nested.id))
        assertEquals(listOf(child.copy(hasChildren = true)), nodeRepository.observeChildren(project.id, root.id).first())
        assertEquals(listOf(nested), nodeRepository.observeChildren(project.id, child.id).first())
    }

    @Test
    fun completableNodesCanToggleCompletionAndStructuralNodesIgnoreManualCompletion() = runBlocking {
        val structural = nodeRepository.createNode(project.id, null, "Structural",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val task = nodeRepository.createNode(
            projectId = project.id,
            parentId = structural.id,
            title = "Completable",
        )

        assertFalse(nodeRepository.setCompleted(structural.id, true))
        assertTrue(nodeRepository.setCompleted(task.id, true))
        assertTrue(nodeRepository.getNode(task.id)?.isCompleted == true)
        assertTrue(nodeRepository.toggleCompleted(task.id))
        assertFalse(nodeRepository.getNode(task.id)?.isCompleted == true)
        assertFalse(nodeRepository.toggleCompleted(structural.id))
    }

    @Test
    fun progressIsDerivedFromCompletableDescendantsAcrossMultipleLevels() = runBlocking {
        val root = nodeRepository.createNode(project.id, null, "MVP",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val data = nodeRepository.createNode(project.id, root.id, "Database")
        val layers = nodeRepository.createNode(project.id, root.id, "Capas")
        val progress = nodeRepository.createNode(project.id, root.id, "Progreso")
        val ui = nodeRepository.createNode(project.id, root.id, "Interfaz")

        nodeRepository.setCompleted(data.id, true)
        nodeRepository.setCompleted(layers.id, true)

        val rootProgress = nodeRepository.calculateProgress(root.id)
        val childProgress = nodeRepository.calculateProgress(progress.id)

        assertEquals(2, rootProgress?.completed)
        assertEquals(4, rootProgress?.total)
        assertEquals(50, rootProgress?.percentage)
        assertEquals(NodeProgressState.PARTIAL, rootProgress?.state)
        assertEquals(false, rootProgress?.isComplete)
        assertEquals(0, childProgress?.completed)
        assertEquals(1, childProgress?.total)
        assertEquals(0, childProgress?.percentage)
        assertEquals(NodeProgressState.NOT_STARTED, childProgress?.state)
    }

    @Test
    fun projectProgressIsDerivedOnlyFromLeafTasksAcrossAllDepths() = runBlocking {
        val root = nodeRepository.createNode(project.id, null, "Proyecto",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val layerA = nodeRepository.createNode(project.id, root.id, "Capa A",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val layerB = nodeRepository.createNode(project.id, layerA.id, "Capa B",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val taskOne = nodeRepository.createNode(project.id, layerB.id, "Tarea 1")
        val taskTwo = nodeRepository.createNode(project.id, layerA.id, "Tarea 2")
        val topTask = nodeRepository.createNode(project.id, root.id, "Tarea raíz")

        nodeRepository.setCompleted(taskOne.id, true)
        nodeRepository.setCompleted(topTask.id, true)

        val projectProgress = nodeRepository.calculateProjectProgress(project.id)

        assertEquals(2, projectProgress?.completed)
        assertEquals(3, projectProgress?.total)
        assertEquals(66, projectProgress?.percentage)
        assertEquals(NodeProgressState.PARTIAL, projectProgress?.state)
        assertEquals(false, projectProgress?.isComplete)
    }

    @Test
    fun projectProgressIsNoWorkWhenProjectHasNoNodes() = runBlocking {
        val emptyProject = projectRepository.createProject("Proyecto vacío")

        val projectProgress = nodeRepository.calculateProjectProgress(emptyProject.id)

        assertEquals(NodeProgressState.NO_WORK, projectProgress?.state)
        assertEquals(0, projectProgress?.total)
        assertEquals(0, projectProgress?.percentage)
    }

    @Test
    fun depthAndAncestorsAreDerivedFromParentRelations() = runBlocking {
        val root = nodeRepository.createNode(project.id, null, "Root",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val child = nodeRepository.createNode(project.id, root.id, "Child",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val grandChild = nodeRepository.createNode(project.id, child.id, "Grandchild")

        assertEquals(1, nodeRepository.getNodeDepth(root.id))
        assertEquals(2, nodeRepository.getNodeDepth(child.id))
        assertEquals(3, nodeRepository.getNodeDepth(grandChild.id))
        assertEquals(listOf(root.copy(hasChildren = true), child.copy(hasChildren = true), grandChild), nodeRepository.getNodePath(grandChild.id))
    }

    @Test
    fun nodesWithChildrenCannotBeCompletedManuallyAndProgressDerivesFromDescendants() = runBlocking {
        val parent = nodeRepository.createNode(project.id, null, "Parent task",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val child = nodeRepository.createNode(project.id, parent.id, "Child task")

        assertFalse(nodeRepository.setCompleted(parent.id, true))
        assertFalse(nodeRepository.toggleCompleted(parent.id))
        assertFalse(nodeRepository.getNode(parent.id)?.isCompleted == true)

        assertTrue(nodeRepository.setCompleted(child.id, true))

        val parentProgress = nodeRepository.calculateProgress(parent.id)

        assertEquals(1, parentProgress?.completed)
        assertEquals(1, parentProgress?.total)
        assertEquals(100, parentProgress?.percentage)
        assertEquals(NodeProgressState.COMPLETE, parentProgress?.state)
    }

    @Test
    fun emptyNodesArePendingCompletableLeaves() = runBlocking {
        val empty = nodeRepository.createNode(project.id, null, "Empty container")

        val progress = nodeRepository.calculateProgress(empty.id)

        assertEquals(0, progress?.completed)
        assertEquals(1, progress?.total)
        assertEquals(0, progress?.percentage)
        assertEquals(NodeProgressState.NOT_STARTED, progress?.state)
        assertFalse(progress?.isComplete == true)
    }
}
