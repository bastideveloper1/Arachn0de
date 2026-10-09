package com.r0ybt.arachn0de.metro

import org.junit.Assert.*
import org.junit.Test

class MetroPrecisionTest {
    private fun time(ms:Long)=MetroTime(1_000_000+ms,100_000+ms,1)
    private fun ride(a:String,b:String,line:String="L1")=MetroStep(a,b,line,1,MetroService.NORMAL,MetroStepKind.RIDE)
    private val longRoute=MetroRoute("test",listOf("s0","s20"),false,(0 until 20).map {ride("s$it","s${it+1}")})
    @Test fun twentySegmentsHaveNoPollingOrRoundingDriftInForegroundOrScreenOff() {
        val s=MetroTracking.start(longRoute,time(0))
        for(i in 1..20) {
            repeat(17) {MetroTracking.position(s,time(i*120_000L-1))}
            val p=MetroTracking.position(s,time(i*120_000L))
            assertEquals(i*120_000L,p.offset);assertEquals("s$i",p.station);assertEquals(0f,p.fraction)
        }
        assertEquals(MetroTracking.position(s,time(1_800_000)),MetroTracking.position(s,time(1_800_000)))
        assertEquals(1,s.events.size);assertNull(s.ended)
    }
    @Test fun aheadAndBehindConfirmationsResetRemainingEstimateWithoutResettingActualTime() {
        val original=MetroTracking.start(longRoute,time(0))
        var s=MetroTracking.confirm(original,"s15",time(1_500_000))
        assertEquals(1_800_000L,s.offset);assertEquals("s16",MetroTracking.position(s,time(1_620_000)).station)
        assertEquals(1_500_000L,MetroStages.railMillis(s,time(1_500_000)))
        s=MetroTracking.confirm(s,"s12",time(1_620_000))
        assertEquals("s12",MetroTracking.position(s,time(1_620_000)).station)
        assertEquals("s13",MetroTracking.position(s,time(1_740_000)).station)
        assertEquals(original.id,s.id);assertEquals(original.start,s.start);assertEquals(original.control!!.since,s.control!!.since)
    }
    @Test fun pauseCorrectionAndResumeNeverIncludeThePausedIntervalInRailProgress() {
        var s=MetroTracking.pause(MetroTracking.start(longRoute,time(0)),time(600_000))
        s=MetroTracking.confirm(s,"s8",time(900_000))
        assertEquals(960_000L,MetroTracking.position(s,time(3_000_000)).offset)
        s=MetroTracking.resume(s,time(3_000_000))
        assertEquals("s9",MetroTracking.position(s,time(3_120_000)).station)
        assertEquals(720_000L,MetroStages.railMillis(s,time(3_120_000)))
    }
    @Test fun sameBootCivilClockJumpDoesNotInvalidateMonotonicProgressButRebootDoes() {
        val s=MetroTracking.start(longRoute,time(0))
        val shifted=MetroTime(9_000_000,1_900_000,1)
        assertFalse(MetroTracking.position(s,shifted).uncertain);assertEquals("s15",MetroTracking.position(s,shifted).station)
        assertTrue(MetroTracking.position(s,shifted.copy(boot=2)).uncertain)
        assertEquals(0L,MetroTracking.position(s,shifted.copy(boot=2)).offset)
    }
    @Test fun longCombinationAndCorrectionOfRepeatedStationKeepConfirmedStages() {
        val route=longRoute.copy(stops=listOf("s0","s20"),steps=listOf(ride("s0","s1"),MetroStep("s1","s1","L1",1,MetroService.NORMAL,MetroStepKind.TRANSFER,"L2"))+listOf(ride("s1","s0","L2"),ride("s0","s20","L2")))
        var s=MetroStages.arrive(MetroTracking.start(route,time(0)),time(100_000))
        s=MetroStages.beginTransfer(s,time(110_000));assertEquals(120_000L,MetroTracking.position(s,time(3_000_000)).offset)
        s=MetroStages.nextLine(s,time(3_000_000));val records=s.control!!.records
        assertEquals(2_890_000L,records.single().transferMillis)
        s=MetroTracking.confirm(s,"s0",time(3_010_000))
        assertEquals(480_000L,s.offset);assertEquals(records,s.control!!.records)
        assertEquals(10_000L,MetroStages.railMillis(s,time(3_010_000)))
        assertEquals(540_000L,MetroTracking.position(s,time(3_070_000)).offset)
    }
}
