package com.r0ybt.arachn0de.wireframe

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[33],qualifiers="w360dp-h891dp")
class WireframeUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun text(value:String) {compose.waitUntil(15000) {pump();compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()}}
    private fun actionLabel(label:String)=SemanticsMatcher(label) {it.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.label==label}
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        pump()
        // The application preference observer belongs to the process scope, not the vault scope.
        // Join both before closing this fixture's database, including very short canvas-only tests.
        val process=Arachn0deApplication::class.java.getDeclaredField("processScope").apply {isAccessible=true}.get(app) as CoroutineScope
        runBlocking {
            process.coroutineContext[Job]?.cancelAndJoin()
            app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin()
        }
        app.database.close()
    }
    @Test fun sharedGalleryHandlesEmptyFiveItemsListAndFailedThumbnail() {
        var count by androidx.compose.runtime.mutableIntStateOf(0)
        var grid by androidx.compose.runtime.mutableStateOf(true)
        var opened=-1
        compose.setContent {Arachn0deTheme {
            com.r0ybt.arachn0de.visual.ToolDocumentGallery((0 until count).map {i->
                com.r0ybt.arachn0de.visual.ToolLibraryItem("fixture:$i","Diseño $i",open={opened=i},preview={m->
                    com.r0ybt.arachn0de.visual.ToolThumbnail("failed-fixture:$i","Diseño $i",m) {throw IllegalArgumentException("Vista no disponible")}
                })
            },grid,{grid=it})
        }}
        compose.onNodeWithText("Tus diseños aparecerán aquí al guardarlos.").assertExists()
        compose.runOnIdle {count=5}
        compose.onNodeWithTag("tool-document:fixture:0").assertIsDisplayed()
        val a=compose.onNodeWithTag("tool-document:fixture:0").fetchSemanticsNode().boundsInRoot
        val b=compose.onNodeWithTag("tool-document:fixture:1").fetchSemanticsNode().boundsInRoot
        assertEquals(a.top,b.top,1f);assertTrue(b.left>a.left)
        compose.onNodeWithTag("tool-document:fixture:0").performClick()
        compose.runOnIdle {assertEquals(0,opened)}
        compose.onNodeWithTag("tool-gallery-layout").performClick()
        val list0=compose.onNodeWithTag("tool-document:fixture:0").fetchSemanticsNode().boundsInRoot
        val list1=compose.onNodeWithTag("tool-document:fixture:1").fetchSemanticsNode().boundsInRoot
        assertTrue(list1.top>list0.top);assertEquals(list0.left,list1.left,1f)
    }
    @Test fun libraryGalleryActionsAndLayoutPreferenceSurviveReopening() {
        createDrawing()
        compose.onNodeWithText("Archivo").performScrollTo().performClick()
        compose.onNodeWithText("Guardado",substring=false).assertExists()
        compose.onNodeWithText("Volver").performClick();text("Mis diseños")
        compose.onNodeWithTag("tool-gallery-layout").performClick()
        assertFalse(app.privateContext.getSharedPreferences(com.r0ybt.arachn0de.domain.model.ToolGalleryPreference.NAME,0).getBoolean("wireframe",true))
        val original=savedDrawing()
        compose.onNodeWithTag("tool-document-menu:${original.id}").performClick()
        compose.onNodeWithText("Renombrar",substring=false).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Diseño renombrado")
        compose.onNodeWithText("Aceptar").performClick();text("Diseño renombrado")
        compose.onNodeWithTag("tool-document-menu:${original.id}").performClick()
        compose.onNodeWithText("Exportar PNG",substring=false).performClick()
        text("Exportar PNG");compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("tool-document-menu:${original.id}").performClick()
        compose.onNodeWithText("Duplicar").performClick();text("Diseño renombrado · copia")
        compose.onNodeWithTag("tool-document-menu:${original.id}").performClick()
        compose.onNodeWithText("Eliminar",substring=false).performClick();text("Eliminar dibujo")
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("tool-document:${original.id}").performClick();text("Herramientas")
        compose.onNodeWithTag("wireframe-canvas").assertIsDisplayed()
        compose.onNodeWithTag("wireframe-panel-fold").performClick()
        compose.onNodeWithTag("wireframe-panel-fold").performClick()
        compose.onNodeWithText("Volver").performClick();text("Mis diseños")
        compose.onNodeWithContentDescription("Ver cuadrícula").assertExists()
        compose.onNodeWithTag("tool-document-menu:${original.id}").performClick()
        compose.onNodeWithText("Eliminar",substring=false).performClick();text("Eliminar dibujo")
        compose.onNodeWithText("Eliminar",substring=false).performClick()
        compose.waitUntil(15000) {pump();app.privateContext.getSharedPreferences(WireCodec.NAME,0).all.size==1}
    }
    @Test fun canvasDragResizeAndCancelCommitOnce() {
        val original=WireDocument().insert(WireTemplates.items.first {it.id=="fab"}).let {it.copy(elements=it.elements.map {e->e.copy(x=.2f,y=.2f)})}
        var document by androidx.compose.runtime.mutableStateOf(original)
        var selected by androidx.compose.runtime.mutableStateOf<String?>(original.elements.single().id)
        var commits=0
        compose.setContent {
            val renderer=androidx.compose.runtime.remember {WireframeRenderer(compose.activity,emptyMap())}
            WireCanvas(document,renderer,selected,true,{selected=it},{document=it;commits++})
        }
        fun point(x:Float,y:Float):androidx.compose.ui.geometry.Offset {
            val bounds=compose.onNodeWithTag("wireframe-canvas").fetchSemanticsNode().size
            val scale=minOf(bounds.width/1080f,bounds.height/2340f)*.94f
            return androidx.compose.ui.geometry.Offset((bounds.width-1080*scale)/2+x*1080*scale,(bounds.height-2340*scale)/2+y*2340*scale)
        }
        val e=original.elements.single()
        val start=point(e.x+e.w/2,e.y+e.h/2)
        compose.onNodeWithTag("wireframe-canvas").performTouchInput {swipe(start,start+androidx.compose.ui.geometry.Offset(30f,30f),300)}
        compose.runOnIdle {assertEquals(1,commits);assertTrue(document.elements.single().x>e.x)}
        val moved=document.elements.single();val handle=point(moved.x+moved.w,moved.y+moved.h)
        compose.onNodeWithTag("wireframe-canvas").performTouchInput {swipe(handle,handle+androidx.compose.ui.geometry.Offset(25f,25f),300)}
        compose.runOnIdle {assertEquals(2,commits);assertTrue(document.elements.single().w>moved.w)}
        val beforeCancel=document;val r=document.elements.single();val center=point(r.x+r.w/2,r.y+r.h/2)
        compose.onNodeWithTag("wireframe-canvas").performTouchInput {down(center);moveBy(androidx.compose.ui.geometry.Offset(20f,20f));cancel()}
        compose.runOnIdle {assertEquals(2,commits);assertEquals(beforeCancel,document)}
    }
    @Test fun canvasGuidesSnapAndReleaseWithoutAffectingCancellation() {
        val original=WireDocument(elements=listOf(WireElement(x=.28f,y=.36f,w=.4f,h=.2f)))
        var document by androidx.compose.runtime.mutableStateOf(original)
        var commits=0
        compose.setContent {
            val renderer=androidx.compose.runtime.remember {WireframeRenderer(compose.activity,emptyMap())}
            WireCanvas(document,renderer,original.elements.single().id,true,{}, {document=it;commits++},true)
        }
        val start=canvasPoint(.48f,.46f);val target=canvasPoint(.499f,.499f)
        compose.onNodeWithTag("wireframe-canvas").performTouchInput {swipe(start,target,300)}
        compose.runOnIdle {assertEquals(1,commits);assertEquals(.3f,document.elements.single().x,.00001f);assertEquals(.4f,document.elements.single().y,.00001f)}
        val snapped=document;val center=canvasPoint(.5f,.5f);val free=canvasPoint(.6f,.6f)
        compose.onNodeWithTag("wireframe-canvas").performTouchInput {down(center);moveTo(free);cancel()}
        compose.runOnIdle {assertEquals(1,commits);assertEquals(snapped,document)}
        compose.onNodeWithTag("wireframe-canvas").performTouchInput {swipe(center,free,300)}
        compose.runOnIdle {assertEquals(2,commits);assertTrue(document.elements.single().x>.35f);assertTrue(document.elements.single().y>.45f)}
    }
    @Test fun frameCoordinatesIndependentMagnetAndResize() {
        val original=WireDocument(elements=listOf(WireElement(x=.28f,y=.36f,w=.4f,h=.2f)))
        var document by androidx.compose.runtime.mutableStateOf(original)
        var guides by androidx.compose.runtime.mutableStateOf(true)
        var magnetic by androidx.compose.runtime.mutableStateOf(false)
        var commits=0
        compose.setContent {
            val renderer=androidx.compose.runtime.remember {WireframeRenderer(compose.activity,emptyMap())}
            WireCanvas(document,renderer,original.elements.single().id,true,{}, {document=it;commits++},guides,magnetic,true,true,true)
        }
        fun dragNearCenter() {
            val start=canvasPoint(.48f,.46f,device=true,tools=true);val target=canvasPoint(.499f,.499f,device=true,tools=true)
            compose.onNodeWithTag("wireframe-canvas").performTouchInput {swipe(start,target,300)}
        }
        dragNearCenter()
        compose.runOnIdle {assertEquals(.299f,document.elements.single().x,.00001f);assertEquals(.399f,document.elements.single().y,.00001f)}
        compose.runOnIdle {document=original;guides=false;magnetic=true}
        dragNearCenter()
        compose.runOnIdle {assertEquals(.3f,document.elements.single().x,.00001f);assertEquals(.4f,document.elements.single().y,.00001f);assertEquals(2,commits)}
        val handle=canvasPoint(.7f,.6f,device=true,tools=true);val larger=canvasPoint(.74f,.63f,device=true,tools=true)
        compose.onNodeWithTag("wireframe-canvas").performTouchInput {swipe(handle,larger,300)}
        compose.runOnIdle {assertEquals(.44f,document.elements.single().w,.00001f);assertEquals(.23f,document.elements.single().h,.00001f);assertEquals(3,commits)}
        compose.runOnIdle {document=original.copy(format=WireFormat.DESKTOP);magnetic=false}
        val desktopStart=canvasPoint(.48f,.46f,device=true,tools=true,format=WireFormat.DESKTOP)
        val desktopEnd=canvasPoint(.6f,.6f,device=true,tools=true,format=WireFormat.DESKTOP)
        compose.onNodeWithTag("wireframe-canvas").performTouchInput {swipe(desktopStart,desktopEnd,300)}
        compose.runOnIdle {assertEquals(.4f,document.elements.single().x,.00001f);assertEquals(.5f,document.elements.single().y,.00001f);assertEquals(4,commits)}
    }
    @Test fun visualOptionsHideRestoreAndDoNotDirtyOrChangeDocument() {
        val restoration=StateRestorationTester(compose)
        createDrawing(restoration)
        val before=app.privateContext.getSharedPreferences(WireCodec.NAME,0).all.toMap()
        compose.onNodeWithTag("wire-visual:GUIDES").assertIsOn().performClick()
        compose.onNodeWithTag("wire-visual:MAGNET").assertIsOn().performClick()
        compose.onNodeWithTag("wire-visual:GUIDES").assertIsOff().performClick()
        compose.onNodeWithTag("wire-visual:MAGNET").assertIsOff()
        compose.onNodeWithTag("wire-visual:MARGINS").assertIsOff().performClick()
        compose.onNodeWithTag("wire-visual:DEVICE").assertIsOff().performClick()
        compose.onNodeWithTag("wireframe-visual-bar-toggle").performClick()
        compose.onNodeWithTag("wireframe-visual-toolbar").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("wireframe-visual-bar-toggle").performClick()
        compose.onNodeWithTag("wire-visual:MARGINS").assertIsOn()
        compose.onNodeWithTag("wire-visual:DEVICE").assertIsOn()
        compose.onNodeWithTag("wire-visual:GUIDES").assertIsOn()
        compose.onNodeWithTag("wire-visual:MAGNET").assertIsOff()
        compose.onNodeWithTag("wire-composition-menu",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("wire-composition:THIRDS").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("wire-composition-menu",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("wire-composition:THIRDS").assertIsSelected().performClick()
        compose.onNodeWithContentDescription("Deshacer").assertIsNotEnabled()
        compose.onNodeWithText("Archivo").performScrollTo().performClick()
        compose.onNodeWithText("Guardado").assertExists()
        assertEquals(before,app.privateContext.getSharedPreferences(WireCodec.NAME,0).all)
        // Export receives the unchanged document only, with no editor layers or toolbar.
        val d=WireCodec.decode(before.values.single() as String)
        WireframeRenderer(compose.activity,d.assets).use {renderer->
            val bytes=java.io.ByteArrayOutputStream()
            renderer.png(d,false,bytes)
            val bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes.toByteArray(),0,bytes.size())
            assertEquals(1080,bitmap.width);assertEquals(2340,bitmap.height)
            assertEquals(android.graphics.Color.WHITE,bitmap.getPixel(5,5))
            assertEquals(android.graphics.Color.WHITE,bitmap.getPixel(540,100))
            assertEquals(android.graphics.Color.WHITE,bitmap.getPixel(540,2300));bitmap.recycle()
        }
    }
    @Test fun holdHelpDoesNotToggleAndCompositionIsIndependent() {
        createDrawing()
        for(tool in WireVisualTool.entries) {
            val node=compose.onNodeWithTag("wire-visual:${tool.name}")
            val initial=node.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState]
            node.performTouchInput {val point=androidx.compose.ui.geometry.Offset(width*.3f,height*.3f);down(point);advanceEventTime(700);moveTo(point)}
            compose.mainClock.advanceTimeByFrame()
            text(tool.hint)
            compose.onNodeWithTag("wire-help:${tool.name}").assertExists()
            compose.onNodeWithText(tool.hint).assertExists()
            if(tool==WireVisualTool.GUIDES) {
                node.performTouchInput {advanceEventTime(2000);moveBy(androidx.compose.ui.geometry.Offset.Zero)}
                compose.onNodeWithTag("wire-help:${tool.name}").assertExists()
            }
            assertEquals(initial,node.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState])
            node.performTouchInput {up()}
            compose.onNodeWithTag("wire-help:${tool.name}").assertDoesNotExist()
            assertEquals(initial,node.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState])
        }
        compose.onNodeWithTag("wire-visual:GUIDES").assertIsOn()
        compose.onNodeWithTag("wire-composition-menu",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("wire-composition:CENTER").performClick()
        compose.onNodeWithTag("wire-visual:GUIDES").assertIsOn()
        compose.onNodeWithTag("wire-visual:GUIDES").performTouchInput {click(androidx.compose.ui.geometry.Offset(width*.3f,height*.3f))}
        compose.onNodeWithTag("wire-visual:GUIDES").assertIsOff()
        compose.onNodeWithTag("wire-composition-menu",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("wire-composition:CENTER").assertIsSelected()
        compose.onNodeWithTag("wire-composition:GRID").performClick()
        compose.onNodeWithTag("wire-visual:GUIDES").assertIsOff()
    }
    @Test fun graphicalColorDraftAlphaUndoAndEmptyProperties() {
        createDrawing()
        val rootHeight=compose.onNodeWithTag("wireframe-panel").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithText("Propiedades").performScrollTo().performClick()
        compose.onNodeWithText("Toca un elemento o selecciónalo en la lista.").assertDoesNotExist()
        assertTrue(compose.onNodeWithTag("wireframe-panel").fetchSemanticsNode().boundsInRoot.height<rootHeight)
        panelBack()
        compose.onNodeWithText("Añadir").performClick();compose.onNodeWithText("Botones").performScrollTo().performClick()
        compose.onNodeWithTag("wire-resource:button").performClick();panelBack();panelBack()
        compose.onNodeWithText("Elementos").performScrollTo().performClick();compose.onNodeWithText("Botón rectangular · Continuar").performClick()
        compose.onNodeWithTag("wire-color:Relleno:Rojo").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Deshacer").performClick()
        compose.onNodeWithTag("wire-color:Relleno:Rojo").assertIsNotSelected()
        compose.onNodeWithContentDescription("Rehacer").performClick()
        compose.onNodeWithTag("wire-color:Relleno:Rojo").assertIsSelected()
        compose.onNodeWithTag("wire-color-custom:Relleno").performScrollTo().performClick()
        compose.onNodeWithText("Relleno · AARRGGBB").performScrollTo().performTextReplacement("GG")
        compose.onNodeWithText("Aplicar").assertIsNotEnabled()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("wire-color:Relleno:Rojo").assertIsSelected()
        compose.onNodeWithTag("wire-color-custom:Relleno").performClick()
        compose.onNodeWithText("Relleno · AARRGGBB").performScrollTo().performTextReplacement("80336699")
        compose.onNodeWithTag("wire-color-channel:Rojo").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(120f)}
        compose.onNodeWithTag("wire-color-channel:Opacidad").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(64f)}
        compose.onNodeWithText("Aplicar").performClick()
        compose.onNodeWithContentDescription("Color seleccionado de Relleno: #40786699").assertExists()
        compose.onNodeWithContentDescription("Deshacer").performClick()
        compose.onNodeWithTag("wire-color:Relleno:Rojo").assertIsSelected()
        compose.onNodeWithContentDescription("Rehacer").performClick()
        compose.onNodeWithContentDescription("Color seleccionado de Relleno: #40786699").assertExists()
        panelBack();panelBack();compose.onNodeWithText("Archivo").performScrollTo().performClick()
        compose.onNodeWithText("Guardar",substring=false).performClick();text("Dibujo guardado")
        val saved=WireCodec.decode(app.privateContext.getSharedPreferences(WireCodec.NAME,0).all.values.single() as String)
        assertEquals(0x40786699L,saved.elements.single().fill)
        panelBack();compose.onNodeWithText("Lienzo").performScrollTo().performClick()
        compose.onNodeWithTag("wire-color:Fondo:Oscuro").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Deshacer").performClick()
        compose.onNodeWithTag("wire-color:Fondo:Blanco").assertIsSelected()
        compose.onNodeWithContentDescription("Rehacer").performClick()
        compose.onNodeWithTag("wire-color:Fondo:Oscuro").assertIsSelected()
    }
    @Test fun signatureIsSharedByLibraryAndEditorExportOnly() {
        createDrawing()
        compose.onNodeWithText("Volver",substring=false).performClick();text("Mis diseños")
        compose.onNodeWithText("Firma PNG").assertDoesNotExist()
        compose.onNodeWithTag("tool-document-menu:${savedDrawing().id}").performClick();compose.onNodeWithText("Exportar PNG").performClick()
        compose.onNodeWithTag("wire-export-signature").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("tool-document:${savedDrawing().id}").performClick();compose.onNodeWithText("Archivo").performScrollTo().performClick()
        compose.onNodeWithText("Firma fuera del lienzo").assertDoesNotExist()
        compose.onNodeWithText("Exportar PNG").performClick()
        compose.onNodeWithTag("wire-export-signature").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText("Cancelar").performClick();panelBack()
        compose.onNodeWithText("Volver",substring=false).performClick();text("Mis diseños")
        compose.onNodeWithTag("tool-document-menu:${savedDrawing().id}").performClick();compose.onNodeWithText("Exportar PNG").performClick()
        compose.onNodeWithTag("wire-export-signature").assertIsOff().performClick()
        compose.onNodeWithText("Exportar",substring=false).performClick()
        val started=checkNotNull(org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivityForResult)
        assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT,started.intent.action)
        assertEquals("image/png",started.intent.type)
        compose.runOnIdle {org.robolectric.Shadows.shadowOf(compose.activity).receiveResult(started.intent,android.app.Activity.RESULT_CANCELED,null)}
    }
    private fun createDrawing(restoration:StateRestorationTester?=null) {
        if(restoration==null) compose.setContent {Arachn0deTheme {WireframeScreen {}}}
        else restoration.setContent {Arachn0deTheme {WireframeScreen {}}}
        text("Lienzo vacío")
        compose.onNodeWithText("Lienzo vacío").performClick();text("Aceptar")
        compose.onNodeWithText("Aceptar").performClick();text("Herramientas")
    }
    private fun panelBack() {compose.onNodeWithContentDescription("Volver al nivel anterior").performClick()}
    private fun androidBack() {compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};compose.waitForIdle()}
    private fun canvasPoint(x:Float,y:Float,device:Boolean=false,tools:Boolean=false,format:WireFormat=WireFormat.MOBILE):androidx.compose.ui.geometry.Offset {
        val size=compose.onNodeWithTag("wireframe-canvas").fetchSemanticsNode().size
        val density=compose.activity.resources.displayMetrics.density
        return wireViewport(size,format,density,device,tools).screen(androidx.compose.ui.geometry.Offset(x,y))
    }
    @Test fun catalogReturnScrollRepeatedSelectionFoldAndAndroidBack() {
        val restoration=StateRestorationTester(compose)
        createDrawing(restoration)
        compose.onNodeWithText("Añadir").performClick()
        compose.onNodeWithText("Formas y texto").performScrollTo().performClick()
        compose.onNodeWithTag("wire-resource:shape_RECT").performClick()
        compose.onNodeWithTag("wire-resource:shape_RECT").performClick()
        compose.onNodeWithTag("wireframe-resources").assertExists()
        val cards=listOf("RECT","SQUARE","ROUND_RECT","CIRCLE").map {kind->
            compose.onNodeWithTag("wire-resource:shape_$kind").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        cards.forEach {assertEquals(cards.first().height,it.height,.01f);assertEquals(cards.first().width,it.width,.01f)}
        compose.onNodeWithTag("wireframe-resources").performScrollToNode(hasTestTag("wire-resource:shape_IMAGE"))
        fun scroll()=compose.onNodeWithTag("wireframe-resources").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange].value()
        val offset=scroll();assertTrue(offset>0)
        val first=canvasPoint(.15f,.03f,tools=true)
        compose.onNodeWithTag("wireframe-canvas").performTouchInput {click(first)}
        text("Relleno")
        // A second canvas selection updates the temporary page without pushing another one.
        val second=canvasPoint(.15f,.17f,tools=true)
        compose.onNodeWithTag("wireframe-canvas").performTouchInput {click(second)}
        panelBack()
        compose.onNodeWithTag("wireframe-resources").assertExists();assertEquals(offset,scroll(),.01f)
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(offset,scroll(),.01f)
        compose.onNodeWithTag("wireframe-panel-fold").performClick()
        compose.onNodeWithContentDescription("Deshacer").assertExists()
        compose.onNodeWithTag("wireframe-resources").assertDoesNotExist()
        compose.onNodeWithTag("wireframe-panel-fold").performClick()
        assertEquals(offset,scroll(),.01f)
        androidBack();text("Añadir")
        compose.onNodeWithText("Botones").assertExists()
        androidBack();text("Herramientas")
        compose.onNodeWithText("Archivo").performScrollTo().performClick()
        compose.onNodeWithText("Guardar",substring=false).performClick();text("Dibujo guardado")
        panelBack()
        compose.onNodeWithText("Volver",substring=false).performClick();text("Mis diseños")
        compose.onNodeWithTag("tool-document:${savedDrawing().id}").performClick();text("Herramientas")
        val preferences=app.privateContext.getSharedPreferences(WireCodec.NAME,0)
        val saved=WireCodec.decode(preferences.all.values.single() as String)
        assertEquals(2,saved.elements.size);assertTrue(saved.elements.all {it.template=="shape_RECT"})
    }
    @Test fun elementsPropertiesFileGuidesAndUnsavedExit() {
        createDrawing()
        compose.onNodeWithText("Añadir").performClick()
        compose.onNodeWithText("Botones").performScrollTo().performClick()
        compose.onNodeWithTag("wire-resource:button").performClick()
        panelBack();panelBack()
        compose.onNodeWithText("Elementos").performScrollTo().performClick()
        compose.onNodeWithText("Botón rectangular · Continuar").performClick();text("Texto")
        compose.onNodeWithText("Texto").performTextReplacement("Aceptar cambios")
        panelBack()
        compose.onNodeWithText("Botón rectangular · Aceptar cambios").assertExists()
        panelBack()
        compose.onNodeWithText("Lienzo").performScrollTo().performClick()
        compose.onNodeWithText("Mostrar guías de alineación").assertDoesNotExist()
        compose.onNodeWithTag("wire-visual:GUIDES").assertExists()
        panelBack()
        compose.onNodeWithText("Añadir").performClick()
        compose.onNodeWithText("Entradas").performScrollTo().performClick()
        compose.onNodeWithTag("wire-resource:input").performClick()
        panelBack();panelBack()
        compose.onNodeWithText("Elementos").performScrollTo().performClick()
        compose.onNodeWithText("Campo simple · Escribe aquí |").performClick()
        compose.onNodeWithText("Relleno · AARRGGBB").assertDoesNotExist()
        compose.onNodeWithText("Texto / icono").assertExists()
        panelBack();panelBack()
        compose.onNodeWithText("Archivo").performScrollTo().performClick()
        compose.onNodeWithText("Exportar PNG").assertExists()
        compose.onNodeWithText("Firma fuera del lienzo").assertDoesNotExist()
        androidBack();text("Herramientas")
        androidBack();text("Cambios sin guardar")
        compose.onNodeWithText("Descartar").performClick();text("Mis diseños")
    }
    private fun savedDrawing():WireDocument = WireCodec.decode(app.privateContext.getSharedPreferences(WireCodec.NAME,0).all.values.single() as String)
    private fun saveFromScreenDetail() {
        repeat(4) {panelBack()}
        compose.onNodeWithText("Archivo").performScrollTo().performClick();compose.onNodeWithText("Guardar",substring=false).performClick();text("Dibujo guardado")
    }
    @Test fun createFromScreenEditIndividualAndUndoWholeInsertion() {
        compose.setContent {Arachn0deTheme {WireframeScreen {}}}
        text("Crear desde plantilla");compose.onNodeWithText("Crear desde plantilla").performClick();text("Plantillas")
        compose.onAllNodesWithText("Acceso y formularios").onFirst().performClick()
        compose.onNodeWithTag("tool-document:login").performClick();text("Herramientas")
        compose.onNodeWithContentDescription("Deshacer").performClick()
        compose.onNodeWithContentDescription("Deshacer").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Rehacer").performClick()
        compose.onNodeWithText("Archivo").performScrollTo().performClick();compose.onNodeWithText("Guardar",substring=false).performClick();text("Dibujo guardado")
        val inserted=savedDrawing();assertEquals(WireScreens.items.first().elements().size,inserted.elements.size)
        assertEquals("Inicio de sesión",inserted.name)
        panelBack();compose.onNodeWithText("Elementos").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Bienvenido",substring=true))
        compose.onNode(hasText("Bienvenido",substring=true)).performClick();text("Texto")
        compose.onNodeWithText("Texto").performTextReplacement("Mi espacio")
        panelBack();panelBack();compose.onNodeWithText("Archivo").performScrollTo().performClick()
        compose.onNodeWithText("Guardar",substring=false).performClick()
        compose.waitUntil(15000) {pump();savedDrawing().elements.any {it.text=="Mi espacio"}}
        assertEquals(inserted.elements.size,savedDrawing().elements.size)
        assertEquals(1,savedDrawing().elements.count {it.text=="Mi espacio"})
    }
    @Test fun populatedCanvasConfirmationAndSingleUndoKeepOriginal() {
        createDrawing()
        compose.onNodeWithText("Añadir").performClick();compose.onNodeWithText("Botones").performScrollTo().performClick()
        compose.onNodeWithTag("wire-resource:button").performClick();panelBack()
        compose.onNodeWithText("Pantallas").performScrollTo().performClick();compose.onAllNodesWithText("Acceso y formularios").onFirst().performClick()
        compose.onNodeWithTag("wire-screen:login").performClick();compose.onNodeWithText("Insertar",substring=false).performClick()
        text("Insertar pantalla");compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Insertar",substring=false).performClick();compose.onNodeWithText("Insertar de todos modos").performClick()
        compose.onNodeWithContentDescription("Deshacer").performClick()
        saveFromScreenDetail();assertEquals(listOf("button"),savedDrawing().elements.map {it.template})
        panelBack();compose.onNodeWithContentDescription("Rehacer").performClick()
        compose.onNodeWithText("Archivo").performScrollTo().performClick();compose.onNodeWithText("Guardar",substring=false).performClick()
        compose.waitUntil(15000) {pump();savedDrawing().elements.size>1}
        assertEquals(1+WireScreens.items.first().elements().size,savedDrawing().elements.size)
        assertEquals("button",savedDrawing().elements.first().template)
    }
    @Test fun incompatibleScreenRequiresExplicitCanvasFormatAndPreservesNavigation() {
        createDrawing()
        compose.onNodeWithText("Añadir").performClick();compose.onNodeWithText("Pantallas").performScrollTo().performClick()
        compose.onNodeWithText("Escritorio y web").performScrollTo().performClick()
        compose.onNodeWithTag("wire-screen:web").performClick();compose.onNodeWithText("Insertar",substring=false).performClick()
        text("Formato del molde");compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithContentDescription("Deshacer").assertIsNotEnabled()
        compose.onNodeWithText("Insertar",substring=false).performClick();compose.onNodeWithText("Elegir formato").performClick()
        text("Lienzo");compose.onNodeWithText("Escritorio",substring=false).performClick();panelBack()
        compose.onNodeWithText("Añadir").performClick();compose.onNodeWithText("Pantallas").performScrollTo().performClick()
        compose.onNodeWithText("Escritorio y web").performScrollTo().performClick()
        compose.onNodeWithTag("wire-screen:web").performClick();compose.onNodeWithText("Insertar",substring=false).performClick()
        saveFromScreenDetail();assertEquals(WireFormat.DESKTOP,savedDrawing().format)
        assertEquals(WireScreens.items.first {it.id=="web"}.elements().size,savedDrawing().elements.size)
    }

    @Test fun newDesktopFromSameCatalogRestoresDraftAndSavesNativeFormat() {
        val restoration=StateRestorationTester(compose)
        restoration.setContent {Arachn0deTheme {WireframeScreen {}}}
        text("Crear desde plantilla");compose.onNodeWithText("Crear desde plantilla").performClick();text("Plantillas")
        assertTrue(app.privateContext.getSharedPreferences(WireCodec.NAME,0).all.isEmpty())
        compose.onNodeWithText("Escritorio y web").performScrollTo().performClick()
        compose.onNodeWithTag("tool-document:web").performClick();text("Herramientas")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Archivo").performScrollTo().performClick()
        compose.onNodeWithText("Guardar",substring=false).performClick();text("Dibujo guardado")
        val doc=savedDrawing();assertEquals(WireFormat.DESKTOP,doc.format)
        assertEquals("Página con barra de navegación superior",doc.name)
        assertEquals(WireScreens.items.first {it.id=="web"}.elements().size,doc.elements.size)
    }

    @Test fun fourEdgeHandlesResizeIndependentAxesAndCancelWithoutCommit() {
        val original=WireDocument(elements=listOf(WireTemplates.items.first {it.id=="toolbar"}.element(WireFormat.MOBILE).copy(x=.2f,y=.2f,w=.5f,h=.15f)))
        var document by androidx.compose.runtime.mutableStateOf(original);var commits=0
        compose.setContent {
            val renderer=androidx.compose.runtime.remember {WireframeRenderer(compose.activity,emptyMap())}
            WireCanvas(document,renderer,original.elements.single().id,true,{}, {document=it;commits++})
        }
        val e=original.elements.single();val right=canvasPoint(e.x+e.w,e.y+e.h/2)
        compose.onNodeWithTag("wireframe-canvas").performTouchInput {swipe(right,right-androidx.compose.ui.geometry.Offset(35f,0f),300)}
        compose.runOnIdle {assertEquals(1,commits);assertEquals(e.h,document.elements.single().h,0f);assertTrue(document.elements.single().w<e.w);assertNotNull(document.elements.single().contentUnit)}
        val resized=document.elements.single();val bottom=canvasPoint(resized.x+resized.w/2,resized.y+resized.h)
        compose.onNodeWithTag("wireframe-canvas").performTouchInput {swipe(bottom,bottom-androidx.compose.ui.geometry.Offset(0f,25f),300)}
        compose.runOnIdle {assertEquals(2,commits);assertEquals(resized.w,document.elements.single().w,0f);assertTrue(document.elements.single().h<resized.h)}
        val beforeCancel=document;val a=document.elements.single();val left=canvasPoint(a.x,a.y+a.h/2)
        compose.onNodeWithTag("wireframe-canvas").performTouchInput {down(left);moveBy(androidx.compose.ui.geometry.Offset(20f,0f));cancel()}
        compose.runOnIdle {assertEquals(2,commits);assertEquals(beforeCancel,document)}
    }
    @Test fun individualIconsAnnotationsNavigationRestoreAndObservedExportCancellation() {
        val restoration=StateRestorationTester(compose);createDrawing(restoration)
        compose.onNodeWithText("Añadir").performClick();compose.onNodeWithText("Navbar").performScrollTo().performClick()
        compose.onNodeWithTag("wireframe-resources").performScrollToNode(hasTestTag("wire-resource:toolbar"))
        compose.onNodeWithTag("wire-resource:toolbar").performClick();panelBack();panelBack()
        compose.onNodeWithText("Elementos").performScrollTo().performClick()
        compose.onNode(hasText(WireTemplates.items.first {it.id=="toolbar"}.label,substring=true)).performClick()
        compose.onNodeWithTag("wire-icon-slot:0").performScrollTo().performClick()
        compose.onNodeWithTag("wire-icon-picker").performScrollToNode(hasTestTag("wire-icon:star"))
        compose.onNodeWithTag("wire-icon:star").performClick();panelBack()
        compose.onNodeWithText("Anotar elemento").performScrollTo().performClick()
        compose.onNodeWithText("Añadir anotación").performClick()
        val point=canvasPoint(.5f,.5f,tools=true)
        compose.onNodeWithTag("wireframe-canvas").performTouchInput {click(point)}
        compose.onNodeWithText("Título de observación (opcional)").performScrollTo().performTextReplacement("Navegación principal")
        compose.onNodeWithText("Observación",substring=false).performScrollTo().performTextReplacement("Sin administración.\nSolo navegación pública.")
        compose.onNodeWithText("Vincular al elemento seleccionado").onParent().performScrollTo()
        compose.onNodeWithText("Vincular al elemento seleccionado").performScrollTo().performClick()
        compose.onNodeWithText("Vinculada a un elemento").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Título de observación (opcional)").performScrollTo().assertTextContains("Navegación principal")
        panelBack();compose.onNodeWithText("Archivo").performScrollTo().performClick();compose.onNodeWithText("Guardar",substring=false).performClick();text("Dibujo guardado")
        val saved=savedDrawing();assertEquals("star",saved.elements.single().icons[0]);assertEquals(saved.elements.single().id,saved.annotations.single().elementId)
        assertEquals("Sin administración.\nSolo navegación pública.",saved.annotations.single().text)
        compose.onNodeWithText("Exportar PNG").performClick();compose.onNodeWithText("Exportar con observaciones").performClick();compose.onNodeWithText("Exportar",substring=false).performClick()
        val started=checkNotNull(org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivityForResult)
        assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE,started.intent.action)
        compose.runOnIdle {org.robolectric.Shadows.shadowOf(compose.activity).receiveResult(started.intent,android.app.Activity.RESULT_CANCELED,null)}
        assertEquals(saved,savedDrawing())
    }
    @Test fun annotationDragCancellationVisibilityAndUndoRemainSeparateFromElements() {
        val a=WireAnnotation(x=.3f,y=.3f,text="Mover marcador");val original=WireDocument(annotations=listOf(a))
        var document by androidx.compose.runtime.mutableStateOf(original);var show by androidx.compose.runtime.mutableStateOf(true);var commits=0
        compose.setContent {
            val renderer=androidx.compose.runtime.remember {WireframeRenderer(compose.activity,emptyMap())}
            WireCanvas(document,renderer,null,true,{}, {document=it;commits++},showAnnotations=show,annotationMode=true)
        }
        val start=canvasPoint(.3f,.3f);val end=canvasPoint(.5f,.5f)
        compose.onNodeWithTag("wireframe-canvas").performTouchInput {down(start);moveTo(end);cancel()}
        compose.runOnIdle {assertEquals(original,document);assertEquals(0,commits)}
        compose.onNodeWithTag("wireframe-canvas").performTouchInput {swipe(start,end,300)}
        compose.runOnIdle {assertEquals(1,commits);assertTrue(document.elements.isEmpty());assertEquals(.5f,document.annotations.single().x,.0001f);show=false}
        compose.onNodeWithTag("wireframe-canvas").performTouchInput {swipe(end,start,300)}
        compose.runOnIdle {assertEquals(1,commits);val history=WireHistory();history.record(original,document);assertEquals(original,history.undo(document))}
    }

}
