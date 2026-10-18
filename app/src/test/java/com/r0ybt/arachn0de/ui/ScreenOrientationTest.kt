package com.r0ybt.arachn0de.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ScreenOrientationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exceptionAllowsRotationAndRestoresPortraitWhenClosed() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val visible = mutableStateOf(true)
        val allow = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides activity) {
                if (visible.value) ScreenOrientation(allow.value)
            }
        }
        compose.runOnIdle { assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, activity.requestedOrientation) }
        compose.runOnIdle { allow.value = false }
        compose.runOnIdle { assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation) }
        compose.runOnIdle { allow.value = true }
        compose.runOnIdle { assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER, activity.requestedOrientation) }
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation) }
        controller.pause().stop().destroy()
    }
}
