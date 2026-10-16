package com.r0ybt.arachn0de.metro

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
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
class MetroLearningPersistenceTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:MetroRepository
    private fun open() {db=Arachn0deDatabase.create(context);repo=MetroRepository(db,context)}
    private fun time(ms:Long)=MetroTime(1_000_000+ms,1000+ms,1)
    @Before fun setup() {context.deleteDatabase("arachn0de.db");open()}
    @After fun close() {db.close()}
    private suspend fun seed():String {
        val net=repo.snapshot().preferences.planningNetwork
        val route=MetroPlanner.plan(net,listOf("los-heroes","tobalaba"),false,MetroRestrictions())!!
        val id=repo.savePlan(route);assertTrue(repo.begin(id,repo.snapshot().journeys.single().row.revision,time(0)));return id
    }
    private suspend fun doors(id:String,index:Int,ms:Long,arrival:Boolean=false):Boolean {
        val journey=repo.snapshot().journeys.single()
        val station=journey.data.active!!.route.steps.getOrNull(index)?.from ?: journey.data.active!!.route.stops.last()
        return repo.confirmDoors(id,journey.row.revision,station,time(ms),arrival)
    }
    @Test fun beginDoesNotLearnAndDoorsSurviveProcessReopenWithOriginalClockAndPrediction()=runBlocking<Unit> {
        val id=seed();assertTrue(repo.learningEvents().isEmpty())
        val original=repo.snapshot().journeys.single().data.active!!.originalRoute
        assertTrue(doors(id,0,10_000));val first=repo.learningEvents().single()
        db.close();open();assertEquals(listOf(first),repo.learningEvents())
        assertTrue(doors(id,1,110_000))
        val net=repo.snapshot().preferences.network
        val observation=MetroLearning.observations(repo.learningEvents(),net).single()
        assertEquals(100_000L,observation.millis);assertNull(observation.exclusion)
        val captured=MetroLearningCodec.decode(first.payload,net)
        assertEquals(time(10_000),captured.time);assertEquals(10_000L,captured.estimatedOffset)
        assertEquals(original,repo.snapshot().journeys.single().data.active!!.originalRoute)
    }
    @Test fun staleRevisionsAndInvalidCaptureRollBackSessionAndLedgerTogether()=runBlocking<Unit> {
        val id=seed();val before=repo.snapshot()
        assertFalse(repo.confirmDoors(id,before.journeys.single().row.revision-1,"los-heroes",time(10_000)))
        assertTrue(repo.learningEvents().isEmpty());assertEquals(before,repo.snapshot())
        assertThrows(IllegalArgumentException::class.java) {runBlocking {repo.confirmDoors(id,before.journeys.single().row.revision,"los-heroes",MetroTime(-1,1,1))}}
        assertTrue(repo.learningEvents().isEmpty());assertEquals(before,repo.snapshot())
    }
    @Test fun lateCorrectionMovesImmediatelyWithoutChangingDestinationOrMeasuring()=runBlocking<Unit> {
        val id=seed();doors(id,0,10_000)
        val journey=repo.snapshot().journeys.single();val station=journey.data.active!!.route.steps[1].from
        assertTrue(repo.correctPosition(id,journey.row.revision,station,time(200_000)))
        val updated=repo.snapshot().journeys.single().data.active!!
        assertEquals(station,updated.confirmed);assertEquals("tobalaba",updated.route.stops.last())
        assertEquals("CORRECTION",MetroLearning.observations(repo.learningEvents(),repo.snapshot().preferences.network).single().exclusion)
    }
    @Test fun arrivalUndoKeepsTheRawCaptureAndInvalidatesItsContribution()=runBlocking<Unit> {
        val id=seed();doors(id,0,10_000)
        val count=repo.snapshot().journeys.single().data.active!!.route.steps.size
        doors(id,count,1_000_000,true)
        assertNull(repo.snapshot().journeys.single().data.active)
        val finished=repo.snapshot()
        assertTrue(repo.confirmDoors(id,finished.journeys.single().row.revision,"tobalaba",time(1_001_000),true))
        assertEquals(finished,repo.snapshot());assertEquals(2,repo.learningEvents().size)
        val raw=repo.learningEvents().last().payload
        assertTrue(repo.undoArrival(id,repo.snapshot().journeys.single().row.revision,time(1_010_000)))
        assertEquals(raw,repo.learningEvents().last().payload)
        assertEquals("UNDONE",repo.learningEvents().last().invalidReason)
        assertEquals("UNDONE",MetroLearning.observations(repo.learningEvents(),repo.snapshot().preferences.network).single().exclusion)
    }
    @Test fun backupRoundTripPreservesLearningButPreventsBridgingToTheRestoredDeviceClock()=runBlocking<Unit> {
        val id=seed();doors(id,0,10_000);doors(id,1,110_000)
        val before=repo.learningEvents()
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val data=backups.snapshot();assertEquals(before,data.metroLearningEvents)
        val bytes=BackupJson.encode(data);assertEquals(31,JSONObject(String(bytes)).getInt("dataVersion"))
        assertEquals(before,BackupJson.decode(bytes).metroLearningEvents)
        val file=backups.create();val inspection=backups.inspect(file.inputStream())
        try {backups.restore(inspection)} finally {backups.discard(inspection)}
        assertEquals(before,repo.learningEvents());assertNull(db.metroLearningDao().clock())
        assertTrue(doors(id,2,220_000));assertTrue(doors(id,3,320_000))
        assertEquals(listOf(null,"RESTORED_CLOCK",null),MetroLearning.observations(repo.learningEvents(),repo.snapshot().preferences.network).map {it.exclusion})
    }
    @Test fun corruptBackupIsRejectedWithoutLosingPreviousEventsSessionOrClock()=runBlocking<Unit> {
        val id=seed();doors(id,0,10_000)
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val before=repo.snapshot();val events=repo.learningEvents();val clock=db.metroLearningDao().clock()
        val bad=backups.snapshot().copy(metroLearningEvents=events.map {it.copy(payload=JSONObject(it.payload).put("stepIndex",-1).toString())})
        assertThrows(IllegalArgumentException::class.java) {runBlocking {backups.restore(bad)}}
        assertEquals(before,repo.snapshot());assertEquals(events,repo.learningEvents());assertEquals(clock,db.metroLearningDao().clock())
    }
    @Test fun historicalBackupDefaultsToNoLearningAndClearRestoreClearsOriginalEvents()=runBlocking<Unit> {
        seed();val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val historical=BackupJson.encode(backups.snapshot())
        assertTrue(JSONObject(String(historical)).getInt("dataVersion")<=30)
        assertTrue(BackupJson.decode(historical).metroLearningEvents.isEmpty())
        val id=repo.snapshot().journeys.single().row.id;doors(id,0,10_000)
        backups.restore(BackupJson.decode(historical))
        assertTrue(repo.learningEvents().isEmpty());assertNull(db.metroLearningDao().clock())
    }
    @Test fun independentStoresDoNotShareClocksOrEvents()=runBlocking<Unit> {
        val id=seed();doors(id,0,10_000)
        val other=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build()
        try {assertTrue(MetroRepository(other,context).learningEvents().isEmpty());assertNull(other.metroLearningDao().clock())} finally {other.close()}
    }
    @Test fun repeatedSameClockConfirmationDoesNotDuplicateOrChangeRevision()=runBlocking<Unit> {
        val id=seed();doors(id,0,10_000)
        val before=repo.snapshot();val events=repo.learningEvents()
        assertTrue(doors(id,0,10_000));assertEquals(before,repo.snapshot());assertEquals(events,repo.learningEvents())
    }
    @Test fun cancellationBeforeCommitPreservesBothSessionAndOriginalLedger()=runBlocking<Unit> {
        val id=seed();val before=repo.snapshot()
        try {
            db.withTransaction {
                doors(id,0,10_000)
                throw kotlinx.coroutines.CancellationException("Injected before commit")
            }
        } catch(_:kotlinx.coroutines.CancellationException) {}
        assertEquals(before,repo.snapshot());assertTrue(repo.learningEvents().isEmpty());assertNull(db.metroLearningDao().clock())
    }
    @Test fun restoreInsertFailureRollsBackPreviousLedgerSessionAndClock()=runBlocking<Unit> {
        val id=seed();doors(id,0,10_000)
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val data=backups.snapshot();doors(id,1,110_000)
        val before=repo.snapshot();val events=repo.learningEvents();val clock=db.metroLearningDao().clock()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_learning_restore BEFORE INSERT ON metro_learning_events BEGIN SELECT RAISE(ABORT,'injected'); END")
        try {assertTrue(runCatching {backups.restore(data)}.isFailure)} finally {db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_learning_restore")}
        assertEquals(before,repo.snapshot());assertEquals(events,repo.learningEvents());assertEquals(clock,db.metroLearningDao().clock())
    }
    @Test fun recordingOrderSurvivesNewSessionsReopenAndBackupDespiteCalendarChanges()=runBlocking<Unit> {
        val id=seed();doors(id,0,10_000)
        val count=repo.snapshot().journeys.single().data.active!!.route.steps.size;doors(id,count,1_000_000,true)
        val before=repo.snapshot().journeys.single()
        assertTrue(repo.begin(id,before.row.revision,time(1_100_000)))
        assertTrue(repo.confirmDoors(id,repo.snapshot().journeys.single().row.revision,"los-heroes",MetroTime(10,1_200_000,1)))
        val events=repo.learningEvents();assertEquals(3,events.size)
        assertNotEquals(events.first().sessionId,events.last().sessionId)
        db.close();open();assertEquals(events,repo.learningEvents())
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        backups.restore(BackupJson.decode(BackupJson.encode(backups.snapshot())))
        assertEquals(events,repo.learningEvents())
    }
    @Test fun removingTripKeepsItsIndependentOriginalLearningEvents()=runBlocking<Unit> {
        val id=seed();doors(id,0,10_000)
        val count=repo.snapshot().journeys.single().data.active!!.route.steps.size;doors(id,count,1_000_000,true)
        val events=repo.learningEvents();repo.remove(id,repo.snapshot().journeys.single().row.revision)
        assertEquals(events,repo.learningEvents())
        BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory)).snapshot().validate()
    }
}
