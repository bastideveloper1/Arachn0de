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
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk = [28])
class ObligationUiTest {
    private val compose = createComposeRule()
    private lateinit var app: Arachn0deApplication
    private lateinit var project: String
    private lateinit var oldLocale: Locale
    private lateinit var oldZone: TimeZone
    private val fixed = 1_792_108_860_000L
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            oldLocale=Locale.getDefault(); oldZone=TimeZone.getDefault()
            Locale.setDefault(Locale.US); TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            app=ApplicationProvider.getApplicationContext()
            project=runBlocking { app.projectRepository.createProject("Project").id }
        }
        override fun after() { app.database.close(); Locale.setDefault(oldLocale); TimeZone.setDefault(oldZone) }
    }).around(compose)
    private fun await(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty() }
    private fun click(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }
    private fun input(label: String,value: String) { compose.onNodeWithText(label).performScrollTo().performTextReplacement(value) }
    private fun toggle() = compose.onNodeWithTag("obligation-enabled").performScrollTo().performClick()
    private fun listTo(text: String) = compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(text))

    @Test fun individualDraftValidatesRestoresCreatesAndConfirmsFinancialRemoval() {
        runBlocking { app.personRepository.save("r", "Roy", null); app.personRepository.save("s", "Scarlett", null) }
        val restoration=StateRestorationTester(compose)
        restoration.setContent { Arachn0deTheme { AppSafeArea { AppRoot(app.projectRepository,app.nodeRepository) { fixed } } } }
        await("Project"); compose.onNodeWithText("Project").performClick(); await("Nuevo elemento")
        compose.onNodeWithText("Nuevo elemento").performClick()
        input("Título","Bill"); toggle(); click("USD"); input("Monto","0")
        compose.onNode(hasText("Guardar") or hasText("Crear")).assertIsNotEnabled()
        click("Responsables (0)"); click("Roy"); click("Scarlett"); compose.onAllNodes(hasText("Guardar") or hasText("Crear")).onLast().performClick()
        input("Monto","10.50"); restoration.emulateSavedInstanceStateRestore(); await("Monto")
        compose.onNodeWithText("Monto").performScrollTo().assert(hasText("10.50"))
        compose.onNode(hasText("Guardar") or hasText("Crear")).performClick()
        compose.waitUntil(10_000) { runBlocking { app.nodeRepository.getProjectNodes(project).size==1 } }
        val node=runBlocking { app.nodeRepository.getProjectNodes(project).single() }
        assertEquals(Obligation(1050,"USD"),node.obligation)
        assertEquals(setOf("r", "s"), runBlocking { app.database.personDao().assignmentIds(node.id).toSet() })
        listTo("Bill"); compose.onNodeWithText("Bill").performClick(); await("CAPA 1")
        compose.onNodeWithText("Nuevo elemento").assertIsNotEnabled(); compose.onNodeWithText("Crear varios").assertDoesNotExist()
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasContentDescription("Opciones del elemento")); compose.onNodeWithContentDescription("Opciones del elemento").performClick(); compose.onNodeWithText("Editar").performClick(); toggle()
        compose.onNode(hasText("Guardar") or hasText("Crear")).performClick(); await("Eliminar datos financieros")
        compose.onNodeWithTag("cancel-remove-obligation").performClick()
        assertEquals(Obligation(1050,"USD"),runBlocking { app.nodeRepository.getNode(node.id)!!.obligation })
        compose.onNode(hasText("Guardar") or hasText("Crear")).performClick()
        compose.onNodeWithText("Eliminar datos y continuar").performClick()
        compose.waitUntil(10_000) { runBlocking { app.nodeRepository.getNode(node.id)!!.obligation==null } }
        assertEquals(node.copy(obligation=null),runBlocking { app.nodeRepository.getNode(node.id)!!.copy(updatedAt=node.updatedAt) })
    }

    @Test fun obligationDisplaysInCalendarAttentionAndNoteConversionRequiresConfirmation() {
        val node=runBlocking { app.nodeRepository.createNode(project,null,"Bill",dueAt=fixed-1,obligation=Obligation(50000,"CLP")) }
        compose.setContent { Arachn0deTheme { AppSafeArea { AppRoot(app.projectRepository,app.nodeRepository) { fixed } } } }
        await("Project"); compose.onNodeWithContentDescription("Abrir menú").performClick(); compose.onNodeWithText("Calendario").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasStateDescription("Hoy · 1 tarea")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("calendar-task:${node.id}"))
        compose.onNodeWithTag("calendar-task:${node.id}").assert(hasText("CLP",substring=true))
        compose.onNodeWithText("Volver").performClick()
        compose.onNodeWithContentDescription("Abrir menú").performClick(); compose.onNodeWithText("Atención").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("attention-task:${node.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("attention-task:${node.id}").assert(hasText("CLP",substring=true))
        compose.onNodeWithTag("attention-task:${node.id}").performClick(); await("CAPA 1")
        compose.onNodeWithContentDescription("Opciones del elemento").performScrollTo().performClick(); compose.onNodeWithText("Convertir en nota").performClick(); await("Eliminar datos financieros")
        assertEquals(NodePurpose.ACTION,runBlocking { app.nodeRepository.getNode(node.id)!!.purpose })
        compose.onNodeWithTag("cancel-remove-obligation").performClick()
        compose.onNodeWithContentDescription("Opciones del elemento").performScrollTo().performClick(); compose.onNodeWithText("Convertir en nota").performClick()
        compose.onNodeWithText("Eliminar datos y continuar").performClick()
        compose.waitUntil(10_000) { runBlocking { app.nodeRepository.getNode(node.id)!!.purpose==NodePurpose.NOTE } }
        assertNull(runBlocking { app.nodeRepository.getNode(node.id)!!.obligation })
        compose.onNodeWithContentDescription("Opciones del elemento").performScrollTo().performClick(); compose.onNodeWithText("Convertir en tarea").performClick()
        compose.waitUntil(10_000) { runBlocking { app.nodeRepository.getNode(node.id)!!.purpose==NodePurpose.ACTION } }
        assertNull(runBlocking { app.nodeRepository.getNode(node.id)!!.obligation })
    }

    @Test fun batchFinancialInputsSurviveRestorationAndCommitWithMonthlyDates() {
        val restoration=StateRestorationTester(compose)
        restoration.setContent {
            val state=rememberSaveable(stateSaver=NodeBatchDraft.Saver) { mutableStateOf<NodeBatchDraft?>(NodeBatchDraft(null).apply {
                baseName="Cuota"; quantity="3"; numberingMode=NumberingMode.SUFFIX
                temporalRule=BatchTemporalRule.MONTHLY; dates.dueAt=fixed
            }) }
            state.value?.let { draft -> Arachn0deTheme {
                NodeBatchDialog(draft,emptyList(),true,false,{}, { specs,ids ->
                    runBlocking { app.nodeRepository.createBatch(project,null,draft.batchId,specs,ids) }; state.value=null
                })
            } }
        }
        toggle(); input("Monto","50000"); restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Monto").performScrollTo().assert(hasText("50000"))
        compose.onNodeWithText("Crear lote").performClick()
        val nodes=runBlocking { app.nodeRepository.getProjectNodes(project) }
        assertEquals(listOf("Cuota 1","Cuota 2","Cuota 3"),nodes.map { it.title })
        assertTrue(nodes.all { it.obligation==Obligation(50000,"CLP") && it.dueAt!=null })
    }
}
