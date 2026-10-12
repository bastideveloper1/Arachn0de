package com.r0ybt.arachn0de.metro

import org.junit.Assert.*
import org.junit.Test

class Metro2BehaviorTest {
    private fun time(ms:Long)=MetroTime(1_000_000+ms,100_000+ms,1)
    private val route=MetroRoute("test",listOf("a","d"),false,listOf(
        MetroStep("a","b","L1",1,MetroService.NORMAL,MetroStepKind.RIDE),
        MetroStep("b","b","L1",1,MetroService.NORMAL,MetroStepKind.TRANSFER,"L2"),
        MetroStep("b","c","L2",1,MetroService.NORMAL,MetroStepKind.RIDE),
        MetroStep("c","d","L2",1,MetroService.NORMAL,MetroStepKind.RIDE)))
    @Test fun transferFreezesPositionAcrossCodecIndependentTimerAndManualNextStage() {
        val s=MetroStages.confirmArrival(MetroTracking.start(route,time(0)),time(100000))
        assertEquals(MetroPhase.TRANSFERRING,s.control!!.phase)
        assertEquals(MetroTracking.position(s,time(100000)),MetroTracking.position(s,time(900000)))
        assertEquals(800000L,MetroStages.duration(s.control!!.transferStarted!!,time(900000),true))
        val next=MetroStages.nextLine(s,time(900000))
        assertEquals(0f,MetroTracking.position(next,time(900000)).fraction)
        assertEquals(800000L,next.control!!.records.single().transferMillis)
    }
    @Test fun explicitFinishDuringTransferPreservesRailAndTransferDurationsAndIsIdempotent() {
        val s=MetroStages.confirmArrival(MetroTracking.start(route,time(0)),time(100000))
        val end=MetroTracking.finish(s,time(300000))
        assertEquals(time(300000).wall,end.ended);assertEquals(s.id,end.id)
        assertEquals(100000L,end.control!!.records.first().railMillis)
        assertEquals(200000L,end.control!!.records.first().transferMillis)
        assertEquals(end,MetroTracking.finish(end,time(500000)))
        assertEquals(MetroPhase.TRANSFERRING,MetroStages.undoArrival(end,time(400000)).control!!.phase)
    }
    @Test fun pauseAndResumeDoNotAddOneStationOrLetTransferTimeAdvanceRail() {
        var s=MetroTracking.start(route,time(0))
        s=MetroTracking.pause(s,time(60000));s=MetroTracking.resume(s,time(660000))
        assertEquals(60000L,MetroTracking.position(s,time(660000)).offset)
        assertEquals(120000L,MetroTracking.position(s,time(720000)).offset)
    }
    @Test fun previewContainsOnlyRouteFactsAndDistinctLines() {
        assertEquals("≈ ${route.minutes} min · L1 / L2 · 1 combinaciones",MetroTripPreview.facts(route))
    }
    @Test fun reminderOncePerStageSurvivesCorrectionsWithoutFalsePauseOrRebootAlert() {
        val s=MetroTracking.start(route,time(0));assertEquals(0,MetroArrivalAlert.pending(s,time(0)))
        val marked=MetroArrivalAlert.mark(s,time(0))
        assertNull(MetroArrivalAlert.pending(marked,time(60000)))
        assertNull(MetroArrivalAlert.pending(MetroTracking.pause(s,time(1000)),time(50000)))
        assertNull(MetroArrivalAlert.pending(s,time(50000).copy(boot=2)))
        val next=MetroStages.nextLine(MetroStages.confirmArrival(marked,time(120000)),time(400000))
        assertEquals(3,MetroArrivalAlert.pending(next,time(520000)))
    }
}
