package com.r0ybt.arachn0de.metro

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.data.repository.NodeRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class MetroReturnPersistenceTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:MetroRepository
    private fun time(ms:Long=0)=MetroTime(1_000_000+ms,100_000+ms,1)
    private fun scheduledTime(time:String)=LocalDateTime.parse("2026-10-09T$time").atZone(ZoneId.of("America/Santiago")).toInstant().toEpochMilli()
    @Before fun setup() {context.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(context);repo=MetroRepository(db,context)}
    @After fun close() {db.close()}
    private suspend fun seed(stops:List<String> = listOf("las-parcelas","los-dominicos"),linked:Boolean=false):MetroJourney {
        val p=repo.snapshot().preferences
        val node=if(linked) {val project=ProjectRepository(db.projectDao(),db).createProject("Proyecto ida");NodeRepository(db).createNode(project.id,null,"Ida").id} else null
        val id=repo.savePlan(MetroPlanner.plan(p.planningNetwork,stops,false,p.restrictions)!!,node)
        return repo.snapshot().journeys.first {it.row.id==id}
    }
    @Test fun finishedMultiLineTripRecalculatesDirectionAndNeverCopiesOutboundSessionOrTaskLink()=runBlocking<Unit> {
        var j=seed(listOf("las-parcelas","santa-ana","tobalaba","plaza-de-puente-alto"),true)
        repo.begin(j.row.id,j.row.revision,time());j=repo.snapshot().journeys.single()
        repo.tracking(j.row.id,j.row.revision) {MetroTracking.finish(it,time(3_000_000))}
        j=repo.snapshot().journeys.single();val before=repo.snapshot()
        val draft=MetroReturn.draft(j);assertEquals("plaza-de-puente-alto",draft.origin);assertEquals("las-parcelas",draft.destination)
        val route=MetroReturn.plan(j,before.preferences)!!;assertEquals(2,route.stops.size)
        val returned=repo.savePlan(route,personId=draft.personId)
        val rows=repo.snapshot().journeys;assertEquals(j,rows.first {it.row.id==j.row.id})
        val other=rows.first {it.row.id==returned};assertNull(other.row.nodeId);assertTrue(other.data.sessions.isEmpty());assertNotEquals(j.row.id,other.row.id)
        route.steps.filter {it.kind==MetroStepKind.RIDE}.forEach {s->val l=before.preferences.planningNetwork.lines.getValue(s.line);assertEquals(s.direction,l.stations.indexOf(s.to)-l.stations.indexOf(s.from))}
    }
    @Test fun activePausedReturnIsOnlyAProposalAndCannotReplaceTheTracking()=runBlocking<Unit> {
        var j=seed();repo.begin(j.row.id,j.row.revision,time());j=repo.snapshot().journeys.single()
        repo.tracking(j.row.id,j.row.revision) {MetroTracking.pause(it,time(30_000))};j=repo.snapshot().journeys.single()
        val before=repo.snapshot();val route=MetroReturn.plan(j,before.preferences)!!
        assertEquals(before,repo.snapshot());val other=repo.savePlan(route)
        val saved=repo.snapshot().journeys.first {it.row.id==other}
        assertThrows(IllegalArgumentException::class.java) {runBlocking {repo.begin(other,saved.row.revision,time(60_000))}}
        assertEquals(j,repo.snapshot().journeys.first {it.row.id==j.row.id})
    }
    @Test fun returnAfterDestinationChangeUsesFinalDestinationAndOriginalOrigin()=runBlocking<Unit> {
        var j=seed();repo.begin(j.row.id,j.row.revision,time());j=repo.snapshot().journeys.single()
        repo.changeDestination(j.row.id,j.row.revision,"santa-ana","republica",time(30_000));j=repo.snapshot().journeys.single()
        assertEquals(MetroReturnDraft("republica","las-parcelas",false,null),MetroReturn.draft(j))
        val proposal=MetroReturn.plan(j,repo.snapshot().preferences)!!
        assertEquals(listOf("republica","las-parcelas"),proposal.stops);assertEquals(1,j.data.active!!.routeArchives.size)
    }
    @Test fun sameStationReturnAndUnavailableReturnDoNotWrite()=runBlocking<Unit> {
        val j=seed(listOf("republica","republica"));val before=repo.snapshot()
        assertTrue(MetroReturn.plan(j,before.preferences)!!.steps.isEmpty())
        assertNull(MetroReturn.plan(j,before.preferences.copy(restrictions=MetroRestrictions(closed=setOf("republica")))))
        assertEquals(before,repo.snapshot())
    }
    @Test fun scheduledActiveRecalibrationAndDestinationSurviveCloseBackupAndRollback()=runBlocking<Unit> {
        val p=repo.snapshot().preferences;val wall=scheduledTime("12:00")
        val route=MetroPlanner.planAt(p.planningNetwork,listOf("las-parcelas","baquedano"),wall,p.restrictions)!!
        val station=route.steps.first().to
        val id=repo.savePlan(route);var j=repo.snapshot().journeys.single()
        val now=MetroTime(wall,100_000,1);repo.begin(id,j.row.revision,now);j=repo.snapshot().journeys.single()
        repo.tracking(id,j.row.revision) {MetroTracking.confirm(it,station,now.copy(wall=wall+60_000,elapsed=160_000))}
        val original=repo.snapshot();db.close();db=Arachn0deDatabase.create(context);repo=MetroRepository(db,context)
        assertEquals(original,repo.snapshot());j=repo.snapshot().journeys.single()
        val changed=now.copy(wall=wall+120_000,elapsed=220_000)
        repo.changeDestination(id,j.row.revision,station,"las-parcelas",changed)
        val before=repo.snapshot();val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val data=backups.snapshot();val file=backups.create();val inspected=backups.inspect(file.inputStream())
        backups.restore(inspected);backups.discard(inspected)
        assertEquals(before.journeys.single().data.copy(sessions=before.journeys.single().data.sessions.map(MetroStages::portable)),repo.snapshot().journeys.single().data)
        val raw=JSONObject(data.metroJourneys.single().payload);raw.getJSONObject("plan").put("departure",-1)
        val stable=repo.snapshot()
        assertThrows(IllegalArgumentException::class.java) {runBlocking {backups.restore(data.copy(metroJourneys=listOf(data.metroJourneys.single().copy(payload=raw.toString()))))}}
        assertEquals(stable,repo.snapshot())
        val recovered=repo.snapshot().journeys.single()
        repo.changeDestination(id,recovered.row.revision,station,"las-parcelas",changed.copy(wall=changed.wall+30_000,elapsed=changed.elapsed+30_000))
        assertEquals(recovered,repo.snapshot().journeys.single())
    }
    @Test fun scheduledReturnUsesNewDateAndServiceInsteadOfReversingOldSteps()=runBlocking<Unit> {
        val p=repo.snapshot().preferences
        val route=MetroPlanner.planAt(p.planningNetwork,listOf("las-parcelas","monte-tabor"),scheduledTime("12:00"),p.restrictions)!!
        repo.savePlan(route);val j=repo.snapshot().journeys.single();val before=repo.snapshot()
        val peak=MetroReturn.plan(j,p,scheduledTime("07:00"))!!
        assertEquals(listOf("monte-tabor","las-parcelas"),peak.stops)
        assertEquals(MetroService.RED,peak.steps.first().service);assertEquals(MetroService.GREEN,peak.steps.last().service)
        val saturday=scheduledTime("07:00")+86_400_000
        assertTrue(MetroReturn.plan(j,p,saturday)!!.steps.all {it.service==MetroService.NORMAL})
        assertEquals(before,repo.snapshot())
    }
    @Test fun startAndNextStageRequireReviewIfReferenceServiceChanged()=runBlocking<Unit> {
        val p=repo.snapshot().preferences;val route=MetroPlanner.planAt(p.planningNetwork,listOf("las-parcelas","monte-tabor"),scheduledTime("12:00"),p.restrictions)!!
        val id=repo.savePlan(route);val j=repo.snapshot().journeys.single()
        assertThrows(IllegalArgumentException::class.java) {runBlocking {repo.begin(id,j.row.revision,MetroTime(scheduledTime("07:00"),100_000,1))}}
        assertEquals(j,repo.snapshot().journeys.single());assertTrue(j.data.sessions.isEmpty())
        val midday=MetroTime(scheduledTime("12:00"),100_000,1)
        repo.begin(id,j.row.revision,midday);var active=repo.snapshot().journeys.single()
        repo.changeDestination(id,active.row.revision,"las-parcelas","monte-tabor",midday);active=repo.snapshot().journeys.single()
        assertThrows(IllegalArgumentException::class.java) {runBlocking {repo.tracking(id,active.row.revision) {MetroStages.nextLine(it,MetroTime(scheduledTime("07:00"),100_000,1))}}}
        assertEquals(active,repo.snapshot().journeys.single())
        assertThrows(IllegalArgumentException::class.java) {runBlocking {repo.changeDestination(id,active.row.revision,"las-parcelas","monte-tabor",MetroTime(scheduledTime("07:00"),100_000,1),expected=active.data.plan)}}
        assertEquals(active,repo.snapshot().journeys.single())
    }
}
