package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.domain.model.CalendarDay
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w400dp-h850dp")
class DashboardSortPanelTest {
    @Test fun dashboardFavoriteOrderDoesNotChangeCalendarFilters() {
        val today=CalendarDay(2026,10,28)
        val calendar=ContextCalendarState(today).apply {selected=today;urgentOnly=true;favorites=true}
        var configuration by mutableStateOf(com.r0ybt.arachn0de.domain.model.SortConfiguration())
        val actions=GlobalBarActions()
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalGlobalNavigation provides GlobalNavigation({},true,true,actions=actions)) {
                Column {DashboardSortAccess(calendar,null,today,sortConfiguration={configuration},onSortChange={configuration=it});GlobalBottomBar()}
            }
        }}
        compose.onNodeWithContentDescription("Ordenar").performClick()
        compose.onNodeWithTag("sort-favorites").performClick()
        compose.onNodeWithTag("sort-favorites:LAST").performClick()
        compose.onNodeWithTag("dashboard-apply-filters").performClick()
        compose.onNodeWithContentDescription("Ordenar").performClick()
        compose.onNodeWithText("Favoritos: Favoritos abajo").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(calendar.urgentOnly);assertTrue(calendar.favorites);assertEquals(today,calendar.selected)
        }
        compose.onNodeWithTag("dashboard-clear-filters").performClick()
        compose.runOnIdle {
            assertFalse(calendar.filtering)
            assertEquals(com.r0ybt.arachn0de.domain.model.FavoriteOrder.LAST,configuration.favorites)
        }
    }
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun globalFilterOpensCalendarDirectlyAndPreservesFiltersAcrossReopening() {
        val today=CalendarDay(2026,10,28)
        val calendar=ContextCalendarState(today).apply {selected=today;mode=0}
        val actions=GlobalBarActions()
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalGlobalNavigation provides GlobalNavigation({},true,true,actions=actions)) {
                Column {DashboardSortAccess(calendar,null,today);GlobalBottomBar()}
            }
        }}
        compose.onNodeWithContentDescription("Filtrar").assertIsEnabled().performClick()
        compose.onNodeWithTag("context-calendar").assertIsDisplayed()
        compose.onNodeWithText("Orden manual").assertDoesNotExist()
        compose.onNodeWithTag("sort-criteria").assertDoesNotExist()
        compose.onNodeWithTag("context-calendar-fold").performClick()
        compose.onNodeWithText("Urgentes").performScrollTo().performClick()
        compose.onNodeWithText("Solo urgentes pendientes").performClick()
        compose.onNodeWithText("Favoritos").performScrollTo().performClick()
        compose.onNodeWithText("Solo favoritos").performClick()
        compose.runOnIdle {assertEquals(today,calendar.selected);assertTrue(calendar.urgentOnly);assertTrue(calendar.favorites);assertTrue(calendar.filtering)}
        compose.onNodeWithTag("dashboard-apply-filters").performClick()
        compose.onNodeWithContentDescription("Filtrar").performClick()
        compose.onNodeWithText("Solo urgentes pendientes").assertExists()
        compose.onNodeWithText("Solo favoritos").assertExists()
        compose.onNodeWithTag("context-calendar-attention").assertDoesNotExist()
        compose.onNodeWithTag("context-calendar-favorites").assertDoesNotExist()
        compose.onNodeWithTag("dashboard-clear-filters").assertIsDisplayed().performClick()
        compose.runOnIdle {assertFalse(calendar.filtering);assertNull(calendar.selected)}
    }
}
