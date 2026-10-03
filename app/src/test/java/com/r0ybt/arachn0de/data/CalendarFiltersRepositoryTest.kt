package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[24,28])
class CalendarFiltersRepositoryTest {
    private lateinit var db: Arachn0deDatabase
    private lateinit var nodes: NodeRepository
    private lateinit var people: PersonRepository
    private val zone = TimeZone.getTimeZone("UTC")
    private val now = CalendarDates.labelInstant(CalendarDay(2026,10,5))
    @Before fun setup() = runBlocking {
        val context=RuntimeEnvironment.getApplication(); context.deleteDatabase("arachn0de.db")
        db=Arachn0deDatabase.create(context); nodes=NodeRepository(db) { now }; people=PersonRepository(db,AvatarStore(context))
        db.projectDao().insert(ProjectEntity("p","Proyecto","",0,1,1)); people.save("roy","Roy",null); Unit
    }
    @After fun cleanup() { db.close(); RuntimeEnvironment.getApplication().deleteDatabase("arachn0de.db") }
    private suspend fun filtered(filter: NodeFilter): FilteredCalendar {
        val assignments = people.observeAllAssignments().first().mapValues { (_,p) -> p.map { it.id }.toSet() }
        return FilteredCalendar(CalendarSnapshot(nodes.observeAllState().first(),zone),filter,assignments)
    }
    @Test fun recurrenceMaterializedOnlyOnceAlongsideNormalTaskAndFinancialNode() = runBlocking {
        val scheduleDay=RecurrenceSchedule.parse("2026-10-05")
        nodes.recurrence.create(RecurrenceRuleEntity("rule","p",null,"Teléfono","",15000,"CLP",scheduleDay,"MONTHLY",1,null,0,"ACTIVE","UTC",0,null),setOf("roy"))
        repeat(2) { nodes.recurrence.materializeDue() }
        val task=nodes.createNode("p",null,"Normal",dueAt=now,responsibleIds=setOf("roy"))
        val bill=nodes.createNode("p",null,"USD",dueAt=now,obligation=Obligation(1050,"USD"),responsibleIds=setOf("roy"))
        val criteria=NodeFilter(TemporalRanges.resolve(TimeFilter.THIS_MONTH,now,zone,Locale.FRANCE),"roy",CompletionFilter.PENDING)
        val results=filtered(criteria).tasks
        assertEquals(3,results.size); assertEquals(3,results.map { it.id }.toSet().size)
        assertEquals(1,db.recurrenceDao().rules().size); assertEquals(1,db.recurrenceDao().occurrences().size)
        assertEquals(setOf("CLP","USD"),results.mapNotNull { it.obligation?.currencyCode }.toSet())
        val occurrence=results.single { it.id != task.id && it.id != bill.id }
        nodes.setCompleted(occurrence.id,true)
        assertEquals(2,filtered(criteria).tasks.size)
        assertEquals(listOf(occurrence.id),filtered(criteria.copy(completion=CompletionFilter.COMPLETED)).tasks.map { it.id })
        assertEquals("ACTIVE",db.recurrenceDao().get("rule")!!.status)
    }
    @Test fun liveAssignmentDateStateAndDeletionChangesUseExistingFlowsAndDoNotWriteFilters() = runBlocking {
        val task=nodes.createNode("p",null,"Task",dueAt=now)
        val criteria=NodeFilter(TemporalRanges.day(CalendarDay(2026,10,5),zone),"roy",CompletionFilter.PENDING)
        assertTrue(filtered(criteria).tasks.isEmpty())
        people.setResponsiblePeople(task.id,setOf("roy")); assertEquals(listOf(task.id),filtered(criteria).tasks.map { it.id })
        nodes.setCompleted(task.id,true); assertTrue(filtered(criteria).tasks.isEmpty())
        assertEquals(1,filtered(criteria.copy(completion=CompletionFilter.COMPLETED)).tasks.size)
        nodes.setCompleted(task.id,false); nodes.updateNodeWithDates(task.id,"Task","",null,now+86400000)
        assertTrue(filtered(criteria).tasks.isEmpty()); assertEquals(1,filtered(criteria.copy(range=null)).tasks.size)
        nodes.deleteNode(task.id); assertTrue(filtered(criteria.copy(range=null)).tasks.isEmpty())
        assertEquals(9,db.openHelper.readableDatabase.version)
    }
}
