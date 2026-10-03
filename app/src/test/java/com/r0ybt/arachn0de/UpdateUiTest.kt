package com.r0ybt.arachn0de

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.ui.AboutScreen
import com.r0ybt.arachn0de.update.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UpdateUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun checkIsManualAndShowsInstalledVersionAndUpToDate() {
        var calls = 0
        val repo = UpdateRepository(ReleaseSource { calls++; emptyList() })
        compose.runOnUiThread { compose.activity.setContent { AboutScreen(onBack = {}, repository = repo) } }
        compose.onNodeWithText("Versión instalada: 0.2.0").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("Buscar actualizaciones").performClick()
        compose.onNodeWithText("Arachn0de está actualizado.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, calls) }
    }
    @Test fun failureCanBeRetriedWithoutLeavingScreen() {
        var fail = true
        val repo = UpdateRepository(ReleaseSource { if (fail) throw java.io.IOException(); emptyList() })
        compose.runOnUiThread { compose.activity.setContent { AboutScreen(onBack = {}, repository = repo) } }
        compose.onNodeWithText("Buscar actualizaciones").performClick()
        compose.onNodeWithText("Volver a intentar").assertIsDisplayed()
        compose.runOnIdle { fail = false }
        compose.onNodeWithText("Volver a intentar").performClick()
        compose.onNodeWithText("Arachn0de está actualizado.").assertIsDisplayed()
    }
    @Test fun drawerAboutReturnsToHomeWithoutChecking() {
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Acerca de").performClick()
        compose.onNodeWithText("Acerca de Arachn0de").assertIsDisplayed()
        compose.onNodeWithText("Buscar actualizaciones").assertIsDisplayed()
        compose.onNodeWithText("Volver").performClick()
        compose.onNodeWithText("Proyectos").assertIsDisplayed()
    }
    @Test fun downloadIsExplicitAndInstallAppearsOnlyAfterVerification() {
        val apk = ReleaseAsset("Arachn0de-v0.3.0.apk", "https://github.com/bastideveloper1/Arachn0de/releases/download/v0.3.0/Arachn0de-v0.3.0.apk", 1)
        val release = GitHubRelease("v0.3.0", SemanticVersion.parse("0.3.0")!!, "Beta", "Notas", true, listOf(apk), apk, null)
        var downloadsCount = 0
        val finish = kotlinx.coroutines.CompletableDeferred<Unit>()
        val downloads = object : UpdateDownloads {
            override suspend fun download(release: GitHubRelease, verifying: suspend () -> Unit): VerifiedUpdate {
                downloadsCount++; verifying(); finish.await()
                return VerifiedUpdate(release, java.io.File(compose.activity.cacheDir, "unused.apk"), byteArrayOf())
            }
            override suspend fun revalidate(update: VerifiedUpdate) { }
            override suspend fun recover(): VerifiedUpdate? = null
        }
        val repo = UpdateRepository(ReleaseSource { listOf(release) })
        compose.runOnUiThread { compose.activity.setContent { AboutScreen(onBack = {}, repository = repo, downloads = downloads) } }
        compose.onNodeWithText("Instalar actualización").assertDoesNotExist()
        compose.onNodeWithText("Buscar actualizaciones").performClick()
        compose.runOnIdle { assertEquals(0, downloadsCount) }
        compose.onNodeWithText("Descargar actualización").performScrollTo().performClick()
        compose.onNodeWithText("Verificando actualización…").assertExists()
        compose.onNodeWithText("Instalar actualización").assertDoesNotExist()
        compose.runOnIdle { finish.complete(Unit) }
        compose.onNodeWithText("Instalar actualización").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, downloadsCount) }
    }

}
