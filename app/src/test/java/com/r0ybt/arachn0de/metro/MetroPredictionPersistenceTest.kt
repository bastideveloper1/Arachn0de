package com.r0ybt.arachn0de.metro

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class MetroPredictionPersistenceTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:MetroRepository
    private fun open() {db=Arachn0deDatabase.create(context);repo=MetroRepository(db,context)}
    @Before fun setup() {context.deleteDatabase("arachn0de.db");open()}
    @After fun close() {db.close()}
    private fun time(ms:Long)=MetroTime(10_000_000+ms,100_000+ms,1)
    private suspend fun journey()=repo.snapshot().journeys.single()
    private suspend fun train(count:Int=6):String {
        val p=repo.snapshot().preferences
        val route=MetroPlanner.plan(p.planningNetwork,listOf("los-heroes","republica"),false,p.restrictions)!!
        val id=repo.savePlan(route)
        repeat(count) {i->val start=i*1_000_000L
            repo.begin(id,journey().row.revision,time(start))
            repo.confirmDoors(id,journey().row.revision,"los-heroes",time(start+10_000))
            repo.confirmDoors(id,journey().row.revision,"republica",time(start+100_000),true)
        }
        return id
    }
    @Test fun newSessionFreezesLearningAndRealConfirmationsReanchorImmediatelyWithoutRestarting()=runBlocking<Unit> {
        val id=train();assertEquals(6,repo.snapshot().observations.count {it.exclusion==null})
        val events=repo.learningEvents()
        repo.begin(id,journey().row.revision,time(7_000_000))
        assertEquals(events,repo.learningEvents())
        val started=journey().data.active!!;assertEquals(114_000L,started.route.totalMillis)
        repo.confirmDoors(id,journey().row.revision,"los-heroes",time(7_010_000))
        val s=journey().data.active!!;assertEquals(started.id,s.id);assertEquals(started.start,s.start)
        assertEquals(started.route,s.route);assertEquals(0L,MetroTracking.position(s,time(7_010_000)).offset)
        assertEquals(114_000L,MetroEstimates.remaining(s,time(7_010_000),true))
        repo.correctPosition(id,journey().row.revision,"republica",time(7_070_000))
        assertEquals(0L,MetroEstimates.remaining(journey().data.active!!,time(7_070_000),true));assertNull(journey().data.active!!.ended)
        repo.correctPosition(id,journey().row.revision,"los-heroes",time(7_080_000))
        assertEquals(114_000L,MetroEstimates.remaining(journey().data.active!!,time(7_080_000),true))
        assertEquals(started.originalRoute,journey().data.active!!.originalRoute)
        assertEquals("republica",journey().data.active!!.route.stops.last())
    }
    @Test fun subsequentObservationsUpdateOnlyFutureSessionsAndUndoKeepsThePredictionReference()=runBlocking<Unit> {
        val id=train();repo.begin(id,journey().row.revision,time(7_000_000))
        val original=journey().data.active!!.originalRoute
        repo.confirmDoors(id,journey().row.revision,"los-heroes",time(7_010_000))
        repo.confirmDoors(id,journey().row.revision,"republica",time(7_210_000),true)
        assertEquals(original,journey().data.sessions.last().route)
        assertTrue(repo.undoArrival(id,journey().row.revision,time(7_220_000)))
        assertEquals(original,journey().data.active!!.route)
        assertEquals("UNDONE",repo.snapshot().observations.last().exclusion)
    }
    @Test fun adaptedBackupPreservesFrozenPredictionUndoAndClocksAndRejectsCorruptionAtomically()=runBlocking<Unit> {
        val id=train();repo.begin(id,journey().row.revision,time(7_000_000))
        repo.confirmDoors(id,journey().row.revision,"los-heroes",time(7_010_000))
        repo.confirmDoors(id,journey().row.revision,"republica",time(7_110_000),true)
        val before=journey();db.close();open();assertEquals(before,journey())
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val file=backups.create();val inspected=backups.inspect(file.inputStream())
        try {backups.restore(inspected)} finally {backups.discard(inspected)}
        val restored=journey();assertEquals(before.data.plan,restored.data.plan)
        assertEquals(before.data.sessions.last().originalRoute,restored.data.sessions.last().originalRoute)
        assertEquals(before.data.sessions.last().route,restored.data.sessions.last().route)
        assertEquals(before.data.sessions.last().control!!.undo!!.previous.route,restored.data.sessions.last().control!!.undo!!.previous.route)
        val data=backups.snapshot();val raw=JSONObject(data.metroJourneys.single().payload)
        raw.getJSONArray("sessions").getJSONObject(6).getJSONObject("route").getJSONObject("prediction").getJSONArray("parts").getJSONObject(0).put("millis",1)
        val snapshot=repo.snapshot()
        assertTrue(runCatching {backups.restore(data.copy(metroJourneys=data.metroJourneys.map {it.copy(payload=raw.toString())}))}.isFailure)
        assertEquals(snapshot,repo.snapshot())
        assertTrue(repo.undoArrival(id,journey().row.revision,time(7_120_000)))
        assertEquals(before.data.sessions.last().route,journey().data.active!!.route)
    }
    @Test fun cancellationPreservesMeasuredIntervalsThroughReopenAndBackupWithoutArrivalAndRejectsCorruption()=runBlocking<Unit> {
        val p=repo.snapshot().preferences
        val route=MetroPlanner.plan(p.planningNetwork,listOf("los-heroes","universidad-de-chile"),false,p.restrictions)!!
        val id=repo.savePlan(route)
        repo.begin(id,journey().row.revision,time(0))
        repo.confirmDoors(id,journey().row.revision,"los-heroes",time(10_000))
        repo.confirmDoors(id,journey().row.revision,"la-moneda",time(100_000))
        val observations=repo.snapshot().observations
        assertEquals(1,observations.count {it.exclusion==null})
        repo.tracking(id,journey().row.revision) {MetroTracking.pause(it,time(120_000))}
        val current=journey().data.active!!
        val events=repo.learningEvents()
        repo.tracking(id,journey().row.revision) {MetroTracking.cancel(it,time(180_000))}
        val cancelled=journey().data.sessions.last()
        assertTrue(cancelled.cancelled);assertNull(journey().data.active)
        assertEquals(MetroTracking.position(current,time(180_000)).offset,cancelled.offset)
        assertNotEquals(route.totalMillis,cancelled.offset)
        assertEquals("la-moneda",cancelled.confirmed);assertNull(cancelled.events.last().station)
        assertTrue(cancelled.events.none {it.kind=="ARRIVED"});assertNull(cancelled.control!!.undo)
        assertEquals(60_000L,cancelled.pausedMillis);assertEquals(180_000L,cancelled.realMillis)
        assertEquals(events,repo.learningEvents());assertEquals(observations,repo.snapshot().observations)
        assertEquals("Cancelado",MetroTripPreview.status(journey()))
        assertEquals(7,JSONObject(journey().row.payload).getInt("version"))
        db.close();open();assertEquals(cancelled,journey().data.sessions.last())
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val file=backups.create();val inspected=backups.inspect(file.inputStream())
        try {backups.restore(inspected)} finally {backups.discard(inspected)}
        val restored=journey().data.sessions.last()
        assertTrue(restored.cancelled);assertEquals(cancelled.offset,restored.offset)
        assertEquals(cancelled.route,restored.route);assertEquals(events,repo.learningEvents())
        assertEquals(observations,repo.snapshot().observations)
        val before=repo.snapshot();val backup=backups.snapshot()
        val raw=JSONObject(backup.metroJourneys.single().payload)
        raw.getJSONArray("sessions").getJSONObject(0).put("ended",JSONObject.NULL)
        assertTrue(runCatching {backups.restore(backup.copy(metroJourneys=backup.metroJourneys.map {it.copy(payload=raw.toString())}))}.isFailure)
        assertEquals(before,repo.snapshot())
        val downgraded=JSONObject(journey().row.payload).put("version",6)
        assertTrue(runCatching {MetroCodec.journey(downgraded.toString(),p.network)}.isFailure)
        repo.begin(id,journey().row.revision,time(300_000));assertNotNull(journey().data.active)
        assertTrue(journey().data.sessions.first().cancelled)
    }
    @Test fun cancellationWorksAtStageGatesAndAfterDestinationChangesWithoutUndoingPriorMeasurements()=runBlocking<Unit> {
        val p=repo.snapshot().preferences
        val route=MetroPlanner.plan(p.planningNetwork,listOf("las-parcelas","los-dominicos"),false,p.restrictions)!!
        val boundary=route.steps.indexOfFirst {it.kind==MetroStepKind.TRANSFER}
        val started=MetroTracking.start(route,time(0))
        val arrived=MetroStages.arrive(started,time(500_000))
        assertEquals(boundary,arrived.control!!.records.last().boundary)
        val transferring=MetroStages.beginTransfer(arrived,time(510_000))
        val ready=MetroTracking.replan(started,route,time(10_000))
        for(session in listOf(started,arrived,transferring,ready)) {
            val closed=MetroTracking.cancel(session,time(600_000))
            assertTrue(closed.cancelled);assertEquals(session.control!!.records,closed.control!!.records)
            assertNull(closed.control.undo);assertEquals(closed,MetroTracking.cancel(closed,time(700_000)))
            assertEquals(closed,MetroCodec.journey(MetroCodec.journey(MetroJourneyData(route,listOf(closed))),p.network).sessions.single())
        }
    }
    @Test fun originalZeroDurationPauseSeparatesOnlyItsIntervalAndSurvivesTripDeletionAndBackup()=runBlocking<Unit> {
        val p=repo.snapshot().preferences
        val route=MetroPlanner.plan(p.planningNetwork,listOf("los-heroes","baquedano"),false,p.restrictions)!!
        val id=repo.savePlan(route);repo.begin(id,journey().row.revision,time(0))
        repo.confirmDoors(id,journey().row.revision,"los-heroes",time(10_000))
        repo.confirmDoors(id,journey().row.revision,"la-moneda",time(100_000))
        repo.tracking(id,journey().row.revision) {MetroTracking.pause(it,time(110_000))}
        val paused=journey().data.active!!
        assertEquals(MetroTracking.position(paused,time(110_000)),MetroTracking.position(paused,time(170_000)))
        assertEquals(MetroEstimates.remaining(paused,time(110_000)),MetroEstimates.remaining(paused,time(170_000)))
        repo.tracking(id,journey().row.revision) {MetroTracking.resume(it,time(110_000))}
        repo.confirmDoors(id,journey().row.revision,"universidad-de-chile",time(200_000))
        repo.confirmDoors(id,journey().row.revision,"santa-lucia",time(300_000))
        val events=repo.learningEvents();assertEquals(6,events.size)
        assertEquals(listOf(null,"PAUSE",null),repo.snapshot().observations.map {it.exclusion})
        val captures=events.map {MetroLearningCodec.decode(it.payload,p.network)}
        assertEquals(listOf(MetroCaptureKind.PAUSE,MetroCaptureKind.RESUME),captures.subList(2,4).map {it.kind})
        assertEquals(captures[2].time,captures[3].time)
        assertEquals(captures[2].pauseMillis,captures[3].pauseMillis)
        val raw=JSONObject(events[2].payload);assertEquals(2,raw.getInt("version"))
        assertTrue(runCatching {MetroLearningCodec.decode(raw.put("version",1).toString(),p.network)}.isFailure)
        repo.tracking(id,journey().row.revision) {MetroTracking.cancel(it,time(310_000))}
        repo.remove(id,journey().row.revision);assertTrue(repo.snapshot().journeys.isEmpty())
        db.close();open();assertEquals(events,repo.learningEvents())
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val file=backups.create();val inspected=backups.inspect(file.inputStream())
        try {backups.restore(inspected)} finally {backups.discard(inspected)}
        assertTrue(repo.snapshot().journeys.isEmpty());assertEquals(events,repo.learningEvents())
        assertEquals(listOf(null,"PAUSE",null),repo.snapshot().observations.map {it.exclusion})
        val before=repo.snapshot();val data=backups.snapshot()
        val malformed=data.metroLearningEvents.mapIndexed {i,row->if(i==2) row.copy(payload=JSONObject(row.payload).put("paused",false).toString()) else row}
        assertTrue(runCatching {backups.restore(data.copy(metroLearningEvents=malformed))}.isFailure)
        assertEquals(before,repo.snapshot());assertEquals(events,repo.learningEvents())
    }
    @Test fun sparseConfirmationsKeepTheEntireInterruptedObservationWithoutInventingIndividualTimes()=runBlocking<Unit> {
        val p=repo.snapshot().preferences
        val route=MetroPlanner.plan(p.planningNetwork,listOf("los-heroes","baquedano"),false,p.restrictions)!!
        val id=repo.savePlan(route);repo.begin(id,journey().row.revision,time(0))
        repo.confirmDoors(id,journey().row.revision,"los-heroes",time(10_000))
        repo.tracking(id,journey().row.revision) {MetroTracking.pause(it,time(100_000))}
        repo.tracking(id,journey().row.revision) {MetroTracking.resume(it,time(700_000))}
        repo.confirmDoors(id,journey().row.revision,"santa-lucia",time(900_000))
        val observation=repo.snapshot().observations.single()
        assertEquals("PAUSE",observation.exclusion);assertEquals(890_000L,observation.millis)
        assertEquals(3,observation.steps.size)
        val diagnostic=MetroCalculation.lines(route,repo.snapshot().observations,p.network,journey().data.active)
        assertTrue(diagnostic.any {it.startsWith("Tiempo registrado con pausa:")})
        assertTrue(diagnostic.any {it.contains("guardado, sin usar para aprender tiempos habituales")})
        assertTrue(MetroLearning.estimate(route.networkVersion,observation.steps,repo.snapshot().observations).accepted.isEmpty())
        repo.confirmDoors(id,journey().row.revision,"universidad-catolica",time(1_000_000))
        assertEquals(listOf("PAUSE",null),repo.snapshot().observations.map {it.exclusion})
    }
    @Test fun hereWhilePausedCorrectsStationWithoutOpeningDoorsOrResumingAndArrivalDoesNotRescueTheInterval()=runBlocking<Unit> {
        val p=repo.snapshot().preferences
        val route=MetroPlanner.plan(p.planningNetwork,listOf("los-heroes","republica"),false,p.restrictions)!!
        val id=repo.savePlan(route);repo.begin(id,journey().row.revision,time(0))
        repo.confirmDoors(id,journey().row.revision,"los-heroes",time(10_000))
        repo.tracking(id,journey().row.revision) {MetroTracking.pause(it,time(30_000))}
        repo.confirmDoors(id,journey().row.revision,"republica",time(50_000))
        val paused=journey().data.active!!
        assertNotNull(paused.pausedAt);assertNull(paused.ended);assertEquals("republica",paused.confirmed)
        val last=MetroLearningCodec.decode(repo.learningEvents().last().payload,p.network)
        assertEquals(MetroCaptureKind.PAUSED_LOCATION,last.kind);assertFalse(last.valid)
        assertEquals(MetroEstimates.remaining(paused,time(50_000)),MetroEstimates.remaining(paused,time(100_000)))
        repo.confirmDoors(id,journey().row.revision,"republica",time(120_000),true)
        assertNull(journey().data.active);assertFalse(journey().data.sessions.last().cancelled)
        assertEquals(MetroCaptureKind.ARRIVAL,MetroLearningCodec.decode(repo.learningEvents().last().payload,p.network).kind)
        assertTrue(repo.snapshot().observations.none {it.exclusion==null})
        assertTrue(repo.undoArrival(id,journey().row.revision,time(150_000)))
        assertNotNull(journey().data.active!!.pausedAt)
        assertEquals("UNDONE",repo.learningEvents().last().invalidReason)
    }
    @Test fun betweenStationCorrectionIsAuditedAsNonTemporalAndDoesNotGenerateFalseLearning()=runBlocking<Unit> {
        val id=train();repo.begin(id,journey().row.revision,time(7_000_000))
        repo.confirmDoors(id,journey().row.revision,"los-heroes",time(7_010_000))
        repo.correctPosition(id,journey().row.revision,"los-heroes",time(7_050_000),between=true)
        val s=journey().data.active!!
        assertNull(s.confirmed);assertEquals(.5f,MetroTracking.position(s,time(7_050_000)).fraction)
        assertEquals("CORRECTION",repo.snapshot().observations.last().exclusion)
        assertEquals(MetroCaptureKind.CORRECTION,repo.snapshot().lastCaptures.getValue(s.id).capture.kind)
    }
    @Test fun changeDestinationArchivesFrozenOldModelAndKeepsOriginalPredictionThroughRestore()=runBlocking<Unit> {
        val id=train();repo.begin(id,journey().row.revision,time(7_000_000))
        val original=journey().data.active!!.originalRoute
        repo.changeDestination(id,journey().row.revision,"los-heroes","tobalaba",time(7_050_000))
        val changed=journey().data.active!!
        assertEquals(original,changed.originalRoute);assertEquals(original,changed.routeArchives.single().route)
        assertEquals(MetroPhase.READY,changed.control!!.phase)
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        backups.restore(BackupJson.decode(BackupJson.encode(backups.snapshot())))
        assertEquals(original,journey().data.active!!.routeArchives.single().route)
    }
    @Test fun capturePreservesPresentedPositionAndEtaRatherThanReconstructingAnOlderUiFrame()=runBlocking<Unit> {
        val id=train(0);repo.begin(id,journey().row.revision,time(0))
        assertTrue(repo.confirmDoors(id,journey().row.revision,"los-heroes",time(10_000),shownOffset=2000,shownEta=118_000))
        val s=journey().data.active!!;val capture=repo.snapshot().lastCaptures.getValue(s.id).capture
        assertEquals(2000L,capture.estimatedOffset);assertEquals(118_000L,capture.etaMillis)
        assertEquals(time(10_000),capture.time);assertEquals(0L,MetroTracking.position(s,time(10_000)).offset)
    }
    @Test fun zeroDistanceJourneyStillFinishesButCannotTrainANonexistentRailSegment()=runBlocking<Unit> {
        val p=repo.snapshot().preferences
        val route=MetroPlanner.plan(p.planningNetwork,listOf("los-heroes","los-heroes"),false,p.restrictions)!!
        val id=repo.savePlan(route);repo.begin(id,journey().row.revision,time(0))
        assertTrue(repo.confirmDoors(id,journey().row.revision,"los-heroes",time(10_000),true))
        assertNull(journey().data.active)
        val event=repo.learningEvents().single();assertEquals("NO_RAIL",MetroLearningCodec.decode(event.payload,p.network).exclusion)
    }
}
