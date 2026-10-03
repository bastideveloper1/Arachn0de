package com.r0ybt.arachn0de.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CalendarUiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var app: Arachn0deApplication
    private lateinit var originalZone: TimeZone
    private lateinit var project: String
    private lateinit var task: String
    private val instant = 1_792_108_860_000L // 2026-10-16 00:01 UTC
    private val clock = AtomicLong(instant)
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            originalZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            app = ApplicationProvider.getApplicationContext()
            runBlocking {
                project = app.projectRepository.createProject("Personal").id
                val root = app.nodeRepository.createNode(project, null, "Salud").id
                val inner = app.nodeRepository.createNode(project, root, "Tratamiento").id
                task = app.nodeRepository.createNode(project, inner, "Revisión", dueAt = instant).id
                app.personRepository.save("p", "Roy", null)
                app.personRepository.setResponsiblePeople(task, setOf("p"))
            }
        }
        override fun after() { app.database.close(); TimeZone.setDefault(originalZone) }
    }).around(compose)
    private fun mount(restorer: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            Arachn0deTheme { AppSafeArea { AppRoot(app.projectRepository, app.nodeRepository) { clock.get() } } }
        }
        if (restorer == null) compose.setContent(content) else restorer.setContent(content)
    }
    private fun awaitTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun awaitText(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun open() {
        awaitText("Personal")
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Calendario").performClick()
        awaitTag("calendar-month")
    }
    private fun row() {
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("calendar-task:$task"))
        awaitTag("calendar-task:$task")
    }
    @Test fun selectionRestorationAndRealTaskReturnPreserveCalendar() {
        val restorer = StateRestorationTester(compose)
        clock.set(instant - 14 * 24 * 60 * 60 * 1000L)
        mount(restorer); open()
        compose.onNodeWithTag("calendar-day:2026-10-16").performClick(); row()
        compose.onNodeWithTag("calendar-task:$task").assert(hasText("Personal › Salud › Tratamiento"))
        compose.onNodeWithTag("calendar-task:$task").assert(hasContentDescription("Roy"))
        val before = runBlocking { app.nodeRepository.getProjectNodes(project) }
        restorer.emulateSavedInstanceStateRestore(); row()
        assertEquals(before, runBlocking { app.nodeRepository.getProjectNodes(project) })
        compose.onNodeWithTag("calendar-task:$task").performClick(); awaitText("CAPA 3")
        restorer.emulateSavedInstanceStateRestore(); awaitText("CAPA 3")
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Completar"))
        compose.onNodeWithText("Completar").performClick()
        compose.waitUntil(10_000) { runBlocking { app.nodeRepository.getNode(task)!!.isCompleted } }
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasContentDescription("Volver al Calendario"))
        compose.onNodeWithContentDescription("Volver al Calendario").performClick(); row()
        compose.onNodeWithTag("calendar-task:$task").assert(hasText("Completada", substring = true))
        compose.onNodeWithTag("calendar-task:$task").performClick(); awaitText("CAPA 3")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; row()
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("calendar-month"))
        compose.onNodeWithContentDescription("Mes siguiente").performClick()
        restorer.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("calendar-day:2026-11-16").assertIsSelected()
        compose.onNodeWithContentDescription("Mes anterior").performClick()
        compose.onNodeWithTag("calendar-day:2026-10-16").assertIsSelected()
    }
    @Test fun liveCompletionRoutesAssignmentsAndDeletionRefreshReadOnlyProjection() {
        mount(); open(); row()
        runBlocking { app.nodeRepository.setCompleted(task, true) }
        awaitText("Completada")
        runBlocking { app.nodeRepository.setCompleted(task, false) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Completada", substring = true).fetchSemanticsNodes().isEmpty() }
        val destination = runBlocking { app.nodeRepository.createNode(project, null, "Destino").id }
        runBlocking { app.nodeRepository.moveNode(task, destination) }
        awaitText("Personal › Destino")
        runBlocking { app.nodeRepository.updateNode(destination, "Hogar", ""); app.personRepository.save("p", "María", null, isNew = false) }
        awaitText("Personal › Hogar")
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("María").fetchSemanticsNodes().isNotEmpty() }
        runBlocking { app.personRepository.setResponsiblePeople(task, emptySet()) }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("María").fetchSemanticsNodes().isEmpty() }
        runBlocking { app.nodeRepository.deleteNode(task) }
        compose.onNodeWithTag("calendar-list").performScrollToIndex(2)
        awaitText("Sin tareas para este día")
    }
    @Test fun timezoneRefreshWithSameInstantRegroupsWithoutWritingDates() {
        mount(); open(); row()
        val before = runBlocking { app.nodeRepository.getProjectNodes(project) }
        compose.runOnIdle {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT-03:00"))
            app.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
        }
        compose.onNodeWithTag("calendar-list").performScrollToIndex(2)
        awaitText("Sin tareas para este día")
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasText("Hoy"))
        compose.onNodeWithText("Hoy").performClick(); row()
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("calendar-month"))
        compose.onNodeWithTag("calendar-day:2026-10-15").assertIsSelected()
        assertEquals(before, runBlocking { app.nodeRepository.getProjectNodes(project) })
        compose.runOnIdle { clock.set(instant + 24 * 60 * 60 * 1000); app.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED)) }
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasText("Hoy"))
        compose.onNodeWithText("Hoy").performClick()
        compose.onNodeWithTag("calendar-list").performScrollToIndex(2)
        awaitText("Sin tareas para este día")
        assertEquals(before, runBlocking { app.nodeRepository.getProjectNodes(project) })
    }
}
