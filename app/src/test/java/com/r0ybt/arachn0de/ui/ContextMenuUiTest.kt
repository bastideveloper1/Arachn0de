package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w360dp-h800dp")
class ContextMenuUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(text:String)=compose.waitUntil(15000) {pump();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun mount(content:@Composable ()->Unit) {compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {AppSafeArea(content=content)}}}}
    private fun action(label:String)=compose.onNodeWithTag("context-action:$label")
    @After fun close() {
        compose.runOnUiThread {
            val content=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until content.childCount) (content.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            content.removeAllViews()
        };pump()
        val application=app;val db=application.database
        runBlocking {
            application.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin()
            withTimeout(15000) {db.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}
        };db.close()
    }
    @Test fun nodeMenusKeepCallbacksCopyVariantsAndOnlyRemoveEquivalentAccesses() {
        val project=runBlocking {app.projectRepository.createProject("Proyecto")}
        val nodes=runBlocking {listOf(NodePurpose.ACTION,NodePurpose.NOTE,NodePurpose.LAYER).map {app.nodeRepository.createNode(project.id,null,it.name,purpose=it)}}
        var chosen by mutableStateOf(nodes.first())
        var drag by mutableStateOf(true)
        var canCopy by mutableStateOf(true)
        var selected=""
        var interactionMode by mutableStateOf(androidx.compose.ui.input.InputMode.Touch)
        val inputManager=object:androidx.compose.ui.input.InputModeManager {
            override val inputMode get()=interactionMode
            override fun requestInputMode(inputMode:androidx.compose.ui.input.InputMode)=true
        }
        val accessibility=org.robolectric.Shadows.shadowOf(compose.activity.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager)
        accessibility.setTouchExplorationEnabled(false)
        mount {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalInputModeManager provides inputManager) {
            NodeCard(chosen,null,false,false,{selected="open"},{selected="edit"},{selected="delete"},true,true,
                {up,done->selected=if(up) "up" else "down";done()},{},canDragReorder=drag,
                onMove={selected="move"},onResponsible={selected="responsible"},onCopy={selected="copy:$it"},canCopy=canCopy,onHistory={selected="history"})
            }
        }
        fun open() {compose.onNodeWithTag("node-options:${chosen.id}").performTouchInput {click()};action("Copiar este elemento").assertIsDisplayed()}
        for(node in nodes) {
            compose.runOnIdle {chosen=node}
            open()
            if(node.isStructural) action("Editar tecnologías").assertExists() else {
                action("Editar tecnologías").assertDoesNotExist()
                compose.onNodeWithTag("card-technology:${node.id}").assertExists()
            }
            action("Editar").assertExists();action("Responsables").assertExists()
            if(node.isStructural) {action("Subir").assertDoesNotExist();action("Bajar").assertDoesNotExist()}
            action("Copiar este elemento").performClick();assertEquals("copy:false",selected)
            open();action("Mover a…").performClick();assertEquals("move",selected)
            open();action("Eliminar").assert(hasStateDescription("Acción destructiva")).performClick();assertEquals("delete",selected)
            open();action("Responsables").performScrollTo().performClick();assertEquals("responsible",selected)
            open();action("Historial").performScrollTo().performClick();assertEquals("history",selected)
            if(node.isStructural) {
                open();action("Copiar texto con descendientes").performClick();assertEquals("copy:true",selected)
                compose.runOnIdle {drag=false}
                open();action("Subir").performScrollTo().performClick();assertEquals("up",selected)
                open();action("Bajar").performScrollTo().performClick();assertEquals("down",selected)
            }
        }
        compose.runOnIdle {drag=true;interactionMode=androidx.compose.ui.input.InputMode.Keyboard}
        open();action("Subir").assertExists();action("Bajar").assertExists();action("Cancelar").performClick()
        compose.runOnIdle {interactionMode=androidx.compose.ui.input.InputMode.Touch;accessibility.setTouchExplorationEnabled(true)}
        open();action("Subir").assertExists();action("Bajar").assertExists();action("Cancelar").performClick()
        compose.runOnIdle {accessibility.setTouchExplorationEnabled(false);canCopy=false}
        open();action("Copiar este elemento").assertIsNotEnabled();action("Copiar texto con descendientes").assertIsNotEnabled()
    }
    @Test fun projectMenuKeepsExclusiveActionsAndDisabledDescendantCopy() {
        val project=runBlocking {app.projectRepository.createProject("Proyecto")}
        var selected=""
        mount {ProjectCard(project,ProjectCardAlerts(),{selected="open"},{selected="edit"},{selected="delete"},
            onMoveInside={selected="move"},onCopy={selected="copy:$it"},canCopyDescendants=false,onPhoto={selected="photo"})}
        fun open() {compose.onNodeWithContentDescription("Opciones del proyecto").performClick();action("Copiar").assertIsDisplayed()}
        open();action("Copiar texto con descendientes").assertIsNotEnabled();action("Copiar").performClick();assertEquals("copy:false",selected)
        open();action("Convertir en capa").performScrollTo().performClick();assertEquals("move",selected)
        open();action("Editar").performScrollTo().performClick();assertEquals("edit",selected)
        open();action("Fotografía").performScrollTo().performClick();assertEquals("photo",selected)
        open();action("Eliminar").performClick();assertEquals("delete",selected)
    }
    @Test fun longMenuPinsPrioritiesAndDestructiveActionWithLargeText() {
        var selected=""
        mount {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
                ContextActionMenu("Contexto de prueba",{}) {
                    repeat(20) {index->action("Propiedad $index",Icons.Default.Settings,{selected="property:$index"})}
                    action("Copiar este elemento",Icons.Default.ContentCopy,{selected="copy"})
                    action("Mover a…",Icons.Default.DriveFileMove,{selected="move"})
                    action("Eliminar",Icons.Default.Delete,{selected="delete"},destructive=true)
                }
            }
        }
        val before=action("Copiar este elemento").fetchSemanticsNode().boundsInRoot
        action("Propiedad 19").performScrollTo()
        action("Propiedad 19").assertIsDisplayed()
        assertEquals(before,action("Copiar este elemento").fetchSemanticsNode().boundsInRoot)
        listOf("Copiar este elemento","Mover a…","Eliminar","Cancelar").forEach {action(it).assertIsDisplayed()}
        assertTrue(action("Eliminar").fetchSemanticsNode().boundsInRoot.top > action("Mover a…").fetchSemanticsNode().boundsInRoot.bottom)
        action("Copiar este elemento").performClick();assertEquals("copy",selected)
        action("Mover a…").performClick();assertEquals("move",selected)
    }
    @Test fun detailTitleOpensSameFullEditorAndDeletionStillNeedsConfirmation() {
        val project=runBlocking {app.projectRepository.createProject("Proyecto")}
        val layer=runBlocking {app.nodeRepository.createNode(project.id,null,"Capa",purpose=NodePurpose.LAYER)}
        mount {AppRoot(app.projectRepository,app.nodeRepository)}
        await("Proyecto");compose.onNodeWithText("Proyecto").performClick();await("Capa")
        compose.onNodeWithTag("node-options:${layer.id}").performClick();action("Cancelar").performClick()
        compose.onNode(hasText("Capa") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Opciones del elemento").performClick();action("Editar").assertDoesNotExist()
        action("Cancelar").performClick()
        compose.onNode(hasText("Capa") and hasClickAction()).performClick();await("Título")
        compose.onNodeWithText("Descripción").assertExists();compose.onNodeWithText("Guardar").assertExists()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithContentDescription("Opciones del elemento").performClick();action("Eliminar").performClick()
        await("Eliminar nodo")
        assertNotNull(runBlocking {app.nodeRepository.getNode(layer.id)})
        compose.onNodeWithText("Cancelar").performClick()
        assertNotNull(runBlocking {app.nodeRepository.getNode(layer.id)})
    }
    @Test fun compactLayerPeopleIndicatorOpensEveryoneWithoutAnAvatarRow() {
        val people=(1..3).map {Person("person:$it","Persona $it")}
        val ids=people.mapTo(hashSetOf()) {it.id}
        mount {
            CompositionLocalProvider(LocalCompactCardFacts provides mapOf("node:layer" to CompactCardFacts(people=ids)),
                LocalIndicatorDirectory provides IndicatorDirectory(people=people.associateBy {it.id})) {
                Row {
                    CardIndicatorDetail("node:layer",{},fillWidth=false)
                    CompactAssignments("layer",people,showTechnologies=false,showPeople=false,fillWidth=false)
                }
            }
        }
        people.forEach {compose.onNodeWithText(it.name).assertDoesNotExist()}
        compose.onAllNodesWithContentDescription("Personas").assertCountEquals(1)
        compose.onNodeWithTag("detail-signal:node:layer:people").performClick()
        compose.waitUntil(15000) {pump();people.all {person->runCatching {compose.onNodeWithTag("person-name:${person.id}",useUnmergedTree=true).assertIsDisplayed()}.isSuccess}}
        people.forEach {compose.onNodeWithTag("person-name:${it.id}",useUnmergedTree=true).assertIsDisplayed()}
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithTag("detail-signal:node:layer:people").assertIsDisplayed()
    }

    @Test fun titleEditorWrapsKeepsCharacterLimitAndSavesAllLines() {
        val project=runBlocking {app.projectRepository.createProject("Proyecto")}
        val node=runBlocking {app.nodeRepository.createNode(project.id,null,"Título corto")}
        val draft=com.r0ybt.arachn0de.ui.state.EditorDraft(node.id,null,node.title,node.description)
        var saved=false
        mount {NodeDialog(draft,false,{}, {title,description->
            runBlocking {app.nodeRepository.updateNode(node.id,title,description)};saved=true
        })}
        await("Título")
        compose.onNodeWithText("Título").performTextReplacement("x".repeat(101))
        compose.onNodeWithText("Guardar").assertIsNotEnabled()
        val multiline="x".repeat(100)
        compose.onNodeWithText("Título").performTextReplacement(multiline)
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Título").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertTrue(layouts.last().lineCount>1)
        assertFalse(layouts.last().didOverflowWidth)
        compose.waitUntil(15000) {pump();runCatching {compose.onNodeWithText("Guardar").assertIsEnabled()}.isSuccess}
        compose.onNodeWithText("Guardar").performClick()
        assertTrue(saved)
        assertEquals(multiline,runBlocking {app.nodeRepository.getNode(node.id)!!.title})
    }

    @Test fun projectTitleWrapsWithoutNewlinesAndSavesCompleteText() {
        val project=runBlocking {app.projectRepository.createProject("Proyecto")}
        val draft=com.r0ybt.arachn0de.ui.state.EditorDraft(project.id,null,project.name,project.description)
        var saved=false
        mount {ProjectDialog(draft,false,{}, {title,description->
            runBlocking {app.projectRepository.updateProject(project.id,title,description)};saved=true
        })}
        await("Nombre")
        val title="W".repeat(60)
        compose.onNodeWithText("Nombre").performTextReplacement(title)
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Nombre").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertTrue(layouts.last().lineCount>1)
        assertFalse(layouts.last().didOverflowWidth)
        compose.waitUntil(15000) {pump();runCatching {compose.onNodeWithText("Guardar").assertIsEnabled()}.isSuccess}
        compose.onNodeWithText("Guardar").performClick()
        assertTrue(saved)
        assertEquals(title,runBlocking {app.database.projectDao().getById(project.id)!!.name})
    }

}
