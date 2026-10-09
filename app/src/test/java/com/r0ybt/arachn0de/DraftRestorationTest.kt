package com.r0ybt.arachn0de

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk = [28])
class DraftRestorationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: Arachn0deApplication
    private lateinit var projectId: String
    private lateinit var parentId: String
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            app = ApplicationProvider.getApplicationContext()
            runBlocking {
                projectId = app.projectRepository.createProject("Proyecto borradores").id
                parentId = app.nodeRepository.createNode(projectId, null, "Padre", purpose = com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER).id
            }
        }
        override fun after() { app.database.close() }
    }).around(compose)

    private fun awaitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(if (text == "Guardar") hasText("Guardar") or hasText("Crear") else hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun recreate() {
        compose.activityRule.scenario.recreate()
        awaitText("Guardar")
    }
    private fun save() {
        compose.onNode(hasText("Guardar") or hasText("Crear")).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Guardar") or hasText("Crear")).fetchSemanticsNodes().isEmpty() }
    }
    private fun openProject() {
        awaitText("Proyecto borradores")
        compose.onNodeWithText("Proyecto borradores").performClick()
        awaitText("Padre")
    }

    @Test fun newProjectDraftSurvivesRecreationAndCancelClearsIt() {
        awaitText("Nuevo proyecto")
        compose.onNodeWithText("Nuevo proyecto").performClick()
        compose.onNodeWithText("Nombre").performTextInput("Sin guardar")
        compose.onNodeWithText("Descripción").performTextInput("Texto pendiente")
        recreate()
        compose.onNodeWithText("Sin guardar").assertExists()
        compose.onNodeWithText("Texto pendiente").assertExists()
        runBlocking { check(app.database.projectDao().observeAll().first().size == 1) }
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Nuevo proyecto").performClick()
        compose.onNodeWithText("Sin guardar").assertDoesNotExist()
    }

    @Test fun editProjectRestoresDraftAndUpdatesSameIdentity() {
        awaitText("Proyecto borradores")
        compose.onNodeWithContentDescription("Opciones del proyecto").performClick()
        compose.onNodeWithContentDescription("Editar proyecto").performClick()
        compose.onNodeWithText("Nombre").performTextReplacement("Editado pendiente")
        recreate()
        compose.onNodeWithText("Editado pendiente").assertExists()
        save()
        runBlocking { check(app.projectRepository.getProject(projectId)?.name == "Editado pendiente") }
    }

    @Test fun childDraftRestoresOriginalParentAndSavesOnce() {
        openProject()
        compose.onNodeWithText("Padre").performClick()
        awaitText("CAPA 1")
        compose.onNodeWithText("Nuevo elemento").performClick()
        awaitText("Guardar")
        compose.onNodeWithText("Título").performTextInput("Hijo pendiente")
        compose.onNodeWithText("Descripción").performTextInput("Detalle")
        recreate()
        compose.onNodeWithText("Hijo pendiente").assertExists()
        compose.onNodeWithText("Detalle").assertExists()
        save()
        runBlocking {
            val child = app.nodeRepository.getProjectNodes(projectId).single { it.title == "Hijo pendiente" }
            check(child.parentId == parentId)
        }
    }

    @Test fun missingParentDoesNotRedirectDraftToRoot() {
        openProject()
        compose.onNodeWithText("Padre").performClick()
        awaitText("CAPA 1")
        compose.onNodeWithText("Nuevo elemento").performClick()
        awaitText("Guardar")
        compose.onNodeWithText("Título").performTextInput("No redirigir")
        runBlocking { app.nodeRepository.deleteNode(parentId) }
        recreate()
        compose.onNodeWithText("No redirigir").assertExists()
        compose.onNode(hasText("Guardar") or hasText("Crear")).performClick()
        awaitText("No se pudo completar la operación")
        compose.onNodeWithText("Entendido").performClick()
        compose.onNodeWithText("No redirigir").assertExists()
        runBlocking { check(app.nodeRepository.getProjectNodes(projectId).isEmpty()) }
    }

    @Test fun deleteConfirmationRestoresWithoutExecutingIt() {
        awaitText("Proyecto borradores")
        compose.onNodeWithContentDescription("Opciones del proyecto").performClick()
        compose.onNodeWithContentDescription("Eliminar proyecto").performClick()
        compose.activityRule.scenario.recreate()
        awaitText("Eliminar")
        runBlocking { check(app.projectRepository.getProject(projectId) != null) }
        compose.onNodeWithText("Cancelar").performClick()
    }
    @Test fun editedNodeRestoresIdentityAndUnsavedContent() {
        openProject()
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Editar").performClick()
        compose.onNodeWithText("Título").performTextReplacement("Padre editado")
        recreate()
        compose.onNodeWithText("Padre editado").assertExists()
        save()
        runBlocking {
            val nodes = app.nodeRepository.getProjectNodes(projectId)
            check(nodes.size == 1 && nodes.single().id == parentId && nodes.single().title == "Padre editado")
        }
    }

    @Test fun openMapSurvivesRecreation() {
        openProject()
        compose.onNodeWithContentDescription("Capas de cebolla").performClick()
        compose.activityRule.scenario.recreate()
        awaitText("Cerrar")
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("Padre").assertExists()
    }

    @Test fun backClosesRestoredEditorWithoutLeavingProjectOrSaving() {
        openProject()
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNodeWithText("Editar").performClick()
        compose.onNodeWithText("Título").performTextReplacement("Sin confirmar")
        recreate()
        compose.runOnUiThread {
            @Suppress("DEPRECATION")
            org.robolectric.shadows.ShadowDialog.getLatestDialog().onBackPressed()
        }
        compose.onNode(hasText("Guardar") or hasText("Crear")).assertDoesNotExist()
        compose.onNodeWithText("Padre").assertExists()
        runBlocking { check(app.nodeRepository.getNode(parentId)?.title == "Padre") }
    }

}
