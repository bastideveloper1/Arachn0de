package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w600dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardCompactionBlockThreeTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun actualCardsKeepOneProgressRowAndActionsClearAcrossWidthsTitlesAndFontSizes() {
        val layer=Node("layer","core",null,"Capa","",false,0,0,0,true,purpose=NodePurpose.LAYER)
        val task=Node("task","core","layer","Tarea","",false,0,0,0,false)
        val progress=NodeProgress("layer",3,5,60,NodeProgressState.PARTIAL)
        var kind by mutableIntStateOf(0)
        var width by mutableIntStateOf(320)
        var scale by mutableFloatStateOf(1f)
        var long by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale),
                LocalContainerProgress provides mapOf("project:core" to progress)) {
                Box(Modifier.width(width.dp).testTag("card-viewport")) {
                    val title=if(long) "Título largo que conserva su distribución y no debe invadir los controles de la tarjeta" else "Breve"
                    if(kind==0) ProjectCard(Project("core",title,"",0L,0L),ProjectCardAlerts(),{},{},{})
                    else {
                        val node=(if(kind==1)layer else task).copy(title=title,description=if(long)"Descripción existente" else "")
                        NodeCard(node,progress,kind==1,kind==2,{},{},{},false,false,{_,done->done()},{},modifier=Modifier.testTag("node-card"))
                    }
                }
            }
        }}
        for(type in 0..2) for(size in listOf(260,360)) for(font in listOf(1f,1.5f)) for(wrapped in listOf(false,true)) {
            compose.runOnIdle {kind=type;width=size;scale=font;long=wrapped}
            val card=compose.onNodeWithTag(if(type==0)"project-card:core" else "node-card").fetchSemanticsNode().boundsInRoot
            val options=if(type==0)compose.onNodeWithContentDescription("Opciones del proyecto") else compose.onNodeWithTag("node-options:${if(type==1)"layer" else "task"}")
            options.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            val controls=options.fetchSemanticsNode().boundsInRoot
            assertTrue(controls.right<=card.right && controls.bottom<=card.bottom)
            val owner=if(type==0)"project:core" else "node:${if(type==1)"layer" else "task"}"
            if(type==2) compose.onNodeWithTag("container-progress:$owner").assertDoesNotExist()
            else {
                val bar=compose.onNodeWithTag("container-progress-bar:$owner").fetchSemanticsNode().boundsInRoot
                val label=compose.onNodeWithTag("container-progress-count:$owner",useUnmergedTree=true)
                label.assertTextContains("3/5",substring=true).assertContentDescriptionContains("60 %",substring=true)
                val text=label.fetchSemanticsNode().boundsInRoot
                assertTrue(bar.width<=48*compose.density.density+1)
                assertTrue(bar.right<=text.left+1)
                assertEquals(bar.center.y,text.center.y,1f)
                assertTrue(text.right<=controls.left+1)
                val layouts=mutableListOf<TextLayoutResult>()
                label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
                assertEquals(1,layouts.single().lineCount)
                assertFalse("type=$type width=$size font=$font long=$wrapped " +
                    "layout=${layouts.single().size} constraints=${layouts.single().layoutInput.constraints} " +
                    "overflowWidth=${layouts.single().didOverflowWidth} overflowHeight=${layouts.single().didOverflowHeight}",layouts.single().hasVisualOverflow)
            }
            compose.onAllNodesWithText("Añadir descripción").assertCountEquals(0)
        }
    }

    @Test fun filteredCountSharesProgressAndSurvivesWhenThereIsNoMeasurableWork() {
        var progress by mutableStateOf(NodeProgress("layer",3,5,60,NodeProgressState.PARTIAL))
        var count by mutableIntStateOf(2)
        compose.setContent {Arachn0deTheme {Box(Modifier.width(150.dp)) {ContainerProgressLine("node:layer",progress,count)}}}
        compose.onNodeWithTag("task-view-count:layer",useUnmergedTree=true)
            .assertContentDescriptionContains("2 tareas en esta vista",substring=true)
            .assertContentDescriptionContains("3 de 5",substring=true)
        val label=compose.onNodeWithTag("task-view-count:layer",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val bar=compose.onNodeWithTag("container-progress-bar:node:layer").fetchSemanticsNode().boundsInRoot
        assertEquals(label.center.y,bar.center.y,1f)
        compose.runOnIdle {progress=NodeProgress("layer",0,0,0,NodeProgressState.NO_WORK)}
        compose.onNodeWithTag("container-progress-bar:node:layer").assertDoesNotExist()
        compose.onNodeWithTag("task-view-count:layer").assertTextEquals("2 tareas")
        compose.runOnIdle {count=0}
        compose.onNodeWithTag("task-view-count:layer").assertTextEquals("0 tareas")
    }

    @Test fun indicatorsKeepInactiveSlotsCompactOrderAndRecurrenceTouchArea() {
        var populated by mutableStateOf(false)
        var opened=0
        val pending=Node("pending","p",null,"Pendiente","",false,0,0,0,false)
        val verify=pending.copy(id="verify",awaitingVerification=true)
        compose.setContent {Arachn0deTheme {
            val facts=if(populated) CompactCardFacts(people=setOf("person"),technologies=setOf("tech"),notes=setOf("note"),
                tasks=setOf("pending","verify"),files=setOf("file"),due=true,today=true,high=true) else CompactCardFacts()
            CompositionLocalProvider(LocalIndicatorDirectory provides IndicatorDirectory(nodes=mapOf(pending.id to pending,verify.id to verify))) {
                Box(Modifier.width(360.dp)) {CompactCardIndicators("preview",facts,onRecurrence={opened++},taskStateCount=3)}
            }
        }}
        val order=listOf("people","technologies","notes","due","files","tasks","verify","today","high","recurrence")
        for(active in listOf(false,true)) {
            compose.runOnIdle {populated=active}
            order.forEach {compose.onNodeWithTag("card-signal:preview:$it",useUnmergedTree=true).assertExists()}
            val bounds=order.map {compose.onNodeWithTag("card-signal:preview:$it",useUnmergedTree=true).getUnclippedBoundsInRoot()}
            bounds.zipWithNext().forEach {(a,b)->assertTrue(a.right<b.left)}
            bounds.take(9).forEach {assertEquals(16f,(it.right-it.left).value,.1f)}
            for(i in 0..7)assertEquals(2f,(bounds[i+1].left-bounds[i].right).value,.1f)
            val files=compose.onNodeWithTag("card-signal:preview:files",useUnmergedTree=true)
            files.assert(hasStateDescription(if(active)"Configurado" else "Sin datos"))
            compose.onNodeWithTag("card-signal:preview:verify",useUnmergedTree=true).assert(hasStateDescription(if(active)"Configurado" else "Sin datos"))
        }
        val recurrence=compose.onNodeWithContentDescription("Recurrente",useUnmergedTree=true)
        val touch=recurrence.fetchSemanticsNode().touchBoundsInRoot
        assertTrue(touch.width>=48*compose.density.density-1)
        assertTrue(touch.height>=48*compose.density.density-1)
        recurrence.performClick()
        assertEquals(1,opened)
    }
    @Test fun taskAndNoteCardsOmitContainedTaskSlotsButKeepMetadataAndOwnVerificationState() {
        var note by mutableStateOf(false)
        var populated by mutableStateOf(false)
        val task=Node("leaf","p",null,"Elemento","",false,0,0,0,false,awaitingVerification=true)
        compose.setContent {Arachn0deTheme {
            val node=task.copy(purpose=if(note)NodePurpose.NOTE else NodePurpose.ACTION)
            val facts=if(populated)CompactCardFacts(tasks=setOf("other"),files=setOf("file"),people=setOf("person"),due=true) else CompactCardFacts()
            CompositionLocalProvider(LocalCompactCardFacts provides mapOf("node:leaf" to facts)) {
                Box(Modifier.width(360.dp)) {NodeCard(node,null,false,true,{},{},{},false,false,{_,done->done()},{})}
            }
        }}
        for(isNote in listOf(false,true)) for(active in listOf(false,true)) {
            compose.runOnIdle {note=isNote;populated=active}
            for(signal in listOf("tasks","verify")) compose.onNodeWithTag("card-signal:node:leaf:$signal",useUnmergedTree=true).assertDoesNotExist()
            val metadata=listOf("people","notes","due","files","today","high")
            val bounds=metadata.map {compose.onNodeWithTag("card-signal:node:leaf:$it",useUnmergedTree=true).assertExists().getUnclippedBoundsInRoot()}
            bounds.zipWithNext().forEach {(left,right)->assertEquals(2f,(right.left-left.right).value,.1f)}
            if(!isNote) compose.onNodeWithTag("task-state:leaf").assert(hasStateDescription("Por verificar"))
        }
    }

}
