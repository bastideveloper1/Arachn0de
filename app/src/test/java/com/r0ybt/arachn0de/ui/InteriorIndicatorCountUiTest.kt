package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
class InteriorIndicatorCountUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun badgeHidesZeroAndPreservesTouchSizeAtLargeFont() {
        var count by mutableIntStateOf(3)
        compose.setContent {Arachn0deTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
                Row(Modifier.width(280.dp)) {InteriorIndicatorCount(count,"tareas sin completar","header-test-badge") {Box(Modifier.size(48.dp)) {Text("✓")}}}
            }
        }}
        compose.onNodeWithTag("header-test-badge",useUnmergedTree=true).assertIsDisplayed()
        val bounds=compose.onNodeWithTag("header-test-badge",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width>0 && bounds.height>0)
        compose.onNodeWithText("✓",useUnmergedTree=true).onParent().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.runOnIdle {count=0}
        compose.onNodeWithTag("header-test-badge",useUnmergedTree=true).assertDoesNotExist()
    }
    @Test fun separateStateTotalsAreInformationalAndVerificationFollowsConfiguredMode() {
        var threeStates by mutableStateOf(true)
        var counts by mutableStateOf(InteriorTaskCounts(setOf("a","b"),emptySet(),setOf("v")))
        compose.setContent {Arachn0deTheme {CardIndicatorDetail("project:p",{},taskCounts=counts,verificationEnabled=threeStates)}}
        fun pending()=compose.onNodeWithTag("detail-task-state:project:p:PENDING")
        fun verify()=compose.onNodeWithTag("detail-task-state:project:p:VERIFY")
        pending().assert(hasStateDescription("2 tareas pendientes")).assertHasNoClickAction()
        verify().assert(hasStateDescription("1 tareas por verificar")).assertHasNoClickAction()
        val p=pending().fetchSemanticsNode().boundsInRoot
        val v=verify().fetchSemanticsNode().boundsInRoot
        assertEquals(p.center.y,v.center.y,1f);assertTrue(p.right<=v.left)
        compose.runOnIdle {threeStates=false}
        pending().assertExists();verify().assertDoesNotExist()
        compose.runOnIdle {counts=InteriorTaskCounts(emptySet(),emptySet());threeStates=true}
        pending().assert(hasStateDescription("0 tareas pendientes"))
        verify().assert(hasStateDescription("0 tareas por verificar"))
        compose.onNodeWithTag("detail-signal:project:p:tasks").assertDoesNotExist()
    }

}
