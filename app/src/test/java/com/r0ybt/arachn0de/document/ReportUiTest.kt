package com.r0ybt.arachn0de.document

import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.ui.ProjectNodeScreen
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.sharing.SharingTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReportUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun click(text:String) {val n=compose.onNode(hasText(text,substring=true) and hasClickAction());runCatching {n.performScrollTo()};n.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) {it()}}
    private fun ready() {compose.waitUntil(15000) {compose.onAllNodesWithTag("document-preview").fetchSemanticsNodes().isNotEmpty()}}
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount)(root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        val process=Arachn0deApplication::class.java.getDeclaredField("processScope").apply {isAccessible=true}.get(app) as CoroutineScope
        runBlocking {process.coroutineContext[Job]?.cancelAndJoin();app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin()}
        app.database.close()
    }
    @Test fun coreOptionsOpenActualPreviewAndBackReturnsWithoutChangingRecords() {
        val p=runBlocking {app.projectRepository.createProject("Núcleo informe","Descripción original")}
        val layer=runBlocking {app.nodeRepository.createNode(p.id,null,"Capa",purpose=NodePurpose.LAYER)}
        compose.setContent {Arachn0deTheme {ProjectNodeScreen(p,app.nodeRepository,{})}}
        compose.waitUntil(15000) {compose.onAllNodesWithTag("core-context:Options").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("core-context:Options").performClick()
        compose.onNodeWithTag("context-action:Exportar PDF / Word").performScrollTo().performClick();ready()
        compose.onNodeWithTag("report-export").assertIsEnabled()
        compose.onNode(hasSetTextAction() and hasText("Núcleo informe")).assertExists()
        click("Volver")
        compose.onNodeWithTag("document-report").assertDoesNotExist()
        assertEquals("Núcleo informe",runBlocking {app.database.projectDao().getById(p.id)!!.name});assertNotNull(runBlocking {app.database.nodeDao().getById(layer.id)})
    }
    @Test fun pinchPanAndPageSwipeKeepPreviewReadableWithoutChangingTheDocument() {
        val data=ReportContent(listOf(ReportSection("Raíz",0,"",emptyList(),listOf(ReportPart.Text("Texto pequeño legible. ".repeat(4000))))),emptyMap())
        val plan=ReportLayout.measure(data,ReportOptions("Informe",index=false))
        var page by mutableIntStateOf(0)
        compose.setContent {Arachn0deTheme {ReportPreview(data,plan,page,{page=it},androidx.compose.ui.Modifier.fillMaxSize())}}
        fun current()=compose.onNodeWithTag("document-preview-page:$page")
        current().assert(hasStateDescription("Página 1; zoom 100 %; desplazamiento 0,0"))
        current().performTouchInput {pinch(start0=center+androidx.compose.ui.geometry.Offset(-30f,0f),start1=center+androidx.compose.ui.geometry.Offset(30f,0f),end0=center+androidx.compose.ui.geometry.Offset(-100f,0f),end1=center+androidx.compose.ui.geometry.Offset(100f,0f))}
        val zoomed=current().fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
        assertFalse(zoomed,zoomed.contains("zoom 100 %"))
        current().performTouchInput {swipe(center,center+androidx.compose.ui.geometry.Offset(55f,70f))}
        assertEquals(0,page)
        val moved=current().fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
        assertNotEquals(zoomed,moved)
        current().performTouchInput {pinch(start0=center+androidx.compose.ui.geometry.Offset(-120f,0f),start1=center+androidx.compose.ui.geometry.Offset(120f,0f),end0=center+androidx.compose.ui.geometry.Offset(-5f,0f),end1=center+androidx.compose.ui.geometry.Offset(5f,0f))}
        current().assert(hasStateDescription("Página 1; zoom 100 %; desplazamiento 0,0"))
        compose.onNodeWithTag("document-preview").performTouchInput {swipeUp()}
        compose.waitUntil(10000) {page==1}
        assertTrue(plan.pages.size>1)
    }
    @Test fun layerPreviewEditsDocumentOptionsAndRejectsInvalidDateWithoutWritingSources() {
        val p=runBlocking {app.projectRepository.createProject("Núcleo")}
        val layer=runBlocking {app.nodeRepository.createNode(p.id,null,"Capa informe",purpose=NodePurpose.LAYER)}
        runBlocking {app.nodeRepository.createNode(p.id,layer.id,"Tarea")}
        var shown by mutableStateOf(true)
        compose.setContent {Arachn0deTheme {if(shown)DocumentReportDialog(p.id,layer.id) {shown=false}}};ready()
        compose.onNode(hasSetTextAction() and hasText("Capa informe")).performTextReplacement("Informe editado")
        compose.onNode(hasSetTextAction() and hasText(java.time.LocalDate.now().toString())).performTextReplacement("2026-02-31")
        compose.waitUntil(10000) {runCatching {compose.onNodeWithTag("report-export").assertIsNotEnabled()}.isSuccess}
        compose.onNode(hasSetTextAction() and hasText("2026-02-31")).performTextReplacement("2026-10-19")
        click("Salida");click("Word")
        compose.waitUntil(10000) {runCatching {compose.onNodeWithTag("report-export").assertIsEnabled()}.isSuccess}
        compose.onNodeWithText("Exportar Word").assertExists()
        click("Imágenes");click("Izquierda");click("Minimizar");click("Abrir controles")
        click("Volver");compose.onNodeWithTag("document-report").assertDoesNotExist()
        assertEquals("Capa informe",runBlocking {app.database.nodeDao().getById(layer.id)!!.title})
    }
    @Test fun fullscreenPinchAndSystemBackPreserveOptionsAndControlsHaveSpacing() {
        val project=runBlocking {app.projectRepository.createProject("Informe original","Contenido del informe")}
        var closed=false
        compose.setContent {Arachn0deTheme {DocumentReportDialog(project.id,null) {closed=true}}};ready()
        compose.onNode(hasSetTextAction() and hasText("Informe original")).performTextReplacement("Título conservado")
        compose.onNodeWithTag("report-index-switch").performScrollTo().assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
        val toggle=compose.onNodeWithTag("report-index-switch").fetchSemanticsNode().boundsInRoot
        val label=compose.onNodeWithTag("report-index-label",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(label.left-toggle.right>=8*compose.density.density-1)
        val firstTab=compose.onNodeWithTag("report-tab:Documento").fetchSemanticsNode().boundsInRoot
        val secondTab=compose.onNodeWithTag("report-tab:Imágenes").fetchSemanticsNode().boundsInRoot
        assertTrue(secondTab.left-firstTab.right>=8*compose.density.density-1)
        click("Imágenes");click("Izquierda");click("Salida");click("Word")
        compose.waitUntil(15000) {runCatching {compose.onNodeWithTag("report-export").assertIsEnabled()}.isSuccess}
        val small=compose.onNodeWithTag("document-preview-page:0").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("document-preview-page:0").performTouchInput {click(center)}
        compose.onNodeWithTag("report-fullscreen").assertIsDisplayed()
        val large=compose.onNodeWithTag("document-preview-page:0")
        assertTrue(large.fetchSemanticsNode().boundsInRoot.height>small)
        large.performTouchInput {pinch(start0=center+androidx.compose.ui.geometry.Offset(-30f,0f),start1=center+androidx.compose.ui.geometry.Offset(30f,0f),end0=center+androidx.compose.ui.geometry.Offset(-100f,0f),end1=center+androidx.compose.ui.geometry.Offset(100f,0f))}
        assertFalse(large.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription].contains("zoom 100 %"))
        compose.runOnUiThread {
            val owner=(org.robolectric.shadows.ShadowDialog.getLatestDialog() as? androidx.activity.OnBackPressedDispatcherOwner) ?: compose.activity
            owner.onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithTag("report-fullscreen").assertDoesNotExist();assertFalse(closed)
        compose.onNodeWithTag("report-export").assertIsEnabled()
        compose.onNodeWithText("Exportar Word").assertExists()
        click("Imágenes");compose.onNodeWithText("Izquierda").assertIsSelected()
        click("Documento")
        compose.onNode(hasSetTextAction() and hasText("Título conservado")).assertExists()
        assertEquals("Informe original",runBlocking {app.database.projectDao().getById(project.id)!!.name})
    }

}
