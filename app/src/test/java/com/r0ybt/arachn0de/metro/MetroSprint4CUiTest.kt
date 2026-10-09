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
class MetroSprint4CUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {MetroNavigation.requests.value=null;app.database.close()}
    private fun exists(text:String)=compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();block()
    }
    private fun click(text:String) {compose.onNodeWithText(text).performClick();compose.mainClock.advanceTimeBy(100)}
    private fun screen(node:String?=null) {compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),nodeId=node,onBack={})}};await {exists("Líneas")}}
    @Test fun returnFromFinishedTaskCreatesStandalonePlanAndPreservesTheOutboundLink()=runBlocking<Unit> {
        val p=app.projectRepository.createProject("Proyecto ida")
        val node=app.nodeRepository.createNode(p.id,null,"Ida")
        val repo=app.metroRepository;val prefs=repo.snapshot().preferences
        val route=MetroPlanner.plan(prefs.planningNetwork,listOf("las-parcelas","santa-ana","republica"),false,prefs.restrictions)!!
        val id=repo.savePlan(route,node.id);var j=repo.snapshot().journeys.single()
        repo.begin(id,j.row.revision,metroTime(compose.activity));j=repo.snapshot().journeys.single()
        val arrival=metroTime(compose.activity)
        repo.tracking(id,j.row.revision) {MetroTracking.finish(it,arrival)}
        val before=repo.snapshot();screen(node.id);await {exists("Planificar regreso")};click("Planificar regreso")
        compose.onNodeWithText("Origen: República").assertExists();compose.onNodeWithText("Destino: Las Parcelas").assertExists()
        compose.onAllNodesWithText("Parada 1: Santa Ana").assertCountEquals(0)
        assertEquals(before,repo.snapshot())
        compose.onNodeWithText("Guardar viaje").performScrollTo();click("Guardar viaje")
        await {runBlocking {repo.snapshot().journeys.size==2}}
        val after=repo.snapshot();assertEquals(before.journeys.single(),after.journeys.first {it.row.id==id})
        val other=after.journeys.first {it.row.id!=id};assertNull(other.row.nodeId);assertTrue(other.data.sessions.isEmpty())
        assertEquals(listOf("republica","las-parcelas"),other.data.plan.stops)
    }
    @Test fun referenceScheduleIsAnExplicitScenarioWithDateAndUncertaintyNotAnOperationalPanel() {
        screen();click("Planificar")
        compose.onNodeWithText("Simular horario de referencia").performScrollTo();click("Simular horario de referencia")
        compose.onNode(hasText("Fecha y hora en Santiago:",substring=true)).assertExists()
        compose.onNode(hasText("Referencia histórica, no horario vigente confirmado.",substring=true)).assertExists()
        compose.onNodeWithText("Horario y tipo de servicio").assertDoesNotExist()
        click("Normal");compose.onNode(hasText("Fecha y hora en Santiago:",substring=true)).assertDoesNotExist()
    }
}
