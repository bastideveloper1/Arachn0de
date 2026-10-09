package com.r0ybt.arachn0de.metro

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.time.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[24,28],application=Application::class)
class MetroScheduleTest {
    private val net get()=ApplicationProvider.getApplicationContext<Application>().assets.open("metro/santiago-beta1.json").bufferedReader().use {MetroNetwork.decode(it.readText())}
    private fun wall(time:String,date:String="2026-10-09")=LocalDateTime.parse("${date}T$time").atZone(ZoneId.of("America/Santiago")).toInstant().toEpochMilli()
    private fun plan(time:String,stops:List<String>,schedule:MetroSchedule=MetroSchedule(),date:String="2026-10-09")=MetroPlanner.planAt(net,stops,wall(time,date),MetroRestrictions(),schedule)
    @Test fun morningAfternoonLimitsWeekendsHolidaysAndUnknownLinesAreExplicit() {
        val schedule=MetroSchedule(setOf("2026-10-09"))
        assertEquals(false,schedule.express("L2",1,wall("07:00")))
        for(line in listOf("L2","L4","L5")) for(direction in listOf(-1,1)) {
            assertEquals(false,MetroSchedule().express(line,direction,wall("05:59")))
            assertEquals(true,MetroSchedule().express(line,direction,wall("06:00")))
            assertEquals(false,MetroSchedule().express(line,direction,wall("09:00")))
            assertEquals(true,MetroSchedule().express(line,direction,wall("18:00")))
            assertEquals(false,MetroSchedule().express(line,direction,wall("21:00")))
            assertEquals(false,MetroSchedule().express(line,direction,wall("07:00","2026-10-10")))
        }
        assertEquals(false,MetroSchedule().express("L1",1,wall("07:00")))
        assertNull(MetroSchedule(availableLines=emptySet()).express("L5",1,wall("07:00")))
    }
    @Test fun normalOutsidePeakAndExpressWithinNeverAuthorizeAWrongColorStop() {
        val normal=plan("12:00",listOf("las-parcelas","monte-tabor"))!!
        assertTrue(normal.steps.all {it.service==MetroService.NORMAL})
        val peak=plan("07:00",listOf("las-parcelas","monte-tabor"))!!
        assertTrue(peak.usedExpress);assertTrue(peak.serviceChanges>0 || peak.transfers>0)
        peak.steps.forEachIndexed {i,s->if(s.kind==MetroStepKind.RIDE && peak.steps.getOrNull(i+1)?.kind!=MetroStepKind.RIDE) assertTrue(net.stops(s.to,s.line,s.service))}
        assertEquals(normal,MetroCodec.journey(MetroCodec.journey(MetroJourneyData(normal)),net).plan)
        assertEquals(5,JSONObject(MetroCodec.journey(MetroJourneyData(normal))).getInt("version"))
        assertEquals(peak,MetroCodec.journey(MetroCodec.journey(MetroJourneyData(peak)),net).plan)
    }
    @Test fun laterLineUsesItsArrivalTimeNotOnlyDepartureTime() {
        val route=plan("05:58",listOf("republica","toesca"))!!
        assertEquals(MetroService.NORMAL,route.steps.first().service)
        assertEquals(MetroService.GREEN,route.steps.last().service)
        var elapsed=0L
        route.steps.forEach {s->assertTrue(MetroSchedule().permits(s,wall("05:58")+elapsed));elapsed+=s.minutes*60_000L}
    }
    @Test fun leavingPeakBeforeNextLineReplansNormalAndKeepsValidTransfer() {
        val route=plan("08:58",listOf("toesca","republica"))!!
        assertEquals(MetroService.GREEN,route.steps.first().service)
        assertEquals(MetroService.NORMAL,route.steps.last().service)
        assertEquals(route,MetroCodec.journey(MetroCodec.journey(MetroJourneyData(route)),net).plan)
    }
    @Test fun ambiguousBoundaryCrossingAndMissingDataCannotInventAnAttendedTrain() {
        val ride=MetroStep("las-parcelas","monte-tabor","L5",1,MetroService.NORMAL,MetroStepKind.RIDE)
        assertFalse(MetroSchedule().permits(ride,wall("05:59")))
        val closed=net.stations.keys-listOf("las-parcelas","monte-tabor")
        assertNull(MetroPlanner.planAt(net,listOf("las-parcelas","monte-tabor"),wall("05:59"),MetroRestrictions(closed=closed)))
        assertNull(plan("07:00",listOf("las-parcelas","monte-tabor"),MetroSchedule(availableLines=emptySet())))
    }
    @Test fun referenceDateAndStopsAreValidatedAndHistoricalV4RemainsStrict() {
        val scheduled=plan("12:00",listOf("las-parcelas","monte-tabor"))!!
        val raw=JSONObject(MetroCodec.journey(MetroJourneyData(scheduled)))
        assertThrows(IllegalArgumentException::class.java) {MetroCodec.journey(JSONObject(raw.toString()).put("version",4).toString(),net)}
        raw.getJSONObject("plan").put("departure",-1)
        assertThrows(IllegalArgumentException::class.java) {MetroCodec.journey(raw.toString(),net)}
        val historic=MetroPlanner.plan(net,listOf("las-parcelas","monte-tabor"),false,MetroRestrictions())!!
        val old=MetroCodec.journey(MetroJourneyData(historic));assertEquals(4,JSONObject(old).getInt("version"));assertEquals(historic,MetroCodec.journey(old,net).plan)
    }
}
