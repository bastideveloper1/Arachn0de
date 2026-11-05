package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28],qualifiers="w320dp-h800dp")
class AlternatingBottomBarTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun switchRetainsFocusAndState() {
        var disposals=0
        compose.setContent {Arachn0deTheme {
            val state=rememberBottomModulesState()
            CompositionLocalProvider(LocalBottomModulesState provides state,
                LocalGlobalNavigation provides GlobalNavigation({},false,true)) {
                Column(Modifier.width(320.dp)) {
                    Text("Audio y Pomodoro",Modifier.height(48.dp).testTag("temporary-controls"))
                    Text("Panel abierto",Modifier.testTag("open-panel"))
                    AlternatingBottomBar {
                        var text by remember {mutableStateOf("")}
                        DisposableEffect(Unit) {onDispose {disposals++}}
                        OutlinedTextField(text,{text=it},modifier=Modifier.testTag("retained-editor"))
                    }
                    GlobalBottomBar()
                }
            }
        }}
        compose.onNodeWithTag("global-bottom-bar").assertDoesNotExist()
        compose.onNodeWithTag("retained-editor").performClick().performTextInput("Borrador intacto")
        compose.onNodeWithContentDescription("Mostrar barra global").performClick()
        compose.onNodeWithTag("global-bottom-bar").assertIsDisplayed()
        compose.onNodeWithTag("retained-editor").assertDoesNotExist()
        val toggle=compose.onNodeWithTag("bottom-bar-switch").fetchSemanticsNode()
        assertFalse(toggle.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text))
        assertTrue(toggle.boundsInRoot.width>=48*compose.density.density-1)
        compose.onNodeWithTag("temporary-controls").assertIsDisplayed()
        compose.onNodeWithTag("open-panel").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mostrar barra contextual").performClick()
        compose.onNodeWithTag("global-bottom-bar").assertDoesNotExist()
        compose.onNodeWithTag("retained-editor").assertTextContains("Borrador intacto").assertIsFocused()
        compose.runOnIdle {assertEquals(0,disposals)}
    }

    @Test fun overflowKeepsAllActions() {
        val calls=mutableListOf<String>()
        val labels=listOf("Contenido completo","Crear elemento","Opciones adicionales")
        compose.setContent {Arachn0deTheme {
            val density=LocalDensity.current
            val state=rememberBottomModulesState()
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f),LocalBottomModulesState provides state,
                LocalProjectLock provides ProjectLockAction(true) {calls.add("Bloquear")},
                LocalGlobalNavigation provides GlobalNavigation({calls.add("Menú")},false,true,search={calls.add("Buscar")},actions=remember {GlobalBarActions()})) {
                RegisterGlobalSort(true) {calls.add("Ordenar")}
                Column(Modifier.width(240.dp)) {
                    NavigationBarFrame("test-context-bar") {
                        AlternatingBottomBar {CompactBarActions(labels.map {label->BottomBarAction(label,Icons.Default.Add) {calls.add(label)}})}
                    }
                    GlobalBottomBar()
                }
            }
        }}
        fun choose(label:String) {
            if(compose.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty())compose.onNodeWithTag("bottom-bar-overflow").performClick()
            compose.onNodeWithText(label).performClick()
        }
        labels.forEach(::choose)
        compose.onNodeWithContentDescription("Mostrar barra global").performClick()
        compose.onNodeWithTag("test-context-bar").assertIsNotDisplayed()
        listOf("Bloquear","Ordenar","Buscar","Menú").forEach(::choose)
        assertEquals(labels+listOf("Bloquear","Ordenar","Buscar","Menú"),calls)
        val toggle=compose.onNodeWithTag("bottom-bar-switch").fetchSemanticsNode().boundsInRoot
        val bar=compose.onNodeWithTag("global-bottom-bar").fetchSemanticsNode().boundsInRoot
        assertTrue(toggle.right<=bar.right+1);assertTrue(toggle.width>=48*compose.density.density-1)
        compose.onNodeWithContentDescription("Mostrar barra contextual").performClick()
        compose.onNodeWithTag("global-bottom-bar").assertDoesNotExist()
    }

    @Test fun selectionSurvivesContextReturn() {
        var contextual by mutableStateOf(true)
        var globalAvailable by mutableStateOf(true)
        compose.setContent {Arachn0deTheme {
            val state=rememberBottomModulesState()
            CompositionLocalProvider(LocalBottomModulesState provides state,
                LocalGlobalNavigation provides GlobalNavigation({},false,globalAvailable)) {
                Column {
                    if(contextual)AlternatingBottomBar {Text("Contexto",Modifier.height(48.dp))}
                    GlobalBottomBar()
                }
            }
        }}
        compose.onNodeWithContentDescription("Mostrar barra global").performClick()
        compose.runOnIdle {contextual=false}
        compose.onNodeWithTag("global-bottom-bar").assertIsDisplayed()
        compose.onNodeWithTag("bottom-bar-switch").assertDoesNotExist()
        compose.runOnIdle {contextual=true}
        compose.onNodeWithTag("global-bottom-bar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mostrar barra contextual").performClick()
        compose.runOnIdle {globalAvailable=false}
        compose.onNodeWithText("Contexto").assertIsDisplayed()
        compose.onNodeWithTag("bottom-bar-switch").assertDoesNotExist()
    }
}
