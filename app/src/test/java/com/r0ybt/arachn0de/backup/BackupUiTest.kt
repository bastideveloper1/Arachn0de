package com.r0ybt.arachn0de.backup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.MainActivity
import com.r0ybt.arachn0de.ui.BackupSection
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as Arachn0deApplication
    private fun setup(onRestored: () -> Unit = {}) {
        val repo = BackupRepository(app.database, app, BackupAvatarFiles(app, BackupFixture::syncDirectory))
        compose.runOnUiThread { compose.activity.setContent { Arachn0deTheme { Column { BackupSection(onRestored, repo) } } } }
    }
    private fun select(bytes: ByteArray) {
        val uri = Uri.parse("content://backup-ui/input")
        shadowOf(compose.activity.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        compose.onNodeWithText("Restaurar backup").performClick()
        val launched = shadowOf(compose.activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, launched.intent.action)
        compose.runOnUiThread { compose.activity.activityResultRegistry.dispatchResult(launched.requestCode, Activity.RESULT_OK, Intent().setData(uri)) }
    }
    @Test fun validSelectionRequiresConfirmationCancelDoesNothingAndConfirmedEmptyRestoreReplacesData() {
        val project = runBlocking { app.projectRepository.createProject("Estado actual") }
        var restored = false
        setup { restored = true }
        val bytes = BackupFixture.archive(BackupFixture.empty())
        select(bytes)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("¿Reemplazar todos los datos?").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(project.id, runBlocking { app.database.projectDao().getAll().single().id })
        compose.onNodeWithText("Cancelar").performClick()
        assertFalse(restored)
        assertEquals(1, runBlocking { app.database.projectDao().getAll().size })
        select(bytes)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Reemplazar y restaurar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Reemplazar y restaurar").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(isRoot()).fetchSemanticsNodes(); restored }
        assertTrue(runBlocking { app.database.projectDao().getAll().isEmpty() })
    }
    @Test fun invalidSelectionNeverOffersDestructiveConfirmation() {
        runBlocking { app.projectRepository.createProject("Conservar") }
        setup()
        select(byteArrayOf(1, 2, 3))
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No se pudo validar el backup", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Reemplazar y restaurar").assertDoesNotExist()
        assertEquals("Conservar", runBlocking { app.database.projectDao().getAll().single().name })
    }
    @Test @Config(sdk = [24]) fun restoreFromAboutReturnsToDashboardWithRestoredProjects() {
        runBlocking { app.projectRepository.createProject("Antes") }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Antes").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Antes").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Nuevo elemento").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Acerca de").performScrollTo().performClick()
        val incoming = BackupFixture.empty().copy(projects = listOf(com.r0ybt.arachn0de.data.local.ProjectEntity("restored", "Recuperado", "", 3, 1, 2)))
        val uri = Uri.parse("content://backup-ui/root")
        shadowOf(compose.activity.contentResolver).registerInputStream(uri, ByteArrayInputStream(BackupFixture.archive(incoming)))
        compose.onNodeWithText("Restaurar backup").performScrollTo().performClick()
        val launched = shadowOf(compose.activity).nextStartedActivityForResult
        compose.runOnUiThread { compose.activity.activityResultRegistry.dispatchResult(launched.requestCode, Activity.RESULT_OK, Intent().setData(uri)) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Reemplazar y restaurar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Reemplazar y restaurar").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Recuperado").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Acerca de Arachn0de").assertDoesNotExist()
        compose.onNodeWithText("Antes").assertDoesNotExist()
        assertEquals("Backup restaurado correctamente.", org.robolectric.shadows.ShadowToast.getTextOfLatestToast())
    }

    @Test fun failingUiCallbackCannotMisreportACommittedRestoreAsRollback() {
        runBlocking { app.projectRepository.createProject("Actual") }
        val repository = BackupRepository(app.database, app, BackupAvatarFiles(app, BackupFixture::syncDirectory))
        val actions = com.r0ybt.arachn0de.ui.state.BackupActions(repository, BackupDocuments(app.contentResolver), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main))
        val uri = Uri.parse("content://backup-ui/callback")
        shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(BackupFixture.archive(BackupFixture.empty())))
        compose.runOnUiThread { actions.inspect(uri) }
        compose.waitUntil(10_000) { compose.onAllNodes(isRoot()).fetchSemanticsNodes(); actions.candidate != null && !actions.busy }
        compose.runOnUiThread { actions.restore { throw IllegalStateException("Injected UI callback failure") } }
        compose.waitUntil(10_000) { compose.onAllNodes(isRoot()).fetchSemanticsNodes(); !actions.busy }
        assertTrue(runBlocking { app.database.projectDao().getAll().isEmpty() })
        assertTrue(actions.notice!!.startsWith("Los datos se restauraron"))
    }

    @Test fun creatingBackupLaunchesSafOnlyAfterArtifactExistsAndReportsSuccessfulSave() {
        setup()
        compose.onNodeWithText("Crear backup").performClick()
        compose.waitUntil(10_000) { shadowOf(compose.activity).peekNextStartedActivityForResult() != null }
        val launched = shadowOf(compose.activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, launched.intent.action)
        assertTrue(launched.intent.getStringExtra(Intent.EXTRA_TITLE)!!.matches(Regex("Arachn0de-Backup-\\d{4}-\\d{2}-\\d{2}\\.arachnode")))
        val uri = Uri.parse("content://backup-ui/output")
        val bytes = ByteArrayOutputStream()
        shadowOf(compose.activity.contentResolver).registerOutputStream(uri, bytes)
        compose.runOnUiThread { compose.activity.activityResultRegistry.dispatchResult(launched.requestCode, Activity.RESULT_OK, Intent().setData(uri)) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Backup guardado correctamente.").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(runBlocking { BackupContainer.readBackup(ByteArrayInputStream(bytes.toByteArray())) }.projects.isEmpty())
    }
}
