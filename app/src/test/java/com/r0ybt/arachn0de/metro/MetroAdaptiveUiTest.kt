package com.r0ybt.arachn0de.metro

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
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
class MetroAdaptiveUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {MetroNavigation.requests.value=null;app.database.close()}
    private fun exists(text:String)=compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();block()
    }
    private suspend fun seed():String {
        val repo=app.metroRepository;val p=repo.snapshot().preferences
        val route=MetroPlanner.plan(p.planningNetwork,listOf("los-heroes","republica"),false,p.restrictions)!!
        val id=repo.savePlan(route);repo.begin(id,repo.snapshot().journeys.single().row.revision,metroTime(compose.activity));return id
    }
    @Test fun legacyDisabledPlanStartsDirectlyWithoutAModeSelectorAndTheTrainAdvances()=runBlocking<Unit> {
        val repo=app.metroRepository;val net=repo.snapshot().preferences.planningNetwork
        val id=repo.savePlan(MetroPlanner.plan(net,listOf("los-heroes","republica"),false,MetroRestrictions())!!)
        repo.enabled(id,false)
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),journeyId=id,onBack={})}}
        await {exists("Comenzar viaje")}
        for(label in listOf("Activar modo Viaje","Volver a modo normal","Modo Viaje","Modo normal"))
            compose.onNodeWithText(label).assertDoesNotExist()
        compose.onNodeWithText("Comenzar viaje").assertIsEnabled().performClick()
        await {exists("Llegué")}
        compose.onNodeWithTag("metro-train").assertExists()
        val journey=repo.snapshot().journeys.single();val session=journey.data.active!!
        assertFalse(journey.row.enabled);assertTrue(repo.learningEvents().isEmpty())
        val anchor=MetroTime(session.anchorWall,session.anchorElapsed,session.boot)
        assertTrue(MetroTracking.position(session,anchor.copy(wall=anchor.wall+60_000,elapsed=anchor.elapsed+60_000)).offset>
            MetroTracking.position(session,anchor).offset)
    }
    @Test fun existingHereAndArrivedButtonsCaptureDoorsWhileBeginRemainsOnlyATrackingStart()=runBlocking<Unit> {
        seed();val repo=app.metroRepository;assertTrue(repo.learningEvents().isEmpty())
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={})}}
        await {exists("Llegué")}
        compose.onNode(hasText("Los Héroes") and hasClickAction()).performClick()
        await {exists("Estoy aquí")};compose.onNodeWithTag("metro-confirm-location").performClick()
        await {runBlocking {repo.learningEvents().size==1}}
        val before=repo.snapshot().journeys.single().data.active!!
        val captured=repo.snapshot().lastCaptures.getValue(before.id).capture
        assertEquals(MetroCaptureKind.DOORS,captured.kind)
        assertEquals(0L,MetroTracking.position(before,captured.time).offset)
        compose.onNodeWithText("Llegué").performClick()
        await {exists("Finalizado") && runBlocking {repo.learningEvents().size==2}}
        assertNull(repo.snapshot().journeys.single().data.active)
        assertEquals(MetroCaptureKind.ARRIVAL,repo.snapshot().lastCaptures.getValue(before.id).capture.kind)
        compose.onNodeWithText("Deshacer llegada").performClick()
        await {exists("Llegué") && runBlocking {repo.learningEvents().last().invalidReason=="UNDONE"}}
    }
    @Test fun cancellationIsExplicitAndDoesNotRecordAnArrivalOrOfferPersonSelection()=runBlocking<Unit> {
        seed();val repo=app.metroRepository
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={})}}
        await {exists("Llegué")}
        compose.onNodeWithTag("metro-train").assertExists()
        compose.onNodeWithTag("metro-actions:Seguimiento").performClick()
        compose.onNodeWithText("Cambiar Persona").assertDoesNotExist()
        compose.onNodeWithText("Finalizar recorrido").assertDoesNotExist()
        compose.onNodeWithText("Cancelar seguimiento").performClick()
        await {exists("¿Cancelar seguimiento?")}
        compose.onNode(hasText("sin registrar llegada al destino",substring=true)).assertExists()
        compose.onNodeWithText("Confirmar").performClick();await {exists("Cancelado")}
        assertNull(repo.snapshot().journeys.single().data.active)
        assertTrue(repo.snapshot().journeys.single().data.sessions.last().cancelled)
        assertTrue(repo.learningEvents().isEmpty())
        compose.onNodeWithText("Deshacer llegada").assertDoesNotExist()
        compose.onNodeWithText("Opciones del viaje").performClick()
        compose.onNode(hasText("Seguimiento interrumpido:",substring=true)).assertExists()
        compose.onNode(hasText(" · real ",substring=true)).assertDoesNotExist()
    }
    @Test fun onlyTheTrainIconChangesToneWhileItsBoundsAndBackgroundStayFixed() {
        compose.mainClock.autoAdvance=false
        compose.setContent {Arachn0deTheme {androidx.compose.material3.Surface {MetroTrainMarker()}}}
        compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
        val bounds=compose.onNodeWithTag("metro-train").fetchSemanticsNode().boundsInRoot
        fun pixels():IntArray {
            val decor=compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(decor.width,decor.height,android.graphics.Bitmap.Config.ARGB_8888)
            compose.runOnUiThread {decor.draw(android.graphics.Canvas(bitmap))}
            return IntArray(decor.width*decor.height).also {
                bitmap.getPixels(it,0,decor.width,0,0,decor.width,decor.height)
                bitmap.recycle()
            }
        }
        val before=pixels();compose.mainClock.advanceTimeBy(900);compose.waitForIdle();val after=pixels()
        assertEquals(bounds,compose.onNodeWithTag("metro-train").fetchSemanticsNode().boundsInRoot)
        val changed=before.indices.count {before[it]!=after[it]}
        assertTrue(changed>0);assertTrue(changed<=bounds.width.toInt()*bounds.height.toInt());assertEquals(before.first(),after.first())
    }
    @Test fun pauseHereAndResumeKeepTheirRolesAndACancelledTripCanBeDeletedExplicitly()=runBlocking<Unit> {
        val id=seed();val repo=app.metroRepository
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={})}}
        await {exists("Llegué")}
        compose.onNodeWithText("Pausar").performClick();await {exists("Reanudar")}
        compose.onNode(hasText("Los Héroes") and hasClickAction()).performClick()
        await {exists("Estoy aquí")}
        compose.onNodeWithText("«Estoy aquí» corrige la estación y mantiene la pausa.").assertExists()
        compose.onNodeWithTag("metro-confirm-location").performClick()
        await {runBlocking {repo.learningEvents().size==2}}
        assertNotNull(repo.snapshot().journeys.single().data.active!!.pausedAt)
        assertEquals(MetroCaptureKind.PAUSED_LOCATION,repo.snapshot().lastCaptures.values.single().capture.kind)
        compose.onNodeWithText("Reanudar").assertIsDisplayed();compose.onNodeWithText("Llegué").assertIsDisplayed()
        compose.onNodeWithText("Reanudar").performClick();await {exists("Pausar")}
        val events=repo.learningEvents();assertEquals(3,events.size)
        compose.onNodeWithTag("metro-actions:Seguimiento").performClick()
        compose.onNodeWithText("Cambiar Persona").assertDoesNotExist()
        compose.onNodeWithText("Cancelar seguimiento").performClick()
        await {exists("¿Cancelar seguimiento?")};compose.onNodeWithText("Confirmar").performClick()
        await {exists("Cancelado")}
        compose.onNodeWithTag("metro-actions:$id").performClick();compose.onNodeWithText("Eliminar viaje").performClick()
        await {exists("Confirmar")};compose.onNodeWithText("Confirmar").performClick()
        await {runBlocking {repo.snapshot().journeys.isEmpty()}}
        assertEquals(events,repo.learningEvents())
    }
    @Test fun secondaryHereUsesStationPickerAndRecordsDoors()=runBlocking<Unit> {
        seed();val repo=app.metroRepository
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={})}}
        await {exists("Llegué")}
        compose.onNodeWithTag("metro-actions:Seguimiento").performClick()
        compose.onNodeWithText("Estoy aquí").performClick()
        await {exists("Buscar estación")}
        compose.onNode(hasSetTextAction()).performTextInput("República")
        val station=hasText("República") and hasAnyAncestor(isDialog()) and hasSetTextAction().not()
        await {compose.onAllNodes(station).fetchSemanticsNodes().isNotEmpty()};compose.onNode(station).performClick()
        await {runBlocking {repo.learningEvents().size==1}}
        val state=repo.snapshot();val s=state.journeys.single().data.active!!
        assertEquals("republica",s.confirmed);assertNull(s.ended)
        assertEquals(MetroCaptureKind.DOORS,state.lastCaptures.getValue(s.id).capture.kind)
        assertTrue(state.lastCaptures.getValue(s.id).capture.valid)
        assertTrue(state.observations.isEmpty())
    }
    @Test fun calculationAndExpandableHelpAreSecondaryAndDoNotWriteTripOrLearningState()=runBlocking<Unit> {
        seed();val repo=app.metroRepository;val before=repo.snapshot()
        compose.setContent {Arachn0deTheme {MetroStatisticsScreen(onBack={})}}
        await {exists("Ver cálculo")}
        compose.onAllNodesWithText("Ver cálculo")[0].performClick()
        await {compose.onAllNodesWithTag("metro-calculation").fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasText("Tiempo estándar: 2 min 0 s") and hasAnyAncestor(hasTestTag("metro-calculation"))).assertExists()
        compose.onNodeWithTag("metro-calculation").performScrollToNode(hasText("Cómo ayudar al aprendizaje"))
        compose.onNodeWithText("Cómo ayudar al aprendizaje").performClick()
        compose.onNodeWithTag("metro-calculation").performScrollToNode(hasText("La primera confirmación fiable",substring=true))
        compose.onNode(hasText("La primera confirmación fiable",substring=true)).assertIsDisplayed()
        assertEquals(before,repo.snapshot())
        compose.onNodeWithText("Cerrar").performClick();await {exists("Estadísticas de Metro")}
    }
}
