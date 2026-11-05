package com.r0ybt.arachn0de.status

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.ManagedKanbanDialog
import com.r0ybt.arachn0de.data.repository.ManagementMethodology
import com.r0ybt.arachn0de.ui.ProjectNodeScreen
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=com.r0ybt.arachn0de.sharing.SharingTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class CoreStatusUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {
        shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame();block()
    }
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()}
        val process=Arachn0deApplication::class.java.getDeclaredField("processScope").apply {isAccessible=true}.get(app) as CoroutineScope
        runBlocking {process.coroutineContext[Job]?.cancelAndJoin()}
        val database=app.database
        runBlocking {
            app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin()
            withTimeout(15000) {database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}
        }
        database.close()
    }
    @Test fun normalTaskDetailKeepsCheckboxWithoutRedundantStateLine() {
        val core=runBlocking {app.projectRepository.createProject("Detalle")}
        val task=runBlocking {app.nodeRepository.createNode(core.id,null,"Tarea normal")}
        compose.setContent {Arachn0deTheme {ProjectNodeScreen(core,app.nodeRepository,{},openNodeId=task.id)}}
        await {compose.onAllNodesWithTag("detail-completion:${task.id}").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Estado: Pendiente").assertDoesNotExist()
        compose.onNodeWithTag("detail-completion:${task.id}").assertHasClickAction().performClick()
        await {runBlocking {app.nodeRepository.getNode(task.id)!!.isCompleted}}
        compose.onNodeWithText("Estado: Completada").assertDoesNotExist()
        compose.onNodeWithTag("detail-completion:${task.id}").assertHasClickAction().performClick()
        await {runBlocking {!app.nodeRepository.getNode(task.id)!!.isCompleted}}
        compose.onNodeWithText("Estado: Pendiente").assertDoesNotExist()
    }

    @Test fun coreCapsuleFiltersVisibleTasksWithoutWritingTheirActualStatus() {
        val core=runBlocking {app.projectRepository.createProject("Semáforo").also {app.projectRepository.setVerificationEnabled(it.id,true)}}
        runBlocking {
            val pending=app.nodeRepository.createNode(core.id,null,"Pendiente única")
            val verify=app.nodeRepository.createNode(core.id,null,"Por revisar única")
            val complete=app.nodeRepository.createNode(core.id,null,"Completada única")
            app.nodeRepository.setTaskStatus(verify.id,TaskStatus.VERIFY)
            app.nodeRepository.setCompleted(complete.id,true)
        }
        val before=runBlocking {app.database.backupDao().nodes()}
        val project=runBlocking {app.projectRepository.getProject(core.id)!!}
        val access=com.r0ybt.arachn0de.access.AccessRepository(app.database)
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(com.r0ybt.arachn0de.ui.LocalElementAccess provides access) {
                ProjectNodeScreen(project,app.nodeRepository,{})
            }
        }}
        await {compose.onAllNodesWithContentDescription("Filtros").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("status-filter:VERIFY").assertDoesNotExist()
        fun filter(status:TaskStatus?) {
            await {compose.onAllNodes(hasTestTag("core-context:View") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("core-context:View").performClick()
            compose.onNodeWithTag("task-view:${status?.name ?: "ALL"}").performClick()
            await {compose.onAllNodesWithTag("task-view-selector").fetchSemanticsNodes().isEmpty()}
            await {compose.onAllNodes(hasTestTag("core-context:View") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        }
        val titles=mapOf(TaskStatus.PENDING to "Pendiente única",TaskStatus.VERIFY to "Por revisar única",TaskStatus.COMPLETED to "Completada única")
        titles.forEach {(status,title)->
            filter(status)
            val id=before.single {it.title==title}.id
            compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(title))
            await {compose.onAllNodesWithTag("node-title:$id",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("node-options:$id",useUnmergedTree=true).assertHasClickAction()
            assertEquals(before,runBlocking {app.database.backupDao().nodes()})
        }
        filter(null)
        assertEquals(before,runBlocking {app.database.backupDao().nodes()})
    }
    @Test fun cardMenuConfirmsConversionCanCancelAndPreservesInheritance() {
        val core=runBlocking {app.projectRepository.createProject("Opciones")}
        val task=runBlocking {app.nodeRepository.createNode(core.id,null,"Tarea configurable")}
        compose.setContent {Arachn0deTheme {ProjectNodeScreen(core,app.nodeRepository,{})}}
        await {compose.onAllNodesWithTag("nodes-list").fetchSemanticsNodes().isNotEmpty()}
        fun locate() {compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(task.title))}
        fun mode(label:String) {
            locate();compose.onNodeWithTag("node-options:${task.id}",useUnmergedTree=true).performClick()
            compose.onNodeWithText("Estados de tareas…").performScrollTo().performClick()
            compose.onNodeWithText(label).performClick()
            await {compose.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()}
        }
        locate();compose.onNodeWithTag("node-options:${task.id}",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Estados de tareas…").performScrollTo().performClick()
        compose.onNodeWithText("Heredar del contenedor").assertIsSelected()
        compose.onNodeWithText("3 estados").performClick()
        await {runBlocking {app.nodeRepository.getNode(task.id)!!.taskStateCount==3}}
        await {compose.onAllNodesWithText("3 estados").fetchSemanticsNodes().isEmpty()}
        locate();compose.onNodeWithTag("node-options:${task.id}",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Cambiar estado").performScrollTo().performClick()
        compose.onNodeWithText("Pendiente").assertIsSelected()
        await {compose.onAllNodes(hasText("Por verificar") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Por verificar").performClick()
        await {runBlocking {app.nodeRepository.getNode(task.id)!!.awaitingVerification}}
        await {compose.onAllNodesWithText("Por verificar").fetchSemanticsNodes().isEmpty()}
        locate();compose.onNodeWithTag("node-options:${task.id}",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Estados de tareas…").performScrollTo().performClick()
        compose.onNodeWithText("3 estados").assertIsSelected()
        compose.onNodeWithText("2 estados").performClick()
        await {compose.onAllNodesWithText("¿Cambiar a 2 estados?").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("1 tarea pasará",substring=true).assertIsDisplayed()
        compose.onNodeWithTag("task-state-conversion-cancel").performClick()
        assertEquals(3,runBlocking {app.nodeRepository.getNode(task.id)!!.taskStateCount})
        assertTrue(runBlocking {app.nodeRepository.getNode(task.id)!!.awaitingVerification})
        compose.onNodeWithText("2 estados").performClick()
        await {compose.onAllNodesWithText("¿Cambiar a 2 estados?").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("task-state-conversion-confirm").performClick()
        await {runBlocking {app.nodeRepository.getNode(task.id)!!.let {it.taskStateCount==2 && it.taskStatus==TaskStatus.PENDING}}}
        await {compose.onAllNodesWithText("2 estados").fetchSemanticsNodes().isEmpty()}
        mode("3 estados")
        assertEquals(TaskStatus.PENDING,runBlocking {app.nodeRepository.getNode(task.id)!!.taskStatus})
        mode("Heredar del contenedor")
        assertNull(runBlocking {app.nodeRepository.getNode(task.id)!!.taskStateCount})
        compose.onNodeWithTag("status-filter:VERIFY").assertDoesNotExist()
    }
    @Test fun managedCapsuleFiltersTheFiveHistoricalPhasesWithoutAnyWrites() {
        val data=runBlocking {
            val core=app.projectRepository.createProject("Gestión")
            val context=app.nodeRepository.management.configure(core.id,null,true,ManagementMethodology.AGILE)!!.id
            val sprint=app.nodeRepository.createNode(core.id,null,"Sprint",purpose=NodePurpose.LAYER)
            app.nodeRepository.setSprintMode(sprint.id,true)
            context to WorkState.entries.associateWith {phase->
                app.nodeRepository.createNode(core.id,sprint.id,"Tarea ${phase.name}").also {app.nodeRepository.setWorkState(it.id,phase)}.id
            }
        }
        val before=runBlocking {app.database.backupDao().nodes()}
        compose.setContent {Arachn0deTheme {ManagedKanbanDialog(data.first,app.nodeRepository.management.kanban,{}, {},inline=true) {}}}
        await {compose.onAllNodesWithTag("status-filter:UNPLANNED").fetchSemanticsNodes().isNotEmpty()}
        data.second.forEach {(phase,id)->
            compose.onNodeWithTag("status-filter:${phase.name}").performClick()
            compose.onNodeWithTag("kanban-list").performScrollToNode(hasTestTag("kanban-task:$id"))
            compose.onNodeWithTag("kanban-task:$id").assertIsDisplayed()
            data.second.filterKeys {it!=phase}.values.forEach {other->compose.onNodeWithTag("kanban-task:$other").assertDoesNotExist()}
            assertEquals(before,runBlocking {app.database.backupDao().nodes()})
        }
    }

}
