package com.r0ybt.arachn0de.ui

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.MainActivity
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.rememberProjectCardAlerts
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk = [28])
class ProjectCardAlertsIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: Arachn0deApplication
    private lateinit var previousZone: TimeZone
    private lateinit var project: Project
    private lateinit var task: Node
    private val zone = TimeZone.getTimeZone("UTC")
    private fun instant(day: Int, hour: Int = 12) = Calendar.getInstance(zone).apply {
        clear(); set(2026, Calendar.OCTOBER, day, hour, 0, 0)
    }.timeInMillis
    private val clock = AtomicLong(instant(8))
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            previousZone = TimeZone.getDefault(); TimeZone.setDefault(zone)
            app = ApplicationProvider.getApplicationContext()
            runBlocking {
                project = app.projectRepository.createProject("Alertas del proyecto", "Información interna")
                val layer = app.nodeRepository.createNode(project.id, null, "Capa", purpose = NodePurpose.LAYER)
                val inner = app.nodeRepository.createNode(project.id, layer.id, "Subcapa", purpose = NodePurpose.LAYER)
                task = app.nodeRepository.createNode(project.id, inner.id, "Tarea profunda")
            }
        }
        override fun after() { app.database.close(); TimeZone.setDefault(previousZone) }
    }).around(compose)
    private fun await(text: String) = compose.waitUntil(10_000) {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun mount(owner: LifecycleOwner? = null) {
        compose.runOnUiThread { compose.activity.setContent {
            @Composable fun content() { Arachn0deTheme { AppSafeArea { AppRoot(app.projectRepository, app.nodeRepository) { clock.get() } } } }
            if (owner == null) content() else CompositionLocalProvider(LocalLifecycleOwner provides owner) { content() }
        } }
        await(project.name)
    }
    @Test fun confirmedEditsAndCompletionRefreshDeepTaskAlertsAndDetailsRemainAvailable() {
        mount()
        runBlocking { app.nodeRepository.setPriority(task.id, Priority.HIGH) }; await("1 prioritaria")
        runBlocking { app.nodeRepository.updateNodeWithDates(task.id, task.title, "", null, instant(8, 1)) }; await("1 vence hoy")
        runBlocking { app.nodeRepository.setCompleted(task.id, true) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("1 prioritaria").fetchSemanticsNodes().isEmpty() && compose.onAllNodesWithText("1 vence hoy").fetchSemanticsNodes().isEmpty() }
        runBlocking { app.nodeRepository.setCompleted(task.id, false) }; await("1 prioritaria"); await("1 vence hoy")
        runBlocking { app.nodeRepository.setPriority(task.id, Priority.LOW); app.nodeRepository.updateNodeWithDates(task.id, task.title, "", null, instant(9)) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("1 prioritaria").fetchSemanticsNodes().isEmpty() && compose.onAllNodesWithText("1 vence hoy").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(project.name).performClick(); await("Proyecto raíz")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Información interna"))
        compose.onNodeWithText("Información interna").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tecnologías de ${project.name}").assertDoesNotExist()
    }
    @Test fun clockAndZoneChangesRefreshWithoutAnyRoomWriteAndResumeCatchesUp() {
        runBlocking { app.nodeRepository.updateNodeWithDates(task.id, task.title, "", null, instant(8, 1)) }
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        mount(owner); await("1 vence hoy")
        compose.runOnUiThread { TimeZone.setDefault(TimeZone.getTimeZone("GMT-03:00")); app.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED)) }
        compose.waitUntil(10_000) { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); compose.onAllNodesWithText("1 vence hoy").fetchSemanticsNodes().isEmpty() }
        compose.runOnUiThread { TimeZone.setDefault(zone); app.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED)) }; await("1 vence hoy")
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED; clock.set(instant(9)) }
        compose.onNodeWithText("1 vence hoy").assertExists()
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("1 vence hoy").fetchSemanticsNodes().isEmpty() }
        compose.runOnUiThread { clock.set(instant(8)); app.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED)) }; await("1 vence hoy")
    }
    @Test fun localMidnightRefreshesTheDashboardWithoutBroadcastOrDatabaseEmission() {
        runBlocking {
            app.nodeRepository.updateNodeWithDates(task.id, task.title, "", null, instant(8))
            repeat(2) { app.nodeRepository.createNode(project.id, null, "Mañana $it", dueAt = instant(9)) }
        }
        clock.set(instant(9, 0) - 500)
        mount(); await("1 vence hoy")
        compose.runOnUiThread { clock.set(instant(9, 0)) }
        await("2 vencen hoy")
        compose.onNodeWithText("1 vence hoy").assertDoesNotExist()
    }
    @Test fun ticksWithinSameDayReuseTheAggregateAndANewDayRecomputesIt() {
        val tree = NodeTreeSnapshot(listOf(task.copy(parentId = null, dueAt = instant(8))))
        var now by mutableStateOf(instant(8))
        var result: Map<String, ProjectCardAlerts> = emptyMap()
        compose.runOnUiThread { compose.activity.setContent {
            val state by rememberProjectCardAlerts(tree, now, "UTC")
            result = state
            androidx.compose.material3.Text(state[project.id]?.dueToday?.toString() ?: "0")
        } }
        await("1")
        val first = result
        compose.runOnUiThread { now += 60_000 }
        compose.waitForIdle(); assertSame(first, result)
        compose.runOnUiThread { now = instant(9) }; await("0")
        assertTrue(result.isEmpty())
    }
}
