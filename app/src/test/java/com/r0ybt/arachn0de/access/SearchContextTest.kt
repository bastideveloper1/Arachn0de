package com.r0ybt.arachn0de.access

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.searchDetailLines
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class SearchContextTest {
    private val zone=TimeZone.getTimeZone("UTC")
    private val now=java.time.Instant.parse("2026-10-25T12:00:00Z").toEpochMilli()
    private val person=Person("ana","Ana")
    private fun node(id:String="pay",due:Long?=null)=Node(id,"p",null,"Internet","",false,0,1,2,false,dueAt=due,obligation=Obligation(25000,"CLP"))
    private fun rule(status:String="ACTIVE",end:Long?=null)=RecurrenceRuleEntity("r","p",null,"Internet","",25000,"CLP",RecurrenceSchedule.parse("2026-08-05"),"MONTHLY",1,end,2,status,"UTC",540,null)
    private fun data(nodes:List<Node>,paid:Map<String,Long> = emptyMap(),people:Map<String,List<Person>> = emptyMap(),recurrences:SearchRecurrences=SearchRecurrences())=
        searchPresentation(nodes,TagState(),paid,people,recurrences,now,zone)
    @Test fun partialPaymentsUseActualRemainingAmountAndNeverInferAnUnassignedPerson() {
        val task=node();val before=task.copy()
        val detail=data(listOf(task),mapOf(task.id to 10000)).details.getValue("node:pay")
        assertEquals(15000L,detail.amount!!.amountMinor);assertTrue(detail.partial);assertEquals(SearchPaymentState.PARTIAL,detail.payment)
        assertTrue(detail.people.isEmpty());assertNull(detail.dueAt);assertEquals(before,task)
        val result=ElementSearch.index(listOf(Project("p","Finanzas","",0,1,1)),listOf(task)).last()
        val lines=searchDetailLines(result,detail,Locale.forLanguageTag("es-CL"),zone,now).joinToString()
        assertTrue(lines.contains("Pendiente:"));assertTrue(lines.contains("15.000"));assertFalse(lines.contains("25.000"));assertFalse(lines.contains("Vence:"));assertFalse(lines.contains("Responsable"))
    }
    @Test fun completedManualTaskIsNotReportedAsPaidAndSharedOwnersDoNotMultiplyMoney() {
        val task=node()
        val manual=data(listOf(task.copy(isCompleted=true))).details.getValue("node:pay")
        assertEquals(SearchPaymentState.COMPLETED,manual.payment)
        val shared=data(listOf(task),mapOf("pay" to 10000),mapOf("pay" to listOf(person,person,Person("luis","Luis")))).details.getValue("node:pay")
        assertEquals(listOf("Ana","Luis"),shared.people);assertEquals(15000L,shared.amount!!.amountMinor)
        val paid=data(listOf(task.copy(isCompleted=true)),mapOf("pay" to 25000)).details.getValue("node:pay")
        assertEquals(SearchPaymentState.PAID,paid.payment);assertTrue(paid.attention.isEmpty())
        val partialManual=data(listOf(task.copy(isCompleted=true)),mapOf("pay" to 10000)).details.getValue("node:pay")
        assertTrue(partialManual.partial);assertEquals(15000L,partialManual.amount!!.amountMinor)
    }
    @Test fun overdueMonthlyOccurrenceShowsFutureRuleDateInsteadOfOldDueDateWithoutChangingCursor() {
        val due=java.time.Instant.parse("2026-10-05T09:00:00Z").toEpochMilli()
        val task=node(due=due);val original=rule()
        val recurrences=SearchRecurrences(listOf(original),listOf(RecurrenceOccurrenceEntity("r",RecurrenceSchedule.parse("2026-10-05"),task.id)))
        val detail=data(listOf(task),people=mapOf(task.id to listOf(person)),recurrences=recurrences).details.getValue("node:pay")
        assertEquals(RecurrenceSchedule.parse("2026-11-05"),detail.recurrence!!.nextDay)
        assertEquals(due,detail.dueAt);assertEquals(AttentionReason.OVERDUE,detail.attention.first());assertEquals(2L,original.nextIndex)
        val result=ElementSearch.index(listOf(Project("p","Finanzas","",0,1,1)),listOf(task)).last()
        val line=searchDetailLines(result,detail,Locale.forLanguageTag("es-CL"),zone,now).joinToString()
        assertTrue(line.contains("Ana"));assertTrue(line.contains("Próxima:"));assertTrue(line.contains("5 nov"));assertTrue(line.contains("Vence: 5 oct"));assertTrue(line.contains("Vencida"))
    }
    @Test fun pausedFinishedDeletedAndEndedRulesNeverInventAFutureOccurrence() {
        val task=node()
        for(status in listOf("PAUSED","FINISHED")) {
            val detail=data(listOf(task),recurrences=SearchRecurrences(listOf(rule(status)),listOf(RecurrenceOccurrenceEntity("r",1,"pay")))).details.getValue("node:pay")
            assertNull(detail.recurrence!!.nextDay);assertEquals(status,detail.recurrence.status)
        }
        for(r in listOf(rule().copy(isDeleted=true),rule(end=RecurrenceSchedule.parse("2026-10-06")))) {
            val d=data(listOf(task),recurrences=SearchRecurrences(listOf(r),listOf(RecurrenceOccurrenceEntity("r",1,"pay"))))
            assertNull(d.details.getValue("node:pay").recurrence!!.nextDay);assertTrue("node:pay" in d.facts.recurring)
        }
    }
    @Test fun factsRespectStructuralRelationshipsAndExistingAttentionEligibility() {
        val parent=node("layer").copy(purpose=NodePurpose.LAYER,priority=Priority.HIGH)
        val task=node().copy(parentId="layer",priority=Priority.HIGH)
        val draft=node("draft").copy(projectId=null)
        val note=node("note").copy(purpose=NodePurpose.NOTE)
        val d=data(listOf(parent,task,draft,note))
        assertEquals(setOf("node:pay"),d.facts.attention);assertEquals(setOf("node:pay"),d.facts.obligations)
        assertNull(d.details.getValue("node:layer").amount);assertTrue(d.details.getValue("node:note").attention.isEmpty())
    }
}
