package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.data.repository.TechnologyState
import org.junit.Test
import org.junit.Assert.*
import java.util.TimeZone

class CompactCardFactsTest {
    private fun node(id:String,parent:String?=null,purpose:NodePurpose=NodePurpose.ACTION)=Node(id,"p",parent,id,"",false,0,0,0,purpose==NodePurpose.LAYER,purpose=purpose)
    @Test fun ancestorsAndProjectDeduplicateSharedPeopleAndTechnologies() {
        val layer=node("l",purpose=NodePurpose.LAYER);val a=node("a","l");val b=node("b","l")
        val person=Person("one","Persona")
        val result=compactCardFacts(listOf(layer,a,b),mapOf("l" to listOf(person),"a" to listOf(person),"b" to listOf(person)),TechnologyState(nodeIds=mapOf("a" to setOf("t"),"b" to setOf("t")),projectIds=mapOf("p" to setOf("t"))),null,0)
        assertEquals(setOf("one"),result.getValue("node:l").people)
        assertEquals(setOf("t"),result.getValue("project:p").technologies)
        assertTrue(result.getValue("node:l").due.not())
        assertEquals(setOf("one"),result.getValue("node:a").people)
    }
    @Test fun deadlinesAndPendingSignalsRespectCompletionAndLocalDay() {
        val zone=TimeZone.getTimeZone("UTC");val a=node("a").copy(dueAt=1000,priority=Priority.HIGH)
        val pending=compactNodeFacts(a,2000,zone)
        assertTrue(pending.due && pending.today && pending.high)
        val completed=compactNodeFacts(a.copy(isCompleted=true),2000,zone)
        assertFalse(completed.due);assertFalse(completed.today);assertFalse(completed.high)
        assertTrue(compactNodeFacts(node("b"),0,zone).empty)
        assertFalse(compactNodeFacts(a,86400000,zone).today)
    }
    @Test fun malformedCrossProjectParentDoesNotLeakAssociations() {
        val parent=node("l",purpose=NodePurpose.LAYER).copy(projectId="other")
        val result=compactCardFacts(listOf(parent,node("a","l")),mapOf("a" to listOf(Person("one","Persona"))),TechnologyState(),null,0)
        assertTrue(result.getValue("node:l").people.isEmpty())
    }
    @Test fun metroAssociationPropagatesOnceAndIndependentTripsStayOutOfCards() {
        val route=com.r0ybt.arachn0de.metro.MetroRoute("test",listOf("a","b"),false,emptyList())
        val linked=com.r0ybt.arachn0de.metro.MetroJourney(com.r0ybt.arachn0de.data.local.MetroJourneyEntity("j",nodeId="a",payload=""),com.r0ybt.arachn0de.metro.MetroJourneyData(route))
        val independent=linked.copy(row=linked.row.copy(id="independent",nodeId=null))
        val metro=com.r0ybt.arachn0de.metro.MetroSnapshot(com.r0ybt.arachn0de.metro.MetroPreferences(""),listOf(linked,independent))
        val facts=compactCardFacts(listOf(node("l",purpose=NodePurpose.LAYER),node("a","l")),emptyMap(),TechnologyState(),metro,0)
        assertEquals(setOf("j"),facts.getValue("node:l").journeys)
        assertEquals(setOf("j"),facts.getValue("project:p").journeys)
    }

