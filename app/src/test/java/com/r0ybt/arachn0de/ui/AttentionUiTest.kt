package com.r0ybt.arachn0de.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.TaskTemporal
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AttentionUiTest {
    private val compose = createComposeRule()
    private lateinit var app: Arachn0deApplication
    private lateinit var project: String
    private lateinit var root: String
    private lateinit var inner: String
    private lateinit var urgent: String
    private lateinit var other: String
    private val fixed = 1_800_000_000_000L
    private val clock = AtomicLong(fixed)
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            app = ApplicationProvider.getApplicationContext()
            runBlocking {
                project = app.projectRepository.createProject("Personal").id
                root = app.nodeRepository.createNode(project, null, "Salud").id
                inner = app.nodeRepository.createNode(project, root, "Tratamiento").id
                urgent = app.nodeRepository.createNode(project, inner, "Mismo título", dueAt = fixed - 1).id
                val work = app.projectRepository.createProject("Trabajo").id
                other = app.nodeRepository.createNode(work, null, "Mismo título", dueAt = fixed + 10_000).id
                app.personRepository.save("p", "Roy", null)
                app.personRepository.setResponsiblePeople(urgent, setOf("p"))
            }
        }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun mount(restorer: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            Arachn0deTheme { AppSafeArea { AppRoot(app.projectRepository, app.nodeRepository) { clock.get() } } }
        }
        if (restorer == null) compose.setContent(content) else restorer.setContent(content)
    }
    private fun awaitText(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun awaitTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun openAttention() {
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Atención").performClick()
        awaitTag("attention-task:$urgent")
    }
    private fun up() {
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasContentDescription("Volver a la capa anterior"))
        compose.onNodeWithContentDescription("Volver a la capa anterior").performClick()
    }

    @Test fun indicatorsProjectionNavigationAndSavedRouteUseRealNodeAndAncestors() {
        val restoration = StateRestorationTester(compose)
        mount(restoration)
        awaitText("Contiene 1 vencida"); awaitText("Contiene 1 próxima")
        val before = runBlocking { app.nodeRepository.getProjectNodes(project) }
        openAttention()
        compose.onNodeWithTag("attention-task:$urgent").assert(hasText("Personal › Salud › Tratamiento"))
        compose.onNodeWithTag("attention-task:$urgent").assert(hasContentDescription("Roy"))
        compose.onNodeWithTag("attention-task:$other").assert(hasText("Trabajo"))
        assertEquals(before, runBlocking { app.nodeRepository.getProjectNodes(project) })
        compose.onNodeWithTag("attention-task:$urgent").performClick()
        awaitText("CAPA 3")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Responsables"))
        compose.onNodeWithContentDescription("Roy").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        awaitText("CAPA 3")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Completar"))
        compose.onNodeWithText("Completar").performClick()
        compose.waitUntil(10_000) { runBlocking { app.nodeRepository.getNode(urgent)!!.isCompleted } }
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Reabrir"))
        compose.onNodeWithText("Reabrir").performClick()
        compose.waitUntil(10_000) { runBlocking { !app.nodeRepository.getNode(urgent)!!.isCompleted } }
        up(); awaitText("CAPA 2")
        up(); awaitText("CAPA 1")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Contiene 1 vencida"))
        compose.onAllNodesWithText("Contiene 1 vencida")[0].assertExists()
        up()
        compose.onNodeWithContentDescription("Capas de cebolla").performClick()
        awaitTag("layer-navigator")
        compose.onNodeWithTag("layer-navigator").performScrollToNode(hasTestTag("navigator-home"))
        compose.onNodeWithTag("navigator-home").performClick()
        awaitTag("attention-task:$urgent")
        assertEquals(urgent, runBlocking { app.nodeRepository.getNode(urgent)!!.id })
    }

    @Test fun liveViewUpdatesCompletionDatesRoutePersonAndTimeWithoutChangingManualOrder() {
        mount(); awaitText("Personal"); openAttention()
        runBlocking { app.nodeRepository.setCompleted(urgent, true) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("attention-task:$urgent").fetchSemanticsNodes().isEmpty() }
        runBlocking { app.nodeRepository.setCompleted(urgent, false) }
        awaitTag("attention-task:$urgent")
        val target = runBlocking { app.nodeRepository.createNode(project, null, "Destino").id }
        runBlocking { app.nodeRepository.moveNode(urgent, target) }
        awaitText("Personal › Destino")
        runBlocking { app.nodeRepository.updateNode(target, "Hogar", ""); app.personRepository.save("p", "María", null, isNew = false) }
        awaitText("Personal › Hogar")
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("María").fetchSemanticsNodes().isNotEmpty() }
        runBlocking { app.personRepository.setResponsiblePeople(urgent, emptySet()) }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("María").fetchSemanticsNodes().isEmpty() }
        runBlocking { app.nodeRepository.updateNodeWithDates(urgent, "Mismo título", "", null, fixed + TaskTemporal.UPCOMING_WINDOW_MILLIS + 1) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("attention-task:$urgent").fetchSemanticsNodes().isEmpty() }
        val before = runBlocking { app.nodeRepository.getProjectNodes(project) }
        compose.runOnIdle {
            clock.set(fixed + TaskTemporal.UPCOMING_WINDOW_MILLIS + 2)
            app.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
        }
        awaitTag("attention-task:$urgent")
        compose.onNodeWithTag("attention-task:$urgent").assert(hasText("Vencida", substring = true))
        assertEquals(before, runBlocking { app.nodeRepository.getProjectNodes(project) })
        runBlocking { app.nodeRepository.deleteNode(urgent) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("attention-task:$urgent").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun visitingAttentionFromLayerAndReturningKeepsLocationAndUsesDeepTaskClock() {
        // Start normal, with a deadline deeper than the displayed level.
        runBlocking { app.nodeRepository.updateNodeWithDates(urgent, "Mismo título", "", null, fixed + TaskTemporal.UPCOMING_WINDOW_MILLIS + 1) }
        mount(); awaitText("Personal")
        compose.onNodeWithText("Personal").performClick()
        awaitText("Salud")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Salud"))
        compose.onNodeWithText("Salud").performClick()
        awaitText("CAPA 1")
        compose.onNodeWithText("Contiene 1 vencida").assertDoesNotExist()
        compose.runOnIdle { clock.set(fixed + TaskTemporal.UPCOMING_WINDOW_MILLIS + 2); app.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED)) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Contiene 1 vencida").fetchSemanticsNodes().isNotEmpty() }
        openAttention()
        compose.onNodeWithText("Volver").performClick()
        awaitText("CAPA 1")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Tratamiento"))
        compose.onNodeWithText("Tratamiento").assertExists()
    }
}
