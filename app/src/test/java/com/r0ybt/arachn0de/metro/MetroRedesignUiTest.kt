package com.r0ybt.arachn0de.metro

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.NodeDialog
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.state.NodeActions
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w320dp-h800dp")
class MetroRedesignUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {app.database.close()}
    private fun exists(text:String)=compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun await(predicate:()->Boolean) = compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();predicate()
    }
    // PixelCopy captureToImage does not receive a frame under this Robolectric setup.
    // Render the actual last window into a native Canvas instead (including dialogs).
    private fun renderedWindow():Bitmap {
        lateinit var bitmap:Bitmap
        compose.runOnIdle {
            val wm=Class.forName("android.view.WindowManagerGlobal")
            val manager=wm.getDeclaredMethod("getInstance").invoke(null)
            val views=wm.getDeclaredField("mViews").apply {isAccessible=true}.get(manager) as List<*>
            val view=views.last() as android.view.View
            bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
        }
        return bitmap
    }
    private fun capture(name:String) {
        val bitmap=renderedWindow()
        val directory=File("/tmp/arachnode-metro-review").apply {mkdirs()}
        File(directory,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun screen() {compose.setContent {Arachn0deTheme {Surface(Modifier.fillMaxSize()) {MetroScreen(emptyList(),emptyList(),onBack={})}}};await {exists("Planificar viaje")}}
    @Test fun explorerClassificationContextRestrictionsAndNormalizedPickerOnSmallScreen() {
        screen();capture("01-inicio")
        compose.onNodeWithText("Líneas").performClick();capture("02-explorar")
        compose.onNodeWithText("San Pablo").performClick();capture("03-estacion")
        compose.onNodeWithText("Estoy aquí").assertDoesNotExist();compose.onNodeWithText("Cerrar para pasajeros").assertDoesNotExist()
        compose.onNodeWithText("Usar como origen").performClick()
        compose.onNodeWithText("Destino: Elegir estación").performClick();capture("04-selector")
        compose.onNodeWithText("Buscar estación").performTextInput(" NUNOA ")
        compose.onNodeWithText("Ñuñoa").assertExists();compose.onNodeWithText("Ñuñoa").performClick()
        compose.onNodeWithText("Buscar ruta").performScrollTo().performClick();capture("05-planificar")
        compose.onNodeWithTag("metro-planning").performScrollToNode(hasText("Iniciar viaje"))
        compose.onNodeWithText("Iniciar viaje").assertIsDisplayed()
        compose.onNodeWithText("Líneas").performClick();compose.onNodeWithText("L2").performClick()
        compose.onNodeWithText("Vespucio Norte").assertExists();compose.onAllNodesWithText("Roja").onFirst().assertExists();compose.onAllNodesWithText("Verde").onFirst().assertExists()
        capture("06-roja-verde")
        compose.onNodeWithText("Inicio").performClick();compose.onNodeWithText("Gestionar restricciones").performScrollTo().performClick();capture("07-restricciones")
        compose.onNodeWithText("Agregar desde el mapa").performClick();capture("07b-gestionar-mapa");compose.onNodeWithText("Vespucio Norte").performClick();capture("07c-gestionar-estacion")
        compose.onNodeWithText("Cerrar para pasajeros").performClick();compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {"vespucio-norte" in app.metroRepository.snapshot().preferences.restrictions.closed}}
        compose.onNodeWithText("Salir de gestión").performClick();compose.onNodeWithText("Vespucio Norte").performClick();compose.onNodeWithText("Reabrir para pasajeros").assertDoesNotExist()
    }
    @Test fun timelineIsContinuousWithVariableHeightsLargeTextAndMarkerInterpolatesRealCenters() {
        val net=runBlocking {app.metroRepository.snapshot().preferences.network}
        var fraction by mutableStateOf(0f)
        val list=androidx.compose.foundation.lazy.LazyListState()
        val rows=listOf(MetroTimelineNode("vespucio-norte","L2","Primera estación"),MetroTimelineNode("los-heroes","L2","Combinación y nombre largo\nInformación adicional que aumenta la altura\nOtra línea de texto"),MetroTimelineNode("hospital-el-pino","L2","Destino"))
        compose.setContent {Arachn0deTheme {CompositionLocalProvider(LocalDensity provides Density(1f,1.6f)) {Surface {MetroVerticalTimeline(rows,net,Modifier.width(320.dp).height(380.dp),list=list,progress=0 to fraction,avatar={androidx.compose.material3.Text("Ana")},onStation={})}}}}
        val first=compose.onNodeWithTag("metro-row-0").fetchSemanticsNode().boundsInRoot
        val second=compose.onNodeWithTag("metro-row-1").fetchSemanticsNode().boundsInRoot
        assertEquals(first.bottom,second.top,.1f);assertTrue(second.height>first.height)
        val initial=compose.onNodeWithTag("metro-avatar").fetchSemanticsNode().boundsInRoot.center
        compose.runOnIdle {fraction=.5f}
        val half=compose.onNodeWithTag("metro-avatar").fetchSemanticsNode().boundsInRoot.center
        assertEquals(first.height*.5f,half.y-initial.y,1f);assertEquals(initial.x,half.x,.1f)
        val pixels=renderedWindow()
        // Scan the actual rendered rail, across both row bounds; circles may vary but never background.
        val railX=22
        val windowTop=compose.activity.window.decorView.rootWindowInsets?.systemWindowInsetTop ?: 0
        val bg=pixels.getPixel(0,second.top.toInt()+1+windowTop)
        for(y in (first.top.toInt()+30)..(second.bottom.toInt()-1)) assertNotEquals("Rail gap at $y",bg,pixels.getPixel(railX,y+windowTop))
        capture("08-timeline-large-text")
        compose.onNodeWithTag("metro-timeline").performTouchInput {swipeUp()};assertTrue(list.firstVisibleItemIndex>0 || list.firstVisibleItemScrollOffset>0);capture("09-timeline-scroll")
    }
    @Test fun taskTripUsesCurrentSubLayerAndTemplatePlanRemainsEditableWithoutHistory()=runBlocking<Unit> {
        val project=app.projectRepository.createProject("P")
        val parent=app.nodeRepository.createNode(project.id,null,"Capa",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val draft=EditorDraft(null,parent.id,"Viaje de trabajo","").apply {attachmentsLoaded=true}
        val scope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.Main)
        val actions=NodeActions(app.nodeRepository,scope)
        var done by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {NodeDialog(draft,actions.operation.busy,{}, {_,_->actions.saveDraft(project.id,draft,true) {done=true}})}}
        await {exists("Viaje Metro")};compose.onNodeWithText("Viaje Metro").performClick()
        compose.onNodeWithText("Origen: Elegir estación").performClick();compose.onNodeWithText("Buscar estación").performTextInput("los heroes");compose.onNodeWithText("Los Héroes").performClick()
        compose.onNodeWithText("Destino: Elegir estación").performScrollTo().performClick();compose.onNodeWithText("Buscar estación").performTextInput("republica");compose.onNodeWithText("República").performClick()
        await {draft.templateMetroPlan!=null};capture("10-tarea-viaje")
        compose.onNodeWithText("Crear").performClick();await {done}
        val journey=app.metroRepository.snapshot().journeys.single();val node=app.database.nodeDao().getById(journey.row.nodeId!!)!!
        assertEquals(parent.id,node.parentId);assertEquals(project.id,node.projectId);assertEquals("Viaje de trabajo",node.title);assertTrue(journey.data.sessions.isEmpty())
        val template=app.savedTemplateRepository.fromTask(node.id)
        val other=EditorDraft(null,parent.id,"","").apply {attachmentsLoaded=true}
        template.apply(other,System.currentTimeMillis(),java.util.TimeZone.getDefault())
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {NodeDialog(other,false,{}, {_,_->})}}}
        await {exists("Destino: República")};compose.onNodeWithText("Destino: República").performScrollTo().performClick()
        compose.onNodeWithText("Buscar estación").performTextInput("tobalaba");compose.onNodeWithText("Tobalaba").performClick()
        await {MetroCodec.journey(other.templateMetroPlan!!,MetroNetwork.decode(other.templateMetroCatalog!!)).plan.stops.last()=="tobalaba"}
        assertTrue(MetroCodec.journey(other.templateMetroPlan!!,MetroNetwork.decode(other.templateMetroCatalog!!)).sessions.isEmpty());capture("11-plantilla-viaje")
        val edit=EditorDraft(node.id,parent.id,node.title,node.description).apply {attachmentsLoaded=true}
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {NodeDialog(edit,false,{}, {_,_->})}}}
        await {exists("Destino: República")};capture("14-editar-tarea")
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
    @Test fun savedTripStartsImmediatelyAndTrackingAvatarAndCorrectionKeepPause()=runBlocking<Unit> {
        com.r0ybt.arachn0de.backup.BackupRepository(app.database,app,com.r0ybt.arachn0de.backup.BackupAvatarFiles(app,com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory)).restore(com.r0ybt.arachn0de.backup.BackupFixture.complete())
        val net=app.metroRepository.snapshot().preferences.network
        val route=MetroPlanner.plan(net,listOf("los-heroes","republica"),false,MetroRestrictions())!!
        val id=app.metroRepository.savePlan(route,personId="r")
        screen();compose.onNodeWithText("Viajes").performClick();capture("12-viajes")
        compose.onNodeWithTag("metro-trip:$id").performClick();await {exists("Comenzar viaje")};compose.onNodeWithText("Comenzar viaje").performClick();await {exists("Pausar")};capture("13-seguimiento")
        val journey=app.metroRepository.snapshot().journeys.single();assertTrue(journey.data.active!!.automatic)
        compose.onNodeWithTag("metro-avatar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Roy").assertExists()
        await {com.r0ybt.arachn0de.data.local.AvatarStore(app).readThumbnail(runBlocking {app.database.personDao().get("r")!!}.avatarFile!!)!=null}
        val avatar=compose.onNodeWithTag("metro-avatar").fetchSemanticsNode().boundsInRoot.center
        // The shared PersonAvatar renders the original magenta fixture, rather than a generic icon.
        await {renderedWindow().getPixel(avatar.x.toInt(),avatar.y.toInt())==android.graphics.Color.MAGENTA}
        capture("13b-avatar-real")
        compose.onNodeWithText("Pausar").performClick();await {exists("Reanudar")}
        compose.onNode(hasText("Los Héroes") and hasClickAction()).performClick();compose.onNodeWithText("Estoy aquí").performClick()
        await {runBlocking {app.metroRepository.snapshot().journeys.single().data.active!!.events.any {it.kind=="CONFIRM"}}}
        assertNotNull(app.metroRepository.snapshot().journeys.single().data.active!!.pausedAt)
        compose.onNode(hasText("Los Héroes") and hasClickAction()).performClick()
        compose.onNodeWithText("Corrección avanzada").performClick()
        compose.onNodeWithText("Estoy entre estaciones (desde aquí, aproximado)").performScrollTo().performClick()
        await {runBlocking {app.metroRepository.snapshot().journeys.single().data.active!!.events.last().kind=="BETWEEN"}}
        val row=compose.onNodeWithTag("metro-row-0").fetchSemanticsNode().boundsInRoot
        val marker=compose.onNodeWithTag("metro-avatar").fetchSemanticsNode().boundsInRoot.center
        assertEquals(row.top+30f+row.height*.5f,marker.y,1f)
        capture("13c-avatar-entre-estaciones")
        assertNotNull(app.metroRepository.snapshot().journeys.single().data.active!!.pausedAt)
    }
}
