package com.r0ybt.arachn0de.metro

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class MetroBlock3Test {
    private fun time(ms:Long)=MetroTime(1000000+ms,100000+ms,1)
    @Test fun confirmationStartsTransferOnceAndEstimationStopsAtBoundary() {
        val route=MetroRoute("test",listOf("a","d"),false,listOf(
            MetroStep("a","b","L1",1,MetroService.NORMAL,MetroStepKind.RIDE),
            MetroStep("b","b","L1",1,MetroService.NORMAL,MetroStepKind.TRANSFER,"L2"),
            MetroStep("b","d","L2",1,MetroService.NORMAL,MetroStepKind.RIDE)))
        val initial=MetroTracking.start(route,time(0))
        assertEquals(MetroPhase.RIDING,initial.control!!.phase)
        assertEquals("b",MetroTracking.position(initial,time(999999)).station)
        val confirmed=MetroStages.confirmArrival(initial,time(150000))
        assertEquals(MetroPhase.TRANSFERRING,confirmed.control!!.phase)
        assertEquals(time(150000),confirmed.control!!.transferStarted)
        assertEquals(confirmed,MetroStages.confirmArrival(confirmed,time(160000)))
        val next=MetroStages.nextLine(confirmed,time(210000))
        assertEquals(60000L,next.control!!.records.single().transferMillis)
        assertEquals(0f,MetroTracking.position(next,time(210000)).fraction)
    }
    @Test fun grutaDeLourdesUsesSameSegmentBoundaryAsEveryStation() {
        val app=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val net=MetroNetwork.decode(app.assets.open("metro/santiago-beta1.json").bufferedReader().use {it.readText()})
        val route=MetroPlanner.plan(net,listOf("quinta-normal","san-pablo"),false,MetroRestrictions())!!
        val index=route.steps.indexOfFirst {it.to=="gruta-de-lourdes"}
        assertTrue(index>=0)
        val boundary=MetroStages.offset(route,index+1)
        val s=MetroTracking.start(route,time(0))
        assertEquals("gruta-de-lourdes",MetroTracking.position(s,time(boundary)).station)
        assertNotEquals("gruta-de-lourdes",MetroTracking.position(s,time(boundary-1)).station)
        assertEquals(0f,MetroTracking.position(s,time(boundary)).fraction)
    }
    @Test fun optionalAlertIsSuppressedWhilePausedUncertainOrAlreadyRecorded() {
        val route=MetroRoute("test",listOf("a","c"),false,listOf(
            MetroStep("a","b","L1",1,MetroService.NORMAL,MetroStepKind.RIDE),
            MetroStep("b","c","L1",1,MetroService.NORMAL,MetroStepKind.RIDE)))
        val s=MetroTracking.start(route,time(0))
        assertNull(MetroArrivalAlert.pending(s,time(0)))
        assertEquals(1,MetroArrivalAlert.pending(s,time(120000)))
        assertNull(MetroArrivalAlert.pending(MetroTracking.pause(s,time(120000)),time(150000)))
        assertNull(MetroArrivalAlert.pending(s,time(120000).copy(boot=2)))
        val marked=MetroArrivalAlert.mark(s,time(120000))
        assertNull(MetroArrivalAlert.pending(marked,time(130000)))
        assertEquals(s.control,marked.control)
    }
}
