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
class MetroStationMenuUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {MetroNavigation.requests.value=null;app.database.close()}
    private fun exists(text:String)=compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();block()
    }
    private fun mount() {
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={})}}
        await {exists("Líneas")}
    }
    private fun action(label:String)=compose.onNodeWithTag("context-action:$label")
    private fun openStation(name:String) {
        compose.onNode(hasText(name,substring=true) and hasClickAction()).performClick()
        await {compose.onAllNodesWithTag("context-action:Cancelar").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun planningSheetKeepsTwoColumnsFavoriteHomeAndOriginWithoutStartingATrip()=runBlocking<Unit> {
        mount();compose.onNodeWithText("Líneas").performClick();await {exists("San Pablo")};openStation("San Pablo")
        val origin=action("Usar como origen").fetchSemanticsNode().boundsInRoot
        val destination=action("Usar como destino").fetchSemanticsNode().boundsInRoot
        assertEquals(origin.top,destination.top,1f);assertTrue(origin.right<destination.left)
        compose.onNodeWithText("Estoy aquí").assertDoesNotExist();action("Cerrar para pasajeros").assertDoesNotExist()
        action("Marcar favorito").performClick();await {runBlocking {"san-pablo" in app.metroRepository.snapshot().preferences.favorites}}
        openStation("San Pablo");action("Quitar favorito").assertExists();action("Asignar Casa").performClick()
        await {runBlocking {app.metroRepository.snapshot().preferences.home=="san-pablo"}}
        openStation("San Pablo");action("Usar como origen").performClick();await {exists("Origen: San Pablo")}
        assertTrue(app.metroRepository.snapshot().journeys.isEmpty());assertTrue(app.metroRepository.learningEvents().isEmpty())
    }
    @Test fun managementKeepsItsExistingConfirmationAndDoesNotLeakActionsIntoNormalStationMenus()=runBlocking<Unit> {
        mount();compose.onNodeWithContentDescription("Opciones de Metro").performClick();compose.onNodeWithText("Preferencias").performClick()
        compose.onNodeWithText("Restricciones de viaje").performClick();await {exists("Agregar desde el mapa")}
        compose.onNodeWithText("Agregar desde el mapa").performClick();await {exists("San Pablo")};openStation("San Pablo")
        action("Preferir evitar").assertExists();action("Cerrar para pasajeros").performClick();await {exists("Confirmar")}
        assertFalse("san-pablo" in app.metroRepository.snapshot().preferences.restrictions.closed)
        compose.onNodeWithText("Confirmar").performClick();await {runBlocking {"san-pablo" in app.metroRepository.snapshot().preferences.restrictions.closed}}
        openStation("San Pablo");action("Reabrir para pasajeros").assertExists();action("Cancelar").performClick()
        compose.onNodeWithText("Salir de gestión").performClick();openStation("San Pablo")
        action("Reabrir para pasajeros").assertDoesNotExist();action("Preferir evitar").assertDoesNotExist()
    }
    @Test @Config(qualifiers="w320dp-h600dp") fun largeTextKeepsHereReachableWithoutSeparateCorrections()=runBlocking<Unit> {
        val repo=app.metroRepository;val p=repo.snapshot().preferences
        val id=repo.savePlan(MetroPlanner.plan(p.planningNetwork,listOf("los-heroes","republica"),false,p.restrictions)!!)
        repo.begin(id,repo.snapshot().journeys.single().row.revision,metroTime(compose.activity))
        compose.setContent {Arachn0deTheme {androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f,1.6f)
        ) {MetroScreen(emptyList(),emptyList(),onBack={})}}}
        await {exists("Llegué")};openStation("Los Héroes")
        compose.onNodeWithTag("metro-confirm-location").assertIsDisplayed();action("Cancelar").assertIsDisplayed()
        action("Corrección avanzada").assertDoesNotExist()
        action("Corregir posición").assertDoesNotExist()
        compose.onNodeWithTag("metro-confirm-location").performClick()
        await {runBlocking {repo.learningEvents().size==1}}
        assertEquals(MetroCaptureKind.DOORS,repo.snapshot().lastCaptures.values.single().capture.kind)
        assertTrue(repo.snapshot().observations.isEmpty());assertNotNull(repo.snapshot().journeys.single().data.active)
        compose.onNodeWithText("Llegué").assertIsDisplayed()
    }
}
