package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.pomodoro.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PomodoroFinishStabilityTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun stableFinishInBothThemes() {
        val timer=PomodoroController(compose.activity) {PomodoroTime(1_800_000_000_000,100_000,7)}
        var bash by mutableStateOf(false)
        var expanded by mutableStateOf(false)
        var requests=0
        var mounts=0
        var unmounts=0
        compose.setContent {Arachn0deTheme {
            var confirm by rememberSaveable {mutableStateOf(false)}
            val modules=rememberBottomModulesState()
            val data by timer.state.collectAsState()
            CompositionLocalProvider(LocalBottomModulesState provides modules,
                LocalPomodoroFinishRequest provides {requests++;confirm=true},
                LocalBottomModulesContent provides { _:Boolean ->
                    if(!expanded) PomodoroBar(timer,data,compact=true,onExpand={expanded=true})
                }) {
                BottomModulesFrame {
                    BashPresentationScope(bash) {
                        if(expanded) PomodoroScreen(timer,data) {expanded=false}
                        else Box(Modifier.weight(1f)) {Text("General")}
                    }
                    if(confirm && data.visible) {
                        DisposableEffect(Unit) {mounts++;onDispose {unmounts++}}
                        PomodoroFinishConfirmation(timer) {confirm=false}
                    }
                }
            }
        }}
        for(mode in listOf(false,true)) {
            compose.runOnIdle {bash=mode;timer.start()}
            compose.onNodeWithTag("pomodoro-finish").performClick()
            // Confirmation must not transfer footer ownership or recreate the requesting bar.
            val before=timer.state.value
            repeat(4) {
                compose.mainClock.advanceTimeBy(500)
                compose.onAllNodesWithText("Finalizar Pomodoro").assertCountEquals(1)
                compose.onAllNodesWithTag("pomodoro-bar").assertCountEquals(1)
                compose.runOnIdle {assertEquals(before,timer.state.value)}
            }
            compose.runOnIdle {assertEquals(mounts,unmounts+1)}
            compose.onNodeWithText("Cancelar").performClick()
            compose.onNodeWithText("Finalizar Pomodoro").assertDoesNotExist()
            compose.runOnIdle {assertEquals(before,timer.state.value);assertEquals(mounts,unmounts)}
            // Full view uses the same request owner and still waits for confirmation.
            // The full dial intentionally breathes forever; drive its clock manually in this native test.
            compose.mainClock.autoAdvance=false
            compose.onNodeWithTag("pomodoro-expand").performClick()
            compose.mainClock.advanceTimeBy(64)
            // Invoke the full-view action semantically while the radial drawing is animated.
            val beforeFullRequest=requests
            compose.onNodeWithTag("pomodoro-finish").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) {it()}
            compose.mainClock.advanceTimeBy(1000)
            compose.runOnIdle {assertEquals(beforeFullRequest+1,requests)}
            compose.onNodeWithText("Cancelar").performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.runOnIdle {assertEquals(before,timer.state.value);expanded=false}
            compose.mainClock.advanceTimeBy(64)
            compose.mainClock.autoAdvance=true
            compose.onNodeWithTag("pomodoro-finish").performClick()
            compose.mainClock.advanceTimeBy(1000)
            compose.onNodeWithText("Finalizar Pomodoro").assertExists()
            compose.onNodeWithText("Finalizar",substring=false).performClick()
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithTag("pomodoro-bar").assertDoesNotExist()
            compose.onNodeWithText("Finalizar Pomodoro").assertDoesNotExist()
            compose.runOnIdle {assertEquals(PomodoroStatus.FINISHED,timer.state.value.session!!.status);assertEquals(mounts,unmounts)}
        }
        compose.runOnIdle {assertEquals(6,requests);assertEquals(6,mounts)}
    }
}
