package com.r0ybt.arachn0de.metro

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class MetroStagePersistenceTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:MetroRepository
    private fun open() {db=Arachn0deDatabase.create(context);repo=MetroRepository(db,context)}
    private fun time(ms:Long)=MetroTime(1_000_000+ms,100_000+ms,1)
    @Before fun setup() {context.deleteDatabase("arachn0de.db");open()}
    @After fun close() {db.close()}
    private suspend fun seed():String {
        val p=ProjectRepository(db.projectDao(),db).createProject("Metro stages")
        val task=NodeRepository(db).createNode(p.id,null,"Linked")
        val net=repo.snapshot().preferences.planningNetwork
        val route=MetroPlanner.plan(net,listOf("las-parcelas","los-dominicos"),false,MetroRestrictions())!!
        assertTrue(route.transfers>0)
        val id=repo.savePlan(route,task.id);repo.begin(id,repo.snapshot().journeys.single().row.revision,time(0));return id
    }
    private suspend fun track(id:String,action:(MetroSession)->MetroSession) {assertTrue(repo.tracking(id,repo.snapshot().journeys.single().row.revision,action))}
    @Test fun reopenDuringCombinationKeepsProgressUndoIdentityAndTiming()=runBlocking<Unit> {
        val id=seed();track(id) {MetroStages.arrive(it,time(30_000))};track(id) {MetroStages.beginTransfer(it,time(40_000))}
        val before=repo.snapshot();db.close();open();assertEquals(before,repo.snapshot())
        val active=repo.snapshot().journeys.single().data.active!!
        assertEquals(MetroPhase.TRANSFERRING,active.control!!.phase)
        assertEquals(active.offset,MetroTracking.position(active,time(900_000)).offset)
        assertTrue(repo.undoArrival(id,repo.snapshot().journeys.single().row.revision,time(100_000)))
        val restored=repo.snapshot().journeys.single().data.active!!
        assertEquals(active.id,restored.id);assertEquals(30_000L,restored.offset);assertTrue(restored.control!!.records.isEmpty())
        assertNotNull(repo.snapshot().journeys.single().row.nodeId)
    }
    @Test fun backupRestoresCombinationDurationsAndReversibleTransitionWithoutDuplicatingSessions()=runBlocking<Unit> {
        val id=seed();track(id) {MetroStages.arrive(it,time(30_000))};track(id) {MetroStages.beginTransfer(it,time(40_000))}
        val before=repo.snapshot();val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val exported=backups.create();val inspection=backups.inspect(exported.inputStream())
        track(id) {MetroStages.nextLine(it,time(90_000))}
        backups.restore(inspection);backups.discard(inspection)
        val restored=repo.snapshot().journeys.single()
        assertEquals(before.journeys.single().row.id,restored.row.id);assertEquals(before.journeys.single().row.nodeId,restored.row.nodeId)
        assertEquals(before.journeys.single().data.copy(sessions=before.journeys.single().data.sessions.map(MetroStages::portable)),restored.data)
        assertFalse(restored.data.active!!.control!!.clockTrusted)
        assertTrue(restored.data.active!!.control!!.undo!!.previous.uncertain)
        assertEquals(1,restored.data.sessions.size)
        assertTrue(repo.undoArrival(id,repo.snapshot().journeys.single().row.revision,time(120_000)))
        assertEquals(MetroPhase.RIDING,repo.snapshot().journeys.single().data.active!!.control!!.phase)
    }
    @Test fun repeatedTransitionsDoNotDuplicateRecordsAndStaleRevisionsCannotUndoNewStage()=runBlocking<Unit> {
        val id=seed();track(id) {MetroStages.arrive(it,time(30_000))};val arrived=repo.snapshot().journeys.single()
        track(id) {MetroStages.arrive(it,time(31_000))};assertEquals(arrived,repo.snapshot().journeys.single())
        track(id) {MetroStages.beginTransfer(it,time(40_000))};track(id) {MetroStages.nextLine(it,time(50_000))}
        val after=repo.snapshot();track(id) {MetroStages.nextLine(it,time(60_000))};assertEquals(after,repo.snapshot())
        assertFalse(repo.undoArrival(id,arrived.row.revision,time(70_000)))
        assertThrows(IllegalArgumentException::class.java) {runBlocking {repo.undoArrival(id,after.journeys.single().row.revision,time(80_000))}}
        assertEquals(after,repo.snapshot())
    }
    @Test fun historicalV1AndV2AreReadableAndStopBeforeAutomaticTransferWithoutRewritingOnRead()=runBlocking<Unit> {
        seed();val snapshot=repo.snapshot();val net=snapshot.preferences.network
        val raw=JSONObject(MetroCodec.journey(snapshot.journeys.single().data))
        raw.getJSONArray("sessions").getJSONObject(0).remove("control");raw.getJSONArray("sessions").getJSONObject(0).remove("routeArchives");raw.put("version",2)
        val v2=MetroCodec.journey(raw.toString(),net)
        assertNull(v2.active!!.control);assertNotNull(MetroTracking.position(v2.active!!,time(99_000_000)).waiting)
        raw.put("version",1);raw.getJSONArray("sessions").getJSONObject(0).remove("automatic")
        val v1=MetroCodec.journey(raw.toString(),net);assertFalse(v1.active!!.automatic)
        assertEquals(v2.active!!.id,v1.active!!.id)
        val row=snapshot.journeys.single().row;db.metroDao().save(row.copy(payload=raw.toString()))
        repo.snapshot();assertEquals(raw.toString(),db.metroDao().journey(row.id)!!.payload)
    }
    @Test fun malformedControlAndNestedUndoAreRejectedBeforeReplacingData()=runBlocking<Unit> {
        val id=seed();track(id) {MetroStages.arrive(it,time(30_000))}
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory));val data=backups.snapshot()
        val before=repo.snapshot();val row=data.metroJourneys.single();val raw=JSONObject(row.payload)
        val control=raw.getJSONArray("sessions").getJSONObject(0).getJSONObject("control")
        control.put("firstStep",-1)
        assertThrows(IllegalArgumentException::class.java) {runBlocking {backups.restore(data.copy(metroJourneys=listOf(row.copy(payload=raw.toString()))))}}
        assertEquals(before,repo.snapshot())
        val good=JSONObject(row.payload);val undo=good.getJSONArray("sessions").getJSONObject(0).getJSONObject("control").getJSONObject("undo")
        undo.getJSONObject("previous").getJSONArray("sessions").getJSONObject(0).getJSONObject("control").put("undo",JSONObject())
        assertThrows(IllegalArgumentException::class.java) {MetroCodec.journey(good.toString(),before.preferences.network)}
        assertEquals(before,repo.snapshot())
    }
}
