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
class MetroCompactButtonUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {MetroNavigation.requests.value=null;app.database.close()}
    @Test fun locationButtonIsCompactAndStillConfirmsSelectedStation()=runBlocking<Unit> {
        val repo=app.metroRepository;val net=repo.snapshot().preferences.planningNetwork
        val id=repo.savePlan(MetroPlanner.plan(net,listOf("los-heroes","republica"),false,MetroRestrictions())!!)
        repo.begin(id,repo.snapshot().journeys.single().row.revision,metroTime(compose.activity))
        val session=repo.snapshot().journeys.single().data.active!!
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={})}}
        compose.waitUntil(15000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("Los Héroes").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Los Héroes").performClick()
        compose.onNodeWithText("Estoy aquí").assertExists()
        val button=compose.onNodeWithTag("metro-confirm-location").fetchSemanticsNode().boundsInRoot
        val dialog=compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        val density=compose.activity.resources.displayMetrics.density
        assertTrue(button.height>=48*density-1);assertTrue(button.width<dialog.width-32*density)
        compose.onNodeWithTag("metro-confirm-location").performClick()
        compose.waitUntil(15000) {runBlocking {repo.snapshot().journeys.single().data.active!!.events.any {it.kind=="CONFIRM"}}}
        val updated=repo.snapshot().journeys.single().data.active!!
        assertEquals(session.id,updated.id);assertEquals("los-heroes",updated.confirmed)
    }
}
