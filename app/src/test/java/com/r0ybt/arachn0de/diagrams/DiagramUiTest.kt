package com.r0ybt.arachn0de.diagrams

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import com.r0ybt.arachn0de.visual.canvasViewport
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,qualifiers="w400dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagramUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();block()}
    private fun control(text:String)=compose.onAllNodes((hasText(text) or hasText("✓ $text") or hasContentDescription(text)) and hasClickAction()).onFirst()
    private fun click(text:String) {val node=control(text);try {node.performScrollTo()}catch(e:AssertionError) {if(e.message?.contains("no parent layout")!=true)throw e};node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) {it()}}
    private fun documents()=DiagramCodec.library(app.privateContext.getSharedPreferences(DiagramCodec.NAME,0).all).documents
    private fun playbackAction(text:String) {
        control(text).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) {it()}
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(64)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount)(root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        val process=Arachn0deApplication::class.java.getDeclaredField("processScope").apply {isAccessible=true}.get(app) as CoroutineScope
        runBlocking {process.coroutineContext[Job]?.cancelAndJoin();app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin();withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}};app.database.close()
    }
    private fun start(template:String="Flujo básico") {compose.setContent {Arachn0deTheme {DiagramScreen {}}};if(template=="Lienzo vacío")click(template) else {click("Crear desde plantilla");compose.onNodeWithTag("tool-document-gallery").performScrollToNode(hasText(template));click(template)}}
    private fun save():DiagramDocument {
        click("Guardar")
        if(compose.onAllNodesWithText("Nombre del diagrama").fetchSemanticsNodes().isNotEmpty())click("Guardar diagrama")
        await {documents().isNotEmpty()};return documents().single()
    }
    private fun view():com.r0ybt.arachn0de.visual.CanvasViewport {
        val b=compose.onNodeWithTag("diagram-canvas").fetchSemanticsNode().boundsInRoot
        return diagramViewport(b.width,b.height,compose.activity.resources.displayMetrics.density,documents().single().camera!!)
    }
    @Test fun iconLibraryOpensEmptyAndWithExistingIconAndCloses() {
        compose.setContent {Arachn0deTheme {DiagramScreen {}}}
        click("Biblioteca de iconos (0/48)")
        compose.onNodeWithText("Importa imágenes desde el editor para reutilizarlas.").assertIsDisplayed()
        click("Cerrar")
        val bitmap=android.graphics.Bitmap.createBitmap(40,20,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.RED)}
        val bytes=java.io.ByteArrayOutputStream().also {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val preferences=app.privateContext.getSharedPreferences(DiagramCodec.NAME,0)
        val asset=runBlocking {DiagramRepository(preferences).importImage(com.r0ybt.arachn0de.visual.canvasImage(bytes))}
        compose.waitUntil(15000) {compose.onAllNodesWithText("Biblioteca de iconos (1/48)").fetchSemanticsNodes().isNotEmpty()}
        click("Biblioteca de iconos (1/48)")
        compose.onNodeWithContentDescription("Icono importado").assertIsDisplayed()
        compose.onNodeWithText("Icono 1").assertIsDisplayed()
        click("Cerrar")
        assertTrue(DiagramCodec.library(preferences.all).assets.containsKey(asset))
        compose.onNodeWithTag("diagram-library").assertIsDisplayed()
    }
    @Test fun modelGalleryCategoriesAndLateralZoomShareExistingMode() {
        compose.setContent {Arachn0deTheme {DiagramScreen {}}}
        click("Crear desde plantilla")
        compose.onNodeWithText("Lienzo vacío").assertExists()
        compose.onNodeWithTag("diagram-model-category:Usuarios").performScrollTo().performClick()
        compose.onNodeWithTag("tool-document-gallery").performScrollToNode(hasTestTag("tool-document:model:login"))
        compose.onNodeWithTag("tool-document:model:login").performClick()
        compose.onNodeWithTag("diagram-visual:zoom").assertIsOff().performClick().assertIsOn()
        click("Opciones");click("Vista")
        compose.onNodeWithTag("diagram-tool:Zoom y desplazamiento").assertIsSelected()
        // The sidebar and panel have the same accessible label; address the panel by its tool tag.
        compose.onNodeWithTag("diagram-tool:Zoom y desplazamiento").performScrollTo().performClick()
        compose.onNodeWithTag("diagram-visual:zoom").assertIsOff()
        compose.onNodeWithTag("diagram-visual:zoom").performClick()
        compose.onNodeWithTag("diagram-tool:Zoom y desplazamiento").assertIsSelected()
        click("Minimizar");val saved=save()
        assertTrue(saved.nodes.any {it.text.contains("hash")});assertTrue(saved.camera!!.scale>=.45f)
        click("Volver")
        compose.onNodeWithTag("tool-document:${saved.id}").performClick()
        compose.onNodeWithTag("diagram-canvas").assertIsDisplayed()
        click("Guardar");assertEquals(saved.nodes,documents().single().nodes)
    }
    @Test fun libraryTemplateSidebarFoldSaveAndReopenPreserveContent() {
        start();val saved=save()
        compose.onNodeWithTag("diagram-visual:grid").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("diagram-visual:magnet").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("diagram-visual:snap").assertIsOff()
        compose.onNodeWithTag("diagram-visual:fit").performClick()
        click("Opciones");click("Minimizar");click("Guardar")
        click("Volver");compose.onNodeWithText("Mis diseños").assertExists()
        compose.onNodeWithTag("tool-gallery-layout").performClick()
        assertFalse(app.privateContext.getSharedPreferences(com.r0ybt.arachn0de.domain.model.ToolGalleryPreference.NAME,0).getBoolean("diagrams",true))
        compose.onNodeWithTag("tool-document-menu:${saved.id}").performClick();click("Guardar copia")
        await {documents().size==2}
        compose.onNodeWithTag("tool-document-menu:${saved.id}").performClick();click("Eliminar")
        compose.onNodeWithText("Eliminar diagrama").assertExists();click("Cancelar")
        compose.onNodeWithTag("tool-document:${saved.id}").performClick()
        compose.onNodeWithTag("diagram-canvas").assertIsDisplayed()
        click("Guardar");await {documents().size==2}
        assertEquals(saved.nodes,documents().first {it.id==saved.id}.nodes)
        assertEquals(saved.edges,documents().first {it.id==saved.id}.edges)
    }
    @Test fun repeatedConnectedDuplicationKeepsNewestNodeSelectedAndUndoRemovesBoth() {
        start("Lienzo vacío");click("Elementos");click("Rectángulo")
        repeat(4) {click("Duplicar y conectar")}
        click("Editar texto");compose.onNode(hasSetTextAction()).performTextReplacement("Final de cadena");click("Confirmar")
        save();await {documents().singleOrNull()?.nodes?.size==5}
        val chain=documents().single();assertEquals(4,chain.edges.size);assertEquals("Final de cadena",chain.nodes.last().text)
        assertTrue(chain.nodes.dropLast(1).none {it.text=="Final de cadena"})
        click("Opciones");click("Deshacer");click("Deshacer");click("Guardar")
        await {documents().single().nodes.size==4};assertEquals(3,documents().single().edges.size)
        click("Rehacer");click("Guardar");await {documents().single().nodes.size==5};assertEquals(4,documents().single().edges.size)
    }
    @Test fun blankSelectDragDoesNotPanAndNodeDownTargetSurvivesTouchSlop() {
        start("Flujo básico");val saved=save();val n=saved.nodes.first();val v=view()
        compose.onNodeWithTag("diagram-canvas").performTouchInput {swipe(Offset(8f,8f),Offset(80f,80f))}
        click("Guardar");await {documents().single().camera==saved.camera};assertEquals(saved.nodes,documents().single().nodes)
        // Start near the right edge of an unselected card. After slop the pointer is outside it.
        val start=Offset(v.left+(n.x+n.w*.94f)*v.width,v.top+(n.y+n.h*.5f)*v.height)
        compose.onNodeWithTag("diagram-canvas").performTouchInput {swipe(start,start+Offset(45f,30f))}
        click("Guardar");await {documents().single().nodes.first()!=n}
        val moved=documents().single();assertEquals(saved.nodes.drop(1),moved.nodes.drop(1));assertEquals(saved.camera,moved.camera)
        compose.onNodeWithText("Límite del lienzo").assertDoesNotExist()
    }
    @Test @Config(qualifiers="w320dp-h640dp") fun fixedToolsAndBottomDeleteRemainVisibleAndIndependent() {
        start("Flujo básico");save()
        listOf("Deshacer","Eliminar","Imán","Ajustar a cuadrícula","Conectar","Elementos","Opciones","Vista previa en vivo").forEach {control(it).performScrollTo().assertIsDisplayed()}
        control("Mover").performScrollTo().assertIsDisplayed().assertIsSelected();control("Redimensionar").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Eliminar").assertCountEquals(1)
        control("Eliminar").assert(hasAnyAncestor(hasTestTag("diagram-bottom-controls")))
        control("Eliminar").assertIsNotEnabled();click("Imán");control("Imán").assertIsOn();control("Ajustar a cuadrícula").assertIsOff()
        click("Ajustar a cuadrícula");control("Ajustar a cuadrícula").assertIsOn();click("Imán");control("Imán").assertIsOff()
        val before=documents().single();val n=before.nodes.first();val v=view()
        compose.onNodeWithTag("diagram-canvas").performTouchInput {click(Offset(v.left+(n.x+n.w/2)*v.width,v.top+(n.y+n.h/2)*v.height))}
        control("Eliminar").assertIsEnabled();click("Eliminar");save();await {documents().single().nodes.size==before.nodes.size-1}
        click("Deshacer");save();await {documents().single().nodes==before.nodes}
        assertEquals(before.edges,documents().single().edges)

    }
    @Test fun fourFacesConnectAndEndpointCanBeRetargeted() {
        start("Flujo básico");val saved=save();click("Conectar");control("Conectar").assertIsSelected()
        val a=saved.nodes.first();val b=saved.nodes.last()
        fun point(n:DiagramNode,side:DiagramAnchor):Offset {val v=view();val p=diagramAnchorPoint(n,side);return Offset(v.left+p.x*v.width,v.top+p.y*v.height)}
        for(side in DiagramAnchor.entries) {
            val v=view();compose.onNodeWithTag("diagram-canvas").performTouchInput {click(Offset(v.left+(a.x+a.w/2)*v.width,v.top+(a.y+a.h/2)*v.height))}
            val from=point(a,side);val to=point(b,DiagramAnchor.LEFT)
            compose.onNodeWithTag("diagram-canvas").performTouchInput {swipe(from,to)}
            save();await {documents().single().edges.size==saved.edges.size+side.ordinal+1}
            assertEquals(side,documents().single().edges.last().fromAnchor)
        }
        val from=point(b,DiagramAnchor.LEFT);val to=point(saved.nodes[1],DiagramAnchor.TOP)
        compose.onNodeWithTag("diagram-canvas").performTouchInput {swipe(from,to)}
        save();await {documents().single().edges.last().to==saved.nodes[1].id}
        assertEquals(DiagramAnchor.TOP,documents().single().edges.last().toAnchor)
        click("Elementos");val v=view()
        compose.onNodeWithTag("diagram-canvas").performTouchInput {click(Offset(v.left+(a.x+a.w/2)*v.width,v.top+(a.y+a.h/2)*v.height))}
        control("Mover").assertIsSelected();click("Redimensionar")
        val r=point(a,DiagramAnchor.RIGHT);compose.onNodeWithTag("diagram-canvas").performTouchInput {swipe(r,r+Offset(20f,0f))}
        save();await {documents().single().nodes.first().w>a.w}
    }
    @Test fun moveSelectedNodeFromResizeHandleDoesNotChangeSize() {
        start("Lienzo vacío");click("Elementos");click("Rectángulo");save()
        for(side in DiagramAnchor.entries) {
            val before=documents().single();val n=before.nodes.first();val v=view()
            compose.onNodeWithTag("diagram-canvas").performTouchInput {click(Offset(v.left+(n.x+n.w/2)*v.width,v.top+(n.y+n.h/2)*v.height))}
            val current=view();val p=diagramAnchorPoint(n,side)
            // Stay just inside the painted boundary and exceed Android's touch slop.
            val center=DPoint(n.x+n.w/2,n.y+n.h/2)
            val from=Offset(current.left+p.x*current.width+(center.x-p.x).compareTo(0f)*2f,current.top+p.y*current.height+(center.y-p.y).compareTo(0f)*2f)
            compose.onNodeWithTag("diagram-canvas").performTouchInput {swipe(from,from+Offset(45f,30f))}
            save();try {await {documents().single().nodes.first()!=n}} catch(t:Throwable) {throw AssertionError("Move from $side at $from, viewport=$current, node=$n",t)}
            val moved=documents().single().nodes.first()
            assertEquals(n.w,moved.w,0f);assertEquals(n.h,moved.h,0f)
            assertEquals(before.nodes.drop(1),documents().single().nodes.drop(1))
        }
        click("Redimensionar");control("Redimensionar").assertIsSelected()
        click("Mover");control("Mover").assertIsSelected()
    }
    @Test fun tableFieldsAreEditableAndPersistWithoutChangingModes() {
        start("Lienzo vacío");click("Elementos");click("Tabla ERD");save();click("Editar tabla")
        fun edit(label:String,value:String) {compose.onNode(hasText(label) and hasSetTextAction()).performScrollTo().performTextReplacement(value)}
        edit("Encabezado de tabla","Personas");edit("Tipo opcional 1","INTEGER")
        click("FK campo 2");click("Añadir campo");edit("Nombre de campo 3","temporal");click("Eliminar campo 3")
        save();await {documents().single().nodes.single().text=="Personas"}
        val n=documents().single().nodes.single();assertEquals(2,n.fields.size)
        assertTrue(n.fields[0].primaryKey);assertEquals("INTEGER",n.fields[0].type);assertTrue(n.fields[1].foreignKey)
        control("Mover").assertIsSelected();click("Redimensionar");control("Eliminar").assertIsEnabled()
        save();assertEquals(n,documents().single().nodes.single())
    }
    @Test fun firstSaveNamingRenameCopyAndExportHaveDistinctPurposes() {
        start("Lienzo vacío");click("Guardar")
        compose.onNodeWithText("Nombre del diagrama").assertExists();assertTrue(documents().isEmpty())
        compose.onNode(hasSetTextAction()).performTextReplacement("Mi diagrama");click("Cancelar");assertTrue(documents().isEmpty())
        click("Guardar");compose.onNode(hasSetTextAction()).performTextReplacement("Mi diagrama");click("Guardar diagrama")
        await {documents().singleOrNull()?.name=="Mi diagrama"};val original=documents().single()
        click("Opciones");control("Historial").assertDoesNotExist();control("Rehacer").assertExists()
        click("Documento");click("Nombre del documento");compose.onNode(hasSetTextAction()).performTextReplacement("Renombrado")
        click("Guardar");await {documents().single().name=="Renombrado"};assertEquals(original.id,documents().single().id)
        click("Volver al nivel anterior");click("Guardar copia");await {documents().size==2}
        assertEquals(setOf("Renombrado","Renombrado · copia"),documents().map {it.name}.toSet())
        assertEquals(2,documents().map {it.id}.distinct().size)
        click("Opciones");click("Documento");click("Exportar documento");control("PNG").assertExists()
    }
    @Test fun firstSaveThroughCopyAlsoRequestsName() {
        start("Lienzo vacío");click("Opciones");click("Documento");click("Guardar copia")
        compose.onNodeWithText("Nombre del diagrama").assertExists();assertTrue(documents().isEmpty())
        compose.onNode(hasSetTextAction()).performTextReplacement("Primera copia");click("Guardar diagrama")
        await {documents().singleOrNull()?.name=="Primera copia"}
    }
    @Test @Config(qualifiers="w320dp-h640dp") fun globalLockStaysAtRightInLibraryAndEditor() {
        var locked=0
        compose.setContent {androidx.compose.runtime.CompositionLocalProvider(
            com.r0ybt.arachn0de.ui.LocalGlobalNavigation provides com.r0ybt.arachn0de.ui.GlobalNavigation({},true),
            com.r0ybt.arachn0de.ui.LocalProjectLock provides com.r0ybt.arachn0de.ui.ProjectLockAction(true,{locked++})
        ) {Arachn0deTheme {DiagramScreen {}}}}
        await {compose.onAllNodesWithTag("diagram-library").fetchSemanticsNodes().isNotEmpty()}
        fun check(tag:String) {
            compose.onAllNodesWithContentDescription("Bloquear").assertCountEquals(1)
            val lock=compose.onNodeWithContentDescription("Bloquear").fetchSemanticsNode().boundsInRoot
            val page=compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(lock.right>=page.right-20*compose.activity.resources.displayMetrics.density)
        }
        check("diagram-library");click("Lienzo vacío")
        await {compose.onAllNodesWithTag("diagram-editor").fetchSemanticsNodes().isNotEmpty()}
        click("Opciones");check("diagram-editor");click("Bloquear");assertEquals(1,locked)
    }
    @Test fun contextualPropertiesReplaceSizeScreenAndPreserveModes() {
        start("Lienzo vacío");click("Elementos");click("Rectángulo");val before=save().nodes.single()
        control("Tamaño y posición").assertDoesNotExist()
        click("Propiedades")
        control("Aumentar X").assertDoesNotExist()
        click("Mantener proporciones");click("Desde el centro");click("Escalar contenido")
        click("Ajustes precisos ▾");click("Aumentar X");click("Aumentar Ancho")
        save();await {documents().single().nodes.single().w>before.w}
        val after=documents().single().nodes.single()
        assertTrue(after.lockAspect);assertTrue(after.centeredResize)
        assertEquals(DiagramResizeContent.SCALE,after.resizeContent);assertEquals(before.x+.01f,after.x,.0001f)
        click("Ajustes precisos ▴");control("Aumentar X").assertDoesNotExist()
        click("Redimensionar");control("Redimensionar").assertIsSelected()
        control("Eliminar").assertIsEnabled();click("Mover");control("Mover").assertIsSelected()
        save();await {documents().single().nodes.single()==after}
    }
    @Test fun imagePropertiesOnlyShowTypographyForLabels() {
        var n by androidx.compose.runtime.mutableStateOf(DiagramNode(shape=DiagramShape.IMAGE,text=""))
        compose.setContent {Arachn0deTheme {androidx.compose.foundation.layout.Column {DiagramNodeProperties(n,true) {n=it}}}}
        control("Aumentar Tipografía").assertDoesNotExist();control("Escalar contenido").assertDoesNotExist()
        compose.runOnIdle {n=n.copy(text="Etiqueta")}
        control("Aumentar Tipografía").assertExists();click("Aumentar Tipografía");assertEquals(30f,n.font,0f)
    }
    @Test fun standalonePaletteEditsAndConfirmsHex() {
        var result:Int?=null
        compose.setContent {Arachn0deTheme {DiagramPalette(DIAGRAM_COAL,emptyList(),{},{}) {result=it}}}
        val hsl=FloatArray(3);androidx.core.graphics.ColorUtils.colorToHSL(DIAGRAM_COAL,hsl);hsl[0]=120f
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(120f)}
        compose.onNode(hasSetTextAction()).assert(hasText("%08X".format(androidx.core.graphics.ColorUtils.HSLToColor(hsl))))
        assertNull(result)
        compose.onNode(hasSetTextAction()).performTextReplacement("FF123456");click("Aplicar color")
        assertEquals(0xff123456.toInt(),result)
    }
    @Test fun gradientDialogConfirmsThreeColorsAndNestedPalette() {
        var result:DiagramGradient?=null
        compose.setContent {Arachn0deTheme {DiagramGradientDialog(null,emptyList(),{},{}) {result=it}}}
        click("Añadir color");click("Color 2")
        compose.onNode(hasSetTextAction()).performTextReplacement("FFABCDEF");click("Aplicar color")
        assertNull(result);click("Aplicar degradado");assertEquals(3,result!!.colors.size);assertEquals(0xffabcdef.toInt(),result!!.colors[1])
    }
    @Test fun actualPaperLimitsNotifyWithoutPanningOrMovingOtherNodes() {
        start("Lienzo vacío");click("Elementos");click("Rectángulo");save()
        listOf("left","right","top","bottom").forEach {side->
            val d=documents().single();val n=d.nodes.single();val v=view()
            val from=Offset(v.left+(n.x+n.w/2)*v.width,v.top+(n.y+n.h/2)*v.height)
            val to=when(side) {"left"->Offset(v.left-8,from.y);"right"->Offset(v.left+v.width+8,from.y);"top"->Offset(from.x,v.top-8);else->Offset(from.x,v.top+v.height+8)}
            compose.onNodeWithTag("diagram-canvas").performTouchInput {swipe(from,to)}
            compose.onNodeWithText("Límite del lienzo").assertExists();click("Guardar")
            await {documents().single().nodes.single()!=n}
            val changed=documents().single();assertEquals(d.camera,changed.camera)
            val moved=changed.nodes.single();when(side) {"left"->assertEquals(0f,moved.x,.0001f);"right"->assertEquals(1f,moved.x+moved.w,.0001f);"top"->assertEquals(0f,moved.y,.0001f);else->assertEquals(1f,moved.y+moved.h,.0001f)}
        }
    }
    @Test fun formatChangeRequiresConfirmationAndPaletteCancelDoesNotChangeSavedColor() {
        start("Lienzo vacío");save();click("Opciones");click("Formato del documento");click("9:16")
        assertEquals(DiagramPageFormat.FREE,documents().single().page.format);click("Conservar posiciones")
        click("Guardar");await {documents().single().page.format==DiagramPageFormat.STORY}
        click("Volver al nivel anterior");click("Fondo")
        click("Color personalizado")
        compose.onNode(hasSetTextAction()).performTextReplacement("FF123456");click("Cancelar")
        click("Guardar");await {documents().single().background==DIAGRAM_COAL}
        click("Color personalizado");compose.onNode(hasSetTextAction()).performTextReplacement("FF123456");click("Guardar color favorito")
        await {DiagramCodec.library(app.privateContext.getSharedPreferences(DiagramCodec.NAME,0).all).colors.contains(0xff123456.toInt())}
        click("Aplicar color");click("Guardar");await {documents().single().background==0xff123456.toInt()}
    }
    @Test fun visualFiguresContextEditorsAndModularBackKeepWorkAndNoPermanentPropertiesTab() {
        start("Lienzo vacío")
        compose.onNodeWithTag("diagram-panel-body").assertDoesNotExist()
        compose.onNodeWithText("Propiedades").assertDoesNotExist()
        click("Elementos");compose.onNodeWithText("Rectángulo").assertDoesNotExist()
        control("Rectángulo").assertExists();control("Importar").assertExists()
        click("Rectángulo");control("Editar texto").assertExists()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        click("Editar texto");compose.onNode(hasSetTextAction()).performTextReplacement("Mi idea")
        click("Minimizar");click("Editar texto")
        compose.onNode(hasSetTextAction()).assert(hasText("Mi idea"))
        click("Confirmar");click("Opciones");click("Deshacer");click("Rehacer")
        save();await {documents().single().nodes.single().text=="Mi idea"}
        click("Volver");compose.onNodeWithTag("diagram-library").assertExists()
        click("Diagrama");click("Elementos");click("Texto")
        click("Volver");compose.onNodeWithText("Hay cambios sin guardar.").assertExists();click("Seguir editando")
    }
    @Test fun numberedConnectionsChooseGraphicRoutesStrokesAndArrowsAndKeepNumbersAfterDeletion() {
        start();save();click("Opciones");click("Conexiones del documento");click("Conexión 1")
        control("Trayectoria").assertExists();control("Editar texto").assertDoesNotExist()
        click("Trayectoria");click("Curva");click("Confirmar")
        click("Trazo");click("Discontinua");click("Confirmar")
        click("Flechas");click("Ambas flechas");click("Confirmar")
        save();await {documents().single().edges.first().stroke==DiagramStroke.DASHED}
        val edge=documents().single().edges.first();assertEquals(DiagramRoute.CURVE,edge.route);assertTrue(edge.startArrow&&edge.endArrow)
        click("Eliminar")
        control("Conexión 1").assertDoesNotExist();control("Conexión 2").assertExists()
        click("Opciones");click("Deshacer");click("Conexiones del documento")
        control("Conexión 1").assertExists();control("Conexión 2").assertExists()
        click("Minimizar");control("Conexión 1").assertDoesNotExist()
    }
    @Test fun numberedCanvasBadgeAndPortDragUseTheSamePersistentConnectionIds() {
        start("Red de dispositivos");val saved=save();click("Conectar")
        val b=compose.onNodeWithTag("diagram-canvas").fetchSemanticsNode().boundsInRoot
        val order=saved.edges.map {it.id};val v=view();val density=compose.activity.resources.displayMetrics.density
        val badge=diagramBadges(saved,order,v,b.width,b.height,12*density).first()
        compose.onNodeWithTag("diagram-canvas").performTouchInput {click(badge.point)}
        control("Animar ▶").assertExists()
        var currentView=view()
        val source=saved.nodes.first();val target=saved.nodes.last()
        compose.onNodeWithTag("diagram-canvas").performTouchInput {click(Offset(currentView.left+(source.x+source.w/2)*currentView.width,currentView.top+(source.y+source.h/2)*currentView.height))}
        currentView=view()
        compose.onNodeWithTag("diagram-canvas").performTouchInput {swipe(Offset(currentView.left+(source.x+source.w)*currentView.width,currentView.top+(source.y+source.h/2)*currentView.height),Offset(currentView.left+(target.x+target.w/2)*currentView.width,currentView.top+(target.y+target.h/2)*currentView.height))}
        save();await {documents().single().edges.size==3};assertEquals(3,diagramBadges(documents().single(),documents().single().edges.map {it.id},view(),b.width,b.height,12*density).size)
        assertEquals(saved.edges.map {it.id},documents().single().edges.take(2).map {it.id})
    }
    @Test fun multiSelectionEnablesArrangementAndGridSnapAndGuidesAreIndependent() {
        start();save();click("Opciones");click("Alineación")
        control("Alinear izquierda").assertIsNotEnabled();click("Selección múltiple")
        val v=view();val nodes=documents().single().nodes
        nodes.take(2).forEach {n->compose.onNodeWithTag("diagram-canvas").performTouchInput {click(Offset(v.left+(n.x+n.w/2)*v.width,v.top+(n.y+n.h/2)*v.height))}}
        control("Alinear arriba").assertIsEnabled();click("Alinear arriba");save()
        await {documents().single().nodes.take(2).map {it.y}.distinct().size==1}
        click("Volver al nivel anterior");click("Espaciado de cuadrícula")
        click("Mostrar cuadrícula");control("Mostrar cuadrícula").assertIsOn();control("Ajustar a cuadrícula").assertIsOff()
        click("Ajustar a cuadrícula");control("Ajustar a cuadrícula").assertIsOn()
        control("Imán").assertIsOff();click("Imán");control("Imán").assertIsOn()
        control("Mostrar cuadrícula").assertIsOn();control("Ajustar a cuadrícula").assertIsOn()
        click("Volver al nivel anterior");click("Vista");control("Guías").assertDoesNotExist();click("Ajustar a pantalla")
    }
    @Test fun backgroundImageUsesExistingLibraryAndIsNotANodeAndCanBeReplacedOrRemoved() {
        val bitmap=android.graphics.Bitmap.createBitmap(40,20,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.RED)}
        val bytes=java.io.ByteArrayOutputStream().also {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val asset=runBlocking {DiagramRepository(app.privateContext.getSharedPreferences(DiagramCodec.NAME,0)).importImage(com.r0ybt.arachn0de.visual.canvasImage(bytes))}
        start("Lienzo vacío");save();click("Opciones");click("Fondo");click("Imagen de fondo");click("Usar icono 1")
        save();await {documents().single().backgroundAsset==asset};assertTrue(documents().single().nodes.isEmpty())
        click("Rellenar");save();await {documents().single().backgroundFit==DiagramBackgroundFit.FILL}
        control("Quitar imagen de fondo").assertExists();click("Quitar imagen de fondo");click("Fondo negro")
        save();await {documents().single().backgroundAsset==null&&documents().single().background==android.graphics.Color.BLACK}
        assertTrue(DiagramCodec.library(app.privateContext.getSharedPreferences(DiagramCodec.NAME,0).all).assets.containsKey(asset))
    }
    @Test fun livePreviewKeepsEditingVisibleAndPresentationHidesItWithoutMutatingTheDocument() {
        start("Red de dispositivos");val saved=save()
        compose.mainClock.autoAdvance=false;playbackAction("Vista previa en vivo")
        control("Elementos").assertExists();control("Guardar").assertExists()
        playbackAction("Pausar vista previa");control("Reanudar vista previa").assertExists()
        playbackAction("Reanudar vista previa");playbackAction("Detener vista previa")
        compose.mainClock.autoAdvance=true;save();assertEquals(saved,documents().single())
        click("Opciones");click("Vista");control("Presentación").assertExists();compose.mainClock.autoAdvance=false;playbackAction("Presentación")
        control("Elementos").assertDoesNotExist();control("Guardar").assertIsNotEnabled()
        playbackAction("Pausar");playbackAction("Reanudar");playbackAction("Edición");compose.mainClock.autoAdvance=true
        assertEquals(saved,documents().single())
    }
    @Test fun animationSettingsCanChangeWhileLivePreviewContinuesInTheEditor() {
        start("Red de dispositivos");save();compose.mainClock.autoAdvance=false
        playbackAction("Vista previa en vivo");playbackAction("Opciones");playbackAction("Conexiones del documento");playbackAction("Conexión 1");playbackAction("Animar ▶")
        compose.onNodeWithContentDescription("Flujo animado").assertIsOn()
        playbackAction("Velocidad y cantidad");playbackAction("2×")
        control("Pausar vista previa").assertExists();control("Elementos").assertExists()
        playbackAction("Detener vista previa");compose.mainClock.autoAdvance=true;save()
        await {documents().single().edges.first().flow.speed==2f}
    }
    @Test fun exportShowsOnlyTheSelectedFormatOptionsAndNoFlowOffersDirectConfiguration() {
        start();click("Vista previa en vivo");click("Configurar conexión")
        click("Conexión 1");click("Animar ▶")
        compose.onNodeWithContentDescription("Flujo animado").assertIsOff()
        click("Opciones");click("Documento");click("Exportar documento")
        control("PNG").assertExists();control("GIF").assertExists();control("MP4").assertExists()
        click("GIF");compose.onNodeWithContentDescription("Repetir GIF").assertExists()
        click("Volver al nivel anterior");click("PNG")
        compose.onNodeWithContentDescription("Repetir GIF").assertDoesNotExist()
        control("Elegir carpeta y exportar").assertExists()
    }
    @Test @Config(qualifiers="w320dp-h640dp") fun smallGreenBashScreenWithLargeFontsKeepsGraphicToolsAndEditorsAccessible() {
        val preferences=com.r0ybt.arachn0de.ui.theme.AppearancePreferences(app.privateContext.getSharedPreferences(com.r0ybt.arachn0de.ui.theme.AppearancePreferences.FILE,0))
        preferences.setTheme(com.r0ybt.arachn0de.ui.theme.AppearanceTheme.GreenBash)
        compose.setContent {Arachn0deTheme(preferences) {
            val density=androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,2f)) {DiagramScreen {}}
        }}
        click("Lienzo vacío");click("Elementos")
        control("Elipse").performScrollTo().assertWidthIsEqualTo(androidx.compose.ui.unit.Dp(88f)).assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(56f))
        click("Elipse");click("Editar texto");compose.onNode(hasSetTextAction()).performTextReplacement("Idea")
        click("Minimizar");click("Editar texto");compose.onNode(hasSetTextAction()).assert(hasText("Idea"));click("Confirmar")
        save();await {documents().single().nodes.single().text=="Idea"}
    }
    @Test fun recreationPreservesSelectedObjectSpecificEditorAndMinimizedDraft() {
        val restoration=StateRestorationTester(compose)
        restoration.setContent {Arachn0deTheme {DiagramScreen {}}}
        click("Lienzo vacío");click("Elementos");click("Rectángulo");click("Editar texto")
        compose.onNode(hasSetTextAction()).performTextReplacement("Idea recuperada")
        click("Minimizar");restoration.emulateSavedInstanceStateRestore()
        control("Editar texto").assertExists();compose.onNodeWithTag("diagram-panel-body").assertDoesNotExist()
        click("Editar texto");compose.onNode(hasSetTextAction()).assert(hasText("Idea recuperada"));click("Confirmar")
        save();await {documents().single().nodes.single().text=="Idea recuperada"}
    }

    @Test fun libraryGroupsAndDeleteConfirmationKeepSavedDocumentUntilConfirmed() {
        start();val saved=save();click("Volver")
        compose.onNodeWithText("Crear desde plantilla").assertExists()
        compose.onNodeWithText("Diagramas guardados (1)").assertExists()
        compose.onNodeWithText("Biblioteca personal de iconos (0/48)").assertExists()
        click("Acciones de ${saved.name}");click("Eliminar")
        compose.onNodeWithText("Eliminar diagrama").assertExists()
        assertEquals(1,documents().size);click("Cancelar");assertEquals(1,documents().size)
        click("Acciones de ${saved.name}");click("Eliminar");click("Eliminar")
        await {documents().isEmpty()}
    }
    @Test fun groupMoveIsExplicitKeepsSelectionAndExportsAreDirectlyAccessible() {
        start();val before=save()
        click("Exportar");compose.onNodeWithText("PNG").assertExists();compose.onNodeWithText("GIF").assertExists()
        click("Volver al nivel anterior");click("Volver al nivel anterior");click("Mover conjunto")
        compose.onNodeWithText("Mover conjunto · arrastra el contenido").assertExists()
        compose.onNodeWithTag("diagram-canvas").performTouchInput {swipe(center,center+androidx.compose.ui.geometry.Offset(12f,12f),500)}
        click("Guardar");await {documents().single()!=before}
        val moved=documents().single();assertEquals(before.camera,moved.camera)
        before.nodes.zip(moved.nodes).forEach {(a,b)->assertEquals(a.w,b.w,0f);assertEquals(a.h,b.h,0f)}
        click("Deshacer");click("Guardar");await {documents().single()==before}
        click("Terminar")
    }

    @Test fun libraryRendersWithEveryOfficialTheme() {
        val preferences=com.r0ybt.arachn0de.ui.theme.AppearancePreferences(app.privateContext.getSharedPreferences("diagram-theme-test",0))
        compose.setContent {Arachn0deTheme(preferences) {DiagramScreen {}}}
        com.r0ybt.arachn0de.ui.theme.AppearanceTheme.entries.forEach {theme->
            compose.runOnIdle {preferences.setTheme(theme)}
            compose.onNodeWithText("Crear desde plantilla").assertExists()
            compose.onNodeWithText("Mis diseños").assertExists()
            compose.onNodeWithTag("diagram-library").assertIsDisplayed()
        }
        compose.onNodeWithText("Crear desde plantilla").assertExists()
    }
    @Test fun saveHasSpacingAndLastAnimationOnlyUpdatesTheChosenConnection() {
        start("Red de dispositivos")
        val saveButton=control("Guardar")
        val saveBounds=saveButton.fetchSemanticsNode().boundsInRoot
        val left=control("Exportar").fetchSemanticsNode().boundsInRoot
        val right=control("Vista previa en vivo").fetchSemanticsNode().boundsInRoot
        val density=compose.density.density
        assertTrue(saveBounds.left-left.right>=4*density-1)
        assertTrue(right.left-saveBounds.right>=4*density-1)
        saveButton.assertWidthIsEqualTo(androidx.compose.ui.unit.Dp(88f)).assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(56f))
        val before=save()
        click("Opciones");click("Conexiones del documento");click("Conexión 1");click("Animar ▶")
        click("Velocidad y cantidad");click("2×");click("Volver al nivel anterior")
        click("Estilo y brillo");click("Pulso")
        click("Opciones");click("Conexiones del documento");click("Conexión 2");click("Animar ▶")
        click("Usar última configuración");save()
        val copied=documents().single()
        assertEquals(2f,copied.edges[1].flow.speed,0f)
        assertEquals(DiagramParticleStyle.PULSE,copied.edges[1].flow.particleStyle)
        assertEquals(copied.edges[0].flow,copied.edges[1].flow)
        assertEquals(before.edges.drop(2),copied.edges.drop(2))
        click("Velocidad y cantidad");click("½×");save()
        val edited=documents().single()
        assertEquals(copied.edges[0],edited.edges[0])
        assertEquals(copied.edges[1].flow.copy(speed=.5f),edited.edges[1].flow)
    }

    @Test @Config(qualifiers="w320dp-h640dp") fun visualBarsKeepTouchSizesSelectionAndDisabledStatesOnNarrowScreen() {
        start()
        control("Mover").assertIsSelected().assertWidthIsEqualTo(androidx.compose.ui.unit.Dp(88f)).assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(56f))
        control("Deshacer").assertIsNotEnabled().assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"No disponible"))
        control("Guardar").performScrollTo().assertIsDisplayed().assertWidthIsEqualTo(androidx.compose.ui.unit.Dp(88f))
        click("Conectar");control("Conectar").assertIsSelected();control("Mover").assertIsNotSelected()
        click("Mover");control("Mover").assertIsSelected()
        click("Elementos")
        compose.onNodeWithTag("diagram-panel-body").assertIsDisplayed()
        control("Rectángulo").performScrollTo().assertWidthIsEqualTo(androidx.compose.ui.unit.Dp(88f)).assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(56f))
        click("Minimizar")
        control("Eliminar").performScrollTo().assertIsNotEnabled()
        control("Opciones").performScrollTo().assertIsDisplayed()
        val root=compose.onNodeWithTag("diagram-editor").fetchSemanticsNode().boundsInRoot
        val bar=compose.onNodeWithTag("diagram-bottom-controls").fetchSemanticsNode().boundsInRoot
        assertTrue(bar.left>=root.left && bar.right<=root.right && bar.bottom<=root.bottom)
    }

}
