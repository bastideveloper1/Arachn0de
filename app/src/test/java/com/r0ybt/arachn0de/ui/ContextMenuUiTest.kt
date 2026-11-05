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
    @Test fun elementConversionsStayInPanelAndBackNeverExecutesThem() {
        var drafts=0;var notes=0
        mount {ContextActionMenu("Tarea",{},inline=true,showCancel=false,groupConversions=true) {
            action("Editar",Icons.Default.Edit,{})
            action("Convertir en nota",Icons.Default.Description,{notes++})
            action("Mover a Borradores",Icons.Default.Description,{drafts++})
        }}
        await("Convertir en...")
        compose.onNodeWithText("Convertir en nota").assertDoesNotExist()
        compose.onNodeWithTag("context-action:Convertir en...").performClick()
        compose.onNodeWithText("Convertir en Borrador").assertExists()
        compose.onNodeWithText("Editar").assertDoesNotExist()
        compose.onNodeWithTag("context-action:← Atrás").performClick()
        assertEquals(0,drafts);assertEquals(0,notes)
        compose.onNodeWithText("Editar").assertExists()
        compose.onNodeWithTag("context-action:Convertir en...").performClick()
        compose.onNodeWithText("Convertir en Borrador").performClick()
        assertEquals(1,drafts);assertEquals(0,notes)
    }
    @Test fun repeatedExportNavigationEntersOnceAndCanReopen() {
        mount {ContextActionMenu("Opciones",{},inline=true,showCancel=false) {
            items+=ContextAction("Exportar o compartir",Icons.Default.Share,{},children=listOf(
                ContextAction("Guardar imagen",Icons.Default.Image,{})))
        }}
        repeat(2) {
            val click=action("Exportar o compartir").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!
            compose.runOnIdle {repeat(8) {click()}}
            action("Guardar imagen").assertIsDisplayed()
            compose.onNodeWithContentDescription("Volver").performClick()
            action("Exportar o compartir").assertIsDisplayed()
        }
    }
    @Test fun exportScopeUsesSamePanelAndSingleActionRunsDirectly() {
        var single=0;var branch=0;var shared=0
        mount {ContextActionMenu("Opciones",{},inline=true,showCancel=false) {
            items+=ContextAction("Guardar imagen",Icons.Default.Image,{},children=listOf(
                ContextAction("Solo elemento",Icons.Default.Image,{single++}),
                ContextAction("Con descendientes",Icons.Default.Collections,{branch++})))
            action("Compartir imagen",Icons.Default.Share,{shared++})
        }}
        await("Guardar imagen")
        compose.onNodeWithText("Compartir imagen").performClick();assertEquals(1,shared)
        compose.onNodeWithText("Guardar imagen").performClick()
        compose.onNodeWithText("Solo elemento").assertExists()
        compose.onNodeWithText("Con descendientes").performClick();assertEquals(1,branch);assertEquals(0,single)
        compose.onNodeWithTag("context-action:← Atrás").performClick()
        compose.onNodeWithText("Guardar imagen").performClick()
        compose.onNodeWithText("Solo elemento").performClick();assertEquals(1,single)
    }
    @Test fun nestedExportGroupKeepsRootCompactAndReturnsOneLevel() {
        var pdf=0;var word=0
        mount {ContextActionMenu("Opciones",{},inline=true,showCancel=false) {
            action("Editar",Icons.Default.Edit,{})
            items+=ContextAction("Exportar o compartir",Icons.Default.Share,{},children=listOf(
                ContextAction("PDF / Word",Icons.Default.Description,{},children=listOf(
                    ContextAction("PDF",Icons.Default.Description,{pdf++}),
                    ContextAction("Word / DOCX",Icons.Default.Description,{word++}))),
                ContextAction("Compartir imagen",Icons.Default.Share,{})))
        }}
        await("Exportar o compartir")
        compose.onNodeWithText("PDF / Word").assertDoesNotExist()
        compose.onNodeWithText("Exportar o compartir").performClick()
        compose.onNodeWithText("Editar").assertDoesNotExist()
        compose.onNodeWithText("PDF / Word").performClick()
        compose.onNodeWithText("PDF").assertExists()
        val panel=compose.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.PaneTitle,"PDF / Word"),useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val backCell=compose.onNodeWithTag("context-action:← Atrás").fetchSemanticsNode().boundsInRoot
        assertEquals(action("PDF").fetchSemanticsNode().boundsInRoot.left,backCell.left,1f)
        compose.onNodeWithText("Word / DOCX").performClick();assertEquals(1,word);assertEquals(0,pdf)
        compose.onNodeWithText("← Atrás").assertDoesNotExist()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithTag("context-action:PDF / Word").assertExists()
        compose.onNodeWithText("Editar").assertDoesNotExist()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Editar").assertExists()
        assertEquals(1,word);assertEquals(0,pdf)
    }
    @Test fun exportBackFollowsContentAndPanelShrinksForShortMenus() {
        val failures=mutableListOf<String>()
        for(inlineMenu in listOf(true,false)) {
            var shortHeight=0f
            for(extra in listOf(0,5)) {
                mount {Box(Modifier.fillMaxWidth().height(500.dp)) {
                    ContextActionMenu("Opciones",{},inline=inlineMenu,showCancel=false,bottomAligned=!inlineMenu) {
                        items+=ContextAction("Exportar o compartir",Icons.Default.Share,{},children=
                            (0 until extra).map {ContextAction("Opción $it",Icons.Default.Description,{})}+
                                ContextAction("Guardar imagen",Icons.Default.Image,{}))
                    }
                }}
                await("Exportar o compartir")
                compose.onNodeWithText("Exportar o compartir").performClick()
                val last=action("Guardar imagen").fetchSemanticsNode().boundsInRoot
                val back=action("← Atrás").fetchSemanticsNode().boundsInRoot
                val panel=compose.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.PaneTitle,"Exportar o compartir"),useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                val gap=back.top-last.bottom
                val density=compose.density.density
                if(kotlin.math.abs(if(extra%2==0) back.top-last.top else gap-4*density)>1f)failures+="inline=$inlineMenu opciones=${extra+1}: separación=$gap, panel=$panel, última=$last, volver=$back"
                if(panel.bottom-back.bottom<6*density-1f)failures+="Falta margen inferior: $back / $panel"
                if(back.width>panel.width*.6f)failures+="Volver ocupa más de una celda: $back / $panel"
                if(extra==0)shortHeight=panel.height else if(panel.height<=shortHeight)failures+="La altura no se adapta: inline=$inlineMenu corta=$shortHeight larga=${panel.height}"
                compose.onNodeWithContentDescription("Volver").assertIsDisplayed().performClick()
            }
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
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
            if(node.isStructural) action("Tecnologías").assertExists() else {
                action("Tecnologías").assertDoesNotExist()
                compose.onNodeWithTag("card-technology:${node.id}").assertExists()
            }
            action("Metro / modo Viaje").assertDoesNotExist();action("Convertir en viaje").assertDoesNotExist()
            action("Editar").assertExists();action("Cancelar").assertDoesNotExist();action("Responsables").assertExists()
            action("Subir").assertDoesNotExist();action("Bajar").assertDoesNotExist()
            action("Copiar este elemento").performClick();assertEquals("copy:false",selected)
            open();action("Mover a…").performClick();assertEquals("move",selected)
            open();action("Eliminar").assert(hasStateDescription("Acción destructiva")).performClick();assertEquals("delete",selected)
            open();action("Responsables").performScrollTo().performClick();assertEquals("responsible",selected)
            open();action("Historial").performScrollTo().performClick();assertEquals("history",selected)
            if(node.isStructural) {
                open();action("Copiar texto con descendientes").performClick();assertEquals("copy:true",selected)
                compose.runOnIdle {drag=false}
                open();action("Subir").assertDoesNotExist();action("Bajar").assertDoesNotExist()
                compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
            }
        }
        compose.runOnIdle {drag=true;interactionMode=androidx.compose.ui.input.InputMode.Keyboard}
        open();action("Subir").assertDoesNotExist();action("Bajar").assertDoesNotExist();compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.runOnIdle {interactionMode=androidx.compose.ui.input.InputMode.Touch;accessibility.setTouchExplorationEnabled(true)}
        open();action("Subir").assertDoesNotExist();action("Bajar").assertDoesNotExist();compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
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
        compose.onNodeWithTag("node-options:${layer.id}").performClick();compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNode(hasText("Capa") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Opciones del elemento").performClick();action("Editar").assertDoesNotExist()
        compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
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
        val changes=java.util.IdentityHashMap<Any,Pair<Int,String>>()
        val observer=androidx.compose.runtime.snapshots.Snapshot.registerApplyObserver {objects,_->synchronized(changes) {
            objects.forEach {obj->
                val value=(obj as? State<*>)?.value
                val detail=if(value is Number || value is Boolean || value is androidx.compose.ui.unit.Dp || value is androidx.compose.ui.graphics.Color || value is androidx.compose.ui.text.TextLayoutResult) value.toString().take(180) else value?.javaClass?.name.orEmpty()
                changes[obj]=((changes[obj]?.first ?: 0)+1) to detail
            }
        }}
        try {await("Nombre")} finally {
            observer.dispose()
            println("PROJECT_IDLE_RECOMPOSERS "+Recomposer.runningRecomposers.value.map {it.hasPendingWork})
            println("PROJECT_IDLE_READINESS properties=${draft.projectPropertiesLoaded} attachments=${draft.attachmentsLoaded} technologies=${draft.technologiesLoaded}")
            synchronized(changes) {changes.entries.sortedByDescending {it.value.first}.take(18).forEach {println("PROJECT_IDLE_STATE ${it.key.javaClass.name} ${it.value}")}}
        }
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
