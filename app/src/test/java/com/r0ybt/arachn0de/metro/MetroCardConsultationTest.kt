package com.r0ybt.arachn0de.metro

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.ui.AppRoot
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class MetroCardConsultationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {MetroNavigation.requests.value=null;app.database.close()}
    private fun exists(text:String)=compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun await(predicate:()->Boolean)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();predicate()
    }
    private fun fixture()=runBlocking {
        val project=app.projectRepository.createProject("Proyecto consulta")
        val layer=app.nodeRepository.createNode(project.id,null,"Capa consulta",purpose=NodePurpose.LAYER)
        val nested=app.nodeRepository.createNode(project.id,layer.id,"Subcapa consulta",purpose=NodePurpose.LAYER)
        val task=app.nodeRepository.createNode(project.id,nested.id,"Tarjeta viaje")
        val net=app.metroRepository.snapshot().preferences.network
        val plan=MetroPlanner.plan(net,listOf("republica","universidad-de-santiago","el-parron"),false,MetroRestrictions())!!
        val id=app.metroRepository.savePlan(plan,task.id)
        task.id to id
    }
    @Test fun previewOpensPendingRouteAndBackReturnsToDeepCardWhileEditingIsSeparate()=runBlocking<Unit> {
        val (nodeId,id)=fixture()
        val before=app.metroRepository.snapshot()
        assertTrue(before.journeys.single().data.plan.transfers>0)
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        await {exists("Proyecto consulta")};compose.onNodeWithText("Proyecto consulta").performClick()
        await {exists("Capa consulta")};compose.onNodeWithText("Capa consulta").performClick()
        await {exists("Subcapa consulta")};compose.onNodeWithText("Subcapa consulta").performClick()
        val label="Ver recorrido · República → Universidad de Santiago → El Parrón"
        await {exists(label)};compose.onNodeWithText(label).performScrollTo().performClick()
        await {exists("Pendiente")}
        compose.onNodeWithText("Comenzar viaje").assertExists()
        compose.onNodeWithText("Título").assertDoesNotExist()
        compose.onNodeWithText("Editar viaje").assertExists()
        compose.onNodeWithTag("metro-timeline").performScrollToIndex(0)
        compose.onNodeWithText("L1 · Dirección San Pablo · Tomar tren normal").assertExists()
        compose.onNodeWithTag("metro-timeline").performScrollToNode(hasText("Universidad de Santiago"))
        compose.onAllNodesWithText("Universidad de Santiago").onFirst().assertExists()
        compose.onNodeWithTag("metro-timeline").performScrollToNode(hasText("Combina con",substring=true))
        compose.onNode(hasText("Combina con",substring=true)).assertExists()
        compose.onNodeWithTag("metro-timeline").performScrollToNode(hasText("El Parrón"))
        compose.onNodeWithText("El Parrón").assertExists()
        assertEquals(before,app.metroRepository.snapshot())
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};await {exists(label)}
        compose.onNodeWithText("Nuevo elemento").assertExists()
        compose.onNodeWithText("Tarjeta viaje",useUnmergedTree=true).performClick();await {exists(label)}
        compose.onNodeWithText(label).performScrollTo().performClick();await {exists("Pendiente")}
        compose.onNodeWithText("Volver").performClick();await {exists(label)}
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};await {exists(label)}
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasTestTag("node-options:$nodeId"))
        compose.onNodeWithTag("node-options:$nodeId").performClick()
        compose.onNodeWithText("Editar").performScrollTo().performClick()
        await {exists("Título")}
        assertEquals(id,app.metroRepository.snapshot().journeys.single().row.id)
        assertEquals(before,app.metroRepository.snapshot())
    }
    private fun consultation(state:String,purpose:NodePurpose=NodePurpose.ACTION)=runBlocking<Unit> {
        val (nodeId,id)=fixture();val repo=app.metroRepository
        if(purpose!=NodePurpose.ACTION) assertTrue(app.nodeRepository.convertPurpose(nodeId,purpose))
        val time=metroTime(compose.activity)
        if(state!="Pendiente") {
            var j=repo.snapshot().journeys.single()
            repo.begin(id,j.row.revision,time)
            j=repo.snapshot().journeys.single()
            repo.tracking(id,j.row.revision) {MetroTracking.confirm(it,"los-heroes",time)}
            j=repo.snapshot().journeys.single()
            if(state=="Reanudar") repo.tracking(id,j.row.revision) {MetroTracking.pause(it,time)}
            if(state=="Finalizado") repo.tracking(id,j.row.revision) {MetroTracking.finish(it,time)}
        }
        val before=repo.snapshot()
        val restoration=StateRestorationTester(compose)
        var backs=0
        restoration.setContent {Arachn0deTheme {MetroScreen(listOf(requireNotNull(runBlocking {app.nodeRepository.getNode(nodeId)})),emptyList(),nodeId,requestToken=71,onBack={backs++})}}
        await {exists(state)}
        assertEquals(before,repo.snapshot())
        restoration.emulateSavedInstanceStateRestore();await {exists(state)}
        assertEquals(before,repo.snapshot())
        compose.onNodeWithText("Volver").performClick();assertEquals(1,backs)
        assertEquals(before,repo.snapshot())
        if(state=="Finalizado") {assertNull(repo.snapshot().journeys.single().data.active);assertNotNull(repo.snapshot().journeys.single().data.sessions.single().ended)}
    }
    @Test fun convertedNoteKeepsExistingJourneyReadableWithoutNewAssociation()=consultation("Pendiente",NodePurpose.NOTE)
    @Test fun convertedLayerKeepsExistingJourneyReadableWithoutNewAssociation()=consultation("Pendiente",NodePurpose.LAYER)
    @Test fun pendingConsultationNeverStartsOrDuplicatesJourney()=consultation("Pendiente")
    @Test fun activeConsultationKeepsCorrectionsAndDoesNotRestart()=consultation("Pausar")
    @Test fun pausedConsultationKeepsPauseAndProgressOnSavedStateRestore()=consultation("Reanudar")
    @Test fun finishedConsultationKeepsFinalSessionAndRequiresExplicitNewStart()=consultation("Finalizado")
    @Test fun associatedPendingTripIsNotReplacedByAnotherActiveTrip()=runBlocking<Unit> {
        val (nodeId,id)=fixture();val repo=app.metroRepository;val net=repo.snapshot().preferences.network
        val other=repo.savePlan(MetroPlanner.plan(net,listOf("tobalaba","los-heroes"),false,MetroRestrictions())!!)
        val j=repo.snapshot().journeys.first {it.row.id==other};repo.begin(other,j.row.revision,metroTime(compose.activity))
        val before=repo.snapshot()
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),nodeId,requestToken=99,onBack={})}}
        await {exists("Pendiente")}
        compose.onNodeWithText("Pausar").assertDoesNotExist()
        assertEquals(before,repo.snapshot());assertEquals(nodeId,repo.snapshot().journeys.first {it.row.id==id}.row.nodeId)
        compose.onNodeWithText("Editar viaje").performClick();await {exists("Origen: República")}
        assertEquals(before,repo.snapshot())
    }
}
