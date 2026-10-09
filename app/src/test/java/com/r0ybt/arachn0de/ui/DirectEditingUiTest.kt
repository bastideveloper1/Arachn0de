package com.r0ybt.arachn0de.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.MainActivity
import com.r0ybt.arachn0de.domain.model.NodePurpose
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk = [28])
class DirectEditingUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: Arachn0deApplication
    private lateinit var projectId: String
    private lateinit var nodeId: String
    private val description = "Texto **destacado** y __subrayado__"
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            app = ApplicationProvider.getApplicationContext()
            runBlocking {
                projectId = app.projectRepository.createProject("Proyecto directo").id
                nodeId = app.nodeRepository.createNode(projectId, null, "Elemento directo", description).id
            }
        }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun await(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun openDetail() {
        await("Proyecto directo"); compose.onNodeWithText("Proyecto directo").performClick()
        await("Elemento directo"); compose.onNodeWithText("Elemento directo").performScrollTo().performClick()
        compose.onNodeWithTag("detail-scope-title").assertExists()
    }
    private fun stored() = runBlocking { app.nodeRepository.getProjectNodes(projectId).single() }
    private fun cancelChanges() {
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("¿Descartar cambios?").assertExists()
        compose.onAllNodesWithText("Descartar cambios").onLast().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("node-editor").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun titleTapConfirmsAndDescriptionTapCancelsWithoutChangingFormat() {
        openDetail()
        compose.onNodeWithTag("detail-scope-title").performClick(); await("Título")
        compose.onNodeWithText("Título").assertIsFocused().performTextReplacement("Título actualizado")
        compose.onNodeWithText("Guardar").performClick(); await("Título actualizado")
        assertEquals("Título actualizado", stored().title); assertEquals(description, stored().description)
        compose.onNodeWithTag("detail-description").performScrollTo().performClick(); await("Descripción")
        compose.onNodeWithText("Descripción").assertIsFocused().performTextReplacement("Cambios temporales")
        cancelChanges()
        assertEquals(description, stored().description)
        compose.onNodeWithTag("detail-description").performScrollTo().performClick(); await("Descripción")
        compose.onNodeWithText("Descripción").assert(hasText(description))
    }

    @Test fun longTitleUsesExistingValidationAndLongDescriptionIsSavedInFull() {
        openDetail(); compose.onNodeWithTag("detail-scope-title").performClick(); await("Título")
        compose.onNodeWithText("Título").performTextReplacement("x".repeat(101))
        compose.onNodeWithText("Guardar").assertIsNotEnabled()
        compose.onNodeWithText("Título").performTextReplacement("x".repeat(100))
        val longDescription = ("Párrafo **destacado** y __subrayado__.\n").repeat(80).trim()
        compose.onNodeWithText("Descripción").performScrollTo().performTextReplacement(longDescription)
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("node-editor").fetchSemanticsNodes().isEmpty() }
        assertEquals("x".repeat(100), stored().title)
        assertEquals(longDescription, stored().description)
    }

    @Test fun scrollingOverDescriptionDoesNotOpenEditor() {
        openDetail()
        compose.onNodeWithTag("detail-description").performScrollTo().performTouchInput { swipeUp() }
        compose.onNodeWithTag("node-editor").assertDoesNotExist()
        assertEquals(description, stored().description)
    }

    @Test fun unchangedDirectEditorCancelsWithoutConfirmation() {
        openDetail(); compose.onNodeWithTag("detail-scope-title").performClick(); await("Título")
        compose.onNodeWithText("Cancelar").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("node-editor").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("¿Descartar cambios?").assertDoesNotExist()
        assertEquals("Elemento directo", stored().title)
    }

    @Test fun directSaveFailureRetainsDraftAcrossRecreationAndRetryPreservesFormat() {
        openDetail(); compose.onNodeWithTag("detail-description").performScrollTo().performClick(); await("Descripción")
        val changed = "$description y añadido"
        compose.onNodeWithText("Descripción").performTextReplacement(changed)
        app.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_direct BEFORE UPDATE ON nodes BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        compose.onNodeWithText("Guardar").performClick(); await("No se pudo completar la operación")
        compose.onNodeWithText("Entendido").performClick()
        assertEquals(description, stored().description)
        compose.activityRule.scenario.recreate(); await("Descripción")
        compose.onNodeWithText("Descripción").assert(hasText(changed))
        app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_direct")
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("node-editor").fetchSemanticsNodes().isEmpty() }
        assertEquals(changed, stored().description)
    }

    @Test fun directEditingAlsoWorksForNotesAndLayersWithEmptyDescription() {
        runBlocking {
            app.nodeRepository.convertPurpose(nodeId, NodePurpose.NOTE)
            app.nodeRepository.createNode(projectId, null, "Capa directa", purpose = NodePurpose.LAYER)
        }
        openDetail(); compose.onNodeWithTag("detail-scope-title").performClick(); await("Título")
        compose.onNodeWithText("Título").performTextReplacement("Nota editada")
        compose.onNodeWithText("Guardar").performClick(); await("Nota editada")
        compose.onNodeWithContentDescription("Volver a la capa anterior").performClick()
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Capa directa"))
        compose.onNodeWithText("Capa directa").performClick()
        compose.onNodeWithTag("detail-description").performScrollTo().performClick(); await("Descripción")
        compose.onNodeWithText("Descripción").performTextInput("Descripción de capa")
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { runBlocking { app.nodeRepository.getProjectNodes(projectId).any { it.description == "Descripción de capa" } } }
    }
}
