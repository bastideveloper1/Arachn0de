package com.r0ybt.arachn0de.security

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
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w360dp-h800dp")
class SecurityCardsUiTest {
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
    @Test fun fourCardsOpenUnifiedSecurityAndBackupDestinations() {
        compose.setContent {Arachn0deTheme {com.r0ybt.arachn0de.ui.AppSafeArea {
            SecurityControls(app.security,app.security.current.value!!)
        }}}
        compose.onNodeWithText("Seguridad y respaldos").performClick();text("Bloqueo automático")
        val screen=compose.onNodeWithTag("security-screen").fetchSemanticsNode().boundsInRoot
        val cards=listOf("security-timeout","security-captures","security-passwords","security-backups").map {compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot}
        assertTrue(cards[0].top>screen.top+screen.height*.4f);assertTrue(cards[0].bottom<=cards[1].top);assertTrue(cards[1].bottom<=cards[2].top);assertTrue(cards[2].bottom<=cards[3].top);assertTrue(cards[3].bottom<=screen.bottom)
        compose.onNodeWithTag("security-timeout").performClick();text("1 minuto");compose.onNodeWithText("1 minuto").performClick()
        await {app.security.current.value!!.context.getSharedPreferences("security_preferences",0).getLong("background_timeout",0)==60000L}
        compose.onNodeWithTag("security-captures").performClick();text("Permitir capturas de pantalla")
        assertFalse(app.security.captures.allowed.value)
        compose.onNodeWithContentDescription("Close sheet").performClick()
        compose.onNodeWithTag("security-passwords").performClick();text("Cambiar contraseña")
        compose.onNodeWithText("Cambiar contraseña").assertIsEnabled()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithTag("security-backups").performClick();text("Backup y restauración")
        compose.onNodeWithText("Crear backup").assertExists();compose.onNodeWithText("Restaurar backup").assertExists()
        compose.onNodeWithText("Volver").performClick();text("Gestionar respaldos")

    }    @Test fun manualOptionUsesExistingDurationSelectorAndCanReturnToAutomatic() {
        compose.setContent {Arachn0deTheme {com.r0ybt.arachn0de.ui.AppSafeArea {SecurityControls(app.security,app.security.current.value!!)}}}
        compose.onNodeWithText("Seguridad y respaldos").performClick();text("Bloqueo automático")
        compose.onNodeWithTag("security-timeout").performClick();text("Hasta bloquear manualmente")
        compose.onNodeWithText("Hasta bloquear manualmente").performClick()
        await {AutoLockPreference.read(app.security.current.value!!.context.getSharedPreferences(ScreenCapturePreference.NAME,0))==AutoLockPreference.MANUAL}
        text("Hasta bloquear manualmente")
        compose.onNodeWithTag("security-timeout").performClick();text("1 minuto")
        compose.onNodeWithText("1 minuto").performClick()
        await {AutoLockPreference.read(app.security.current.value!!.context.getSharedPreferences(ScreenCapturePreference.NAME,0))==60000L}
    }

}
