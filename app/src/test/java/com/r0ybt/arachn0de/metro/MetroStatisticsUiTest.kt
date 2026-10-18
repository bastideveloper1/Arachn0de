package com.r0ybt.arachn0de.metro

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
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
class MetroStatisticsUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {app.database.close()}
    private fun await(text:String)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private suspend fun training(route:MetroRoute,count:Int=6) {
        repeat(count) {i->repeat(2) {j->
            val duration=if(i==5) 900_000L else 60_000L
            val step=route.steps.single()
            val capture=MetroCapture(route,if(j==0) step.from else step.to,j,step.line,step.direction,step.service,
                MetroCaptureKind.DOORS,MetroTime(1_000_000+i*1_000_000L+j*duration,100_000+j*duration,1),
                "00000000-0000-0000-0000-000000000001",false,0,true,null,0,120_000)
            app.database.metroLearningDao().insert(MetroLearningEventEntity("sample-$i-$j","session-$i",j.toLong(),MetroLearningCodec.encode(capture)))
        }}
    }
    @Test fun fixedPredictionsRemainOriginalWhileCurrentLearningUsesOriginalEventsAfterTripDeletion()=runBlocking<Unit> {
        val repo=app.metroRepository;val p=repo.snapshot().preferences
        val route=MetroPlanner.plan(p.planningNetwork,listOf("los-heroes","republica"),false,p.restrictions)!!
        val id=repo.savePlan(route);repo.begin(id,repo.snapshot().journeys.single().row.revision,metroTime(compose.activity))
        training(route)
        val state=repo.snapshot()
        val captures=repo.learningEvents().map {MetroCaptureStatus(MetroLearningCodec.decode(it.payload,p.network),it.invalidReason)}
        val entries=metroStatisticsEntries(state,captures)
        assertEquals(110_000L,entries.first {it.session==null}.route.totalMillis)
        assertEquals(120_000L,entries.first {it.session!=null}.route.totalMillis)
        val cancelledAt=metroTime(compose.activity)
        repo.tracking(id,state.journeys.single().row.revision) {MetroTracking.cancel(it,cancelledAt)}
        repo.remove(id,repo.snapshot().journeys.single().row.revision)
        val before=repo.snapshot();val events=repo.learningEvents()
        compose.setContent {Arachn0deTheme {MetroStatisticsScreen(onBack={})}}
        await("Mediciones aceptadas: 5")
        compose.onNodeWithTag("metro-learning:Detalles").performClick()
        await("Estimación adaptada: 1 min 50 s")
        compose.onNodeWithText("Ver cálculo").performClick();await("Ver cálculo")
        val list=compose.onNodeWithTag("metro-calculation")
        list.performScrollToNode(hasText("Mediciones aceptadas: 5 · descartadas: 1",substring=true))
        compose.onNode(hasText("Mediciones aceptadas: 5 · descartadas: 1",substring=true)).assertIsDisplayed()
        list.performScrollToNode(hasText("Duración fuera de los límites plausibles: 1"))
        compose.onNodeWithText("Duración fuera de los límites plausibles: 1").assertIsDisplayed()
        assertEquals(before,repo.snapshot());assertEquals(events,repo.learningEvents())
    }
    @Test fun singleArrivalIsShownAsAnEventWithoutInventingAnIntervalOrContribution()=runBlocking<Unit> {
        val repo=app.metroRepository;val preferences=repo.snapshot().preferences
        val route=MetroPlanner.plan(preferences.planningNetwork,listOf("los-heroes","republica"),false,preferences.restrictions)!!
        repo.savePlan(route)
        val step=route.steps.single()
        val capture=MetroCapture(route,step.to,1,step.line,step.direction,step.service,MetroCaptureKind.ARRIVAL,
            MetroTime(1_000_000,100_000,1),"00000000-0000-0000-0000-000000000001",false,0,true,null,0,45_000)
        app.database.metroLearningDao().insert(MetroLearningEventEntity("one","single",0,MetroLearningCodec.encode(capture)))
        val before=repo.snapshot();val events=repo.learningEvents()
        compose.setContent {Arachn0deTheme {MetroStatisticsScreen(onBack={})}}
        await("Mediciones aceptadas: 0")
        compose.onNodeWithText("Mediciones descartadas: 0").assertExists()
        compose.onNodeWithTag("metro-learning:Actividad").performClick()
        compose.onAllNodesWithText("Sin medición").assertCountEquals(2)
        compose.onNodeWithText("Aceptadas: 0 · Descartadas: 0").assertExists()
        compose.onNodeWithText("Servicio: NORMAL").assertExists()
        compose.onNodeWithTag("metro-learning:Tramos").performClick()
        compose.onNodeWithText("Mediciones válidas: 0/5").assertExists()
        assertEquals(before,repo.snapshot());assertEquals(events,repo.learningEvents())
    }

    @Test fun expressSummaryShowsOneEffectiveIntervalAndProvisionalEvidence()=runBlocking<Unit> {
        val repo=app.metroRepository
        val net=repo.snapshot().preferences.planningNetwork
        val line=net.lines.getValue("L5")
        val stops=line.stations.indices.filter {net.stops(line.stations[it],"L5",MetroService.GREEN)}
        val pair=stops.zipWithNext().first {(a,b)->b>a+1 && line.express[a]=="V"}
        val route=MetroPlanner.plan(net,listOf(line.stations[pair.first],line.stations[pair.second]),true,MetroRestrictions())!!
        repo.savePlan(route)
        assertEquals(1,metroLearningContexts(repo.snapshot(),emptyList()).size)
        compose.setContent {Arachn0deTheme {MetroStatisticsScreen(onBack={})}}
        await("Mediciones aceptadas: 0")
        compose.onNodeWithTag("metro-learning:Tramos").performClick()
        compose.onAllNodesWithText("Intervalo entre paradas efectivas").assertCountEquals(1)
        compose.onNodeWithText("Atravesadas sin detención:",substring=true).assertExists()
        compose.onNodeWithText("Base provisional:",substring=true).assertExists()
        compose.onNodeWithText("Estimación sin aprendizaje:",substring=true).assertExists()
        compose.onNodeWithText("Mediciones válidas: 0/5").assertExists()
    }
    @Test fun acceptedMeasurementBelowThresholdIsNotPresentedAsAppliedLearning()=runBlocking<Unit> {
        val repo=app.metroRepository
        val p=repo.snapshot().preferences
        val route=MetroPlanner.plan(p.planningNetwork,listOf("los-heroes","republica"),false,p.restrictions)!!
        training(route,1)
        val before=repo.snapshot();val events=repo.learningEvents()
        compose.setContent {Arachn0deTheme {MetroStatisticsScreen(onBack={})}}
        await("Mediciones aceptadas: 1")
        compose.onNodeWithTag("metro-learning:Actividad").performClick()
        compose.onNodeWithText("Medición registrada",substring=false).assertExists()
        compose.onNodeWithText("Aceptadas: 1 · Descartadas: 0").assertExists()
        compose.onNodeWithText("Tiempo real observado: 1 min 0 s").assertExists()
        compose.onNodeWithText("1 útiles · faltan 4",substring=true).assertExists()
        compose.onNodeWithText("Aprendizaje aplicado",substring=true).assertDoesNotExist()
        assertEquals(before,repo.snapshot());assertEquals(events,repo.learningEvents())
    }
    @Test fun emptyStatisticsDoNotInventMeasurementsAndBackReturnsToTheCaller() {
        var exits=0
        compose.setContent {Arachn0deTheme {MetroStatisticsScreen(onBack={exits++})}}
        await("Todavía no hay rutas ni mediciones guardadas.")
        compose.onNodeWithText("Ver cálculo").assertDoesNotExist()
        compose.onNodeWithContentDescription("Volver").performClick();assertEquals(1,exits)
    }
}
