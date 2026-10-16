package com.r0ybt.arachn0de.cleanup

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.sharing.SharingTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class CompletedCleanupUiTest {
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
        val manager=app.security
        val closing=CoroutineScope(Dispatchers.Default).async {manager.lock()}
        await {closing.isCompleted};runBlocking {closing.await()}
    }
    @Test fun manualCancelDoesNotDeleteAndConfirmReportsActualCount() {
        val project=runBlocking {app.projectRepository.createProject("Cleanup")}
        val node=runBlocking {app.nodeRepository.createNode(project.id,null,"Done").also {app.nodeRepository.setCompleted(it.id,true)}}
        compose.setContent {Arachn0deTheme {val open=completedCleanupLauncher(project.id);TextButton(open) {Text("Abrir limpieza")}}}
        compose.onNodeWithText("Abrir limpieza").performClick()
        await {compose.onAllNodesWithText("1 tareas elegibles",substring=true).fetchSemanticsNodes().isNotEmpty()}
        assertNotNull(runBlocking {app.database.nodeDao().getById(node.id)})
        compose.onNodeWithText("Cancelar").performClick();assertNotNull(runBlocking {app.database.nodeDao().getById(node.id)})
        compose.onNodeWithText("Abrir limpieza").performClick()
        await {runCatching {compose.onNodeWithText("Limpiar",substring=false).assertIsEnabled()}.isSuccess}
        compose.onNodeWithText("Limpiar",substring=false).performClick()
        await {compose.onAllNodesWithText("Tareas eliminadas: 1.").fetchSemanticsNodes().isNotEmpty()}
        assertNull(runBlocking {app.database.nodeDao().getById(node.id)})
    }
    @Test fun automaticActivationAndReductionRequireConfirmationAndNeverDisables() {
        val repo=app.security.current.value!!.cleanup
        compose.setContent {Arachn0deTheme {Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {CleanupSettings(repo)}}}
        await {runCatching {compose.onNodeWithText("1 año (365 días)").assertIsEnabled()}.isSuccess}
        compose.onNodeWithText("1 año (365 días)").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick();assertEquals(0,runBlocking {repo.days()})
        compose.onNodeWithText("1 año (365 días)").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar").performClick();await {runBlocking {repo.days()==365}}
        val scheduler=app.getSystemService(android.app.job.JobScheduler::class.java)
        await {scheduler.getPendingJob(CleanupSchedule.ID)!=null}
        compose.onNodeWithText("1 día").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick();assertEquals(365,runBlocking {repo.days()})
        compose.onNodeWithText("Nunca").performScrollTo().performClick();await {runBlocking {repo.days()==0}}
        await {scheduler.getPendingJob(CleanupSchedule.ID)==null}
    }
}
