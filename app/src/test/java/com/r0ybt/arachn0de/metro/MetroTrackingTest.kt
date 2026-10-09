package com.r0ybt.arachn0de.metro

import org.junit.Assert.*
import org.junit.Test

class MetroTrackingTest {
    private val route=MetroRoute("test",listOf("a","b","d"),false,listOf(MetroStep("a","b","L1",1,MetroService.NORMAL,MetroStepKind.RIDE),MetroStep("b","b","L1",1,MetroService.NORMAL,MetroStepKind.WAYPOINT),MetroStep("b","c","L1",1,MetroService.NORMAL,MetroStepKind.RIDE),MetroStep("c","c","L1",1,MetroService.NORMAL,MetroStepKind.TRANSFER,"L2"),MetroStep("c","d","L2",1,MetroService.NORMAL,MetroStepKind.RIDE)))
    private fun time(ms: Long,boot: Int=1)=MetroTime(1_000_000+ms,100_000+ms,boot)
    @Test fun elapsedTrackingPausesBetweenStationsAndDoesNotIncludePausedTime() {
        var s=MetroTracking.start(route,time(0));assertEquals(.5f,MetroTracking.position(s,time(60_000)).fraction)
        s=MetroTracking.pause(s,time(60_000));val position=MetroTracking.position(s,time(600_000));assertEquals(60_000L,position.offset)
        s=MetroTracking.resume(s,time(600_000));assertEquals(540_000L,s.pausedMillis);assertEquals(90_000L,MetroTracking.position(s,time(630_000)).offset)
    }
    @Test fun transfersAndStopsRemainPendingAndDestinationNeverAutoFinishes() {
        var s=MetroTracking.start(route,time(0)).copy(automatic=false);assertEquals(MetroStepKind.WAYPOINT,MetroTracking.position(s,time(999_000)).waiting)
        assertEquals(listOf("b","d"),MetroTracking.remainingStops(s,time(999_000)))
        s=MetroTracking.pass(s,time(120_000));assertEquals(listOf("d"),MetroTracking.remainingStops(s,time(120_000)))
        assertEquals(MetroStepKind.TRANSFER,MetroTracking.position(s,time(999_000)).waiting)
        s=MetroTracking.pass(s,time(300_000));val position=MetroTracking.position(s,time(999_000));assertEquals("d",position.station);assertNull(s.ended)
        s=MetroTracking.finish(s,time(999_000));assertEquals(1_999_000L,s.ended);assertEquals(999_000L,MetroTracking.totalMillis(s,time(2_000_000)))
    }
    @Test fun forwardBackwardAndPausedCorrectionsKeepHistoryAndResetFutureGates() {
        var s=MetroTracking.start(route,time(0)).copy(automatic=false);s=MetroTracking.pass(s,time(120_000));s=MetroTracking.pass(s,time(300_000))
        s=MetroTracking.pause(s,time(310_000));val count=s.events.size
        s=MetroTracking.confirm(s,"a",time(320_000));assertNotNull(s.pausedAt);assertEquals(0L,s.offset);assertEquals(count+1,s.events.size)
        s=MetroTracking.resume(s,time(330_000));assertEquals(MetroStepKind.WAYPOINT,MetroTracking.position(s,time(900_000)).waiting)
        s=MetroTracking.confirm(s,"d",time(910_000));assertEquals("d",MetroTracking.position(s,time(920_000)).station);assertNull(s.ended)
    }
    @Test fun betweenStationsIsApproximateAndRebootOrClockChangesFreezeWithWarning() {
        var s=MetroTracking.start(route,time(0));assertEquals(60_000L,MetroTracking.confirm(s,"a",time(0),between=true).offset);s=MetroTracking.confirm(s,"b",time(30_000),between=true)
        assertNull(s.confirmed);assertEquals(.5f,MetroTracking.position(s,time(30_000)).fraction)
        assertTrue(MetroTracking.position(s,time(60_000,2)).uncertain)
        assertEquals(s.offset,MetroTracking.position(s,time(60_000,2)).offset)
        assertTrue(MetroTracking.position(s,MetroTime(9_000_000,160_000,1)).uncertain)
        s=MetroTracking.confirm(s,"c",time(60_000,2));assertFalse(MetroTracking.position(s,time(60_000,2)).uncertain)
    }
    @Test fun clockRollbackDoesNotLosePauseOrRealMonotonicDurationAndResumeKeepsUncertainty() {
        var s=MetroTracking.pause(MetroTracking.start(route,time(0)),time(20_000))
        val rolled=MetroTime(500_000,160_000,1)
        assertEquals(60_000L,MetroTracking.totalMillis(s,rolled));assertEquals(40_000L,MetroTracking.currentPauseMillis(s,rolled))
        s=MetroTracking.resume(s,rolled);assertTrue(s.uncertain);assertEquals(40_000L,s.pausedMillis)
        s=MetroTracking.confirm(s,"a",rolled);assertFalse(s.uncertain)
        val finished=MetroTracking.finish(s,rolled);assertEquals(60_000L,MetroTracking.totalMillis(finished,time(999_000)))
    }
    @Test fun automaticRailEstimationStopsAtStageBoundaryEvenWhenScreenIsOff() {
        val s=MetroTracking.start(route,time(0));assertTrue(s.automatic)
        val stopped=MetroTracking.position(s,time(999_000));assertEquals(1,stopped.step);assertEquals(MetroStepKind.WAYPOINT,stopped.waiting)
        assertEquals(120_000L,stopped.offset);assertNull(s.ended);assertEquals(1,s.events.size)
    }
    @Test fun automaticCorrectionAndPauseReanchorWithoutChangingRestrictionsOrRequiringPasses() {
        var s=MetroTracking.start(route,time(0));s=MetroTracking.pause(s,time(180_000))
        assertEquals(120_000L,MetroTracking.position(s,time(999_000)).offset)
        s=MetroTracking.confirm(s,"a",time(999_000));assertNotNull(s.pausedAt);assertEquals(0L,s.offset)
        s=MetroTracking.resume(s,time(1_000_000));assertEquals(MetroStepKind.WAYPOINT,MetroTracking.position(s,time(1_400_000)).waiting)
        assertEquals(120_000L,MetroTracking.position(s,time(1_400_000)).offset)
    }
    @Test fun replanKeepsOriginalStatisticsPauseAndHistoryWhileChangingOnlyPendingRoute() {
        var s=MetroTracking.pause(MetroTracking.start(route,time(0)),time(20_000))
        val replacement=route.copy(stops=listOf("c","d"),steps=route.steps.takeLast(1))
        s=MetroTracking.replan(s,replacement,time(30_000));assertNotNull(s.pausedAt);assertEquals(route,s.originalRoute);assertEquals(listOf(route),s.routeHistory);assertEquals(replacement,s.route);assertEquals(3,s.events.size)
        assertEquals(30_000L,MetroTracking.totalMillis(s,time(30_000)));assertEquals("c",s.confirmed)
    }    @Test fun historicalManualSessionCanOptIntoAutomaticWithoutLosingPauseOrRecoveryWarning() {
        val old=MetroTracking.pause(MetroTracking.start(route,time(0)).copy(automatic=false),time(60_000))
        val changed=MetroTracking.enableAutomatic(old,time(900_000,2))
        assertTrue(changed.automatic);assertTrue(changed.uncertain);assertEquals(old.pausedAt,changed.pausedAt)
        assertEquals(old.events,changed.events);assertEquals(60_000L,changed.offset)
        val corrected=MetroTracking.confirm(changed,"a",time(900_000,2))
        val resumed=MetroTracking.resume(corrected,time(900_000,2));assertEquals(MetroStepKind.WAYPOINT,MetroTracking.position(resumed,time(1_440_000,2)).waiting)
    }

}
