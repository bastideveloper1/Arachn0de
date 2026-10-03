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
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NodeBatchUiTest {
    private val compose = createComposeRule()
    private lateinit var app: Arachn0deApplication
    private lateinit var project: String
    private lateinit var parent: String
    private val fixed = 1_800_000_000_000L
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            app = ApplicationProvider.getApplicationContext()
            runBlocking {
                project = app.projectRepository.createProject("Project").id
                parent = app.nodeRepository.createNode(project, null, "Parent").id
                app.personRepository.save("p", "Roy", null)
            }
        }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun await(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun input(label: String, value: String) { compose.onNodeWithText(label).performScrollTo().performTextReplacement(value) }
    private fun click(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }

    @Test fun entryCreatesNotesInCurrentLayerWithCommonPeopleAndRestoredParameters() {
        val restore = StateRestorationTester(compose)
        restore.setContent { Arachn0deTheme { AppSafeArea { AppRoot(app.projectRepository, app.nodeRepository) { fixed } } } }
        await("Project"); compose.onNodeWithText("Project").performClick()
        await("Parent"); compose.onNodeWithText("Parent").performClick()
        await("Nuevo elemento"); compose.onNodeWithText("Nuevo elemento").performClick(); compose.onNodeWithTag("option:Crear varios").performScrollTo().performClick()
        input("Título", "Episode"); input("Cantidad", "3")
        click("Al final"); input("Número inicial", "4"); click("Nota")
        input("Descripción", "Information")
        click("Responsables (0)")
        await("Roy"); compose.onNodeWithText("Roy").performClick(); compose.onNodeWithText("Guardar").performClick()
        restore.emulateSavedInstanceStateRestore()
        await("Crear")
        click("Nota"); compose.onNodeWithText("Diaria").assertDoesNotExist()
        click("Vista previa · 3 elementos")
        compose.onNodeWithText("Episode 4").assertExists(); compose.onNodeWithText("Episode 6").assertExists()
        compose.onNodeWithText("Crear").performClick()
        compose.waitUntil(10_000) { runBlocking { app.nodeRepository.getProjectNodes(project).size == 4 } }
        val notes = runBlocking { app.nodeRepository.observeProjectState(project).first().childrenOf(parent) }
        assertEquals(listOf("Episode 4", "Episode 5", "Episode 6"), notes.map { it.title })
        assertTrue(notes.all { it.purpose == NodePurpose.NOTE && it.description == "Information" && !it.isCompleted && it.dueAt == null })
        notes.forEach { assertEquals("p", runBlocking { app.personRepository.observeAssignments(project).first().getValue(it.id).single().id }) }
        assertEquals(NodeProgressState.NO_WORK, runBlocking { app.nodeRepository.calculateProgress(parent)!!.state })
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Episode 4"))
        compose.onNodeWithText("Episode 4").performClick()
        await("Nota · Convierte en tarea para añadir hijos")
        compose.onNodeWithText("Crear varios").assertDoesNotExist()
    }

    @Test fun temporalPickerAndCompleteDraftRestorePreviewThenFailureAndRetryUseSameSpecs() {
        val restoration = StateRestorationTester(compose)
        lateinit var draft: NodeBatchDraft
        var saved = 0
        var submitted = emptyList<GeneratedNodeSpec>()
        val people = runBlocking { app.personRepository.observePeople().first() }
        app.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER batch_ui_fail BEFORE INSERT ON nodes WHEN NEW.title='Episode 5' BEGIN SELECT RAISE(ABORT,'fail'); END")
        restoration.setContent {
            var state by rememberSaveable(stateSaver = NodeBatchDraft.Saver) { mutableStateOf<NodeBatchDraft?>(NodeBatchDraft(parent).apply {
                baseName = "Episode"; quantity = "3"; numberingMode = NumberingMode.SUFFIX; startNumber = "4"
                description = "Details"; temporalRule = BatchTemporalRule.MONTHLY; dates.dueAt = fixed; responsibleIds = listOf("p")
            }) }
            val scope = rememberCoroutineScope()
            val actions = remember(scope) { NodeActions(app.nodeRepository, scope) }
            state?.let { current ->
                draft = current
                Arachn0deTheme {
                    NodeBatchDialog(current, people, true, actions.operation.busy, {}, { specs, ids ->
                        submitted = specs
                        actions.createBatch(project, current, specs, ids) { saved++; state = null }
                    })
                    OperationErrorDialog(actions.operation)
                }
            }
        }
        click("Vencimiento: ${formatTaskDate(fixed)}")
        compose.onNodeWithText("Elegir hora").performClick()
        val inputs = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag("task-time-input")))
        inputs[0].performTextReplacement("09"); inputs[1].performTextReplacement("30")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Aplicar").performClick()
        compose.runOnIdle {
            assertEquals(parent, draft.parentId); assertEquals("3", draft.quantity); assertEquals("4", draft.startNumber)
            assertEquals(NumberingMode.SUFFIX, draft.numberingMode); assertEquals(NodePurpose.ACTION, draft.purpose)
            assertEquals("Details", draft.description); assertEquals(listOf("p"), draft.responsibleIds)
            assertEquals(BatchTemporalRule.MONTHLY, draft.temporalRule)
        }
        val expected = NodeBatchGenerator.generate(draft.parameters(), TimeZone.getDefault())
        click("Vista previa · 3 elementos")
        compose.onNodeWithText("Episode 4 — ${formatTaskDate(expected[0].dueAt!!)}").assertExists()
        compose.onNodeWithText("Crear lote").performClick()
        await("No se pudo crear el lote. Se conservan tus parámetros; revisa el destino y los responsables y reintenta.")
        assertEquals(1, runBlocking { app.nodeRepository.getProjectNodes(project).size }); assertEquals(0, saved)
        app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER batch_ui_fail")
        // Dismiss the existing operation-error dialog, keeping the generation form and inputs.
        compose.onNodeWithText("Entendido").performClick()
        compose.onNodeWithText("Crear lote").performClick()
        compose.waitUntil(10_000) { saved == 1 }
        val created = runBlocking { app.nodeRepository.observeProjectState(project).first().childrenOf(parent) }
        assertEquals(expected, submitted)
        assertEquals(expected.map { it.title }, created.map { it.title }); assertEquals(expected.map { it.dueAt }, created.map { it.dueAt })
        assertTrue(created.all { it.startAt == null && !it.isCompleted })
    }
}
