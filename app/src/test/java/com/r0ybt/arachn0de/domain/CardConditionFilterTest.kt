package com.r0ybt.arachn0de.domain

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class CardConditionFilterTest {
    private val zone=TimeZone.getTimeZone("UTC")
    private val day=CalendarDay(2026,11,4)
    private val now=CalendarDates.labelInstant(day)+10*60*60*1000L
    private fun node(id:String,due:Long?=null,priority:Priority=Priority.NONE)=
        Node(id,"p",null,id,"",false,0,1,1,false,dueAt=due,priority=priority)
    private val rows=listOf(node("overdue",now-1),node("today",now+1),
        node("upcoming",now+23*60*60*1000),node("far",now+48*60*60*1000),
        node("urgent",priority=Priority.HIGH),node("urgent-overdue",now-1,Priority.HIGH),
        node("verify",priority=Priority.HIGH).copy(awaitingVerification=true),
        node("completed",now-1,Priority.HIGH).copy(isCompleted=true),
        node("medium",priority=Priority.MEDIUM),node("note").copy(purpose=NodePurpose.NOTE),
        node("layer").copy(purpose=NodePurpose.LAYER),
        node("scheduled",now+23*60*60*1000).copy(startAt=now+1),node("start-only").copy(startAt=now-1))

    @Test fun individualQueriesCoverEveryEmittedAttentionReasonExactly() {
        val expected=mapOf(
            AttentionReason.HIGH_PRIORITY to setOf("urgent","urgent-overdue","verify"),
            AttentionReason.OVERDUE to setOf("overdue","urgent-overdue"),
            AttentionReason.DUE_TODAY to setOf("today"),
            AttentionReason.UPCOMING to setOf("upcoming"))
        val union=mutableSetOf<String>()
        expected.forEach {(reason,ids)->
            val filter=if(reason==AttentionReason.HIGH_PRIORITY)CardConditionFilter(urgentOnly=true) else CardConditionFilter(due=reason)
            val actual=rows.filter {filter.matches(it,now,zone)}.map {it.id}.toSet()
            assertEquals(ids,actual);union.addAll(actual)
        }
        assertEquals(rows.filter {AttentionPolicy.reasons(it,now,zone).isNotEmpty()}.map {it.id}.toSet(),union)
        assertTrue(rows.none {AttentionReason.MEDIUM_PRIORITY in AttentionPolicy.reasons(it,now,zone)})
        assertEquals(rows,rows.filter {CardConditionFilter().matches(it,now,zone)})
    }

    @Test fun urgentDueFavoriteDateAndExistingFiltersIntersectWithoutChangingData() {
        val index=ContextCalendarIndex(NodeTreeSnapshot(rows),zone,"p")
        val favorites=setOf("node:urgent-overdue","node:today","node:note")
        val conditions=CardConditionFilter(true,AttentionReason.OVERDUE)
        val result=index.results(day,false,true,favorites,now,zone,conditions)
        assertEquals(listOf("urgent-overdue"),result.map {it.id})
        val existing=NodeFilter(personId="person",tagId="tag",priority=Priority.HIGH,completion=CompletionFilter.PENDING)
        assertEquals(result,result.filter {existing.matches(it,setOf("person"),tagIds=setOf("tag"))})
        assertTrue(result.filter {existing.matches(it,emptySet(),tagIds=setOf("tag"))}.isEmpty())
        assertTrue(result.filter {existing.matches(it,setOf("person"),tagIds=emptySet())}.isEmpty())
        assertTrue(index.results(TemporalRanges.shift(day,1),false,true,favorites,now,zone,conditions).isEmpty())
        assertEquals(setOf("urgent-overdue","today","note"),index.results(null,false,true,favorites,now,zone).map {it.id}.toSet())
        assertEquals(rows,index.tree.nodes)
    }
}
