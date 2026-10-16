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
    private suspend fun training(route:MetroRoute) {
        repeat(6) {i->repeat(2) {j->
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
        await("Estimación adaptada: 1 min 50 s")
        compose.onNodeWithText("Ver cálculo").performClick();await("Ver cálculo")
        val list=compose.onNodeWithTag("metro-calculation")
        list.performScrollToNode(hasText("Mediciones aceptadas: 5 · descartadas: 1",substring=true))
        compose.onNode(hasText("Mediciones aceptadas: 5 · descartadas: 1",substring=true)).assertIsDisplayed()
        list.performScrollToNode(hasText("Duración fuera de los límites plausibles: 1"))
        compose.onNodeWithText("Duración fuera de los límites plausibles: 1").assertIsDisplayed()
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
