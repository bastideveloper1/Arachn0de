package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.SortConfiguration
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w320dp-h800dp")
class NavigationVisualIntegrationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun bounds(tag:String)=compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    @Test fun measuredFooterKeepsLastCardAboveTemporaryContextualAndGlobalBarsWithEqualMargins() {
        compose.setContent {Arachn0deTheme {
            val modules=rememberBottomModulesState()
            CompositionLocalProvider(LocalGlobalNavigation provides GlobalNavigation({},true,true),
                LocalBottomModulesState provides modules,LocalBottomModulesContent provides {_:Boolean->
                    Text("Audio",Modifier.fillMaxWidth().height(48.dp).testTag("temporary-audio"))
                    Text("Pomodoro",Modifier.fillMaxWidth().height(48.dp).testTag("temporary-timer"))
                    ContextualBottomPanels();GlobalBottomBar()
                }) {
                BottomModulesFrame {
                    LazyColumn(Modifier.weight(1f).testTag("cards")) {
                        items(30) {Text("Tarjeta $it",Modifier.fillMaxWidth().height(70.dp).testTag("card:$it"))}
                    }
                    ContextualBottomPanel {Row {NavigationBarAction("Crear",androidx.compose.material.icons.Icons.Default.Add) {}}}
                }
            }
        }}
        compose.onNodeWithTag("cards").performScrollToIndex(29)
        compose.onNodeWithTag("card:29").assertIsDisplayed()
        val card=bounds("card:29");val audio=bounds("temporary-audio");val timer=bounds("temporary-timer")
        val contextual=bounds("contextual-bottom-bar");val global=bounds("global-bottom-bar")
        assertTrue(card.bottom<=audio.top+1);assertTrue(audio.bottom<=timer.top+1)
        assertTrue(timer.bottom<=contextual.top+1);assertTrue(contextual.bottom<=global.top+1)
        assertEquals(contextual.left,global.left,1f);assertEquals(contextual.right,global.right,1f)
        listOf("Bloquear","Ordenar","Buscar","Menú").forEach {compose.onNodeWithText(it).assertIsDisplayed()}
    }
    @Test fun contextEditorSurvivesModalFooterTransferAndReturnsWithItsDraft() {
        val sourceMarker=staticCompositionLocalOf {"Contexto incorrecto"}
        compose.setContent {Arachn0deTheme {
            val modules=rememberBottomModulesState()
            CompositionLocalProvider(LocalBottomModulesState provides modules,LocalBottomModulesContent provides {_:Boolean->
                ContextualBottomPanels()
            }) {
                BottomModulesFrame {
                    Text("Contenido",Modifier.weight(1f))
                    CompositionLocalProvider(sourceMarker provides "Contexto de origen") {
                    ContextualBottomPanel {
                        Text(sourceMarker.current,Modifier.testTag("context-origin"))
                        var draft by remember {mutableStateOf("Borrador conservado")}
                        var editing by remember {mutableStateOf(false)}
                        androidx.compose.material3.TextButton(onClick={editing=true},modifier=Modifier.testTag("open-context-editor")) {Text(draft)}
                        if(editing) ModularPanelDialog(onDismissRequest={editing=false},title={Text("Editor contextual")},
                            text={androidx.compose.material3.TextField(draft,{draft=it},modifier=Modifier.testTag("context-draft"))},
                            confirmButton={androidx.compose.material3.TextButton(onClick={editing=false},modifier=Modifier.testTag("close-context-editor")) {Text("Cerrar")}})
                    }
                    }
                }
            }
        }}
        compose.onNodeWithTag("context-origin").assertTextEquals("Contexto de origen")
        compose.onNodeWithTag("open-context-editor").performClick()
        compose.onNodeWithTag("context-draft").assertIsDisplayed().performTextReplacement("Texto editado")
        compose.onNodeWithTag("close-context-editor").performClick()
        compose.onNodeWithTag("open-context-editor").assertIsDisplayed().assertTextContains("Texto editado")
        compose.onNodeWithTag("open-context-editor").performClick()
        compose.onNodeWithTag("context-draft").assertTextContains("Texto editado")
    }
    @Test fun sharedPanelHasEqualFooterButtonsInBothModesAndPreservesSelections() {
        var sort by mutableStateOf(SortConfiguration())
        val filters=ScopeFilters()
        compose.setContent {Arachn0deTheme {NodeSortMenu(sort,{sort=it},{},onApply={it(null)},filters=filters)}}
        val a=bounds("sort-filter-toggle");val b=bounds("sort-apply")
        assertEquals(a.width,b.width,1f);assertEquals(a.height,b.height,1f)
        compose.onNodeWithTag("sort-check:STATE").performClick()
        compose.onNodeWithTag("sort-filter-toggle").performClick()
        val buttons=listOf("sort-filter-toggle","sort-filter-clear","sort-apply").map(::bounds)
        buttons.forEach {assertEquals(buttons.first().width,it.width,1f);assertEquals(buttons.first().height,it.height,1f);assertEquals(buttons.first().top,it.top,1f)}
        compose.onNodeWithText("Limpiar").assertIsDisplayed()
        compose.onNodeWithText("Limpiar filtros").assertDoesNotExist()
        compose.onNodeWithTag("sort-filter-toggle").performClick()
        compose.onNodeWithTag("sort-check:STATE").assertIsOn()
    }
}
