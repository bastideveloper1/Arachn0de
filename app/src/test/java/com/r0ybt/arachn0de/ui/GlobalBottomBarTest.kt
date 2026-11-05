package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w320dp-h800dp")
class GlobalBottomBarTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun simpleSearchFocusesExistingFieldAndClearsWithoutOpeningResultsOrAdvancedMode() {
        var query by mutableStateOf("")
        var closed=false
        compose.setContent {Arachn0deTheme {ContextualSearchPanel(query,{query=it},{query="";closed=true})}}
        compose.onNodeWithTag("contextual-search-query").assertIsFocused().performTextInput("Documento")
        compose.runOnIdle {assertEquals("Documento",query)}
        compose.onNodeWithText("Búsqueda avanzada").assertDoesNotExist()
        compose.onNodeWithTag("contextual-search-results").assertDoesNotExist()
        compose.onNodeWithText("Limpiar").performClick()
        compose.runOnIdle {assertEquals("",query);assertFalse(closed)}
        compose.onNodeWithTag("contextual-search-query").performTextInput("Otra")
        compose.onNodeWithText("Cerrar").performClick()
        compose.runOnIdle {assertEquals("",query);assertTrue(closed)}
    }
    @Test fun narrowBarKeepsFourTargetsInOrderAndLeavesTemporaryModulesAboveIt() {
        val actions=GlobalBarActions()
        var locks=0;var sorts=0;var searches=0;var menus=0
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalProjectLock provides ProjectLockAction(true) {locks++},
                LocalGlobalNavigation provides GlobalNavigation({menus++},true,true,search={searches++},actions=actions)) {
                RegisterGlobalSort(true) {sorts++}
                Column(Modifier.width(320.dp)) {
                    HeaderBar({})
                    Text("Acciones contextuales",Modifier.height(48.dp).testTag("context-controls"))
                    Text("Audio y Pomodoro",Modifier.height(48.dp).testTag("temporary-modules"))
                    GlobalBottomBar()
                }
            }
        }}
        val controls=listOf("Bloquear","Ordenar","Buscar","Abrir menú")
        val bounds=controls.map {label->
            compose.onAllNodesWithContentDescription(label).assertCountEquals(1)
            compose.onNodeWithContentDescription(label).assertIsDisplayed().performClick().fetchSemanticsNode().boundsInRoot
        }
        bounds.forEach {assertTrue(it.width>=48*compose.density.density-1);assertTrue(it.height>=48*compose.density.density-1)}
        bounds.zipWithNext().forEach {(a,b)->assertTrue(a.right<=b.left);assertEquals(a.top,b.top,1f)}
        assertTrue(compose.onNodeWithTag("temporary-modules").fetchSemanticsNode().boundsInRoot.bottom<=bounds.first().top)
        compose.runOnIdle {assertEquals(listOf(1,1,1,1),listOf(locks,sorts,searches,menus))}
    }
}
