package com.r0ybt.arachn0de.metro

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.MetroLearningEventEntity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class MetroPredictionTest {
    private val net=MetroNetwork.decode(ApplicationProvider.getApplicationContext<Application>().assets.open("metro/santiago-beta1.json").bufferedReader().use {it.readText()})
    private val route=MetroPlanner.plan(net,listOf("los-heroes","republica"),false,MetroRestrictions())!!
    private fun t(ms:Long)=MetroTime(1_000_000+ms,100_000+ms,1)
    private fun samples(r:MetroRoute=route,path:List<MetroStep> = r.steps,millis:Long=90_000,count:Int=30,next:MetroStep?=null)=List(count) {i->
        MetroObservation("o$i","a$i","b$i",MetroLearning.context(r.networkVersion,path,next),path,millis,path.sumOf {it.minutes*60_000L},i.toLong())
    }
    @Test fun oneModelDrivesMillisecondTimingMapSummaryPauseCorrectionAndRemaining() {
        val adapted=MetroEstimates.adapt(route,samples())
        assertEquals(120_000L,adapted.baseMillis);assertEquals(90_000L,adapted.totalMillis);assertEquals(2,adapted.minutes)
        var s=MetroTracking.start(adapted,t(0))
        assertEquals(.5f,MetroTracking.position(s,t(45_000)).fraction)
        assertEquals(45_000L,MetroEstimates.remaining(s,t(45_000),true))
        assertEquals("≈ 1 min",MetroEstimates.mapEta(adapted,1,45_000))
        s=MetroTracking.pause(s,t(45_000));assertEquals(45_000L,MetroEstimates.remaining(s,t(300_000),true))
        s=MetroTracking.resume(s,t(300_000));assertEquals(30_000L,MetroEstimates.remaining(s,t(315_000),true))
        s=MetroTracking.confirm(s,route.stops.first(),t(315_000));assertEquals(0L,s.offset)
        assertEquals(90_000L,MetroEstimates.remaining(s,t(315_000),true));assertEquals(adapted,s.originalRoute)
        assertEquals(45_000L,MetroTracking.confirm(s,route.stops.first(),t(315_000),between=true).offset)
    }
    @Test fun accumulatedMeasurementsAdjustOnlyTheirPathWithExplicitIntermediateInference() {
        val longRoute=MetroPlanner.plan(net,listOf("los-heroes","tobalaba"),false,MetroRestrictions())!!
        val path=longRoute.steps.take(3)
        val adapted=MetroEstimates.adapt(longRoute,samples(longRoute,path,270_000))
        assertEquals(listOf(90_000L,90_000L,90_000L),adapted.durations.take(3))
        assertEquals(120_000L,adapted.duration(3));assertEquals(0,adapted.prediction!!.parts.first().first)
        assertEquals(3,adapted.prediction.parts.first().end)
        assertEquals(30,adapted.prediction.parts.sumOf {it.accepted})
        val text=MetroCalculation.lines(adapted,emptyList(),net).joinToString("\n")
        assertTrue(text.contains("Tiempos intermedios inferidos"));assertTrue(text.contains("no son mediciones individuales"))
    }
    @Test fun freezeKeepsOriginalEvidenceEvenWhenTheTrainingSetChanges() {
        val adapted=MetroEstimates.adapt(route,samples())
        assertEquals(adapted,MetroEstimates.adapt(adapted,samples(millis=200_000)))
        assertEquals(200_000L,MetroEstimates.adapt(adapted.copy(prediction=null),samples(millis=200_000)).totalMillis)
        assertNull(MetroEstimates.adapt(route,samples(count=4)).prediction)
    }
    @Test fun payload6RoundTripsOriginalUndoArchivesAndScheduledReferenceWithoutChangingHistoricalReaders() {
        val adapted=MetroEstimates.adapt(route,samples())
        val s=MetroStages.arrive(MetroTracking.start(adapted,t(0)),t(95_000))
        val raw=MetroCodec.journey(MetroJourneyData(adapted,listOf(s)))
        assertEquals(6,JSONObject(raw).getInt("version"))
        assertEquals(MetroJourneyData(adapted,listOf(s)),MetroCodec.journey(raw,net))
        assertEquals(adapted,MetroStages.undoArrival(MetroCodec.journey(raw,net).sessions.single(),t(100_000)).route)
        for(version in 1..5) assertThrows(IllegalArgumentException::class.java) {MetroCodec.journey(JSONObject(raw).put("version",version).toString(),net)}
        val historical=MetroCodec.journey(MetroCodec.journey(MetroJourneyData(route)),net)
        assertNull(historical.plan.prediction);assertEquals(120_000L,historical.plan.totalMillis)
        val changed=MetroDestination.change(MetroTracking.start(adapted,t(0)),route,route.stops.first(),t(1000))
        assertEquals(changed,MetroCodec.journey(MetroCodec.journey(MetroJourneyData(route,listOf(changed))),net).active)
        val scheduled=MetroPlanner.planAt(net,listOf("los-heroes","republica"),java.time.ZonedDateTime.of(2026,10,14,12,0,0,0,java.time.ZoneId.of("America/Santiago")).toInstant().toEpochMilli(),MetroRestrictions())!!
        val scheduledAdapted=MetroEstimates.adapt(scheduled,samples(scheduled))
        assertEquals(scheduledAdapted,MetroCodec.journey(MetroCodec.journey(MetroJourneyData(scheduledAdapted)),net).plan)
    }
    @Test fun malformedOrInconsistentPredictionsAreRejected() {
        val adapted=MetroEstimates.adapt(route,samples())
        fun mutate(key:String,value:Any):String=JSONObject(MetroCodec.journey(MetroJourneyData(adapted))).apply {getJSONObject("plan").getJSONObject("prediction").getJSONArray("parts").getJSONObject(0).put(key,value)}.toString()
        for(pair in listOf("millis" to -1,"millis" to 150_000,"first" to 1,"end" to 2,"accepted" to 101,"mean" to "bad"))
            assertTrue(runCatching {MetroCodec.journey(mutate(pair.first,pair.second),net)}.isFailure)
    }
    @Test fun combinationLearnsOnlyBetweenTwoDoorCapturesAndSeparatesTheOutgoingService() {
        val r=MetroPlanner.plan(net,listOf("las-parcelas","los-dominicos"),false,MetroRestrictions())!!
        val boundary=r.steps.indexOfFirst {it.kind==MetroStepKind.TRANSFER}
        assertTrue(boundary>=0)
        val domain="00000000-0000-0000-0000-000000000001"
        fun capture(index:Int,time:MetroTime):MetroCapture {
            val ride=MetroLearningCodec.referenceRide(r,index)!!
            return MetroCapture(r,r.steps[index].from,index,ride.line,ride.direction,ride.service,MetroCaptureKind.DOORS,time,domain,false,0,true,null,0,r.totalMillis)
        }
        val rows=(0 until 30).flatMap {i->listOf(
            MetroLearningEventEntity("a$i","s$i",0,MetroLearningCodec.encode(capture(boundary,t(i*1_000_000L)))),
            MetroLearningEventEntity("b$i","s$i",1,MetroLearningCodec.encode(capture(boundary+1,t(i*1_000_000L+180_000))))
        )}
        val observations=MetroLearning.observations(rows,net)
        assertTrue(observations.all {it.exclusion==null})
        val adapted=MetroEstimates.adapt(r,observations)
        assertEquals(180_000L,adapted.duration(boundary));assertEquals(120_000L,adapted.duration(0))
        assertTrue(MetroPresentation.instruction(adapted,boundary,net).endsWith("≈ 3 min"))
        assertNotEquals(MetroLearning.context(r.networkVersion,listOf(r.steps[boundary]),r.steps[boundary+1]),
            MetroLearning.context(r.networkVersion,listOf(r.steps[boundary]),r.steps[boundary+1].copy(service=MetroService.RED)))
        val s=MetroTracking.start(adapted,t(0));val arrival=MetroStages.confirmArrival(s,t(500_000))
        assertEquals(MetroPhase.TRANSFERRING,arrival.control!!.phase)
        assertEquals(MetroStages.offset(adapted,boundary),arrival.offset)
        assertEquals(adapted,MetroStages.nextLine(arrival,t(700_000)).route)
    }
    @Test fun aNonStoppingExpressStationCannotTrainEvenWhenAHistoricalCaptureWasMarkedValid() {
        val line=net.lines.getValue("L2")
        val r=MetroPlanner.plan(net,listOf(line.stations.first(),line.stations.last()),true,MetroRestrictions())!!
        val index=r.steps.indexOfFirst {it.kind==MetroStepKind.RIDE && !net.stops(it.to,it.line,it.service)}+1
        assertTrue(index>0)
        fun capture(i:Int):MetroCapture {
            val ride=MetroLearningCodec.referenceRide(r,i)!!
            return MetroCapture(r,r.steps.getOrNull(i)?.from ?: r.stops.last(),i,ride.line,ride.direction,ride.service,MetroCaptureKind.DOORS,
                t(i*100_000L),"00000000-0000-0000-0000-000000000001",false,0,true,null,0,r.totalMillis)
        }
        val rows=listOf(MetroLearningEventEntity("a","s",0,MetroLearningCodec.encode(capture(0))),MetroLearningEventEntity("b","s",1,MetroLearningCodec.encode(capture(index))))
        assertEquals("NO_STOP",MetroLearning.observations(rows,net).single().exclusion)
    }
    @Test fun diagnosticIncludesAppliedWeightsExclusionsReliableDurationAndHelp() {
        val observations=samples()+samples(millis=900_000,count=1).map {it.copy(id="bad",recordedOrder=99)}
        val adapted=MetroEstimates.adapt(route,observations)
        val text=MetroCalculation.lines(adapted,observations,net).joinToString("\n")
        for(expected in listOf("Tiempo estándar","Estimación adaptada","Duración real observada","desfase","Mediciones aceptadas","descartadas","Método estadístico","peso aplicado","Última observación","límites plausibles")) assertTrue(expected,text.contains(expected))
        assertTrue(MetroCalculation.help.any {it.contains("Si notas un desfase")})
        assertTrue(MetroCalculation.help.any {it.contains("primera confirmación")})
    }
}
