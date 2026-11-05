package com.r0ybt.arachn0de.ux

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.access.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[33],qualifiers="w360dp-h800dp")
class DestinationExplorerTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun index():List<ElementResult> {
        val cores=listOf(Project("p","Universidad","",0,0,0),Project("q","Trabajo","",1,0,0))
        val layer=Node("a","p",null,"Estudios","",false,0,0,0,true,purpose=NodePurpose.LAYER)
        val other=layer.copy(id="b",projectId="q",title="Formación")
        val leaf=Node("leaf","q","b","Lectura","",false,0,0,0,false)
        return ElementSearch.index(cores,listOf(layer,other,leaf)+(0..25).map {leaf.copy(id="task$it",title="Tarea $it")})
    }
    @Test fun globalSearchBreadcrumbsLeafInvalidAndExplicitConfirmationWithoutIntermediateWrites() {
        var chosen:ElementResult?=null;var cancelled=false
        compose.setContent {Arachn0deTheme {DestinationExplorer(index(),setOf("a"),onDismiss={cancelled=true},onConfirm={chosen=it})}}
        compose.onNodeWithTag("destination-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("destination:project:p").performClick()
        compose.onNodeWithTag("destination:node:a").assertDoesNotExist()
        assertNull(chosen)
        compose.onNodeWithTag("destination-search").performTextInput("Formación")
        compose.onNodeWithText("Trabajo").assertExists()
        compose.onNodeWithTag("destination:node:b").performClick()
        assertNull(chosen)
        compose.onNodeWithTag("destination:node:leaf").performClick()
        compose.onNodeWithTag("destination-confirm").assertIsNotEnabled()
        compose.onNodeWithText("› Formación").performClick()
        compose.onNodeWithTag("destination-confirm").assertIsEnabled().performClick()
        assertEquals("b",chosen?.nodeId)
        compose.onNodeWithText("Cancelar").performClick();assertTrue(cancelled)
    }
    @Test fun bottomSearchAndActionsStayOutsideScrollingListWithKeyboard() {
        compose.setContent {Arachn0deTheme {DestinationExplorer(index(),emptySet(),initialKey="node:b",onDismiss={},onConfirm={})}}
        val search=compose.onNodeWithTag("destination-search").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val list=compose.onNodeWithTag("destination-list").fetchSemanticsNode().boundsInRoot
        assertTrue(search.top>=list.bottom)
        compose.onNodeWithTag("destination-list").performScrollToIndex(20)
        assertEquals(search,compose.onNodeWithTag("destination-search").fetchSemanticsNode().boundsInRoot)
        val window=org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
        compose.runOnIdle {
            val insets=androidx.core.view.WindowInsetsCompat.Builder().setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(),androidx.core.graphics.Insets.of(0,0,0,180)).setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(),true).build()
            fun find(v:android.view.View):android.view.View? {
                if(v.javaClass.simpleName=="AndroidComposeView")return v
                if(v is android.view.ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let {return it}
                return null
            }
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(checkNotNull(find(window)),insets)
        }
        compose.onNodeWithTag("destination-search").assertIsDisplayed().performTextInput("Estudios")
        compose.onNodeWithText("Cancelar").assertIsDisplayed();compose.onNodeWithTag("destination-confirm").assertIsDisplayed()
        compose.onNodeWithTag("destination:node:a").assertExists()
    }

    @Test fun deepBreadcrumbsSurviveHorizontalScrollingRepeatedNavigationAndRecomposition() {
        val core=Project("deep","Núcleo profundo","",0,0,0)
        val layers=(0..23).map {i->Node("level$i",core.id,if(i==0)null else "level${i-1}",
            "Nivel $i","",false,0,0,0,i<23,purpose=NodePurpose.LAYER)}
        var entries by mutableStateOf(ElementSearch.index(listOf(core),layers))
        var busy by mutableStateOf(false)
        var confirmations=0;var cancelled=false
        compose.setContent {Arachn0deTheme {DestinationExplorer(entries,emptySet(),initialKey="node:level23",busy=busy,
            onDismiss={cancelled=true},onConfirm={confirmations++})}}
        val path=compose.onNodeWithTag("destination-path")
        repeat(3) {round->
            path.performScrollToIndex(0).performTouchInput {swipeLeft()}
            path.performScrollToIndex(25)
            compose.onNodeWithText(if(round==0)"› Nivel 23" else "› Nivel 23 revisión ${round-1}").assertIsDisplayed()
            compose.runOnIdle {
                entries=entries.map {if(it.key=="node:level23")it.copy(title="Nivel 23 revisión $round") else it}
                busy=true
            }
            compose.onNodeWithText("› Nivel 23 revisión $round").assertIsNotEnabled()
            compose.onNodeWithTag("destination:node:level23").assertIsNotEnabled()
            compose.runOnIdle {busy=false}
            for(level in listOf(16,8,0)) {
                path.performScrollToNode(hasText("› Nivel $level"))
                compose.onNodeWithText("› Nivel $level").performClick()
                compose.onNodeWithTag("destination:node:level$level").assertIsSelected()
            }
            path.performTouchInput {swipeRight()}.performScrollToIndex(0)
            compose.onNodeWithText("Núcleos").performClick()
            compose.onNodeWithTag("destination-confirm").assertIsNotEnabled()
            compose.onNodeWithTag("destination:project:deep").performClick()
            for(level in 0..23)compose.onNodeWithTag("destination:node:level$level").performClick()
            compose.onNodeWithTag("destination:node:level23").assertIsSelected()
            compose.onNodeWithTag("destination-confirm").assertIsEnabled()
        }
        assertEquals(0,confirmations)
        compose.onNodeWithText("Cancelar").performClick()
        assertTrue(cancelled)
        assertEquals(0,confirmations)
    }

    @Test fun repeatedBreadcrumbNavigationBetweenLongListAndNewEmptyLayerDoesNotConfirmMove() {
        val entries=index().let {rows->rows+rows.first {it.key=="node:b"}.copy(key="node:empty",nodeId="empty",title="Nueva vacía")}
        var confirmations=0;var cancelled=false
        compose.setContent {Arachn0deTheme {DestinationExplorer(entries,setOf("a"),initialKey="node:b",
            onDismiss={cancelled=true},onConfirm={confirmations++})}}
        repeat(3) {
            compose.onNodeWithTag("destination-list").performScrollToIndex(20)
            compose.onNodeWithText("› Trabajo").performClick()
            compose.onNodeWithTag("destination:node:empty").performClick()
            compose.onNodeWithTag("destination-confirm").assertIsEnabled()
            compose.onNodeWithText("Núcleos").performClick()
            compose.onNodeWithTag("destination-confirm").assertIsNotEnabled()
            compose.onNodeWithTag("destination:project:q").performClick()
            compose.onNodeWithTag("destination:node:b").performClick()
        }
        assertEquals(0,confirmations)
        compose.onNodeWithText("Cancelar").performClick()
        assertTrue(cancelled)
        assertEquals(0,confirmations)
    }
}
