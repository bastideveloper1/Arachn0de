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
class MetroPresentationTest {
    private val net get()=MetroNetwork.decode(ApplicationProvider.getApplicationContext<Application>().assets.open("metro/santiago-beta1.json").bufferedReader().use {it.readText()})
    @Test fun accentInsensitiveTokenSearchKeepsRealNamesAndRanksExactPrefixPartial() {
        val cases=mapOf(" maipu  " to "Plaza de Maipú","NUNOA" to "Ñuñoa","ESTACION   central" to "Estación Central","los heroes" to "Los Héroes","bio bio" to "Bío Bío","irarrazaval" to "Irarrázaval")
        cases.forEach {(q,name)->assertEquals(name,MetroSearch.stations(net,q).first().name)}
        assertEquals("nunoa",MetroSearch.key("ÑUÑOÁ"))
        assertEquals("Estación Central",MetroSearch.stations(net,"central estacion").single().name)
        assertEquals("Plaza de Maipú",MetroSearch.stations(net,"maip").first().name)
        assertTrue(MetroSearch.stations(net,"maipu","L1").isEmpty())
        assertEquals("San Pablo",MetroSearch.stations(net,"san pablo").first().name)
    }
    @Test fun directionUsesActualOrderBothWaysForEveryAdjacentPairInAllSevenLines() {
        net.lines.values.forEach {line->line.stations.zipWithNext().forEach {(a,b)->
            assertEquals(net.stations.getValue(line.stations.last()).name,MetroPresentation.direction(net,line.id,a,b))
            assertEquals(net.stations.getValue(line.stations.first()).name,MetroPresentation.direction(net,line.id,b,a))
        }}
        assertThrows(IllegalArgumentException::class.java) {MetroPresentation.direction(net,"L1","nunoa","los-heroes")}
    }
    @Test fun transferInstructionsUseFollowingTrainTerminalIncludingViasAndExpressBothWays() {
        val stops=listOf("hospital-el-pino","los-dominicos","cerrillos","plaza-de-maipu")
        listOf(false,true).forEach {express->listOf(stops,stops.reversed()).forEach {ordered->
            val route=MetroPlanner.plan(net,ordered,express,MetroRestrictions())!!
            assertTrue(route.transfers>1)
            route.steps.forEachIndexed {i,s->if(s.kind in setOf(MetroStepKind.TRANSFER,MetroStepKind.CHANGE)) {
                val following=route.steps.drop(i+1).first {it.kind==MetroStepKind.RIDE}
                val instruction=MetroPresentation.instruction(route,i,net)
                assertTrue(instruction.contains(following.line))
                assertTrue(instruction.contains("Dirección ${MetroPresentation.direction(net,following.line,following.from,following.to)}"))
                assertTrue(instruction.contains(MetroPresentation.train(following.service)))
            }}
        }}
    }
    @Test fun stationVisitsDifferFromSegmentsTransfersAndExpressStops() {
        val normal=MetroPlanner.plan(net,listOf("los-heroes","republica"),false,MetroRestrictions())!!
        assertEquals(1,normal.physicalSegments);assertEquals(2,MetroPresentation.stations(normal))
        val route=MetroPlanner.plan(net,listOf("vespucio-norte","hospital-el-pino"),true,MetroRestrictions())!!
        assertEquals(26,MetroPresentation.stations(route));assertTrue(MetroPresentation.stoppingStations(route,net)<26)
        val old=MetroRoute(MetroCatalogRevision.ORIGINAL,listOf("carlos-valdovinos","san-joaquin"),true,listOf(
            MetroStep("carlos-valdovinos","camino-agricola","L5",1,MetroService.RED,MetroStepKind.RIDE),
            MetroStep("camino-agricola","san-joaquin","L5",1,MetroService.RED,MetroStepKind.RIDE)))
        assertEquals(2,MetroPresentation.stoppingStations(old,net)) // Original route must not gain an r1 stop.

        val loop=MetroPlanner.plan(net,listOf("los-heroes","republica","los-heroes"),false,MetroRestrictions())!!
        assertEquals(3,MetroPresentation.stations(loop))
        val session=MetroTracking.start(loop,MetroTime(0,0,1));val halfway=MetroTracking.position(session,MetroTime(60_000,60_000,1))
        assertEquals(2,MetroPresentation.ridesRemaining(loop,halfway));assertTrue(MetroPresentation.positionText(loop,halfway,net).contains("Entre Los Héroes y República"))
    }
    @Test fun automaticPayloadRoundTripLegacyDefaultsAndStrictValidation() {
        val route=MetroPlanner.plan(net,listOf("los-heroes","cerrillos"),false,MetroRestrictions())!!
        val session=MetroTracking.start(route,MetroTime(0,0,1))
        val raw=MetroCodec.journey(MetroJourneyData(route,listOf(session)))
        assertEquals(session,MetroCodec.journey(raw,net).active)
        val legacy=JSONObject(raw).put("version",1).apply {getJSONArray("sessions").getJSONObject(0).apply {remove("automatic");remove("control");remove("routeArchives")}}
        assertFalse(MetroCodec.journey(legacy.toString(),net).active!!.automatic)
        val missing=JSONObject(raw).apply {getJSONArray("sessions").getJSONObject(0).apply {remove("automatic");remove("control");remove("routeArchives")}}
        assertThrows(Exception::class.java) {MetroCodec.journey(missing.toString(),net)}
        val invalid=JSONObject(raw).apply {getJSONArray("sessions").getJSONObject(0).put("automatic","true")}
        assertThrows(Exception::class.java) {MetroCodec.journey(invalid.toString(),net)}
        assertThrows(IllegalArgumentException::class.java) {MetroCodec.journey(JSONObject(raw).put("version",6).toString(),net)}
    }
}
