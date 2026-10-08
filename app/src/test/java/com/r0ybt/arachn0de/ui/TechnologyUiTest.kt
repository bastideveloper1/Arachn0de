package com.r0ybt.arachn0de.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.MainActivity
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.TechnologyRepository
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TechnologyUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as Arachn0deApplication
    private lateinit var repository: TechnologyRepository
    @Before fun setup() {
        repository = TechnologyRepository(app.database, TechnologyIconStore(app, BackupFixture::syncDirectory))
    }
    @After fun close() { repository.close() }
    private fun library() {
        compose.runOnUiThread { compose.activity.setContent { Arachn0deTheme { TechnologiesScreen(repository) {} } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Nueva tecnología").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun create(name: String) {
        compose.onNodeWithText("Nueva tecnología").performClick()
        compose.onNodeWithText("Nombre").performTextInput(name)
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { runBlocking { app.database.technologyDao().catalog().any { it.name == name } } }
    }
    @Test fun libraryCreateEditAndConfirmedDeleteLeaveTheOwnersUntouched() {
        val project = runBlocking { app.projectRepository.createProject("Project") }
        library(); create("Python")
        compose.onNodeWithText("Editar").performClick()
        compose.onNodeWithText("Nombre").performTextReplacement("Python personalizada")
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Python personalizada").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Eliminar").performClick()
        compose.onNodeWithText("Eliminar tecnología").assertExists()
        assertEquals(1, runBlocking { app.database.technologyDao().catalog().size })
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(1, runBlocking { app.database.technologyDao().catalog().size })
        compose.onNodeWithText("Eliminar").performClick()
        compose.onAllNodesWithText("Eliminar").onLast().performClick()
        compose.waitUntil(10_000) { runBlocking { app.database.technologyDao().catalog().isEmpty() } }
        assertNotNull(runBlocking { app.database.projectDao().getById(project.id) })
    }
    @Test fun pickerImportsPrivateIconAndReplacingItRemovesOnlyPreviousUnsharedFile() {
        library(); create("Kotlin")
        fun selectIcon(color: Int) {
            val beforeCount = File(app.filesDir, "technology-icons").listFiles().orEmpty().size
            compose.onNodeWithText("Seleccionar icono").performClick()
            val launched = shadowOf(compose.activity).nextStartedActivityForResult
            val file = File(app.cacheDir, "technology-picker.png")
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            compose.runOnUiThread { compose.activity.activityResultRegistry.dispatchResult(launched.requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
            compose.waitUntil(10_000) { File(app.filesDir, "technology-icons").listFiles().orEmpty().size > beforeCount && compose.onAllNodesWithText("Guardar").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        }
        compose.onNodeWithText("Editar").performClick(); selectIcon(android.graphics.Color.BLUE)
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { runBlocking { app.database.technologyDao().catalog().single().iconFile != null } }
        val old = runBlocking { app.database.technologyDao().catalog().single().iconFile!! }
        compose.onNodeWithText("Editar").performClick(); selectIcon(android.graphics.Color.RED)
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { runBlocking { app.database.technologyDao().catalog().single().iconFile != old } }
        val new = runBlocking { app.database.technologyDao().catalog().single().iconFile!! }
        assertTrue(File(app.filesDir, "technology-icons/$new").isFile); assertFalse(File(app.filesDir, "technology-icons/$old").exists())
    }
    @Test fun ownerControlsAllowMultipleAssignmentAndConsultingSharedNames() {
        val project = runBlocking { app.projectRepository.createProject("Project") }
        val layer = runBlocking { app.nodeRepository.createNode(project.id, null, "Layer", purpose = com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER) }
        val task = runBlocking { app.nodeRepository.createNode(project.id, layer.id, "Task") }
        runBlocking { repository.save("python", "Python", null, true); repository.save("docker", "Docker", null, true) }
        compose.runOnUiThread { compose.activity.setContent { Arachn0deTheme { Column {
            TechnologyOwnerControl(project.id, true, "Project", repository)
            TechnologyOwnerControl(layer.id, false, "Layer", repository)
            TechnologyOwnerControl(task.id, false, "Task", repository)
        } } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Tecnologías de Project").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        listOf("Project", "Layer", "Task").forEach { name ->
            compose.onNodeWithContentDescription("Tecnologías de $name").performClick()
            compose.onNodeWithText("Python").performClick(); compose.onNodeWithText("Docker").performClick()
            compose.onNodeWithText("Guardar").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Guardar").fetchSemanticsNodes().isEmpty() }
        }
        assertEquals(1, runBlocking { app.database.technologyDao().projects().map { it.projectId }.toSet().size })
        assertEquals(4, runBlocking { app.database.technologyDao().nodes().size })
        runBlocking { repository.save("python", "Python compartida", null, false) }
        compose.onNodeWithContentDescription("Tecnologías de Task").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Python compartida").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Python compartida").assertExists(); compose.onNodeWithText("Docker").assertExists()
    }
    @Test fun projectAndNodeCardsDisplaySharedCatalogControls() {
        val project = runBlocking { app.projectRepository.createProject("Visible project") }
        val node = runBlocking { app.nodeRepository.createNode(project.id, null, "Visible task") }
        runBlocking {
            repository.save("tool", "Herramienta visible", null, true)
            repository.assign(project.id, true, setOf("tool")); repository.assign(node.id, false, setOf("tool"))
        }
        compose.runOnUiThread { compose.activity.setContent { Arachn0deTheme { Column {
            ProjectCard(project, null, {}, {}, {})
            NodeCard(node, null, false, true, {}, {}, {}, false, false, { _, done -> done() }, {})
        } } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Tecnologías de Visible project").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Tecnologías de Visible project").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tecnologías de Visible task").assertIsDisplayed().performClick()
        compose.onNodeWithText("Herramienta visible").assertExists()
    }
    @Test fun navigationMenuOpensLocalTechnologyCatalog() {
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Tecnologías").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Nueva tecnología").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nueva tecnología").assertExists(); compose.onNodeWithText("Volver").performClick()
        compose.onNodeWithContentDescription("Abrir menú").assertExists()
    }
}
