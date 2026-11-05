package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
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

    @Test fun favoriteIsInformationalAndOptionsKeepTouchTarget() {
        val db=androidx.room.Room.inMemoryDatabaseBuilder(compose.activity,
            com.r0ybt.arachn0de.data.local.Arachn0deDatabase::class.java).build()
        val access=com.r0ybt.arachn0de.access.AccessRepository(db)
        var kind by mutableIntStateOf(0)
        var width by mutableIntStateOf(260)
        var scale by mutableFloatStateOf(1f)
        var selected by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalElementAccessState provides com.r0ybt.arachn0de.access.AccessState(favorites=if(selected)setOf("project:core","node:node") else emptySet()),
                LocalElementAccess provides access,
                LocalDensity provides Density(density.density,scale)) {
                Box(Modifier.width(width.dp)) {
                    if(kind==0) ProjectCard(Project("core","Título extenso que ocupa varias líneas","",0L,0L),ProjectCardAlerts(),{},{},{})
                    else NodeCard(Node("node","core",null,"Título extenso que ocupa varias líneas","",false,0,0,0,false,
                        purpose=when(kind) {1->NodePurpose.LAYER;2->NodePurpose.ACTION;else->NodePurpose.NOTE}),
                        null,false,kind==2,{},{},{},false,false,{_,done->done()},{})
                }
            }
        }}
        try {
            for(type in 0..3) for(size in listOf(260,360)) for(font in listOf(1f,1.5f)) for(marked in listOf(false,true)) {
                compose.runOnIdle {kind=type;width=size;scale=font;selected=marked}
                val favorite=compose.onNodeWithTag("favorite:${if(type==0)"project:core" else "node:node"}")
                val options=if(type==0) compose.onNodeWithContentDescription("Opciones del proyecto") else compose.onNodeWithTag("node-options:node")
                favorite.assertDoesNotExist()
                options.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                val menu=options.fetchSemanticsNode().boundsInRoot
                val title=compose.onNodeWithTag(if(type==0)"project-title:core" else "node-title:node",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                assertTrue(title.right<=menu.left+.5f)
                val icon=compose.onNodeWithTag("card-signal:${if(type==0)"project:core" else "node:node"}:favorite",useUnmergedTree=true)
                if(marked) {
                    val bounds=icon.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    assertEquals(16*compose.density.density,bounds.width,.5f)
                    icon.assert(hasClickAction().not())
                } else icon.assertDoesNotExist()
            }
        } finally {
            compose.runOnUiThread {compose.activity.setContent {}}
            compose.waitForIdle()
            db.close()
        }
    }

    @Test fun bankCardsKeepStableBoundsForShortAndWrappedTitles() {
        var long by mutableStateOf(false)
        var sourceSize by mutableStateOf(400 to 100)
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalBankRootIds provides setOf("bank")) {
                androidx.compose.foundation.layout.Column(Modifier.width(320.dp)) {
                    val title=if(long) "Nombre muy largo de una cuenta bancaria que ocupa varias líneas" else "Banco"
                    ProjectCard(Project("bank",title,"",0L,0L),ProjectCardAlerts(),{},{},{})
                    BankAccountCard(title,"",1000,"account",{})
                }
            }
        }}
        var coreHeight=0f;var accountHeight=0f
        for(size in listOf(400 to 100,100 to 400,200 to 200)) for(wrapped in listOf(false,true)) {
            compose.runOnIdle {sourceSize=size;long=wrapped}
            val core=compose.onNodeWithTag("project-card:bank").fetchSemanticsNode().boundsInRoot
            val account=compose.onNodeWithTag("bank-account:account").fetchSemanticsNode().boundsInRoot
            if(coreHeight==0f) {coreHeight=core.height;accountHeight=account.height}
            assertEquals(coreHeight,core.height,1f);assertEquals(accountHeight,account.height,1f)
        }
    }
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
            assertTrue("tipo=$type etiqueta=$hasTag",card.height>=64*density-1)
            if(!long && !hasTag) assertTrue("tipo=$type sin reserva de etiqueta",card.height<88*density-1)
            val actions=(if(type==0) compose.onNodeWithContentDescription("Opciones del proyecto") else compose.onNodeWithTag("node-options:node")).fetchSemanticsNode().boundsInRoot
            assertEquals(48*density,actions.width,1f);assertEquals(48*density,actions.height,1f)
            assertTrue(card.bottom-actions.bottom>=8*density-1)
            if(hasTag) {
                val badge=compose.onNodeWithTag("card-corner-tag",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                assertEquals(card.top,badge.top,1f);assertEquals(card.right,badge.right,1f)
                assertTrue("tipo=$type largo=$long badge=$badge controls=$actions",badge.bottom<=actions.top)
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
            if(type==0) {
                compose.onNodeWithText("Corta +1",useUnmergedTree=true).assertIsDisplayed()
                compose.onNodeWithContentDescription("Corta, Heredada",useUnmergedTree=true).assertExists()
            } else {
                compose.onNodeWithText("Corta",useUnmergedTree=true).assertIsDisplayed()
                compose.onNodeWithText("Heredada",useUnmergedTree=true).assertIsDisplayed()
            }
        }
    }
    @Test fun recurrenceStaysInIndicatorRowWithoutIncreasingCardHeight() {
        var recurrent by mutableStateOf(false);var opened=0
        compose.setContent {Arachn0deTheme {Box(Modifier.width(320.dp)) {
            NodeCard(Node("rec","core",null,"Tarea equivalente","",false,0,0,0,false,purpose=NodePurpose.ACTION),null,false,true,{},{},{},false,false,{_,done->done()},{},
                modifier=Modifier.testTag("rec-card"),onRecurrence=if(recurrent) ({opened++}) else null)
        }}}
        val before=compose.onNodeWithTag("rec-card").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle {recurrent=true}
        val after=compose.onNodeWithTag("rec-card").fetchSemanticsNode().boundsInRoot.height
        assertEquals(before,after,1f)
        compose.onNodeWithText("↻ Recurrencia").assertDoesNotExist()
        val icon=compose.onNodeWithContentDescription("Recurrente")
        icon.assertIsDisplayed().performClick();assertEquals(1,opened)
        val bounds=icon.fetchSemanticsNode().boundsInRoot
        val row=compose.onNodeWithTag("card-indicators:node:rec",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top>=row.top && bounds.bottom<=row.bottom)
    }

}
