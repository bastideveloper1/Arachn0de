package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w411dp-h891dp")
class PrimaryControlButtonTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun sharedControlsKeepGeometryCallbacksAndDisabledState() {
        var enabled by mutableStateOf(true)
        var clicks=0;var locks=0
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalProjectLock provides ProjectLockAction(enabled) {locks++}) {
                Row {
                    listOf("Nuevo Núcleo","Crear","Capas").forEach {label->
                        PrimaryControlButton({clicks++},Modifier.width(80.dp).height(64.dp).testTag(label),enabled) {
                            Text(label,style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    ProjectLockButton(Modifier.width(80.dp).height(64.dp).testTag("lock"),boxed=true)
                }
            }
        }}
        val bounds=listOf("Nuevo Núcleo","Crear","Capas","lock").map {
            compose.onNodeWithTag(it).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInRoot
        }
        bounds.forEach {assertEquals(bounds[0].width,it.width,1f);assertEquals(bounds[0].height,it.height,1f);assertEquals(bounds[0].top,it.top,1f)}
        compose.onNodeWithTag("Crear").performClick();compose.onNodeWithTag("lock").performClick()
        assertEquals(1,clicks);assertEquals(1,locks)
        compose.runOnIdle {enabled=false}
        compose.onNodeWithTag("Crear").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("lock").assertIsNotEnabled().performClick()
        assertEquals(1,clicks);assertEquals(1,locks)
    }
}
