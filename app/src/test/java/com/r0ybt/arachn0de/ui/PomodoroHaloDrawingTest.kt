package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.pomodoro.PomodoroAppearance
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PomodoroHaloDrawingTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun liveHaloChangesPixelsAndStopsWhenCovered() {
        var visible by mutableStateOf(true)
        compose.mainClock.autoAdvance=false
        compose.setContent {
            CompositionLocalProvider(LocalAudioFooterVisible provides visible) {
                Box(Modifier.size(240.dp).background(PomodoroBackground).testTag("halo")) {
                    PomodoroDial(PomodoroAppearance.SOLAR,"25:00","",{.5f},Modifier.size(240.dp))
                }
            }
        }
        compose.mainClock.advanceTimeBy(1000)
        val first=sample()
        compose.mainClock.advanceTimeBy(1800)
        val next=sample()
        assertTrue("La animación no llega al halo: $first / $next",next>first+.005f)
        compose.mainClock.advanceTimeBy(1200)
        val peak=sample()
        compose.mainClock.advanceTimeBy(4000)
        assertTrue("El ciclo de ocho segundos debe producir un cambio visible",peak>sample()+.04f)
        compose.runOnIdle {visible=false}
        compose.mainClock.advanceTimeBy(32)
        val stopped=sample()
        compose.mainClock.advanceTimeBy(5000)
        assertEquals(stopped,sample(),.001f)
    }

    @Test fun minimalAndPreviewAreStaticFromTheirFirstDraw() {
        compose.mainClock.autoAdvance=false
        compose.setContent {
            Row {
                Box(Modifier.size(140.dp).background(PomodoroBackground).testTag("minimal")) {
                    PomodoroDial(PomodoroAppearance.MINIMAL,"25:00","",{.5f},Modifier.size(140.dp))
                }
                Box(Modifier.size(140.dp).background(PomodoroBackground).testTag("preview")) {
                    PomodoroDial(PomodoroAppearance.SOLAR,"25:00","",{.5f},Modifier.size(140.dp),preview=true)
                }
            }
        }
        compose.mainClock.advanceTimeBy(32)
        val minimal=sample("minimal")
        val preview=sample("preview")
        assertEquals(PomodoroBackground.red,minimal,.005f)
        assertTrue(preview>minimal+.04f)
        compose.mainClock.advanceTimeBy(8000)
        assertEquals(minimal,sample("minimal"),.001f)
        assertEquals(preview,sample("preview"),.001f)
    }

    private fun sample(tag:String="halo"):Float {
            val bounds=compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            var red=0f
            compose.runOnIdle {
                val view=compose.activity.findViewById<android.view.View>(android.R.id.content)
                val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                red=Color.red(bitmap.getPixel((bounds.left+bounds.width*.54f).toInt(),(bounds.top+bounds.height*.75f).toInt()))/255f
                bitmap.recycle()
            }
            return red
        }
}
