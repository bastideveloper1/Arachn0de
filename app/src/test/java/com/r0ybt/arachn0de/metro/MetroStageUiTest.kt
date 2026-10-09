package com.r0ybt.arachn0de.metro

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
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
class MetroStageUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {MetroNavigation.requests.value=null;app.database.close()}
    private fun exists(text:String)=compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun await(block:()->Boolean) {try {compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();block()
    }} catch(e:Throwable) {compose.onRoot(useUnmergedTree=true).printToLog("MetroStageFailure");println(compose.onRoot(useUnmergedTree=true).printToString());throw e}}
    private suspend fun seed(combination:Boolean=true):String {
        val net=app.metroRepository.snapshot().preferences.planningNetwork
        val stops=if(combination) listOf("las-parcelas","los-dominicos") else listOf("los-heroes","republica")
        return app.metroRepository.savePlan(MetroPlanner.plan(net,stops,false,MetroRestrictions())!!)
    }
    @Test fun stageControlsPauseUndoCombineAndContinueWithoutReinitializingTrip()=runBlocking<Unit> {
        val repo=app.metroRepository;val id=seed();repo.begin(id,repo.snapshot().journeys.single().row.revision,metroTime(compose.activity))
        val sessionId=repo.snapshot().journeys.single().data.active!!.id
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={})}}
        await {exists("Pausar")}
        val pause=compose.onNodeWithText("Pausar").fetchSemanticsNode().boundsInRoot
        val arrived=compose.onNodeWithText("Llegué").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(pause.center.y-arrived.center.y)<10);assertTrue(arrived.left>pause.right)
        compose.onNodeWithText("Pausar").performClick();await {exists("Reanudar")}
        compose.onNodeWithText("Llegué").performClick();await {exists("Deshacer llegada")}
        assertNotNull(repo.snapshot().journeys.single().data.active!!.pausedAt)
        compose.onNodeWithText("Deshacer llegada").performClick();await {runBlocking {repo.snapshot().journeys.single().data.active?.control?.phase==MetroPhase.RIDING} && exists("Reanudar")}
        compose.onNodeWithText("Reanudar").performClick();await {exists("Pausar")}
        compose.onNodeWithText("Llegué").performClick();await {exists("Iniciar combinación")}
        compose.onNodeWithText("Iniciar combinación").performClick();await {exists("Comenzar siguiente línea")}
        compose.onNodeWithText("Combinando").assertExists();compose.onNodeWithText("Pausar").assertDoesNotExist()
        val before=repo.snapshot().journeys.single().data.active!!
        assertEquals(before.offset,MetroTracking.position(before,metroTime(compose.activity).copy(elapsed=before.anchorElapsed+999999)).offset)
        compose.onNodeWithText("Comenzar siguiente línea").performClick();await {exists("Pausar")}
        compose.onNodeWithText("Deshacer llegada").assertDoesNotExist();compose.onNodeWithText("Corregir estación actual").assertExists()
        assertEquals(sessionId,repo.snapshot().journeys.single().data.active!!.id)
        compose.onNodeWithText("Llegué").performClick();await {exists("Finalizado")}
        compose.onNodeWithText("Deshacer llegada").performClick();await {exists("Pausar")}
        assertEquals(sessionId,repo.snapshot().journeys.single().data.active!!.id)
    }
    @Test fun tripsListIsCompactHasNoInterleavedActionsAndOpensExistingDetail()=runBlocking<Unit> {
        val id=seed(false);val before=app.metroRepository.snapshot()
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={})}}
        await {exists("Viajes")};compose.onNodeWithText("Viajes").performClick()
        await {compose.onAllNodesWithTag("metro-trip:$id").fetchSemanticsNodes().isNotEmpty()}
        val card=compose.onNodeWithTag("metro-trip:$id")
        assertTrue(card.fetchSemanticsNode().boundsInRoot.height<150*compose.activity.resources.displayMetrics.density)
        compose.onNodeWithText("Editar plan").assertDoesNotExist();compose.onNodeWithText("Eliminar datos Metro").assertDoesNotExist()
        card.performClick();await {exists("Pendiente")}
        compose.onNodeWithText("Comenzar viaje").assertExists();compose.onNodeWithText("Editar viaje").assertExists()
        assertEquals(before,app.metroRepository.snapshot())
    }
    @Test fun hierarchyIndicatorMarksOnlyActiveBranchAndOpensExistingTracking()=runBlocking<Unit> {
        val project=app.projectRepository.createProject("Proyecto actividad")
        val other=app.projectRepository.createProject("Proyecto ajeno")
        val layer=app.nodeRepository.createNode(project.id,null,"Capa actividad",purpose=NodePurpose.LAYER)
        val nested=app.nodeRepository.createNode(project.id,layer.id,"Subcapa actividad",purpose=NodePurpose.LAYER)
        val task=app.nodeRepository.createNode(project.id,nested.id,"Viaje actividad")
        val unrelated=app.nodeRepository.createNode(project.id,null,"Capa ajena",purpose=NodePurpose.LAYER)
        val repo=app.metroRepository;val net=repo.snapshot().preferences.network
        val id=repo.savePlan(MetroPlanner.plan(net,listOf("los-heroes","republica"),false,MetroRestrictions())!!,task.id)
        repo.begin(id,repo.snapshot().journeys.single().row.revision,metroTime(compose.activity))
        val before=repo.snapshot()
        val map=MetroActivity.hierarchy(app.nodeRepository.getProjectNodes(project.id),before,metroTime(compose.activity))
        assertEquals(setOf("project:${project.id}","node:${layer.id}","node:${nested.id}","node:${task.id}"),map.keys)
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        await {compose.onAllNodesWithTag("metro-activity:project:${project.id}").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("metro-activity:project:${other.id}").assertDoesNotExist()
        compose.onNodeWithTag("metro-activity:project:${project.id}").performClick();await {exists("Pausar")}
        assertEquals(before,repo.snapshot());compose.onNodeWithText("Volver").performClick();await {exists("Proyecto actividad")}
        compose.onNodeWithText("Proyecto actividad",useUnmergedTree=true).performClick();await {exists("Capa actividad")}
        compose.onNodeWithTag("metro-activity:node:${unrelated.id}").assertDoesNotExist()
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasTestTag("metro-activity:node:${layer.id}"))
        compose.onNodeWithTag("metro-activity:node:${layer.id}").assertExists()
        val j=repo.snapshot().journeys.single();val time=metroTime(compose.activity)
        repo.tracking(id,j.row.revision) {MetroTracking.finish(it,time)}
        await {compose.onAllNodesWithTag("metro-activity:node:${layer.id}").fetchSemanticsNodes().isEmpty()}
        assertTrue(MetroActivity.hierarchy(app.nodeRepository.getProjectNodes(project.id),repo.snapshot(),time).isEmpty())
    }
    @Test fun multipleEntryIndicatorOffersSelectorAndReducedMotionIsFixed() {
        android.provider.Settings.Global.putFloat(compose.activity.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,0f)
        val entries=listOf(MetroActivityEntry("a","node-a","A → B","Activo",true,false),MetroActivityEntry("b","node-b","C → D","Combinando",true,false))
        compose.setContent {Arachn0deTheme {CompositionLocalProvider(LocalMetroActivity provides mapOf("project:p" to entries)) {MetroActivityIndicator("project:p")}}}
        compose.onNodeWithTag("metro-activity:project:p").assert(hasStateDescription("Indicador fijo: movimiento reducido")).performClick()
        compose.onNodeWithText("Elegir seguimiento").assertExists()
        compose.onNodeWithText("C → D · Combinando").performClick()
        assertEquals("node-b",MetroNavigation.requests.value!!.nodeId)
    }
}
