package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.math.BigInteger
import java.util.Locale

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class CompactPreviewUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun dispose() {compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()}}
    private fun await(text:String) {compose.waitUntil(15000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    @Test fun titleAndIndicatorsStayCompactWithoutDescriptionsOrEmptyRows() {
        val nodes=runBlocking {val p=app.projectRepository.createProject("Prueba")
            listOf(app.nodeRepository.createNode(p.id,null,"Título breve","Contenido que debe quedarse en el detalle"),
                app.nodeRepository.createNode(p.id,null,"Título breve",dueAt=2000000000000),
                app.nodeRepository.createNode(p.id,null,"Título extenso con palabras que deben envolver y mantener los controles accesibles")) }
        compose.setContent {Arachn0deTheme {Column(Modifier.width(380.dp)) {nodes.forEach {node->
            NodeCard(node,null,false,true,{}, {}, {},false,false,{_,done->done()}, {},
                modifier=Modifier.testTag("preview:${node.id}"),showOptions=false)
        }}}}
        compose.onNodeWithText("Contenido que debe quedarse en el detalle").assertDoesNotExist()
        compose.onNodeWithTag("card-indicators:node:${nodes[0].id}", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("card-indicators:node:${nodes[1].id}", useUnmergedTree = true).assertExists()
        val bounds=nodes.map {compose.onNodeWithTag("preview:${it.id}").fetchSemanticsNode().boundsInRoot}
        assertEquals(bounds[0].height,bounds[1].height,2f)
        assertTrue(bounds[2].height>=bounds[0].height)
        assertTrue(bounds[2].height<bounds[0].height*3)
        runBlocking {assertEquals("Contenido que debe quedarse en el detalle",app.nodeRepository.getNode(nodes[0].id)!!.description)}
    }
    @Test fun longTitleRemainsWholeWithLargeFont() {
        val node=runBlocking {val p=app.projectRepository.createProject("Accesibilidad")
            app.nodeRepository.createNode(p.id,null,"Título extenso con palabras que deben envolver y mantener los controles accesibles")}
        compose.setContent {Arachn0deTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
                Box(Modifier.width(280.dp)) {NodeCard(node,null,false,true,{}, {}, {},false,false,{_,done->done()}, {},showOptions=false)}
            }
        }}
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(node.title,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertTrue(layouts.single().lineCount>3)
        assertFalse(layouts.single().hasVisualOverflow)
    }
    @Test fun descriptionIsHiddenInListButPreservedInRealDetail() {
        val description="Contenido íntegro reservado para el detalle y el editor."
        runBlocking {val p=app.projectRepository.createProject("Proyecto de prueba","Descripción del proyecto")
            app.nodeRepository.createNode(p.id,null,"Tarea de prueba",description)}
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        await("Proyecto de prueba");compose.onNodeWithText("Descripción del proyecto").assertDoesNotExist()
        compose.onNodeWithText("Proyecto de prueba").performClick();await("Tarea de prueba")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Tarea de prueba"))
        compose.onNode(hasText(description) and hasAnyAncestor(hasTestTag("nodes-list"))).assertDoesNotExist()
        compose.onNodeWithText("Tarea de prueba").performClick();await(description)
        compose.onNodeWithText(description).assertExists()
    }
    @Test fun largeFinancialAmountsAndDifferentCurrenciesRemainWholeAtLargeFont() {
        val amount=BigInteger("9999999999999999999999999999999999999999")
        val summary=FinancialSummary(mapOf("USD" to CurrencyTotals(pendingMinor=amount,pendingCount=1,thisMonthPendingMinor=amount),
            "JPY" to CurrencyTotals(pendingMinor=BigInteger.TEN,pendingCount=1,nextMonthPendingMinor=BigInteger.TEN)))
        compose.setContent {Arachn0deTheme {val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.6f)) {
                Column(Modifier.width(240.dp).verticalScroll(rememberScrollState())) {FinancialSummaryCard(summary)}
            }
        }}
        compose.onNodeWithTag("financial-currency:USD").assertExists()
        compose.onNodeWithTag("financial-currency:JPY").assertExists()
        val node=compose.onNodeWithTag("financial-amount:USD:0")
        node.performScrollTo().assertTextContains(Money.format(amount,"USD",Locale.US),substring=true)
        val layout=mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layout)}
        assertTrue(layout.isNotEmpty());assertFalse(layout.single().hasVisualOverflow)
    }
}
