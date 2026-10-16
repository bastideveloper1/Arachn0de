package com.r0ybt.arachn0de.metro

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.MetroLearningEventEntity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class MetroLearningTest {
    private val net=MetroNetwork.decode(ApplicationProvider.getApplicationContext<Application>().assets.open("metro/santiago-beta1.json").bufferedReader().use {it.readText()})
    private val route=MetroPlanner.plan(net,listOf("los-heroes","tobalaba"),false,MetroRestrictions())!!
    private val domain="00000000-0000-0000-0000-000000000001"
    private fun capture(index:Int,ms:Long,kind:MetroCaptureKind=MetroCaptureKind.DOORS):MetroCapture {
        val ride=MetroLearningCodec.referenceRide(route,index)!!
        return MetroCapture(route,route.steps.getOrNull(index)?.from ?: route.stops.last(),index,ride.line,ride.direction,ride.service,kind,
            MetroTime(1_000_000+ms,ms,1),domain,false,0,kind!=MetroCaptureKind.CORRECTION,if(kind==MetroCaptureKind.CORRECTION) "CORRECTION" else null,0,route.minutes*60_000L)
    }
    private fun row(seq:Long,e:MetroCapture)=MetroLearningEventEntity("e$seq","s",seq,MetroLearningCodec.encode(e))
    private fun observations(vararg e:MetroCapture)=MetroLearning.observations(e.mapIndexed {i,c->row(i.toLong(),c)},net)
    private fun sample(id:Int,ms:Long,steps:List<MetroStep> = route.steps.take(1))=MetroObservation("$id","a$id","b$id",MetroLearning.context(route.networkVersion,steps),steps,ms,steps.sumOf {it.minutes*60_000L},id.toLong())

    @Test fun sparseConfirmationsYieldOnlyAdjacentOriginalPairsWithDirectAndAccumulatedMeasurements() {
        val events=listOf(capture(0,0),capture(1,90_000),capture(2,200_000),capture(5,540_000),capture(route.steps.size,1_400_000,MetroCaptureKind.ARRIVAL))
        val result=MetroLearning.observations(events.mapIndexed {i,e->row(i.toLong(),e)},net)
        assertEquals(4,result.size);assertEquals(listOf(true,true,false,false),result.map {it.direct})
        assertEquals(listOf(90_000L,110_000L,340_000L,860_000L),result.map {it.millis})
        assertTrue(result.all {it.exclusion==null})
        assertTrue(observations(events.first()).isEmpty())
    }
    @Test fun correctionIsABarrierAndNextOpeningEstablishesANewReliableStart() {
        val result=observations(capture(0,0),capture(1,100_000,MetroCaptureKind.CORRECTION),capture(2,200_000),capture(3,300_000))
        assertEquals(listOf("CORRECTION","CORRECTION",null),result.map {it.exclusion})
    }
    @Test fun backgroundAndCalendarJumpsDoNotInvalidateSameBootMonotonicMeasurements() {
        val result=observations(capture(0,0),capture(1,100_000).copy(time=MetroTime(10,100_000,1)))
        assertNull(result.single().exclusion);assertEquals(100_000L,result.single().millis)
    }
    @Test fun rebootRestorePauseAndNonMonotonicTimeAreExcluded() {
        val first=capture(0,100)
        assertEquals("REBOOT",observations(first,capture(1,100_000).copy(time=MetroTime(1_100_000,100_000,2))).single().exclusion)
        assertEquals("RESTORED_CLOCK",observations(first,capture(1,100_000).copy(clockDomain="00000000-0000-0000-0000-000000000002")).single().exclusion)
        assertEquals("PAUSE",observations(first,capture(1,100_000).copy(pauseMillis=1000)).single().exclusion)
        assertEquals("NON_MONOTONIC",observations(first,capture(1,50)).single().exclusion)
    }
    @Test fun undoPreservesOriginalEventsAndInvalidatesBothAdjacentContributions() {
        val rows=listOf(row(0,capture(0,0)),row(1,capture(1,100_000)).copy(invalidReason="UNDONE"),row(2,capture(2,200_000)))
        assertTrue(MetroLearning.observations(rows,net).all {it.exclusion=="UNDONE"})
    }
    @Test fun startupUsesBaseAndInertiaGrowsWithoutDoubleCounting() {
        val steps=route.steps.take(1)
        val four=(0..3).map {sample(it,90_000)}
        assertEquals(120_000L,MetroLearning.estimate(route.networkVersion,steps,four).finalMillis)
        val five=four+sample(4,90_000)
        val estimate=MetroLearning.estimate(route.networkVersion,steps,five+five)
        assertEquals(5,estimate.accepted.size);assertEquals(115_000L,estimate.finalMillis)
        assertEquals(90_000L,MetroLearning.estimate(route.networkVersion,steps,(0..29).map {sample(it,90_000)}).finalMillis)
    }
    @Test fun robustFilterAndWindowKeepAnomaliesFromDistortingEstimatesAndRetainReasons() {
        val samples=(0..104).map {sample(it,90_000)}+sample(105,400_000)+sample(106,900_000)
        val result=MetroLearning.estimate(route.networkVersion,route.steps.take(1),samples)
        assertEquals(90_000L,result.finalMillis)
        assertEquals(98,result.accepted.size)
        assertEquals(7,result.discarded.count {it.exclusion=="OUTSIDE_WINDOW"})
        assertEquals(1,result.discarded.count {it.exclusion=="OUTLIER_MAD"})
        assertEquals(1,result.discarded.count {it.exclusion=="IMPLAUSIBLE_DURATION"})
        assertEquals(3,MetroLearning.estimate(route.networkVersion,route.steps.take(1),samples,3).accepted.size+MetroLearning.estimate(route.networkVersion,route.steps.take(1),samples,3).discarded.count {it.exclusion!="OUTSIDE_WINDOW"})
    }
    @Test fun recentWindowUsesRecordingOrderEvenWhenTheCalendarMovesBackwards() {
        val first=sample(0,110_000).copy(at=Long.MAX_VALUE,recordedOrder=0)
        val latest=sample(1,90_000).copy(at=0,recordedOrder=1)
        val result=MetroLearning.estimate(route.networkVersion,route.steps.take(1),listOf(first,latest),1)
        assertEquals(listOf(latest),result.accepted)
    }
    @Test fun accumulatedSamplesNeverBecomeFabricatedIndividualMeasurements() {
        val accumulated=route.steps.take(3)
        val result=MetroLearning.estimate(route.networkVersion,route.steps.take(1),(0..20).map {sample(it,210_000,accumulated)})
        assertTrue(result.accepted.isEmpty());assertEquals(120_000L,result.finalMillis)
        assertNotEquals(MetroLearning.context(route.networkVersion,accumulated),MetroLearning.context(route.networkVersion,accumulated.reversed()))
    }
    @Test fun codecRejectsUnknownFieldsTypesAndInvalidCoordinates() {
        val e=capture(1,100_000);assertEquals(e,MetroLearningCodec.decode(MetroLearningCodec.encode(e),net))
        assertThrows(IllegalArgumentException::class.java) {MetroLearningCodec.decode(MetroLearningCodec.encode(e.copy(stepIndex=-1)),net)}
        assertThrows(IllegalArgumentException::class.java) {MetroLearningCodec.decode(org.json.JSONObject(MetroLearningCodec.encode(e)).put("elapsed",1.5).toString(),net)}
        assertThrows(IllegalArgumentException::class.java) {MetroLearningCodec.decode(org.json.JSONObject(MetroLearningCodec.encode(e)).put("extra",1).toString(),net)}
        assertThrows(IllegalArgumentException::class.java) {MetroLearningCodec.validate(listOf(row(0,e),row(0,e)),net)}
        assertThrows(IllegalArgumentException::class.java) {MetroLearningCodec.validate(listOf(row(0,e),row(2,e)),net)}
    }
}
