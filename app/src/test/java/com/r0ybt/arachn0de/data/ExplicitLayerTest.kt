package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[24,28])
class ExplicitLayerTest {
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    @Before fun setup()=runBlocking { val c=RuntimeEnvironment.getApplication();c.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(c);nodes=NodeRepository(db);db.projectDao().insert(ProjectEntity("p","Project","",0,1,1)) }
    @After fun close() { db.close() }
    @Test fun emptyLayerIsStructuralHasNoWorkAndCannotComplete()=runBlocking {
        val layer=nodes.createNode("p",null,"Empty",purpose=NodePurpose.LAYER)
        assertTrue(layer.isStructural);assertFalse(layer.hasChildren);assertFalse(layer.isCompletable)
        assertFalse(nodes.setCompleted(layer.id,true))
        assertEquals(NodeProgressState.NO_WORK,NodeTreeSnapshot(listOf(layer)).progressById.getValue(layer.id).state)
    }
    @Test fun lastDeletionAndMoveKeepLayerAndExplicitConversionIsRequired()=runBlocking {
        val a=nodes.createNode("p",null,"A",purpose=NodePurpose.LAYER);val b=nodes.createNode("p",null,"B",purpose=NodePurpose.LAYER)
        val child=nodes.createNode("p",a.id,"Task");nodes.setCompleted(child.id,true)
        assertFalse(nodes.convertPurpose(a.id,NodePurpose.ACTION))
        nodes.moveNode(child.id,b.id);assertEquals(NodePurpose.LAYER,nodes.getNode(a.id)!!.purpose)
        nodes.deleteNode(child.id);assertEquals(NodePurpose.LAYER,nodes.getNode(b.id)!!.purpose)
        assertTrue(nodes.convertPurpose(b.id,NodePurpose.ACTION));assertTrue(nodes.getNode(b.id)!!.isCompletable)
    }
    @Test fun completedActionAndNoteRejectChildrenAtRepositoryAndSql()=runBlocking {
        for(purpose in listOf(NodePurpose.ACTION,NodePurpose.NOTE)) {
            val parent=nodes.createNode("p",null,purpose.name,purpose=purpose)
            if (purpose == NodePurpose.ACTION) nodes.setCompleted(parent.id,true)
            assertTrue(runCatching { nodes.createNode("p",parent.id,"Child") }.isFailure)
            assertTrue(runCatching { db.nodeDao().insert(NodeEntity("child-${purpose.name}","p",parent.id,"Child","",false,0,1,1)) }.isFailure)
            assertEquals(purpose,nodes.getNode(parent.id)!!.purpose)
        }
    }
    @Test fun legacyRecurrencePromotesItsOldActionDestinationOnlyWhenDue()=runBlocking {
        val action=nodes.createNode("p",null,"Legacy destination");nodes.setCompleted(action.id,true)
        val rule=RecurrenceRuleEntity("legacy","p",action.id,"Recurring","",null,null,1,"DAILY",1,null,0,"ACTIVE","UTC",0,null)
        // Older schemas allowed rules targeting ACTION leaves before their first occurrence.
        db.recurrenceDao().insert(rule)
        nodes.recurrence.materializeBatch(0)
        assertEquals(NodePurpose.ACTION,nodes.getNode(action.id)!!.purpose)
        nodes.recurrence.materializeBatch(86400000L)
        assertEquals(NodePurpose.LAYER,nodes.getNode(action.id)!!.purpose)
        assertEquals("ACTIVE",db.recurrenceDao().get("legacy")!!.status)
        assertEquals(1L,db.recurrenceDao().get("legacy")!!.nextIndex)
        assertEquals(1,db.recurrenceDao().occurrences().size)
        nodes.recurrence.materializeBatch(86400000L);assertEquals(1,db.recurrenceDao().occurrences().size)
    }
    @Test fun layerWithChildrenRejectsTypeAndCompletionChanges()=runBlocking {
        val parent=nodes.createNode("p",null,"Layer",purpose=NodePurpose.LAYER);nodes.createNode("p",parent.id,"Task")
        assertTrue(runCatching { db.openHelper.writableDatabase.execSQL("UPDATE nodes SET purpose='ACTION' WHERE id=?",arrayOf(parent.id)) }.isFailure)
        assertTrue(runCatching { db.openHelper.writableDatabase.execSQL("UPDATE nodes SET isCompleted=1 WHERE id=?",arrayOf(parent.id)) }.isFailure)
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }
}
