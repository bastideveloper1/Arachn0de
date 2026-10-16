package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.data.local.StoryTaskEntity
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class PendingCardIndicatorsTest {
    private val now=1_800_000_000_000L
    private fun task(id:String,parent:String?=null,done:Boolean=false)=Node(id,"p",parent,id,"",done,0,0,0,false)
    private fun layer(id:String,parent:String?=null)=task(id,parent).copy(purpose=NodePurpose.LAYER,hasChildren=true)
    private fun facts(nodes:List<Node>,participation:ParticipationState?=null)=compactCardFacts(nodes,emptyMap(),TechnologyState(),null,now,TimeZone.getTimeZone("UTC"),participation)
    private fun assertNoAttention(f:CompactCardFacts) {
        assertTrue(f.tasks.isEmpty());assertFalse(f.due);assertFalse(f.today);assertFalse(f.high)
        assertTrue(f.deadlines.isEmpty());assertTrue(f.priorities.isEmpty());assertTrue(f.dueNodes.isEmpty());assertTrue(f.todayNodes.isEmpty())
    }

    @Test fun fiveTasksFinishAndReactivateWithoutRemovingHistory() {
        val container=layer("layer").copy(dueAt=now-1000)
        val normal=task("normal","layer")
        val urgent=task("urgent","layer").copy(priority=Priority.HIGH,dueAt=now+3600000)
        val nodes=listOf(container,normal,urgent)+(1..3).map {task("done$it","layer",true).copy(priority=Priority.HIGH,dueAt=now-1000)}
        val active=facts(nodes).getValue("project:p")
        assertEquals(setOf("normal","urgent"),active.tasks)
        assertEquals(setOf("urgent"),active.priorities);assertEquals(setOf("layer","urgent"),active.deadlines)
        val completed=nodes.map {if(it.isCompletable) it.copy(isCompleted=true) else it}
        val closed=facts(completed)
        assertNoAttention(closed.getValue("project:p"));assertNoAttention(closed.getValue("node:layer"))
        assertEquals(5,completed.count {it.isCompletable});assertEquals(nodes.map {it.id},completed.map {it.id})
        val reopened=facts(completed.map {if(it.id=="urgent") it.copy(isCompleted=false) else it}).getValue("project:p")
        assertEquals(setOf("urgent"),reopened.tasks);assertTrue(reopened.high && reopened.due)
    }

    @Test fun mixedLayerUsesOnlyPendingDescendantsAndKeepsPermanentContent() {
        val finished=task("done","layer",true).copy(priority=Priority.HIGH,dueAt=now-1000,description="Nota histórica")
        val pending=task("pending","layer").copy(dueAt=now+3*86400000L)
        val result=facts(listOf(layer("layer"),finished,pending)).getValue("node:layer")
        assertEquals(setOf("pending"),result.tasks);assertEquals(setOf("pending"),result.dueNodes)
        assertTrue(result.deadlines.isEmpty());assertFalse(result.high);assertTrue(result.notes.contains("done"))
    }

    @Test fun completedUrgentTaskNeverPropagatesPriority() {
        val result=facts(listOf(layer("layer"),task("urgent","layer",true).copy(priority=Priority.HIGH)))
        listOf("node:urgent","node:layer","project:p").forEach {assertNoAttention(result.getValue(it))}
    }

    @Test fun completedUpcomingAndOverdueTasksNeverPropagateDeadlines() {
        val result=facts(listOf(layer("layer"),task("late","layer",true).copy(dueAt=now-1000),
            task("soon","layer",true).copy(dueAt=now+1000)))
        result.values.forEach(::assertNoAttention)
    }

    @Test fun completedTasksKeepAssociationsNotesAndFileIndicators() {
        val done=task("done","layer",true).copy(description="Historial "+AttachmentReferences.token("00000000-0000-0000-0000-000000000001"))
        val result=compactCardFacts(listOf(layer("layer"),done),mapOf("done" to listOf(Person("person","Persona"))),
            TechnologyState(nodeIds=mapOf("done" to setOf("technology"))),null,now)
        listOf("node:done","node:layer","project:p").forEach {key->
            val f=result.getValue(key)
            assertNoAttention(f)
            assertEquals(setOf("person"),f.people);assertEquals(setOf("technology"),f.technologies)
            assertEquals(setOf("done"),f.notes);assertEquals(setOf("done"),f.files)
        }
        val note=task("note").copy(purpose=NodePurpose.NOTE,dueAt=now-1000)
        val metadata=compactNodeFacts(note,now)
        assertTrue(metadata.due);assertTrue(metadata.deadlines.isEmpty())
    }

    @Test fun nestedAndLinkedTasksDeduplicateAndIgnoreCompletedLinks() {
        val task=task("pending","inner").copy(priority=Priority.HIGH,dueAt=now-1000)
        val nodes=listOf(layer("outer"),layer("inner","outer"),task,task("done",done=true))
        val participation=ParticipationState(work=BacklogState(records=BacklogRecords(storyTasks=listOf(
            StoryTaskEntity("outer","pending"),StoryTaskEntity("outer","done"),StoryTaskEntity("outer","missing")))))
        val result=facts(nodes,participation)
        listOf("node:inner","node:outer","project:p").forEach {key->
            val f=result.getValue(key)
            assertEquals(setOf("pending"),f.tasks);assertEquals(setOf("pending"),f.deadlines);assertEquals(setOf("pending"),f.priorities)
        }
    }

    @Test fun editsMovesAndDeletionRecomputeFromCurrentSnapshot() {
        val a=layer("a");val b=layer("b").copy(projectId="other")
        val task=task("t","a").copy(priority=Priority.HIGH,dueAt=now-1000)
        assertTrue(facts(listOf(a,b,task)).getValue("node:a").high)
        val edited=task.copy(priority=Priority.NONE,dueAt=null)
        val edit=facts(listOf(a,b,edited)).getValue("node:a")
        assertFalse(edit.high);assertFalse(edit.due);assertEquals(setOf("t"),edit.tasks)
        val moved=facts(listOf(a,b,task.copy(projectId="other",parentId="b")))
        assertNoAttention(moved.getValue("node:a"));assertNoAttention(moved.getValue("project:p"))
        assertEquals(setOf("t"),moved.getValue("node:b").tasks);assertTrue(moved.getValue("project:other").high)
        facts(listOf(a,b)).values.forEach(::assertNoAttention)
    }
}
