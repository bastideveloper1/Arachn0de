package com.r0ybt.arachn0de.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.local.NodeEntity
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NodeInvariantTest {
    private lateinit var context: Context
    private lateinit var db: Arachn0deDatabase
    private lateinit var nodes: NodeRepository
    private lateinit var projects: ProjectRepository
    private lateinit var projectId: String

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("arachn0de.db")
        db = Arachn0deDatabase.create(context)
        nodes = NodeRepository(db) { 100L }
        projects = ProjectRepository(db.projectDao()) { 100L }
        projectId = projects.createProject("Project").id
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase("arachn0de.db")
    }

    @Test
    fun rejectsSelfParentAndMovingAnAncestorUnderItsDescendant() = runBlocking {
        val root = create("Root")
        val child = create("Child", root.id)
        val grandchild = create("Grandchild", child.id)
        expectFailure<IllegalArgumentException> { nodes.moveNode(root.id, root.id) }
        expectFailure<IllegalArgumentException> { nodes.moveNode(root.id, grandchild.id) }
        assertNull(nodes.getNode(root.id)!!.parentId)
        assertEquals(listOf(root.id, child.id, grandchild.id), nodes.getNodePath(grandchild.id).map { it.id })
    }

    @Test
    fun rejectsCrossProjectAndMissingParentsWithoutPartialWrites() = runBlocking {
        val root = create("Root")
        val foreignProject = projects.createProject("Other")
        val foreignParent = nodes.createNode(foreignProject.id, null, "Foreign")
        expectFailure<IllegalArgumentException> { create("Invalid", foreignParent.id) }
        expectFailure<IllegalArgumentException> { nodes.moveNode(root.id, foreignParent.id) }
        expectFailure<IllegalArgumentException> { create("Invalid", "missing") }
        expectFailure<IllegalArgumentException> { nodes.createNode("missing", null, "Invalid") }
        assertEquals(listOf(root), nodes.getProjectNodes(projectId))
    }

    @Test
    fun pendingLeafBecomesContainerAndReturnsToPendingLeaf() = runBlocking {
        val root = create("Root")
        assertTrue(root.isCompletable)
        val child = create("Child", root.id)
        assertTrue(nodes.getNode(root.id)!!.isStructural)
        assertFalse(nodes.getNode(root.id)!!.isCompletable)
        assertFalse(nodes.setCompleted(root.id, true))
        assertFalse(nodes.setCompleted(root.id, false))
        nodes.deleteNode(child.id)
        assertEquals(root, nodes.getNode(root.id))
        assertTrue(nodes.setCompleted(root.id, true))
    }

    @Test
    fun completedLeafLosesManualCompletionPermanentlyWhenItGainsChildren() = runBlocking {
        val root = create("Completed leaf")
        nodes.setCompleted(root.id, true)
        val child = create("Pending child", root.id)
        assertFalse(nodes.getNode(root.id)!!.isCompleted)
        assertEquals(0, nodes.calculateProgress(root.id)!!.percentage)
        nodes.updateNode(root.id, "Renamed container")
        nodes.deleteNode(child.id)
        val leaf = nodes.getNode(root.id)!!
        assertTrue(leaf.isCompletable)
        assertFalse(leaf.isCompleted)
        assertTrue(nodes.toggleCompleted(root.id))
    }

    @Test
    fun completingContainerDoesNotCascadeAndContentEditingPreservesCompletion() = runBlocking {
        val parent = create("Parent")
        val completed = create("Completed", parent.id)
        val pending = create("Pending", parent.id)
        nodes.setCompleted(completed.id, true)
        assertFalse(nodes.setCompleted(parent.id, true))
        assertFalse(nodes.toggleCompleted(parent.id))
        assertTrue(nodes.getNode(completed.id)!!.isCompleted)
        assertFalse(nodes.getNode(pending.id)!!.isCompleted)
        nodes.updateNode(completed.id, "Edited", "Description")
        assertTrue(nodes.getNode(completed.id)!!.isCompleted)
        assertEquals(50, nodes.calculateProgress(parent.id)!!.percentage)
    }

    @Test
    fun movingLastChildUpdatesBothParentsAndNullExplicitlyMovesToRoot() = runBlocking {
        val oldParent = create("Old")
        val newParent = create("New")
        val child = create("Child", oldParent.id)
        nodes.setCompleted(newParent.id, true)
        assertTrue(nodes.moveNode(child.id, newParent.id))
        assertTrue(nodes.getNode(oldParent.id)!!.isCompletable)
        assertFalse(nodes.getNode(oldParent.id)!!.isCompleted)
        assertTrue(nodes.getNode(newParent.id)!!.isStructural)
        assertFalse(nodes.getNode(newParent.id)!!.isCompleted)
        assertTrue(nodes.moveNode(child.id, null))
        assertNull(nodes.getNode(child.id)!!.parentId)
        assertTrue(nodes.getNode(newParent.id)!!.isCompletable)
    }

    @Test
    fun databaseRejectsCyclesCrossProjectLinksAndContainerCompletionWithoutRepository() = runBlocking {
        val root = create("Root")
        val child = create("Child", root.id)
        val foreign = projects.createProject("Foreign")
        val foreignLeaf = nodes.createNode(foreign.id, null, "Foreign leaf")
        nodes.setCompleted(foreignLeaf.id, true)
        val sql = db.openHelper.writableDatabase
        expectFailure<SQLiteConstraintException> {
            sql.execSQL("UPDATE nodes SET parentId = ? WHERE id = ?", arrayOf(child.id, root.id))
        }
        expectFailure<SQLiteConstraintException> {
            db.nodeDao().insert(entity("self", projectId, "self"))
        }
        expectFailure<SQLiteConstraintException> {
            db.nodeDao().insert(entity("cross", foreign.id, root.id))
        }
        expectFailure<SQLiteConstraintException> {
            sql.execSQL("UPDATE nodes SET isCompleted = 1 WHERE id = ?", arrayOf(root.id))
        }
        expectFailure<SQLiteConstraintException> {
            sql.execSQL("UPDATE nodes SET projectId = ? WHERE id = ?", arrayOf(foreign.id, child.id))
        }
        expectFailure<SQLiteConstraintException> {
            sql.execSQL("UPDATE nodes SET parentId = ? WHERE id = ?", arrayOf(foreignLeaf.id, child.id))
        }
        // A rejected move rolls back its trigger side effects too.
        assertTrue(nodes.getNode(foreignLeaf.id)!!.isCompleted)
        assertEquals(root.id, nodes.getNode(child.id)!!.parentId)
        assertEquals(2, nodes.getProjectNodes(projectId).size)
        assertFalse(nodes.getNode(root.id)!!.isCompleted)
    }

    @Test
    fun databaseTriggersNormalizeTransitionsEvenForDirectDaoWrites() = runBlocking {
        val root = create("Root")
        nodes.setCompleted(root.id, true)
        db.nodeDao().insert(entity("direct", projectId, root.id))
        assertFalse(nodes.getNode(root.id)!!.isCompleted)
        db.nodeDao().delete("direct")
        assertTrue(nodes.getNode(root.id)!!.isCompletable)
        assertFalse(nodes.getNode(root.id)!!.isCompleted)
    }

    @Test
    fun activeProjectSnapshotUpdatesAncestorProgressForCompletionCreationMoveAndDeletion() = runBlocking {
        withTimeout(15_000) {
            val root = create("Root")
            val child = create("Child", root.id)
            val leaf = create("Leaf", child.id)
            val emissions = Channel<NodeTreeSnapshot>(Channel.UNLIMITED)
            val observer = launch(start = CoroutineStart.UNDISPATCHED) {
                nodes.observeProjectState(projectId).collect { emissions.send(it) }
            }
            suspend fun awaitState(predicate: (NodeTreeSnapshot) -> Boolean): NodeTreeSnapshot {
                while (true) {
                    val state = emissions.receive()
                    if (predicate(state)) return state
                }
            }
            try {
                awaitState { it.progressById[root.id]?.percentage == 0 }
                nodes.setCompleted(leaf.id, true)
                awaitState { it.progressById[root.id]?.percentage == 100 }
                nodes.setCompleted(leaf.id, false)
                awaitState { it.progressById[root.id]?.percentage == 0 }
                nodes.setCompleted(leaf.id, true)
                awaitState { it.progressById[root.id]?.percentage == 100 }
                val sibling = create("Sibling", root.id)
                awaitState { it.progressById[root.id]?.percentage == 50 }
                nodes.moveNode(sibling.id, null)
                awaitState { it.nodesById[sibling.id]?.parentId == null && it.progressById[root.id]?.percentage == 100 }
                nodes.deleteNode(child.id)
                val state = awaitState { it.nodesById[root.id]?.isCompletable == true }
                assertEquals(0, state.progressById[root.id]!!.percentage)
                assertEquals(1, state.progressById[root.id]!!.total)
            } finally {
                observer.cancel()
                emissions.close()
            }
        }
    }

    @Test
    fun thirtyTwoLevelsSupportProgressPathsMovesAndSubtreeDeletion() = runBlocking {
        val root = create("Root")
        var deepest = root
        repeat(31) { deepest = create("Level $it", deepest.id) }
        assertEquals(32, nodes.getNodeDepth(deepest.id))
        nodes.setCompleted(deepest.id, true)
        assertEquals(100, nodes.calculateProgress(root.id)!!.percentage)
        expectFailure<IllegalArgumentException> { nodes.moveNode(root.id, deepest.id) }
        nodes.moveNode(deepest.id, null)
        assertEquals(0, nodes.calculateProgress(root.id)!!.percentage)
        nodes.deleteNode(root.id)
        assertEquals(listOf(deepest.id), nodes.getProjectNodes(projectId).map { it.id })
    }

    @Test
    fun concurrentTogglesEditsAndStructuralChangesDoNotLoseUpdates() = runBlocking {
        withTimeout(20_000) {
            val root = create("Root")
            val toggles = (1..40).map { async(Dispatchers.Default) { nodes.toggleCompleted(root.id) } }
            val edit = async(Dispatchers.Default) { nodes.updateNode(root.id, "Edited", "Kept") }
            toggles.awaitAll()
            edit.await()
            assertFalse(nodes.getNode(root.id)!!.isCompleted)
            assertEquals("Edited", nodes.getNode(root.id)!!.title)
            assertEquals("Kept", nodes.getNode(root.id)!!.description)
            listOf(
                async(Dispatchers.Default) { nodes.setCompleted(root.id, true) },
                async(Dispatchers.Default) { create("Child", root.id) },
            ).awaitAll()
            assertTrue(nodes.getNode(root.id)!!.hasChildren)
            assertFalse(nodes.getNode(root.id)!!.isCompleted)
        }
    }

    @Test
    fun concurrentOppositeMovesCannotIntroduceACycle() = runBlocking {
        withTimeout(15_000) {
            val a = create("A")
            val b = create("B")
            val results = listOf(
                async(Dispatchers.Default) { runCatching { nodes.moveNode(a.id, b.id) } },
                async(Dispatchers.Default) { runCatching { nodes.moveNode(b.id, a.id) } },
            ).awaitAll()
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(2, nodes.getProjectNodes(projectId).size)
        }
    }

    @Test
    fun positionsAppendAfterDeletionAndConcurrentCreation() = runBlocking {
        val first = create("First")
        create("Second")
        val last = create("Last")
        nodes.deleteNode(first.id)
        val added = create("Added")
        assertTrue(added.position > last.position)
        (1..10).map { async(Dispatchers.Default) { create("Concurrent $it") } }.awaitAll()
        val positions = nodes.getProjectNodes(projectId).map { it.position }
        assertEquals(positions.size, positions.toSet().size)
    }

    @Test
    fun deletingAProjectRemovesItsNodesButPreservesOtherProjects() = runBlocking {
        val root = create("Root")
        create("Child", root.id)
        val other = projects.createProject("Other")
        val retained = nodes.createNode(other.id, null, "Keep")
        projects.deleteProject(projectId)
        assertTrue(nodes.getProjectNodes(projectId).isEmpty())
        assertEquals(retained, nodes.getNode(retained.id))
    }

    @Test
    fun defensiveTraversalsRejectCorruptionWithoutInfiniteRecursion() = runBlocking {
        val root = create("Root")
        val child = create("Child", root.id)
        // Simulate externally corrupted storage by explicitly removing the SQL guard.
        val sql = db.openHelper.writableDatabase
        sql.execSQL("DROP TRIGGER nodes_no_cycle_move")
        sql.execSQL("UPDATE nodes SET parentId = ? WHERE id = ?", arrayOf(child.id, root.id))
        withTimeout(5_000) {
            expectFailure<IllegalStateException> { nodes.getNodePath(root.id) }
            expectFailure<IllegalStateException> { nodes.getNodeDepth(child.id) }
            expectFailure<IllegalStateException> { nodes.calculateProgress(root.id) }
        }
    }

    @Test
    fun unequalBranchesCountLeavesInsteadOfAveragingChildPercentages() = runBlocking {
        val root = create("Root")
        val completedLeaf = create("Completed", root.id)
        val branch = create("Branch", root.id)
        repeat(3) { create("Pending $it", branch.id) }
        nodes.setCompleted(completedLeaf.id, true)
        val progress = nodes.calculateProgress(root.id)!!
        assertEquals(1, progress.completed)
        assertEquals(4, progress.total)
        assertEquals(25, progress.percentage)
    }

    private suspend fun create(title: String, parentId: String? = null): Node =
        nodes.createNode(projectId, parentId, title)

    private fun entity(id: String, projectId: String, parentId: String?) =
        NodeEntity(id, projectId, parentId, id, "", false, 0, 100, 100)

    private suspend inline fun <reified T : Throwable> expectFailure(action: () -> Unit) {
        try {
            action()
            fail("Expected ${T::class.java.simpleName}")
        } catch (failure: Throwable) {
            if (failure !is T) throw failure
        }
    }
}
