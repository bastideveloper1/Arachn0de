package com.r0ybt.arachn0de.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class PriorityUiTest {
    @get:Rule val compose=createComposeRule()
    private fun node(id:String,priority:Priority,due:Long?=null)=Node(id,"p",null,id,"",false,0,1,1,false,dueAt=due,priority=priority)
    @Test fun compactSelectionSupportsAllAndClear() {
        val state=ScopeFilters(); compose.setContent { ScopeFiltersDialog(state,emptyList(),emptyList()) }
        compose.onNodeWithTag("priority-selector").performScrollTo().performClick(); compose.onNodeWithTag("priority-option:HIGH").performClick()
        compose.runOnIdle { assertEquals(Priority.HIGH,state.priority) }
        compose.onNodeWithText("Limpiar filtros").performClick(); compose.runOnIdle { assertNull(state.priority); assertFalse(state.active) }
    }
    @Test fun editorPriorityAndBatchAndFiltersSurviveRecreation() {
        val restorer=StateRestorationTester(compose); var editor:EditorDraft?=null; var batch:NodeBatchDraft?=null; var filters:ScopeFilterStore?=null; var calendar:CalendarFilterState?=null
        restorer.setContent {
            val e by rememberSaveable(stateSaver=EditorDraft.Saver) { mutableStateOf<EditorDraft?>(EditorDraft(null,null,"Title","")) }; editor=e
            val b by rememberSaveable(stateSaver=NodeBatchDraft.Saver) { mutableStateOf<NodeBatchDraft?>(NodeBatchDraft(null)) }; batch=b
            filters=rememberSaveable(saver=ScopeFilterStore.Saver) { ScopeFilterStore() }; calendar=rememberSaveable(saver=CalendarFilterState.Saver) { CalendarFilterState(CalendarDay(2026,10,5)) }
        }
        compose.runOnIdle { editor!!.priority=Priority.HIGH; editor!!.tagIds=listOf("t"); editor!!.responsibleIds=listOf("roy"); batch!!.dates.priority=Priority.LOW; filters!!.scope("root").priority=Priority.MEDIUM; calendar!!.priority=Priority.HIGH }
        restorer.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(Priority.HIGH,editor!!.priority); assertEquals(listOf("t"),editor!!.tagIds); assertEquals(listOf("roy"),editor!!.responsibleIds); assertEquals(Priority.LOW,batch!!.dates.priority); assertEquals(Priority.MEDIUM,filters!!.scope("root").priority); assertEquals(Priority.HIGH,calendar!!.priority) }
    }
    @Test fun attentionShowsReasonsContextPeopleAndTagsOncePerNodeAndReactsToCompletion() {
        var n by mutableStateOf(node("Bill",Priority.HIGH,99).copy(obligation=Obligation(15000,"CLP")))
        val tags=TagState(listOf(Tag("t","bug","bug")),mapOf("Bill" to setOf("t")))
        compose.setContent { AttentionScreen(AttentionSnapshot(NodeTreeSnapshot(listOf(n)),100,TimeZone.getTimeZone("UTC")),listOf(Project("p","Project","",0,1,1)),mapOf("Bill" to listOf(Person("roy","Roy"))),true,{}, {},tags,listOf(Person("roy","Roy"))) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("attention-task:Bill").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithTag("attention-task:Bill").assertCountEquals(1); compose.onNodeWithTag("attention-reasons:Bill", useUnmergedTree=true).assertTextEquals("Atrasada · Prioridad alta"); compose.onNodeWithText("bug", useUnmergedTree=true).assertExists(); compose.onNodeWithText("Project", useUnmergedTree=true).assertExists()
        compose.runOnIdle { n=n.copy(isCompleted=true) }
        compose.waitUntil(10000) { compose.onAllNodesWithText("No hay tareas que requieran atención.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("attention-task:Bill").assertDoesNotExist()
    }
    @Test fun attentionReusesPriorityFilterAndClearRestoresResults() {
        val ns=listOf(node("High",Priority.HIGH),node("Medium",Priority.MEDIUM,99)); val snapshot=AttentionSnapshot(NodeTreeSnapshot(ns),100,TimeZone.getTimeZone("UTC"))
        compose.setContent { AttentionScreen(snapshot,listOf(Project("p","Project","",0,1,1)),emptyMap(),true,{}, {}) }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("attention-task:High").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Filtros").performClick(); compose.onNodeWithTag("priority-selector").performScrollTo().performClick(); compose.onNodeWithTag("priority-option:HIGH").performClick(); compose.onNodeWithText("Aplicar").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("attention-task:Medium").fetchSemanticsNodes().isEmpty() }; compose.onNodeWithTag("attention-task:High").assertExists()
        compose.onNodeWithText("Filtros activos").performClick(); compose.onNodeWithText("Limpiar filtros").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("attention-task:Medium").fetchSemanticsNodes().isNotEmpty() }
    }
}
