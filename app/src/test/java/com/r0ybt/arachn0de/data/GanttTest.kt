package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.gantt.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class)
class GanttTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    @Before fun setup() {app.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(app)}
    @After fun close() {db.close()}
    private suspend fun demo()=GanttDemo.create(db,PersonRepository(db,AvatarStore(app,syncDirectory=BackupFixture::syncDirectory)),LocalDate.of(2026,10,11),ZoneId.of("America/Santiago"))
    private suspend fun document(d:GanttDemo.Result,s:GanttSelection):GanttDocument {
        val state=ManagementRepository(db).iterations.observe(d.context).first()
        return ganttDocument(state,d.context,s,workResponsibilities(state.people.records.nodes,db.backupDao().assignments().map {it.nodeId to it.personId}),db.ganttAnnotationDao().all(),"DEMO",LocalDate.of(2026,10,11),ZoneId.of("America/Santiago"))
    }
    @Test fun realDemoIdentityPhasesAndIndependentDates()=runBlocking {
        val d=demo();assertEquals(6,db.backupDao().persons().size)
        assertEquals(3,db.iterationDao().plans().size)
        val tree=db.backupDao().nodes();assertEquals(27,tree.count {it.purpose=="ACTION"})
        assertEquals(9,db.backlogDao().roles().count {it.role=="STORY"})
        assertEquals(27,db.sprintLinkDao().all().size);assertEquals(9,db.cascadeRelationDao().stories().size)
        val doc=document(d,GanttSelection(sprint=d.sprints[1]));assertEquals(9,doc.total);assertEquals(3,doc.completed)
        val all=document(d,GanttSelection(scope=GanttScope.ALL));assertEquals(listOf("Cerrado","En curso","Previsto"),d.sprints.map {id->all.lanes.flatMap {it.items}.first {it.id==id}.status})
        assertTrue(doc.items.filter {it.title.startsWith("HU")}.all {it.start!=null && it.end!=null && it.end<doc.last})
        val story=doc.items.first {it.title.startsWith("HU")};val before=db.backupDao().nodes();assertTrue(runCatching {ManagementRepository(db).gantt.planStory(d.context,story.id,100,50)}.isFailure);assertEquals(before,db.backupDao().nodes())
    }
    @Test fun monthWeekNoInventedDatesAndSharedTotals()=runBlocking {
        val d=demo();val all=document(d,GanttSelection(scope=GanttScope.ALL));assertEquals(27,all.total)
        val persons=document(d,GanttSelection(scope=GanttScope.ALL,people=true));assertEquals(all.total,persons.total)
        assertTrue(persons.lanes.flatMap {it.items}.count()>all.items.size)
        val week=document(d,GanttSelection(scope=GanttScope.WEEK,sprint=d.sprints[1],anchor=d.base))
        assertTrue(week.items.all {(it.start==null && it.end==null) || ((it.start ?: it.end!!)<=week.last && (it.end ?: it.start!!)>=week.first)})
        val month=document(d,GanttSelection(scope=GanttScope.MONTH,sprint=d.sprints[1],anchor=d.base))
        assertEquals(31,month.ticks.size)
        assertTrue(all.items.any {it.start==null && it.end==null})
    }
    @Test fun calendarLeapYearAndYearBoundary() {
        assertEquals(29,ganttTicks(LocalDate.of(2024,2,1),LocalDate.of(2024,2,29),GanttScale.DAY).size)
        val weeks=ganttTicks(LocalDate.of(2026,12,29),LocalDate.of(2027,1,8),GanttScale.WEEK)
        assertEquals(LocalDate.of(2027,1,3),weeks.first().end)
        assertEquals(LocalDate.of(2027,1,8),weeks.last().end)
    }
    @Test fun annotationBackup30Historical29AndInvalidBeforeReplacement()=runBlocking {
        val d=demo();val backup=BackupRepository(db,app,BackupAvatarFiles(app,BackupFixture::syncDirectory));val before=backup.snapshot()
        val json=BackupJson.encode(before);assertEquals(30,org.json.JSONObject(String(json)).getInt("dataVersion"))
        val decoded=BackupJson.decode(json);assertEquals(before.ganttAnnotations,decoded.ganttAnnotations)
        backup.restore(decoded);assertEquals(before.nodes,db.backupDao().nodes());assertEquals(before.ganttAnnotations,db.ganttAnnotationDao().all())
        val old=before.copy(ganttAnnotations=emptyList());assertEquals(29,org.json.JSONObject(String(BackupJson.encode(old))).getInt("dataVersion"))
        assertTrue(BackupJson.decode(BackupJson.encode(old)).ganttAnnotations.isEmpty())
        val invalid=before.copy(ganttAnnotations=before.ganttAnnotations.map {it.copy(contextId="missing")})
        assertTrue(runCatching {backup.restore(invalid)}.isFailure);assertEquals(before.nodes,db.backupDao().nodes())
        assertTrue(runCatching {validateGanttAnnotations(listOf(before.ganttAnnotations.first().copy(lane=Double.NaN)),setOf(d.context))}.isFailure)
    }
    @Test fun annotationEditMoveDeleteReopen()=runBlocking {
        val d=demo();val a=db.ganttAnnotationDao().all().first();val repo=ManagementRepository(db).gantt
        repo.save(d.context,"⏸️ Aplazar",a.epochDay+1,.91,a.id);db.close();db=Arachn0deDatabase.create(app)
        assertEquals("⏸️ Aplazar",db.ganttAnnotationDao().all().first {it.id==a.id}.text)
        assertEquals(.91,db.ganttAnnotationDao().all().first {it.id==a.id}.lane,0.0)
        ManagementRepository(db).gantt.delete(d.context,a.id);assertFalse(db.ganttAnnotationDao().all().any {it.id==a.id})
    }
    @Test fun exportPaginationIncludesAllLanesTicksAndBounds()=runBlocking {
        val d=demo();val doc=document(d,GanttSelection(scope=GanttScope.ALL))
        listOf(true,false).forEach {vertical->val pages=GanttRenderer.pages(doc,vertical)
            assertEquals(doc.items.filter {it.kind=="Historia"}.map {it.id}.toSet(),pages.flatMap {it.lanes}.map {it.id}.toSet())
            assertEquals(doc.ticks,pages.flatMap {it.ticks}.distinct())
            assertTrue(pages.all {it.width.toLong()*it.height<=4_000_000})
        }
    }
    @Test fun explicitDemoIsAtomicIndependentRestorableAndDeletable()=runBlocking {
        val projects=ProjectRepository(db.projectDao(),db)
        val existing=projects.createProject("Proyecto real","Conservar")
        val nodes=NodeRepository(db)
        val original=nodes.createNode(existing.id,null,"Trabajo real")
        val backup=BackupRepository(db,app,BackupAvatarFiles(app,BackupFixture::syncDirectory))
        val before=backup.snapshot()
        assertTrue(runCatching {GanttDemo.create(db,PersonRepository(db,AvatarStore(app,syncDirectory=BackupFixture::syncDirectory)),beforeCommit={error("Fallo antes del commit")})}.isFailure)
        assertEquals(before,backup.snapshot().copy(createdAt=before.createdAt))
        assertTrue(runCatching {GanttDemo.create(db,PersonRepository(db,AvatarStore(app,syncDirectory=BackupFixture::syncDirectory)),beforeCommit={throw kotlinx.coroutines.CancellationException("Cancelar")})}.isFailure)
        assertEquals(before,backup.snapshot().copy(createdAt=before.createdAt))
        val first=demo();val second=demo()
        assertNotEquals(first.project,second.project)
        assertNotEquals(projects.getProject(first.project)!!.name,projects.getProject(second.project)!!.name)
        assertEquals(original.id,db.nodeDao().getById(original.id)!!.id)
        assertEquals(before.persons,db.backupDao().persons().filter {it.id !in first.people+second.people})
        val snapshot=backup.snapshot()
        backup.restore(BackupJson.decode(BackupJson.encode(snapshot)))
        assertEquals(snapshot,backup.snapshot().copy(createdAt=snapshot.createdAt))
        db.close();db=Arachn0deDatabase.create(app)
        assertEquals(snapshot,BackupRepository(db,app,BackupAvatarFiles(app,BackupFixture::syncDirectory)).snapshot().copy(createdAt=snapshot.createdAt))
        db.close();app.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(app)
        val emptyBackup=BackupRepository(db,app,BackupAvatarFiles(app,BackupFixture::syncDirectory))
        emptyBackup.restore(BackupJson.decode(BackupJson.encode(snapshot)))
        assertEquals(snapshot,emptyBackup.snapshot().copy(createdAt=snapshot.createdAt))
        assertTrue(ProjectRepository(db.projectDao(),db).deleteProject(first.project))
        assertFalse(db.backupDao().nodes().any {it.projectId==first.project})
        assertFalse(db.managementContextDao().all().any {it.id==first.context})
        assertEquals(3,db.iterationDao().plans().size)
        assertTrue(db.ganttAnnotationDao().all().all {it.contextId==second.context})
        assertEquals(existing,ProjectRepository(db.projectDao(),db).getProject(existing.id))
        assertNotNull(db.projectDao().getById(second.project))
        assertTrue(db.backupDao().persons().map {it.id}.containsAll(first.people))
    }
    @Test fun demoKanbanChangesImmediatelyFeedGanttAndPlans()=runBlocking {
        val d=demo();val repo=ManagementRepository(db)
        val before=document(d,GanttSelection(sprint=d.sprints[1]))
        val task=before.items.first {it.kind=="Tarea" && it.status=="Planificada"}
        repo.kanban.confirm(d.context,task.id)
        val after=document(d,GanttSelection(sprint=d.sprints[1]))
        assertEquals("Haciendo",after.items.first {it.id==task.id}.status)
        assertNotEquals(before.sprintProgress,after.sprintProgress)
        assertEquals(after.items.size,after.items.distinctBy {it.id}.size)
        val story=after.items.first {it.kind=="Historia"}
        val newDate=d.base.plusDays(20).atStartOfDay(ZoneId.of("America/Santiago")).toInstant().toEpochMilli()
        repo.gantt.planStory(d.context,story.id,newDate,newDate)
        assertEquals(d.base.plusDays(20),document(d,GanttSelection(scope=GanttScope.ALL)).items.first {it.id==story.id}.start)
    }

    @Test fun periodIncludesSpanningActivitiesAndUndatedWithoutChangingStoredDates()=runBlocking {
        val d=demo();val repo=ManagementRepository(db)
        val story=db.cascadeRelationDao().stories().first {it.sprintId==d.sprints[1]}.storyId
        fun time(day:LocalDate)=day.atStartOfDay(ZoneId.of("America/Santiago")).toInstant().toEpochMilli()
        repo.gantt.planStory(d.context,story,time(d.base.minusDays(3)),time(d.base.plusDays(20)))
        val before=db.backupDao().nodes()
        val week=document(d,GanttSelection(scope=GanttScope.WEEK,sprint=d.sprints[1],anchor=d.base))
        assertTrue(week.items.any {it.id==story && it.start!!<week.first && it.end!!>week.last})
        val future=document(d,GanttSelection(scope=GanttScope.WEEK,sprint=d.sprints[2],anchor=d.base))
        assertTrue(future.items.any {it.start==null && it.end==null})
        assertEquals(before,db.backupDao().nodes())
    }

}
