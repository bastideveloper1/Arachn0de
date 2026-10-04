package com.r0ybt.arachn0de.domain

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Test
import org.junit.Assert.*
import java.math.BigInteger
import java.util.*

class FinancialSnapshotTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val month = CalendarMonth(2026,10)
    // Explicit structural purposes for the synthetic hierarchy used by these tests.
    private fun fixtureTree(nodes: List<Node>): NodeTreeSnapshot {
        val parents = nodes.mapNotNull { it.parentId }.toSet()
        return NodeTreeSnapshot(nodes.map { if (it.id in parents) it.copy(purpose = NodePurpose.LAYER) else it })
    }
    private fun due(m: Int, d: Int = 15, h: Int = 12) = GregorianCalendar(utc).apply { clear(); set(2026,m-1,d,h,0) }.timeInMillis
    private fun node(id: String, parent: String? = null, project: String = "p", money: Obligation? = null,
        completed: Boolean = false, date: Long? = null, purpose: NodePurpose = NodePurpose.ACTION, created: Long = 1) =
        Node(id,project,parent,id,"",completed,999,created,1,false,dueAt=date,purpose=purpose,obligation=money)
    private fun snapshot(nodes: List<Node>, period: FinancialPeriod = FinancialPeriod.ALL, person: String? = null,
        assignments: Map<String,List<Person>> = emptyMap(), zone: TimeZone = utc) =
        FinancialSnapshot(fixtureTree(nodes),assignments,FinancialSelection(period,person),month,zone)
    private val clp = Obligation(50000,"CLP")
    private val usd = Obligation(1050,"USD")

    @Test fun recursiveScopesCurrenciesCountsStatesAndOrdinaryProgressStayDistinct() {
        val nodes = listOf(node("root"),node("inner","root"),node("pending","root",money=clp,date=due(10)),
            node("paid","inner",money=clp,completed=true,date=due(10)),node("usd","inner",money=usd),
            node("other",project="q",money=clp,completed=true),node("normal","root"),node("note","root",purpose=NodePurpose.NOTE))
        val s=snapshot(nodes)
        val totals=s.byNodeId.getValue("root")
        assertEquals(BigInteger.valueOf(100000),totals.byCurrency.getValue("CLP").totalMinor)
        assertEquals(BigInteger.valueOf(50000),totals.byCurrency.getValue("CLP").pendingMinor)
        assertEquals(BigInteger.valueOf(50000),totals.byCurrency.getValue("CLP").completedMinor)
        assertEquals(BigInteger.valueOf(1050),totals.byCurrency.getValue("USD").totalMinor)
        assertEquals(totals,s.byProjectId.getValue("p")); assertEquals(2,s.byNodeId.getValue("inner").count)
        assertEquals(4,s.summary.count); assertEquals(2,s.summary.pendingCount); assertEquals(2,s.summary.completedCount)
        assertEquals(FinancialState.PARTIAL,s.summary.state)
        assertEquals(FinancialState.COMPLETED,s.byProjectId.getValue("q").state)
        assertEquals(FinancialState.PENDING,s.byNodeId.getValue("pending").state)
        assertEquals(FinancialState.NO_OBLIGATIONS,s.byNodeId.getValue("note").state)
        assertEquals(4,s.tree.projectProgressById.getValue("p").total)
    }
    @Test fun selectionUsesLocalDueMonthAndFullResponsibilityMembershipWithoutDuplication() {
        val roy=Person("r","Roy",null); val scarlett=Person("s","Scarlett",null)
        val nodes=listOf(node("oct",money=clp,date=due(10)),node("sep",money=usd,date=due(9)),
            node("nov",money=clp,date=due(11)),node("none",money=clp),node("edge",money=clp,date=due(11,1,1)))
        val assignments=mapOf("oct" to listOf(roy,scarlett),"none" to listOf(roy))
        assertEquals(listOf("oct"),snapshot(nodes,FinancialPeriod.THIS_MONTH,"r",assignments).tasks.map { it.id })
        assertEquals(BigInteger.valueOf(50000),snapshot(nodes,FinancialPeriod.THIS_MONTH,"s",assignments).summary.byCurrency.getValue("CLP").totalMinor)
        assertEquals(5,snapshot(nodes).summary.count)
        assertEquals(listOf("sep"),snapshot(nodes,FinancialPeriod.PREVIOUS_MONTH).tasks.map { it.id })
        assertEquals(listOf("edge","nov"),snapshot(nodes,FinancialPeriod.NEXT_MONTH).tasks.map { it.id })
        assertEquals(listOf("oct","edge"),snapshot(nodes,FinancialPeriod.THIS_MONTH,zone=TimeZone.getTimeZone("GMT-03:00")).tasks.map { it.id })
        assertEquals(2,snapshot(nodes,person="r",assignments=assignments).summary.count)
        assertTrue(snapshot(nodes,person="missing",assignments=assignments).tasks.isEmpty())
        assertTrue(snapshot(listOf(node("parent"),node("child","parent",money=clp)),person="r",
            assignments=mapOf("parent" to listOf(roy))).tasks.isEmpty())
        val saved=snapshot(nodes,assignments=assignments)
        assertEquals(assignments,saved.responsibleByNode)
    }
    @Test fun actualLeavesOnlyAndDeterministicOrderIgnoreManualPositionsAndInputOrder() {
        val nodes=listOf(node("layer",money=clp,date=due(10)),node("a","layer",money=usd,date=due(10),created=2),
            node("b","layer",money=clp,date=due(10),created=2),node("first",money=clp,date=due(10),created=1),
            node("note",money=clp,date=due(10),purpose=NodePurpose.NOTE),node("undated",money=clp))
        val s=snapshot(nodes)
        assertEquals(listOf("first","a","b","undated"),s.tasks.map { it.id })
        assertEquals(s.tasks,snapshot(nodes.reversed()).tasks)
        assertEquals(listOf("layer","a"),s.pathTo("a").map { it.id })
        assertTrue(s.pathTo("missing").isEmpty())
        assertEquals(2,s.byNodeId.getValue("layer").count)
    }
    @Test fun aggregatesBeyondLongRemainExactAndFormatZeroAndLargeTotals() {
        val huge=Obligation(Long.MAX_VALUE,"USD")
        val s=snapshot(listOf(node("a",money=huge),node("b",money=huge,completed=true)))
        val totals=s.summary.byCurrency.getValue("USD")
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE)*BigInteger.TWO,totals.totalMinor)
        assertEquals(totals.totalMinor,totals.pendingMinor+totals.completedMinor)
        assertEquals("$184,467,440,737,095,516.14 USD",Money.format(totals.totalMinor,"USD",Locale.US))
        assertEquals("$0.00 USD",Money.format(BigInteger.ZERO,"USD",Locale.US))
    }
    @Test fun deepTreesEmptyScopesAndMonthYearBoundariesNeedNoRecursiveCalls() {
        val nodes=List(10000) { i -> node("n$i",if(i==0) null else "n${i-1}",money=if(i==9999) clp else null) }
        val s=snapshot(nodes)
        assertEquals(1,s.byNodeId.getValue("n0").count); assertEquals(10000,s.pathTo("n9999").size)
        assertEquals(FinancialState.NO_OBLIGATIONS,snapshot(emptyList()).summary.state)
        assertEquals(CalendarMonth(2025,12),FinancialPeriod.PREVIOUS_MONTH.month(CalendarMonth(2026,1)))
        assertEquals(CalendarMonth(2027,1),FinancialPeriod.NEXT_MONTH.month(CalendarMonth(2026,12)))
    }
}
