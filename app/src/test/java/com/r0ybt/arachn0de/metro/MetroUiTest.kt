package com.r0ybt.arachn0de.metro

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
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
class MetroUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {app.database.close()}
    private fun await(predicate: ()->Boolean)=compose.waitUntil(10000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();predicate()
    }
    private fun exists(text: String)=compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    @Test fun explorerFullScreenKeepsStationContextAndHomeAfterSavedStateRestore() {
        val restoration=StateRestorationTester(compose)
        restoration.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={})}}
        await {exists("Explorar")};compose.onNodeWithText("Explorar").performClick()
        compose.onNodeWithText("San Pablo").performClick();compose.onNodeWithText("Asignar Casa").performClick()
        await {exists("San Pablo · Casa")}
        compose.onNodeWithText("Ampliar").performClick();compose.onNodeWithText("Inicio").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore();await {exists("Reducir")}
        compose.onAllNodesWithText("Reducir")[0].performClick();compose.onNodeWithText("Inicio").assertExists()
        assertEquals("san-pablo",runBlocking {app.metroRepository.snapshot().preferences.home})
    }
    @Test fun directHereCorrectionAndFixedPauseRemainFunctionalFullScreenAndRestore()=runBlocking<Unit> {
        val repo=app.metroRepository;val net=repo.snapshot().preferences.network
        val route=MetroPlanner.plan(net,listOf("los-heroes","republica"),false,MetroRestrictions())!!
        val id=repo.savePlan(route);var j=repo.snapshot().journeys.single();repo.begin(id,j.row.revision,metroTime(compose.activity))
        val restoration=StateRestorationTester(compose)
        restoration.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),onBack={})}}
        await {exists("Pausar")};compose.onNodeWithText("Pausar").performClick()
        await {exists("Reanudar")};compose.onNodeWithText("Ampliar").performClick()
        compose.onNodeWithText("Reanudar").assertIsDisplayed()
        compose.onNodeWithText("Los Héroes").performClick();compose.onNodeWithText("Estoy aquí").performClick()
        await {runBlocking {repo.snapshot().journeys.single().data.active!!.events.any {it.kind=="CONFIRM"}}}
        assertNotNull(repo.snapshot().journeys.single().data.active!!.pausedAt)
        restoration.emulateSavedInstanceStateRestore();await {exists("Reanudar")};compose.onNodeWithText("Reanudar").assertIsDisplayed()
        compose.onNodeWithText("Reanudar").performClick();await {exists("Pausar")}
        compose.onNodeWithText("Llegué").performClick();await {repoState(repo)}
        j=repo.snapshot().journeys.single();assertNotNull(j.data.sessions.single().ended);assertNull(j.data.active)
    }
    @Test fun notificationRequestOpensActiveTrackingFromAnAlreadyVisibleDifferentTab()=runBlocking<Unit> {
        val repo=app.metroRepository;val net=repo.snapshot().preferences.network
        val id=repo.savePlan(MetroPlanner.plan(net,listOf("los-heroes","tobalaba"),false,MetroRestrictions())!!)
        repo.begin(id,repo.snapshot().journeys.single().row.revision,metroTime(compose.activity))
        var token by androidx.compose.runtime.mutableStateOf(0L)
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),requestToken=token,onBack={})}}
        await {exists("Pausar")};compose.onNodeWithText("Explorar").performClick()
        compose.onNodeWithText("Pausar").assertDoesNotExist()
        compose.runOnIdle {token=1L};await {exists("Pausar")};compose.onNodeWithText("Pausar").assertIsDisplayed()
    }
    private fun repoState(repo: MetroRepository)=runBlocking {repo.snapshot().journeys.single().data.active==null}
}
