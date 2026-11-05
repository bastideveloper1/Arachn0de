package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.*
import com.r0ybt.arachn0de.access.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w320dp-h850dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UnifiedSearchUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val now=java.time.Instant.parse("2026-10-25T12:00:00Z").toEpochMilli()
    private val project=Project("p","Núcleo","",0,1,2)
    private val nodes=List(30) {Node("n$it","p",null,"Tarea $it","Texto técnico",it==2,it,1,2,false,
        dueAt=if(it in 1..2)now-86_400_000L else null,priority=if(it==0) Priority.HIGH else Priority.NONE)}
    private val index=ElementSearch.index(listOf(project),nodes)
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {
        shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(32);block()
    }
    private fun fresh()=await {compose.onAllNodesWithTag("search-updating").fetchSemanticsNodes().isEmpty() && compose.onAllNodesWithTag("search-results").fetchSemanticsNodes().isNotEmpty()}
    @Test fun filtersFoldResetAndSpecializedAccessKeepResultsReachable() {
        var criteria by mutableStateOf(SearchCriteria());var query by mutableStateOf("");var mode by mutableStateOf("Buscar")
        var detailed=0;var opened:String?=null
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalElementAccessState provides AccessState(favorites=setOf("node:n0","node:n1","node:n2"))) {
                ElementSearchScreen(index,mode,query,{query=it},{mode=it},{},{opened=it.key},rememberLazyListState(),
                    criteria=criteria,onCriteria={criteria=it},nodes=nodes,onAttention={detailed++},clock={now})
            }
        }}
        fresh()
        compose.onNodeWithTag("search-mode:Buscar").assertIsSelected()
        compose.onNodeWithTag("search-filter:Favoritos").performScrollTo().performClick().assertIsSelected()
        await {compose.onAllNodesWithTag("access-result:project:p").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("search-filter:Atención").performScrollTo().performClick().assertIsSelected()
        await {compose.onAllNodesWithTag("access-result:node:n2").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("access-result:node:n1").assertExists().assertIsNotEnabled()
        compose.onNodeWithTag("search-open-attention").assertIsNotEnabled()
        compose.onNodeWithText("Los filtros se combinan (AND).").assertDoesNotExist()
        compose.onNodeWithTag("search-apply").performClick();fresh()
        compose.onNodeWithTag("search-unfold").assertExists()
        compose.onNodeWithTag("search-open-attention").performClick();assertEquals(1,detailed)
        compose.onNodeWithTag("access-result:node:n0").performClick();assertEquals("node:n0",opened)
        compose.onNodeWithTag("search-unfold").performClick()
        compose.onNodeWithTag("search-filter:Favoritos").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("search-reset").performClick()
        compose.runOnIdle {assertEquals(SearchCriteria(),criteria);assertEquals("",query);assertEquals("Buscar",mode)}
        fresh()
        compose.onNodeWithTag("search-apply").performClick()
        compose.onNodeWithTag("search-results").performScrollToNode(hasTestTag("access-result:node:n29"))
        compose.onNodeWithTag("access-result:node:n29").assertExists().assertIsEnabled()
    }
    @Test fun textClearExclusiveOrdersTagsAndEmptyRecoveryDoNotLoseSessionOnFold() {
        var criteria by mutableStateOf(SearchCriteria());var query by mutableStateOf("");var mode by mutableStateOf("Buscar")
        val tags=TagState(tags=listOf(Tag("tag","Casa","casa")),nodeIds=mapOf("n1" to setOf("tag")))
        val restoration=androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalElementAccessState provides AccessState(recent=listOf("node:n29"),counts=mapOf("node:n28" to 4))) {
                ElementSearchScreen(index,mode,query,{query=it},{mode=it},{},{},rememberLazyListState(),criteria=criteria,onCriteria={criteria=it},nodes=nodes,tags=tags,clock={now})
            }
        }}
        fresh()
        compose.onNodeWithTag("search-query").performTextInput("TEXTO TECNICO")
        await {compose.onAllNodesWithTag("access-result:project:p").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("search-clear").performClick();assertEquals("",query)
        compose.onNodeWithTag("search-mode:Recientes").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("search-mode:Frecuentes").performClick().assertIsSelected()
        compose.onNodeWithTag("search-mode:Recientes").assertIsNotSelected()
        compose.onNodeWithTag("search-tags").performScrollTo().performClick();compose.onNodeWithText("Casa").performClick()
        await {compose.onAllNodesWithTag("access-result:node:n1").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("search-query").performScrollTo().performTextInput("no coincide")
        compose.onNodeWithTag("search-apply").performClick()
        await {compose.onAllNodesWithText("Sin coincidencias").fetchSemanticsNodes().isNotEmpty()}
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("search-unfold").assertExists()
        compose.onNodeWithTag("search-empty-reset").performClick();fresh()
        assertEquals(SearchCriteria(),criteria);assertEquals("",query);assertEquals("Buscar",mode)
        compose.onNodeWithTag("search-unfold").performClick()
        compose.onNodeWithTag("search-mode:Buscar").performScrollTo().assertIsSelected()
    }
    @Test fun smallViewportLargeFontsThemesAndKeyboardApplyKeepActionsReachable() {
        val prefs=AppearancePreferences(compose.activity.getSharedPreferences("search-themes",0).apply {edit().clear().commit()})
        var height by mutableStateOf(480.dp)
        var query by mutableStateOf("")
        var hidden=0
        val keyboard=object:SoftwareKeyboardController {override fun show(){};override fun hide(){hidden++}}
        compose.setContent {Arachn0deTheme(prefs) {CompositionLocalProvider(LocalDensity provides Density(1f,1.7f),LocalSoftwareKeyboardController provides keyboard) {
            Box(Modifier.width(280.dp).height(height)) {
                ElementSearchScreen(index,"Buscar",query,{query=it},{},{},{},rememberLazyListState(),nodes=nodes,clock={now})
            }
        }}}
        for(theme in listOf(AppearanceTheme.Arachn0de,AppearanceTheme.Dark,AppearanceTheme.GreenBash)) {
            compose.runOnIdle {prefs.setTheme(theme)}
            compose.onNodeWithTag("search-query").performScrollTo().performClick()
            compose.onNodeWithTag("search-query").performTextInput("Tarea")
            // A reduced viewport reproduces the space available above an on-screen keyboard.
            compose.runOnIdle {height=300.dp}
            val panel=compose.onNodeWithTag("search-controls").fetchSemanticsNode().boundsInRoot
            val action=compose.onNodeWithTag("search-apply").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(action.width>=48f && action.height>=48f);assertTrue(action.bottom<=panel.bottom+.5f)
            compose.onNodeWithTag("search-filter:Recurrencias").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("search-apply").performClick()
            assertTrue(hidden>0);compose.onNodeWithTag("search-query").assertDoesNotExist()
            compose.runOnIdle {height=480.dp;query=""}
            compose.onNodeWithTag("search-unfold").performClick()
            compose.onNodeWithTag("search-reset").assertIsDisplayed()
        }
    }
    @Test fun togglingPaymentAndOtherFiltersKeepsChipBoundsAndActionsStable() {
        var criteria by mutableStateOf(SearchCriteria())
        compose.setContent {Arachn0deTheme {Box(Modifier.width(320.dp).height(600.dp)) {
            ElementSearchScreen(index,"Buscar","",{},{},{},{},rememberLazyListState(),criteria=criteria,onCriteria={criteria=it},nodes=nodes,clock={now})
        }}}
        for(label in listOf("Pagos","Recurrencias","Atención","Favoritos")) {
            val node=compose.onNodeWithTag("search-filter:$label").performScrollTo()
            val before=node.fetchSemanticsNode().boundsInRoot
            node.performClick().assertIsSelected()
            val after=node.fetchSemanticsNode().boundsInRoot
            assertEquals(before.width,after.width,.5f)
            assertEquals(before.top,after.top,.5f)
            compose.onNodeWithTag("search-apply").assertIsDisplayed()
            node.performClick().assertIsNotSelected()
        }
        compose.onNodeWithTag("search-tags").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("search-reset").assertIsDisplayed()
    }
    @Test fun paymentMetadataShowsPendingPersonAndDueDateWithOriginalNavigation() {
        val task=nodes.first().copy(title="Secadora, cuota 1",obligation=Obligation(25000,"CLP"),dueAt=now+86_400_000L)
        var opened:String?=null
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalBankPaid provides mapOf(task.id to 10000L)) {
                ElementSearchScreen(ElementSearch.index(listOf(project),listOf(task)),"Buscar","",{},{},{},{opened=it.nodeId},rememberLazyListState(),nodes=listOf(task),
                    peopleByNode=mapOf(task.id to listOf(Person("ana","Ana"))),clock={now})
            }
        }}
        fresh();compose.onNodeWithTag("search-apply").performClick()
        compose.onNode(hasText("Ana",substring=true) and hasText("Pendiente:",substring=true)).assertExists()
        compose.onNodeWithTag("access-result:node:${task.id}").performClick();assertEquals(task.id,opened)
    }
    @Test fun emptyDatabaseUsesBriefNoElementsState() {
        compose.setContent {Arachn0deTheme {ElementSearchScreen(emptyList(),"Buscar","",{},{},{},{},rememberLazyListState(),clock={now})}}
        await {compose.onAllNodesWithText("No hay elementos").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Sin coincidencias").assertDoesNotExist();compose.onNodeWithTag("search-empty-reset").assertDoesNotExist()
    }    @Test fun emptyRecurrenceResultsKeepTheExistingProgrammeManagementRoute() {
        var criteria by mutableStateOf(SearchCriteria(recurrences=true));var opened=0
        compose.setContent {Arachn0deTheme {
            ElementSearchScreen(index,"Buscar","",{},{},{},{},rememberLazyListState(),criteria=criteria,onCriteria={criteria=it},nodes=nodes,onRecurrences={opened++},clock={now})
        }}
        await {compose.onAllNodesWithText("Sin coincidencias").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("search-open-recurrences").assertExists().assertIsNotEnabled()
        compose.onNodeWithTag("search-apply").performClick()
        compose.onNodeWithTag("search-open-recurrences").assertIsEnabled().performClick();assertEquals(1,opened)
    }

}
