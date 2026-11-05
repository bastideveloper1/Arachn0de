package com.r0ybt.arachn0de.domain

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class ContextCalendarIndexTest {
    private val day=CalendarDay(2026,12,31)
    private val due=CalendarDates.labelInstant(day)
    private fun task(id:String,project:String?="p",parent:String?=null)=Node(id,project,parent,id,"",false,0,1,1,false,dueAt=due)
    @Test fun rootCoreAndDeepScopeUseAllDescendantsWithoutSiblingsDraftsOrDuplicates() {
        val layers=(0 until 180).map {i->task("layer$i",parent=if(i==0) null else "layer${i-1}").copy(purpose=NodePurpose.LAYER,hasChildren=true)}
        val a=task("deep",parent="layer179");val b=task("direct");val c=task("other",project="q");val draft=task("draft",project=null)
        val source=layers+listOf(a,b,c,draft,task("undated").copy(dueAt=null))
        val tree=NodeTreeSnapshot(source);val utc=TimeZone.getTimeZone("UTC")
        assertEquals(setOf("deep","direct","other"),ContextCalendarIndex(tree,utc).tasksOn(day).map {it.id}.toSet())
        assertEquals(setOf("deep","direct"),ContextCalendarIndex(tree,utc,"p").tasksOn(day).map {it.id}.toSet())
        assertEquals(listOf(a),ContextCalendarIndex(tree,utc,"p","layer179").tasksOn(day))
        assertTrue(ContextCalendarIndex(tree,utc,"q","layer179").tasksOn(day).isEmpty())
        assertTrue(ContextCalendarIndex(tree,utc).tasksOn(TemporalRanges.shift(day,1)).isEmpty())
        assertEquals(source,tree.nodes)
    }
    @Test fun localDaysDuePrecedenceAndStartOnlyReuseExistingDates() {
        val zone=TimeZone.getTimeZone("America/Santiago")
        val boundary=TemporalRanges.day(CalendarDay(2027,1,1),zone).startInclusive
        val dueTask=task("due").copy(startAt=boundary-1,dueAt=boundary)
        val startTask=task("start").copy(startAt=boundary-1,dueAt=null)
        val index=ContextCalendarIndex(NodeTreeSnapshot(listOf(dueTask,startTask)),zone)
        assertEquals(listOf(startTask),index.tasksOn(CalendarDay(2026,12,31)))
        assertEquals(listOf(dueTask),index.tasksOn(CalendarDay(2027,1,1)))
    }
    @Test fun contextualFiltersIntersectWithoutDatesOrWritesAndReuseAttention() {
        val utc=TimeZone.getTimeZone("UTC")
        val layer=task("layer").copy(purpose=NodePurpose.LAYER,hasChildren=true,dueAt=null)
        val nodes=listOf(layer,task("high",parent="layer").copy(dueAt=null,priority=Priority.HIGH),
            task("due",parent="layer"),task("done",parent="layer").copy(isCompleted=true),
            task("note",parent="layer").copy(purpose=NodePurpose.NOTE,dueAt=null),
            task("sibling"),task("foreign",project="q"),task("draft",project=null))
        val favorites=setOf("node:high","node:due","node:done","node:note","node:foreign")
        val tree=NodeTreeSnapshot(nodes)
        for((project,parent) in listOf(null to null,"p" to null,"p" to "layer")) {
            val index=ContextCalendarIndex(tree,utc,project,parent)
            val all=index.results(null,false,false,favorites,due,utc)
            for(date in listOf<CalendarDay?>(null,day)) for(attention in listOf(false,true)) for(favorite in listOf(false,true)) {
                val expected=all.filter {n->(date==null || n in index.tasksOn(date)) &&
                    (!attention || AttentionPolicy.reasons(n,due,utc).isNotEmpty()) && (!favorite || "node:${n.id}" in favorites)}
                assertEquals(expected.map {it.id}.toSet(),index.results(date,attention,favorite,favorites,due,utc).map {it.id}.toSet())
            }
        }
        val deep=ContextCalendarIndex(tree,utc,"p","layer")
        assertEquals(setOf("high","due"),deep.results(null,true,true,favorites,due,utc).map {it.id}.toSet())
        assertEquals(setOf("due"),deep.results(day,true,true,favorites,due,utc).map {it.id}.toSet())
        assertTrue(deep.results(TemporalRanges.shift(day,10),true,true,favorites,due,utc).isEmpty())
        assertEquals(nodes,tree.nodes)
    }
}
