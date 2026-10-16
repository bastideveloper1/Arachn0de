package com.r0ybt.arachn0de.report

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Test
import org.junit.Assert.*

class ObligationReportStateTest {
    @Test fun selectedMonthAndStateFilterRowsCountsAndMoneyTogether() {
        val source=(0..2).flatMap {i->listOf(ReportFixture.node("pending$i"),ReportFixture.node("paid$i",completed=true))}+
            listOf(ReportFixture.node("other-pending",due=ReportFixture.instant(9)),ReportFixture.node("other-paid",completed=true,due=ReportFixture.instant(11)))
        val snapshot=ReportFixture.snapshot(FinancialPeriod.THIS_MONTH,source=source)
        listOf(false,true,null).forEach {completed->
            val data=ObligationReportData.from(snapshot,ReportFixture.projects,null,ReportFixture.now,ReportFixture.locale,completed)
            assertEquals(if(completed==null) 6 else 3,data.obligations.size)
            assertTrue(data.obligations.none {it.title.startsWith("other")})
            if(completed!=null) assertTrue(data.obligations.all {it.status==if(completed) "Completada" else "Pendiente"})
            val currency=data.currencies.single()
            assertEquals(Money.format(java.math.BigInteger.valueOf(if(completed==null) 300000L else 150000L),"CLP",ReportFixture.locale),currency.total)
            assertEquals(Money.format(java.math.BigInteger.valueOf(if(completed==true) 0L else 150000L),"CLP",ReportFixture.locale),currency.pending)
            assertEquals(Money.format(java.math.BigInteger.valueOf(if(completed==false) 0L else 150000L),"CLP",ReportFixture.locale),currency.completed)
            assertTrue(data.counts.contains(if(completed==true) "0 pendientes" else "3 pendientes"))
        }
        assertEquals(6,snapshot.tasks.size);assertEquals(3,snapshot.tasks.count {it.isCompleted})
    }
    @Test fun stateFilterPreservesPersonAndExplicitlyCombinedReports() {
        val snapshot=ReportFixture.snapshot(FinancialPeriod.ALL,"r")
        val data=ObligationReportData.from(snapshot,ReportFixture.projects,"Roy",ReportFixture.now,ReportFixture.locale,false)
        assertEquals(listOf("Cuota notebook"),data.obligations.map {it.title});assertEquals("Roy",data.person)
        val all=ReportFixture.data(ReportFixture.snapshot())
        assertTrue(all.obligations.any {it.status=="Completada"})
        assertTrue(runCatching {ObligationReportData.from(snapshot,ReportFixture.projects,"Roy",ReportFixture.now,ReportFixture.locale,true)}.isFailure)
    }
}
