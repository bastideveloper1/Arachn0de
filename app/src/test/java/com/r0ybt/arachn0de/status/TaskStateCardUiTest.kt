package com.r0ybt.arachn0de.status

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.style.TextDecoration
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.*
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w320dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskStateCardUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun oneControlCyclesWithoutLabelsAndOnlyCompletedTitlesAreStruckAcrossOfficialThemes() {
        val original=Node("task","p",null,"Título de tarea suficientemente largo para ocupar dos líneas","",false,0,1,1,false)
        var node by mutableStateOf(original)
        var count by mutableIntStateOf(3)
        var skin by mutableStateOf(AppearanceTheme.Arachn0de)
        val prefs=AppearancePreferences(compose.activity.getSharedPreferences("state-ui",0))
        compose.setContent {Arachn0deTheme(prefs) {CompositionLocalProvider(LocalAppearancePalette provides skin.palette) {
            NodeCard(node,null,false,true,{},{},{},false,false,{_,_->},{
                val next=TaskStatePolicy.next(node.taskStatus,count)
                node=node.copy(isCompleted=next==TaskStatus.COMPLETED,awaitingVerification=next==TaskStatus.VERIFY)
            },tags=listOf(Tag("tag","Etiqueta","etiqueta")))
        }}}
        for(base in listOf(AppearanceTheme.Arachn0de,AppearanceTheme.DayGrey)) {
            compose.runOnIdle {prefs.setTheme(base);skin=base;node=original;count=3}
            for(expected in listOf(TaskStatus.PENDING,TaskStatus.VERIFY,TaskStatus.COMPLETED,TaskStatus.PENDING)) {
                compose.onAllNodesWithTag("task-state:task").assertCountEquals(1)
                compose.onNodeWithTag("task-state:task").assert(hasStateDescription(expected.label))
                TaskStatus.entries.forEach {compose.onNodeWithText(it.label).assertDoesNotExist()}
                val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                compose.onNodeWithTag("node-title:task",useUnmergedTree=true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
                assertEquals(expected==TaskStatus.COMPLETED,layouts.single().layoutInput.style.textDecoration?.contains(TextDecoration.LineThrough)==true)
                compose.onNodeWithTag("task-state:task").performClick()
            }
            compose.runOnIdle {node=original;count=2}
            compose.onNodeWithTag("task-state:task").performClick().assert(hasStateDescription(TaskStatus.COMPLETED.label))
            compose.onNodeWithTag("task-state:task").performClick().assert(hasStateDescription(TaskStatus.PENDING.label))
        }
    }
    @Test fun verificationRemainsPendingInContainerProgressAndUsesDiscreetMarks() {
        val layer=Node("layer","p",null,"Capa","",false,0,1,1,true,purpose=NodePurpose.LAYER)
        val task=Node("task","p","layer","Verificar","",false,0,1,1,false,awaitingVerification=true)
        val tree=NodeTreeSnapshot(listOf(layer,task))
        assertTrue(tree.progressById.getValue(layer.id).hasPending)
        compose.setContent {Arachn0deTheme {ContainerWorkMark(tree.progressById[layer.id])}}
        compose.onNodeWithContentDescription("Tareas pendientes").assertExists()
        compose.onNodeWithText("Tareas pendientes").assertDoesNotExist()
    }
}
