package com.r0ybt.arachn0de.security

import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.MainActivity
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=LegacyUiTestApplication::class,sdk=[28])
class ScreenCaptureUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private val manager get()=app.security
    private fun secure()=compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0
    private fun waitText(text:String)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    @Before fun setup() {
        val session=manager.current.value!!
        File(session.access.root,"key.envelope").writeBytes(session.access.withKey {VaultCrypto.wrap(session.access.id,"capture-ui-fixture".toCharArray(),it)})
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {SecurityControls(manager,session)}}}
    }
    @After fun close() {manager.captures.revoke();app.database.close()}
    @Test fun correctAuthorizationChangesWindowAndManualDisableNeedsNoPassword() {
        assertTrue(secure());compose.onNodeWithText("Capturas bloqueadas").assertExists()
        compose.onNodeWithText("Seguridad").performClick()
        compose.onNodeWithText("Permitir capturas de pantalla").performScrollTo().performClick()
        assertTrue(secure())
        compose.onNodeWithText("Contraseña principal").performTextInput("capture-ui-fixture")
        compose.onNodeWithText("Autorizar").performClick()
        waitText("Capturas permitidas en esta sesión")
        assertFalse(secure());assertTrue(manager.captures.allowed.value)
        compose.onNodeWithText("Bloquear capturas de pantalla").performScrollTo().performClick()
        assertTrue(secure());assertFalse(manager.captures.allowed.value)
        compose.onNodeWithText("Contraseña principal").assertDoesNotExist()
    }
    @Test fun wrongPasswordStaysSecureAndPasswordDialogRemainsProtectedWithoutRevokingManualPermission() {
        compose.onNodeWithText("Seguridad").performClick()
        compose.onNodeWithText("Permitir capturas de pantalla").performScrollTo().performClick()
        compose.onNodeWithText("Contraseña principal").performTextInput("wrong-ui-fixture")
        compose.onNodeWithText("Autorizar").performClick()
        waitText("No se autorizaron las capturas. Comprueba la contraseña y reintenta.")
        assertTrue(secure());assertFalse(manager.captures.allowed.value)
        compose.runOnUiThread {runBlocking {assertTrue(manager.captures.grant(manager.captures.epoch) {true})}}
        assertFalse(secure())
        compose.onNodeWithText("Cambiar contraseña").performScrollTo().performClick()
        waitText("Contraseña actual");compose.onNodeWithText("Contraseña actual").assertExists()
        assertFalse(secure());assertTrue(manager.captures.allowed.value)
        val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
        assertTrue(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
    }
    @Test fun decoyControlsDoNotExposeCaptureAuthorization() {
        val current=manager.current.value!!
        val decoy=VaultSession(current.access,current.context,current.database,false)
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {SecurityControls(manager,decoy)}}}
        compose.onNodeWithText("Seguridad").performClick()
        compose.onNodeWithText("Permitir capturas de pantalla").assertDoesNotExist()
        compose.onNodeWithText("Capturas bloqueadas").assertDoesNotExist()
        assertTrue(secure())
        decoy.scope.cancel()
    }
}
