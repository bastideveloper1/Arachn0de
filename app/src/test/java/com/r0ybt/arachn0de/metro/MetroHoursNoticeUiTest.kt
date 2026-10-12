package com.r0ybt.arachn0de.metro

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28])
class MetroHoursNoticeUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun wall(time:String)=LocalDateTime.parse("2026-10-09T$time").atZone(ZoneId.of("America/Santiago")).toInstant().toEpochMilli()
    @After fun close() {MetroNavigation.requests.value=null;app.database.close()}
    @Test fun noticeIsSingleInformationalBlockAndDisappearsWhenNoLongerRelevant() {
        val start=mutableLongStateOf(wall("22:50"))
        compose.setContent {Arachn0deTheme {Column {MetroHoursNotice(start.longValue,20)}}}
        compose.onAllNodesWithText("Cerca del horario de cierre",substring=true).assertCountEquals(1)
        compose.onAllNodesWithText("El viaje podría finalizar después del cierre",substring=true).assertCountEquals(1)
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        compose.runOnIdle {start.longValue=wall("12:00")}
        compose.onAllNodesWithText("Horario orientativo",substring=true).assertCountEquals(0)
        compose.runOnIdle {start.longValue=wall("23:30")}
        compose.onAllNodesWithText("Fuera del horario habitual",substring=true).assertCountEquals(1)
        compose.onAllNodesWithText("Cerca del horario de cierre",substring=true).assertCountEquals(0)
    }
    @Test fun outsideHoursPlanStillStartsAndTrackingRemainsAvailable()=runBlocking<Unit> {
        val repo=app.metroRepository
        val net=repo.snapshot().preferences.planningNetwork
        val route=MetroPlanner.plan(net,listOf("los-heroes","republica"),false,MetroRestrictions())!!
        assertEquals(listOf(MetroOperatingHours.Warning.OUTSIDE),MetroOperatingHours.warnings(wall("23:30"),route.minutes))
        val id=repo.savePlan(route)
        repo.begin(id,repo.snapshot().journeys.single().row.revision,MetroTime(wall("23:30"),100_000,1))
        assertEquals(route,repo.snapshot().journeys.single().data.active!!.route)
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={})}}
        compose.waitUntil(15000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("Pausar").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Pausar").assertIsEnabled()
        compose.onNodeWithText("Llegué").assertIsEnabled()
        compose.onAllNodesWithText("Horario orientativo",substring=true).assertCountEquals(0)
    }
}
