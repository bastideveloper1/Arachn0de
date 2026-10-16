package com.r0ybt.arachn0de.access

import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.ui.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class ElementAccessUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {
        compose.runOnUiThread {
            val content=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until content.childCount) (content.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            // A still-attached ComposeView can recreate its composition during measurement.
            content.removeAllViews()
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
        val application=app;val db=application.database
        // Let in-flight Room reads/transactions finish before closing the Robolectric pool.
        runBlocking {
            application.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin()
            withTimeout(15000) {db.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}
        }
        db.close()
    }
    private fun await(text:String) {compose.waitUntil(15000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    @Test fun restoringAnOpenProjectAndTaskDoesNotCountAgainButDeliberateReopeningDoes() {
        val project=runBlocking {app.projectRepository.createProject("Visitas")}
        val task=runBlocking {app.nodeRepository.createNode(project.id,null,"Destino visitado")}
        val repo=AccessRepository(app.database)
        val restoration=androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        await("Visitas")
        compose.onNodeWithTag("project-card:${project.id}").performClick()
        await("Destino visitado")
        compose.onNodeWithText("Destino visitado").performClick()
        compose.waitUntil(15000) {runBlocking {repo.snapshot().counts["node:${task.id}"]==1}}
        assertEquals(1,runBlocking {repo.snapshot().counts["project:${project.id}"]})
        restoration.emulateSavedInstanceStateRestore()
        await("Destino visitado")
        compose.waitForIdle()
        assertEquals(1,runBlocking {repo.snapshot().counts["node:${task.id}"]})
        assertEquals(1,runBlocking {repo.snapshot().counts["project:${project.id}"]})
        compose.onNodeWithContentDescription("Volver a la capa anterior").performClick()
        await("Destino visitado")
        compose.onNodeWithText("Destino visitado").performClick()
        compose.waitUntil(15000) {runBlocking {repo.snapshot().counts["node:${task.id}"]==2}}
    }
    @Test fun globalSearchOpensOriginalNestedTaskAndFavoriteRemainsAfterOpening() {
        val p=runBlocking {app.projectRepository.createProject("Proyecto raíz")}
        val layer=runBlocking {app.nodeRepository.createNode(p.id,null,"Área",purpose=NodePurpose.LAYER)}
        val task=runBlocking {app.nodeRepository.createNode(p.id,layer.id,"Revisión técnica","Texto")}
        val repo=AccessRepository(app.database);runBlocking {repo.toggle("node:${task.id}")}
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}}
        await("Proyecto raíz");compose.onNodeWithContentDescription("Abrir menú").performClick();compose.onNodeWithText("Buscar").performClick();compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Título o descripción").performTextInput("REVISION")
        await("Revisión técnica")
        compose.onNodeWithText("Tarea · Proyecto raíz › Área").assertExists()
        compose.onNodeWithTag("access-result:node:${task.id}").performClick()
        await("Revisión técnica")
        compose.onNodeWithText("Título o descripción").assertDoesNotExist()
        assertNotNull(runBlocking {app.database.nodeDao().getById(task.id)})
        assertEquals(setOf("node:${task.id}"),runBlocking {repo.snapshot().favorites})
    }
    @Test fun rapidCaptureCreatesOnlyOneTaskOnRepeatedSubmission() {
        val p=runBlocking {app.projectRepository.createProject("Destino")}
        val repo=AccessRepository(app.database)
        val index=ElementSearch.index(listOf(p),emptyList())
        var dismissed=false
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {CompositionLocalProvider(LocalElementAccessState provides AccessState()) {
            QuickCaptureDialog(index,repo) {dismissed=true}
        }}}}
        compose.mainClock.advanceTimeBy(300)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        compose.onNodeWithText("Crear tarea").assertIsNotEnabled()
        compose.onNodeWithText("Título").performTextInput("Captura");compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Crear tarea").performClick()
        compose.waitUntil(15000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();compose.mainClock.advanceTimeByFrame();dismissed}
        assertEquals(1,runBlocking {app.database.backupDao().nodes().size})
        assertEquals("Captura",runBlocking {app.database.backupDao().nodes().single().title})
        compose.onNodeWithText("Crear tarea").performClick()
        compose.waitForIdle()
        assertEquals(1,runBlocking {app.database.backupDao().nodes().size})
    }
    @Test fun cancellingQuickCaptureNeverCreatesRows() {
        val p=runBlocking {app.projectRepository.createProject("Destino")}
        val repo=AccessRepository(app.database)
        var dismissed=false
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {
            QuickCaptureDialog(ElementSearch.index(listOf(p),emptyList()),repo) {dismissed=true}
        }}}
        compose.onNodeWithText("Título").performTextInput("Sin guardar")
        compose.onNodeWithText("Cancelar").performClick()
        assertTrue(dismissed)
        assertTrue(runBlocking {app.database.backupDao().nodes().isEmpty()})
    }
    @Test fun largeFontResultsKeepTypePathAndOriginalIdentity() {
        val index=listOf(ElementResult("node:original","project","original","Título\ncon acentos","Descripción","Tarea","Proyecto › Capa",false))
        var opened:String?=null
        compose.runOnUiThread {compose.activity.setContent {
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.8f)) {
                Arachn0deTheme {ElementSearchScreen(index,"Buscar","",{}, {}, {}, {opened=it.nodeId},androidx.compose.foundation.lazy.rememberLazyListState())}
            }
        }}
        await("Título\ncon acentos")
        compose.onNodeWithText("Tarea · Proyecto › Capa").assertIsDisplayed()
        compose.onNodeWithTag("access-result:node:original").performClick()
        assertEquals("original",opened)
    }

}
