package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class CardHeaderDistributionTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun identitiesFollowFirstTitleLineAndTagsStayAboveFullSizeActions() {
        var kind by mutableStateOf(0)
        var long by mutableStateOf(false)
        fun caption() = if(long) "Etiqueta muy larga que debe truncarse" else "Corta"
        compose.setContent {Arachn0deTheme {
            val title=if(long) "Un título extenso con varias líneas para comprobar su alineación" else "Título"
            val tags=listOf(Tag("tag",caption(),caption()))
            Box(Modifier.width(320.dp)) {
                if(kind==0) ProjectCard(Project("core",title,"",0L,0L),ProjectCardAlerts(),{},{},{},tags=tags)
                else NodeCard(Node("node","core",null,title,"",false,0,0,0,false,
                    purpose=if(kind==1) NodePurpose.LAYER else NodePurpose.NOTE),null,false,false,{},{},{},false,false,{_,done->done()},{},tags=tags)
            }
        }}
        for(type in 0..2) for(extended in listOf(false,true)) {
            compose.runOnIdle {kind=type;long=extended}
            val density=compose.density.density
            val icon=compose.onNodeWithContentDescription(when(type) {0->"Proyecto sin fotografía";1->"Capa";else->"Nota"},useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            val title=compose.onNodeWithTag(if(type==0) "project-title:core" else "node-title:node",useUnmergedTree=true)
            val bounds=title.fetchSemanticsNode().boundsInRoot
            val layout=mutableListOf<TextLayoutResult>()
            title.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layout)}
            val firstLine=bounds.top+(layout.single().getLineTop(0)+layout.single().getLineBottom(0))/2
            assertEquals(18*density,icon.width,1f);assertEquals(18*density,icon.height,1f)
            assertEquals(firstLine,icon.center.y,1.1f)
            assertEquals(8*density,bounds.left-icon.right,1f)
            val label=compose.onNodeWithText(caption(),useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            val menu=(if(type==0) compose.onNodeWithContentDescription("Opciones del proyecto") else compose.onNodeWithTag("node-options:node")).fetchSemanticsNode().boundsInRoot
            assertTrue(label.bottom<=menu.top)
            assertTrue(menu.width>=48*density-1 && menu.height>=48*density-1)
        }
    }
}
