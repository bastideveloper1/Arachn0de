package com.r0ybt.arachn0de.status

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
class TaskStateConfigurationTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    private lateinit var projects:ProjectRepository
    private lateinit var configuration:TaskStateConfiguration
    @Before fun setup() {context.deleteDatabase("arachn0de.db");open()}
    private fun open() {db=Arachn0deDatabase.create(context);nodes=NodeRepository(db);projects=ProjectRepository(db.projectDao(),db);configuration=TaskStateConfiguration(db)}
    @After fun close() {db.close()}
    private suspend fun verify(project:String,parent:String?=null):Node=nodes.createNode(project,parent,"Por verificar").also {nodes.setTaskStatus(it.id,TaskStatus.VERIFY)}
    @Test fun rootConversionRespectsDescendantOverridesOtherProjectsAndActualStates()=runBlocking {
        val p=projects.createProject("Núcleo");val q=projects.createProject("Ajeno")
        projects.setVerificationEnabled(p.id,true);projects.setVerificationEnabled(q.id,true)
        val layer=nodes.createNode(p.id,null,"Capa",purpose=NodePurpose.LAYER)
        val nested=nodes.createNode(p.id,layer.id,"Anidada",purpose=NodePurpose.LAYER)
        val inherited=listOf(verify(p.id),verify(p.id,nested.id))
        val exception=nodes.createNode(p.id,null,"Excepción",purpose=NodePurpose.LAYER)
        nodes.setTaskStateCount(exception.id,3)
        val protectedLayer=verify(p.id,exception.id)
        val protectedTask=verify(p.id,nested.id);nodes.setTaskStateCount(protectedTask.id,3)
        val outside=verify(q.id)
        val pending=nodes.createNode(p.id,nested.id,"Pendiente")
        val complete=nodes.createNode(p.id,nested.id,"Completada");nodes.setCompleted(complete.id,true)
        val untouched=listOf(protectedLayer,protectedTask,outside,pending,complete).associate {it.id to db.nodeDao().getById(it.id)}
        val before=db.backupDao().nodes();val beforeProjects=db.backupDao().projects()
        val preview=configuration.preview(TaskStateConfiguration.Request(p.id,null,2))
        assertEquals(inherited.map {it.id}.toSet(),preview.conversions)
        assertEquals(before,db.backupDao().nodes());assertEquals(beforeProjects,db.backupDao().projects())
        assertTrue(runCatching {projects.setVerificationEnabled(p.id,false)}.isFailure)
        assertEquals(before,db.backupDao().nodes())
        configuration.apply(preview.request,preview)
        inherited.forEach {assertEquals(TaskStatus.PENDING,nodes.getNode(it.id)!!.taskStatus)}
        untouched.forEach {(id,row)->assertEquals(row,db.nodeDao().getById(id))}
        projects.setVerificationEnabled(p.id,true)
        inherited.forEach {assertEquals(TaskStatus.PENDING,nodes.getNode(it.id)!!.taskStatus)}
    }
    @Test fun layerAndIndividualInheritanceChangesOnlyConvertEffectiveThreeToTwo()=runBlocking {
        val p=projects.createProject("Núcleo")
        val layer=nodes.createNode(p.id,null,"Capa",purpose=NodePurpose.LAYER);nodes.setTaskStateCount(layer.id,3)
        val nested=nodes.createNode(p.id,layer.id,"Anidada",purpose=NodePurpose.LAYER)
        val inherited=verify(p.id,nested.id)
        val exception=verify(p.id,nested.id);nodes.setTaskStateCount(exception.id,3)
        val preview=configuration.preview(TaskStateConfiguration.Request(p.id,layer.id,null))
        assertEquals(setOf(inherited.id),preview.conversions)
        configuration.apply(preview.request,preview)
        assertNull(nodes.getNode(layer.id)!!.taskStateCount)
        assertEquals(TaskStatus.PENDING,nodes.getNode(inherited.id)!!.taskStatus)
        assertEquals(TaskStatus.VERIFY,nodes.getNode(exception.id)!!.taskStatus)
        val individual=configuration.preview(TaskStateConfiguration.Request(p.id,exception.id,null))
        assertEquals(setOf(exception.id),individual.conversions)
        assertTrue(runCatching {nodes.setTaskStateCount(exception.id,null)}.isFailure)
        configuration.apply(individual.request,individual)
        assertEquals(TaskStatus.PENDING,nodes.getNode(exception.id)!!.taskStatus)
        assertNull(nodes.getNode(exception.id)!!.taskStateCount)
        assertFalse(projects.getProject(p.id)!!.verificationEnabled)
    }
    @Test fun staleConfirmationAndWriteFailurePreserveEverythingThenBackupAndReopenKeepConversion()=runBlocking {
        val p=projects.createProject("Seguro");projects.setVerificationEnabled(p.id,true)
        val first=verify(p.id)
        val request=TaskStateConfiguration.Request(p.id,null,2)
        val stale=configuration.preview(request)
        val late=verify(p.id)
        val before=db.backupDao().nodes();val events=db.nodeEventDao().all()
        assertTrue(runCatching {configuration.apply(request,stale)}.isFailure)
        assertEquals(before,db.backupDao().nodes());assertEquals(events,db.nodeEventDao().all())
        val preview=configuration.preview(request)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fixture_state_failure BEFORE UPDATE OF verificationEnabled ON projects WHEN NEW.verificationEnabled=0 BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        assertTrue(runCatching {configuration.apply(request,preview)}.isFailure)
        assertEquals(before,db.backupDao().nodes());assertEquals(events,db.nodeEventDao().all())
        assertTrue(projects.getProject(p.id)!!.verificationEnabled)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fixture_state_failure")
        configuration.apply(request,preview)
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val snapshot=backup.snapshot()
        assertEquals(snapshot.nodes,BackupJson.decode(BackupJson.encode(snapshot)).nodes)
        val archive=backup.create("fixture".toCharArray());val inspected=backup.inspect(archive.inputStream(),"fixture".toCharArray())
        nodes.deleteNode(first.id);backup.restore(inspected);backup.discard(inspected)
        assertEquals(snapshot.nodes,db.backupDao().nodes())
        db.close();open()
        for(id in listOf(first.id,late.id)) assertEquals(TaskStatus.PENDING,nodes.getNode(id)!!.taskStatus)
        assertFalse(projects.getProject(p.id)!!.verificationEnabled)
    }
}
