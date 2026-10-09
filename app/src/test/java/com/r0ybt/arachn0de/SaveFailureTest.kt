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
class SaveFailureTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: Arachn0deApplication
    private lateinit var projectId: String
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            app = ApplicationProvider.getApplicationContext()
            runBlocking { projectId = app.projectRepository.createProject("Proyecto de prueba").id }
        }
        override fun after() { app.database.close() }
    }).around(compose)

    private fun awaitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(if (text == "Guardar") hasText("Guardar") or hasText("Crear") else hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun failWrites(table: String, operation: String) {
        app.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_test BEFORE $operation ON $table BEGIN SELECT RAISE(ABORT, 'injected failure'); END"
        )
    }
    private fun allowWrites() { app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_test") }
    private fun dismissError() {
        awaitText("No se pudo completar la operación")
        compose.onNodeWithText("Entendido").performClick()
    }

    @Test fun failedProjectCreationKeepsDraftAndRetryCreatesOnce() {
        awaitText("Nuevo proyecto")
        compose.onNodeWithText("Nuevo proyecto").performClick()
        compose.onNodeWithText("Nombre").performTextInput("Borrador conservado")
        compose.onNodeWithText("Descripción").performTextInput("Descripción conservada")
        failWrites("projects", "INSERT")
        compose.onNode(hasText("Guardar") or hasText("Crear")).performClick()
        dismissError()
        compose.activityRule.scenario.recreate()
        awaitText("Guardar")
        compose.onNodeWithText("Borrador conservado").assertExists()
        compose.onNodeWithText("Descripción conservada").assertExists()
        allowWrites()
        compose.onNode(hasText("Guardar") or hasText("Crear")).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Guardar") or hasText("Crear")).fetchSemanticsNodes().isEmpty() }
        runBlocking {
            check(app.projectRepository.getProject(projectId) != null)
            check(app.database.projectDao().observeAll().first().count { it.name == "Borrador conservado" } == 1)
        }
    }

    @Test fun failedNodeCreationKeepsDraftAndRetrySucceeds() {
        awaitText("Proyecto de prueba")
        compose.onNodeWithText("Proyecto de prueba").performClick()
        awaitText("Nuevo elemento")
        compose.onNodeWithText("Nuevo elemento").performClick()
        // Defaults are loaded asynchronously before the editor is mounted.
        awaitText("Título")
        compose.onNodeWithText("Título").performScrollTo().performTextInput("Tarea conservada")
        failWrites("nodes", "INSERT")
        compose.onNode(hasText("Guardar") or hasText("Crear")).performClick()
        dismissError()
        compose.activityRule.scenario.recreate()
        awaitText("Guardar")
        compose.onNodeWithText("Tarea conservada").assertExists()
        allowWrites()
        compose.onNode(hasText("Guardar") or hasText("Crear")).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Guardar") or hasText("Crear")).fetchSemanticsNodes().isEmpty() }
        runBlocking { check(app.nodeRepository.getProjectNodes(projectId).single().title == "Tarea conservada") }
    }

    @Test fun failedProjectDeletionRetainsConfirmationAndData() {
        awaitText("Proyecto de prueba")
        compose.onNodeWithContentDescription("Opciones del proyecto").performClick()
        compose.onNodeWithContentDescription("Eliminar proyecto").performClick()
        failWrites("projects", "DELETE")
        compose.onNodeWithText("Eliminar").performClick()
        dismissError()
        compose.onNodeWithText("Eliminar").assertExists()
        runBlocking { check(app.projectRepository.getProject(projectId) != null) }
        allowWrites()
        compose.onNodeWithText("Eliminar").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Eliminar").fetchSemanticsNodes().isEmpty() }
        runBlocking { check(app.projectRepository.getProject(projectId) == null) }
    }

    @Test fun failedProjectEditKeepsChangedName() {
        awaitText("Proyecto de prueba")
        compose.onNodeWithContentDescription("Opciones del proyecto").performClick()
        compose.onNodeWithContentDescription("Editar proyecto").performClick()
        compose.onNodeWithText("Nombre").performTextReplacement("Nombre editado")
        failWrites("projects", "UPDATE")
        compose.onNode(hasText("Guardar") or hasText("Crear")).performClick()
        dismissError()
        compose.onNodeWithText("Nombre editado").assertExists()
        runBlocking { check(app.projectRepository.getProject(projectId)?.name == "Proyecto de prueba") }
        allowWrites()
        compose.onNode(hasText("Guardar") or hasText("Crear")).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Guardar") or hasText("Crear")).fetchSemanticsNodes().isEmpty() }
        runBlocking { check(app.projectRepository.getProject(projectId)?.name == "Nombre editado") }
    }

    @Test fun missingProjectUpdateKeepsEditorAndReportsRejection() {
        awaitText("Proyecto de prueba")
        compose.onNodeWithContentDescription("Opciones del proyecto").performClick()
        compose.onNodeWithContentDescription("Editar proyecto").performClick()
        runBlocking { app.projectRepository.deleteProject(projectId) }
        compose.onNode(hasText("Guardar") or hasText("Crear")).performClick()
        dismissError()
        compose.onNode(hasText("Guardar") or hasText("Crear")).assertExists()
        compose.onNodeWithText("Proyecto de prueba").assertExists()
    }

    @Test fun activityRecreationKeepsSameOpenDatabase() {
        val before = app.database
        compose.activityRule.scenario.recreate()
        check(before === (compose.activity.application as Arachn0deApplication).database)
        runBlocking { check(app.projectRepository.getProject(projectId) != null) }
    }
}
