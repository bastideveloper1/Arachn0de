package com.r0ybt.arachn0de.templates

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.metro.*
import com.r0ybt.arachn0de.ui.state.EditorDraft
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class SavedTemplatesTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:SavedTemplateRepository
    private lateinit var backup:BackupRepository
    @Before fun setup() {context.deleteDatabase("arachn0de.db");open()}
    private fun open() {db=Arachn0deDatabase.create(context);repo=SavedTemplateRepository(db);backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))}
    @After fun close() {db.close()}
    private fun row(c:SavedTaskConfiguration=SavedTaskConfiguration("Pagar cuenta"))=SavedTemplateEntity("template","Pago habitual",SavedTemplateCodec.encode(c))
    @Test fun createRenameEditSearchDeleteAndReopenAreIndependentOfTasks()=runBlocking<Unit> {
        repo.save(row(),true);repo.save(row(SavedTaskConfiguration("Título nuevo",priority=Priority.HIGH)).copy(name="Mantenimiento"),false)
        assertEquals("Mantenimiento",repo.observe().first().single().name)
        db.close();open();assertEquals(Priority.HIGH,SavedTemplateCodec.decode(repo.observe().first().single().payload).priority)
        assertTrue(db.backupDao().nodes().isEmpty());repo.delete("template");assertTrue(repo.observe().first().isEmpty())
    }
    @Test fun fromCompletedTaskCopiesConfigurationWithoutIdentityCompletionEventsOrPaymentHistory()=runBlocking<Unit> {
        val p=ProjectRepository(db.projectDao()).createProject("P");val nodes=NodeRepository(db)
        db.personDao().insert(PersonEntity("person","Persona",null));db.technologyDao().insert(TechnologyEntity("b","B",null));db.technologyDao().insert(TechnologyEntity("a","A",null))
        val n=nodes.createNode(p.id,null,"Pago","Texto",obligation=Obligation(1234,"CLP"),priority=Priority.HIGH,responsibleIds=setOf("person"))
        db.technologyDao().assignNodes(listOf(NodeTechnologyEntity(n.id,"b",0),NodeTechnologyEntity(n.id,"a",1)))
        nodes.setCompleted(n.id,true)
        val c=repo.fromTask(n.id);assertEquals(1234L,c.amount);assertEquals(listOf("b","a"),c.technologies)
        val saved=row(c);repo.save(saved,true);nodes.deleteNode(n.id);assertEquals(saved,db.savedTemplateDao().all().single())
        val draft=EditorDraft(null,null,"","");c.apply(draft,0,TimeZone.getTimeZone("UTC"));assertNotEquals(n.id,draft.creationId)
        assertEquals("NONE",draft.recurrenceFrequency);assertFalse(draft.batchEnabled);assertEquals("Pago",draft.title)
        val created=nodes.createNode(p.id,null,draft.title,obligation=draft.obligation());assertFalse(created.isCompleted);assertEquals(1,db.nodeEventDao().eventsForNodes(listOf(created.id)).size)
        assertFalse(saved.payload.contains(n.id));assertFalse(saved.payload.contains("isCompleted"))
    }
    @Test fun datesResolveOnApplyAcrossLeapMonthsYearBoundariesAndRespectLocalHour() {
        val zone=TimeZone.getTimeZone("America/Santiago")
        fun now(y:Int,m:Int,d:Int)=Calendar.getInstance(zone).apply {clear();set(y,m-1,d,12,0)}.timeInMillis
        fun assertDate(rule:TemplateDate,instant:Long,y:Int,m:Int,d:Int,h:Int=9) {val c=Calendar.getInstance(zone).apply {timeInMillis=requireNotNull(rule.resolve(instant,zone))};assertEquals(listOf(y,m-1,d,h,30),listOf(c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH),c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE)))}
        assertDate(TemplateDate(TemplateDateKind.TOMORROW,minute=570),now(2025,12,31),2026,1,1)
        assertDate(TemplateDate(TemplateDateKind.DAY_NEXT_MONTH,31,570),now(2024,1,15),2024,2,29)
        assertDate(TemplateDate(TemplateDateKind.DAY_NEXT_MONTH,31,570),now(2025,1,15),2025,2,28)
        assertDate(TemplateDate(TemplateDateKind.IN_DAYS,7,570),now(2025,12,28),2026,1,4)
        assertDate(TemplateDate(TemplateDateKind.FIRST_DAY_NEXT,minute=570),now(2025,12,31),2026,1,1)
        assertDate(TemplateDate(TemplateDateKind.LAST_DAY,minute=570),now(2024,2,1),2024,2,29)
        val c=SavedTaskConfiguration("Hoy",due=TemplateDate(TemplateDateKind.TODAY,minute=570));val draft=EditorDraft(null,null,"","")
        c.apply(draft,now(2025,1,1),zone);val first=draft.dueAt;c.apply(draft,now(2025,1,2),zone);assertNotEquals(first,draft.dueAt)
    }
    @Test fun deletedReferencesAreReportedAndOmittedWithoutMutatingLibrary()=runBlocking<Unit> {
        val original=row(SavedTaskConfiguration("Configuración",people=listOf("deleted"),technologies=listOf("missing"),tags=listOf("absent")))
        repo.save(original,true);val application=repo.prepare(original)
        assertEquals(3,application.warnings.size);assertTrue(application.configuration.people.isEmpty());assertTrue(application.configuration.technologies.isEmpty());assertTrue(application.configuration.tags.isEmpty());assertEquals(original,db.savedTemplateDao().all().single())
    }
    @Test fun templateAndTechnologyOrderSurviveFullBackupIntoEmptyDatabase()=runBlocking<Unit> {
        backup.restore(BackupFixture.complete().copy(technologies=listOf(TechnologyEntity("a","A",null),TechnologyEntity("b","B",null)),nodeTechnologies=listOf(NodeTechnologyEntity("task","b",0),NodeTechnologyEntity("task","a",1))))
        val saved=row(SavedTaskConfiguration("Reusable",people=listOf("r"),technologies=listOf("b","a"),due=TemplateDate(TemplateDateKind.DAY_NEXT_MONTH,15,630)))
        repo.save(saved,true);val external=backup.create().readBytes();db.close();context.deleteDatabase("arachn0de.db");open()
        val candidate=backup.inspect(external.inputStream());backup.restore(candidate);backup.discard(candidate)
        assertEquals(saved,db.savedTemplateDao().all().single());assertEquals(listOf("b","a"),db.technologyDao().nodes().filter {it.nodeId=="task"}.map {it.technologyId});assertEquals(15,repo.prepare(saved).configuration.due.number)
        assertEquals(2,db.personDao().assignmentIds("bill").size)
    }
    @Test fun genuineV16BackupWithoutTemplatesOrPositionsRestoresPreviousCatalogOrder()=runBlocking<Unit> {
        val original=BackupFixture.empty().copy(projects=listOf(ProjectEntity("p","P","",0,1,1)),technologies=listOf(TechnologyEntity("a","Zulu",null),TechnologyEntity("b","Alpha",null)),projectTechnologies=listOf(ProjectTechnologyEntity("p","a"),ProjectTechnologyEntity("p","b")))
        val old=JSONObject(BackupJson.encode(original).toString(Charsets.UTF_8)).apply {put("dataVersion",16);BackupFixture.removeSprintFields(this)}
        val decoded=BackupJson.decode(old.toString().toByteArray());assertTrue(decoded.savedTemplates.isEmpty());assertEquals(mapOf("a" to 1,"b" to 0),decoded.projectTechnologies.associate {it.technologyId to it.position})
        backup.restore(decoded);assertEquals(listOf("b","a"),db.technologyDao().projects().map {it.technologyId})
    }
    @Test fun corruptionDuplicateIdsAndFailedRestoreLeaveExistingDataUntouched()=runBlocking<Unit> {
        repo.save(row(),true);val before=backup.snapshot()
        assertTrue(runCatching {backup.restore(before.copy(savedTemplates=listOf(row(),row())))}.isFailure)
        assertTrue(runCatching {backup.restore(before.copy(savedTemplates=listOf(row().copy(payload="{}"))))}.isFailure)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_template BEFORE INSERT ON saved_templates BEGIN SELECT RAISE(ABORT,'injected'); END")
        assertTrue(runCatching {backup.restore(before.copy(savedTemplates=listOf(row().copy(name="Changed"))))}.isFailure)
        assertEquals(before.savedTemplates,db.savedTemplateDao().all())
        val json=row().payload.replace("\"version\":1","\"version\":1,\"version\":1");assertTrue(runCatching {SavedTemplateCodec.decode(json)}.isFailure)
    }
    @Test fun metroTemplateCopiesPlanWithoutAnySessionsAndCreatesIndependentInactiveTrip()=runBlocking<Unit> {
        val p=ProjectRepository(db.projectDao()).createProject("P");val nodes=NodeRepository(db);val metro=MetroRepository(db,context)
        val net=metro.snapshot().preferences.planningNetwork;val route=MetroPlanner.plan(net,listOf("los-heroes","tobalaba"),false,MetroRestrictions())!!
        val node=nodes.createNode(p.id,null,"Viaje");val id=metro.savePlan(route,node.id);assertTrue(metro.begin(id,metro.snapshot().journeys.single().row.revision,MetroTime(100,100,1)))
        val c=repo.fromTask(node.id);assertTrue(MetroCodec.journey(c.metroPlan!!,MetroNetwork.decode(c.metroCatalog!!)).sessions.isEmpty())
        val draft=EditorDraft(null,null,"","");c.apply(draft,0,TimeZone.getTimeZone("UTC"))
        nodes.withEditorAssignments(draft.creationId,c.technologies,c.metroCatalog,c.metroPlan,c.traveler) {nodes.createNode(p.id,null,draft.title,creationId=draft.creationId)}
        val journey=metro.snapshot().journeys.single {it.row.nodeId==draft.creationId};assertEquals(route,journey.data.plan);assertNull(journey.data.active);assertTrue(journey.data.sessions.isEmpty());assertNotEquals(id,journey.row.id)
        assertNotNull(metro.snapshot().journeys.single {it.row.id==id}.data.active)
    }
    @Test fun invalidAssignmentRollsBackTaskCreationAndDoesNotChangePersons()=runBlocking<Unit> {
        val p=ProjectRepository(db.projectDao()).createProject("P");val nodes=NodeRepository(db)
        assertTrue(runCatching {nodes.withEditorAssignments("new",listOf("deleted"),null,null,null) {nodes.createNode(p.id,null,"Task",creationId="new")}}.isFailure)
        assertNull(db.nodeDao().getById("new"));assertTrue(db.personDao().observePeople().first().isEmpty())
    }
}
