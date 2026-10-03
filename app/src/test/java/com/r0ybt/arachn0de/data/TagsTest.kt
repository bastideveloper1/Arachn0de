package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [24,28])
class TagsTest {
    private lateinit var db: Arachn0deDatabase
    private lateinit var nodes: NodeRepository
    private var now = RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2026-10-10"), "UTC", 0)
    @Before fun setup() = runBlocking {
        val context = RuntimeEnvironment.getApplication(); context.deleteDatabase("arachn0de.db")
        db = Arachn0deDatabase.create(context); nodes = NodeRepository(db) { now }
        db.projectDao().insert(ProjectEntity("p","Project","",0,1,1))
        db.projectDao().insert(ProjectEntity("other","Other","",0,1,1))
    }
    @After fun close() { db.close(); RuntimeEnvironment.getApplication().deleteDatabase("arachn0de.db") }
    @Test fun normalizationReuseRenameCollisionAndUnusedRetention() = runBlocking {
        val tag = nodes.tags.create("  Trabajo   Urgente  ")
        assertEquals("Trabajo Urgente", tag.name); assertEquals("trabajo urgente", tag.normalizedName)
        assertEquals(tag.id, nodes.tags.create("TRABAJO urgente").id)
        assertTrue(runCatching { nodes.tags.create("   ") }.isFailure)
        val other = nodes.tags.create("Otro")
        assertTrue(runCatching { nodes.tags.rename(other.id,"Trabajo urgente") }.isFailure)
        nodes.tags.rename(tag.id,"Urgente")
        assertEquals(2,nodes.tags.observe().first().tags.size)
    }
    @Test fun allNodeTypesNoInheritanceAndCascadesOnlyRelations() = runBlocking {
        val tag = nodes.tags.create("A")
        val root = nodes.createNode("p",null,"Root",tagIds=setOf(tag.id))
        val leaf = nodes.createNode("p",root.id,"Leaf")
        val note = nodes.createNode("p",root.id,"Note",purpose=NodePurpose.NOTE,tagIds=setOf(tag.id))
        val bill = nodes.createNode("p",root.id,"Bill",obligation=Obligation(12,"CLP"),tagIds=setOf(tag.id))
        assertTrue(db.tagDao().nodeIds(leaf.id).isEmpty())
        nodes.deleteNode(note.id); assertNotNull(db.tagDao().get(tag.id))
        nodes.tags.delete(tag.id)
        assertTrue(db.tagDao().nodeTags().isEmpty()); assertNotNull(nodes.getNode(bill.id)); assertNotNull(nodes.getNode(root.id))
    }
    @Test fun editorInvalidTagRollsBackContentAndAssignments() = runBlocking {
        val tag = nodes.tags.create("Existing")
        val node = nodes.createNode("p",null,"Original",tagIds=setOf(tag.id))
        assertTrue(runCatching { nodes.updateEditor(node.id,"Changed","",null,null,null,false,true,setOf("missing")) }.isFailure)
        assertEquals("Original",nodes.getNode(node.id)!!.title); assertEquals(listOf(tag.id),db.tagDao().nodeIds(node.id))
        assertTrue(nodes.updateEditor(node.id,"Changed","",null,null,null,false,true,emptySet()))
        assertTrue(db.tagDao().nodeIds(node.id).isEmpty()); assertNotNull(db.tagDao().get(tag.id))
    }
    @Test fun batchAtomicAndRetryAssignments() = runBlocking {
        val tag = nodes.tags.create("Shared")
        val specs = listOf(GeneratedNodeSpec("One","",NodePurpose.ACTION,null,null), GeneratedNodeSpec("Two","",NodePurpose.ACTION,null,null))
        assertTrue(runCatching { nodes.createBatch("p",null,"bad",specs,tagIds=setOf("missing")) }.isFailure)
        assertTrue(nodes.getProjectNodes("p").isEmpty())
        val batch = nodes.createBatch("p",null,"batch",specs,tagIds=setOf(tag.id))
        assertEquals(batch, nodes.createBatch("p",null,"batch",specs,tagIds=setOf(tag.id)))
        assertEquals(2,db.tagDao().nodeTags().size)
        assertTrue(runCatching { nodes.createBatch("p",null,"batch",specs) }.isFailure)
    }
    @Test fun scopedAndFiltersKeepOnlyMinimalAncestorsWithoutChangingProgress() = runBlocking {
        val tag = nodes.tags.create("Match")
        val root = nodes.createNode("p",null,"Root")
        val branch = nodes.createNode("p",root.id,"Branch")
        val leaf = nodes.createNode("p",branch.id,"Match",dueAt=10,tagIds=setOf(tag.id),responsibleIds=emptySet())
        nodes.createNode("p",root.id,"Other")
        nodes.createNode("other",null,"Outside",tagIds=setOf(tag.id))
        val tree = nodes.observeAllState().first(); val progress = tree.progressById
        val filter = NodeFilter(TemporalRange(0,20),completion=CompletionFilter.PENDING,tagId=tag.id)
        val tags = nodes.tags.observe().first()
        val rows = ScopedNodeFilter.apply(tree,"p",root.id,filter,emptyMap(),tags.nodeIds)
        assertEquals(listOf(branch.id,leaf.id),rows.map { it.node.id }); assertFalse(rows.first().isMatch); assertTrue(rows.last().isMatch)
        assertTrue(ScopedNodeFilter.apply(tree,"p",branch.id,filter.copy(personId="absent"),emptyMap(),tags.nodeIds).isEmpty())
        assertEquals(progress,tree.progressById)
    }
    @Test fun recurrenceCopiesTemplateFutureOnlyAndDoesNotReplayDeletion() = runBlocking {
        val a = nodes.tags.create("A"); val b = nodes.tags.create("B")
        val rule = RecurrenceRuleEntity("r","p",null,"Plan","",null,null,RecurrenceSchedule.parse("2026-10-10"),"MONTHLY",1,null,0,"ACTIVE","UTC",0,null)
        nodes.recurrence.create(rule,tagIds=setOf(a.id)); nodes.recurrence.materializeDue()
        val old = nodes.getProjectNodes("p").single(); assertEquals(listOf(a.id),db.tagDao().nodeIds(old.id))
        nodes.recurrence.editTemplate("r","p",null,"Plan","",null,emptySet(),setOf(b.id))
        now = RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2026-11-10"),"UTC",0)
        nodes.recurrence.materializeDue(); assertEquals(listOf(a.id),db.tagDao().nodeIds(old.id))
        val fresh = nodes.getProjectNodes("p").single { it.id != old.id }; assertEquals(listOf(b.id),db.tagDao().nodeIds(fresh.id))
        nodes.deleteNode(fresh.id); nodes.recurrence.materializeDue(); assertEquals(1,nodes.getProjectNodes("p").size)
    }
}
