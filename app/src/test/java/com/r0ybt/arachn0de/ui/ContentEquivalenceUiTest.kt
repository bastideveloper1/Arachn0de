package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import com.r0ybt.arachn0de.ui.state.*
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
class ContentEquivalenceUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {
        shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame();block()
    }
    private fun action(label:String)=compose.onNodeWithTag("context-action:$label")
    private fun openContent() {
        await {compose.onAllNodesWithTag("core-context:Content").fetchSemanticsNodes().isNotEmpty()}
        if(!runCatching {compose.onNodeWithTag("core-context:Content").assertIsSelected()}.isSuccess)
            compose.onNodeWithTag("core-context:Content").performClick()
    }
    private fun property(label:String,field:NodeProperty) {
        openContent();await {runCatching {action(label).assertIsEnabled()}.isSuccess}
        action(label).performScrollTo().performClick()
        await {compose.onAllNodesWithTag("node-property-editor:${field.name}").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun root()=runBlocking {app.projectRepository.createProject("Núcleo","Descripción conservada")}
    private fun mount(project:Project,node:String?=null,fontScale:Float=1f) {compose.setContent {Arachn0deTheme {
        val live by remember(project.id) {app.projectRepository.observeProjects()}.collectAsState(initial=listOf(project))
        val density=LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {
            ProjectNodeScreen(live.firstOrNull {it.id==project.id} ?: project,app.nodeRepository,{},openNodeId=node)
        }
    }}}
    @After fun close() {
        compose.runOnUiThread {
            val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount)(root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()
        }
        val process=Arachn0deApplication::class.java.getDeclaredField("processScope").apply {isAccessible=true}.get(app) as CoroutineScope
        runBlocking {process.coroutineContext[Job]?.cancelAndJoin();app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin();withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}}
        app.database.close()
    }
    @Test fun quickDraftCaptureShowsOnlyBasicFieldsSavesWithoutProjectAndCancelsWithoutWriting() {
        val application=app
        var open by mutableStateOf(true)
        compose.setContent {Arachn0deTheme {
            if(open) DraftElementEditor(null,application.nodeRepository,application.personRepository,quickCapture=true) {open=false}
            else androidx.compose.material3.Button({open=true}) {androidx.compose.material3.Text("Capturar otra")}
        }}
        await {compose.onAllNodesWithText("Fotografía").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("quick-draft-panel").assertExists()
        compose.onNodeWithTag("node-editor").assertDoesNotExist()
        compose.onNodeWithText("No pago").assertExists()
        compose.onNodeWithContentDescription("Grabar audio").assertExists()
        for(label in listOf("Inicio","Prioritario","Común","Planificación","Tipo: Tarea","Crear varios","Descartar borrador"))
            compose.onNodeWithText(label).assertDoesNotExist()
        compose.onNodeWithContentDescription("Negrita").assertDoesNotExist()
        compose.onNodeWithText("Título").performTextInput("Captura rápida")
        await {runCatching {compose.onNodeWithText("Guardar").assertIsEnabled()}.isSuccess}
        compose.onNodeWithText("Guardar").performClick()
        await {compose.onAllNodesWithText("Capturar otra").fetchSemanticsNodes().isNotEmpty()}
        val saved=runBlocking {application.database.backupDao().nodes().single()}
        assertNull(saved.projectId);assertEquals("Captura rápida",saved.title)
        compose.onNodeWithText("Capturar otra").performClick()
        await {compose.onAllNodesWithText("Fotografía").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Título").performTextInput("Cancelar captura")
        compose.onNodeWithText("Cancelar").performClick()
        await {compose.onAllNodesWithText("Capturar otra").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(listOf(saved),runBlocking {application.database.backupDao().nodes()})
    }
    @Test fun ordinaryLayerTaskAndNoteMenusOpenTheirContentAndSaveTheCorrectTitle() {
        val application=app
        val project=root()
        val nodes=runBlocking {listOf(NodePurpose.LAYER,NodePurpose.ACTION,NodePurpose.NOTE).map {purpose->
            application.nodeRepository.createNode(project.id,null,"Destino ${purpose.name}","Descripción",purpose=purpose)
        }}
        mount(project)
        for(node in nodes) {
            await {compose.onAllNodesWithTag("node-options:${node.id}").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("node-options:${node.id}").performScrollTo().performClick()
            action("Editar").assertDoesNotExist()
            action("Guardar como plantilla").assertExists()
            action("Contenido").assertDoesNotExist()
            compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
            compose.onNode(hasText(node.title) and hasClickAction()).performClick()
            openContent()
            action("Usar Guardado").assertDoesNotExist()
            await {runCatching {action("Título").assertIsEnabled()}.isSuccess}
            action("Título").performScrollTo().performClick()
            compose.onNodeWithTag("modular-text-field").assert(hasText(node.title))
            val title="Actualizado ${node.purpose.name}"
            compose.onNodeWithTag("modular-text-field").performTextReplacement(title)
            compose.onNodeWithText("Confirmar").performClick()
            await {runBlocking {application.nodeRepository.getNode(node.id)!!.title==title}}
            await {compose.onAllNodesWithTag("core-context:Content").fetchSemanticsNodes().isNotEmpty()}
            compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
            await {runCatching {compose.onNodeWithTag("core-context:Content").assertIsNotSelected()}.isSuccess}
            compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
            await {compose.onAllNodesWithTag("node-options:${node.id}").fetchSemanticsNodes().isNotEmpty()}
            val saved=runBlocking {application.nodeRepository.getNode(node.id)!!}
            assertEquals(node.purpose,saved.purpose);assertEquals(node.parentId,saved.parentId)
            assertEquals(node.description,saved.description);assertEquals(node.isCompleted,saved.isCompleted)
        }
    }
    @Test fun exteriorCoreContentOpensTheCorrectOwnerAndReturnsToTheList() {
        val application=app
        val project=root()
        val other=runBlocking {application.projectRepository.createProject("Otro Núcleo")}
        var destination by mutableStateOf<Project?>(null)
        var request by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {
            val current=destination
            if(current==null) ProjectList(listOf(other,project),emptyMap(),{},
                onEdit={error("No debe abrir el editor general")},onDelete={},onOpenProject={destination=it},
                onContent={destination=it;request=true})
            else ProjectNodeScreen(current,application.nodeRepository,{destination=null},
                entryAction=if(request) "content" else null,onEntryActionHandled={request=false})
        }}
        await {compose.onAllNodesWithTag("project-card:${project.id}").fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasContentDescription("Opciones del proyecto") and hasAnyAncestor(hasTestTag("project-card:${project.id}"))).performClick()
        compose.onNodeWithTag("context-action:Editar").assertDoesNotExist()
        action("Contenido").assertDoesNotExist()
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithTag("project-card:${project.id}").performClick()
        openContent()
        assertEquals(project.id,destination?.id)
        action("Título").performClick()
        compose.onNodeWithTag("modular-text-field").assert(hasText(project.name))
        compose.onNodeWithText("Cancelar").performClick()
        await {compose.onAllNodesWithTag("core-context:Content").fetchSemanticsNodes().isNotEmpty()}
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        await {runCatching {compose.onNodeWithTag("core-context:Content").assertIsNotSelected()}.isSuccess}
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        await {compose.onAllNodesWithTag("projects-list").fetchSemanticsNodes().isNotEmpty()}
        assertNull(destination)
        assertEquals(project,runBlocking {application.projectRepository.getProject(project.id)})
    }
    @Test fun coreTitleRetainsItsLimitAndReopensTheCorrectOwner() {
        val project=root();mount(project,fontScale=1.6f);openContent()
        action("Título").performClick()
        await {compose.onAllNodesWithTag("modular-text-field").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("modular-text-field").performTextReplacement("")
        compose.onNodeWithText("Confirmar").assertIsNotEnabled()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(project.name,runBlocking {app.projectRepository.getProject(project.id)!!.name})
        action("Título").performClick()
        val title="Título extenso "+"W".repeat(TitleLimits.PROJECT-15)
        compose.onNodeWithTag("modular-text-field").performTextReplacement(title)
        compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {app.projectRepository.getProject(project.id)!!.name==title}}
        action("Título").performClick()
        compose.onNodeWithTag("modular-text-field").assert(hasText(title))
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(project.description,runBlocking {app.projectRepository.getProject(project.id)!!.description})
    }
    @Test fun corePriorityCancelsSavesReopensAndPreservesOtherFields() {
        val project=root();mount(project,fontScale=1.6f)
        property("Prioridad",NodeProperty.PRIORITY)
        compose.onNodeWithText("Común").performClick()
        assertEquals(listOf("NONE"),runBlocking {corePropertyValues(app,project.id,NodeProperty.PRIORITY)})
        compose.onNodeWithText("Cancelar").performClick()
        property("Prioridad",NodeProperty.PRIORITY);compose.onNodeWithText("Común").performClick();compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {corePropertyValues(app,project.id,NodeProperty.PRIORITY)==listOf("HIGH")}}
        property("Prioridad",NodeProperty.PRIORITY);compose.onNodeWithText("Prioritario").assertExists()
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Prioritario").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertFalse("size=${layouts.single().size} lines=${layouts.single().lineCount} width=${layouts.single().didOverflowWidth} height=${layouts.single().didOverflowHeight} bounds=${compose.onNodeWithText("Prioritario").fetchSemanticsNode().boundsInRoot}",layouts.single().hasVisualOverflow)
        assertTrue(compose.onNodeWithText("Confirmar").fetchSemanticsNode().boundsInRoot.height>=48f*compose.density.density)
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(project,runBlocking {app.projectRepository.getProject(project.id)!!}.copy(updatedAt=project.updatedAt))
        assertTrue(runBlocking {app.database.backupDao().nodes().isEmpty()})
    }
    @Test fun corePeopleCancelsSavesAndReopensWithTheExistingPicker() {
        val project=root();runBlocking {app.personRepository.save("person-test","Responsable de prueba",null)};mount(project)
        property("Responsables",NodeProperty.PEOPLE)
        compose.onNodeWithText("Responsable de prueba").performClick();compose.onNodeWithText("Cancelar").performClick()
        assertTrue(runBlocking {corePropertyValues(app,project.id,NodeProperty.PEOPLE).isEmpty()})
        property("Responsables",NodeProperty.PEOPLE)
        compose.onNodeWithText("Responsable de prueba").performClick();compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {corePropertyValues(app,project.id,NodeProperty.PEOPLE)==listOf("person-test")}}
        property("Responsables",NodeProperty.PEOPLE);compose.onNodeWithText("Responsable de prueba").assertExists();compose.onNodeWithText("Cancelar").performClick()
    }
    @Test fun coreTagsUseTheExistingCatalogAndSaveOnlyThatProperty() {
        val project=root();val tag=runBlocking {app.nodeRepository.tags.create("Etiqueta de prueba")};mount(project)
        property("Etiquetas",NodeProperty.TAGS)
        await {compose.onAllNodesWithTag("tag-choice:${tag.id}").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("tag-choice:${tag.id}").performClick();compose.onNodeWithText("Guardar").performClick()
        await {runBlocking {corePropertyValues(app,project.id,NodeProperty.TAGS)==listOf(tag.id)}}
        property("Etiquetas",NodeProperty.TAGS);compose.onNodeWithTag("tag-choice:${tag.id}").assertIsSelected();compose.onNodeWithText("Cerrar").performClick()
        assertEquals("Descripción conservada",runBlocking {app.projectRepository.getProject(project.id)!!.description})
        assertTrue(runBlocking {corePropertyValues(app,project.id,NodeProperty.PEOPLE).isEmpty()})
    }
    @Test fun coreDescriptionUsesTheAttachmentEditorAndPreservesCancellation() {
        val project=root();mount(project)
        property("Descripción",NodeProperty.DESCRIPTION)
        compose.onNode(hasSetTextAction() and hasText("Descripción conservada")).performTextReplacement("Cambio descartado")
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(project.description,runBlocking {app.projectRepository.getProject(project.id)!!.description})
        property("Descripción",NodeProperty.DESCRIPTION)
        compose.onNode(hasSetTextAction() and hasText("Descripción conservada")).performTextReplacement("Descripción nueva")
        compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {app.projectRepository.getProject(project.id)!!.description=="Descripción nueva"}}
        property("Descripción",NodeProperty.DESCRIPTION);compose.onNode(hasSetTextAction() and hasText("Descripción nueva")).assertExists();compose.onNodeWithText("Cancelar").performClick()
    }
    @Test fun coreImageRemovalCancelsAndPreservesSharedReferencesOnSave() {
        val application=app
        val project=root();val other=runBlocking {application.projectRepository.createProject("Otro Núcleo")}
        val image=java.io.File(application.cacheDir,"core-image.png")
        image.outputStream().use {out->android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888).compress(android.graphics.Bitmap.CompressFormat.PNG,100,out)}
        val repo=com.r0ybt.arachn0de.data.repository.AttachmentRepository(application.database,com.r0ybt.arachn0de.data.local.AttachmentStore(application,syncDirectory=com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory))
        val file=runBlocking {repo.import(android.net.Uri.fromFile(image),"core-seed")}
        val seed=com.r0ybt.arachn0de.ui.state.EditorDraft(project.id,null,project.name,project.description,attachmentDraftId="core-seed").apply {insertImage(file.id)}
        runBlocking {
            repo.saveProjectDraft("core-seed",project.id,seed.description,emptySet()) {application.projectRepository.updateProject(project.id,project.name,it)}
            application.projectRepository.updateProject(other.id,other.name,seed.description)
        }
        mount(project)
        fun remove() {
            property("Descripción",NodeProperty.DESCRIPTION)
            await {compose.onAllNodes(hasSetTextAction() and hasText("core-image.png",substring=true)).fetchSemanticsNodes().isNotEmpty()}
            val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNode(hasSetTextAction() and hasText("Descripción")).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
            val files=runBlocking {application.database.attachmentDao().files()}.associateBy {it.id}
            val transformed=AttachmentVisualTransformation(files).filter(androidx.compose.ui.text.AnnotatedString(seed.description))
            val start=AttachmentReferences.matches(seed.description).first().range.first
            val bounds=layouts.single().getBoundingBox(transformed.offsetMapping.originalToTransformed(start))
            compose.onNodeWithTag("description-inner",useUnmergedTree=true).performTouchInput {click(bounds.center)}
            compose.onNodeWithText("Quitar",substring=false).performClick()
        }
        remove();compose.onNodeWithText("Cancelar").performClick()
        assertEquals(listOf(file.id),runBlocking {repo.filesForProject(project.id).map {it.id}})
        assertTrue(repo.localFile(file).exists())
        remove();compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {repo.filesForProject(project.id).isEmpty()}}
        assertEquals(listOf(file.id),runBlocking {repo.filesForProject(other.id).map {it.id}})
        assertTrue(repo.localFile(file).exists())
        assertTrue(runBlocking {application.database.backupDao().nodes().isEmpty()})
    }
    @Test fun materialSearchDialogWithoutLibraryOrSelectorsSettles() {
        var query by mutableStateOf("")
        compose.setContent {Arachn0deTheme {
            androidx.compose.material3.AlertDialog(onDismissRequest={},title={androidx.compose.material3.Text("Búsqueda aislada")},
                text={androidx.compose.material3.OutlinedTextField(query,{query=it},label={androidx.compose.material3.Text("Buscar")},singleLine=true)},
                confirmButton={androidx.compose.material3.TextButton({}) {androidx.compose.material3.Text("Cerrar")}})
        }}
        compose.onNodeWithText("Buscar").assertExists()
        compose.onNodeWithText("Buscar").performTextInput("Consulta")
        assertEquals("Consulta",query)
    }
    @Test fun savedLibraryAloneLoadsAndClosesWithoutChangingTheDraft() {
        val application=app
        runBlocking {application.savedTemplateRepository.save(com.r0ybt.arachn0de.data.local.SavedTemplateEntity(
            "library-isolation","Guardado aislado",com.r0ybt.arachn0de.templates.SavedTemplateCodec.encode(
                com.r0ybt.arachn0de.templates.SavedTaskConfiguration("Título guardado"))),true)}
        val draft=EditorDraft(null,null,"Original","Descripción")
        var open by mutableStateOf(true)
        compose.setContent {Arachn0deTheme {
            if(open) SavedTemplatesLibrary(draft) {open=false}
            else androidx.compose.material3.Text("Biblioteca cerrada")
        }}
        await {compose.onAllNodesWithText("Guardado aislado").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("Biblioteca cerrada").assertExists()
        assertEquals("Original",draft.title);assertEquals("Descripción",draft.description)
    }
    @Test fun nodeEditorAloneRendersAndClosesWithoutSaving() {
        val application=app
        val project=root()
        val node=runBlocking {application.nodeRepository.createNode(project.id,null,"Original","Descripción",purpose=NodePurpose.NOTE)}
        val draft=EditorDraft(node.id,null,node.title,node.description,purpose=node.purpose)
        var open by mutableStateOf(true)
        compose.setContent {Arachn0deTheme {
            if(open) NodeDialog(draft,false,{open=false},{_,_->error("No debe guardar")})
            else androidx.compose.material3.Text("Editor cerrado")
        }}
        await {compose.onAllNodesWithTag("node-editor").fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasSetTextAction() and hasText("Original")).performTextReplacement("Cambio descartado")
        compose.onNodeWithContentDescription("Cerrar",substring=false).performClick()
        compose.onNodeWithText("Editor cerrado").assertExists()
        assertEquals(node.title,runBlocking {application.nodeRepository.getNode(node.id)!!.title})
    }
}
