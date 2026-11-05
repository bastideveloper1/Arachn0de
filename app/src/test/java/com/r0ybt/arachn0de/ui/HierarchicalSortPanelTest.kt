package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28],qualifiers="w400dp-h850dp")
class HierarchicalSortPanelTest {
    @Test fun favoriteDirectionsAndLayerPresentationSurviveReopeningIndependently() {
        val preferences=com.r0ybt.arachn0de.ui.state.NodeSortPreferences()
        var open by mutableStateOf(true)
        compose.setContent {Arachn0deTheme {
            if(open) NodeSortMenu(preferences.configuration("p:project-root"),{preferences.configure("p:project-root",it)},
                {open=false},onApply={it(null)})
        }}
        val layers=compose.onNodeWithTag("sort-layers-first")
        layers.assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("sort-check:STATE").performScrollTo().performClick()
        layers.assertIsOn()
        val originalRules=preferences.configuration("p:project-root").rules
        for(choice in listOf(FavoriteOrder.FIRST,FavoriteOrder.LAST,FavoriteOrder.NONE)) {
            compose.onNodeWithTag("sort-favorites").performClick()
            compose.onNodeWithTag("sort-favorites:${choice.name}").performClick()
            compose.runOnIdle {
                val value=preferences.configuration("p:project-root")
                assertEquals(choice,value.favorites);assertTrue(value.layersFirst)
                assertEquals(originalRules,value.rules)
                open=false
            }
            compose.waitForIdle()
            compose.runOnIdle {open=true}
            compose.onNodeWithTag("sort-layers-first").assertIsOn()
            compose.onNodeWithTag("sort-check:STATE").assertIsOn()
            compose.onNodeWithText("Favoritos: ${choice.label}").assertIsDisplayed()
            compose.onNodeWithTag("sort-apply").assertIsDisplayed()
        }
    }
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun checkboxDirectionAutosaveAndReapplyAreIndependentAndTwoStateCannotReverse() {
        var value by mutableStateOf(SortConfiguration())
        var changes=0;var applies=0;var three by mutableStateOf(true)
        compose.setContent {Arachn0deTheme {NodeSortMenu(value,{value=it;changes++},{},onApply={done->applies++;done(null)},threeStates=three)}}
        compose.onNodeWithTag("sort-rule:STATE").performClick()
        compose.runOnIdle {assertFalse(value.rules.first().reverse);assertEquals(0,changes)}
        compose.onNodeWithTag("sort-check:STATE").performClick()
        compose.runOnIdle {assertTrue(value.rules.first().enabled);assertFalse(value.rules.first().reverse);assertFalse(value.manual);assertEquals(1,changes);assertEquals(0,applies)}
        compose.onNodeWithTag("sort-rule:STATE").performClick()
        compose.runOnIdle {assertTrue(value.rules.first().reverse);assertEquals(2,changes)}
        compose.onNodeWithTag("sort-apply").performClick();compose.onNodeWithTag("sort-apply").performClick()
        compose.runOnIdle {assertEquals(2,applies);assertEquals(2,changes);three=false}
        compose.onNodeWithTag("sort-rule:STATE").performClick()
        compose.runOnIdle {assertEquals(2,changes)}
        compose.onNodeWithText("Pendientes primero").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").assertDoesNotExist();compose.onNodeWithText("Confirmar").assertDoesNotExist()
    }
    @Test fun switchingSortAndFilterKeepsSelectionsAndDoesNotApplySort() {
        var value by mutableStateOf(SortConfiguration())
        val filters=ScopeFilters()
        var applies=0
        compose.setContent {Arachn0deTheme {NodeSortMenu(value,{value=it},{},onApply={applies++;it(null)},
            filters=filters,people=emptyList(),tags=listOf(Tag("a","Trabajo","trabajo")),onInherit={},owns=false)}}
        compose.onNodeWithTag("sort-inherit").assertIsToggleable()
        compose.onNodeWithTag("sort-check:STATE").performClick()
        compose.onNodeWithTag("sort-filter-toggle").performClick()
        compose.onNodeWithText("Prioridad",substring=false).performClick()
        compose.onNodeWithText("Prioritario").performClick()
        compose.onNodeWithTag("sort-filter-toggle").performClick()
        compose.onNodeWithTag("sort-check:STATE").assertIsOn()
        compose.runOnIdle {assertEquals(Priority.HIGH,filters.priority);assertEquals(0,applies)}
        compose.onNodeWithTag("sort-filter-toggle").performClick()
        compose.onNodeWithText("Prioritario").assertExists()
        compose.onNodeWithText("Etiqueta",substring=false).performClick()
        compose.onNodeWithTag("tag-choice:a").performScrollTo().performClick()
        compose.runOnIdle {assertEquals("Etiqueta seleccionada antes de volver","a",filters.tag)}
        compose.onNodeWithContentDescription("Volver").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals("a",filters.tag)}
        compose.onNodeWithTag("sort-filter-toggle").performClick()
        compose.onNodeWithTag("sort-apply").performClick()
        compose.runOnIdle {assertEquals(1,applies)}
    }
    @Test fun longPressReordersUsingExistingDragAndSimpleTouchDoesNotMoveHierarchy() {
        var value by mutableStateOf(SortConfiguration())
        compose.setContent {Arachn0deTheme {NodeSortMenu(value,{value=it},{},onApply={it(null)},threeStates=true)}}
        val listBounds=compose.onNodeWithTag("sort-criteria").fetchSemanticsNode().boundsInRoot
        val bounds=compose.onNodeWithTag("sort-rule:STATE").fetchSemanticsNode().boundsInRoot
        val start=Offset(bounds.width*.8f,bounds.center.y-listBounds.top)
        val end=start+Offset(0f,bounds.height*1.5f)
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("sort-criteria").performTouchInput {down(start);advanceEventTime(700);moveTo(start)}
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("sort-rule:STATE").assert(hasStateDescription("Arrastrando"))
        repeat(6) {step->
            compose.onNodeWithTag("sort-criteria").performTouchInput {moveTo(start+(end-start)*((step+1)/6f),delayMillis=40)}
            compose.mainClock.advanceTimeBy(48)
        }
        compose.onNodeWithTag("sort-criteria").performTouchInput {up()}
        compose.mainClock.autoAdvance=true
        compose.waitForIdle()
        compose.runOnIdle {assertNotEquals(SortCriterion.STATE,value.rules.first().criterion);assertEquals(SortCriterion.entries.size,value.rules.map {it.criterion}.toSet().size)}
    }
    @Test fun compactPanelKeepsApplyAccessibleAcrossExistingThemes() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val preferences=AppearancePreferences(context.getSharedPreferences("sorting-themes-${java.util.UUID.randomUUID()}",0))
        var value by mutableStateOf(SortConfiguration())
        compose.setContent {Arachn0deTheme(preferences) {NodeSortMenu(value,{value=it},{},onApply={it(null)})}}
        for(theme in AppearanceTheme.entries) {
            compose.runOnIdle {preferences.setTheme(theme)}
            compose.onNodeWithTag("sort-apply").assertIsDisplayed().assertIsEnabled()
            val bounds=compose.onNodeWithTag("sort-apply").fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.height>=48*compose.density.density-1)
            compose.onNodeWithTag("sort-criteria").performScrollToIndex(0)
            compose.onNodeWithTag("sort-check:STATE").assertIsDisplayed()
        }
    }
    @Test fun filterSelectorsKeepSemanticsAndUseBottomPanelNavigation() {
        val state=ScopeFilters().apply {open=true}
        compose.setContent {Arachn0deTheme {ScopeFiltersDialog(state,emptyList(),listOf(Tag("a","Trabajo","trabajo")))}}
        compose.onNodeWithText("Etiqueta",substring=false).performClick()
        compose.onNodeWithText("Trabajo").performClick()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.runOnIdle {assertEquals("a",state.tag)}
        compose.onNodeWithText("Prioridad",substring=false).performClick()
        compose.onNodeWithText("Prioritario").performClick()
        compose.runOnIdle {assertEquals(Priority.HIGH,state.priority)}
        compose.onNodeWithText("Tiempo",substring=false).performClick()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Limpiar").performClick()
        compose.runOnIdle {assertFalse(state.active);assertFalse(state.open)}
    }
}
