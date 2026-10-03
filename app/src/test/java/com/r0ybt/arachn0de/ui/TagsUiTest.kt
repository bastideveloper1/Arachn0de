package com.r0ybt.arachn0de.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.r0ybt.arachn0de.domain.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class TagsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun searchAmongTwoHundredTagsSelectAndClear() {
        var selected by mutableStateOf(emptySet<String>())
        compose.setContent { TagSelector((0 until 200).map { Tag("$it","Tag $it","tag $it") },selected,{ selected=it },single=true) }
        compose.onNodeWithText("Etiquetas: Todas").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("tag 199")
        compose.onNodeWithText("Tag 199").performClick()
        compose.runOnIdle { assertEquals(setOf("199"),selected) }
        compose.onNodeWithText("Todas las etiquetas").performClick()
        compose.runOnIdle { assertTrue(selected.isEmpty()) }
    }
    @Test fun selectionAndScopeSurviveRecreation() {
        val restorer = StateRestorationTester(compose)
        var editor: com.r0ybt.arachn0de.ui.state.EditorDraft? = null
        var scopes: ScopeFilterStore? = null
        restorer.setContent {
            val draft by rememberSaveable(stateSaver = com.r0ybt.arachn0de.ui.state.EditorDraft.Saver) { mutableStateOf<com.r0ybt.arachn0de.ui.state.EditorDraft?>(com.r0ybt.arachn0de.ui.state.EditorDraft(null,null,"T","")) }
            val store = rememberSaveable(saver=ScopeFilterStore.Saver) { ScopeFilterStore() }
            editor = draft; scopes = store
        }
        compose.runOnIdle { editor!!.tagIds = listOf("a","b"); scopes!!.scope("root").tag="a"; scopes!!.scope("layer").person="roy" }
        restorer.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(listOf("a","b"),editor!!.tagIds); assertEquals("a",scopes!!.scope("root").tag); assertEquals("roy",scopes!!.scope("layer").person) }
    }
    @Test fun scopeClearRestoresAllFourDimensions() {
        val state = ScopeFilters().apply { time=TimeFilter.TODAY; person="p"; completion=CompletionFilter.PENDING; tag="t"; open=true }
        compose.setContent { ScopeFiltersDialog(state,listOf(Person("p","Person")),listOf(Tag("t","Tag","tag"))) }
        compose.onNodeWithText("Limpiar filtros").performClick()
        compose.runOnIdle { assertFalse(state.active); assertFalse(state.open) }
    }
}
