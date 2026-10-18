package com.r0ybt.arachn0de.pomodoro

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.PomodoroScreen
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w800dp-h800dp")
class PomodoroAppearanceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun legacyCheckpointKeepsSessionAndStylesRoundTripWithoutChangingTiming() {
        val now=PomodoroTime(1_800_000_000_000,100_000,7)
        val original=PomodoroState(PomodoroConfig(18,3,12,6,true,false)).start(now).pause(now)
        val legacy=JSONObject(PomodoroCodec.encode(original)).put("version",1).apply {getJSONObject("config").remove("appearance")}.toString()
        assertEquals(original,PomodoroCodec.decode(legacy))
        PomodoroAppearance.entries.forEach {style->
            val state=original.copy(config=original.config.copy(appearance=style))
            assertEquals(state,PomodoroCodec.decode(PomodoroCodec.encode(state)))
        }
        listOf<Any>("UNKNOWN",true,JSONObject.NULL).forEach {invalid->
            val payload=JSONObject(PomodoroCodec.encode(original)).apply {getJSONObject("config").put("appearance",invalid)}.toString()
            assertTrue(runCatching {PomodoroCodec.decode(payload)}.isFailure)
        }
    }

    @Test fun allStylesSelectSaveReopenAndFitBothOrientationsWithoutChangingSession() {
        val context=compose.activity
        context.getSharedPreferences(PomodoroController.CACHE,0).edit().clear().commit()
        val storage=context.getSharedPreferences("appearance-pomodoro-fixture",0)
        storage.edit().clear().commit()
        val clock={PomodoroTime(1_800_000_000_000,100_000,7)}
        val controller=PomodoroController(context,clock)
        controller.attach(storage);controller.start();controller.pause()
        val session=controller.state.value.session
        var horizontal by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {
            val data by controller.state.collectAsState()
            Box(Modifier.requiredSize(if(horizontal) 640.dp else 320.dp,if(horizontal) 300.dp else 640.dp)) {
                PomodoroScreen(controller,data,allowLandscape=false) {}
            }
        }}
        PomodoroAppearance.entries.forEach {style->
            compose.onNodeWithTag("pomodoro-settings").performClick()
            compose.onNodeWithTag("pomodoro-style:${style.name}").performScrollTo().performClick()
            compose.onNodeWithText("Guardar").performClick()
            compose.runOnIdle {
                assertEquals(style,controller.state.value.config.appearance)
                assertEquals(session,controller.state.value.session)
                val reopened=PomodoroController(context,clock)
                reopened.attach(storage)
                assertEquals(style,reopened.state.value.config.appearance)
                reopened.attach(null)
            }
            listOf(false,true).forEach {landscape->
                compose.runOnIdle {horizontal=landscape}
                compose.onNodeWithTag("pomodoro-dial:${style.name}").assertIsDisplayed()
                val circle=compose.onNodeWithTag("pomodoro-circle").fetchSemanticsNode().boundsInRoot
                val time=compose.onNodeWithTag("pomodoro-time").fetchSemanticsNode().boundsInRoot
                val controls=compose.onNodeWithTag("pomodoro-settings").fetchSemanticsNode().boundsInRoot
                assertTrue(circle.width>0 && circle.height>0)
                assertTrue(time.left>=circle.left && time.right<=circle.right)
                assertTrue(time.top>=circle.top && time.bottom<=circle.bottom)
                assertTrue(circle.bottom<=controls.top)
                compose.runOnIdle {assertEquals(session,controller.state.value.session)}
            }
        }
        compose.runOnIdle {controller.attach(null)}
    }
}
