package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.audio.AudioState
import com.r0ybt.arachn0de.pomodoro.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w400dp-h891dp")
class BottomModulesLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private val audio get()=app.security.current.value!!.audio
    private fun pump() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
    }
    @After fun close() {
        compose.runOnUiThread {
            val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()
        }
        pump();val process=Arachn0deApplication::class.java.getDeclaredField("processScope").apply {isAccessible=true}.get(app) as CoroutineScope
        runBlocking {process.coroutineContext[Job]?.cancelAndJoin()}
        audio.dispose()
        runBlocking {
            app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin()
            withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}
        }
        app.database.close()
    }
    private fun timer():PomodoroController {
        compose.waitUntil(15000) {pump();audio.canStartRecording}
        compose.runOnIdle {audio.state.value=AudioState(recording=true,durationMs=16_000)}
        return PomodoroController(compose.activity) {PomodoroTime(1_800_000_000_000,100_000,7)}.apply {start()}
    }
    private fun bounds(tag:String)=compose.onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
    private fun checkStack() {
        compose.onAllNodesWithTag("audio-bar").assertCountEquals(1)
        compose.onAllNodesWithTag("pomodoro-bar").assertCountEquals(1)
        val a=bounds("audio-bar");val p=bounds("pomodoro-bar");val controls=bounds("section-controls")
        assertEquals(a.left,p.left,1f);assertEquals(a.right,p.right,1f)
        assertEquals(compose.density.density,p.top-a.bottom,1f)
        assertTrue(controls.bottom<=a.top+1)
        compose.onAllNodesWithTag("global-bottom-bar").fetchSemanticsNodes().singleOrNull()?.let {bar->assertTrue(p.bottom<=bar.boundsInRoot.top+1)}
        assertEquals(48*compose.density.density,a.height,1f)
        assertEquals(48*compose.density.density,p.height,1f)
    }
    @Test fun specializedToolsHideGlobalBarAndRestoreItOnReturnToCores() {
        runBlocking {app.projectRepository.createProject("Inicio de navegación")}
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        fun awaitTag(tag:String) {compose.waitUntil(15000) {pump();compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}}
        awaitTag("dashboard-create-core")
        for((category,entry) in listOf("Herramientas" to "drawer-diagrams","Herramientas" to "drawer-wireframe",
            "Inicio" to "menu-item:Metro","Inicio" to "drawer-slots","Inicio" to "drawer-antisocial")) {
            compose.onNodeWithContentDescription("Abrir menú").performClick()
            compose.onNodeWithText(category).performClick()
            compose.onNodeWithTag(entry).performScrollTo().performClick()
            compose.waitUntil(15000) {pump();compose.onAllNodesWithTag("navigation-drawer").fetchSemanticsNodes().isEmpty()}
            compose.onNodeWithTag("global-bottom-bar").assertDoesNotExist()
            compose.onNodeWithContentDescription("Abrir menú").assertDoesNotExist()
            if(entry in setOf("drawer-diagrams","drawer-wireframe")) compose.onNodeWithText("Volver").performClick()
            else compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
            awaitTag("dashboard-create-core")
            compose.onNodeWithTag("global-bottom-bar").assertIsDisplayed()
        }
    }
    @Test fun actualDashboardAndCoreShareMeasuredNavigationFrames() {
        runBlocking {app.projectRepository.createProject("Núcleo de navegación")}
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        compose.waitUntil(15000) {pump();compose.onAllNodesWithTag("dashboard-create-core").fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodesWithTag("global-bottom-bar").assertCountEquals(1)
        compose.onAllNodesWithTag("contextual-bottom-bar").assertCountEquals(1)
        compose.onNodeWithContentDescription("Filtrar").assertIsEnabled()
        val dashboard=bounds("contextual-bottom-bar");val global=bounds("global-bottom-bar")
        assertEquals(dashboard.left,global.left,1f);assertEquals(dashboard.right,global.right,1f)
        compose.onNodeWithText("Núcleo de navegación").performClick()
        compose.waitUntil(15000) {pump();compose.onAllNodesWithTag("core-navigation-actions").fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodesWithTag("global-bottom-bar").assertCountEquals(1)
        compose.onAllNodesWithTag("contextual-bottom-bar").assertCountEquals(1)
        compose.onNodeWithContentDescription("Ordenar").assertIsEnabled()
        val contextual=bounds("contextual-bottom-bar");val bottom=bounds("global-bottom-bar")
        assertEquals(contextual.left,bottom.left,1f);assertEquals(contextual.right,bottom.right,1f)
        assertTrue(contextual.bottom<=bottom.top+1)
        compose.onNodeWithTag("core-context:Content").performClick()
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithTag("navigation-drawer").assertIsDisplayed()
        compose.onNodeWithTag("drawer-close-bottom").performClick()
        compose.onNodeWithTag("core-context:Content").assertIsDisplayed()
    }
    @Test fun allVisibilityAndMinimizationCombinationsKeepOrderAndIndependentStateAcrossSectionsAndRestore() {
        val timer=timer()
        var showAudio by mutableStateOf(false);var showTimer by mutableStateOf(false)
        var section by mutableStateOf("General")
        var wide by mutableStateOf(false)
        lateinit var modules:BottomModulesState
        val restore=StateRestorationTester(compose)
        restore.setContent {Arachn0deTheme {
            modules=rememberBottomModulesState()
            val data by timer.state.collectAsState()
            CompositionLocalProvider(LocalGlobalNavigation provides GlobalNavigation({},true,true),LocalBottomModulesState provides modules,LocalBottomModulesContent provides { _:Boolean ->
                if(showAudio) AudioBar(compact=modules.audioCompact,onCompactChange={modules.audioCompact=it})
                if(showTimer) PomodoroBar(timer,data,compact=modules.pomodoroCompact,onCompactChange={modules.pomodoroCompact=it},onExpand={})
                GlobalBottomBar()
            }) {
                Box(Modifier.requiredSize(if(wide) 390.dp else 320.dp,if(wide) 320.dp else 700.dp)) {
                    BottomModulesFrame {
                        Box(Modifier.weight(1f).fillMaxWidth().testTag("main")) {Text(section)}
                        Box(Modifier.fillMaxWidth().height(48.dp).testTag("section-controls")) {Text("Crear | Capas")}
                    }
                }
            }
        }}
        val emptyHeight=bounds("main").height
        compose.onNodeWithTag("audio-bar").assertDoesNotExist();compose.onNodeWithTag("pomodoro-bar").assertDoesNotExist()
        compose.runOnIdle {showAudio=true}
        compose.onNodeWithTag("audio-bar").assertIsDisplayed();compose.onNodeWithTag("pomodoro-bar").assertDoesNotExist()
        assertTrue(bounds("main").height<emptyHeight)
        compose.runOnIdle {showAudio=false;showTimer=true}
        compose.onNodeWithTag("audio-bar").assertDoesNotExist();compose.onNodeWithTag("pomodoro-bar").assertIsDisplayed()
        compose.runOnIdle {showAudio=true}
        checkStack()
        val beforeAudio=audio.state.value;val beforeTimer=timer.state.value
        compose.onNodeWithTag("audio-compact-toggle").performClick()
        compose.runOnIdle {assertTrue(modules.audioCompact);assertFalse(modules.pomodoroCompact)}
        checkStack()
        compose.onNodeWithTag("pomodoro-compact-toggle").performClick()
        compose.runOnIdle {assertTrue(modules.audioCompact);assertTrue(modules.pomodoroCompact)}
        compose.onNodeWithTag("audio-compact-toggle").performClick()
        compose.runOnIdle {assertFalse(modules.audioCompact);assertTrue(modules.pomodoroCompact)}
        checkStack()
        compose.onNodeWithTag("audio-compact-toggle").performClick()
        listOf("Antisocial","Calendario","General").forEach {name ->
            compose.runOnIdle {section=name};compose.onNodeWithText(name).assertExists();checkStack()
        }
        compose.runOnIdle {wide=true};checkStack()
        restore.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertTrue(modules.audioCompact);assertTrue(modules.pomodoroCompact)
            assertEquals(beforeAudio,audio.state.value);assertEquals(beforeTimer,timer.state.value)
        }
        checkStack()
    }

    @Test fun nestedEditorTransfersFooterWithoutCoveringFieldsAndFoldedPanelReturnsAboveBars() {
        val timer=timer()
        var open by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {
            val modules=rememberBottomModulesState()
            val data by timer.state.collectAsState()
            CompositionLocalProvider(LocalBottomModulesState provides modules,LocalBottomModulesContent provides { _:Boolean ->
                FoldedBottomPanels();AudioBar();PomodoroBar(timer,data,onExpand={})
            }) {
                BottomModulesFrame {
                    Box(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().height(48.dp).testTag("section-controls")) {Text("Crear")}
                    if(open) ModularPanelDialog(onDismissRequest={open=false},title={Text("Editor")},
                        text={TextField("Borrador sin guardar",{},modifier=Modifier.fillMaxWidth().testTag("draft"))},
                        confirmButton={TextButton(onClick={}) {Text("Guardar")}},foldLabel="Edición")
                }
            }
        }}
        checkStack()
        val before=timer.state.value;val beforeAudio=audio.state.value
        compose.runOnIdle {open=true}
        compose.onAllNodesWithTag("audio-bar").assertCountEquals(1)
        compose.onAllNodesWithTag("pomodoro-bar").assertCountEquals(1)
        compose.onNodeWithTag("draft").performClick()
        assertTrue(bounds("draft").bottom<=bounds("audio-bar").top+1)
        compose.onNodeWithTag("modular-fold:Edición").performClick()
        compose.onNodeWithTag("draft").assertDoesNotExist()
        assertTrue(bounds("modular-reopen:Edición").bottom<=bounds("audio-bar").top+1)
        compose.onNodeWithTag("modular-reopen:Edición").performClick()
        compose.onNodeWithText("Borrador sin guardar").assertExists()
        compose.runOnIdle {open=false;assertEquals(before,timer.state.value);assertEquals(beforeAudio,audio.state.value)}
        checkStack()
    }
    @Test @Config(qualifiers="w840dp-h400dp-land")
    fun materialSelectorKeepsBothBarsFullWidthInShortLandscapeWindow() {
        val timer=timer()
        var selecting by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {
            val modules=rememberBottomModulesState()
            val data by timer.state.collectAsState()
            CompositionLocalProvider(LocalBottomModulesState provides modules,LocalBottomModulesContent provides { _:Boolean ->
                AudioBar();PomodoroBar(timer,data,onExpand={})
            }) {
                BottomModulesFrame {
                    Box(Modifier.weight(1f))
                    Button(onClick={selecting=true},modifier=Modifier.fillMaxWidth().height(48.dp).testTag("section-controls")) {Text("Seleccionar")}
                    if(selecting) ContextActionMenu("Opciones",{selecting=false}) {
                        action("Elegir",androidx.compose.material.icons.Icons.Default.Info,{selecting=false})
                    }
                }
            }
        }}
        val before=timer.state.value;val recording=audio.state.value
        val width=bounds("section-controls").width
        compose.onNodeWithText("Seleccionar").performClick()
        compose.onAllNodesWithTag("audio-bar").assertCountEquals(1)
        compose.onAllNodesWithTag("pomodoro-bar").assertCountEquals(1)
        assertEquals(width,bounds("audio-bar").width,1f)
        assertEquals(width,bounds("pomodoro-bar").width,1f)
        assertEquals(compose.density.density,bounds("pomodoro-bar").top-bounds("audio-bar").bottom,1f)
        compose.onNodeWithText("Elegir").assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(before,timer.state.value);assertEquals(recording,audio.state.value)}
        checkStack()
    }

}
