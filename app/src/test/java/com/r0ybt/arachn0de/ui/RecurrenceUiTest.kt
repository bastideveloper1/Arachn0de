package com.r0ybt.arachn0de.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RecurrenceUiTest {
    private val compose = createComposeRule()
    private lateinit var app: Arachn0deApplication
    private lateinit var project: String
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() { app = ApplicationProvider.getApplicationContext(); project = runBlocking { app.projectRepository.createProject("Project").id } }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
    @Test fun newActionCreatesPersistentFutureRuleWithoutFutureNodeAndRestoresDraft() {
        val restoration = StateRestorationTester(compose)
        lateinit var editor: EditorDraft
        var saved = false
        restoration.setContent {
            var draft by rememberSaveable(stateSaver = EditorDraft.Saver) { mutableStateOf<EditorDraft?>(EditorDraft(null, null, "Plan", "")) }
            val scope = rememberCoroutineScope(); val actions = remember(scope) { NodeActions(app.nodeRepository, scope) }
            draft?.let { current ->
                editor = current
                Arachn0deTheme { NodeDialog(current, actions.operation.busy, {}, { _, _ -> actions.createRecurrence(project, current) { saved = true; draft = null } }) }
            }
        }
        compose.onNodeWithTag("option:Recurrente").performScrollTo().performClick(); click("Recurrencia: Diaria"); compose.onNodeWithText("Mensual").performClick()
        compose.onNodeWithText("Comienza:",substring=true).performScrollTo().performClick()
        compose.runOnUiThread {
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.DatePickerDialog
            dialog.datePicker.updateDate(2090,0,31)
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        }
        compose.onNodeWithText("Termina: Sin fecha final").performScrollTo().assertExists()
        compose.onNodeWithText("Cada (cantidad de meses)").performScrollTo().performTextReplacement("3")
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals("MONTHLY", editor.recurrenceFrequency); assertEquals("3", editor.recurrenceInterval); assertEquals("2090-01-31", editor.recurrenceStart) }
        compose.onNode(hasText("Guardar") or hasText("Crear")).performClick()
        compose.waitUntil(10_000) { saved }
        val rule = runBlocking { app.database.recurrenceDao().rules().single() }
        assertEquals("MONTHLY", rule.frequency); assertEquals(3, rule.interval); assertNull(rule.endDay)
        assertTrue(runBlocking { app.nodeRepository.getProjectNodes(project).isEmpty() })
    }
    @Test fun visualEndDatePreservesCivilDateAndCanReturnToNoEnd() {
        val draft=EditorDraft(null,null,"Plan","").apply { recurrenceFrequency="MONTHLY";recurrenceStart="2026-10-15";recurrenceInterval="2" }
        compose.setContent { Arachn0deTheme { RecurrenceFields(draft,true) } }
        compose.onNodeWithText("Cada 2 meses, el día 15").assertExists()
        compose.onNodeWithText("Termina: Sin fecha final").performClick()
        compose.runOnUiThread {
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.DatePickerDialog
            dialog.datePicker.updateDate(2026,11,15)
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        }
        compose.runOnIdle {
            assertEquals("2026-12-15",draft.recurrenceEnd)
            val rule=draft.recurrenceRule(project)!!
            assertEquals(RecurrenceSchedule.parse("2026-10-15"),rule.startDay)
            assertEquals(RecurrenceSchedule.parse("2026-12-15"),rule.endDay)
        }
        compose.onNodeWithText("Sin fecha final").performClick()
        compose.runOnIdle { assertNull(draft.recurrenceRule(project)!!.endDay) }
    }
    @Test fun notesHaveNoRecurrenceAndInvalidIntervalCannotSave() {
        compose.setContent { Arachn0deTheme { NodeDialog(remember { EditorDraft(null, null, "Tarea", "") }, false, {}, { _, _ -> }) } }
        compose.onNodeWithTag("option:Recurrente").performScrollTo().performClick(); click("Recurrencia: Diaria"); compose.onNodeWithText("Diaria").performClick()
        compose.onNodeWithText("Cada (cantidad de días)").performScrollTo().performTextReplacement("0")
        compose.onNode(hasText("Guardar") or hasText("Crear")).assertIsNotEnabled()
        click("Nota"); compose.onNodeWithText("Recurrencia: Diaria").assertDoesNotExist()
        compose.onNode(hasText("Guardar") or hasText("Crear")).assertIsEnabled()
    }
    @Test fun managerPausesResumesAndFinishesWithExplicitConfirmationAndKeepsNodes() {
        val repo = app.nodeRepository.recurrence
        runBlocking {
            val draft = EditorDraft(null, null, "Plan", "").apply { recurrenceFrequency = "MONTHLY"; recurrenceStart = "2026-01-10" }
            repo.create(draft.recurrenceRule(project)!!); repo.materializeDue()
        }
        val initial = runBlocking { app.nodeRepository.getProjectNodes(project) }
        compose.setContent { Arachn0deTheme { RecurrenceManager(repo, project, emptyList(), emptyList(), true) } }
        compose.onNodeWithText("Recurrencias").performClick(); compose.onNodeWithText("Plan · Activa").performClick()
        click("Pausar recurrencia")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Reanudar desde hoy").fetchSemanticsNodes().isNotEmpty() }
        click("Reanudar desde hoy")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Pausar recurrencia").fetchSemanticsNodes().isNotEmpty() }
        click("Finalizar recurrencia"); compose.onNodeWithText("¿Finalizar recurrencia?").assertExists()
        compose.onNodeWithText("Cancelar").performClick(); assertEquals("ACTIVE", runBlocking { app.database.recurrenceDao().rules().single().status })
        click("Finalizar recurrencia"); compose.onNodeWithText("Finalizar").performClick()
        compose.waitUntil(10_000) { runBlocking { app.database.recurrenceDao().rules().single().status == "FINISHED" } }
        assertEquals(initial, runBlocking { app.nodeRepository.getProjectNodes(project) })
    }
    @Test fun templateDraftSurvivesClosingAndCanBeDiscardedOrSavedWithoutEditingOccurrences() {
        val repo=app.nodeRepository.recurrence
        val draft=EditorDraft(null,null,"Plan","").apply { recurrenceFrequency="MONTHLY";recurrenceStart="2090-01-31" }
        runBlocking { repo.create(draft.recurrenceRule(project)!!) }
        compose.setContent { Arachn0deTheme { RecurrenceManager(repo,project,emptyList(),emptyList(),true) } }
        compose.onNodeWithText("Recurrencias").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Plan · Activa").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Plan · Activa").performClick();click("Editar futuras ocurrencias")
        compose.onNodeWithText("Título futuro").performScrollTo().performTextReplacement("Pending")
        compose.onNodeWithText("Cerrar").performClick();compose.onNodeWithText("Plan · Activa").performClick()
        compose.onNodeWithText("Título futuro").performScrollTo().assert(hasText("Pending"))
        click("Descartar cambios de plantilla");compose.onNodeWithText("Descartar cambios").performClick()
        compose.onNodeWithText("Plan · Activa").performClick();click("Editar futuras ocurrencias")
        compose.onNodeWithText("Título futuro").performScrollTo().assert(hasText("Plan"))
        compose.onNodeWithText("Título futuro").performTextReplacement("Saved")
        click("Guardar cambios futuros")
        compose.waitUntil(10000) { compose.onAllNodesWithText("Saved · Activa").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Saved · Activa").performClick();click("Editar futuras ocurrencias")
        compose.onNodeWithText("Título futuro").performScrollTo().assert(hasText("Saved"))
        assertTrue(runBlocking { app.nodeRepository.getProjectNodes(project).isEmpty() })
        assertTrue(runBlocking { app.database.nodeEventDao().all().isEmpty() })
    }

}
