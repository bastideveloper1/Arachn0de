package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.sharing.SharingTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class NodePropertyEditingTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {
        shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame();block()
    }
    @After fun close() {
        compose.runOnUiThread {
            val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()
        }
        val process=Arachn0deApplication::class.java.getDeclaredField("processScope").apply {isAccessible=true}.get(app) as CoroutineScope
        runBlocking {process.coroutineContext[Job]?.cancelAndJoin()}
        val database=app.database
        runBlocking {
            app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin()
            withTimeout(15000) {database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}
        }
        database.close()
    }
    @Test fun diagnoseLegacyProjectDialogWithoutReinstallingTheFailedIntegration() {
        val project=runBlocking {app.projectRepository.createProject("Diagnóstico")}
        val editor=EditorDraft(project.id,null,project.name,project.description)
        var showDialog by mutableStateOf(false)
        var dialogCompositions=0
        compose.setContent {Arachn0deTheme {
            ProjectNodeScreen(project,app.nodeRepository,{})
            if(showDialog) {
                SideEffect {dialogCompositions++}
                ProjectDialog(editor,false,{showDialog=false},{_,_->})
            }
        }}
        await {compose.onAllNodesWithTag("core-context:Content").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Content").performClick()
        val changes=java.util.IdentityHashMap<Any,Pair<Int,String>>()
        val observer=androidx.compose.runtime.snapshots.Snapshot.registerApplyObserver {objects,_->synchronized(changes) {
            objects.forEach {obj->
                val value=(obj as? State<*>)?.value
                val text=when(value) {is Boolean,is Number,is androidx.compose.ui.unit.IntSize->value.toString();else->value?.javaClass?.simpleName.orEmpty()}
                val old=changes[obj];changes[obj]=(old?.first ?: 0)+1 to text
            }
        }}
        compose.mainClock.autoAdvance=false
        try {
            compose.runOnUiThread {showDialog=true}
            repeat(40) {
                shadowOf(android.os.Looper.getMainLooper()).idle()
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                compose.mainClock.advanceTimeByFrame()
                Thread.sleep(5)
            }
            println("CORE_DIALOG_DIAGNOSTIC compositions=$dialogCompositions pending="+Recomposer.runningRecomposers.value.map {it.hasPendingWork})
            synchronized(changes) {changes.entries.sortedByDescending {it.value.first}.take(12).forEach {
                println("CORE_DIALOG_STATE ${it.key.javaClass.simpleName}: ${it.value}")
            }}
        } finally {
            observer.dispose();compose.runOnUiThread {showDialog=false};compose.mainClock.autoAdvance=true
        }
        compose.onNodeWithTag("core-context:Content").assertExists()
        assertEquals("Diagnóstico",runBlocking {app.projectRepository.getProject(project.id)!!.name})
    }
    @Test fun layerUsesSharedContextsKeepsOptionsAndRestoresItsDraft() {
        val application=app
        val project=runBlocking {application.projectRepository.createProject("Núcleo")}
        val layer=runBlocking {application.nodeRepository.createNode(project.id,null,"Capa","Texto",purpose=NodePurpose.LAYER)}
        val restore=StateRestorationTester(compose)
        restore.setContent {Arachn0deTheme {ProjectNodeScreen(project,application.nodeRepository,{},openNodeId=layer.id)}}
        await {compose.onAllNodesWithTag("core-context:Creation").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Creation").assertIsNotSelected()
        compose.onNodeWithTag("project-bottom-actions").assertDoesNotExist()
        compose.onNodeWithContentDescription("Opciones del elemento").assertDoesNotExist()
        compose.onNodeWithTag("core-context:Options").performClick()
        listOf("Eliminar","Responsables","Mover a…","Historial","Recurrencias","Convertir en proyecto","Copiar este elemento","Copiar texto con descendientes").forEach {
            compose.onNodeWithTag("context-action:$it").assertExists()
        }
        compose.onNodeWithTag("context-action:Cancelar").assertDoesNotExist()
        compose.onNodeWithTag("context-action:Valores iniciales").assertDoesNotExist()
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithTag("core-context:Creation").assertIsNotSelected()
        compose.onNodeWithTag("core-context:Content").performClick()
        await {runCatching {compose.onNodeWithTag("context-action:Título").assertIsEnabled()}.isSuccess}
        compose.onNodeWithTag("context-action:Valores iniciales").assertExists()
        compose.onNodeWithTag("context-action:Título").performClick()
        compose.onNodeWithTag("modular-text-field").assert(hasText("Capa")).performTextReplacement("Borrador")
        compose.onNodeWithTag("core-context:Options").assertDoesNotExist()
        restore.emulateSavedInstanceStateRestore()
        await {compose.onAllNodesWithTag("modular-text-field").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("modular-text-field").assert(hasText("Borrador"))
        compose.onNodeWithText("Cancelar").performClick()
        await {compose.onAllNodesWithTag("core-context:Content").fetchSemanticsNodes().isNotEmpty()}
        assertEquals("Capa",runBlocking {application.nodeRepository.getNode(layer.id)!!.title})
        compose.onNodeWithTag("core-context:Content").assertIsSelected()
        compose.onNodeWithTag("context-action:Título").performClick()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Capa nueva")
        compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {application.nodeRepository.getNode(layer.id)!!.title=="Capa nueva"}}
        await {compose.onAllNodesWithTag("core-context:Content").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Content").assertIsSelected()
        assertEquals("Texto",runBlocking {application.nodeRepository.getNode(layer.id)!!.description})
    }

    @Test fun taskContextsPreserveCompletionAndEditOnlyPriority() {
        val application=app
        val repository=application.nodeRepository
        val project=runBlocking {application.projectRepository.createProject("Núcleo")}
        val task=runBlocking {repository.createNode(project.id,null,"Tarea","Texto",startAt=10L,dueAt=20L,obligation=Obligation(100,"CLP"))}
        compose.setContent {Arachn0deTheme {ProjectNodeScreen(project,repository,{},openNodeId=task.id)}}
        await {compose.onAllNodesWithTag("detail-completion:${task.id}").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Creation").assertIsNotSelected()
        compose.onNodeWithTag("project-bottom-actions").assertDoesNotExist()
        compose.onNodeWithContentDescription("Opciones del elemento").assertDoesNotExist()
        compose.onNodeWithTag("detail-completion:${task.id}").performClick()
        await {runBlocking {repository.getNode(task.id)!!.isCompleted}}
        compose.onNodeWithTag("core-context:Options").performClick()
        listOf("Eliminar","Responsables","Mover a…","Historial","Recurrencias","Convertir en...","Exportar o compartir").forEach {
            compose.onNodeWithTag("context-action:$it").assertExists()
        }
        compose.onNodeWithTag("context-action:Cancelar").assertDoesNotExist()
        compose.onNodeWithTag("core-context:Content").performClick()
        listOf("Título","Descripción","Planificación","Común","Pago","Responsables","Etiquetas","Tecnologías").forEach {
            compose.onNodeWithTag("context-action:$it").assertExists()
        }
        await {runCatching {compose.onNodeWithTag("context-action:Común").assertIsEnabled()}.isSuccess}
        compose.onNodeWithTag("context-action:Común").performClick()
        await {runBlocking {repository.getNode(task.id)!!.priority==Priority.HIGH}}
        compose.onNodeWithText("Confirmar").assertDoesNotExist()
        await {runCatching {compose.onNodeWithTag("context-action:Prioritario").assertIsEnabled()}.isSuccess}
        compose.onNodeWithTag("context-action:Prioritario").performClick()
        await {runBlocking {repository.getNode(task.id)!!.priority==Priority.NONE}}
        compose.onNodeWithText("Confirmar").assertDoesNotExist()
        compose.onNodeWithTag("core-context:Options").performClick()
        compose.onNodeWithTag("core-context:Content").performClick()
        compose.onNodeWithTag("context-action:Común").assertExists()
        val latest=runBlocking {repository.getNode(task.id)!!}
        assertTrue(latest.isCompleted)
        assertEquals(task.obligation,latest.obligation)
        assertEquals(task.startAt,latest.startAt)
        assertEquals(task.dueAt,latest.dueAt)
        assertEquals(task.description,latest.description)
    }

    @Test fun noteKeepsRichContentToolsDraftAndCancelConfirmFlow() {
        val application=app
        val repository=application.nodeRepository
        val project=runBlocking {application.projectRepository.createProject("Núcleo")}
        val note=runBlocking {repository.createNode(project.id,null,"Nota","Texto original",purpose=NodePurpose.NOTE)}
        val restore=StateRestorationTester(compose)
        restore.setContent {Arachn0deTheme {ProjectNodeScreen(project,repository,{},openNodeId=note.id)}}
        await {compose.onAllNodesWithTag("core-context:Creation").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Creation").assertIsNotSelected()
        compose.onNodeWithTag("project-bottom-actions").assertDoesNotExist()
        compose.onNodeWithContentDescription("Opciones del elemento").assertDoesNotExist()
        compose.onNodeWithTag("detail-completion:${note.id}").assertDoesNotExist()
        compose.onNodeWithTag("core-context:Options").performClick()
        listOf("Eliminar","Responsables","Mover a…","Historial","Recurrencias","Convertir en tarea","Convertir en capa","Copiar este elemento").forEach {
            compose.onNodeWithTag("context-action:$it").assertExists()
        }
        compose.onNodeWithTag("context-action:Cancelar").assertDoesNotExist()
        compose.onNodeWithTag("core-context:Content").performClick()
        compose.onNodeWithTag("context-action:Pago").assertDoesNotExist()
        await {runCatching {compose.onNodeWithTag("context-action:Descripción").assertIsEnabled()}.isSuccess}
        compose.onNodeWithTag("context-action:Descripción").performClick()
        await {runCatching {compose.onNodeWithText("Confirmar").assertIsEnabled()}.isSuccess}
        listOf("Negrita","Cursiva","Subrayado","Imagen").forEach {compose.onNodeWithContentDescription(it).assertExists()}
        compose.onNodeWithText("Editar elemento").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assert(hasText("Texto original")).performTextReplacement("Borrador enriquecido")
        restore.emulateSavedInstanceStateRestore()
        await {compose.onAllNodesWithTag("node-property-editor:DESCRIPTION").fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasSetTextAction()).assert(hasText("Borrador enriquecido"))
        await {runCatching {compose.onNodeWithText("Cancelar").assertIsEnabled()}.isSuccess}
        compose.onNodeWithText("Cancelar").performClick()
        await {compose.onAllNodesWithTag("core-context:Content").fetchSemanticsNodes().isNotEmpty()}
        assertEquals("Texto original",runBlocking {repository.getNode(note.id)!!.description})
        compose.onNodeWithTag("context-action:Descripción").performClick()
        await {runCatching {compose.onNodeWithText("Confirmar").assertIsEnabled()}.isSuccess}
        compose.onNode(hasSetTextAction()).performTextReplacement("Descripción nueva")
        compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {repository.getNode(note.id)!!.description=="Descripción nueva"}}
        await {compose.onAllNodesWithTag("core-context:Content").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Content").assertIsSelected()
        val latest=runBlocking {repository.getNode(note.id)!!}
        assertEquals("Nota",latest.title)
        assertEquals(NodePurpose.NOTE,latest.purpose)
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithTag("core-context:Creation").assertIsNotSelected()
    }

    @Test fun coreKeepsItsApprovedContextsWithNoUpperMenu() {
        val application=app
        val project=runBlocking {application.projectRepository.createProject("Núcleo")}
        compose.setContent {Arachn0deTheme {ProjectNodeScreen(project,application.nodeRepository,{})}}
        await {compose.onAllNodesWithTag("core-context:Creation").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Opciones del proyecto").assertDoesNotExist()
        compose.onNodeWithTag("core-context:Creation").assertIsNotSelected()
        compose.onNodeWithTag("core-context:Options").performClick()
        compose.onNodeWithTag("context-action:Exportar o compartir").assertExists()
        compose.onNodeWithTag("context-action:Cancelar").assertDoesNotExist()
        compose.onNodeWithTag("core-context:Content").performClick()
        compose.onNodeWithTag("context-action:Título").assertExists()
        compose.onNodeWithTag("context-action:Descripción").assertExists()
        compose.onNodeWithTag("context-action:Valores iniciales").assertExists()
        await {runCatching {compose.onNodeWithTag("context-action:Común").assertIsEnabled()}.isSuccess}
        compose.onNodeWithTag("context-action:Común").performClick()
        await {runBlocking {corePropertyValues(application,project.id,NodeProperty.PRIORITY)==listOf(Priority.HIGH.name)}}
        compose.onNodeWithText("Confirmar").assertDoesNotExist()
        await {runCatching {compose.onNodeWithTag("context-action:Prioritario").assertIsEnabled()}.isSuccess}
        compose.onNodeWithTag("context-action:Prioritario").performClick()
        await {runBlocking {corePropertyValues(application,project.id,NodeProperty.PRIORITY)==listOf(Priority.NONE.name)}}
        compose.onNodeWithText("Confirmar").assertDoesNotExist()

    }

    @Test fun tagsAssignImmediatelyAcrossCoreLayerTaskAndNoteAndSurviveClosing() {
        val application=app
        val repository=application.nodeRepository
        val project=runBlocking {application.projectRepository.createProject("Etiquetas directas")}
        val tags=runBlocking {listOf(repository.tags.create("Una"),repository.tags.create("Dos"))}
        val nodes=runBlocking {listOf(NodePurpose.LAYER,NodePurpose.ACTION,NodePurpose.NOTE).map {
            repository.createNode(project.id,null,it.name,purpose=it)
        }}
        var kind by mutableStateOf(0)
        compose.setContent {Arachn0deTheme {key(kind) {
            ProjectNodeScreen(project,repository,{},openNodeId=nodes.getOrNull(kind-1)?.id)
        }}}
        fun assigned():Set<String> = runBlocking {
            if(kind==0)corePropertyValues(application,project.id,NodeProperty.TAGS).toSet()
            else application.database.tagDao().nodeIds(nodes[kind-1].id).toSet()
        }
        fun open() {
            await {compose.onAllNodesWithTag("core-context:Content").fetchSemanticsNodes().isNotEmpty()}
            val tab=compose.onNodeWithTag("core-context:Content")
            if(runCatching {tab.assertIsSelected()}.isFailure)tab.performClick()
            await {runCatching {compose.onNodeWithTag("context-action:Etiquetas").assertIsEnabled()}.isSuccess}
            compose.onNodeWithTag("context-action:Etiquetas").performClick()
            await {compose.onAllNodesWithTag("node-property-editor:TAGS").fetchSemanticsNodes().isNotEmpty()}
        }
        fun toggle(index:Int,expected:Set<String>) {
            val choice=compose.onNodeWithTag("tag-choice:${tags[index].id}")
            await {runCatching {choice.assertIsEnabled()}.isSuccess}
            choice.performClick()
            await {assigned()==expected && compose.onAllNodesWithTag("node-property-editor:TAGS").fetchSemanticsNodes().isEmpty()}
        }
        fun close() {
            compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
            await {compose.onAllNodesWithTag("node-property-editor:TAGS").fetchSemanticsNodes().isEmpty()}
        }
        for(value in 0..3) {
            compose.runOnIdle {kind=value}
            open()
            listOf("Cancelar","Guardar","Aceptar","Confirmar","Cerrar").forEach {compose.onNodeWithText(it).assertDoesNotExist()}
            toggle(0,setOf(tags[0].id));open();toggle(1,setOf(tags[1].id))
            assertEquals(setOf(tags[1].id),assigned())
            open();compose.onNodeWithTag("tag-choice:${tags[1].id}").assertIsSelected()
            toggle(1,emptySet());assertTrue(assigned().isEmpty())
        }
    }

    @Test fun specializedPropertyFieldsKeepActionsAccessibleAndCancelWithoutWrites() {
        val application=app
        val repository=application.nodeRepository
        val project=runBlocking {application.projectRepository.createProject("Núcleo")}
        val task=runBlocking {
            application.database.personDao().insert(com.r0ybt.arachn0de.data.local.PersonEntity("person","Persona",null))
            application.database.technologyDao().insert(com.r0ybt.arachn0de.data.local.TechnologyEntity("tech","Tecnología",null))
            val tag=repository.tags.create("Etiqueta")
            repository.createNode(project.id,null,"Tarea","Texto",responsibleIds=setOf("person"),tagIds=setOf(tag.id))
        }
        compose.setContent {Arachn0deTheme {ProjectNodeScreen(project,repository,{},openNodeId=task.id)}}
        await {compose.onAllNodesWithTag("core-context:Content").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Content").performClick()
        for(label in listOf("Planificación","Pago","Responsables","Tecnologías")) {
            await {runCatching {compose.onNodeWithTag("context-action:$label").assertIsEnabled()}.isSuccess}
            compose.onNodeWithTag("context-action:$label").performClick()
            if(label!="Etiquetas") compose.onNodeWithText("Confirmar").assertIsDisplayed()
            if(label!="Etiquetas") compose.onNodeWithText("Cancelar").assertIsDisplayed()
            compose.onNodeWithTag("core-context:Options").assertDoesNotExist()
            when(label) {
                "Pago"->compose.onNodeWithTag("obligation-enabled").performClick()
                "Responsables"->compose.onNodeWithTag("person-choice:person").performClick()
                "Etiquetas"->compose.onNodeWithText("Quitar etiqueta").performClick()
                "Tecnologías"->compose.onNodeWithText("Ordenar seleccionadas").performClick()
            }
            compose.onNodeWithText("Cancelar").performClick()
            await {compose.onAllNodesWithTag("core-context:Content").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("core-context:Content").assertIsSelected()
        }
        val current=runBlocking {repository.getNode(task.id)!!}
        assertEquals(task,current)
        assertEquals(listOf("person"),runBlocking {application.database.personDao().assignmentIds(task.id)})
        assertEquals(1,runBlocking {application.database.tagDao().nodeIds(task.id).size})
        assertTrue(runBlocking {application.database.technologyDao().forNodes(listOf(task.id)).isEmpty()})
    }

    @Test fun groupedPropertyRequiresConfirmationAndPreservesOtherMemberFields() {
        val application=app
        val repository=application.nodeRepository
        val project=runBlocking {application.projectRepository.createProject("Núcleo")}
        val members=runBlocking {repository.createBatch(project.id,null,"group",NodeBatchGenerator.generate(
            NodeBatchParameters("Tarea",2,NumberingMode.SUFFIX,description="Base"),java.util.TimeZone.getTimeZone("UTC")))}
        val source=members.first()
        compose.setContent {Arachn0deTheme {ProjectNodeScreen(project,repository,{},openNodeId=source.id)}}
        await {compose.onAllNodesWithTag("core-context:Content").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Content").performClick()
        await {runCatching {compose.onNodeWithTag("context-action:Común").assertIsEnabled()}.isSuccess}
        compose.onNodeWithTag("context-action:Descripción").performClick()
        await {compose.onAllNodesWithTag("node-property-editor:DESCRIPTION").fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasSetTextAction()).performTextReplacement("Descripción compartida")
        compose.onNodeWithText("Confirmar").performClick()
        await {compose.onAllNodesWithText("Todos los elementos del grupo").fetchSemanticsNodes().isNotEmpty()}
        assertTrue(runBlocking {repository.groupMembers("group").all {it.priority==Priority.NONE}})
        compose.onNodeWithText("Todos los elementos del grupo").performClick()
        await {runBlocking {repository.groupMembers("group").all {it.description=="Descripción compartida" && it.priority==Priority.NONE}}}
        val current=runBlocking {repository.groupMembers("group")}
        assertEquals(members.map {it.title},current.map {it.title})
        assertTrue(current.all {it.description=="Descripción compartida" && it.dueAt==null})
        assertTrue(runBlocking {runCatching {repository.updateProperty(source.id,NodeProperty.DESCRIPTION,listOf("Base"),listOf("Cambio obsoleto"),setOf(source.id))}.isFailure})
        assertTrue(runBlocking {repository.groupMembers("group").all {it.description=="Descripción compartida" && it.priority==Priority.NONE}})
    }

    @Test fun propertyPatchesPreserveOtherFieldsAndRejectConcurrentChanges()=runBlocking {
        val repository=app.nodeRepository
        val project=app.projectRepository.createProject("Núcleo")
        for(purpose in NodePurpose.entries) {
            val node=repository.createNode(project.id,null,"Original","Descripción",purpose=purpose)
            repository.updateNode(node.id,node.title,"Descripción externa")
            assertTrue(repository.updateProperty(node.id,NodeProperty.TITLE,listOf("Original"),listOf("Nuevo")))
            val latest=repository.getNode(node.id)!!
            assertEquals("Descripción externa",latest.description)
            assertEquals(node.parentId,latest.parentId)
            assertEquals(node.purpose,latest.purpose)
            assertEquals(node.position,latest.position)
            assertTrue(runCatching {repository.updateProperty(node.id,NodeProperty.TITLE,listOf("Original"),listOf("Pérdida"))}.isFailure)
            assertTrue(runCatching {repository.updateProperty(node.id,NodeProperty.TITLE,listOf("Nuevo"),listOf(" "))}.isFailure)
            repository.updateNode(node.id,"Título externo",latest.description)
            assertTrue(repository.updateProperty(node.id,NodeProperty.DESCRIPTION,listOf(latest.description),listOf("Nueva descripción")))
            assertEquals("Título externo",repository.getNode(node.id)!!.title)
        }
        val task=repository.createNode(project.id,null,"Tarea","Texto",startAt=10L,dueAt=20L,obligation=Obligation(100,"CLP"))
        repository.setCompleted(task.id,true)
        assertTrue(repository.updateProperty(task.id,NodeProperty.PRIORITY,listOf("NONE"),listOf("HIGH")))
        assertTrue(repository.updateProperty(task.id,NodeProperty.DATES,listOf("10","20"),listOf("30","40")))
        val latest=repository.getNode(task.id)!!
        assertTrue(latest.isCompleted)
        assertEquals(Obligation(100,"CLP"),latest.obligation)
        assertEquals(Priority.HIGH,latest.priority)
        assertTrue(runCatching {repository.updateProperty(task.id,NodeProperty.PAYMENT,listOf("100","CLP"),listOf("",""))}.isFailure)
        assertEquals(Obligation(100,"CLP"),repository.getNode(task.id)!!.obligation)
        assertFalse(repository.updateProperty("missing",NodeProperty.TITLE,listOf(""),listOf("Nuevo")))
    }
}
