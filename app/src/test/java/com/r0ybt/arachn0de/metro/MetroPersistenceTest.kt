package com.r0ybt.arachn0de.metro

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.Priority
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class MetroPersistenceTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: Arachn0deDatabase
    private lateinit var repo: MetroRepository
    private lateinit var backup: BackupRepository
    private val time=MetroTime(1_000_000,100_000,1)
    @Before fun setup() {context.deleteDatabase("arachn0de.db");open()}
    private fun open() {db=Arachn0deDatabase.create(context);repo=MetroRepository(db,context);backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))}
    @After fun close() {db.close()}
    private suspend fun plan(): MetroRoute {val n=repo.snapshot().preferences.network;return MetroPlanner.plan(n,listOf("los-heroes","tobalaba"),false,MetroRestrictions())!!}
    @Test fun masterCorrectionPreservesLegacyPreferencesActiveJourneyAndFullBackupRoundTrip()=runBlocking<Unit> {
        val current=repo.snapshot().preferences.network
        val old=MetroCatalogRevision.forRoute(current,MetroCatalogRevision.ORIGINAL)
        val oldRaw=org.json.JSONObject(context.assets.open("metro/santiago-beta1.json").bufferedReader().use {it.readText()}).apply {
            put("version",old.version)
            val lines=getJSONArray("lines")
            repeat(lines.length()) { i->val l=lines.getJSONObject(i);if(l.getString("id")=="L5") l.put("express",org.json.JSONArray(old.lines.getValue("L5").express)) }
        }.toString()
        val prefs=MetroPreferences(oldRaw,"carlos-valdovinos",setOf("san-joaquin"),MetroRestrictions(closed=setOf("los-dominicos")))
        db.metroDao().preferences(MetroPreferencesEntity(payload=MetroCodec.preferences(prefs)))
        val historicRoute=MetroRoute(old.version,listOf("carlos-valdovinos","san-joaquin"),true,listOf(
            MetroStep("carlos-valdovinos","camino-agricola","L5",1,MetroService.RED,MetroStepKind.RIDE),
            MetroStep("camino-agricola","san-joaquin","L5",1,MetroService.RED,MetroStepKind.RIDE)))
        val session=MetroTracking.pause(MetroTracking.start(historicRoute,time),time.copy(wall=time.wall+20_000,elapsed=time.elapsed+20_000))
        val payload=MetroCodec.journey(MetroJourneyData(historicRoute,listOf(session)))
        db.metroDao().save(MetroJourneyEntity("historic",payload=payload,revision=7))
        val before=repo.snapshot()
        assertEquals(MetroCatalogRevision.CORRECTED,before.preferences.planningNetwork.version)
        assertEquals(current.lines,before.preferences.planningNetwork.lines)
        val fresh=MetroPlanner.plan(before.preferences.network,listOf("carlos-valdovinos","san-joaquin"),true,prefs.restrictions)!!
        assertEquals(MetroCatalogRevision.CORRECTED,fresh.networkVersion);assertEquals(MetroService.GREEN,fresh.steps.first().service)
        repo.savePlan(fresh)
        val after=repo.snapshot();assertEquals(before.preferences,after.preferences)
        assertEquals(before.journeys.single(),after.journeys.single {it.row.id=="historic"})
        assertEquals(payload,db.metroDao().journey("historic")!!.payload)
        assertFalse(MetroPlanner.affected(MetroRoute(old.version,listOf("los-heroes","republica"),false,listOf(MetroStep("los-heroes","republica","L1",-1,MetroService.NORMAL,MetroStepKind.RIDE))),current,MetroRestrictions()))
        val archive=backup.create().readBytes();db.close();context.deleteDatabase("arachn0de.db");open()
        val candidate=backup.inspect(archive.inputStream());backup.restore(candidate);backup.discard(candidate)
        val restored=repo.snapshot();assertEquals(prefs,restored.preferences)
        assertEquals(after.journeys.map {it.data.plan}.toSet(),restored.journeys.map {it.data.plan}.toSet())
        val recovered=restored.journeys.single {it.row.id=="historic"}.data.active!!
        assertEquals(historicRoute,recovered.route);assertEquals(session.events,recovered.events);assertEquals(session.pausedAt,recovered.pausedAt)
        assertTrue(recovered.uncertain)
        assertEquals(current.lines,restored.preferences.planningNetwork.lines)
    }
    @Test fun settingsSessionsCorrectionsAndOriginalPlansSurviveProcessRecreation()=runBlocking<Unit> {
        repo.settings {it.copy(home="los-heroes",favorites=setOf("tobalaba"),restrictions=MetroRestrictions(closed=setOf("baquedano"),avoided=setOf("el-golf"),interrupted=setOf(MetroRestrictions.segment("L2","hospital-el-pino","copa-lo-martinez"))))}
        val id=repo.savePlan(plan());var j=repo.snapshot().journeys.single();assertTrue(repo.begin(id,j.row.revision,time))
        j=repo.snapshot().journeys.single();assertTrue(repo.tracking(id,j.row.revision) {MetroTracking.pause(it,time.copy(wall=time.wall+60_000,elapsed=time.elapsed+60_000))})
        j=repo.snapshot().journeys.single();assertTrue(repo.tracking(id,j.row.revision) {MetroTracking.confirm(it,"baquedano",time.copy(wall=time.wall+90_000,elapsed=time.elapsed+90_000))})
        val before=repo.snapshot();db.close();open();assertEquals(before,repo.snapshot());assertNotNull(repo.snapshot().journeys.single().data.active!!.pausedAt)
    }
    @Test fun taskConversionKeepsAllFieldsAndResponsiblesAndPersonDeletionFallsBackWithoutMediaCopy()=runBlocking<Unit> {
        val p=ProjectRepository(db.projectDao()).createProject("Metro");val nodes=NodeRepository(db)
        db.personDao().insert(PersonEntity("a","Ana",null));db.personDao().insert(PersonEntity("b","Bea",null))
        val node=nodes.createNode(p.id,null,"Título original","Descripción",priority=Priority.HIGH,startAt=100,dueAt=200,responsibleIds=setOf("a","b"))
        val before=db.nodeDao().getById(node.id);val id=repo.savePlan(plan(),node.id,"a")
        repo.enabled(id,false);repo.enabled(id,true)
        assertEquals(before,db.nodeDao().getById(node.id));assertEquals(setOf("a","b"),db.personDao().assignmentIds(node.id).toSet())
        repo.traveler(id,"b");db.personDao().delete("b")
        assertNull(repo.snapshot().journeys.single().row.personId);assertEquals(before,db.nodeDao().getById(node.id))
        assertFalse(java.io.File(context.filesDir,"metro").exists())
        nodes.deleteNode(node.id);assertNull(repo.snapshot().journeys.single().row.nodeId)
    }
    @Test fun originalSharedAvatarAndUpdatedFramingRemainPersonReferencesWithoutNewFiles()=runBlocking<Unit> {
        backup.restore(BackupFixture.complete())
        val person=db.personDao().get("r")!!
        val originalFiles=java.io.File(context.filesDir,"avatars").listFiles()!!.map {it.name}.toSet()
        val id=repo.savePlan(plan(),"bill","r")
        repo.begin(id,repo.snapshot().journeys.single().row.revision,time)
        db.personDao().update("r","Roy actualizado",person.avatarFile,2f,.2f,-.1f)
        assertEquals(person.avatarFile,db.personDao().get("r")!!.avatarFile)
        assertEquals(2f,db.personDao().get("r")!!.avatarZoom)
        assertEquals("r",repo.snapshot().journeys.single().row.personId)
        repo.traveler(id,"s");assertEquals("s",repo.snapshot().journeys.single().data.active!!.personId)
        assertEquals(originalFiles,java.io.File(context.filesDir,"avatars").listFiles()!!.map {it.name}.toSet())
        assertEquals(setOf("r","s"),db.personDao().assignmentIds("bill").toSet())
    }
    @Test fun editingOneTaskCannotReplaceAnotherTasksJourneyAssociation()=runBlocking<Unit> {
        val p=ProjectRepository(db.projectDao()).createProject("Metro");val nodes=NodeRepository(db)
        val a=nodes.createNode(p.id,null,"A");val b=nodes.createNode(p.id,null,"B")
        val id=repo.savePlan(plan(),a.id);val before=repo.snapshot()
        assertThrows(IllegalArgumentException::class.java) {runBlocking {repo.savePlan(plan(),b.id,existingId=id)}}
        assertEquals(before,repo.snapshot());assertEquals(a.id,repo.snapshot().journeys.single().row.nodeId)
    }
    @Test fun createOneTaskForMultipleStopsAndDisablePreservesItsProperties()=runBlocking<Unit> {
        val p=ProjectRepository(db.projectDao()).createProject("Metro")
        val n=repo.snapshot().preferences.network
        val route=MetroPlanner.plan(n,listOf("los-heroes","tobalaba","cerrillos"),false,MetroRestrictions())!!
        val id=repo.createTask(route,p.id,null,"Viaje de hoy",null,Priority.LOW,100,200,"Recoger documentos")
        val j=repo.snapshot().journeys.single();assertEquals(id,j.row.id)
        val node=db.nodeDao().getById(j.row.nodeId!!)!!;assertEquals("Recoger documentos",node.description);assertEquals("LOW",node.priority)
        repo.enabled(id,false);assertEquals(node,db.nodeDao().getById(node.id))
    }
    @Test fun compareAndSetPreventsDoubleBoardPauseAndStaleCommandsAndCancellationRollsBack()=runBlocking<Unit> {
        val id=repo.savePlan(plan());val revision=repo.snapshot().journeys.single().row.revision
        val attempts=coroutineScope {List(6) {async(Dispatchers.Default) {repo.begin(id,revision,time)}}.awaitAll()};assertEquals(1,attempts.count {it})
        val j=repo.snapshot().journeys.single();val before=repo.snapshot()
        assertThrows(CancellationException::class.java) {runBlocking {repo.tracking(id,j.row.revision) {throw CancellationException("test")}}};assertEquals(before,repo.snapshot())
        assertTrue(repo.tracking(id,j.row.revision) {MetroTracking.pause(it,time)});assertFalse(repo.tracking(id,j.row.revision) {MetroTracking.pause(it,time)})
        val second=repo.savePlan(plan());assertThrows(IllegalArgumentException::class.java) {runBlocking {repo.begin(second,repo.snapshot().journeys.first {it.row.id==second}.row.revision,time)}}
    }
    @Test fun fullBackupRestoresCatalogPreferencesTaskPersonAndHistoryAndInvalidRestoreIsAtomic()=runBlocking<Unit> {
        val p=ProjectRepository(db.projectDao()).createProject("Protected")
        db.personDao().insert(PersonEntity("person","Ana",null))
        repo.settings {it.copy(home="los-heroes",favorites=setOf("tobalaba"))}
        val id=repo.createTask(plan(),p.id,null,"Viaje", "person",Priority.HIGH)
        var j=repo.snapshot().journeys.single();repo.begin(id,j.row.revision,time);j=repo.snapshot().journeys.single()
        repo.tracking(id,j.row.revision) {MetroTracking.confirm(it,"baquedano",time.copy(wall=time.wall+20_000,elapsed=time.elapsed+20_000))}
        val before=repo.snapshot();val archive=backup.create().readBytes();val oldRevision=repo.snapshot().journeys.single().row.revision
        db.close();context.deleteDatabase("arachn0de.db");open()
        val candidate=backup.inspect(archive.inputStream());backup.restore(candidate);backup.discard(candidate)
        val restored=repo.snapshot();assertEquals(before.preferences,restored.preferences)
        assertEquals(before.journeys.single().data.plan,restored.journeys.single().data.plan)
        assertEquals(before.journeys.single().data.active!!.events,restored.journeys.single().data.active!!.events)
        assertTrue(restored.journeys.single().data.active!!.automatic);assertTrue(restored.journeys.single().data.active!!.uncertain);assertFalse(restored.journeys.single().data.active!!.historyElapsedTrusted);assertEquals(80_000L,MetroTracking.totalMillis(restored.journeys.single().data.active!!,MetroTime(time.wall+80_000,8_000_000,1)));assertEquals("person",restored.journeys.single().row.personId)
        assertFalse(repo.tracking(id,oldRevision) {MetroTracking.pause(it,time)})
        val invalid=BackupFixture.empty().copy(metroPreferences=MetroCodec.preferences(restored.preferences),metroJourneys=listOf(restored.journeys.single().row.copy(nodeId="missing")))
        assertThrows(IllegalArgumentException::class.java) {runBlocking {backup.restore(invalid)}};assertEquals(restored,repo.snapshot());assertEquals("Protected",db.projectDao().getById(p.id)!!.name)
        val historical=JSONObject(String(BackupJson.encode(BackupFixture.empty()))).put("dataVersion",15).apply {BackupFixture.removeSprintFields(this);remove("metroPreferences");remove("metroJourneys")}
        backup.restore(BackupJson.decode(historical.toString().toByteArray()));assertTrue(repo.snapshot().journeys.isEmpty());assertNull(repo.snapshot().preferences.home)
    }
    @Test fun databaseRestoreFailureRollsBackMetroAndProductivityAndClearsNoFiles()=runBlocking<Unit> {
        repo.savePlan(plan());val before=repo.snapshot();val prefs=MetroCodec.preferences(before.preferences)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_metro BEFORE INSERT ON metro_preferences BEGIN SELECT RAISE(ABORT,'injected'); END")
        assertThrows(android.database.sqlite.SQLiteException::class.java) {runBlocking {backup.restore(BackupFixture.empty().copy(metroPreferences=prefs))}}
        assertEquals(before,repo.snapshot())
    }    @Test fun editingAttachedPlanPreservesTaskMetadataAndActiveAutomaticSessionAcrossBackup()=runBlocking<Unit> {
        val project=ProjectRepository(db.projectDao()).createProject("P")
        val task=NodeRepository(db).createNode(project.id,null,"Título",description="Descripción",priority=Priority.HIGH,startAt=100,dueAt=200)
        val route=plan();val id=repo.savePlan(route,task.id)
        repo.begin(id,repo.snapshot().journeys.single().row.revision,time)
        val active=repo.snapshot().journeys.single().data.active!!;val metadata=db.nodeDao().getById(task.id)
        val next=MetroPlanner.plan(repo.snapshot().preferences.planningNetwork,listOf("los-heroes","cerrillos"),false,MetroRestrictions())!!
        repo.savePlan(next,task.id,existingId=id)
        assertEquals(metadata,db.nodeDao().getById(task.id));assertEquals(next,repo.snapshot().journeys.single().data.plan)
        assertEquals(active,repo.snapshot().journeys.single().data.active)
        val before=repo.snapshot()
        val corrupt=JSONObject(before.journeys.single().row.payload).apply {getJSONArray("sessions").getJSONObject(0).put("automatic","true")}
        val invalid=BackupFixture.empty().copy(metroPreferences=MetroCodec.preferences(before.preferences),metroJourneys=listOf(before.journeys.single().row.copy(nodeId=null,payload=corrupt.toString())))
        assertThrows(Exception::class.java) {runBlocking {backup.restore(invalid)}}
        assertEquals(before,repo.snapshot());assertEquals(metadata,db.nodeDao().getById(task.id))
        val data=backup.create().readBytes();val inspection=backup.inspect(data.inputStream());backup.restore(inspection);backup.discard(inspection)
        val restored=repo.snapshot().journeys.single().data
        assertEquals(next,restored.plan);assertEquals(route,restored.active!!.route);assertTrue(restored.active!!.automatic);assertTrue(restored.active!!.uncertain)
    }

}
