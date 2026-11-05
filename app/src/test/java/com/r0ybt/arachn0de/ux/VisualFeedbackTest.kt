package com.r0ybt.arachn0de.ux

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.ui.*
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[33])
class VisualFeedbackTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun bankPanelUsesSemanticReadableTextInCharcoalDarkAndBash() {
        var theme by mutableStateOf(AppearanceTheme.Arachn0de)
        var content=Color.Unspecified
        compose.setContent {Arachn0deTheme {CompositionLocalProvider(LocalAppearancePalette provides theme.palette,LocalContentColor provides Color.Black) {
            BankPanel("Pagar",{},{}) {content=LocalContentColor.current;Text("Movimiento neutro")}
        }}}
        listOf(AppearanceTheme.Arachn0de,AppearanceTheme.Dark,AppearanceTheme.GreenBash).forEach {t->
            compose.runOnIdle {theme=t};compose.waitForIdle()
            assertEquals(t.palette.text,content)
            val a=content.luminance();val b=t.palette.panel.luminance()
            assertTrue(t.name,(maxOf(a,b)+.05f)/(minOf(a,b)+.05f)>=4.5f)
            compose.onNodeWithText("Movimiento neutro").assertIsDisplayed()
        }
    }
    @Test fun recordingIsVisibleDuringSilenceAndPauseHasDistinctStaticState() {
        var paused by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {RecordingLevelIcon(listOf(0f,.3f,1f,.6f),paused)}}
        compose.onNodeWithTag("recording-active").assertExists()
        compose.onNodeWithContentDescription("Grabación activa").assertExists()
        val size=compose.onNodeWithTag("recording-active").fetchSemanticsNode().boundsInRoot.size
        compose.runOnIdle {paused=true}
        compose.onNodeWithTag("recording-active").assertDoesNotExist()
        compose.onNodeWithContentDescription("Grabación en pausa").assertExists()
        assertEquals(size,compose.onNodeWithTag("recording-paused").fetchSemanticsNode().boundsInRoot.size)
    }
}
