package com.r0ybt.arachn0de.metro

import org.junit.Assert.*
import org.junit.Test

class MetroStageEngineTest {
    private fun time(ms:Long,boot:Int=1)=MetroTime(1_000_000+ms,100_000+ms,boot)
    private fun ride(a:String,b:String,line:String)=MetroStep(a,b,line,1,MetroService.NORMAL,MetroStepKind.RIDE)
    private fun transfer(at:String,line:String,next:String)=MetroStep(at,at,line,1,MetroService.NORMAL,MetroStepKind.TRANSFER,next)
    private val single=MetroRoute("test",listOf("a","b"),false,listOf(ride("a","b","L1")))
    private val multiple=single.copy(stops=listOf("a","d"),steps=listOf(ride("a","b","L1"),transfer("b","L1","L2"),ride("b","c","L2"),transfer("c","L2","L3"),ride("c","d","L3")))
    @Test fun singleLineRequiresArrivalAndUndoRestoresProgressWithoutStartingNewTimer() {
        val s=MetroTracking.start(single,time(0));assertNull(s.ended)
        val ended=MetroStages.arrive(s,time(60_000));assertNotNull(ended.ended);assertEquals(120_000L,ended.offset)
        assertEquals(ended,MetroStages.arrive(ended,time(70_000)))
        val undo=MetroStages.undoArrival(ended,time(600_000));assertEquals(s.id,undo.id);assertNull(undo.ended)
        assertEquals(60_000L,MetroTracking.position(undo,time(600_000)).offset)
        assertEquals(90_000L,MetroTracking.position(undo,time(630_000)).offset)
        assertEquals(60_000L,MetroStages.railMillis(undo,time(600_000)))
        assertEquals(undo,MetroStages.undoArrival(undo,time(700_000)))
    }
    @Test fun combinationDoesNotMoveRailAndNextStageReanchorsWithoutCarryingDelay() {
        var s=MetroTracking.start(multiple,time(0))
        assertEquals(120_000L,MetroTracking.position(s,time(800_000)).offset)
        s=MetroStages.arrive(s,time(300_000));assertEquals("b",s.confirmed);assertEquals(MetroPhase.ARRIVED,s.control!!.phase)
        assertEquals(s,MetroStages.arrive(s,time(301_000)))
        assertEquals(120_000L,MetroTracking.position(s,time(800_000)).offset)
        s=MetroStages.beginTransfer(s,time(400_000));assertEquals(MetroPhase.TRANSFERRING,s.control!!.phase)
        assertEquals(s,MetroStages.beginTransfer(s,time(500_000)))
        assertEquals(120_000L,MetroTracking.position(s,time(999_000)).offset)
        s=MetroStages.nextLine(s,time(600_000));assertEquals(360_000L,s.offset)
        assertEquals(420_000L,MetroTracking.position(s,time(660_000)).offset)
        assertEquals(600_000L,MetroTracking.totalMillis(s,time(600_000)))
        assertEquals(200_000L,s.control!!.records.single().transferMillis)
        assertEquals(s,MetroStages.nextLine(s,time(700_000)));assertFalse(s.control!!.undo!!.reversible)
        assertEquals(s,MetroStages.undoArrival(s,time(800_000)))
    }
    @Test fun multipleCombinationsKeepEveryStageDurationAndOneSessionIdentity() {
        var s=MetroTracking.start(multiple,time(0));val id=s.id
        for(start in listOf(120_000L,500_000L)) {
            s=MetroStages.arrive(s,time(start));s=MetroStages.beginTransfer(s,time(start+10_000));s=MetroStages.nextLine(s,time(start+60_000))
        }
        assertEquals(id,s.id);assertEquals(2,s.control!!.records.size)
        assertTrue(s.control!!.records.all {it.transferMillis==50_000L})
        s=MetroStages.arrive(s,time(900_000));assertNotNull(s.ended);assertEquals(3,s.control!!.records.size)
        assertEquals(900_000L,MetroTracking.totalMillis(s,time(2_000_000)))
    }
    @Test fun pausedArrivalUndoPreservesPauseAndDoesNotAdvanceDuringWait() {
        var s=MetroTracking.pause(MetroTracking.start(multiple,time(0)),time(30_000))
        val pausedAt=s.pausedAt
        s=MetroStages.arrive(s,time(60_000));assertEquals(pausedAt,s.pausedAt)
        assertEquals(s,MetroStages.beginTransfer(s,time(80_000)))
        s=MetroStages.undoArrival(s,time(800_000));assertEquals(pausedAt,s.pausedAt);assertEquals(30_000L,s.offset)
        assertEquals(30_000L,MetroTracking.position(s,time(999_000)).offset)
        s=MetroTracking.resume(s,time(1_000_000));assertEquals(60_000L,MetroTracking.position(s,time(1_030_000)).offset)
        assertEquals(60_000L,MetroStages.railMillis(s,time(1_030_000)))
    }
    @Test fun undoWhileCombiningRestoresOriginalStageAndRemovesTransitionRecords() {
        val initial=MetroTracking.start(multiple,time(0))
        val s=MetroStages.beginTransfer(MetroStages.arrive(initial,time(40_000)),time(50_000))
        val restored=MetroStages.undoArrival(s,time(200_000))
        assertEquals(40_000L,restored.offset);assertEquals(MetroPhase.RIDING,restored.control!!.phase)
        assertTrue(restored.control!!.records.isEmpty());assertNull(restored.control!!.transferStarted)
    }
    @Test fun adjacentWaypointAndTransferAreOneGateBeforeBoardingNextLine() {
        val route=multiple.copy(stops=listOf("a","b","c"),steps=listOf(ride("a","b","L1"),MetroStep("b","b","L1",1,MetroService.NORMAL,MetroStepKind.WAYPOINT),transfer("b","L1","L2"),ride("b","c","L2")))
        var s=MetroStages.arrive(MetroTracking.start(route,time(0)),time(30_000))
        assertEquals(MetroStepKind.TRANSFER,MetroStages.gateKind(s))
        s=MetroStages.nextLine(MetroStages.beginTransfer(s,time(40_000)),time(80_000))
        assertEquals(3,s.control!!.firstStep);assertEquals(1,s.control!!.records.size)
        assertEquals(360_000L,s.offset);assertEquals(420_000L,MetroTracking.position(s,time(140_000)).offset)
    }
    @Test fun undoKeepsIndependentTravelerEditAndPausedIndicatorsNeverPulse() {
        var s=MetroTracking.start(multiple,time(0)).copy(personId="original")
        s=MetroStages.arrive(s,time(30_000)).copy(personId="updated")
        val restored=MetroStages.undoArrival(s,time(60_000));assertEquals("updated",restored.personId)
        val paused=MetroTracking.pause(restored,time(65_000))
        val row=com.r0ybt.arachn0de.data.local.MetroJourneyEntity("j",null,null,true,"{}",0)
        assertEquals("Pausado",MetroActivity.status(MetroJourney(row,MetroJourneyData(multiple,listOf(paused))),time(900_000)))
    }
    @Test fun rebootFreezesEstimateAndUndoKeepsRecoveryWarning() {
        val s=MetroStages.arrive(MetroTracking.start(multiple,time(0)),time(40_000))
        val restored=MetroStages.undoArrival(s,time(500_000,2))
        assertTrue(MetroTracking.position(restored,time(600_000,2)).uncertain)
        assertEquals(40_000L,MetroTracking.position(restored,time(600_000,2)).offset)
    }
    @Test fun stationCorrectionWithinRailStageKeepsDurationAndOriginalClock() {
        val initial=MetroTracking.start(multiple,time(0))
        val corrected=MetroTracking.confirm(initial,"a",time(30_000))
        assertEquals(initial.control!!.since,corrected.control!!.since)
        assertEquals(60_000L,MetroStages.railMillis(corrected,time(60_000)))
        assertEquals(30_000L,MetroTracking.position(corrected,time(60_000)).offset)
    }

}
