package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w400dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactCardUniformityTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun commonBaseHeightRealCornerAndMeasuredClearanceAcrossCardTypes() {
        var kind by mutableStateOf(0)
        var extended by mutableStateOf(false)
        var tagged by mutableStateOf(false)
        var legacy by mutableStateOf(false)
        fun caption()=if(extended) "Etiqueta extensa que debe truncarse sin cubrir acciones" else "Corta"
        compose.setContent {Arachn0deTheme {
            val title=if(extended) "Título extenso con varias líneas para comprobar la distribución de la tarjeta" else "Título"
            val tags=if(!tagged) emptyList() else listOf(Tag("tag",caption(),caption()))+
                if(legacy) listOf(Tag("old","Heredada","heredada")) else emptyList()
            Box(Modifier.width(320.dp)) {
                if(kind==0) ProjectCard(Project("core",title,"",0L,0L),ProjectCardAlerts(),{},{},{},tags=tags)
                else NodeCard(Node("node","core",null,title,"",false,0,0,0,false,
                    purpose=when(kind) {1->NodePurpose.LAYER;2->NodePurpose.ACTION;else->NodePurpose.NOTE}),
                    null,false,kind==2,{},{},{},false,false,{_,done->done()},{},modifier=Modifier.testTag("card"),tags=tags)
            }
        }}
        for(type in 0..3) for(long in listOf(false,true)) for(hasTag in listOf(false,true)) {
            compose.runOnIdle {kind=type;extended=long;tagged=hasTag}
            val density=compose.density.density
            val card=compose.onNodeWithTag(if(type==0) "project-card:core" else "card").fetchSemanticsNode().boundsInRoot
            if(!long) assertEquals("tipo=$type etiqueta=$hasTag",88*density,card.height,1f)
            else assertTrue(card.height>=88*density-1)
            val actions=(if(type==0) compose.onNodeWithContentDescription("Opciones del proyecto") else compose.onNodeWithTag("node-options:node")).fetchSemanticsNode().boundsInRoot
            assertEquals(48*density,actions.width,1f);assertEquals(48*density,actions.height,1f)
            assertEquals(card.bottom-8*density,actions.bottom,1f)
            if(hasTag) {
                val badge=compose.onNodeWithTag("card-corner-tag",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                assertEquals(card.top,badge.top,1f);assertEquals(card.right,badge.right,1f)
                assertTrue(badge.width<=96*density+1 && badge.bottom<=actions.top)
                compose.onNodeWithText(caption(),useUnmergedTree=true).assertIsDisplayed()
            }
            if(type!=2) {
                val title=compose.onNodeWithTag(if(type==0) "project-title:core" else "node-title:node",useUnmergedTree=true)
                val bounds=title.fetchSemanticsNode().boundsInRoot
                val layouts=mutableListOf<TextLayoutResult>()
                title.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
                val icon=compose.onNodeWithContentDescription(when(type) {0->"Proyecto sin fotografía";1->"Capa";else->"Nota"},useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                assertEquals(18*density,icon.width,1f)
                assertEquals(bounds.top+(layouts.single().getLineTop(0)+layouts.single().getLineBottom(0))/2,icon.center.y,1.1f)
                assertEquals(8*density,bounds.left-icon.right,1f)
            }
        }
        for(type in listOf(0,1,3)) {
            compose.runOnIdle {kind=type;tagged=true;extended=false;legacy=true}
            compose.onNodeWithText("Corta",useUnmergedTree=true).assertIsDisplayed()
            compose.onNodeWithText("Heredada",useUnmergedTree=true).assertIsDisplayed()
        }
    }
}
