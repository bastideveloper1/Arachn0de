package com.r0ybt.arachn0de.status

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.cleanup.CompletedCleanup
import com.r0ybt.arachn0de.ui.theme.*
import com.r0ybt.arachn0de.security.EncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class TaskStatusTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    private lateinit var projects:ProjectRepository
    @Before fun setup() {context.deleteDatabase("arachn0de.db");open()}
    private fun open() {db=Arachn0deDatabase.create(context);nodes=NodeRepository(db);projects=ProjectRepository(db.projectDao(),db)}
    @After fun close() {db.close()}
    private fun backup()=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
    @Test fun inheritedAcrossLayersIndependentCoresAndAllDirectTransitions()=runBlocking<Unit> {
        val a=projects.createProject("A");val b=projects.createProject("B")
        assertFalse(a.verificationEnabled)
        val la=nodes.createNode(a.id,null,"Capa A",purpose=NodePurpose.LAYER)
        val lb=nodes.createNode(a.id,la.id,"Capa B",purpose=NodePurpose.LAYER)
        val tasks=listOf(la.id,lb.id,null).map {nodes.createNode(a.id,it,"Tarea")}
        val other=nodes.createNode(b.id,null,"Otra")
        assertTrue(runCatching {nodes.setTaskStatus(tasks.first().id,TaskStatus.VERIFY)}.isFailure)
        projects.setVerificationEnabled(a.id,true)
        assertTrue(runCatching {nodes.setTaskStatus(other.id,TaskStatus.VERIFY)}.isFailure)
        tasks.forEach {task->
            assertTrue(nodes.setTaskStatus(task.id,TaskStatus.COMPLETED))
            assertTrue(nodes.setTaskStatus(task.id,TaskStatus.VERIFY))
            assertFalse(nodes.getNode(task.id)!!.isCompleted)
            assertEquals(task.parentId,nodes.getNode(task.id)!!.parentId)
        }
        assertTrue(runCatching {projects.setVerificationEnabled(a.id,false)}.isFailure)
        val configuration=com.r0ybt.arachn0de.data.repository.TaskStateConfiguration(db)
        val preview=configuration.preview(com.r0ybt.arachn0de.data.repository.TaskStateConfiguration.Request(a.id,null,2))
        assertTrue(configuration.apply(preview.request,preview))
        assertTrue(tasks.all {nodes.getNode(it.id)!!.taskStatus==TaskStatus.PENDING})
        assertFalse(projects.getProject(a.id)!!.verificationEnabled)
        projects.setVerificationEnabled(a.id,true)
        tasks.forEach {task->
            assertTrue(nodes.setTaskStatus(task.id,TaskStatus.PENDING));assertFalse(nodes.getNode(task.id)!!.awaitingVerification)
            assertTrue(nodes.setTaskStatus(task.id,TaskStatus.VERIFY));assertTrue(nodes.setCompleted(task.id,true))
            assertEquals(TaskStatus.COMPLETED,nodes.getNode(task.id)!!.taskStatus)
        }
        projects.setVerificationEnabled(a.id,false)
        assertFalse(projects.getProject(a.id)!!.verificationEnabled)
        assertFalse(projects.getProject(b.id)!!.verificationEnabled)
    }
    @Test fun filtersNeverWriteAndCleanupExcludesVerificationAndGuardsKeepState()=runBlocking<Unit> {
        val p=projects.createProject("A");projects.setVerificationEnabled(p.id,true)
        val layer=nodes.createNode(p.id,null,"Capa",purpose=NodePurpose.LAYER)
        val pending=nodes.createNode(p.id,layer.id,"Pendiente")
        val verify=nodes.createNode(p.id,layer.id,"Verificar")
        val done=nodes.createNode(p.id,layer.id,"Completa")
        nodes.setTaskStatus(verify.id,TaskStatus.VERIFY);nodes.setCompleted(done.id,true)
        val before=db.backupDao().nodes()
        TaskStatus.entries.forEach {status->
            val result=NodeFilter(taskStatus=status).apply(nodes.getProjectNodes(p.id),emptyMap())
            assertEquals(listOf(when(status) {TaskStatus.PENDING->pending.id;TaskStatus.VERIFY->verify.id;TaskStatus.COMPLETED->done.id}),result.map {it.id})
        }
        assertEquals(before,db.backupDao().nodes())
        val attachments=AttachmentRepository(db,AttachmentStore(context,BackupFixture::syncDirectory))
        assertEquals(listOf(done.id),CompletedCleanup(db,nodes,attachments).preview(p.id).candidates.map {it.node.id})
        assertTrue(runCatching {nodes.convertPurpose(verify.id,NodePurpose.NOTE)}.isFailure)
        assertTrue(runCatching {nodes.setSprintMode(layer.id,true)}.isFailure)
        assertTrue(runCatching {nodes.relocateNode(verify.id,null)}.isFailure)
        assertTrue(runCatching {db.openHelper.writableDatabase.execSQL("UPDATE nodes SET isCompleted=1 WHERE id=?",arrayOf(verify.id))}.isFailure)
        assertEquals(before,db.backupDao().nodes())
        assertFalse(db.nodeDao().getById(layer.id)!!.sprintMode)
    }
    @Test fun restartEncryptedBackupRestoreAndInvalidCandidatePreservePreviousData()=runBlocking<Unit> {
        val p=projects.createProject("Conservar");projects.setVerificationEnabled(p.id,true)
        val task=nodes.createNode(p.id,null,"Ejecutada");nodes.setTaskStatus(task.id,TaskStatus.VERIFY)
        val prefs=AppearancePreferences(context.getSharedPreferences(AppearancePreferences.FILE,0))
        assertEquals(StatusSelectorDirection.VERTICAL,prefs.settings.statusSelectorDirection)
        prefs.setStatusSelectorDirection(StatusSelectorDirection.HORIZONTAL)
        // Backup uses the same private preference namespace as the application store.
        db.privatePreferenceDao().save(PrivatePreferenceEntity(AppearancePreferences.FILE,EncryptedPreferences.encode(context.getSharedPreferences(AppearancePreferences.FILE,0).all)))
        val repo=backup();val snapshot=repo.snapshot()
        assertEquals(43,JSONObject(String(BackupJson.encode(snapshot))).getInt("dataVersion"))
        assertEquals(snapshot.projects,BackupJson.decode(BackupJson.encode(snapshot)).projects)
        val archive=repo.create("clave".toCharArray());val inspected=repo.inspect(archive.inputStream(),"clave".toCharArray())
        nodes.deleteNode(task.id);repo.restore(inspected);repo.discard(inspected)
        assertEquals(snapshot.nodes,db.backupDao().nodes());assertEquals(snapshot.privatePreferences,db.privatePreferenceDao().all())
        assertTrue(runCatching {repo.restore(snapshot.copy(nodes=snapshot.nodes.map {if(it.awaitingVerification) it.copy(isCompleted=true) else it}))}.isFailure)
        assertEquals(snapshot.nodes,db.backupDao().nodes());assertTrue(projects.getProject(p.id)!!.verificationEnabled)
        db.close();open()
        assertEquals(TaskStatus.VERIFY,nodes.getNode(task.id)!!.taskStatus)
        assertEquals(StatusSelectorDirection.HORIZONTAL,AppearancePreferences(context.getSharedPreferences(AppearancePreferences.FILE,0)).settings.statusSelectorDirection)
    }
    @Test fun promotionAndRelocationRetainVerificationAndCoreInheritance()=runBlocking<Unit> {
        val p=projects.createProject("Origen");val target=projects.createProject("Destino")
        projects.setVerificationEnabled(p.id,true)
        val layer=nodes.createNode(p.id,null,"Promover",purpose=NodePurpose.LAYER)
        val task=nodes.createNode(p.id,layer.id,"Conservar estado")
        nodes.setTaskStatus(task.id,TaskStatus.VERIFY)
        assertTrue(runCatching {nodes.relocateNode(task.id,target.id)}.isFailure)
        val promoted=projects.convertLayerToProject(layer.id)
        assertTrue(projects.getProject(promoted)!!.verificationEnabled)
        assertEquals(TaskStatus.VERIFY,nodes.getNode(task.id)!!.taskStatus)
        assertNull(nodes.getNode(task.id)!!.parentId)
        assertTrue(runCatching {projects.moveInside(promoted,target.id,null)}.isFailure)
        projects.setVerificationEnabled(target.id,true)
        assertTrue(nodes.relocateNode(task.id,target.id))
        assertEquals(TaskStatus.VERIFY,nodes.getNode(task.id)!!.taskStatus)
        assertEquals(target.id,nodes.getNode(task.id)!!.projectId)
    }
    @Test fun historicalBackupDefaultsAndSprintPhasesUnchanged()=runBlocking<Unit> {
        val p=projects.createProject("Histórico")
        val layer=nodes.createNode(p.id,null,"Sprint",purpose=NodePurpose.LAYER)
        nodes.setSprintMode(layer.id,true)
        val task=nodes.createNode(p.id,layer.id,"Sprint tarea")
        assertEquals(listOf("No planificada","Planificada","Haciendo","Terminada","Validada"),WorkState.entries.map {it.label})
        val repo=backup();val historical=BackupJson.decode(BackupJson.encode(repo.snapshot()))
        assertTrue(historical.projects.none {it.verificationEnabled});assertTrue(historical.nodes.none {it.awaitingVerification})
        WorkState.entries.forEach {phase->assertTrue(nodes.setWorkState(task.id,phase));assertEquals(phase,nodes.getNode(task.id)!!.workState)}
        assertTrue(runCatching {nodes.setTaskStatus(task.id,TaskStatus.VERIFY)}.isFailure)
        val invalid=EncryptedPreferences.encode(mapOf(AppearancePreferences.DIRECTION to "diagonal"))
        assertTrue(runCatching {AppearancePreferences.validate(invalid)}.isFailure)
    }
}
