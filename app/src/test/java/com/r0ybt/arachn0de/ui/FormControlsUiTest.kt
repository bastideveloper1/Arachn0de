package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.TechnologyEntity
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w360dp-h800dp")
class FormControlsUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {app.database.close()}
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();block()
    }
    @Test fun compactControlsTogglePriorityAndReplaceLegacyTagsOnlyAfterAnExplicitChoice()=runBlocking<Unit> {
        val a=app.nodeRepository.tags.create("A");val b=app.nodeRepository.tags.create("B")
        val draft=EditorDraft("existing",null,"Tarea","").apply {priority=Priority.LOW;tagIds=listOf(a.id,b.id)}
        var saved=false
        compose.setContent {Arachn0deTheme {NodeDialog(draft,false,{},{_,_->saved=true},tagRepository=app.nodeRepository.tags)}}
        await {compose.onAllNodesWithContentDescription("Etiquetas: A, B").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(listOf(a.id,b.id),draft.tagIds);assertEquals(Priority.LOW,draft.priority)
        val switch=compose.onNodeWithContentDescription("Prioridad: No prioritario")
        switch.performScrollTo().performClick();assertEquals(Priority.HIGH,draft.priority)
        compose.onNodeWithContentDescription("Prioridad: Prioritario").performClick();assertEquals(Priority.NONE,draft.priority)
        val people=compose.onNodeWithContentDescription("Responsables (0)").fetchSemanticsNode().boundsInRoot
        val technology=compose.onNodeWithContentDescription("Tecnologías (0)").fetchSemanticsNode().boundsInRoot
        val tags=compose.onNodeWithContentDescription("Etiquetas: A, B").fetchSemanticsNode().boundsInRoot
        assertEquals(people.center.y,technology.center.y,1f);assertEquals(people.center.y,tags.center.y,1f)
        compose.onNodeWithTag("priority-selector").assertDoesNotExist()
        compose.onNodeWithContentDescription("Etiquetas: A, B").performClick()
        await {compose.onAllNodesWithText("✓ B").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("✓ B").performClick();assertEquals(listOf(b.id),draft.tagIds)
        compose.onNodeWithText("Listo").performClick()
        compose.onNodeWithContentDescription("Etiquetas: B").performClick()
        compose.onNodeWithText("Quitar etiqueta").performClick();assertTrue(draft.tagIds.isEmpty())
        compose.onNodeWithText("A").performClick();assertEquals(listOf(a.id),draft.tagIds)
        compose.onNodeWithText("Listo").performClick()
        compose.onNodeWithText("Guardar").performClick();assertTrue(saved)
    }
    @Test fun technologyAndPeopleSelectorsUseFullScreenAndTechnologyCancelKeepsTheDraft() {
        var selected by mutableStateOf(listOf("tool"))
        var peopleOpen by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {Column {
            TechnologySelection(listOf(TechnologyEntity("tool","Python",null)),selected,true,iconOnly=true) {selected=it}
            androidx.compose.material3.TextButton(onClick={peopleOpen=true}) {androidx.compose.material3.Text("Personas")}
            if(peopleOpen) ResponsibleDialog("node",listOf(Person("p","Ana")),emptyList(),false,{peopleOpen=false}) {peopleOpen=false}
        }}}
        compose.onNodeWithContentDescription("Tecnologías (1)").performClick()
        val technologyDialog=compose.onNode(isDialog() and hasAnyDescendant(hasText("Buscar tecnologías"))).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("technology-tile:tool").performClick();compose.onNodeWithText("Cancelar").performClick()
        assertEquals(listOf("tool"),selected)
        compose.onNodeWithContentDescription("Tecnologías (1)").performClick()
        compose.onNodeWithTag("technology-tile:tool").performClick();compose.onNodeWithText("Confirmar").performClick()
        assertTrue(selected.isEmpty())
        compose.onNodeWithText("Personas").performClick()
        val personDialog=compose.onNode(isDialog() and hasAnyDescendant(hasText("Buscar personas"))).fetchSemanticsNode().boundsInRoot
        assertEquals(personDialog.width,technologyDialog.width,1f)
        assertEquals(personDialog.height,technologyDialog.height,1f)
    }
    @Test fun projectClosingKeepsLegacyTagsAndSavingReplacesThemAndReopensTheChoice()=runBlocking<Unit> {
        val a=app.nodeRepository.tags.create("A");val b=app.nodeRepository.tags.create("B")
        app.projectRepository.saveWithTechnologies(null,"root","Núcleo","",null,setOf(a.id),priority=Priority.HIGH)
        val root=app.database.conversionDao().project("root")!!
        app.database.conversionDao().tags(listOf(com.r0ybt.arachn0de.data.local.ConversionTagEntity(root.id,b.id)))
        var current by mutableStateOf<EditorDraft?>(EditorDraft("root",null,"Núcleo",""))
        var saved=false
        compose.setContent {Arachn0deTheme {
            val scope=rememberCoroutineScope()
            val actions=remember {com.r0ybt.arachn0de.ui.state.ProjectActions(app.projectRepository,scope,app.attachmentRepository)}
            current?.let {draft->ProjectDialog(draft,actions.operation.busy,{current=null},{name,description->
                actions.save(draft.id,name,description,draft.creationId,draft) {saved=true;current=null}
            })}
        }}
        fun openTags() {await {compose.onAllNodesWithContentDescription("Etiquetas: A, B").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithContentDescription("Etiquetas: A, B").performClick()}
        openTags();compose.onNodeWithText("✓ A").performClick();compose.onNodeWithText("Listo").performClick()
        compose.onNodeWithText("Cerrar").performClick()
        assertEquals(setOf(a.id,b.id),app.nodeRepository.tags.observe().first().projectIds["root"])
        compose.runOnIdle {current=EditorDraft("root",null,"Núcleo","")}
        openTags();compose.onNodeWithText("✓ B").performClick();compose.onNodeWithText("Listo").performClick()
        compose.onNodeWithText("Guardar").performClick();await {saved}
        assertEquals(setOf(b.id),app.nodeRepository.tags.observe().first().projectIds["root"])
        assertEquals("HIGH",app.database.conversionDao().project("root")!!.priority)
        compose.runOnIdle {current=EditorDraft("root",null,"Núcleo","")}
        await {compose.onAllNodesWithContentDescription("Etiquetas: B").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(listOf(b.id),current!!.tagIds)
    }
    @Test fun singleTagIsAboveTheCardActionsAndLegacyTagsRemainVisible() {
        val node=Node("node","project",null,"Tarea","",false,0,0,0,false)
        var tags by mutableStateOf(listOf(Tag("a","Evaluar","evaluar")))
        compose.setContent {Arachn0deTheme {NodeCard(node,null,false,false,{},{},{},true,true,{_,done->done()},{},tags=tags)}}
        val badge=compose.onNodeWithText("Evaluar",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val menu=compose.onNodeWithTag("node-options:node").fetchSemanticsNode().boundsInRoot
        assertTrue(badge.bottom<=menu.top);assertTrue(badge.right<=menu.right)
        compose.runOnIdle {tags=tags+Tag("b","Delegar","delegar")}
        compose.onNodeWithText("Evaluar",useUnmergedTree=true).assertExists()
        compose.onNodeWithText("Delegar",useUnmergedTree=true).assertExists()
        compose.runOnIdle {tags=emptyList()}
        compose.onNodeWithText("Evaluar").assertDoesNotExist();compose.onNodeWithText("Delegar").assertDoesNotExist()
    }
}
