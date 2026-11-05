package com.r0ybt.arachn0de.status

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.*
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=android.app.Application::class,sdk=[28],qualifiers="w411dp-h891dp")
class StatusSelectorUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun twoAndThreeStatesHave48dpTargetsAndToggleFilterOnly() {
        var enabled by mutableStateOf(false);var selected by mutableStateOf<String?>(null);var behind=0
        compose.setContent {Arachn0deTheme {Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().testTag("behind").clickable {behind++})
            StatusSelector(taskStatusItems(enabled),selected,{selected=it},Modifier.align(Alignment.BottomEnd).padding(bottom=64.dp,end=8.dp))
        }}}
        compose.onNodeWithTag("status-filter:VERIFY").assertDoesNotExist()
        val a=compose.onNodeWithTag("status-filter:PENDING").fetchSemanticsNode().boundsInRoot
        val b=compose.onNodeWithTag("status-filter:COMPLETED").fetchSemanticsNode().boundsInRoot
        assertEquals(a.left,b.left,.1f);assertTrue(b.top>=a.bottom)
        compose.onNodeWithTag("status-filter:PENDING").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick().assertIsSelected()
        assertEquals("PENDING",selected)
        compose.onNodeWithTag("status-filter:PENDING").performClick().assertIsNotSelected();assertNull(selected)
        compose.runOnIdle {enabled=true}
        compose.onNodeWithContentDescription("Filtrar: Por verificar").assertIsDisplayed().performClick().assertIsSelected()
        assertEquals("VERIFY",selected)
        compose.onNodeWithTag("behind").performTouchInput {click(androidx.compose.ui.geometry.Offset(20f,100f))}
        assertEquals(1,behind)
    }
    @Test fun directionReflowsSameFiveExistingPhasesWithoutChangingTheirOrder() {
        var direction by mutableStateOf(StatusSelectorDirection.VERTICAL)
        var selected by mutableStateOf<String?>(null)
        compose.setContent {Arachn0deTheme {StatusSelector(workStatusItems(),selected,{selected=it},direction=direction)}}
        val phases=com.r0ybt.arachn0de.domain.model.WorkState.entries
        phases.forEach {compose.onNodeWithTag("status-filter:${it.name}").assertExists().assertHeightIsAtLeast(48.dp)}
        val vertical=compose.onNodeWithTag("status-selector").fetchSemanticsNode().boundsInRoot
        assertTrue(vertical.width<120f*compose.activity.resources.displayMetrics.density)
        assertTrue(vertical.height<170f*compose.activity.resources.displayMetrics.density)
        compose.onNodeWithTag("status-filter:DOING").performClick();assertEquals("DOING",selected)
        compose.runOnIdle {direction=StatusSelectorDirection.HORIZONTAL}
        val rects=phases.map {compose.onNodeWithTag("status-filter:${it.name}").fetchSemanticsNode().boundsInRoot}
        rects.zipWithNext().forEach {(a,b)->assertEquals(a.top,b.top,.1f);assertTrue(a.right<=b.left)}
        compose.onNodeWithTag("status-filter:DOING").assertIsSelected()
        val horizontal=compose.onNodeWithTag("status-selector").fetchSemanticsNode().boundsInRoot
        assertTrue(horizontal.height<vertical.height);assertTrue(horizontal.width>vertical.width)
    }
    @Test fun horizontalFivePhasesWrapOnNarrowScreenWithLargeTextAndOfficialTheme() {
        compose.setContent {Arachn0deTheme {
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,2f),
                LocalAppearancePalette provides AppearanceTheme.DayGrey.palette) {
                Box(Modifier.requiredWidth(220.dp)) {StatusSelector(workStatusItems(),null,{},direction=StatusSelectorDirection.HORIZONTAL)}
            }
        }}
        val bounds=compose.onNodeWithTag("status-selector").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width<=220*compose.activity.resources.displayMetrics.density)
        com.r0ybt.arachn0de.domain.model.WorkState.entries.forEach {phase->
            compose.onNodeWithTag("status-filter:${phase.name}").assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            compose.onNodeWithText(phase.label).assertDoesNotExist()
        }
    }
    @Test fun appearanceDirectionAppliesToBothDomainsAtOnce() {
        val prefs=AppearancePreferences(compose.activity.getSharedPreferences("selector-direction-test",0))
        compose.setContent {Arachn0deTheme {CompositionLocalProvider(LocalAppearancePreferences provides prefs) {
            Column {
                StatusSelector(taskStatusItems(false),null,{},Modifier.testTag("tasks"))
                StatusSelector(workStatusItems(),null,{},Modifier.testTag("management"))
            }
        }}}
        val vertical=compose.onNodeWithTag("tasks").fetchSemanticsNode().boundsInRoot
        assertTrue(vertical.height>vertical.width)
        compose.runOnIdle {prefs.setStatusSelectorDirection(StatusSelectorDirection.HORIZONTAL)}
        val horizontal=compose.onNodeWithTag("tasks").fetchSemanticsNode().boundsInRoot
        val management=compose.onNodeWithTag("management").fetchSemanticsNode().boundsInRoot
        assertTrue(horizontal.width>horizontal.height);assertTrue(management.width>management.height)
        assertEquals(StatusSelectorDirection.HORIZONTAL,AppearancePreferences(compose.activity.getSharedPreferences("selector-direction-test",0)).settings.statusSelectorDirection)
    }

}
