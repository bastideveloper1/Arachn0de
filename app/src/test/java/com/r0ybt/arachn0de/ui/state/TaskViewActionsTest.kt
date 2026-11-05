package com.r0ybt.arachn0de.ui.state

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class TaskViewActionsTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    private lateinit var projects:ProjectRepository
    private lateinit var scope:CoroutineScope
    @Before fun setup() {context.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(context);nodes=NodeRepository(db);projects=ProjectRepository(db.projectDao(),db);scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)}
    @After fun close() {scope.cancel();shadowOf(Looper.getMainLooper()).idle();db.close()}
    private fun await(block:()->Boolean)=runBlocking {withTimeout(10000) {while(!block()) {shadowOf(Looper.getMainLooper()).idle();delay(10)}}}
    @Test fun oneHostReplacesThePreviousUndoAndOnlyRestoresTheLastTask() {
        val p=runBlocking {projects.createProject("Núcleo").also {projects.setVerificationEnabled(it.id,true)}}
        val tasks=runBlocking {(1..2).map {nodes.createNode(p.id,null,"Tarea $it")}}
        val actions=NodeActions(nodes,scope);var restored=0
        actions.taskViewFeedback=TaskViewFeedback({node,_,_->node.taskStatus==TaskStatus.PENDING},{},{ {_->restored++} })
        actions.cycleTaskStatus(tasks[0].id);await {!actions.operation.busy && actions.snackbar.currentSnackbarData!=null}
        val first=actions.snackbar.currentSnackbarData
        actions.cycleTaskStatus(tasks[1].id);await {!actions.operation.busy && actions.snackbar.currentSnackbarData!=null && actions.snackbar.currentSnackbarData!==first}
        actions.snackbar.currentSnackbarData!!.performAction();await {restored==1 && !actions.operation.busy}
        assertEquals(TaskStatus.VERIFY,runBlocking {nodes.getNode(tasks[0].id)!!.taskStatus})
        assertEquals(TaskStatus.PENDING,runBlocking {nodes.getNode(tasks[1].id)!!.taskStatus})
        tasks.forEach {assertEquals(it.position,runBlocking {nodes.getNode(it.id)!!.position})}
        val backup=runBlocking {BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory)).snapshot()}
        assertEquals(backup.nodes,BackupJson.decode(BackupJson.encode(backup)).nodes)
        assertTrue(runBlocking {db.nodeEventDao().all().any {it.nodeId==tasks[1].id && it.type=="REOPENED"}})
    }
    @Test fun staleUndoAndIncompatibleConfigurationNeverOverwriteLaterChanges()=runBlocking {
        val p=projects.createProject("Núcleo");projects.setVerificationEnabled(p.id,true)
        val task=nodes.createNode(p.id,null,"Tarea")
        nodes.setTaskStatus(task.id,TaskStatus.VERIFY);val before=nodes.getNode(task.id)!!
        nodes.setTaskStatus(task.id,TaskStatus.COMPLETED);val after=nodes.getNode(task.id)!!
        projects.setVerificationEnabled(p.id,false)
        val rows=db.backupDao().nodes();val events=db.nodeEventDao().all()
        assertTrue(runCatching {nodes.restoreTaskStatus(after,before.taskStatus)}.isFailure)
        assertEquals(rows,db.backupDao().nodes());assertEquals(events,db.nodeEventDao().all())
        db.nodeDao().updateContent(task.id,"Otro título","",after.updatedAt+1)
        assertFalse(nodes.restoreTaskStatus(after,TaskStatus.PENDING))
        assertEquals("Otro título",nodes.getNode(task.id)!!.title)
        assertTrue(nodes.getNode(task.id)!!.isCompleted)
    }
    @Test fun blockedReorderNoticeIsThrottledAndDoesNotTouchRows() {
        val actions=NodeActions(nodes,scope)
        actions.blockedReorder();await {actions.snackbar.currentSnackbarData!=null}
        val first=actions.snackbar.currentSnackbarData
        repeat(5) {actions.blockedReorder()};shadowOf(Looper.getMainLooper()).idle()
        assertSame(first,actions.snackbar.currentSnackbarData)
        assertEquals("Vuelve a Todas para reordenar",first!!.visuals.message)
        assertTrue(runBlocking {db.backupDao().nodes().isEmpty()})
    }
}
