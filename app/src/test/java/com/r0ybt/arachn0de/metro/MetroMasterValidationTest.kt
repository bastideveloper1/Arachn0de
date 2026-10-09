package com.r0ybt.arachn0de.metro

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class MetroMasterValidationTest {
    private val catalog get()=ApplicationProvider.getApplicationContext<Application>().assets.open("metro/santiago-beta1.json").bufferedReader().use {it.readText()}
    private val net by lazy {MetroNetwork.decode(catalog)}
    private val master by lazy {JSONObject(javaClass.getResourceAsStream("/metro/santiago-master-2026-10-09.json")!!.bufferedReader().use {it.readText()})}
    @Test fun all143EntriesMatchMasterInOrderClassificationAndPhysicalConnections() {
        val expectedCounts=mapOf("L1" to 27,"L2" to 26,"L3" to 21,"L4" to 23,"L4A" to 6,"L5" to 30,"L6" to 10)
        assertEquals(143,net.lines.values.sumOf {it.stations.size});assertEquals(126,net.stations.size)
        expectedCounts.forEach { (id,count)->
            val line=net.lines.getValue(id);val expected=master.getJSONArray(id);assertEquals(count,line.stations.size);assertEquals(count,expected.length())
            line.stations.forEachIndexed {i,station->
                val row=expected.getJSONObject(i)
                assertEquals("$id #${i+1}",row.getString("name"),net.stations.getValue(station).name)
                assertEquals("$id ${row.getString("name")}",row.getString("express"),line.express.getOrNull(i).orEmpty())
                val other=net.accesses(station).map {it.id}.toSet()-id
                assertEquals(if(row.isNull("combination")) emptySet<String>() else setOf(row.getString("combination")),other)
                other.forEach {target->assertTrue(net.lines.getValue(target).stations.contains(station));assertTrue(net.accesses(station).any {it.id==id})}
            }
        }
    }
    private fun onlyLine(id: String)=MetroRestrictions(interrupted=net.lines.values.filter {it.id!=id}.flatMap {l->l.stations.zipWithNext().map { (a,b)->MetroRestrictions.segment(l.id,a,b)}}.toSet())
    @Test fun everyNormalPhysicalEdgeIsConsecutiveAndBidirectionalWithoutExpressFiltering() {
        net.lines.values.forEach {l->l.stations.zipWithNext().forEach { (a,b)->listOf(a to b,b to a).forEach { (from,to)->
            val route=MetroPlanner.plan(net,listOf(from,to),false,onlyLine(l.id))!!
            assertEquals(2,route.minutes);assertEquals(1,route.steps.size);assertEquals(from,route.steps.single().from);assertEquals(to,route.steps.single().to)
            assertEquals(l.id,route.steps.single().line);assertEquals(MetroService.NORMAL,route.steps.single().service)
        }}}
    }
    @Test fun all79ExpressAccessesObeyRedGreenAndCommonAndNormalLinesHaveNoExpressStops() {
        var classified=0
        net.lines.values.forEach {l->l.stations.forEachIndexed {i,id->
            assertTrue(net.stops(id,l.id,MetroService.NORMAL))
            val c=l.express.getOrNull(i)
            assertEquals(c in setOf("R","C"),net.stops(id,l.id,MetroService.RED))
            assertEquals(c in setOf("V","C"),net.stops(id,l.id,MetroService.GREEN))
            if(c!=null) classified++
        };assertFalse(net.stops("not-a-station",l.id,MetroService.NORMAL))}
        assertEquals(79,classified)
    }
    private fun assertTrainContinuity(route: MetroRoute) {
        route.steps.forEachIndexed {i,s->
            val next=route.steps.getOrNull(i+1)
            if(s.kind==MetroStepKind.RIDE) {
                val l=net.lines.getValue(s.line)
                assertEquals(s.to,l.stations[l.stations.indexOf(s.from)+s.direction])
                if(i==0) assertTrue(net.stops(s.from,s.line,s.service))
                if(next==null) assertTrue(net.stops(s.to,s.line,s.service))
            } else {
                assertTrue(net.stops(s.from,s.line,s.service));assertNotNull(next);assertTrue(net.stops(s.to,next!!.line,next.service))
                if(s.kind==MetroStepKind.CHANGE && s.service!=next.service) assertEquals("C",net.lines.getValue(s.line).express[net.lines.getValue(s.line).stations.indexOf(s.from)])
            }
        }
        assertEquals(route.physicalSegments*2+(route.transfers+route.serviceChanges)*4,route.minutes)
        assertEquals(route,MetroCodec.journey(MetroCodec.journey(MetroJourneyData(route)),net).plan)
    }
    @Test fun redGreenJourneysOnAllThreeLinesChangeAtACommonStopAndTraverseNonStops() {
        listOf(Triple("L2","dorsal","einstein"),Triple("L4","principe-de-gales","simon-bolivar"),Triple("L5","rodrigo-de-araya","carlos-valdovinos"),Triple("L5","camino-agricola","pedrero")).forEach { (line,a,b)->
            listOf(a to b,b to a).forEach { (from,to)->
                val route=MetroPlanner.plan(net,listOf(from,to),true,onlyLine(line))!!
                assertTrue(route.serviceChanges>0);assertEquals(0,route.transfers);assertTrue(route.physicalSegments>1);assertTrainContinuity(route)
            }
        }
        val all=MetroPlanner.plan(net,listOf("plaza-de-maipu","vicente-valdes"),true,onlyLine("L5"))!!
        assertEquals(29,all.physicalSegments);assertEquals(58,all.minutes);assertTrue(all.steps.any {!net.stops(it.to,it.line,it.service)})
        assertTrainContinuity(all)
    }
    @Test fun multipleTransfersAndRedGreenChangesStayPhysicalInMultiStopJourneys() {
        val route=MetroPlanner.plan(net,listOf("rodrigo-de-araya","carlos-valdovinos","los-dominicos","hospital-el-pino"),true,MetroRestrictions())!!
        assertTrue(route.transfers>=2);assertTrue(route.serviceChanges>=1)
        route.steps.filter {it.kind==MetroStepKind.TRANSFER}.forEach {assertTrue(it.from in net.lines.getValue(it.line).stations);assertTrue(it.from in net.lines.getValue(it.nextLine).stations)}
        // Waypoints separate independently planned legs; their next departure may use another line.
        assertTrainContinuity(route)
        assertEquals(listOf("rodrigo-de-araya","carlos-valdovinos","los-dominicos","hospital-el-pino"),route.stops)
    }
    @Test fun terminalClosuresAreNotPermanentAndUnknownRevisionsAreNotGuessed() {
        val p=MetroPreferences(catalog);assertTrue(p.restrictions.closed.isEmpty())
        assertNotNull(MetroPlanner.plan(net,listOf("hernando-de-magallanes","los-dominicos"),false,p.restrictions))
        val unknown=net.copy(version="unknown-snapshot")
        assertEquals(unknown,MetroCatalogRevision.forPlanning(unknown))
        assertThrows(IllegalArgumentException::class.java) {MetroCatalogRevision.forRoute(unknown,MetroCatalogRevision.CORRECTED)}
    }
}
