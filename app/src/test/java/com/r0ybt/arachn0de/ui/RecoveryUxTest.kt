package com.r0ybt.arachn0de.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
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
class RecoveryUxTest {
    private val compose=createComposeRule()
    private lateinit var app:Arachn0deApplication
    private lateinit var project:String
    @get:Rule val rules:RuleChain=RuleChain.outerRule(object:ExternalResource() {
        override fun before() { app=ApplicationProvider.getApplicationContext();runBlocking { project=app.projectRepository.createProject("Project").id } }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun await(label:String) { try { compose.waitUntil(10000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() } } catch(e:Throwable) { throw AssertionError("RECOVERY-$label\n"+compose.onRoot().printToString(),e) } }
    private fun click(label:String) {
        if (label in setOf("First", "Second", "Destination") && compose.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(label))
        }
        await(label); compose.waitUntil(10000) { compose.onAllNodes(hasText(label) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }; val node = compose.onNodeWithText(label)
        if (label in setOf("First", "Second", "Destination")) node.performScrollTo()
        node.performClick() }
    private fun input(label:String,value:String)=compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
    private fun open() { compose.setContent { Arachn0deTheme { AppSafeArea { AppRoot(app.projectRepository,app.nodeRepository) } } };await("Project");click("Project");await("Nuevo elemento") }
    private fun menu(id:String) {
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasTestTag("node-options:$id"))
        compose.onNodeWithTag("node-options:$id").performClick()
    }
    @Test fun undoNormalAndBatchUsesSnackbarAndNeverResurrectsDraft() {
        runBlocking { repeat(3) { app.nodeRepository.createNode(project,null,"Old $it") } }
        open();click("Nuevo elemento");input("Título","New");click("Crear");await("Deshacer");click("Deshacer")
        compose.waitUntil(10000) { runBlocking { app.nodeRepository.getProjectNodes(project).size==3 } }
        click("Nuevo elemento");await("Título");compose.onNodeWithText("Título").assert(hasText(""));input("Título","Batch")
        compose.onNodeWithTag("option:Crear varios").performScrollTo().performClick();input("Cantidad","12");click("Crear")
        await("12 elementos creados");click("Deshacer")
        compose.waitUntil(10000) { runBlocking { app.nodeRepository.getProjectNodes(project).size==3 } }
        runBlocking { assertEquals(3,app.database.nodeEventDao().all().size) }
    }
    @Test fun longPressSelectionTapCounterExitAndOneDeleteConfirmationWithRetry() {
        val rows=runBlocking { listOf(app.nodeRepository.createNode(project,null,"First"),app.nodeRepository.createNode(project,null,"Second",purpose=NodePurpose.NOTE)) }
        open()
        compose.onAllNodesWithContentDescription("Más opciones")[0].performTouchInput { longClick() }
        await("1 seleccionados");compose.onNodeWithText("First").assertIsSelected()
        click("Second");await("2 seleccionados");click("First");await("1 seleccionados");click("Salir");compose.onNodeWithText("1 seleccionados").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Más opciones")[0].performScrollTo().performClick();click("Seleccionar");click("Second");click("Eliminar");await("¿Eliminar 2 elementos?")
        compose.onAllNodesWithText("Eliminar seleccionados").assertCountEquals(1)
        app.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER recovery_fail BEFORE DELETE ON nodes WHEN OLD.id='${rows.last().id}' BEGIN SELECT RAISE(ABORT,'fail'); END")
        click("Eliminar seleccionados");await("No se pudo completar la operación");click("Entendido")
        compose.onNodeWithText("¿Eliminar 2 elementos?").assertExists();assertEquals(2,runBlocking { app.nodeRepository.getProjectNodes(project).size })
        app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER recovery_fail");click("Eliminar seleccionados");await("2 elementos eliminados")
        compose.onNodeWithText("2 seleccionados").assertDoesNotExist()
    }
    @Test fun bulkMoveSelectionClearsAndContextNavigationDoesNotCarrySelection() {
        val rows=runBlocking { listOf(app.nodeRepository.createNode(project,null,"First"),app.nodeRepository.createNode(project,null,"Second"),app.nodeRepository.createNode(project,null,"Destination",purpose=NodePurpose.LAYER)) }
        open();await("First")
        compose.onAllNodesWithContentDescription("Más opciones")[0].performTouchInput { longClick() };await("1 seleccionados");click("Second");click("Mover");await("Mover 2 elementos")
        compose.onNodeWithTag("navigator-node:${rows[2].id}").performScrollTo().performClick();await("2 elementos movidos")
        compose.onNodeWithText("2 seleccionados").assertDoesNotExist()
        runBlocking { assertEquals(rows[2].id,app.nodeRepository.getNode(rows[0].id)!!.parentId);assertEquals(rows[2].id,app.nodeRepository.getNode(rows[1].id)!!.parentId) }
        click("Destination");compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Volver a la capa anterior").fetchSemanticsNodes().isNotEmpty() };compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("First"));await("First")
        compose.onAllNodesWithContentDescription("Más opciones")[0].performTouchInput { longClick() };await("1 seleccionados")
        compose.onNodeWithContentDescription("Volver a la capa anterior").performScrollTo().performClick();compose.waitUntil(10000) { compose.onAllNodesWithText("1 seleccionados").fetchSemanticsNodes().isEmpty() }
    }
    @Test fun groupChoiceOnlyThisAndClosedDraftThenAllMembersKeepsIndividualDatesAndRetries() {
        val rows=runBlocking { app.nodeRepository.createBatch(project,null,"group",NodeBatchGenerator.generate(NodeBatchParameters("Cuota",12,NumberingMode.SUFFIX,1,description="Base",temporalRule=BatchTemporalRule.MONTHLY,firstDueAt=1769853600000,obligation=Obligation(10000,"CLP")),java.util.TimeZone.getTimeZone("UTC"))) }
        open();await("Cuota 1");menu(rows[0].id);click("Editar");input("Monto","12000");click("Guardar");await("Solo este elemento");click("Solo este elemento")
        compose.waitUntil(10000) { runBlocking { app.nodeRepository.getProjectNodes(project).count { it.obligation?.amountMinor==12000L }==1 } }
        menu(rows[0].id);click("Editar");input("Descripción","Shared");click("Cerrar")
        menu(rows[0].id);click("Editar");compose.onNodeWithText("Descripción").assert(hasText("Shared"));click("Guardar");await("Todos los elementos del grupo")
        app.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER recovery_fail BEFORE UPDATE ON nodes WHEN OLD.id='${rows[4].id}' BEGIN SELECT RAISE(ABORT,'fail'); END")
        click("Todos los elementos del grupo");await("No se pudo completar la operación");click("Entendido")
        assertTrue(runBlocking { app.nodeRepository.getProjectNodes(project).all { it.description=="Base" } })
        app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER recovery_fail");click("Todos los elementos del grupo");await("12 elementos actualizados")
        runBlocking { rows.forEach { before -> val after=app.nodeRepository.getNode(before.id)!!;assertEquals("Shared",after.description);assertEquals(before.title,after.title);assertEquals(before.dueAt,after.dueAt);assertEquals(before.position,after.position) } }
    }
}
