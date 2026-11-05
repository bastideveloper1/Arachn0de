package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w411dp-h891dp")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class TaskDatesLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun date(month:Int)=Calendar.getInstance().apply {clear();set(2026,month,15,13,50)}.timeInMillis

    @Test fun fullWidthDatesNeverCompeteWithRemovalAcrossWidthsFontsAndSharedModes() {
        var width by mutableStateOf(400)
        var scale by mutableStateOf(1f)
        var progressive by mutableStateOf(true)
        val draft=EditorDraft("task",null,"Pago","",startAt=date(Calendar.MAY),dueAt=date(Calendar.NOVEMBER))
        compose.setContent {Arachn0deTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                Column(Modifier.requiredWidth(width.dp)) {TaskDatesEditor(draft,true,remember {TaskDatePickerDraft()},progressive=progressive)}
            }
        }}
        for(case in listOf(Triple(400,1f,true),Triple(280,1.8f,true),Triple(220,2f,true),Triple(280,1.8f,false))) {
            compose.runOnIdle {width=case.first;scale=case.second;progressive=case.third}
            for((label,instant) in listOf("Inicio" to draft.startAt!!,"Vencimiento" to draft.dueAt!!)) {
                val text=compose.onNodeWithText("$label: ${formatTaskDate(instant)}")
                val layouts=mutableListOf<TextLayoutResult>()
                text.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
                val layout=layouts.single()
                assertFalse(layout.hasVisualOverflow)
                assertTrue("Date must retain nearly the complete form width",layout.size.width>=((width-32)*compose.activity.resources.displayMetrics.density).toInt())
                assertTrue("Date must not grow into a vertical column",layout.lineCount<=5)
                val removal=compose.onNodeWithText("Quitar $label")
                assertTrue(removal.fetchSemanticsNode().boundsInRoot.top>=text.fetchSemanticsNode().boundsInRoot.bottom)
            }
            if(width==400 && progressive) {
                val start=compose.onNodeWithTag("option:Inicio").fetchSemanticsNode().boundsInRoot
                val due=compose.onNodeWithTag("option:Vencimiento").fetchSemanticsNode().boundsInRoot
                assertEquals(start.top,due.top,.1f)
            }
        }
    }
    @Test fun togglesRemovalAndConfirmationRetainDatesOnReopenAndKeepFooterAvailable() {
        val context=compose.activity.application
        context.deleteDatabase("arachn0de.db")
        val db=Arachn0deDatabase.create(context)
        try {
            val nodes=NodeRepository(db)
            val task=runBlocking {
                val project=ProjectRepository(db.projectDao(),db).createProject("Pago")
                nodes.createNode(project.id,null,"Obligación",dueAt=date(Calendar.NOVEMBER),obligation=Obligation(100,"CLP"))
            }
            var draft by mutableStateOf(EditorDraft(task.id,null,task.title,"",dueAt=task.dueAt,obligation=task.obligation))
            var cancelled=0;var saved=0
            compose.setContent {Arachn0deTheme {
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,1.6f)) {
                    Box(Modifier.requiredWidth(280.dp)) {
                        NodePropertyEditor(draft,NodeProperty.DATES,true,600.dp,emptyList(),TagState(),TagRepository(db),emptyList(),
                            onCancel={cancelled++},onConfirm={
                                runBlocking {assertTrue(nodes.updateNodeWithDates(task.id,task.title,"",draft.activeStart,draft.activeDue))};saved++
                            })
                    }
                }
            }}
            compose.onNodeWithTag("option:Vencimiento").performClick()
            compose.onNodeWithText("Quitar Vencimiento").assertDoesNotExist()
            assertNull(draft.activeDue);assertEquals(task.dueAt,draft.dueAt)
            compose.onNodeWithTag("option:Vencimiento").performClick()
            compose.onNodeWithText("Vencimiento: ${formatTaskDate(task.dueAt!!)}").assertExists()
            compose.onNodeWithText("Cancelar").assertIsDisplayed().performClick();assertEquals(1,cancelled)
            assertEquals(task.dueAt,runBlocking {nodes.getNode(task.id)!!.dueAt})
            compose.onNodeWithText("Confirmar").assertIsDisplayed().performClick();assertEquals(1,saved)
            val reopened=runBlocking {nodes.getNode(task.id)!!}
            compose.runOnIdle {draft=EditorDraft(reopened.id,null,reopened.title,"",dueAt=reopened.dueAt,obligation=reopened.obligation)}
            compose.onNodeWithText("Vencimiento: ${formatTaskDate(task.dueAt!!)}").assertExists()
            compose.onNodeWithText("Quitar Vencimiento").performScrollTo().performClick()
            assertNull(draft.dueAt);assertFalse(draft.dueEnabled)
            compose.onNodeWithText("Confirmar").performClick()
            assertNull(runBlocking {nodes.getNode(task.id)!!.dueAt})
        } finally {db.close()}
    }
}
