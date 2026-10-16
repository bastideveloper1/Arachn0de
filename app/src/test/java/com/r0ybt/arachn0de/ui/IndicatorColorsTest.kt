package com.r0ybt.arachn0de.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class IndicatorColorsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val storage get()=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("indicator-test",0)
    @Before fun clear() {storage.edit().clear().commit()}
    @Test fun independentChoicesPersistAndResetOnlyColorsWithExactOriginalDefaults() {
        val preferences=AppearancePreferences(storage)
        assertTrue(preferences.settings.indicatorColors.isEmpty())
        customizableCardIndicators.forEachIndexed {index,indicator->preferences.setIndicatorColor(indicator.name,IndicatorColor.entries[index])}
        assertEquals(8,preferences.settings.indicatorColors.size)
        assertEquals(preferences.settings,AppearancePreferences(storage).settings)
        preferences.setIndicatorColor("notes",IndicatorColor.BLUE);preferences.setIndicatorColor("people",IndicatorColor.BLUE)
        assertEquals(IndicatorColor.BLUE,preferences.settings.indicatorColors["notes"])
        assertEquals(IndicatorColor.BLUE,preferences.settings.indicatorColors["people"])
        preferences.setTextSize(AppearanceTextSize.Large);preferences.resetIndicatorColors()
        assertTrue(AppearancePreferences(storage).settings.indicatorColors.isEmpty())
        assertEquals(AppearanceTextSize.Large,preferences.settings.textSize)
        assertEquals(Color(0xFFD6D6D6),IndicatorColor.WHITE.color)
    }
    @Test fun cardTintsUpdateImmediatelyWhileInactiveAndHiddenSignalsKeepOriginalBehavior() {
        val preferences=AppearancePreferences(storage)
        val ids=customizableCardIndicators.map {it.name}
        var active=emptyMap<String,Color>();var inactive=emptyMap<String,Color>()
        compose.setContent {Arachn0deTheme(preferences) {
            val colors=ids.associateWith {cardIndicatorColor(it,true)}
            val grey=ids.associateWith {cardIndicatorColor(it,false)}
            SideEffect {active=colors;inactive=grey}
            Column(Modifier.width(400.dp)) {
                CompactCardIndicators("empty")
                CompactCardIndicators("full",CompactCardFacts(people=setOf("p"),technologies=setOf("t"),notes=setOf("n"),tasks=setOf("x"),files=setOf("f"),due=true,today=true,high=true))
            }
        }}
        compose.runOnIdle {assertTrue(active.values.all {it==BlackWhite90.Text});assertTrue(inactive.values.all {it==BlackWhite90.Inactive})}
        ids.forEachIndexed {index,id->compose.runOnIdle {preferences.setIndicatorColor(id,IndicatorColor.entries[index])}}
        compose.runOnIdle {ids.forEachIndexed {index,id->assertEquals(IndicatorColor.entries[index].color,active[id]);assertEquals(BlackWhite90.Inactive,inactive[id])}}
        ids.forEach {compose.onNodeWithTag("card-signal:full:$it").assertExists()}
        listOf("tasks","files","today","high").forEach {compose.onNodeWithTag("card-signal:empty:$it").assertDoesNotExist()}
        listOf("people","technologies","notes","due").forEach {compose.onNodeWithTag("card-signal:empty:$it").assertExists()}
        compose.runOnIdle {preferences.resetIndicatorColors()}
        compose.runOnIdle {assertTrue(active.values.all {it==BlackWhite90.Text})}
    }
    @Test fun settingsDrawerKeepsOneRenamedDesignDestination() {
        var opened=false
        compose.setContent {Arachn0deTheme {AppIdentityDrawer({},onAppearance={opened=true})}}
        compose.onNodeWithText("Ajustes").performScrollTo().performClick()
        compose.onAllNodesWithText("Diseño").assertCountEquals(1)
        compose.onNodeWithText("Apariencia").assertDoesNotExist()
        compose.onNodeWithText("Diseño").performScrollTo().performClick()
        assertTrue(opened)
    }
    @Test fun designSectionChoosesPaletteAndRestoresWithoutRemovingExistingOptions() {
        val preferences=AppearancePreferences(storage)
        compose.setContent {Arachn0deTheme(preferences) {AppearanceScreen({},preferences)}}
        compose.onNodeWithText("Diseño").assertExists()
        compose.onNodeWithText("Tema").assertExists()
        compose.onNodeWithText("Tamaño de texto").performScrollTo().assertExists()
        compose.onNodeWithTag("indicator-setting:notes").performScrollTo().performClick()
        compose.onNodeWithTag("indicator-color:VIOLET").performScrollTo().performClick()
        assertEquals(IndicatorColor.VIOLET,preferences.settings.indicatorColors["notes"])
        compose.onNodeWithTag("indicator-reset").performScrollTo().performClick()
        assertTrue(preferences.settings.indicatorColors.isEmpty())
    }
}
