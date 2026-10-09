package com.r0ybt.arachn0de.metro

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.defaults.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.*
import com.r0ybt.arachn0de.ui.state.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
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
class MetroNativeUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {app.database.close()}
    private fun exists(text:String)=compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun await(predicate:()->Boolean)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();predicate()
    }
    private fun station(field:String,query:String,name:String) {
        compose.onNodeWithText(field).performScrollTo().performClick()
        compose.onNodeWithText("Buscar estación").performTextInput(query)
        compose.onNodeWithText(name).performClick()
    }
    private fun capture(name:String) {
        compose.runOnIdle {
            val wm=Class.forName("android.view.WindowManagerGlobal");val manager=wm.getDeclaredMethod("getInstance").invoke(null)
            val views=wm.getDeclaredField("mViews").apply {isAccessible=true}.get(manager) as List<*>
            val view=views.last() as android.view.View
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(bitmap))
            val dir=File("/tmp/arachnode-metro-native-review").apply {mkdirs()}
            File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    private fun createInContext(depth:Int)=runBlocking<Unit> {
        val p=app.projectRepository.createProject("Proyecto nativo")
        val layer=app.nodeRepository.createNode(p.id,null,"Capa de pruebas",purpose=NodePurpose.LAYER)
        val nested=app.nodeRepository.createNode(p.id,layer.id,"Subcapa de pruebas",purpose=NodePurpose.LAYER)
        // The integration must be visible even with a non-task inherited default.
        app.nodeRepository.creationDefaults.save(DefaultsScope.Project(p.id),CreationDefaults(purpose=DefaultValue.Own(if(depth==2) NodePurpose.LAYER else NodePurpose.NOTE)))
        compose.setContent {Arachn0deTheme {AppSafeArea {AppRoot(app.projectRepository,app.nodeRepository)}}}
        await {exists("Proyecto nativo")};compose.onNodeWithText("Proyecto nativo").performClick()
        await {exists("Nuevo elemento")}
        if(depth>0) {compose.onNodeWithText("Capa de pruebas").performClick();await {exists("Subcapa de pruebas")}}
        if(depth>1) {compose.onNodeWithText("Subcapa de pruebas").performClick()}
        compose.onNodeWithText("Nuevo elemento").performClick();await {exists("Título")}
        compose.onNodeWithText("Título").performTextInput("Viaje de pruebas")
        compose.onNodeWithText("Viaje Metro").performScrollTo().performClick()
        station("Origen: Elegir estación","los heroes","Los Héroes")
        station("Destino: Elegir estación","republica","República")
        capture("crear-contexto-$depth")
        compose.onNodeWithText("Crear").assertIsEnabled().performClick()
        await {runBlocking {app.metroRepository.snapshot().journeys.isNotEmpty()}}
        val journey=app.metroRepository.snapshot().journeys.single();val task=app.database.nodeDao().getById(journey.row.nodeId!!)!!
        assertEquals(p.id,task.projectId);assertEquals(when(depth) {0->null;1->layer.id;else->nested.id},task.parentId)
        assertEquals("ACTION",task.purpose);assertEquals("Viaje de pruebas",task.title)
        assertEquals(listOf("los-heroes","republica"),journey.data.plan.stops)
        compose.onNodeWithText("Metro de Santiago").assertDoesNotExist()
        await {exists("Viaje de pruebas")};compose.onNodeWithText("Nuevo elemento").assertExists()
    }
    @Test fun habitualProjectCreationCanChooseTripWithNoteDefault()=createInContext(0)
    @Test fun habitualLayerCreationKeepsCurrentLayer()=createInContext(1)
    @Test fun habitualSubLayerCreationCanChooseTripWithLayerDefault()=createInContext(2)
    @Test fun selectExistingCancelRestoreThenAttachOnceAndEditInsideSameForm()=runBlocking<Unit> {
        val p=app.projectRepository.createProject("P");val parent=app.nodeRepository.createNode(p.id,null,"Capa",purpose=NodePurpose.LAYER)
        val net=app.metroRepository.snapshot().preferences.planningNetwork
        val plan=MetroPlanner.plan(net,listOf("los-heroes","republica"),false,MetroRestrictions())!!
        val id=app.metroRepository.savePlan(plan)
        val draft=EditorDraft(null,parent.id,"Tarjeta vinculada","").apply {attachmentsLoaded=true}
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main);val actions=NodeActions(app.nodeRepository,scope)
        val restoration=StateRestorationTester(compose)
        var done=false
        restoration.setContent {Arachn0deTheme {NodeDialog(draft,actions.operation.busy,{}, {_,_->actions.saveDraft(p.id,draft,true) {done=true}})}}
        compose.onNodeWithText("Viaje Metro").performClick()
        await {exists("Elegir viaje existente")};compose.onNodeWithText("Elegir viaje existente").performClick()
        compose.onNodeWithText("Los Héroes → República").performClick();await {draft.metroJourneyId==id}
        assertNull(app.metroRepository.snapshot().journeys.single().row.nodeId)
        restoration.emulateSavedInstanceStateRestore();await {exists("Destino: República")}
        capture("vincular-existente")
        compose.onNodeWithText("Crear").performClick();await {done}
        assertEquals(id,app.metroRepository.snapshot().journeys.single().row.id);assertEquals(parent.id,app.database.nodeDao().getById(draft.creationId)!!.parentId)
        val edit=EditorDraft(draft.creationId,parent.id,"Tarjeta vinculada","").apply {attachmentsLoaded=true}
        done=false
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {NodeDialog(edit,actions.operation.busy,{}, {_,_->actions.saveDraft(p.id,edit,true) {done=true}})}}}
        await {exists("Destino: República")};station("Destino: República","tobalaba","Tobalaba")
        capture("editar-inline")
        compose.onNodeWithText("Guardar").performClick();await {done}
        val saved=app.metroRepository.snapshot().journeys.single();assertEquals(id,saved.row.id);assertEquals(draft.creationId,saved.row.nodeId);assertEquals("tobalaba",saved.data.plan.stops.last())
        scope.cancel()
    }
    @Test fun plannerViaNameRemainsVisibleOnSmallScreenAndCanBeChanged()=runBlocking<Unit> {
        val net=app.metroRepository.snapshot().preferences.planningNetwork
        app.metroRepository.savePlan(MetroPlanner.plan(net,listOf("los-heroes","universidad-de-santiago","tobalaba"),false,MetroRestrictions())!!)
        compose.setContent {Arachn0deTheme {Surface(Modifier.fillMaxSize()) {MetroScreen(emptyList(),emptyList(),onBack={})}}}
        await {exists("Viajes")};compose.onNodeWithText("Viajes").performClick();compose.onNodeWithText("Editar plan").performClick()
        compose.onNodeWithText("Parada 1: Universidad de Santiago").performScrollTo().assertIsDisplayed()
        capture("parada-larga-planificar")
        station("Parada 1: Universidad de Santiago","estacion central","Estación Central")
        compose.onNodeWithText("Parada 1: Estación Central").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Quitar").performClick();compose.onNodeWithText("Parada 1: Estación Central").assertDoesNotExist()
    }
}
