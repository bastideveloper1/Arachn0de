package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.local.NodeEntity
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
class NodeOrderTest {
    private lateinit var db: Arachn0deDatabase
    private lateinit var nodes: NodeRepository
    private lateinit var project: String
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("arachn0de.db")
        db = Arachn0deDatabase.create(context)
        nodes = NodeRepository(db) { 99L }
        runBlocking { project = ProjectRepository(db.projectDao()).createProject("Order").id }
    }
    @After fun close() { db.close() }
    private suspend fun insert(id: String, position: Int, parent: String? = null, created: Long = 1) {
        db.nodeDao().insert(NodeEntity(id, project, parent, id, "description", false, position, created, 5))
    }
    private suspend fun ids(parent: String? = null) = db.nodeDao().getSiblings(project, parent).map { it.id }

    @Test fun rootsMoveOneStepAndBoundariesAreNoOps() = runBlocking {
        insert("a",0); insert("b",1); insert("c",2)
        assertTrue(nodes.reorderNode("b", null, true))
        assertEquals(listOf("b","a","c"), ids())
        assertTrue(nodes.reorderNode("b", null, true))
        assertTrue(nodes.reorderNode("b", null, false))
        assertEquals(listOf("a","b","c"), ids())
        assertTrue(nodes.reorderNode("c", null, false))
        assertEquals(listOf(0,1,2),db.nodeDao().getSiblings(project,null).map { it.position })
    }

    @Test fun normalizationPreservesDeterministicOrderContentAndDates() = runBlocking {
        insert("z",7,created=2); insert("b",7); insert("a",7); insert("last",100)
        val before=db.nodeDao().getProjectNodes(project)
        nodes.normalizeProjectOrder(project)
        assertEquals(listOf("a","b","z","last"),ids())
        val after=db.nodeDao().getProjectNodes(project)
        assertEquals(before.mapIndexed { i,n -> n.copy(position=i) },after)
        nodes.normalizeProjectOrder(project)
        assertEquals(after,db.nodeDao().getProjectNodes(project))
    }

    @Test fun duplicatePositionsAreRepairedInSameTransactionAsMove() = runBlocking {
        insert("a",8); insert("b",8); insert("c",8)
        nodes.reorderNode("c",null,true)
        assertEquals(listOf("a","c","b"),ids())
        assertEquals(listOf(0,1,2),db.nodeDao().getSiblings(project,null).map { it.position })
        assertEquals(5L,db.nodeDao().getById("a")!!.updatedAt)
        assertEquals(99L,db.nodeDao().getById("c")!!.updatedAt)
    }

    @Test fun childMoveDoesNotTouchOtherParentsDescendantsOrProjects() = runBlocking {
        insert("parent",0); insert("other",1)
        insert("a",0,"parent"); insert("b",1,"parent"); insert("grandchild",0,"a")
        insert("otherChild",8,"other")
        val otherProject=ProjectRepository(db.projectDao()).createProject("Other")
        nodes.createNode(otherProject.id,null,"Foreign")
        nodes.setCompleted("grandchild",true)
        val progressBefore=nodes.calculateProgress("parent")
        val untouched=db.nodeDao().getProjectNodes(project).filter { it.id !in listOf("a","b") }
        nodes.reorderNode("b","parent",true)
        assertEquals(listOf("b","a"),ids("parent"))
        assertEquals(progressBefore,nodes.calculateProgress("parent"))
        assertTrue(nodes.getNode("grandchild")!!.isCompleted)
        assertEquals(untouched,db.nodeDao().getProjectNodes(project).filter { it.id !in listOf("a","b") })
        assertEquals(1,nodes.getProjectNodes(otherProject.id).size)
        assertFalse(nodes.reorderNode("b","other",true))
        assertFalse(nodes.reorderNode("absent",null,true))
    }

    @Test fun failedMoveRollsBackEveryPosition() = runBlocking {
        insert("a",5); insert("b",5); insert("c",5)
        val before=db.nodeDao().getProjectNodes(project)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_order BEFORE UPDATE OF position ON nodes WHEN NEW.id='b' BEGIN SELECT RAISE(ABORT,'injected'); END")
        try { nodes.reorderNode("c",null,true); fail("Expected failure") } catch (expected: android.database.sqlite.SQLiteException) { }
        assertEquals(before,db.nodeDao().getProjectNodes(project))
    }

    @Test fun concurrentReordersAndCreationKeepUniqueOrder() = runBlocking {
        insert("a",0); insert("b",1); insert("c",2)
        (0 until 20).map { i -> async(Dispatchers.Default) { nodes.reorderNode("b",null,i % 2 == 0) } }.awaitAll()
        nodes.createNode(project,null,"new")
        assertEquals(listOf(0,1,2,3),db.nodeDao().getSiblings(project,null).map { it.position })
    }

    @Test fun exhaustedPositionCompactsBeforeAppending() = runBlocking {
        insert("a",Int.MAX_VALUE)
        val added=nodes.createNode(project,null,"new")
        assertEquals(1,added.position)
        assertEquals(0,db.nodeDao().getById("a")!!.position)
    }

    @Test fun preparedObservationRepairsAllLayersBeforeEmission() = runBlocking {
        insert("a",10); insert("b",10); insert("child1",8,"a"); insert("child2",8,"a")
        val state=nodes.observePreparedProjectState(project).first()
        assertEquals(listOf(0,1),state.childrenOf(null).map { it.position })
        assertEquals(listOf(0,1),state.childrenOf("a").map { it.position })
    }
}
