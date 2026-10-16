package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class HierarchyPinUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        pump()
        runBlocking {app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin();withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}}
        app.database.close()
    }
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {pump();block()}
    private fun text(value:String) {await {compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()}}
    @Test fun contextualPinActionsShowIndicatorsOnlyForRootsAndLayers() {
        val ids=runBlocking {
            val p=app.projectRepository.createProject("Núcleo A")
            val layer=app.nodeRepository.createNode(p.id,null,"Capa B",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
            val task=app.nodeRepository.createNode(p.id,null,"Tarea C")
            listOf(p.id,layer.id,task.id)
        }
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        text("Núcleo A");compose.onNodeWithContentDescription("Opciones del proyecto").performClick();text("Fijar")
        compose.onNodeWithText("Fijar").performClick();await {runBlocking {app.database.projectDao().getById(ids[0])!!.isPinned}}
        await {compose.onAllNodesWithTag("project-pin:${ids[0]}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Opciones del proyecto").performClick();text("Desfijar")
        compose.onNodeWithText("Desfijar").performClick();await {runBlocking {!app.database.projectDao().getById(ids[0])!!.isPinned}}
        compose.onNodeWithText("Núcleo A").performClick();text("Capa B")
        compose.onNodeWithTag("node-options:${ids[1]}").performClick()
        text("Fijar");compose.onNodeWithText("Fijar").performClick();await {runBlocking {app.database.nodeDao().getById(ids[1])!!.isPinned}}
        await {compose.onAllNodesWithTag("node-pin:${ids[1]}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("node-options:${ids[1]}").performClick()
        text("Desfijar");compose.onNodeWithText("Desfijar").performClick();await {runBlocking {!app.database.nodeDao().getById(ids[1])!!.isPinned}}
        compose.onNodeWithTag("node-options:${ids[2]}").performClick()
        text("Editar");compose.onNodeWithText("Fijar").assertDoesNotExist();compose.onNodeWithText("Desfijar").assertDoesNotExist()
    }
    @Test fun detailMenusPinRootAndLayerWithoutTaskAction() {
        val ids=runBlocking {
            val p=app.projectRepository.createProject("Detalle raíz")
            val layer=app.nodeRepository.createNode(p.id,null,"Detalle capa",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
            listOf(p.id,layer.id)
        }
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        text("Detalle raíz");compose.onNodeWithText("Detalle raíz").performClick()
        text("Detalle capa");compose.onNodeWithContentDescription("Opciones del proyecto").performClick()
        text("Fijar");compose.onNodeWithText("Fijar").performClick()
        await {runBlocking {app.database.projectDao().getById(ids[0])!!.isPinned}}
        compose.onNodeWithContentDescription("Opciones del proyecto").performClick();text("Desfijar")
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Detalle capa").performClick()
        await {compose.onAllNodesWithContentDescription("Opciones del elemento").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Opciones del elemento").performClick();text("Fijar")
        compose.onNodeWithText("Fijar").performClick()
        await {runBlocking {app.database.nodeDao().getById(ids[1])!!.isPinned}}
        compose.onNodeWithContentDescription("Opciones del elemento").performClick();text("Desfijar")
    }

}
