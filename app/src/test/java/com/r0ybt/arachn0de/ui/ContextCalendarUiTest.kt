package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w320dp-h850dp")
class ContextCalendarUiTest {
    @Test fun headerSwipesChangeMonthAndKeepSelectionFiltersAndBounds() {
        val today=CalendarDay(2028,1,31)
        val selected=CalendarDay(2028,1,7)
        val state=ContextCalendarState(today).apply {
            mode=2;expanded=2;this.selected=selected;anchor=today
            urgentOnly=true;favorites=true;dueCondition=AttentionReason.OVERDUE
        }
        compose.setContent {Arachn0deTheme {ContextCalendar(state,null,today)}}
        val header=compose.onNodeWithTag("context-calendar-toolbar")
        header.performTouchInput {swipeLeft()}
        compose.runOnIdle {
            assertEquals(CalendarDay(2028,2,29),state.anchor)
            assertEquals(selected,state.selected);assertTrue(state.urgentOnly);assertTrue(state.favorites)
            assertEquals(AttentionReason.OVERDUE,state.dueCondition)
        }
        compose.onNodeWithTag("context-calendar-period-close").assertDoesNotExist()
        header.performTouchInput {swipeRight()}
        compose.runOnIdle {assertEquals(CalendarDay(2028,1,29),state.anchor);state.anchor=CalendarDay(2028,12,31)}
        header.performTouchInput {swipeLeft()}
        compose.runOnIdle {assertEquals(CalendarDay(2029,1,31),state.anchor);state.anchor=CalendarDay(1,1,1)}
        header.performTouchInput {swipeRight()}
        compose.runOnIdle {assertEquals(CalendarDay(1,1,1),state.anchor);state.anchor=CalendarDay(9999,12,31)}
        header.performTouchInput {swipeLeft()}
        compose.runOnIdle {assertEquals(CalendarDay(9999,12,31),state.anchor);assertEquals(selected,state.selected)}
    }

