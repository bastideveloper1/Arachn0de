package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import org.junit.Assert.*
import org.junit.Test

class StoryAssociationReviewTest {
    private fun node(id:String,parent:String?=null,sprint:Boolean=false)=NodeEntity(id,"p",parent,id,"",false,0,0,0,purpose="LAYER",sprintMode=sprint)
    private fun state(records:BacklogRecords,links:List<SprintLinkEntity> = emptyList(),direct:Boolean=false):IterationState {
        val root=ConversionRootEntity("c","p",null,"p","root",0,0,null,null,"NONE",null,false)
        return IterationState(work=BacklogState(nodes=listOf(node("story"),node("epic"),node("sprint",sprint=true),node("task",if(direct) "sprint" else null)),roots=listOf(root),contexts=listOf(ManagementContextEntity("c",true,"AGILE")),objectives=listOf(ManagementObjectiveEntity("goal","c","Goal",position=0)),records=records),links=links)
    }
    private val roles=listOf(ManagementRoleEntity("story","c","STORY"),ManagementRoleEntity("epic","c","EPIC"))
    @Test fun missingOptionalAssociationsAreListedOnceWithoutChangingState() {
        val input=state(BacklogRecords(roles=roles+roles.first()))
        assertEquals(listOf(StoryAssociationReview("story",true,true)),storyAssociationReview("c",input))
        assertFalse(input.work.nodes.first().isCompleted)
        assertTrue(storyAssociationReview("other",input).isEmpty())
    }
    @Test fun indirectObjectivesAndDirectSprintTasksResolveByIds() {
        val records=BacklogRecords(roles=roles,objectiveEpics=listOf(ObjectiveEpicEntity("goal","epic")),epicStories=listOf(EpicStoryEntity("epic","story")),storyTasks=listOf(StoryTaskEntity("story","task")))
        assertTrue(storyAssociationReview("c",state(records,direct=true)).isEmpty())
    }
    @Test fun linkedTasksResolveSprintButNotObjectiveAndIgnoreOtherSprints() {
        val records=BacklogRecords(roles=roles,storyTasks=listOf(StoryTaskEntity("story","task")))
        assertEquals(listOf(StoryAssociationReview("story",true,false)),storyAssociationReview("c",state(records,listOf(SprintLinkEntity(taskId="task",sprintId="sprint")))))
        assertTrue(storyAssociationReview("c",state(records,listOf(SprintLinkEntity(taskId="task",sprintId="missing")))).single().sprintMissing)
    }
    @Test fun emptyContextHasNoPermanentIndicator() {assertTrue(storyAssociationReview("c",state(BacklogRecords())).isEmpty())}
}
