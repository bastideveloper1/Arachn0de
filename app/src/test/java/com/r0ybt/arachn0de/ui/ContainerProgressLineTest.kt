package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
@Config(application=Application::class,sdk=[28],qualifiers="w400dp-h1000dp")
class ContainerProgressLineTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun cardsReuseNestedProgressUpdateInCompactRowButExcludeEmptyAndLeaves() {
        fun node(id:String,parent:String?,purpose:NodePurpose,done:Boolean=false,verify:Boolean=false)=
            Node(id,"core",parent,id,"",done,0,0,0,false,purpose=purpose,awaitingVerification=verify)
        val layer=node("layer",null,NodePurpose.LAYER)
        val nested=node("nested","layer",NodePurpose.LAYER)
        val note=node("note","layer",NodePurpose.NOTE)
        val task=node("task","layer",NodePurpose.ACTION)
        val initial=listOf(layer,nested,note)+List(5) {i->
            node("task$i",if(i%2==0)"nested" else "layer",NodePurpose.ACTION,done=i<3,verify=i==4)
        }
        var nodes by mutableStateOf(initial)
        compose.setContent {Arachn0deTheme {
            val snapshot=remember(nodes) {NodeTreeSnapshot(nodes)}
            CompositionLocalProvider(LocalContainerProgress provides snapshot.projectProgressById.mapKeys {"project:${it.key}"}) {
                Column(Modifier.width(360.dp)) {
                    ProjectCard(Project("core","Núcleo","",0L,0L),ProjectCardAlerts(),{},{},{})
                    listOf(layer,task,note).forEach {n->
                        NodeCard(n,snapshot.progressById[layer.id],n.isStructural,n.isCompletable,{},{},{},false,false,{_,done->done()},{})
                    }
                }
            }
        }}
        fun check(percent:Int) {
            compose.onAllNodes(hasContentDescription("$percent %",substring=true)).assertCountEquals(2)
            for(owner in listOf("project:core","node:layer")) {
                val bar=compose.onNodeWithTag("container-progress-bar:$owner")
                bar.assertExists().assertHasNoClickAction()
                bar.assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,ProgressBarRangeInfo(percent/100f,0f..1f)))
                assertEquals(4*compose.density.density,bar.fetchSemanticsNode().layoutInfo.height.toFloat(),1f)
            }
            compose.onNodeWithTag("container-progress:node:task").assertDoesNotExist()
            compose.onNodeWithTag("container-progress:node:note").assertDoesNotExist()
        }
        check(60)
        compose.runOnIdle {nodes=initial.map {if(it.purpose==NodePurpose.ACTION)it.copy(isCompleted=true,awaitingVerification=false) else it}}
        check(100)
        compose.runOnIdle {nodes=initial.map {if(it.id=="task0")it.copy(isCompleted=false) else it}}
        check(40)
        compose.runOnIdle {nodes=listOf(layer,nested,note)}
        compose.onNodeWithTag("container-progress:project:core").assertDoesNotExist()
        compose.onNodeWithTag("container-progress:node:layer").assertDoesNotExist()
    }
}
