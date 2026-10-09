package com.r0ybt.arachn0de.security

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[28],application=Arachn0deApplication::class,qualifiers="w320dp-h800dp")
class SecurityGateUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun startupDoesNotOpenLegacyDatabaseAndMismatchedConfirmationCannotMigrate() {
        val app=compose.activity.application as Arachn0deApplication
        compose.onNodeWithText("Proteger tus datos").assertIsDisplayed()
        assertNull(app.security.current.value)
        assertTrue(runCatching {app.database}.isFailure)
        assertFalse(app.getDatabasePath("arachn0de.db").exists())
        assertTrue(compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
        compose.onNodeWithText("Contraseña").performTextInput("private-test-value")
        compose.onNodeWithText("Confirmar contraseña").performTextInput("different-test-value")
        compose.onNodeWithText("Proteger y migrar").performClick()
        compose.onNodeWithText("Confirma la misma contraseña de al menos ocho caracteres.").assertExists()
        assertNull(app.security.current.value);assertFalse(app.getDatabasePath("arachn0de.db").exists())
        compose.onAllNodesWithText("Proyectos").assertCountEquals(0)
    }
}
