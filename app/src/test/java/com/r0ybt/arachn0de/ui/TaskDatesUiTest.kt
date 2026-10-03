package com.r0ybt.arachn0de.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.state.rememberTaskScreenNow
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TaskDatesUiTest {
    @get:Rule val compose = createComposeRule()
    private val fixed = 1_800_000_000_000L

    @Test fun pickersApplyLocalDateAndTimeAndDraftRestoresWithoutWriting() {
        val restoration = StateRestorationTester(compose)
        lateinit var draft: EditorDraft
        var saved = 0
        restoration.setContent {
            val state = rememberSaveable(stateSaver = EditorDraft.Saver) { mutableStateOf<EditorDraft?>(EditorDraft("id", null, "Task", "", startAt = fixed)) }
            draft = checkNotNull(state.value)
            Arachn0deTheme { NodeDialog(draft, false, {}, { _, _ -> saved++ }) }
        }
        compose.onNodeWithText("Inicio: ${formatTaskDate(fixed)}").performScrollTo().performClick()
        compose.onNodeWithText("Elegir hora").performClick()
        val inputs = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("task-time-input")))
        inputs[0].performTextReplacement("09")
        inputs[1].performTextReplacement("30")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Aplicar").performClick()
        val day = Calendar.getInstance().apply { timeInMillis = fixed }
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH)) }.timeInMillis
        compose.runOnIdle { assertEquals(localTaskInstant(utc, 9, 30), draft.startAt); assertEquals(0, saved) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(localTaskInstant(utc, 9, 30), draft.startAt); assertEquals(0, saved) }
        compose.onNodeWithText("Quitar Inicio").performScrollTo().performClick()
        compose.runOnIdle { assertNull(draft.startAt) }
        compose.onNodeWithText("Guardar").performClick()
        compose.runOnIdle { assertEquals(1, saved) }
    }

    @Test fun invalidCombinationKeepsInputAndCanBeCorrectedByRemovingDate() {
        val draft = EditorDraft("id", null, "Task", "Details", startAt = fixed, dueAt = fixed - 1)
        var saved = false
        compose.setContent { Arachn0deTheme { NodeDialog(draft, false, {}, { _, _ -> saved = true }) } }
        compose.onNodeWithText("Guardar").assertIsNotEnabled()
        compose.onNodeWithText("El vencimiento no puede ser anterior al inicio. Corrige las fechas para guardar.").assertExists()
        compose.runOnIdle { assertEquals(fixed, draft.startAt); assertEquals(fixed - 1, draft.dueAt); assertFalse(saved) }
        compose.onNodeWithText("Quitar Vencimiento").performScrollTo().performClick()
        compose.onNodeWithText("Guardar").assertIsEnabled().performClick()
        compose.runOnIdle { assertNull(draft.dueAt); assertTrue(saved) }
    }

    @Test fun screenClockRefreshesOnResumeAndSystemTimeChangeWithoutRoomEmission() {
        val clock = AtomicLong(fixed)
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        val node = Node("n", "p", null, "Task", "", false, 0, 0, 0, false, startAt = fixed + 100_000, dueAt = fixed + 200_000)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val now = rememberTaskScreenNow(listOf(node)) { clock.get() }
                Arachn0deTheme { Column { TaskDateIndicator(node, now) } }
            }
        }
        compose.onNodeWithText("Programada", substring = true).assertExists()
        compose.runOnIdle { clock.set(fixed + 100_000); owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil { compose.onAllNodesWithText("Próxima", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle {
            clock.set(fixed + 200_001)
            RuntimeEnvironment.getApplication().sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
        }
        compose.waitUntil { compose.onAllNodesWithText("Vencida", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED; clock.set(fixed) }
        compose.onNodeWithText("Vencida", substring = true).assertExists()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil { compose.onAllNodesWithText("Programada", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.DESTROYED }
    }

    @Test fun datesAreFormattedInDeviceZoneAndContainersAndCompletedDoNotLookOverdue() {
        val utcDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, Calendar.OCTOBER, 2) }.timeInMillis
        val zone = TimeZone.getTimeZone("GMT-03:00")
        val instant = localTaskInstant(utcDay, 21, 0, zone)
        assertEquals(utcDay + 24L * 60 * 60 * 1000, instant)
        val gapDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, Calendar.MARCH, 8) }.timeInMillis
        try { localTaskInstant(gapDay, 2, 30, TimeZone.getTimeZone("America/New_York")); fail("Nonexistent local time") } catch (_: IllegalArgumentException) {}
        var node by mutableStateOf(Node("n", "p", null, "Task", "", false, 0, 0, 0, false, dueAt = fixed))
        compose.setContent { Arachn0deTheme { TaskDateIndicator(node, fixed + 1) } }
        compose.onNodeWithText("Vencida", substring = true).assertExists()
        compose.runOnIdle { node = node.copy(isCompleted = true) }
        compose.onNodeWithText("Vencida", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Completada", substring = true).assertExists()
        compose.runOnIdle { node = node.copy(hasChildren = true, isCompleted = false) }
        compose.onNodeWithText(formatTaskDate(fixed), substring = true).assertDoesNotExist()
    }
}
