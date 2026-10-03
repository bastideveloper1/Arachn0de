package com.r0ybt.arachn0de.domain

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Test
import org.junit.Assert.*
import java.util.*

class PriorityAttentionTest {
    private val zone=TimeZone.getTimeZone("UTC")
    private val now=CalendarDates.labelInstant(CalendarDay(2026,10,5))
    private fun node(id: String, priority: Priority=Priority.NONE,due: Long?=null,parent: String?=null) = Node(id,"p",parent,id,"",false,0,1,1,false,dueAt=due,priority=priority)
    private fun attention(vararg nodes: Node)=AttentionSnapshot(NodeTreeSnapshot(nodes.toList()),now,zone)
    @Test fun defaultAndLatentEligibility() {
        assertEquals(Priority.NONE,node("n").priority)
        assertEquals(Priority.NONE,node("note",Priority.HIGH).copy(purpose=NodePurpose.NOTE).effectivePriority)
        assertEquals(Priority.NONE,node("layer",Priority.HIGH).copy(hasChildren=true).effectivePriority)
    }
    @Test fun highAloneIncludesUndatedAndFarFutureButOtherPrioritiesDoNot() {
        for (priority in Priority.entries) {
            val result=attention(node("n",priority)); assertEquals(priority==Priority.HIGH,result.tasks.isNotEmpty())
        }
        assertEquals(listOf(AttentionReason.HIGH_PRIORITY),attention(node("future",Priority.HIGH,now+30*86400000L)).reasonsByNodeId.getValue("future"))
    }
    @Test fun explicitMultipleReasonsAndConservativeMedium() {
        for ((due,reason) in listOf(now-1 to AttentionReason.OVERDUE,now+1000 to AttentionReason.DUE_TODAY,now+86400000 to AttentionReason.UPCOMING)) {
            for (priority in Priority.entries) {
                val reasons=attention(node("n",priority,due)).reasonsByNodeId.getValue("n")
                assertEquals(reason,reasons.first())
                assertEquals(if (priority==Priority.HIGH || priority==Priority.MEDIUM) 2 else 1,reasons.size)
                if (priority==Priority.MEDIUM) assertEquals(AttentionReason.MEDIUM_PRIORITY,reasons.last())
            }
        }
    }
    @Test fun completedNotesAndStructuralNodesExcludedAndPriorityDoesNotInherit() {
        val result=attention(node("done",Priority.HIGH).copy(isCompleted=true),node("note",Priority.HIGH).copy(purpose=NodePurpose.NOTE),
            node("root",Priority.HIGH),node("child",parent="root"))
        assertTrue(result.tasks.isEmpty()); assertEquals(0,result.byProjectId.getValue("p").total)
    }
    @Test fun propagationCountsEachNodeOnceAndRetainsPath() {
        val result=attention(node("root"),node("branch",parent="root"),node("high",Priority.HIGH,parent="branch"),node("late",Priority.HIGH,now-1,"branch"))
        assertEquals(2,result.tasks.size); assertEquals(2,result.byNodeId.getValue("root").total)
        assertEquals(1,result.byNodeId.getValue("root").priorityOnly); assertEquals(1,result.byNodeId.getValue("root").overdue)
        assertEquals(listOf("root","branch","high"),result.pathTo("high").map { it.id })
        assertEquals(Priority.NONE,result.tree.nodesById.getValue("root").priority)
    }
    @Test fun exactDeterministicOrderUrgencyBeforeImportanceDateThenPriorityThenStableIds() {
        val nodes=listOf(node("late-low",Priority.LOW,now-100),node("late-high",Priority.HIGH,now-100),node("late-earlier",Priority.NONE,now-200),
            node("today",Priority.MEDIUM,now+100),node("soon",Priority.NONE,now+86400000),node("high-future",Priority.HIGH,now+30*86400000L),node("high-undated",Priority.HIGH))
        val expected=listOf("late-earlier","late-high","late-low","today","soon","high-future","high-undated")
        assertEquals(expected,AttentionSnapshot(NodeTreeSnapshot(nodes),now,zone).tasks.map { it.id })
        assertEquals(expected,AttentionSnapshot(NodeTreeSnapshot(nodes.reversed()),now,zone).tasks.map { it.id })
    }
    @Test fun existingTemporalBoundariesAndScheduledPolicyRemainDistinct() {
        assertEquals(AttentionReason.DUE_TODAY,attention(node("n",due=now)).reasonsByNodeId.getValue("n").first())
        assertEquals(AttentionReason.OVERDUE,AttentionSnapshot(NodeTreeSnapshot(listOf(node("n",due=now))),now+1,zone).reasonsByNodeId.getValue("n").first())
        val scheduled=node("n",Priority.MEDIUM,now+100).copy(startAt=now+10)
        assertTrue(attention(scheduled).tasks.isEmpty()); assertEquals(listOf(AttentionReason.HIGH_PRIORITY),attention(scheduled.copy(priority=Priority.HIGH)).reasonsByNodeId.getValue("n"))
    }
    @Test fun todayUsesLocalCivilDateAcrossZones() {
        val n=node("n",due=now+10*3600000)
        val utc=attention(n); val tokyo=AttentionSnapshot(NodeTreeSnapshot(listOf(n)),now,TimeZone.getTimeZone("Asia/Tokyo"))
        assertEquals(AttentionReason.DUE_TODAY,utc.reasonsByNodeId.getValue("n").first())
        assertEquals(AttentionReason.UPCOMING,tokyo.reasonsByNodeId.getValue("n").first())
    }
    @Test fun allPriorityFiltersAndFiveWayAnd() {
        val nodes=Priority.entries.map { node(it.name,it,now) }
        for (priority in Priority.entries) assertEquals(listOf(priority.name),NodeFilter(priority=priority).apply(nodes,emptyMap()).map { it.id })
        assertEquals(4,NodeFilter().apply(nodes,emptyMap()).size)
        val n=node("n",Priority.HIGH,now); val filter=NodeFilter(TemporalRange(now,now+1),"roy",CompletionFilter.PENDING,"bug",Priority.HIGH)
        assertTrue(filter.matches(n,setOf("roy"),tagIds=setOf("bug")))
        assertFalse(filter.matches(n,setOf("ana"),tagIds=setOf("bug"))); assertFalse(filter.matches(n,setOf("roy"),tagIds=emptySet()))
        assertFalse(filter.matches(n.copy(isCompleted=true),setOf("roy"),tagIds=setOf("bug")))
        assertFalse(filter.matches(n.copy(dueAt=now+1),setOf("roy"),tagIds=setOf("bug")))
        assertFalse(NodeFilter(priority=Priority.NONE).matches(n.copy(purpose=NodePurpose.NOTE),emptySet()))
    }
    @Test fun scopedContextAndCalendarKeepEligibilityAndScope() {
        val root=node("root"); val branch=node("branch",parent="root"); val child=node("match",Priority.HIGH,now,"branch")
        val tree=NodeTreeSnapshot(listOf(root,branch,child,node("outside",Priority.HIGH,now)))
        val rows=ScopedNodeFilter.apply(tree,"p","root",NodeFilter(priority=Priority.HIGH),emptyMap(),emptyMap())
        assertEquals(listOf("branch","match"),rows.map { it.node.id }); assertFalse(rows.first().isMatch)
        val calendar=FilteredCalendar(CalendarSnapshot(tree,zone),NodeFilter(priority=Priority.HIGH),emptyMap())
        assertEquals(setOf("match","outside"),calendar.tasks.map { it.id }.toSet())
    }
    @Test fun tenThousandLevelsAreIterativeAndUndatedHighPropagates() {
        val nodes=(0 until 10000).map { node("$it",if (it==9999) Priority.HIGH else Priority.NONE,parent=if (it==0) null else "${it-1}") }
        val result=AttentionSnapshot(NodeTreeSnapshot(nodes),now,zone)
        assertEquals(listOf("9999"),result.tasks.map { it.id }); assertEquals(1,result.byNodeId.getValue("0").total); assertEquals(10000,result.pathTo("9999").size)
    }
}
