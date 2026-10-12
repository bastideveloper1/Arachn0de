package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.withTransaction
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.repository.ManagementMethodology
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class CharterUxTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun dispose() {
        compose.runOnUiThread {
            val content=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until content.childCount) (content.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
        }
        compose.waitForIdle();runBlocking {app.database.withTransaction {}}
    }
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(text:String)=compose.waitUntil(20000) {pump();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun click(text:String) {await(text);val node=compose.onNodeWithText(text);if(runCatching {node.assertIsDisplayed()}.isFailure) node.performScrollTo();node.performClick()}
    private fun layer(text:String) {compose.waitUntil(20000) {pump();runCatching {compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(text))}.isSuccess};click(text)}
    @Test fun createDocumentSectionRemainInCharterAndReadObjectiveHelp() {
        val fixture=runBlocking {
            val project=app.projectRepository.createProject("Acta UX")
            val context=app.nodeRepository.management.configure(project.id,null,true,ManagementMethodology.AGILE)!!.id
            project.id to context
        }
        compose.setContent {Arachn0deTheme {AppSafeArea {AppRoot(app.projectRepository,app.nodeRepository)}}}
        click("Acta UX");layer("Acta de constitución")
        compose.onAllNodesWithText("Propósito y justificación").assertCountEquals(0)
        click("Añadir sección")
        compose.onNodeWithText("Título de la sección").performTextReplacement("Sección personalizada")
        compose.onNodeWithText("Contenido").performTextReplacement("Contenido del documento")
        click("Guardar sección")
        compose.waitUntil(20000) {pump();runBlocking {app.nodeRepository.getProjectNodes(fixture.first).any {it.title=="Sección personalizada"}}}
        await("Añadir sección");layer("Sección personalizada")
        await("Contenido del documento")
        compose.onAllNodesWithText("Nota · Convierte en capa para añadir hijos").assertCountEquals(0)
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        layer("Objetivos");click("?")
        await("Cómo redactar objetivos")
        compose.onNodeWithText("Objetivo general").assertIsDisplayed()
        compose.onNodeWithText("Objetivos específicos").assertExists()
        click("Volver")
        await("Nuevo objetivo");compose.onNodeWithText("Nuevo elemento").assertIsDisplayed()
        runBlocking {
            val saved=app.nodeRepository.getProjectNodes(fixture.first).single {it.title=="Sección personalizada"}
            assertEquals("Contenido del documento",saved.description)
        }
    }
    @Test fun traditionalCreationInObjectivesDoesNotCreateSemanticObjective() {
        val fixture=runBlocking {
            val project=app.projectRepository.createProject("Traditional UX")
            val context=app.nodeRepository.management.configure(project.id,null,true,ManagementMethodology.AGILE)!!.id
            project.id to context
        }
        compose.setContent {Arachn0deTheme {AppSafeArea {AppRoot(app.projectRepository,app.nodeRepository)}}}
        click("Traditional UX");layer("Acta de constitución");layer("Objetivos")
        click("Nuevo elemento");await("Título")
        compose.onNodeWithText("Título").performTextReplacement("Elemento tradicional")
        click("Crear")
        compose.waitUntil(20000) {pump();runBlocking {app.nodeRepository.getProjectNodes(fixture.first).any {it.title=="Elemento tradicional"}}}
        await("Nuevo objetivo");compose.onNodeWithText("Nuevo elemento").assertIsDisplayed()
        runBlocking {
            val saved=app.nodeRepository.getProjectNodes(fixture.first).single {it.title=="Elemento tradicional"}
            assertTrue(app.database.planningDao().objectives().none {it.contextId==fixture.second})
            assertTrue(app.database.backlogDao().roles().none {it.nodeId==saved.id})
        }
    }

}
