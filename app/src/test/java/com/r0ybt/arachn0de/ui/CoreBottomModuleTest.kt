package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.sharing.SharingTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class CoreBottomModuleTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {
        shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame();block()
    }
    private fun back()=compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
    @Test fun taskStatusIsOnlyInContentAndConfigurationOnlyInOptionsWithConversion() {
        val project=runBlocking {app.projectRepository.createProject("Estados")}
        val layer=runBlocking {app.nodeRepository.createNode(project.id,null,"Capa",purpose=NodePurpose.LAYER)}
        runBlocking {app.nodeRepository.setTaskStateCount(layer.id,3)}
        val task=runBlocking {app.nodeRepository.createNode(project.id,layer.id,"Tarea heredada")}
        compose.setContent {Arachn0deTheme {ProjectNodeScreen(project,app.nodeRepository,{},openNodeId=task.id)}}
        await {compose.onAllNodesWithTag("core-context:Content").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Content").performClick()
        compose.onNodeWithTag("context-action:Configurar estados").assertDoesNotExist()
        compose.onAllNodesWithTag("context-action:Cambiar estado").assertCountEquals(1)
        compose.onNodeWithTag("context-action:Cambiar estado").performScrollTo().performClick()
        await {compose.onAllNodesWithText("Por verificar",substring=false).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Por verificar",substring=false).assertIsEnabled().performClick()
        await {runBlocking {app.nodeRepository.getNode(task.id)!!.awaitingVerification}}
        compose.onNodeWithTag("core-context:Options").performClick()
        compose.onNodeWithTag("context-action:Cambiar estado").assertDoesNotExist()
        compose.onAllNodesWithTag("context-action:Configurar estados").assertCountEquals(1)
        compose.onNodeWithTag("context-action:Configurar estados").performScrollTo().performClick()
        compose.onNodeWithText("Heredar del contenedor").assertExists()
        compose.onNodeWithText("2 estados").performClick()
        await {compose.onAllNodesWithTag("task-state-conversion-confirm").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("task-state-conversion-confirm").performClick()
        await {runBlocking {app.nodeRepository.getNode(task.id)!!.taskStateCount==2}}
        assertEquals(com.r0ybt.arachn0de.domain.model.TaskStatus.PENDING,runBlocking {app.nodeRepository.getNode(task.id)!!.taskStatus})
    }
    @Test fun coreStateConfigurationIsOnlyInOptions()=stateConfigurationLocation(null)
    @Test fun layerStateConfigurationIsOnlyInOptions()=stateConfigurationLocation(NodePurpose.LAYER)
    @Test fun noteDoesNotAcquireTaskStateActions()=stateConfigurationLocation(NodePurpose.NOTE)
    private fun stateConfigurationLocation(purpose:NodePurpose?) {
        val project=runBlocking {app.projectRepository.createProject("Ámbito")}
        val node=purpose?.let {runBlocking {app.nodeRepository.createNode(project.id,null,"Elemento",purpose=it)}}
        compose.setContent {Arachn0deTheme {ProjectNodeScreen(project,app.nodeRepository,{},openNodeId=node?.id)}}
        await {compose.onAllNodesWithTag("core-context:Content").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Content").performClick()
        compose.onNodeWithTag("context-action:Configurar estados").assertDoesNotExist()
        compose.onNodeWithTag("context-action:Cambiar estado").assertDoesNotExist()
        compose.onNodeWithTag("core-context:Options").performClick()
        compose.onNodeWithTag("context-action:Cambiar estado").assertDoesNotExist()
        if(purpose==NodePurpose.NOTE)compose.onNodeWithTag("context-action:Configurar estados").assertDoesNotExist()
        else {
            compose.onAllNodesWithTag("context-action:Configurar estados").assertCountEquals(1)
            compose.onNodeWithTag("context-action:Configurar estados").performScrollTo().performClick()
            compose.onNodeWithText("3 estados").assertIsEnabled()
            if(node!=null)compose.onNodeWithText("Heredar del contenedor").assertExists()
            else compose.onNodeWithText("Heredar del contenedor").assertDoesNotExist()
        }
    }
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()}
        val process=Arachn0deApplication::class.java.getDeclaredField("processScope").apply {isAccessible=true}.get(app) as CoroutineScope
        runBlocking {process.coroutineContext[Job]?.cancelAndJoin()}
        val database=app.database
        runBlocking {
            app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin()
            withTimeout(15000) {database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}
        }
        database.close()
    }
    @Test fun creationShowsDirectChoicesAndKeepsRootDestination() = directCreationChoices(false)
    @Test fun creationShowsDirectChoicesAndKeepsLayerDestination() = directCreationChoices(true)
    private fun directCreationChoices(inLayer:Boolean) {
        val project=runBlocking {app.projectRepository.createProject("Núcleo")}
        val parent=if(inLayer)runBlocking {app.nodeRepository.createNode(project.id,null,"Capa",purpose=NodePurpose.LAYER)}.id else null
        compose.setContent {Arachn0deTheme {ProjectNodeScreen(project,app.nodeRepository,{},openNodeId=parent)}}
        await {compose.onAllNodesWithTag("core-context:Creation").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Creation").performClick()
        compose.onNodeWithText("Nuevo").assertIsDisplayed()
        compose.onNodeWithText("Desde plantilla").assertIsDisplayed()
        compose.onAllNodesWithText("Crear",substring=false).assertCountEquals(1)
        compose.onNodeWithText("Desde plantilla").performClick()
        compose.onNodeWithTag("structure-template-panel").assertIsDisplayed()
        compose.onNodeWithText("No hay plantillas compatibles. Guarda una desde Opciones.").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.Dismiss)).onFirst()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.Dismiss) {it()}
        await {compose.onAllNodesWithTag("structure-template-panel").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("core-panel-fold").performClick()
        compose.onNodeWithText("Nuevo").assertDoesNotExist()
        compose.onNodeWithTag("core-context:Creation").performClick()
        compose.onNodeWithText("Nuevo").performClick()
        await {compose.onAllNodesWithTag("quick-create-title").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("quick-create-title").performTextReplacement("Creación directa")
        compose.onNodeWithTag("quick-create-save").performClick()
        await {runBlocking {app.nodeRepository.getProjectNodes(project.id).any {it.title=="Creación directa" && it.parentId==parent}}}
    }
    @Test fun rootStartsCollapsedAndOptionsToggleSwitchAndBackCompactsFirst() {
        val project=runBlocking {app.projectRepository.createProject("Núcleo modular","Descripción original")}
        var exits=0
        compose.setContent {Arachn0deTheme {ProjectNodeScreen(project,app.nodeRepository,{exits++})}}
        await {compose.onAllNodesWithTag("core-bottom-module").fetchSemanticsNodes().isNotEmpty()}
        CoreBottomContext.entries.forEach {compose.onNodeWithTag("core-context:${it.name}").assertIsNotSelected()}
        compose.onNodeWithTag("context-action:Título").assertDoesNotExist()
        compose.onNodeWithTag("context-action:Descripción").assertDoesNotExist()
        compose.onNodeWithTag("core-context:Creation").performClick()
        compose.onNodeWithTag("core-context:Creation").assertIsSelected()
        compose.onNodeWithTag("project-bottom-actions").assertIsDisplayed()
        compose.onNodeWithText("Nuevo",substring=false).assertIsEnabled()
        compose.onNodeWithText("Desde plantilla",substring=false).assertIsDisplayed()
        compose.onNodeWithContentDescription("Opciones del proyecto").assertDoesNotExist()
        compose.onNodeWithTag("core-context:Options").performClick()
        compose.onNodeWithTag("core-context:Options").assertIsSelected()
        compose.onAllNodesWithTag("core-bottom-module").assertCountEquals(1)
        compose.onNodeWithTag("project-bottom-actions").assertDoesNotExist()
        listOf("Limpiar completadas","Fijar","Planificación","Fotografía","Recurrencias","Valores predeterminados","Convertir en capa","Copiar","Copiar texto con descendientes").forEach {
            compose.onNodeWithTag("context-action:$it").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("core-context:Content").performClick()
        compose.onNodeWithTag("core-context:Content").assertIsSelected()
        compose.onNodeWithTag("context-action:Tecnologías").assertIsDisplayed()
        compose.onNodeWithTag("context-action:Fijar").assertDoesNotExist()
        compose.onNodeWithText("Descripción original").assertExists()
        back();CoreBottomContext.entries.forEach {compose.onNodeWithTag("core-context:${it.name}").assertIsNotSelected()};assertEquals(0,exits)
        back();assertEquals(1,exits)
    }
    @Test fun childLevelsUseSharedContextsAndReturningToRootResetsContext() {
        val project=runBlocking {app.projectRepository.createProject("Núcleo")}
        val layer=runBlocking {app.nodeRepository.createNode(project.id,null,"Capa de prueba",purpose=NodePurpose.LAYER)}
        val task=runBlocking {app.nodeRepository.createNode(project.id,layer.id,"Tarea de prueba")}
        compose.setContent {Arachn0deTheme {ProjectNodeScreen(project,app.nodeRepository,{})}}
        await {compose.onAllNodesWithTag("core-context:Options").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Options").performClick()
        compose.onNodeWithText("Capa de prueba",substring=false).performScrollTo().performClick()
        await {runCatching {compose.onNodeWithTag("core-context:Content").assertIsNotSelected()}.isSuccess}
        compose.onNodeWithTag("core-context:Creation").performClick()
        compose.onNodeWithTag("project-bottom-actions").assertIsDisplayed()
        compose.onNodeWithText("Tarea de prueba",substring=false).performScrollTo().performClick()
        await {compose.onAllNodesWithContentDescription("Completar: Tarea de prueba").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Opciones del elemento").assertDoesNotExist()
        compose.onNodeWithTag("core-context:Options").performClick()
        compose.onNodeWithTag("context-action:Historial").assertExists()
        compose.onNodeWithTag("core-context:Options").assertIsSelected()
        compose.onNodeWithTag("context-action:Cancelar").assertDoesNotExist()
        back()
        await {compose.onAllNodesWithTag("context-action:Historial").fetchSemanticsNodes().isEmpty()}
        back()
        compose.onNodeWithTag("core-context:Creation").assertIsNotSelected()
        compose.onNodeWithTag("project-bottom-actions").assertDoesNotExist()
        back()
        await {compose.onAllNodesWithTag("core-context:Creation").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Content").assertIsNotSelected()
    }
    @Test fun creationDraftOwnsTheFooterAndSurvivesRecreationUntilExplicitSave() {
        val project=runBlocking {app.projectRepository.createProject("Núcleo")}
        val restoration=StateRestorationTester(compose)
        restoration.setContent {Arachn0deTheme {ProjectNodeScreen(project,app.nodeRepository,{})}}
        await {compose.onAllNodesWithTag("core-context:Creation").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Creation").performClick()
        compose.onNodeWithText("Nuevo",substring=false).performClick()
        await {compose.onAllNodesWithTag("quick-create-title").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("quick-create-title").performTextReplacement("Borrador intacto")
        compose.onNodeWithTag("core-bottom-module").assertDoesNotExist()
        compose.onNodeWithContentDescription("Opciones del proyecto").assertDoesNotExist()
        assertTrue(runBlocking {app.nodeRepository.getProjectNodes(project.id).isEmpty()})
        restoration.emulateSavedInstanceStateRestore()
        await {compose.onAllNodesWithTag("quick-create-title").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("quick-create-title").assert(hasText("Borrador intacto"))
        compose.onNodeWithTag("quick-create-save").performClick()
        await {runBlocking {app.nodeRepository.getProjectNodes(project.id).any {it.title=="Borrador intacto"}}}
        // The committed row can appear before the save callback restores the footer.
        await {runCatching {compose.onNodeWithTag("core-context:Creation").assertIsNotSelected()}.isSuccess}
    }
    @Test fun longOptionsScrollAboveAnAccessibleFixedSelectorWithLargeFonts() {
        var context by mutableStateOf<CoreBottomContext?>(CoreBottomContext.Options)
        var clicks=0
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.7f)) {
                Box(Modifier.width(300.dp).height(350.dp)) {
                    CoreBottomModule(context,{context=it},350.dp,contextActions={
                        repeat(18) {i->action("Acción larga $i",Icons.Default.Info,{clicks++},enabled=i!=0)}
                    },creationActions={Text("Crear y Capas existentes")})
                }
            }
        }}
        compose.onNodeWithTag("context-action:Acción larga 0").assertIsNotEnabled()
        compose.onNodeWithTag("context-action:Acción larga 17").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1,clicks)
        CoreBottomContext.entries.forEach {compose.onNodeWithTag("core-context:${it.name}").assertIsDisplayed().assertHeightIsAtLeast(48.dp)}
        compose.onNodeWithTag("core-context:Creation").performClick()
        compose.onNodeWithText("Crear y Capas existentes").assertIsDisplayed()
        compose.onNodeWithTag("context-action:Acción larga 17").assertDoesNotExist()
    }
    @Test fun contentFieldsLeadTheGridAndDispatchTheRequestedEditorField() {
        val opened=mutableListOf<String>()
        compose.setContent {Arachn0deTheme {
            CoreBottomModule(CoreBottomContext.Content,{},350.dp,
                contextActions={action("Planificación",Icons.Default.Info,{})},
                creationActions={},onEditProject={opened.add(it)})
        }}
        val title=compose.onNodeWithTag("context-action:Título").fetchSemanticsNode().boundsInRoot
        val description=compose.onNodeWithTag("context-action:Descripción").fetchSemanticsNode().boundsInRoot
        val planning=compose.onNodeWithTag("context-action:Planificación").fetchSemanticsNode().boundsInRoot
        assertEquals(title.top,description.top,1f)
        assertTrue(title.left<description.left)
        assertTrue(title.top<planning.top)
        compose.onNodeWithTag("context-action:Título").performClick()
        compose.onNodeWithTag("context-action:Descripción").performClick()
        assertEquals(listOf("title","description"),opened)
    }
    @Test fun cancelIsAbsentOnlyInIntegratedOptionsAndRemainsInModalMenus() {
        val project=runBlocking {app.projectRepository.createProject("Núcleo")}
        var modal by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {
            if(modal) ContextActionMenu("Menú modal",{modal=false}) {}
            else ProjectNodeScreen(project,app.nodeRepository,{})
        }}
        await {compose.onAllNodesWithTag("core-context:Options").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Options").performClick()
        compose.onNodeWithTag("context-action:Cancelar").assertDoesNotExist()
        compose.onNodeWithTag("context-action:Copiar").assertExists()
        compose.runOnUiThread {modal=true}
        compose.onNodeWithTag("context-action:Cancelar").assertIsDisplayed().performClick()
        compose.onNodeWithTag("context-action:Cancelar").assertDoesNotExist()
    }

    @Test fun propertyEditorsRestoreCancelAndConfirmWithinContent() {
        val application=app
        val repository=application.projectRepository
        val nodes=application.nodeRepository
        val project=runBlocking {repository.createProject("Original","Descripción actual")}
        val restore=StateRestorationTester(compose)
        restore.setContent {Arachn0deTheme {
            val projects by remember(repository) {repository.observeProjects()}.collectAsState(initial=listOf(project))
            ProjectNodeScreen(projects.first {it.id==project.id},nodes,{})
        }}
        await {compose.onAllNodesWithTag("core-context:Content").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Content").performClick()
        compose.onNodeWithTag("context-action:Título").performClick()
        compose.onNodeWithTag("modular-text-field").assert(hasText("Original")).assertIsFocused()
        compose.onNodeWithText("Editar proyecto").assertDoesNotExist()
        compose.onNodeWithTag("core-context:Options").assertDoesNotExist()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("")
        compose.onNodeWithText("Confirmar").assertIsNotEnabled()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Borrador")
        restore.emulateSavedInstanceStateRestore()
        await {compose.onAllNodesWithTag("modular-text-field").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("modular-text-field").assert(hasText("Borrador"))
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals("Original",runBlocking {repository.getProject(project.id)!!.name})
        compose.onNodeWithTag("core-context:Content").assertIsSelected()
        compose.onNodeWithTag("context-action:Título").performClick()
        compose.onNodeWithTag("modular-text-field").assert(hasText("Original")).performTextReplacement("Título nuevo")
        compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {repository.getProject(project.id)!!.name=="Título nuevo"}}
        await {compose.onAllNodesWithTag("core-context:Content").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Título nuevo",substring=false).assertExists()
        compose.onNodeWithTag("context-action:Descripción").performClick()
        compose.onNodeWithTag("modular-text-field").assert(hasText("Descripción actual")).assertIsFocused().performTextReplacement("Descripción descartada")
        back()
        compose.onNodeWithTag("core-context:Content").assertIsSelected()
        assertEquals("Descripción actual",runBlocking {repository.getProject(project.id)!!.description})
        compose.onNodeWithTag("context-action:Descripción").performClick()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Descripción nueva")
        compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {repository.getProject(project.id)!!.description=="Descripción nueva"}}
        await {compose.onAllNodesWithTag("core-context:Content").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Content").assertIsSelected()
        assertEquals("Título nuevo",runBlocking {repository.getProject(project.id)!!.name})
    }

    @Test fun propertySaveMergesLatestValuesAndRejectsConflictsOrMissingCore()=runBlocking {
        val repository=app.projectRepository
        val project=repository.createProject("Original","Descripción")
        repository.setPinned(project.id,true)
        repository.updateProject(project.id,"Original","Cambio externo")
        assertTrue(repository.updateField(project.id,"title","Original","Nuevo"))
        val changed=repository.getProject(project.id)!!
        assertEquals("Cambio externo",changed.description)
        assertTrue(changed.isPinned)
        repository.updateProject(project.id,"Título externo",changed.description)
        assertTrue(repository.updateField(project.id,"description","Cambio externo","Descripción nueva"))
        assertEquals("Título externo",repository.getProject(project.id)!!.name)
        assertTrue(runCatching {repository.updateField(project.id,"title","Nuevo","Sobrescritura")}.isFailure)
        assertTrue(runCatching {repository.updateField(project.id,"title","Título externo"," ")}.isFailure)
        assertEquals("Título externo",repository.getProject(project.id)!!.name)
        assertFalse(repository.updateField("inexistente","title","","Nuevo"))
    }

    @Test fun eachMainActionTogglesAndGesturesRestoreOnlyTheLastSection() {
        var context by mutableStateOf<CoreBottomContext?>(null)
        compose.setContent {Arachn0deTheme {CoreBottomModule(context,{context=it},350.dp,
            contextActions={action("Acción de prueba",Icons.Default.Info,{})},creationActions={Text("Creación existente")},onEditProject={})}}
        fun collapsed() {
            CoreBottomContext.entries.forEach {compose.onNodeWithTag("core-context:${it.name}").assertIsNotSelected().assertHeightIsAtLeast(48.dp)}
            compose.onNodeWithTag("core-panel-fold").assertDoesNotExist()
            assertTrue(compose.onNodeWithTag("core-bottom-module").fetchSemanticsNode().boundsInRoot.height<=54*compose.activity.resources.displayMetrics.density)
        }
        collapsed()
        CoreBottomContext.entries.forEach {entry->
            compose.onNodeWithTag("core-context:${entry.name}").performClick().assertIsSelected()
            compose.onNodeWithTag("core-context:${entry.name}").performClick();collapsed()
        }
        compose.onNodeWithTag("core-context:Content").performClick()
        compose.onNodeWithTag("core-context:Options").performClick()
        compose.onNodeWithTag("context-action:Título").assertDoesNotExist()
        compose.onNodeWithTag("context-action:Acción de prueba").assertExists()
        compose.onNodeWithTag("core-panel-fold").performTouchInput {swipe(center,center+androidx.compose.ui.geometry.Offset(0f,70f),300)}
        collapsed()
        compose.onNodeWithTag("core-context:Content").performTouchInput {swipe(center,center-androidx.compose.ui.geometry.Offset(0f,70f),300)}
        compose.onNodeWithTag("core-context:Options").assertIsSelected()
        compose.onNodeWithTag("core-context:Content").assertIsNotSelected()
    }

}