    @Test fun semanticOrderIncludesContentAndDoesNotAlertOnFutureOrCompletedWork() {
        val far=node("future").copy(dueAt=3*86400000L,priority=Priority.HIGH,isCompleted=true)
        val facts=compactNodeFacts(far,0).copy(people=setOf("person"),technologies=setOf("technology"),notes=setOf("note"),tasks=setOf("task"),files=setOf("file"))
        val indicators=cardIndicators(facts)
        assertEquals(listOf("people","technologies","notes","tasks","files"),indicators.map {it.name})
        assertEquals(IndicatorTone.PEOPLE,indicators.first().tone)
        assertTrue(indicators.drop(1).all {it.tone==IndicatorTone.CONTENT})
    }
    @Test fun inheritedAlertsDeduplicateAndKeepTheirOriginalIdentities() {
        val layer=node("layer",purpose=NodePurpose.LAYER).copy(dueAt=1000)
        val task=node("task","layer").copy(dueAt=1000,priority=Priority.HIGH)
        val result=compactCardFacts(listOf(layer,task),emptyMap(),TechnologyState(),null,2000)
        assertEquals(setOf("task","layer"),result.getValue("node:layer").deadlines)
        assertEquals(setOf("task"),result.getValue("node:layer").priorities)
        assertEquals(setOf("task","layer"),result.getValue("project:p").deadlines)
        assertEquals(setOf("task"),result.getValue("node:layer").tasks)
        assertEquals(setOf("task"),result.getValue("node:task").deadlines)
        assertTrue(result.getValue("node:task").tasks.isEmpty())
    }
    @Test fun manyCardsUseTheSameSnapshotWithoutDatabaseQueries() {
        val nodes=listOf(node("root",purpose=NodePurpose.LAYER))+(0 until 1000).map {node("t$it","root")}
        val result=compactCardFacts(nodes,emptyMap(),TechnologyState(),null,0)
        assertEquals(1000,result.getValue("project:p").tasks.size)
        assertEquals(1000,result.getValue("node:root").tasks.size)
        assertEquals(1002,result.size)
    }

    @Test fun managedParticipationAndTraditionalResponsibilitiesShareOnePersonIndicator() {
        val root=com.r0ybt.arachn0de.data.local.ConversionRootEntity("context","p",null,"p","root",0,0,null,null,"NONE",null,false)
        val participation=com.r0ybt.arachn0de.data.repository.ParticipationState(
            records=com.r0ybt.arachn0de.data.repository.ParticipationRecords(
                participants=listOf(com.r0ybt.arachn0de.data.local.ManagementParticipantEntity("context","one")),
                nodes=listOf(com.r0ybt.arachn0de.data.local.NodeResponsibilityEntity("context","one","task","EXECUTOR"),com.r0ybt.arachn0de.data.local.NodeResponsibilityEntity("context","two","task","SUPERVISOR"))),
            work=com.r0ybt.arachn0de.data.repository.BacklogState(roots=listOf(root)))
        val result=compactCardFacts(listOf(node("task")),mapOf("task" to listOf(Person("one","One"))),TechnologyState(),null,0,participation=participation)
        assertEquals(setOf("one","two"),result.getValue("project:p").people)
        assertEquals(setOf("one","two"),result.getValue("node:task").people)
        assertEquals(1,cardIndicators(result.getValue("project:p")).count {it.name=="people"})
    }

    @Test fun linkedStoryTasksAreContentWithoutInventingInheritedAlerts() {
        val story=node("story",purpose=NodePurpose.LAYER)
        val external=node("external").copy(priority=Priority.HIGH)
        val physical=node("physical","story")
        val participation=com.r0ybt.arachn0de.data.repository.ParticipationState(work=com.r0ybt.arachn0de.data.repository.BacklogState(records=com.r0ybt.arachn0de.data.repository.BacklogRecords(storyTasks=listOf(com.r0ybt.arachn0de.data.local.StoryTaskEntity("story","external")))))
        val result=compactCardFacts(listOf(story,external,physical),emptyMap(),TechnologyState(),null,0,participation=participation)
        assertEquals(setOf("external","physical"),result.getValue("node:story").tasks)
        assertTrue(result.getValue("node:story").priorities.isEmpty())
        val directory=listOf(story,external,physical).associateBy {it.id}
        assertEquals("Propio",indicatorOrigin("node:external",external,directory))
        assertEquals("Relacionado",indicatorOrigin("node:story",external,directory))
        assertEquals("Descendiente",indicatorOrigin("node:story",physical,directory))
    }

}
