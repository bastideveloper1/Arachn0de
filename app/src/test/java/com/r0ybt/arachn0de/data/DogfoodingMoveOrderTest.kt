package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[24,28])
class DogfoodingMoveOrderTest {
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    private var now=100L
    @Before fun setup()=runBlocking {
        val context=RuntimeEnvironment.getApplication();context.deleteDatabase("arachn0de.db")
        db=Arachn0deDatabase.create(context);nodes=NodeRepository(db) { now }
        db.projectDao().insert(ProjectEntity("p","Project","",0,1,1))
    }
    @After fun close() { db.close() }
    @Test fun pendingDestinationPromotesAtomicallyAndPreservesCompatibleData()=runBlocking {
        db.personDao().insert(PersonEntity("person","Person",null))
        val tag=nodes.tags.create("Tag")
        val parent=nodes.createNode("p",null,"Parent","Description",startAt=10,dueAt=20,priority=Priority.HIGH,responsibleIds=setOf("person"),tagIds=setOf(tag.id))
        val history=db.nodeEventDao().forNode(parent.id)
        val child=nodes.createNode("p",null,"Child")
        now=200;assertTrue(nodes.moveNode(child.id,parent.id))
        assertEquals(parent.copy(purpose=NodePurpose.LAYER,hasChildren=true,updatedAt=200),nodes.getNode(parent.id))
        assertEquals(history,db.nodeEventDao().forNode(parent.id))
        assertEquals(listOf("person"),db.personDao().assignmentIds(parent.id))
        assertEquals(setOf(tag.id),nodes.tags.observe().first().nodeIds[parent.id])
        assertEquals(parent.id,nodes.getNode(child.id)!!.parentId)
        nodes.moveNode(child.id,null);assertTrue(nodes.getNode(parent.id)!!.isStructural)
        val pending=nodes.createNode("p",null,"Create parent")
        nodes.createNode("p",pending.id,"Created child");assertTrue(nodes.getNode(pending.id)!!.isStructural)
    }
    @Test fun completedNotesFinancialTasksAndCyclesNeverBecomeDestinations()=runBlocking {
        val source=nodes.createNode("p",null,"Source",purpose=NodePurpose.LAYER)
        val child=nodes.createNode("p",source.id,"Child",purpose=NodePurpose.LAYER)
        val done=nodes.createNode("p",null,"Done");nodes.setCompleted(done.id,true)
        val note=nodes.createNode("p",null,"Note",purpose=NodePurpose.NOTE)
        val money=nodes.createNode("p",null,"Money",obligation=Obligation(100,"CLP"))
        for (target in listOf(done,note,money,source,child)) {
            assertTrue(runCatching { nodes.moveNode(source.id,target.id) }.isFailure)
            assertNull(nodes.getNode(source.id)!!.parentId)
        }
        for(target in listOf(done,note,money)) assertTrue(runCatching { nodes.createNode("p",target.id,"Invalid") }.isFailure)
        assertTrue(nodes.getNode(done.id)!!.isCompleted)
        assertEquals(NodePurpose.ACTION,nodes.getNode(done.id)!!.purpose)
    }
    @Test fun layerAndProjectRootRemainValidAndBulkPromotesPendingDestination()=runBlocking {
        val a=nodes.createNode("p",null,"A");val b=nodes.createNode("p",null,"B")
        val layer=nodes.createNode("p",null,"Layer",purpose=NodePurpose.LAYER)
        assertTrue(nodes.moveNode(a.id,layer.id));assertTrue(nodes.moveNode(a.id,null))
        val pending=nodes.createNode("p",null,"Pending")
        assertEquals(2,nodes.moveSelected("p",setOf(a.id,b.id),pending.id))
        assertTrue(nodes.getNode(pending.id)!!.isStructural)
        assertTrue(runCatching { nodes.moveSelected("p",setOf(pending.id),a.id) }.isFailure)
    }
    @Test fun failureRollsBackPromotionForMoveCreationAndBulk()=runBlocking {
        val parent=nodes.createNode("p",null,"Parent");val child=nodes.createNode("p",null,"Child")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_move BEFORE UPDATE OF parentId ON nodes BEGIN SELECT RAISE(ABORT,'injected'); END")
        assertTrue(runCatching { nodes.moveNode(child.id,parent.id) }.isFailure)
        assertTrue(runCatching { nodes.moveSelected("p",setOf(child.id),parent.id) }.isFailure)
        assertEquals(parent,nodes.getNode(parent.id));assertEquals(child,nodes.getNode(child.id))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_create BEFORE INSERT ON node_events BEGIN SELECT RAISE(ABORT,'injected'); END")
        assertTrue(runCatching { nodes.createNode("p",parent.id,"New") }.isFailure)
        assertEquals(parent,nodes.getNode(parent.id));assertEquals(2,nodes.getProjectNodes("p").size)
    }
    @Test fun completedOrderUsesHistoryRecompletionAndLeavesPendingPositionsUntouched()=runBlocking {
        val a=nodes.createNode("p",null,"A");val b=nodes.createNode("p",null,"B")
        val pending=nodes.createNode("p",null,"Pending")
        now=200;nodes.setCompleted(a.id,true);now=300;nodes.setCompleted(b.id,true)
        suspend fun ordered()=NodePresentationSort.children(nodes.getProjectNodes("p"),NodeSortMode.MANUAL,nodes.observeCompletionTimes("p").first()).map { it.id }
        assertEquals(listOf(pending.id,b.id,a.id),ordered())
        now=400;nodes.setCompleted(a.id,false);now=500;nodes.setCompleted(a.id,true)
        assertEquals(listOf(pending.id,a.id,b.id),ordered())
        assertEquals(listOf(0,1,2),nodes.getProjectNodes("p").map { it.position })
        assertEquals(500L,nodes.observeCompletionTimes("p").first()[a.id])
    }
    @Test fun sprintKeepsDoneAndValidatedGroupsAndOriginalCompletionInstant()=runBlocking {
        val layer=nodes.createNode("p",null,"Sprint",purpose=NodePurpose.LAYER);nodes.setSprintMode(layer.id,true)
        val a=nodes.createNode("p",layer.id,"A");val b=nodes.createNode("p",layer.id,"B");val c=nodes.createNode("p",layer.id,"C")
        now=200;nodes.setWorkState(a.id,WorkState.DONE);now=300;nodes.setWorkState(b.id,WorkState.DONE)
        now=400;nodes.setWorkState(c.id,WorkState.VALIDATED);now=500;nodes.setWorkState(a.id,WorkState.VALIDATED)
        val times=nodes.observeCompletionTimes("p").first()
        val sorted=NodePresentationSort.children(nodes.getProjectNodes("p").filter { it.parentId==layer.id },NodeSortMode.MANUAL,times)
        assertEquals(listOf(c.id,a.id),sorted.filter { it.workState==WorkState.VALIDATED }.map { it.id })
        assertEquals(listOf(b.id),sorted.filter { it.workState==WorkState.DONE }.map { it.id })
        assertEquals(200L,times[a.id])
    }
}
