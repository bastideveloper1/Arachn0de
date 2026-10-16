package com.r0ybt.arachn0de.metro

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.MetroLearningEventEntity
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
class MetroSearchFlowUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {MetroNavigation.requests.value=null;app.database.close()}
    private fun exists(text:String)=compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun tag(tag:String)=compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();block()
    }
    private fun openSearch() {await {exists("Planificar viaje")};compose.onNodeWithText("Planificar viaje").performClick();await {tag("metro-planning")}}
    private fun station(field:String,query:String,name:String) {
        compose.onNodeWithTag("metro-field:$field").performScrollTo().performClick()
        await {exists("Buscar estación")};compose.onNode(hasSetTextAction()).performTextInput(query)
        val item=hasText(name) and hasAnyAncestor(isDialog()) and hasSetTextAction().not()
        await {compose.onAllNodes(item).fetchSemanticsNodes().isNotEmpty()};compose.onNode(item).performClick()
        await {tag("metro-planning") && !compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()}
    }
    private fun chooseSimple() {station("Origen","los heroes","Los Héroes");station("Destino","republica","República")}
    private fun search() {compose.onNodeWithText("Buscar viaje").performScrollTo().performClick();await {tag("metro-search-result") && tag("metro-timeline")}}
    private fun androidBack() {compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}}
    @Test fun searchingShowsMapImmediatelyWithoutSavingAndBothBackActionsKeepTheForm()=runBlocking<Unit> {
        val repo=app.metroRepository;val before=repo.snapshot();var exits=0
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={exits++})}}
        openSearch();chooseSimple()
        compose.onNodeWithTag("metro-timeline").assertDoesNotExist();search()
        compose.onNodeWithTag("metro-planning").assertDoesNotExist();compose.onNodeWithTag("metro-timeline").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("metro-timeline").fetchSemanticsNode().boundsInRoot.height>100)
        compose.onNode(hasText("Dirección",substring=true)).assertExists()
        assertEquals(before,repo.snapshot())
        androidBack();await {tag("metro-planning")}
        compose.onNodeWithText("Origen: Los Héroes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Destino: República").assertExists();assertEquals(0,exits)
        search();compose.onNodeWithContentDescription("Volver").performClick();await {tag("metro-planning")}
        assertEquals(0,exits);assertEquals(before,repo.snapshot())
    }
    @Test fun externalTaskResultAndFullMapSurviveSavedStateWithoutReturningToTheWrongPage()=runBlocking<Unit> {
        val project=app.projectRepository.createProject("Contexto")
        val node=app.nodeRepository.createNode(project.id,null,"Viaje pendiente")
        val restoration=StateRestorationTester(compose);var exits=0
        restoration.setContent {Arachn0deTheme {MetroScreen(listOf(node),listOf(project),nodeId=node.id,onBack={exits++})}}
        await {tag("metro-planning")};chooseSimple()
        compose.onNodeWithText("Expresa").performScrollTo().performClick();search()
        val before=app.metroRepository.snapshot()
        restoration.emulateSavedInstanceStateRestore();await {tag("metro-search-result") && tag("metro-timeline")}
        compose.onNodeWithContentDescription("Ampliar mapa").performClick();await {exists("Contraer mapa") || compose.onAllNodesWithContentDescription("Contraer mapa").fetchSemanticsNodes().isNotEmpty()}
        restoration.emulateSavedInstanceStateRestore();await {tag("metro-search-result") && compose.onAllNodesWithContentDescription("Contraer mapa").fetchSemanticsNodes().isNotEmpty()}
        androidBack();await {exists("Editar búsqueda")};assertTrue(tag("metro-search-result"))
        androidBack();await {tag("metro-planning")}
        compose.onNodeWithText("Origen: Los Héroes").performScrollTo().assertIsDisplayed();compose.onNodeWithText("Destino: República").assertExists()
        compose.onNodeWithText("Expresa").performScrollTo().assertIsSelected()
        assertEquals(0,exits);assertEquals(before,app.metroRepository.snapshot())
    }
    @Test fun saveIsExplicitAndLeavesNoDraftResultWhenOpeningTheStoredTrip()=runBlocking<Unit> {
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={})}}
        openSearch();chooseSimple();search();assertTrue(app.metroRepository.snapshot().journeys.isEmpty())
        compose.onNodeWithText("Guardar ruta").assertDoesNotExist()
        compose.onNodeWithContentDescription("Opciones de Metro").performClick()
        compose.onNodeWithText("Guardar ruta").performClick()
        await {runBlocking {app.metroRepository.snapshot().journeys.size==1}}
        val j=app.metroRepository.snapshot().journeys.single();assertTrue(j.data.sessions.isEmpty())
        await {tag("metro-trip:${j.row.id}")};compose.onNodeWithTag("metro-trip:${j.row.id}").performClick()
        await {exists("Pendiente")};compose.onNodeWithTag("metro-search-result").assertDoesNotExist()
        compose.onNodeWithText("Comenzar viaje").assertExists()
    }
    @Test fun resultOnlyOffersStartingAndTheSecondaryMenuNeverCreatesATask()=runBlocking<Unit> {
        val project=app.projectRepository.createProject("Viajes")
        val before=app.nodeRepository.getProjectNodes(project.id)
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),listOf(project),onBack={})}}
        openSearch();chooseSimple();search()
        compose.onNodeWithText("Crear tarea").assertDoesNotExist()
        compose.onNodeWithText("Guardar viaje").assertDoesNotExist()
        compose.onNodeWithText("Ver cálculo").assertDoesNotExist()
        compose.onNodeWithText("Iniciar viaje").assertIsDisplayed()
        compose.onNodeWithContentDescription("Opciones de Metro").performClick()
        compose.onNodeWithText("Crear tarea").assertDoesNotExist()
        compose.onNodeWithText("Guardar ruta").performClick()
        await {runBlocking {app.metroRepository.snapshot().journeys.size==1}}
        val saved=app.metroRepository.snapshot().journeys.single()
        assertNull(saved.row.nodeId);assertTrue(saved.data.sessions.isEmpty())
        assertEquals(before,app.nodeRepository.getProjectNodes(project.id))
    }
    @Test fun startingFromTheResultPreservesTheAdaptedPredictionAndDoesNotCaptureDoors()=runBlocking<Unit> {
        val repo=app.metroRepository;val p=repo.snapshot().preferences
        val route=MetroPlanner.plan(p.planningNetwork,listOf("los-heroes","republica"),false,p.restrictions)!!
        // Original independent observations, no stored trip or active session.
        repeat(30) {i->repeat(2) {j->
            val step=route.steps.single()
            val e=MetroCapture(route,if(j==0) step.from else step.to,j,step.line,step.direction,step.service,MetroCaptureKind.DOORS,
                MetroTime(1_000_000+i*1_000_000L+j*60_000,100_000+j*60_000L,1),"00000000-0000-0000-0000-000000000001",false,0,true,null,0,120_000)
            app.database.metroLearningDao().insert(MetroLearningEventEntity("training-$i-$j","session-$i",j.toLong(),MetroLearningCodec.encode(e)))
        }}
        val before=repo.snapshot();val events=repo.learningEvents()
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={})}}
        openSearch();chooseSimple();search()
        compose.onNode(hasText("≈ 1 min ·",substring=true)).assertExists();assertEquals(before,repo.snapshot())
        compose.onNodeWithText("Iniciar viaje").performScrollTo().performClick();await {exists("Pausar")}
        val s=repo.snapshot().journeys.single().data.active!!;assertEquals(60_000L,s.route.totalMillis);assertEquals(s.route,s.originalRoute)
        assertEquals(events,repo.learningEvents())
    }
    @Test fun searchingAlongsideAPausedTripDoesNotTurnPreviewStationActionsIntoTrackingCorrections()=runBlocking<Unit> {
        val repo=app.metroRepository;val p=repo.snapshot().preferences
        val route=MetroPlanner.plan(p.planningNetwork,listOf("los-heroes","republica"),false,p.restrictions)!!
        val id=repo.savePlan(route);repo.begin(id,repo.snapshot().journeys.single().row.revision,metroTime(compose.activity))
        val pauseTime=metroTime(compose.activity)
        repo.tracking(id,repo.snapshot().journeys.single().row.revision) {MetroTracking.pause(it,pauseTime)}
        val before=repo.snapshot()
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={})}}
        await {exists("Reanudar")};compose.onNodeWithContentDescription("Opciones de Metro").performClick()
        compose.onNodeWithText("Viajes").performClick();compose.onNodeWithText("Viajar").performClick()
        await {tag("metro-planning")};chooseSimple();search()
        compose.onNodeWithText("Iniciar viaje").performScrollTo().assertIsNotEnabled()
        compose.onNode(hasText("Los Héroes") and hasClickAction()).performClick()
        await {exists("Usar como origen")};compose.onNodeWithText("Estoy aquí").assertDoesNotExist()
        compose.onNodeWithTag("context-action:Cancelar").performClick();assertEquals(before,repo.snapshot())
    }
    @Test fun unavailableRouteKeepsTheFormAndShowsAnErrorWithoutAnyWrites()=runBlocking<Unit> {
        val repo=app.metroRepository;repo.settings {it.copy(restrictions=it.restrictions.copy(closed=setOf("los-heroes")))}
        val before=repo.snapshot()
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={})}}
        openSearch();chooseSimple();compose.onNodeWithText("Buscar viaje").performScrollTo().performClick()
        await {exists("No existe ruta con estas restricciones.")}
        compose.onNodeWithTag("metro-planning").assertExists();compose.onNodeWithTag("metro-search-result").assertDoesNotExist()
        assertEquals(before,repo.snapshot())
    }
    @Test @Config(qualifiers="w320dp-h600dp") fun mapRemainsVisibleWithLargeTextAndManyIntermediateStops() {
        compose.setContent {Arachn0deTheme {androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f,1.6f)
        ) {MetroScreen(emptyList(),emptyList(),onBack={})}}}
        openSearch();chooseSimple()
        repeat(3) {
            compose.onNodeWithTag("metro-planning").performScrollToNode(hasText("+ Agregar parada"))
            compose.onNodeWithText("+ Agregar parada").performClick()
            await {exists("Buscar estación")};compose.onNode(hasSetTextAction()).performTextInput("Baquedano")
            val item=hasText("Baquedano") and hasAnyAncestor(isDialog()) and hasSetTextAction().not()
            await {compose.onAllNodes(item).fetchSemanticsNodes().isNotEmpty()};compose.onNode(item).performClick()
            await {tag("metro-planning") && compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()}
        }
        compose.onNodeWithTag("metro-planning").performScrollToNode(hasText("Buscar viaje"))
        search();compose.onNodeWithTag("metro-timeline").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("metro-timeline").fetchSemanticsNode().boundsInRoot.height>80)
        compose.onNodeWithContentDescription("Ampliar mapa").assertIsDisplayed()
    }
}
