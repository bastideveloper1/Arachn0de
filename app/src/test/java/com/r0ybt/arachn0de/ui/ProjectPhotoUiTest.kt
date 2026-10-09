package com.r0ybt.arachn0de.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.app.Application
import androidx.activity.ComponentActivity
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.ProjectPhotoStore
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
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
@Config(sdk = [28], application = Application::class)
class ProjectPhotoUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = compose.activity.application as Application
    private lateinit var database: Arachn0deDatabase
    private lateinit var photos: ProjectPhotoRepository
    private lateinit var projects: ProjectRepository
    private lateinit var project: Project
    @Before fun setup() = runBlocking {
        app.deleteDatabase("arachn0de.db")
        database = Arachn0deDatabase.create(app)
        photos = ProjectPhotoRepository(database, ProjectPhotoStore(app, BackupFixture::syncDirectory))
        projects = ProjectRepository(database.projectDao(), database)
        project = projects.createProject("Fotografías")
    }
    @After fun cleanup() { database.close() }
    private fun waitText(text: String) { compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private var hostInstalled = false
    private var edited by androidx.compose.runtime.mutableStateOf<Project?>(null)
    private var session by androidx.compose.runtime.mutableIntStateOf(0)
    private fun show() {
        val current = runBlocking { projects.getProject(project.id)!! }
        if (!hostInstalled) {
            compose.runOnUiThread { compose.activity.setContent { Arachn0deTheme {
                androidx.compose.runtime.key(session) { edited?.let { selected ->
                    ProjectPhotoDialog(selected, onClose = { edited = null }, repository = photos)
                } }
            } } }
            hostInstalled = true
        }
        compose.runOnIdle { session++; edited = current }
        waitText("Fotografía del proyecto")
        compose.waitUntil(10_000) {
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.onAllNodes(isEnabled() and hasText("Guardar fotografía")).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun waitClosed() { compose.waitUntil(10_000) { compose.onAllNodesWithText("Fotografía del proyecto").fetchSemanticsNodes().isEmpty() } }
    private fun select(label: String = "Seleccionar fotografía") {
        compose.onNodeWithText(label).performScrollTo().performClick()
        val launched = shadowOf(compose.activity).nextStartedActivityForResult
        val file = File(app.cacheDir, "picked-project.png").apply { writeBytes(BackupFixture.png()) }
        compose.runOnUiThread { compose.activity.activityResultRegistry.dispatchResult(launched.requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
        waitText("Encuadrar fotografía"); compose.onNodeWithText("Confirmar").assertExists()
    }
    @Test fun selectionCancelSaveReplacementAndReopenPreserveFramesAndOriginalUntilCommit() {
        show(); select(); compose.onAllNodesWithText("Cancelar").onLast().performClick()
        compose.waitUntil(10_000) { File(app.filesDir, "project-photos").listFiles().orEmpty().isEmpty() }
        assertNull(runBlocking { projects.getProject(project.id)!!.photo })
        select(); compose.onNodeWithText("Ampliar").performClick(); compose.onNodeWithText("Confirmar").performClick()
        compose.onNodeWithText("Guardar fotografía").performClick()
        compose.waitUntil(10_000) { runBlocking { projects.getProject(project.id)!!.photo != null } }
        waitClosed()
        val saved = runBlocking { projects.getProject(project.id)!!.photo!! }
        assertEquals(1.2f, saved.framing.zoom, .0001f)
        show(); compose.onNodeWithText("Ajustar encuadre").performScrollTo().performClick(); waitText("Zoom: 1.20×")
        compose.onNodeWithText("Restablecer").performScrollTo().performClick()
        compose.onAllNodesWithText("Cancelar").onLast().performClick()
        select("Reemplazar fotografía"); compose.onNodeWithText("Confirmar").performClick()
        assertTrue(File(app.filesDir, "project-photos/${saved.file}").exists())
        compose.onNodeWithText("Cancelar").performClick()
        compose.waitUntil(10_000) { File(app.filesDir, "project-photos").listFiles().orEmpty().size == 1 }
        assertEquals(saved, runBlocking { projects.getProject(project.id)!!.photo })
    }
    @Test fun removingPhotoRequiresSaveAndCancelKeepsConfirmedResource() {
        val source = File(app.cacheDir, "initial-project.png").apply { writeBytes(BackupFixture.png()) }
        val file = runBlocking { photos.import(Uri.fromFile(source), "seed").also { photos.save(project.id, it, AvatarFraming(2f, .3f, 0f), "seed") } }
        show(); compose.onNodeWithText("Quitar fotografía").performScrollTo().performClick(); compose.onNodeWithText("Cancelar").performClick()
        waitClosed(); assertEquals(file, runBlocking { projects.getProject(project.id)!!.photo!!.file })
        show(); compose.onNodeWithText("Quitar fotografía").performScrollTo().performClick(); compose.onNodeWithText("Guardar fotografía").performClick()
        compose.waitUntil(10_000) { runBlocking { projects.getProject(project.id)!!.photo == null } }
        compose.waitUntil(10_000) { !File(app.filesDir, "project-photos/$file").exists() }
    }
    @Test fun decodedPhotoKeepsCompactCardHeightAndOpensPhotoMenu() {
        val source = File(app.cacheDir, "card-project.png").apply { writeBytes(BackupFixture.png()) }
        runBlocking { val file = photos.import(Uri.fromFile(source), "card"); photos.save(project.id, file, AvatarFraming(), "card") }
        var displayed by androidx.compose.runtime.mutableStateOf(project)
        var selected = false
        compose.runOnUiThread { compose.activity.setContent { Arachn0deTheme {
            ProjectCard(displayed, ProjectCardAlerts(), {}, {}, {}, onPhoto = { selected = true })
        } } }
        compose.waitForIdle()
        val before = compose.onNodeWithTag("project-card:${project.id}").fetchSemanticsNode().boundsInRoot.height
        val saved = runBlocking { projects.getProject(project.id)!! }
        compose.runOnIdle { displayed = saved }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Fotografía de ${project.name}").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(before, compose.onNodeWithTag("project-card:${project.id}").fetchSemanticsNode().boundsInRoot.height, .1f)
        compose.onNodeWithContentDescription("Opciones del proyecto").performClick()
        compose.onNodeWithText("Fotografía").performClick(); compose.runOnIdle { assertTrue(selected) }
    }

}
