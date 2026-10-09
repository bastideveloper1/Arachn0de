package com.r0ybt.arachn0de.metro

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class MetroEngineTest {
    private val raw get()=ApplicationProvider.getApplicationContext<Application>().assets.open("metro/santiago-beta1.json").bufferedReader().use {it.readText()}
    private val net get()=MetroNetwork.decode(raw)
    private fun id(name: String)=net.stations.values.single {it.name==name}.id
    private fun route(a: String,b: String,express: Boolean=false,r: MetroRestrictions=MetroRestrictions())=MetroPlanner.plan(net,listOf(id(a),id(b)),express,r)
    @Test fun catalogHasSevenCurrentLinesStablePhysicalInterchangesAndExpressClasses() {
        assertEquals(126,net.stations.size);assertEquals(143,net.lines.values.sumOf {it.stations.size})
        val ends=mapOf("L1" to ("San Pablo" to "Los Dominicos"),"L2" to ("Vespucio Norte" to "Hospital El Pino"),"L3" to ("Plaza Quilicura" to "Fernando Castillo Velasco"),"L4" to ("Tobalaba" to "Plaza de Puente Alto"),"L4A" to ("Vicuña Mackenna" to "La Cisterna"),"L5" to ("Plaza de Maipú" to "Vicente Valdés"),"L6" to ("Cerrillos" to "Los Leones"))
        ends.forEach { (line,pair)->assertEquals(id(pair.first),net.lines.getValue(line).stations.first());assertEquals(id(pair.second),net.lines.getValue(line).stations.last()) }
        assertEquals(setOf("L1","L2"),net.accesses(id("Los Héroes")).map {it.id}.toSet())
        assertEquals(setOf("L4","L4A"),net.accesses(id("Vicuña Mackenna")).map {it.id}.toSet())
        assertEquals(listOf("R","V","R","C"),net.lines.getValue("L2").express.takeLast(4))
        net.lines.values.forEach { l->l.stations.zipWithNext().forEach { (a,b)->assertNotNull(MetroPlanner.plan(net,listOf(a,b),false,MetroRestrictions())) } }
    }
    @Test fun directTransfersDeterministicCostsAndEveryPhysicalStationAreRetained() {
        val direct=route("Los Héroes","Tobalaba")!!
        assertEquals(0,direct.transfers);assertEquals(0,direct.serviceChanges);assertEquals(direct.physicalSegments*2,direct.minutes)
        assertEquals("L1",direct.steps.first().line)
        val combined=route("Vespucio Norte","Los Dominicos")!!;assertTrue(combined.transfers>=1)
        assertEquals(combined.physicalSegments*2+combined.transfers*4+combined.serviceChanges*4,combined.minutes)
        repeat(5) {assertEquals(combined,route("Vespucio Norte","Los Dominicos"))}
        val several=route("Hospital El Pino","Plaza Quilicura")!!;assertTrue(several.transfers>=1)
        listOf(direct,combined,several).forEach {assertEquals(it,MetroCodec.journey(MetroCodec.journey(MetroJourneyData(it)),net).plan)}
    }
    @Test fun expressMustTraverseSkippedStationsAndCannotBoardOrAlightThere() {
        val normal=route("Vespucio Norte","Hospital El Pino")!!
        val express=route("Vespucio Norte","Hospital El Pino",true)!!
        assertTrue(express.usedExpress);assertEquals(normal.minutes,express.minutes);assertEquals(25,express.physicalSegments)
        val changes=route("Copa Lo Martínez","Observatorio",true)!!
        assertTrue(changes.serviceChanges>0 || changes.transfers>0)
        assertTrue(changes.minutes>2)
        assertEquals(changes,MetroCodec.journey(MetroCodec.journey(MetroJourneyData(changes)),net).plan)
        express.steps.filter {it.kind==MetroStepKind.RIDE}.zipWithNext().forEach { (a,b)->assertEquals(a.to,b.from) }
        val noExpress=route("Cerrillos","Franklin",true)!!;assertFalse(noExpress.usedExpress)
    }
    @Test fun closedPassengersMayPassButCannotTransferBoardOrAlightAndRailsMayNotPass() {
        val closed=MetroRestrictions(closed=setOf(id("Baquedano")))
        val through=route("Los Héroes","Tobalaba",r=closed)!!;assertTrue(through.steps.any {it.to==id("Baquedano")})
        assertFalse(through.steps.any {it.from==id("Baquedano") && it.kind!=MetroStepKind.RIDE})
        assertNull(route("Baquedano","Tobalaba",r=closed));assertNull(route("Tobalaba","Baquedano",r=closed))
        val key=MetroRestrictions.segment("L2",id("Hospital El Pino"),id("Copa Lo Martínez"))
        assertNull(route("Hospital El Pino","Tobalaba",r=MetroRestrictions(interrupted=setOf(key))))
        val alternate=route("Los Héroes","Tobalaba",r=MetroRestrictions(avoided=setOf(id("Baquedano"))));assertNotNull(alternate)
        assertNotNull(route("Hospital El Pino","Copa Lo Martínez",r=MetroRestrictions(avoided=setOf(id("Copa Lo Martínez")))))
        assertTrue(MetroPlanner.affected(route("Hospital El Pino","Tobalaba")!!,net,MetroRestrictions(interrupted=setOf(key))))
    }
    @Test fun multiLegPlansPreserveOrderWithoutDwellAndCodecRejectsTeleportation() {
        val stops=listOf(id("Los Héroes"),id("Tobalaba"),id("Cerrillos"),id("Plaza de Maipú"))
        val r=MetroPlanner.plan(net,stops,false,MetroRestrictions())!!
        assertEquals(stops,r.stops);assertEquals(2,r.steps.count {it.kind==MetroStepKind.WAYPOINT})
        assertEquals(stops.zipWithNext().sumOf { (a,b)->MetroPlanner.plan(net,listOf(a,b),false,MetroRestrictions())!!.minutes },r.minutes)
        assertEquals(r,MetroCodec.journey(MetroCodec.journey(MetroJourneyData(r)),net).plan)
        val repeated=MetroPlanner.plan(net,listOf(id("Observatorio"),id("Observatorio"),id("Hospital El Pino")),true,MetroRestrictions())!!
        assertEquals(repeated,MetroCodec.journey(MetroCodec.journey(MetroJourneyData(repeated)),net).plan)
        val bad=r.copy(steps=r.steps.toMutableList().apply {set(0,first().copy(to=id("Cerrillos")))})
        assertThrows(IllegalArgumentException::class.java) {MetroCodec.journey(MetroCodec.journey(MetroJourneyData(bad)),net)}
    }
    @Test fun preferencesAndBoundedPayloadRejectUnknownStationsCorruptionAndDuplicates() {
        val p=MetroPreferences(raw,id("Los Héroes"),setOf(id("Tobalaba")),MetroRestrictions(closed=setOf(id("Baquedano"))))
        assertEquals(p,MetroCodec.preferences(MetroCodec.preferences(p)))
        assertThrows(IllegalArgumentException::class.java) {MetroCodec.preferences(MetroCodec.preferences(p.copy(home="no")))}
        assertThrows(IllegalArgumentException::class.java) {MetroCodec.preferences(MetroCodec.preferences(p).replace("\"version\":1","\"version\":1,\"version\":1"))}
        assertThrows(IllegalArgumentException::class.java) {MetroCodec.preferences("x".repeat(MetroCodec.MAX_BYTES+1))}
    }
}
