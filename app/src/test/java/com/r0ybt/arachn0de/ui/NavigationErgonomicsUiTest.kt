package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.access.*
import com.r0ybt.arachn0de.domain.model.RecurrenceStatus
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w360dp-h800dp")
class NavigationErgonomicsUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun pump() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
    }
    private fun awaitTag(tag:String) = compose.waitUntil(15000) {pump();compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun awaitText(text:String) = compose.waitUntil(15000) {pump();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun mount(content:@Composable ()->Unit) {compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme(content=content)}}}
    private fun preview(name:String,dialog:Boolean=false) {
        compose.runOnIdle {
            val view=if(dialog) org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView else compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val output=java.io.File("build/reports/block1b-$name.png")
            output.parentFile?.mkdirs()
            output.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
    @After fun close() {
        compose.runOnUiThread {
            val content=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until content.childCount) (content.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            content.removeAllViews()
        }
        pump()
        val application=app;val db=application.database
        runBlocking {
            application.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin()
            withTimeout(15000) {db.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}
        }
        db.close()
    }
    @Test fun searchModesStayBelowScrollableResultsAndAboveKeyboardInsets() {
        val index=(0..59).map {ElementResult("node:$it","project","$it","Resultado $it","","Tarea","Proyecto › Capa",false)}
        var query by mutableStateOf("")
        var mode by mutableStateOf("Buscar")
        var keyboard by mutableStateOf(0.dp)
        var opened:String?=null
        mount {
            CompositionLocalProvider(LocalElementAccessState provides AccessState(setOf("node:1"),listOf("node:2"),mapOf("node:3" to 8))) {
                // Deterministic IME + gesture insets exercise the same AppSafeArea contract as AppRoot.
                AppSafeArea(WindowInsets(bottom=keyboard+24.dp)) {
                    ElementSearchScreen(index,mode,query,{query=it},{mode=it},{},{opened=it.nodeId},rememberLazyListState())
                }
            }
        }
        awaitTag("search-results")
        val controls=compose.onNodeWithTag("search-controls").fetchSemanticsNode().boundsInRoot
        val results=compose.onNodeWithTag("search-results").fetchSemanticsNode().boundsInRoot
        assertTrue(results.bottom <= controls.top)
        val options=listOf("Buscar","Favoritos","Recientes","Frecuentes")
        val bounds=options.map {compose.onNodeWithTag("search-mode:$it").fetchSemanticsNode().boundsInRoot}
        bounds.forEach {assertEquals(bounds.first().width,it.width,1f);assertTrue(it.height >= 48f*compose.density.density)}
        assertEquals(bounds[0].top,bounds[1].top,1f)
        assertEquals(bounds[2].top,bounds[3].top,1f)
        assertTrue(bounds[2].top >= bounds[0].bottom)
        compose.onNodeWithTag("search-results").performScrollToNode(hasTestTag("access-result:node:59"))
        compose.onNodeWithTag("access-result:node:59").assertIsDisplayed()
        assertEquals(controls,compose.onNodeWithTag("search-controls").fetchSemanticsNode().boundsInRoot)
        preview("search")
        listOf("Favoritos" to "1","Recientes" to "2","Frecuentes" to "3").forEach {(label,id) ->
            compose.onNodeWithTag("search-mode:$label").performClick()
            awaitTag("access-result:node:$id")
            compose.onAllNodes(hasTestTag("access-result:node:$id")).assertCountEquals(1)
            compose.onNodeWithTag("access-result:node:$id").performClick()
            assertEquals(id,opened)
        }
        compose.onNodeWithTag("search-mode:Buscar").performClick()
        compose.onNodeWithText("Título o descripción").performTextInput("Resultado 59")
        awaitTag("access-result:node:59")
        compose.runOnIdle {keyboard=280.dp}
        compose.waitForIdle()
        val raised=compose.onNodeWithTag("search-controls").fetchSemanticsNode().boundsInRoot
        assertEquals(280f*compose.density.density,controls.bottom-raised.bottom,2f)
        assertTrue(compose.onNodeWithTag("search-results").fetchSemanticsNode().boundsInRoot.bottom <= raised.top)
        options.forEach {compose.onNodeWithTag("search-mode:$it").assertIsDisplayed()}
        compose.onNodeWithTag("access-result:node:59").assertIsDisplayed()
        preview("search-keyboard-insets")
        compose.runOnIdle {keyboard=0.dp}
        compose.waitForIdle()
        assertEquals(controls,compose.onNodeWithTag("search-controls").fetchSemanticsNode().boundsInRoot)
    }
    @Test fun searchAdaptsToNarrowWidthAndLargeTextWithoutClippingControls() {
        mount {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
                Box(Modifier.width(320.dp).height(600.dp)) {
                    ElementSearchScreen(listOf(ElementResult("node:1","p","1","Nombre completo","","Tarea","Proyecto › Capa",false)),"Buscar","",{},{},{},{},rememberLazyListState())
                }
            }
        }
        awaitTag("search-results")
        listOf("Buscar","Favoritos","Recientes","Frecuentes").forEach {compose.onNodeWithTag("search-mode:$it").assertIsDisplayed()}
        val first=compose.onNodeWithTag("search-mode:Buscar").fetchSemanticsNode().boundsInRoot
        val second=compose.onNodeWithTag("search-mode:Favoritos").fetchSemanticsNode().boundsInRoot
        val third=compose.onNodeWithTag("search-mode:Recientes").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top,second.top,1f);assertTrue(third.top >= first.bottom)
        compose.onNodeWithText("Nombre completo").assertIsDisplayed()
        preview("search-large-text")
    }
    @Test fun drawerObligationsIconIsAlignedAndDestinationsKeepTheirCallbacks() {
        var destination=""
        var dismissed=0
        mount {AppSafeArea {AppIdentityDrawer({dismissed++},onProjects={destination="Proyectos"},onMetro={destination="Metro"},
            onObligations={destination="Obligaciones"},onCalendar={destination="Calendario"},onAttention={destination="Avisos"},onRecurrences={destination="Recurrencias"})}}
        compose.onNodeWithText("Proyectos").performClick();assertEquals("Proyectos",destination)
        compose.onNodeWithText("Metro").performClick();assertEquals("Metro",destination)
        compose.onNodeWithText("Seguimiento").performClick()
        val icon=compose.onNodeWithTag("drawer-obligations-icon",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val row=compose.onNodeWithTag("drawer-obligations").fetchSemanticsNode().boundsInRoot
        assertEquals(24f*compose.density.density,icon.width,1f)
        assertEquals(24f*compose.density.density,icon.height,1f)
        assertEquals(row.center.y,icon.center.y,1f)
        preview("drawer")
        listOf("Recurrencias","Avisos","Obligaciones","Calendario").forEach {label ->
            compose.onNodeWithText(label).performScrollTo().performClick();assertEquals(label,destination)
        }
        assertEquals(6,dismissed)
    }
    @Test fun recurrenceSheetWrapsEmptyContentAndScrollsLongListsKeepingStatesAndEditor() {
        val repo=app.nodeRepository.recurrence
        val project=runBlocking {app.projectRepository.createProject("Proyecto").id}
        mount {AppSafeArea {RecurrenceManager(repo,project,emptyList(),emptyList(),true)}}
        compose.onNodeWithText("Recurrencias").performClick();awaitTag("recurrence-list")
        val emptyHeight=compose.onNodeWithTag("recurrence-sheet").fetchSemanticsNode().boundsInRoot.height
        assertTrue(emptyHeight < 400f*compose.density.density)
        compose.onNodeWithText("Crea una regla desde Nuevo elemento → Tarea → Recurrencia.").assertIsDisplayed()
        preview("recurrences-empty",true)
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithTag("recurrence-sheet").assertDoesNotExist()
        val ids=runBlocking {(0..39).map {i ->
            val draft=EditorDraft(null,null,"Regla %02d".format(i),"").apply {recurrenceFrequency="MONTHLY";recurrenceStart="2090-01-10"}
            repo.create(draft.recurrenceRule(project)!!)
        }.also {repo.setStatus(it[0],RecurrenceStatus.PAUSED);repo.setStatus(it[1],RecurrenceStatus.FINISHED)}}
        val before=runBlocking {app.database.recurrenceDao().rules()}
        compose.onNodeWithText("Recurrencias").performClick();awaitTag("recurrence-list")
        compose.onNodeWithTag("recurrence-list").performScrollToNode(hasText("Regla 00 · Pausada"))
        compose.onNodeWithText("Regla 00 · Pausada").assertIsDisplayed()
        compose.onNodeWithTag("recurrence-list").performScrollToNode(hasText("Regla 01 · Finalizada"))
        compose.onNodeWithText("Regla 01 · Finalizada").assertIsDisplayed()
        preview("recurrences",true)
        compose.onNodeWithTag("recurrence-list").performScrollToNode(hasText("Regla 39 · Activa"))
        compose.onNodeWithText("Regla 39 · Activa").assertIsDisplayed()
        compose.onNodeWithText("Cerrar").assertIsDisplayed()
        compose.onNodeWithText("Regla 39 · Activa").performClick()
        awaitText("Pausar recurrencia")
        compose.onNodeWithTag("recurrence-sheet").assertDoesNotExist()
        compose.onNodeWithText("Cerrar").performClick();awaitTag("recurrence-sheet")
        compose.onNodeWithText("Cerrar").performClick()
        assertEquals(before,runBlocking {app.database.recurrenceDao().rules()})
        assertEquals(40,ids.size)
        assertTrue(runBlocking {app.nodeRepository.getProjectNodes(project).isEmpty()})
    }
}
