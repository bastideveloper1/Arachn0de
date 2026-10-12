package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.ui.projectTimeline
import org.junit.Test
import org.junit.Assert.*

class CascadeProjectionTest {
    private fun node(id:String,parent:String?,purpose:String="LAYER",done:Boolean=false,start:Long?=null,end:Long?=null)=NodeEntity(id,"p",parent,id,"",done,0,1,2,startAt=start,dueAt=end,purpose=purpose)
    @Test fun sharedAndNestedObjectiveWorkNeverDoubleCountsOrTreatsNotesAsTasks() {
        val tree=listOf(node("story",null),node("task","story","ACTION",true),node("note","story","NOTE"),node("layer","story"),node("sub","layer","ACTION"))
        val records=BacklogRecords(epicStories=listOf(EpicStoryEntity("epic","story")),objectiveEpics=listOf(ObjectiveEpicEntity("g","epic")))
        val links=listOf(ObjectiveWorkEntity("g","story"),ObjectiveWorkEntity("g","task"),ObjectiveWorkEntity("g","sub"))
        assertEquals(WorkProgress(1,2),objectiveWorkProgress("g",tree,links,records))
        assertEquals(WorkProgress(0,0),objectiveWorkProgress("other",tree,links,records))
    }
    @Test fun ownershipFiltersDoNotInferExecutionFromRoleOrInheritance() {
        val assigned=workResponsibilities(listOf(NodeResponsibilityEntity("c","me","story","EXECUTOR")),listOf("task" to "other","task" to "other"))
        assertTrue(matchesOwnership("story",WorkOwnership.MINE,"me",assigned))
        assertFalse(matchesOwnership("task",WorkOwnership.MINE,"me",assigned))
        assertTrue(matchesOwnership("task",WorkOwnership.OTHERS,"me",assigned))
        assertTrue(matchesOwnership("new",WorkOwnership.UNASSIGNED,"me",assigned))
        assertFalse(matchesOwnership("new",WorkOwnership.MINE,null,assigned))
    }
    @Test fun ganttUsesSprintDatesAndCurrentCheckProgressWithoutPersistingCopies() {
        val root=ConversionRootEntity("c","p",null,"p","p",0,0,null,null,"NONE",null,false)
        val sprint=node("s",null).copy(sprintMode=true)
        val rows=listOf(sprint,node("story",null),node("task","story","ACTION",true,1000,3000),node("note","story","NOTE"))
        val state=IterationState(records=IterationRecords(plans=listOf(IterationPlanEntity("plan","c","s","",2000,4000,"","PREPARED"))),work=BacklogState(nodes=rows,roots=listOf(root),contexts=listOf(ManagementContextEntity("c",true,"AGILE")),records=BacklogRecords(roles=listOf(ManagementRoleEntity("story","c","STORY")))))
        val timeline=projectTimeline(state,"c")
        assertFalse(timeline.any {it.node.id=="note"})
        val story=timeline.first {it.node.id=="story"}
        assertEquals(1000L,story.start);assertEquals(3000L,story.end);assertEquals(1f,story.progress)
        val plan=timeline.first {it.node.id=="s"};assertEquals(2000L,plan.start);assertEquals(4000L,plan.end)
        assertEquals(rows,state.work.nodes)
    }
}