    @Test @Config(qualifiers="w480dp-h1000dp") fun gridSwipeCancelsDayTapAndOverflowKeepsHorizontalScroll() {
        val today=CalendarDay(2028,1,10)
        val state=ContextCalendarState(today).apply {mode=2;expanded=2;selected=today}
        var width by androidx.compose.runtime.mutableStateOf(420f)
        compose.setContent {Arachn0deTheme {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(androidx.compose.ui.unit.Dp(width))) {ContextCalendar(state,null,today)}
        }}
        val grid=compose.onNodeWithTag("context-calendar-grid")
        grid.performTouchInput {swipeLeft()}
        compose.runOnIdle {assertEquals(CalendarMonth(2028,2),state.anchor.calendarMonth);assertEquals(today,state.selected);width=280f}
        grid.performTouchInput {swipeLeft()}
        compose.runOnIdle {assertEquals(CalendarMonth(2028,2),state.anchor.calendarMonth);assertEquals(today,state.selected)}
        val scroll=grid.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange]
        assertTrue(scroll.value()>0f)
        compose.onNodeWithTag("context-calendar-toolbar").performTouchInput {swipeRight()}
        compose.runOnIdle {assertEquals(CalendarMonth(2028,1),state.anchor.calendarMonth);assertEquals(today,state.selected)}
    }
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun todayRollsOverInBothPresentations() {
        var today by androidx.compose.runtime.mutableStateOf(CalendarDay(2026,12,31))
        val state=ContextCalendarState(today).apply {mode=0;urgentOnly=true;favorites=true}
        compose.setContent {Arachn0deTheme {ContextCalendar(state,null,today)}}
        compose.onNodeWithTag("context-calendar-collapsed-date").assertTextContains("31 de diciembre de 2026",substring=true)
        compose.runOnIdle {today=CalendarDay(2027,1,1)}
        compose.onNodeWithTag("context-calendar-collapsed-date").assertTextContains("1 de enero de 2027",substring=true)
        compose.onNodeWithTag("context-calendar-fold").performClick()
        compose.onNodeWithTag("context-calendar-day:2027-1-1").assertExists()
        compose.runOnIdle {
            assertEquals(today,state.anchor);assertNull(state.selected)
            assertTrue(state.urgentOnly);assertTrue(state.favorites)
        }
        // An explicit day remains fixed even if it was today when selected.
        compose.onNodeWithTag("context-calendar-day:2027-1-1").performClick()
        compose.runOnIdle {today=CalendarDay(2027,1,2)}
        compose.runOnIdle {assertEquals(CalendarDay(2027,1,1),state.selected)}
        compose.onNodeWithTag("context-calendar-today").performClick()
        compose.runOnIdle {today=CalendarDay(2027,1,3)}
        compose.onNodeWithTag("context-calendar-day:2027-1-3").assertIsSelected()
        compose.runOnIdle {assertEquals(today,state.anchor);assertEquals(today,state.selected)}
    }

    @Test fun midnightResumeUsesExistingClock() {
        val zone=TimeZone.getDefault()
        val before=localTaskInstant(CalendarDates.labelInstant(CalendarDay(2026,12,31)),23,59,zone)
        val after=localTaskInstant(CalendarDates.labelInstant(CalendarDay(2027,1,1)),0,1,zone)
        val clock=java.util.concurrent.atomic.AtomicLong(before)
        val owner=object:androidx.lifecycle.LifecycleOwner {
            val registry=androidx.lifecycle.LifecycleRegistry(this)
            override val lifecycle:androidx.lifecycle.Lifecycle get()=registry
        }
        val store=ContextCalendarStore()
        compose.runOnIdle {owner.registry.currentState=androidx.lifecycle.Lifecycle.State.RESUMED}
        compose.setContent {Arachn0deTheme {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLifecycleOwner provides owner) {
                val now=com.r0ybt.arachn0de.ui.state.rememberTaskScreenNow(emptyList(),clock::get,onRefresh={},dayOnly=true)
                val today=CalendarDates.localDay(now,zone)
                val state=store.scope("root",today)
                ContextCalendar(state,null,today)
            }
        }}
        compose.runOnIdle {
            store.scope("root",CalendarDay(2026,12,31)).mode=0
            owner.registry.currentState=androidx.lifecycle.Lifecycle.State.CREATED
            clock.set(after)
        }
        compose.runOnIdle {owner.registry.currentState=androidx.lifecycle.Lifecycle.State.RESUMED}
        compose.onNodeWithTag("context-calendar-collapsed-date").assertTextContains("1 de enero de 2027",substring=true)
        compose.runOnIdle {owner.registry.currentState=androidx.lifecycle.Lifecycle.State.DESTROYED}
    }
    @Test fun monthEndSelectionKeepsDateAndResultsAligned() {
        val today=CalendarDay(2028,1,31)
        val zone=TimeZone.getTimeZone("America/Santiago")
        val due=localTaskInstant(CalendarDates.labelInstant(CalendarDay(2028,2,29)),23,30,zone)
        val task=Node("leap","p",null,"Leap task","",false,0,1,1,false,dueAt=due)
        val index=ContextCalendarIndex(NodeTreeSnapshot(listOf(task)),zone)
        val state=ContextCalendarState(today).apply {mode=2;expanded=2;selected=today}
        compose.setContent {Arachn0deTheme {ContextCalendar(state,index,today)}}
        compose.onNodeWithTag("context-calendar-period").performClick()
        compose.onNodeWithTag("context-calendar-next").performClick()
        compose.onNodeWithTag("context-calendar-period-close").performClick()
        compose.onNodeWithTag("context-calendar-day:2028-2-30").assertDoesNotExist()
        compose.onNodeWithTag("context-calendar-day:2028-2-29")
            .assertContentDescriptionContains("1 tareas",substring=true).performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(CalendarDay(2028,2,29),state.anchor)
            assertEquals(state.anchor,state.selected)
            assertEquals(listOf(task),index.tasksOn(state.selected!!))
        }
        repeat(2) {compose.onNodeWithTag("context-calendar-fold").performClick()}
        compose.onNodeWithTag("context-calendar-day:2028-2-29").assertIsSelected()
        compose.onNodeWithTag("context-calendar-period").performClick()
        compose.onNodeWithTag("context-calendar-next").performClick()
        compose.onNodeWithTag("context-calendar-previous").performClick()
        compose.onNodeWithTag("context-calendar-period-close").performClick()
        compose.onNodeWithTag("context-calendar-day:2028-2-29").assertIsSelected()
    }
    @Test fun todayIsIdempotentAndPreservesPresentationAndFilters() {
        val today=CalendarDay(2026,10,28)
        val state=ContextCalendarState(today).apply {
            selected=CalendarDay(2026,9,1);anchor=selected!!
            mode=2;expanded=2;urgentOnly=true;favorites=true
        }
        compose.setContent {Arachn0deTheme {ContextCalendar(state,null,today)}}
        repeat(2) {
            compose.onNodeWithTag("context-calendar-today").performClick()
            compose.runOnIdle {
                assertEquals(today,state.selected);assertEquals(today,state.anchor)
                assertEquals(2,state.mode);assertEquals(2,state.expanded)
                assertTrue(state.urgentOnly);assertTrue(state.favorites)
            }
        }
        compose.runOnIdle {state.selected=null;state.anchor=CalendarDay(2020,1,1);state.urgentOnly=false;state.favorites=false}
        compose.onNodeWithTag("context-calendar-today").performClick()
        compose.runOnIdle {assertEquals(today,state.anchor);assertNull(state.selected);assertFalse(state.filtering);assertEquals(2,state.mode)}
    }

    @Test fun calendarHasNoCardFilterControlsAcrossThemes() {
        val today=CalendarDay(2026,10,27)
        val state=ContextCalendarState(today).apply {urgentOnly=true;favorites=true}
        val prefs=AppearancePreferences(compose.activity.getSharedPreferences("calendar-only",0))
        compose.setContent {Arachn0deTheme(prefs) {ContextCalendar(state,null,today)}}
        for(theme in listOf(AppearanceTheme.Arachn0de,AppearanceTheme.Graphite)) {
            compose.runOnIdle {prefs.setTheme(theme)}
            compose.onNodeWithTag("context-calendar-attention").assertDoesNotExist()
            compose.onNodeWithTag("context-calendar-favorites").assertDoesNotExist()
            compose.onNodeWithTag("context-calendar-period").assertIsDisplayed()
            compose.runOnIdle {assertTrue(state.urgentOnly);assertTrue(state.favorites)}
        }
    }
    @Test fun weekMonthFoldFilterAndRestorationWorkInLightAndDark() {
        val today=CalendarDay(2026,12,31)
        val index=ContextCalendarIndex(NodeTreeSnapshot(emptyList()),TimeZone.getTimeZone("UTC"))
        val prefs=AppearancePreferences(compose.activity.getSharedPreferences("calendar-test",0))
        val restore=StateRestorationTester(compose)
        lateinit var store:ContextCalendarStore
        restore.setContent {Arachn0deTheme(prefs) {
            store=rememberContextCalendarStore()
            val density=androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.5f)) {
                ContextCalendar(store.scope("root",today),index,today)
            }
        }}
        compose.onNodeWithTag("context-calendar-day:${today.key}").performClick()
        compose.onNodeWithTag("context-calendar-period").performClick()
        compose.onNodeWithTag("context-calendar-count").assertExists()
        compose.onNodeWithTag("context-calendar-period-close").performClick()
        compose.onNodeWithTag("context-calendar-mode").performClick()
        compose.runOnIdle {assertEquals(2,store.scope("root",today).mode)}
        compose.onNodeWithTag("context-calendar-period").performClick()
        compose.onNodeWithTag("context-calendar-next").performClick()
        compose.onNodeWithTag("context-calendar-period-close").performClick()
        compose.runOnIdle {assertEquals(CalendarMonth(2027,1),store.scope("root",today).anchor.calendarMonth);assertEquals(today,store.scope("root",today).selected)}
        compose.onNodeWithTag("context-calendar-fold").performClick()
        // Presentation is committed on Dispatchers.IO; Compose idleness alone does not await it.
        compose.waitUntil {compose.onAllNodesWithTag("context-calendar-collapsed-date").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("context-calendar-collapsed-date").assertIsDisplayed()
        compose.onNodeWithTag("context-calendar-mode").assertDoesNotExist()
        compose.onNodeWithTag("context-calendar-fold").performClick()
        compose.waitUntil {compose.onAllNodesWithTag("context-calendar-mode").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("context-calendar-mode").assertExists()
        compose.runOnIdle {store.scope("root",today).urgentOnly=true;store.scope("root",today).favorites=true;store.scope("root",today).dueCondition=AttentionReason.OVERDUE}
        compose.runOnIdle {assertTrue(store.scope("root",today).urgentOnly);assertTrue(store.scope("root",today).favorites)}
        compose.runOnIdle {store.scope("root",today).urgentOnly=false}
        compose.runOnIdle {assertFalse(store.scope("root",today).urgentOnly);assertTrue(store.scope("root",today).favorites);assertEquals(today,store.scope("root",today).selected)}
        compose.onNodeWithTag("context-calendar-fold").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.runOnIdle {assertEquals(today,store.scope("root",today).selected);assertEquals(0,store.scope("root",today).mode);assertTrue(store.scope("root",today).favorites);assertEquals(AttentionReason.OVERDUE,store.scope("root",today).dueCondition)}
        compose.onNodeWithTag("context-calendar-fold").performClick()
        compose.onNodeWithTag("context-calendar-period").performClick()
        compose.onNodeWithTag("context-calendar-clear").performClick()
        compose.runOnIdle {assertNull(store.scope("root",today).selected)}
        compose.onNodeWithTag("context-calendar-mode").performClick()
        compose.runOnIdle {assertEquals(1,store.scope("root",today).mode);prefs.setTheme(AppearanceTheme.DayGrey)}
        compose.onNodeWithTag("context-calendar").assertExists()
        compose.runOnIdle {prefs.setTheme(AppearanceTheme.Dark)}
        compose.onNodeWithTag("context-calendar").assertExists()
    }
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun dateControlsRemainVisibleWithoutCardFiltersAtNarrowWidthsAndLargeFonts() {
        var width by androidx.compose.runtime.mutableStateOf(320f)
        var scale by androidx.compose.runtime.mutableStateOf(1f)
        val today=CalendarDay(2026,10,28)
        val state=ContextCalendarState(today)
        compose.setContent {Arachn0deTheme {
            val density=androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,scale)) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(androidx.compose.ui.unit.Dp(width))) {ContextCalendar(state,null,today)}
            }
        }}
        for(w in listOf(280f,320f)) for(font in listOf(1f,1.5f,2f)) {
            compose.runOnIdle {width=w;scale=font}
            val toolbar=compose.onNodeWithTag("context-calendar-toolbar").fetchSemanticsNode().boundsInRoot
            assertEquals(48*compose.density.density,toolbar.height,1f)
            val controls=listOf("period","fold")
            val bounds=controls.map {tag->
                val node=compose.onNodeWithTag("context-calendar-$tag").assertIsDisplayed().assertWidthIsAtLeast(androidx.compose.ui.unit.Dp(48f)).assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
                node.fetchSemanticsNode().boundsInRoot.also {assertTrue(it.left>=toolbar.left && it.right<=toolbar.right+1)}
            }
            bounds.zipWithNext().forEach {(a,b)->assertTrue(a.right<=b.left+1)}
            compose.onNodeWithTag("context-calendar-favorites").assertDoesNotExist()
            compose.onNodeWithTag("context-calendar-attention").assertDoesNotExist()
        }
    }

}
