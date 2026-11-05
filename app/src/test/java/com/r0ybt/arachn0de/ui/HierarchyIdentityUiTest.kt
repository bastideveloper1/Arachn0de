package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w320dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HierarchyIdentityUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun realCardsKeepIdentitySeparateFromProgressAndTaskControlAcrossStatesAndOfficialThemes() {
        val project=Project("p","Núcleo de prueba","",0,0,0,isPinned=true)
        val layer=Node("layer","p",null,"Capa de prueba","",false,0,1,1,true,purpose=NodePurpose.LAYER,isPinned=true)
        val initial=Node("task","p","layer","Tarea de prueba","",false,0,1,1,false)
        var workPresent by mutableStateOf(true)
        var task by mutableStateOf(initial)
        var custom by mutableStateOf(AppearanceTheme.Arachn0de)
        compose.setContent {Arachn0deTheme {
            val tree=NodeTreeSnapshot(listOf(layer)+if(workPresent) listOf(task) else emptyList())
            CompositionLocalProvider(LocalAppearancePalette provides custom.palette,LocalContainerProgress provides mapOf("project:p" to tree.projectProgressById.getValue("p"),"node:layer" to tree.progressById.getValue("layer"))) {
                Column {
                    ProjectCard(project,ProjectCardAlerts(),{},{},{})
                    NodeCard(layer,tree.progressById["layer"],true,false,{},{},{},false,false,{_,_->},{})
                    if(workPresent) NodeCard(task,null,false,true,{},{},{},false,false,{_,_->},{})
                }
            }
        }}
        for(official in listOf(AppearanceTheme.Arachn0de,AppearanceTheme.DayGrey)) for(status in TaskStatus.entries) {
            compose.runOnIdle {custom=official;task=initial.copy(isCompleted=status==TaskStatus.COMPLETED,awaitingVerification=status==TaskStatus.VERIFY)}
            compose.waitForIdle()
            for((description,summary,pin) in listOf(Triple("Proyecto sin fotografía","p","project-pin:p"),Triple("Capa","layer","node-pin:layer"))) {
                compose.onNodeWithContentDescription(description,useUnmergedTree=true).assertIsDisplayed()
                compose.onNodeWithTag(pin,useUnmergedTree=true).assertIsDisplayed()
                compose.onNodeWithTag("work-summary:$summary",useUnmergedTree=true).assertDoesNotExist()
            }
            compose.onAllNodesWithText("pendientes",substring=true).assertCountEquals(0)
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
            compose.onNodeWithTag("task-state:layer").assertDoesNotExist()
            compose.onAllNodesWithTag("task-state:task").assertCountEquals(1)
            compose.onNodeWithTag("task-state:task").assert(hasStateDescription(status.label))
            val layouts=mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("node-title:task",useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
            assertEquals(status==TaskStatus.COMPLETED,layouts.single().layoutInput.style.textDecoration?.contains(TextDecoration.LineThrough)==true)
        }
        compose.runOnIdle {workPresent=false}
        compose.onNodeWithTag("work-summary:p",useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithTag("work-summary:layer",useUnmergedTree=true).assertDoesNotExist()
    }

}
