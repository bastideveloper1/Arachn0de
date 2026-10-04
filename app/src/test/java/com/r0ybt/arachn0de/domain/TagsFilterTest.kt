package com.r0ybt.arachn0de.domain

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Test
import org.junit.Assert.*
import java.util.TimeZone

class TagsFilterTest {
    private fun node(id: String,parent: String? = null,children: Boolean = false) = Node(id,"p",parent,id,"",false,0,1,1,children,dueAt=10,purpose=if(children) NodePurpose.LAYER else NodePurpose.ACTION)
    @Test fun deepProjectionIsIterativeAndContextIsNotAMatch() {
        val nodes = (0 until 10000).map { node("$it",if (it==0) null else "${it-1}",it<9999) }
        val tree = NodeTreeSnapshot(nodes)
        val rows = ScopedNodeFilter.apply(tree,"p",null,NodeFilter(tagId="t"),emptyMap(),mapOf("9999" to setOf("t")))
        assertEquals(10000,rows.size); assertEquals(9999,rows.last().depth); assertEquals(1,rows.count { it.isMatch })
    }
    @Test fun fourDimensionsAndCalendarEligibilityArePreserved() {
        val eligible = node("task")
        val tree = NodeTreeSnapshot(listOf(eligible,node("note").copy(purpose=NodePurpose.NOTE),node("undated").copy(dueAt=null)))
        val source = CalendarSnapshot(tree,TimeZone.getTimeZone("UTC"))
        val people = mapOf("task" to setOf("p")); val tags = tree.nodes.associate { it.id to setOf("t") }
        val filter = NodeFilter(TemporalRange(0,20),"p",CompletionFilter.PENDING,"t")
        assertEquals(listOf(eligible),FilteredCalendar(source,filter,people,tags).tasks)
        assertTrue(FilteredCalendar(source,filter.copy(tagId="absent"),people,tags).tasks.isEmpty())
        assertTrue(FilteredCalendar(source,filter.copy(completion=CompletionFilter.COMPLETED),people,tags).tasks.isEmpty())
        assertTrue(FilteredCalendar(source,filter.copy(personId="absent"),people,tags).tasks.isEmpty())
        assertEquals(listOf(eligible),source.tasksByDay.values.flatten())
    }
}
