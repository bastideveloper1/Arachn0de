package com.r0ybt.arachn0de.metro

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
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
class MetroModeConflictUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {MetroNavigation.requests.value=null;app.database.close()}
    @Test fun noticeIdentifiesOtherTripAndOpensItButHidesForSameTripOrNoActiveTrip()=runBlocking<Unit> {
        val repo=app.metroRepository;val net=repo.snapshot().preferences.planningNetwork
        val id=repo.savePlan(MetroPlanner.plan(net,listOf("los-heroes","republica"),false,MetroRestrictions())!!)
        repo.begin(id,repo.snapshot().journeys.single().row.revision,metroTime(compose.activity))
        val journey=repo.snapshot().journeys.single()
        var active by mutableStateOf<MetroJourney?>(null)
        var viewed by mutableStateOf<String?>(null)
        var opened:String?=null
        compose.setContent {Arachn0deTheme {ActiveJourneyNotice(active,viewed,emptyList(),net)}}
        compose.onNodeWithText("Ver seguimiento activo").assertDoesNotExist()
        compose.runOnIdle {active=journey}
        compose.onNodeWithText("Ya tienes otro viaje en curso: Los Héroes → República. Finalízalo antes de iniciar otro.").assertExists()
        compose.onNodeWithText("Consulta el viaje activo en Viajes.").assertExists()
        compose.runOnIdle {viewed=id}
        compose.onNodeWithText("Ver seguimiento activo").assertDoesNotExist()
        compose.runOnIdle {viewed=null}
        // Repository observers remove the active entry when a session finishes.
        compose.runOnIdle {active=null}
        compose.onNodeWithText("Ver seguimiento activo").assertDoesNotExist()
    }
    private fun await(text:String) = compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    @Test fun anotherTripDetailBlocksStartAndOpensActiveTrackingThenClearsConflict()=runBlocking<Unit> {
        val repo=app.metroRepository;val net=repo.snapshot().preferences.planningNetwork
        val route=MetroPlanner.plan(net,listOf("los-heroes","republica"),false,MetroRestrictions())!!
        val first=repo.savePlan(route);val second=repo.savePlan(route)
        repo.begin(first,repo.snapshot().journeys.single {it.row.id==first}.row.revision,metroTime(compose.activity))
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={})}}
        await("Viajes");compose.onNodeWithText("Viajes").performClick()
        compose.onNodeWithTag("metro-trip:$second").performClick()
        await("Consulta el viaje activo en Viajes.")
        compose.onNodeWithText("Comenzar viaje").assertIsNotEnabled()
        compose.onNodeWithText("Viajes").performClick();compose.onNodeWithTag("metro-trip:$first").performClick()
        await("Pausar")
        compose.onNodeWithText("Ver seguimiento activo").assertDoesNotExist()
        val active=repo.snapshot().journeys.single {it.row.id==first}
        val finishedAt=metroTime(compose.activity)
        repo.tracking(first,active.row.revision) {MetroTracking.finish(it,finishedAt)}
        await("Volver a modo normal")
        compose.onNodeWithText("Volver a modo normal").performClick()
        await("Activar modo Viaje")
        compose.onNodeWithText("Activar modo Viaje").performClick()
        await("Volver a modo normal")
        compose.onNodeWithText("Viajes").performClick()
        compose.onNodeWithTag("metro-trip:$second").performClick()
        await("Comenzar viaje")
        compose.onNodeWithText("Comenzar viaje").assertIsEnabled()
        compose.onNodeWithText("Ver seguimiento activo").assertDoesNotExist()
    }

    @Test fun linkedFinalizedDraftExplainsModeAndProvidesExistingJourneyNavigation()=runBlocking<Unit> {
        val project=com.r0ybt.arachn0de.data.repository.ProjectRepository(app.database.projectDao(),app.database).createProject("Metro")
        val task=com.r0ybt.arachn0de.data.repository.NodeRepository(app.database).createNode(project.id,null,"Viaje")
        val repo=app.metroRepository;val net=repo.snapshot().preferences.planningNetwork
        val id=repo.savePlan(MetroPlanner.plan(net,listOf("los-heroes","republica"),false,MetroRestrictions())!!,task.id)
        repo.begin(id,repo.snapshot().journeys.single().row.revision,metroTime(compose.activity))
        val finishedAt=metroTime(compose.activity)
        repo.tracking(id,repo.snapshot().journeys.single().row.revision) {MetroTracking.finish(it,finishedAt)}
        val before=repo.snapshot().journeys.single()
        val draft=com.r0ybt.arachn0de.ui.state.EditorDraft(task.id,null,task.title,task.description)
        compose.setContent {Arachn0deTheme {androidx.compose.foundation.layout.Column {MetroDraftFields(draft,true) {}}}}
        await("Modo Viaje · seguimiento finalizado")
        compose.onNodeWithText("Abrir recorrido para cambiar modo").performClick()
        compose.runOnIdle {assertEquals(task.id,MetroNavigation.requests.value?.nodeId)}
        assertEquals(before,repo.snapshot().journeys.single())
    }

}
