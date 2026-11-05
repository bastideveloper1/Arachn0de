package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.sharing.SharingTestApplication::class,sdk=[28],qualifiers="w360dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskViewUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {
        shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();block()
    }
    private fun ready()=await {compose.onAllNodes(hasTestTag("core-context:Creation") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
    private fun choose(status:TaskStatus?) {
        ready();compose.onNode(hasContentDescription("Filtros") or hasContentDescription("Filtros activos")).performSemanticsAction(SemanticsActions.OnClick) {it()}
        await {compose.onAllNodesWithTag("task-view-selector").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("task-view:${status?.name ?: "ALL"}",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Aplicar").performClick()
        await {compose.onAllNodesWithTag("task-view-selector").fetchSemanticsNodes().isEmpty()};ready()
    }
    private fun locate(title:String) {ready();compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(title))}
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount)(root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        val process=Arachn0deApplication::class.java.getDeclaredField("processScope").apply {isAccessible=true}.get(app) as CoroutineScope
        runBlocking {process.coroutineContext[Job]?.cancelAndJoin();app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin();withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}}
        app.database.close()
    }
    @Test fun contextualCountsCompactLayersBackAndInvalidScopeResetWithoutWrites() {
        val p=runBlocking {app.projectRepository.createProject("Núcleo").also {app.projectRepository.setVerificationEnabled(it.id,true)}}
        val layers=runBlocking {listOf("Con tareas","Vacía").map {app.nodeRepository.createNode(p.id,null,it,purpose=NodePurpose.LAYER)}}
        val tasks=runBlocking {listOf(TaskStatus.PENDING,TaskStatus.VERIFY,TaskStatus.COMPLETED).map {state->app.nodeRepository.createNode(p.id,layers[0].id,"Tarea ${state.name}").also {app.nodeRepository.setTaskStatus(it.id,state)}}}
        val project=runBlocking {app.projectRepository.getProject(p.id)!!};val before=runBlocking {app.database.backupDao().nodes()}
        compose.setContent {Arachn0deTheme {ProjectNodeScreen(project,app.nodeRepository,{})}}
        ready()
        compose.onNodeWithTag("core-context:View").assertDoesNotExist()
        compose.onNode(hasAnyDescendant(hasTestTag("navigation-context")) and hasClickAction(),useUnmergedTree=true).performSemanticsAction(SemanticsActions.OnClick) {it()}
        await {compose.onAllNodesWithTag("navigator-node:${layers[0].id}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Capas de cebolla").assertExists()
        compose.onNodeWithTag("navigator-node:${layers[0].id}",useUnmergedTree=true).performSemanticsAction(SemanticsActions.OnClick) {it()}
        ready()
        compose.onNodeWithTag("app-bar-title",useUnmergedTree=true).assertTextEquals(layers[0].title)
        compose.activityRule.scenario.onActivity {it.onBackPressedDispatcher.onBackPressed()}
        ready()
        compose.onNodeWithTag("app-bar-title",useUnmergedTree=true).assertTextEquals(project.name)
        compose.onNode(hasContentDescription("Filtros") or hasContentDescription("Filtros activos")).performSemanticsAction(SemanticsActions.OnClick) {it()}
        compose.onNodeWithTag("task-view:ALL").assertContentDescriptionContains("3 tareas",substring=true)
        compose.onNodeWithText("Aplicar").performClick()
        await {compose.onAllNodesWithTag("task-view-selector").fetchSemanticsNodes().isEmpty()}
        choose(TaskStatus.VERIFY)
        locate(tasks[1].title);compose.onNodeWithTag("node-title:${tasks[1].id}",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasTestTag("task-view-empty-layer:${layers[1].id}"))
        compose.onNodeWithTag("task-view-empty-layer:${layers[1].id}").assertIsDisplayed().performClick()
        ready();compose.onNodeWithTag("core-context:View").assertDoesNotExist()
        compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
        ready();compose.onNodeWithTag("core-context:View").assertDoesNotExist()
        compose.onNode(hasContentDescription("Filtros") or hasContentDescription("Filtros activos")).performSemanticsAction(SemanticsActions.OnClick) {it()}
        compose.onAllNodesWithText("Estado:",substring=true).assertCountEquals(0)
        compose.onAllNodesWithText("Estado de tarea:",substring=true).assertCountEquals(0)
        compose.onNodeWithText("Aplicar").performClick()
        assertEquals(before,runBlocking {app.database.backupDao().nodes()})
    }
    @Test fun changingStatusOffersOnlyLastUndoAndCreationKeepsTheFilteredView() {
        val p=runBlocking {app.projectRepository.createProject("Núcleo").also {app.projectRepository.setVerificationEnabled(it.id,true)}}
        val task=runBlocking {app.nodeRepository.createNode(p.id,null,"Tarea visible")}
        val project=runBlocking {app.projectRepository.getProject(p.id)!!}
        compose.setContent {Arachn0deTheme {ProjectNodeScreen(project,app.nodeRepository,{})}}
        choose(TaskStatus.PENDING);locate(task.title)
        compose.onNodeWithTag("task-state:${task.id}").performClick()
        await {runBlocking {app.nodeRepository.getNode(task.id)!!.taskStatus==TaskStatus.VERIFY}}
        await {compose.onAllNodesWithText("Deshacer").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Deshacer").performClick()
        await {runBlocking {app.nodeRepository.getNode(task.id)!!.taskStatus==TaskStatus.PENDING}}
        locate(task.title);compose.onNodeWithTag("node-title:${task.id}",useUnmergedTree=true).assertIsDisplayed()
        choose(TaskStatus.COMPLETED)
        compose.onNodeWithTag("core-context:Creation").performClick()
        compose.onNode(hasText("Crear",substring=false) and hasAnyAncestor(hasTestTag("project-bottom-actions"))).performClick()
        compose.onNodeWithText("Nuevo",substring=false).performClick()
        await {compose.onAllNodesWithTag("quick-create-title").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("quick-create-title").performTextInput("Creada bajo filtro")
        compose.onNodeWithTag("quick-create-save").performClick()
        await {runBlocking {app.nodeRepository.getProjectNodes(p.id).any {it.title=="Creada bajo filtro"}}}
        await {compose.onAllNodesWithText("Tarea creada. No aparece en esta vista").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(TaskStatus.COMPLETED,androidx.lifecycle.ViewModelProvider(compose.activity)[TaskViewSession::class.java].status)
        compose.onNode(hasText("Ver todas") and hasAnyAncestor(hasTestTag("task-view-snackbar"))).performClick()
        ready();assertNull(androidx.lifecycle.ViewModelProvider(compose.activity)[TaskViewSession::class.java].status)
        locate("Creada bajo filtro")
        assertEquals(task.position,runBlocking {app.nodeRepository.getNode(task.id)!!.position})
    }
    @Test fun openSelectorIgnoresCardTapsButAllowsScrollingAndFilteredDragDoesNotCommit() {
        var clicks=0;var blocked=0;var commits=0
        var open by mutableStateOf(true)
        lateinit var state:androidx.compose.foundation.lazy.LazyListState
        lateinit var reorder:DragReorderState
        compose.setContent {Arachn0deTheme {
            state=rememberLazyListState()
            val drag=rememberDragReorderState(state,"node:",mapOf(false to (0..29).map(Int::toString)),true,null) {_,_,_->commits++}
            SideEffect {reorder=drag;drag.onBlockedDrag={blocked++}}
            LazyColumn(Modifier.fillMaxSize().testTag("test-list").ignoreTaskViewTaps(open).then(drag.gestureModifier),state=state) {
                items(30,key={"node:$it"}) {index->Text("Elemento $index",Modifier.fillMaxWidth().height(80.dp).testTag("test-row:$index").clickable {clicks++})}
            }
        }}
        compose.onNodeWithTag("test-row:0").performTouchInput {click()}
        assertEquals(0,clicks)
        compose.onNodeWithTag("test-list").performTouchInput {swipeUp()}
        compose.runOnIdle {assertTrue(state.firstVisibleItemIndex>0)}
        compose.runOnIdle {open=false}
        compose.onNodeWithTag("test-list").performScrollToIndex(0)
        // Exercise the same drag state with measured LazyColumn geometry; gesture
        // recognition on a physical device remains a separate manual check.
        compose.runOnIdle {
            val item=state.layoutInfo.visibleItemsInfo.first()
            val start=item.offset+item.size/2f
            val order=reorder.groups
            reorder.startDrag(start)
            repeat(3) {reorder.dragTo(start+20f*(it+1))}
            reorder.finishDrag()
            assertNull(reorder.draggedId)
            assertEquals(order,reorder.groups)
        }
        compose.runOnIdle {assertEquals(1,blocked);assertEquals(0,commits);assertEquals(0,clicks)}
    }
    @Test fun panelMeasuresLabelsAt360WithLargeFontsAndRetainsSessionOnlyInMemory() {
        var selected by mutableStateOf<TaskStatus?>(TaskStatus.VERIFY)
        var expanded by mutableStateOf(true)
        var three by mutableStateOf(true)
        val restoration=StateRestorationTester(compose)
        var session:TaskViewSession?=null
        restoration.setContent {Arachn0deTheme {
            session=rememberTaskViewSession()
            CompositionLocalProvider(LocalDensity provides Density(1f,1.6f)) {
                Column {
                    TaskViewPanel(TaskViewControls(selected,TaskStatus.entries.associateWith {2},three,false,{},{},{selected=it}))
                    CoreBottomModule(null,{},500.dp,{}, {},viewControls=TaskViewControls(selected,emptyMap(),three,false,{},{},{}))
                }
            }
        }}
        compose.onAllNodesWithTag("task-view-grid").assertCountEquals(2)
        val results=mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Por verificar · 2",useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(results)}
        assertFalse(results.single().hasVisualOverflow)
        val bar=listOf("Content","Creation","Options").map {compose.onNodeWithTag("core-context:$it").fetchSemanticsNode().boundsInRoot}
        bar.zipWithNext().forEach {(a,b)->assertTrue(a.right<=b.left+1f)};bar.forEach {assertTrue(it.height>=48f)}
        compose.runOnIdle {session!!.status=TaskStatus.VERIFY}
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(TaskStatus.VERIFY,compose.runOnIdle {session!!.status})
        compose.runOnIdle {session!!.validate(false)};assertNull(session!!.status)
        assertNull(TaskViewSession().status)
        compose.runOnIdle {expanded=true;three=false;selected=null}
        compose.onNodeWithTag("task-view:VERIFY").assertDoesNotExist()
        listOf("ALL","PENDING","COMPLETED").forEach {compose.onNodeWithTag("task-view:$it").assertIsDisplayed()}
    }
}
