package com.r0ybt.arachn0de.pomodoro

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.*
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class)
class PomodoroUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun expandedAndCompactControlsShareStateConfirmFinishAndReserveSeparateFooterSpace() {
        val context=compose.activity
        context.getSharedPreferences(PomodoroController.CACHE,0).edit().clear().commit()
        val controller=PomodoroController(context) {PomodoroTime(1_800_000_000_000,100_000,7)}
        val preferences=AppearancePreferences(context.getSharedPreferences("pomodoro-ui-appearance",0))
        preferences.setTheme(AppearanceTheme.Dark)
        var expanded by mutableStateOf(true)
        // The dial's continuous halo must not drive Espresso's automatic clock indefinitely.
        compose.mainClock.autoAdvance=false
        compose.setContent {Arachn0deTheme(preferences) {
            val data by controller.state.collectAsState()
            Box(Modifier.width(320.dp).fillMaxHeight()) {
                if(expanded) PomodoroScreen(controller,data) {expanded=false}
                else Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).testTag("main-content"))
                    PomodoroBar(controller,data) {expanded=true}
                    Box(Modifier.height(48.dp).fillMaxWidth().testTag("audio-footer")) {Text("Audio")}
                }
            }
        }}
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("pomodoro-circle").assertExists()
        compose.onNodeWithTag("pomodoro-toggle").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {assertEquals(PomodoroStatus.RUNNING,controller.state.value.session!!.status);expanded=false}
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("pomodoro-bar").assertIsDisplayed()
        compose.onNodeWithTag("pomodoro-toggle").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {assertEquals(PomodoroStatus.PAUSED,controller.state.value.session!!.status)}
        compose.onNodeWithTag("pomodoro-toggle").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {assertEquals(PomodoroStatus.RUNNING,controller.state.value.session!!.status)}
        val main=compose.onNodeWithTag("main-content").fetchSemanticsNode().boundsInRoot
        val bar=compose.onNodeWithTag("pomodoro-bar").fetchSemanticsNode().boundsInRoot
        val audio=compose.onNodeWithTag("audio-footer").fetchSemanticsNode().boundsInRoot
        assertTrue(main.bottom<=bar.top+1);assertTrue(bar.bottom<=audio.top+1)
        compose.onNodeWithTag("pomodoro-expand").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("pomodoro-screen").assertIsDisplayed()
        compose.onNodeWithTag("pomodoro-finish").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("Cancelar").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {assertTrue(controller.state.value.visible)}
        compose.onNodeWithTag("pomodoro-finish").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNode(hasText("Finalizar") and hasAnyAncestor(isDialog())).performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {assertFalse(controller.state.value.visible)}
        compose.onNodeWithTag("pomodoro-status").assertTextEquals("Finalizada")
    }
    @Test fun compactBarToggleDoesNotChangeTimerSession() {
        val context=compose.activity
        context.getSharedPreferences(PomodoroController.CACHE,0).edit().clear().commit()
        val controller=PomodoroController(context) {PomodoroTime(1_800_000_000_000,100_000,7)}
        controller.start()
        var compact by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {
            val data by controller.state.collectAsState()
            PomodoroBar(controller,data,compact=compact,onCompactChange={compact=it},onExpand={})
        }}
        compose.onNodeWithTag("pomodoro-module-frame").assertHeightIsAtLeast(56.dp)
        compose.onNodeWithTag("pomodoro-compact-toggle").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val before=controller.state.value
        compose.onNodeWithTag("pomodoro-compact-toggle").performClick()
        compose.onNodeWithTag("pomodoro-bar-cycle").assertDoesNotExist()
        compose.runOnIdle {assertTrue(compact);assertEquals(before,controller.state.value)}
        compose.onNodeWithTag("pomodoro-compact-toggle").performClick()
        compose.onNodeWithTag("pomodoro-bar-cycle").assertExists()
        compose.runOnIdle {assertFalse(compact);assertEquals(before,controller.state.value)}
    }

    @Test fun pomodoroIsADirectDrawerEntryOutsideCollapsedTracking() {
        var opened=false
        var dismissed=false
        compose.setContent {Arachn0deTheme {
            AppIdentityDrawer(onDismiss={dismissed=true},onFavorites={},onTechnologies={},onAppearance={},onPomodoro={opened=true})
        }}
        compose.onNodeWithTag("drawer-section:Seguimiento").assertExists()
        compose.onNodeWithTag("drawer-pomodoro").assertIsDisplayed()
        compose.onNodeWithTag("drawer-section:Seguimiento").performClick()
        compose.onAllNodesWithText("Pomodoro").assertCountEquals(1)
        compose.onNodeWithTag("drawer-pomodoro").performClick()
        compose.runOnIdle {assertTrue(opened);assertTrue(dismissed)}
    }

}
