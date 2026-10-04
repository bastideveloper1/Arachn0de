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

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class CreationUxTest {
    private val compose = createComposeRule()
    private lateinit var app: Arachn0deApplication
    private lateinit var project: String
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object:ExternalResource() {
        override fun before() { app = ApplicationProvider.getApplicationContext(); runBlocking {
            project = app.projectRepository.createProject("Project").id
            app.personRepository.save("person", "Persona A", null)
            app.nodeRepository.tags.create("cuentas")
        } }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun click(label:String) {
        if (compose.onAllNodes(hasText(label) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText(label).performScrollTo()
        compose.onNodeWithText(label).performClick()
    }
    private fun option(label:String) = compose.onNodeWithTag("option:$label").performScrollTo().performClick()
    private fun input(label:String,value:String) = compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
    private fun await(label:String) = compose.waitUntil(10000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
    private fun appContent() { compose.setContent { Arachn0deTheme { AppSafeArea { AppRoot(app.projectRepository,app.nodeRepository) } } } }

    @Test fun normalEntryKeepsFullInputWhenRecurrenceIsChosenLastAndRetryClearsOnlyOnSuccess() {
        appContent(); await("Project"); click("Project"); await("Nuevo elemento"); click("Nuevo elemento")
        // Wait for asynchronous defaults resolution and the mounted editor.
        await("Título")
        input("Título","Pagar Internet"); input("Descripción","Plan hogar")
        compose.onNodeWithTag("obligation-enabled").performScrollTo().performClick(); input("Monto","25000")
        compose.onNodeWithTag("priority-selector").performScrollTo().performClick(); compose.onNodeWithTag("priority-option:HIGH").performClick()
        click("Etiquetas: Ninguna"); click("cuentas"); click("Listo")
        click("Responsables (0)"); click("Persona A"); compose.onNodeWithText("Guardar").performClick()
        click("Cerrar"); click("Nuevo elemento")
        compose.onNodeWithText("Título").performScrollTo().assert(hasText("Pagar Internet"))
        option("Recurrente"); click("Recurrencia: Diaria"); click("Mensual"); compose.onNodeWithText("Comienza:",substring=true).performScrollTo().performClick()
        compose.runOnUiThread {
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.DatePickerDialog
            dialog.datePicker.updateDate(2090,0,31)
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        }
        compose.onNodeWithTag("option:Crear varios").performScrollTo().assertIsNotEnabled()
        app.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER ux_fail BEFORE INSERT ON recurrence_rules BEGIN SELECT RAISE(ABORT,'fail'); END")
        compose.onNodeWithText("Crear").performClick(); await("No se pudo completar la operación")
        compose.onNodeWithText("Entendido").performClick()
        compose.onNodeWithText("Descripción").performScrollTo().assert(hasText("Plan hogar"))
        compose.onNodeWithText("Monto").performScrollTo().assert(hasText("25000"))
        click("Prioridad: Alta"); compose.onNodeWithTag("priority-option:HIGH").performClick()
        click("Etiquetas: cuentas"); click("Listo"); click("Responsables (1)"); compose.onNodeWithText("Guardar").performClick()
        app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER ux_fail")
        compose.onNodeWithText("Crear").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Crear").fetchSemanticsNodes().isEmpty() }
        runBlocking {
            val rule = app.database.recurrenceDao().rules().single()
            assertEquals("Pagar Internet",rule.title); assertEquals("Plan hogar",rule.description)
            assertEquals(25000L,rule.amountMinor); assertEquals("CLP",rule.currencyCode); assertEquals("HIGH",rule.priority)
            assertEquals("MONTHLY",rule.frequency); assertEquals(setOf("person"),app.nodeRepository.recurrence.people(rule.id))
            assertEquals(1,app.nodeRepository.recurrence.tagIds(rule.id).size)
            assertTrue(app.nodeRepository.getProjectNodes(project).isEmpty()); assertTrue(app.database.nodeEventDao().all().isEmpty())
        }
        click("Nuevo elemento"); await("Título"); compose.onNodeWithText("Título").assert(hasText("")); compose.onNodeWithText("Monto").assertDoesNotExist()
    }

    @Test fun disclosureAndPurposeAndRecreationPreserveLatentValuesAndBlockCombinedModes() {
        val restoration = StateRestorationTester(compose); lateinit var draft:EditorDraft
        restoration.setContent { val state = rememberSaveable(stateSaver=EditorDraft.Saver) { mutableStateOf<EditorDraft?>(EditorDraft(null,null,"Task","Details")) }
            draft=checkNotNull(state.value); Arachn0deTheme { NodeDialog(draft,false,{}, {_,_->}) } }
        compose.onNodeWithText("Monto").assertDoesNotExist(); compose.onNodeWithText("Recurrencia: Mensual").assertDoesNotExist()
        compose.onNodeWithTag("obligation-enabled").performScrollTo().performClick(); input("Monto","25000")
        compose.onNodeWithTag("obligation-enabled").performScrollTo().performClick(); compose.onNodeWithText("Monto").assertDoesNotExist()
        compose.onNodeWithTag("obligation-enabled").performScrollTo().performClick(); option("Recurrente"); click("Recurrencia: Diaria"); click("Mensual")
        input("Cada (cantidad de meses)","3"); option("Recurrente"); option("Recurrente")
        click("Nota"); compose.onNodeWithTag("obligation-enabled").assertDoesNotExist(); compose.onNodeWithTag("priority-selector").assertDoesNotExist()
        click("Tarea"); compose.onNodeWithText("Monto").performScrollTo().assert(hasText("25000"))
        compose.runOnIdle { draft.startAt=100; draft.dueAt=200; draft.tagIds=listOf("tag"); draft.responsibleIds=listOf("person"); draft.priority=Priority.HIGH }
        option("Fecha de inicio"); option("Vencimiento")
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals("3",draft.recurrenceInterval); assertEquals("MONTHLY",draft.recurrenceFrequency); assertEquals(100L,draft.startAt); assertEquals(200L,draft.dueAt); assertNull(draft.activeStart); assertNull(draft.activeDue); assertEquals(listOf("tag"),draft.tagIds); assertEquals(Priority.HIGH,draft.priority) }
        option("Fecha de inicio"); option("Vencimiento"); option("Recurrente"); option("Crear varios")
        input("Cantidad","12"); click("Al final"); input("Número inicial","4")
        compose.onNodeWithTag("option:Recurrente").performScrollTo().assertIsNotEnabled()
        option("Crear varios"); option("Crear varios"); restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals("12",draft.batchQuantity); assertEquals("4",draft.batchStartNumber); assertEquals(NumberingMode.SUFFIX,draft.batchNumbering); assertEquals(100L,draft.activeStart); assertEquals(200L,draft.activeDue); assertEquals(listOf("person"),draft.responsibleIds) }
    }

    @Test fun emptyFormClosesQuietlyAndExplicitDiscardClearsSignificantWork() {
        appContent(); await("Project"); click("Project"); await("Nuevo elemento"); click("Nuevo elemento")
        compose.onNodeWithText("Descartar").assertDoesNotExist(); click("Cerrar"); click("Nuevo elemento")
        input("Título","Unsaved"); click("Descartar"); click("Continuar editando")
        compose.onNodeWithText("Título").performScrollTo().assert(hasText("Unsaved"))
        click("Descartar"); compose.onNodeWithText("Descartar borrador").performClick(); click("Nuevo elemento")
        compose.onNodeWithText("Título").assert(hasText("")); assertTrue(runBlocking { app.database.nodeEventDao().all().isEmpty() })
    }

    @Test @Config(qualifiers="w320dp-h480dp") fun dynamicSectionsRemainScrollableAndPrimaryActionAccessibleOnSmallScreen() {
        val draft=EditorDraft(null,null,"Task","Long description").apply { financialEnabled=true; amountText="25000"; toggleRecurrence(true) }
        compose.setContent {
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.6f)) {
                Arachn0deTheme { NodeDialog(draft,false,{}, {_,_->}) }
            }
        }
        compose.onNodeWithText("Crear").assertIsDisplayed()
        compose.onNodeWithText("Descartar").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Título").performScrollTo().assertIsDisplayed()
    }
}
