package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class HierarchyBatchUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {app.database.close()}
    private fun await(text:String)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    @Test fun onlyHighIsPriorityAndLegacyValuesRemainUntouchedUntilAnExplicitChoice() {
        var value by mutableStateOf<Priority?>(Priority.LOW)
        compose.setContent {Arachn0deTheme {PrioritySelector(value,{value=it})}}
        compose.onNodeWithText("Prioridad: No prioritario").assertExists();assertEquals(Priority.LOW,value)
        compose.onNodeWithTag("priority-selector").performClick()
        compose.onNodeWithTag("priority-option:LOW").assertDoesNotExist();compose.onNodeWithTag("priority-option:MEDIUM").assertDoesNotExist()
        compose.onNodeWithTag("priority-option:HIGH").performClick();assertEquals(Priority.HIGH,value)
        compose.onNodeWithTag("priority-selector").performClick();compose.onNodeWithTag("priority-option:NONE").performClick()
        assertEquals(Priority.NONE,value)
        val base=Node("n","p",null,"Tarea","",false,0,0,0,false)
        for(priority in Priority.entries) {
            assertEquals(priority!=Priority.HIGH,NodeFilter(priority=Priority.NONE).matches(base.copy(priority=priority),emptySet()))
            assertEquals(priority==Priority.HIGH,NodeFilter(priority=Priority.HIGH).matches(base.copy(priority=priority),emptySet()))
        }
    }
    @Test fun noteIndicatorChangesItsMarkWithoutTakingMoreSpaceAndMetroLinksAreNotRendered() {
        var body by mutableStateOf("")
        val node=Node("n","p",null,"Nota","",false,0,0,0,false,purpose=NodePurpose.NOTE)
        compose.setContent {Arachn0deTheme {CompactCardIndicators("node:n",compactNodeFacts(node.copy(description=body),0).copy(journeys=setOf("legacy")))}}
        val icon=compose.onNodeWithTag("card-signal:node:n:notes")
        icon.assert(hasStateDescription("Sin contenido"));val bounds=icon.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("metro-activity:node:n").assertDoesNotExist()
        compose.runOnIdle {body="Contenido"}
        icon.assert(hasStateDescription("Con contenido"));assertEquals(bounds,icon.fetchSemanticsNode().boundsInRoot)
    }
    @Test fun recurrentTaskEditorDoesNotOfferMetroConversionAndKeepsItsOldPriority() {
        val draft=EditorDraft("existing",null,"Recurrente","").apply {
            priority=Priority.MEDIUM;recurrenceFrequency="DAILY";recurrenceStart="2026-10-14"
        }
        compose.setContent {Arachn0deTheme {NodeDialog(draft,false,{},{_,_->})}}
        compose.onNodeWithText("Viaje Metro").assertDoesNotExist();compose.onNodeWithText("Convertir en viaje").assertDoesNotExist()
        compose.onNodeWithContentDescription("Prioridad: No prioritario").performScrollTo().assertIsDisplayed()
        assertEquals(Priority.MEDIUM,draft.priority)
    }
    @Test fun quickDueSavesAndReopensUsingTheExistingDateField()=runBlocking<Unit> {
        val project=app.projectRepository.createProject("Fechas")
        val draft=EditorDraft(null,null,"Vencimiento","").apply {defaultDueMinute=18*60+30}
        var saved by mutableStateOf<Node?>(null)
        lateinit var editing:EditorDraft
        compose.setContent {Arachn0deTheme {Column {
            if(saved==null) QuickCreateBar(draft,false,{
                saved=runBlocking {app.nodeRepository.createNode(project.id,null,draft.title,dueAt=draft.activeDue)}
            },{},{})
            else {
                val row=saved!!
                val reopened=remember(row.id) {EditorDraft(row.id,null,row.title,row.description,dueAt=row.dueAt)}
                editing=reopened
                TaskDatesEditor(reopened,true,remember {TaskDatePickerDraft()})
                androidx.compose.material3.Button(onClick={runBlocking {
                    assertTrue(app.nodeRepository.updateNodeWithDates(row.id,row.title,row.description,row.startAt,reopened.activeDue))
                }}) {androidx.compose.material3.Text("Guardar vencimiento")}

            }
        }}}
        compose.onNodeWithText("Vence").performClick();await("Elegir hora")
        compose.onNodeWithText("Elegir hora").performClick();await("Aplicar")
        compose.onNodeWithText("Aplicar").performClick();compose.onNodeWithText("Listo").performClick()
        compose.onNodeWithTag("quick-create-save").performClick();await("Vencimiento: ${formatTaskDate(saved!!.dueAt!!)}")
        assertEquals(draft.activeDue,app.nodeRepository.getNode(saved!!.id)!!.dueAt)
        compose.onNodeWithText("Vencimiento: ${formatTaskDate(saved!!.dueAt!!)}").performClick();await("Elegir hora")
        compose.onNodeWithText("Elegir hora").performClick();await("Aplicar")
        val inputs=compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("task-time-input")))
        inputs[0].performTextReplacement("19");inputs[1].performTextReplacement("45")
        compose.onNodeWithText("Aplicar").performClick();compose.onNodeWithText("Guardar vencimiento").performClick()
        assertEquals(editing.activeDue,app.nodeRepository.getNode(saved!!.id)!!.dueAt)
        val updated=java.util.Calendar.getInstance().apply {timeInMillis=app.nodeRepository.getNode(saved!!.id)!!.dueAt!!}
        assertEquals(19,updated.get(java.util.Calendar.HOUR_OF_DAY));assertEquals(45,updated.get(java.util.Calendar.MINUTE))
    }
}
