package com.r0ybt.arachn0de.status

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.backup.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class TaskStateInheritanceTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    private lateinit var projects:ProjectRepository
    @Before fun setup() {context.deleteDatabase("arachn0de.db");open()}
    private fun open() {db=Arachn0deDatabase.create(context);nodes=NodeRepository(db);projects=ProjectRepository(db.projectDao(),db)}
    @After fun close() {db.close()}
    @Test fun inheritedOverridesCycleAndOnlyConfirmedThreeToTwoChangesVerification()=runBlocking<Unit> {
        val p=projects.createProject("Núcleo");val q=projects.createProject("Ajeno")
        var parent:String?=null
        repeat(80) {parent=nodes.createNode(p.id,parent,"Capa $it",purpose=NodePurpose.LAYER).id}
        val task=nodes.createNode(p.id,parent,"Tarea")
        fun status()=runBlocking {nodes.getNode(task.id)!!.taskStatus}
        assertEquals(2,nodes.taskStateCount(task.id));nodes.cycleTaskStatus(task.id);assertEquals(TaskStatus.COMPLETED,status())
        nodes.cycleTaskStatus(task.id);assertEquals(TaskStatus.PENDING,status())
        projects.setVerificationEnabled(p.id,true);assertEquals(3,nodes.taskStateCount(task.id))
        nodes.setTaskStateCount(parent!!,2);assertEquals(2,nodes.taskStateCount(task.id))
        nodes.setTaskStateCount(task.id,3);assertEquals(3,nodes.taskStateCount(task.id))
        nodes.cycleTaskStatus(task.id);assertEquals(TaskStatus.VERIFY,status())
        val before=nodes.getNode(task.id)!!
        projects.setVerificationEnabled(p.id,false)
        assertTrue(runCatching {nodes.setTaskStateCount(task.id,2)}.isFailure)
        val configuration=com.r0ybt.arachn0de.data.repository.TaskStateConfiguration(db)
        val preview=configuration.preview(com.r0ybt.arachn0de.data.repository.TaskStateConfiguration.Request(p.id,task.id,2))
        configuration.apply(preview.request,preview)
        assertEquals(TaskStatus.PENDING,status());assertFalse(nodes.getNode(task.id)!!.isCompleted)
        val tree=NodeTreeSnapshot(nodes.getProjectNodes(p.id));assertEquals(0,tree.projectProgressById[p.id]!!.completed)
        assertEquals(1,tree.projectProgressById[p.id]!!.total)
        nodes.cycleTaskStatus(task.id);assertEquals(TaskStatus.COMPLETED,status())
        nodes.cycleTaskStatus(task.id);assertEquals(TaskStatus.PENDING,status())
        nodes.setTaskStateCount(task.id,null);assertEquals(2,nodes.taskStateCount(task.id))
        nodes.setTaskStateCount(parent!!,3);assertEquals(3,nodes.taskStateCount(task.id))
        val currentTree=NodeTreeSnapshot(nodes.getProjectNodes(p.id))
        assertEquals(3,TaskStatePolicy.count(nodes.getNode(task.id)!!,currentTree.nodesById,projects.getProject(p.id)))
        val after=nodes.getNode(task.id)!!
        assertEquals(before.parentId,after.parentId);assertEquals(before.description,after.description)
        assertFalse(projects.getProject(q.id)!!.verificationEnabled)
        assertTrue(runCatching {nodes.setTaskStateCount(task.id,4)}.isFailure)
        assertTrue(runCatching {db.openHelper.writableDatabase.execSQL("UPDATE nodes SET taskStateCount=4 WHERE id=?",arrayOf(task.id))}.isFailure)
    }
    @Test fun encryptedBackupReopenAndHistoricalDefaultsPreserveOverridesAndVerification()=runBlocking<Unit> {
        val p=projects.createProject("Backup")
        val layer=nodes.createNode(p.id,null,"Capa",purpose=NodePurpose.LAYER)
        val task=nodes.createNode(p.id,layer.id,"Conservar")
        nodes.setTaskStateCount(layer.id,3);nodes.cycleTaskStatus(task.id)
        // Historical backups may retain VERIFY in mode 2; loading never converts them.
        db.nodeDao().setTaskStateCount(task.id,2,123)
        val repo=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val snapshot=repo.snapshot();val encoded=BackupJson.encode(snapshot)
        assertEquals(44,JSONObject(String(encoded)).getInt("dataVersion"))
        assertEquals(snapshot.nodes,BackupJson.decode(encoded).nodes)
        val archive=repo.create("fixture".toCharArray());val inspection=repo.inspect(archive.inputStream(),"fixture".toCharArray())
        nodes.deleteNode(task.id);repo.restore(inspection);repo.discard(inspection)
        assertEquals(snapshot.nodes,db.backupDao().nodes())
        val invalid=JSONObject(String(encoded));val rows=invalid.getJSONArray("nodes")
        rows.getJSONObject(0).put("taskStateCount",4)
        assertTrue(runCatching {BackupJson.decode(invalid.toString().toByteArray())}.isFailure)
        val before=db.backupDao().nodes()
        assertTrue(runCatching {repo.restore(snapshot.copy(nodes=snapshot.nodes.map {it.copy(taskStateCount=4)}))}.isFailure)
        assertEquals(before,db.backupDao().nodes())
        db.close();open()
        assertEquals(TaskStatus.VERIFY,nodes.getNode(task.id)!!.taskStatus);assertEquals(2,nodes.taskStateCount(task.id))
        // A valid old JSON 43 has no overrides and retains its original verification setting.
        projects.setVerificationEnabled(p.id,true);nodes.setTaskStateCount(layer.id,null);nodes.setTaskStateCount(task.id,null)
        val old=repoAfterReopen().snapshot();val oldBytes=BackupJson.encode(old)
        assertEquals(43,JSONObject(String(oldBytes)).getInt("dataVersion"))
        assertTrue(BackupJson.decode(oldBytes).nodes.all {it.taskStateCount==null})
        assertEquals(TaskStatus.VERIFY,nodes.getNode(task.id)!!.taskStatus)
    }
    private fun repoAfterReopen()=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
}
