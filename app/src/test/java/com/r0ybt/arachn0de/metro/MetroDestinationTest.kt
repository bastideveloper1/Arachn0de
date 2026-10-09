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
class MetroDestinationTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:MetroRepository
    private fun time(ms:Long)=MetroTime(1_000_000+ms,100_000+ms,1)
    @Before fun setup() {context.deleteDatabase("arachn0de.db");open()}
    private fun open() {db=Arachn0deDatabase.create(context);repo=MetroRepository(db,context)}
    @After fun close() {db.close()}
    private suspend fun seed(combo:Boolean=false):MetroJourney {
        val net=repo.snapshot().preferences.network
        val route=MetroPlanner.plan(net,if(combo) listOf("las-parcelas","los-dominicos") else listOf("los-heroes","republica"),false,MetroRestrictions())!!
        val id=repo.savePlan(route);repo.begin(id,repo.snapshot().journeys.single().row.revision,time(0));return journey()
    }
    private suspend fun journey()=repo.snapshot().journeys.single()
    private suspend fun track(action:(MetroSession)->MetroSession) {val j=journey();assertTrue(repo.tracking(j.row.id,j.row.revision,action))}
    private suspend fun change(station:String,destination:String,ms:Long=50_000) {val j=journey();assertTrue(repo.changeDestination(j.row.id,j.row.revision,station,destination,time(ms)))}
    @Test fun sameLineKeepsOriginalIdentityAndClockButRequiresExplicitBoarding()=runBlocking<Unit> {
        val original=seed();change("los-heroes","tobalaba")
        val j=journey();val s=j.data.active!!
        assertEquals(original.row.id,j.row.id);assertEquals(original.data.active!!.id,s.id)
        assertEquals(original.data.active!!.start,s.start);assertEquals(original.data.plan,s.originalRoute)
        assertEquals("tobalaba",j.data.plan.stops.last());assertEquals(MetroPhase.READY,s.control!!.phase)
        assertEquals(0L,MetroTracking.position(s,time(9_000_000)).offset)
        track {MetroStages.nextLine(it,time(60_000))}
        assertEquals(30_000L,MetroTracking.position(journey().data.active!!,time(90_000)).offset)
        assertEquals(90_000L,MetroTracking.totalMillis(journey().data.active!!,time(90_000)))
    }
    @Test fun newLineAndFutureCombinationUseOfficialPlannerAndStages()=runBlocking<Unit> {
        seed();change("republica","cerrillos")
        val s=journey().data.active!!;assertTrue(s.route.transfers>0);assertEquals(MetroPlanner.plan(repo.snapshot().preferences.network,listOf("republica","cerrillos"),false,MetroRestrictions()),s.route)
        track {MetroStages.nextLine(it,time(60_000))};track {MetroStages.arrive(it,time(90_000))}
        assertNotNull(journey().data.active!!.control!!.undo)
        assertTrue(repo.undoArrival(journey().row.id,journey().row.revision,time(100_000)))
        assertEquals(MetroPhase.RIDING,journey().data.active!!.control!!.phase)
        assertEquals(1,journey().data.active!!.routeArchives.size)
    }
    @Test fun pauseAndRecordedHistorySurviveChangeAndUndoNewArrival()=runBlocking<Unit> {
        seed();track {MetroTracking.pause(it,time(30_000))};val before=journey().data.active!!
        assertNull(MetroDestination.location(before,time(40_000)))
        change("los-heroes","tobalaba")
        val s=journey().data.active!!;assertEquals(before.pausedAt,s.pausedAt);assertEquals(before.pausedElapsed,s.pausedElapsed)
        assertEquals(0L,MetroTracking.position(s,time(900_000)).offset)
        assertEquals(s,MetroStages.nextLine(s,time(90_000)))
        track {MetroTracking.resume(it,time(100_000))};track {MetroStages.nextLine(it,time(110_000))}
        assertEquals(70_000L,journey().data.active!!.pausedMillis)
        val archived=journey().data.active!!.routeArchives.single();assertTrue(archived.events.any {it.kind=="PAUSE"});assertEquals(30_000L,archived.control!!.records.last().railMillis)
    }
    @Test fun duringCombinationPreservesRailAndTransferRecordsWithoutSilentBoarding()=runBlocking<Unit> {
        seed(true);track {MetroStages.arrive(it,time(30_000))};track {MetroStages.beginTransfer(it,time(40_000))}
        val before=journey().data.active!!;val station=before.confirmed!!
        assertEquals(station,MetroDestination.location(before,time(50_000)))
        change(station,"republica",80_000)
        val s=journey().data.active!!;assertEquals(MetroPhase.READY,s.control!!.phase)
        assertEquals(40_000L,s.routeArchives.single().control!!.records.last().transferMillis)
        assertEquals("Combinando",MetroActivity.status(journey(),time(90_000)))
        assertNotNull(s.control!!.transferStarted);assertFalse(s.control!!.undo!!.reversible)
        assertThrows(IllegalArgumentException::class.java) {runBlocking {repo.undoArrival(journey().row.id,journey().row.revision,time(90_000))}}
        track {MetroStages.nextLine(it,time(100_000))};assertEquals(20_000L,journey().data.active!!.control!!.records.single().transferMillis)
    }
    @Test fun backwardsDestinationAndDestinationAtCurrentStationAreExplicitAndPreserveOrigin()=runBlocking<Unit> {
        seed();change("republica","los-heroes");assertEquals("los-heroes",journey().data.active!!.originalRoute.stops.first())
        change("republica","republica",60_000);val s=journey().data.active!!
        assertTrue(s.route.steps.isEmpty());assertNull(s.ended);track {MetroStages.arrive(it,time(70_000))}
        assertNotNull(journey().data.sessions.single().ended);assertEquals(2,journey().data.sessions.single().routeArchives.size)
        assertEquals(0L,journey().data.sessions.single().control!!.records.last().railMillis)
    }
    @Test fun unavailableRouteAndStaleRevisionAreAtomicAndCandidateDoesNotWrite()=runBlocking<Unit> {
        val j=seed();val net=repo.snapshot().preferences.network
        MetroPlanner.plan(net,listOf("los-heroes","tobalaba"),false,MetroRestrictions());assertEquals(j,journey())
        repo.settings {it.copy(restrictions=it.restrictions.copy(closed=setOf("tobalaba")))};val before=repo.snapshot()
        assertThrows(IllegalStateException::class.java) {runBlocking {change("los-heroes","tobalaba")}}
        assertEquals(before,repo.snapshot());assertFalse(repo.changeDestination(j.row.id,j.row.revision-1,"los-heroes","republica",time(50_000)));assertEquals(before,repo.snapshot())
    }
    @Test fun reopenBackupRestoreAndCorruptArchiveKeepCompleteHistory()=runBlocking<Unit> {
        seed(true);track {MetroStages.arrive(it,time(30_000))};change(journey().data.active!!.confirmed!!,"republica")
        val before=repo.snapshot();db.close();open();assertEquals(before,repo.snapshot())
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory));val data=backups.snapshot()
        val file=backups.create();val inspected=backups.inspect(file.inputStream());track {MetroStages.nextLine(it,time(60_000))}
        backups.restore(inspected);backups.discard(inspected)
        assertEquals(before.journeys.single().data.copy(sessions=before.journeys.single().data.sessions.map(MetroStages::portable)),journey().data)
        val restored=repo.snapshot();val row=data.metroJourneys.single();val raw=JSONObject(row.payload)
        raw.getJSONArray("sessions").getJSONObject(0).getJSONArray("routeArchives").getJSONObject(0).getJSONArray("sessions").getJSONObject(0).put("id","another-session")
        assertThrows(IllegalArgumentException::class.java) {runBlocking {backups.restore(data.copy(metroJourneys=listOf(row.copy(payload=raw.toString()))))}}
        assertEquals(restored,repo.snapshot())
    }
    @Test fun v3WithoutArchivesRemainsReadableAndDoesNotRewriteHistoricalData()=runBlocking<Unit> {
        val j=seed();val raw=JSONObject(j.row.payload).put("version",3)
        raw.getJSONArray("sessions").getJSONObject(0).remove("routeArchives")
        db.metroDao().save(j.row.copy(payload=raw.toString()));assertTrue(journey().data.active!!.routeArchives.isEmpty())
        assertEquals(raw.toString(),db.metroDao().journey(j.row.id)!!.payload)
    }
    @Test fun repeatedChangeAndHereConfirmationKeepBoardingGateWithoutDuplicateHistory()=runBlocking<Unit> {
        seed();change("los-heroes","tobalaba");val before=journey()
        change("los-heroes","tobalaba",60_000);assertEquals(before,journey())
        track {MetroTracking.confirm(it,"los-heroes",time(70_000))}
        assertEquals(MetroPhase.READY,journey().data.active!!.control!!.phase)
        assertEquals(0L,MetroTracking.position(journey().data.active!!,time(900_000)).offset)
    }
    @Test fun locationCorrectionReplanArchivesPartialTimingAndWaitsForBoarding()=runBlocking<Unit> {
        seed();val net=repo.snapshot().preferences.network
        val replacement=MetroPlanner.plan(net,listOf("tobalaba","los-dominicos"),false,MetroRestrictions())!!
        track {MetroTracking.replan(it,replacement,time(30_000))}
        val s=journey().data.active!!;assertEquals(MetroPhase.READY,s.control!!.phase)
        assertEquals(30_000L,s.routeArchives.single().control!!.records.last().railMillis)
        assertEquals("REPLAN",s.events.last().kind)
    }

    @Test fun repeatedDestinationChangesWhileCombiningRetainEachTransferInterval()=runBlocking<Unit> {
        seed(true);track {MetroStages.arrive(it,time(30_000))};track {MetroStages.beginTransfer(it,time(40_000))}
        val station=journey().data.active!!.confirmed!!
        change(station,"republica",80_000);change(station,"cerrillos",100_000)
        val s=journey().data.active!!
        assertEquals(listOf(40_000L,20_000L),s.routeArchives.map {it.control!!.records.last().transferMillis})
        assertNotNull(s.control!!.transferStarted)
        track {MetroStages.nextLine(it,time(120_000))}
        assertEquals(20_000L,journey().data.active!!.control!!.records.last().transferMillis)
    }

}
