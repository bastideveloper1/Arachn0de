package com.r0ybt.arachn0de.templates

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.MainActivity
import com.r0ybt.arachn0de.data.local.SavedTemplateEntity
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.*
import com.r0ybt.arachn0de.ui.state.EditorDraft
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
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w320dp-h640dp")
class SavedTemplatesUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun seed()=runBlocking {
        app.savedTemplateRepository.save(SavedTemplateEntity("pay","Pago habitual",SavedTemplateCodec.encode(SavedTaskConfiguration("Pagar cuenta","Texto reusable",priority=Priority.HIGH,amount=1500,currency="CLP",due=TemplateDate(TemplateDateKind.TOMORROW,minute=570)))),true)
        app.savedTemplateRepository.save(SavedTemplateEntity("other","Mantenimiento",SavedTemplateCodec.encode(SavedTaskConfiguration("Revisar máquina"))),true)
    }
    private fun await(text:String) {compose.waitUntil(10000) {compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    @Test fun accessAppearsAboveDescriptionAndSelectionOnlyFillsDraft() {
        seed();val draft=EditorDraft(null,null,"","")
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {NodeDialog(draft,false,{}, {_,_->})}}}
        compose.onNodeWithText("Guardados").assertIsDisplayed()
        val access=compose.onNodeWithText("Guardados").fetchSemanticsNode().boundsInRoot
        val description=compose.onNodeWithText("Descripción").fetchSemanticsNode().boundsInRoot
        assertTrue(access.top<description.top)
        compose.onNodeWithText("Guardados").performClick();await("Pago habitual")
        compose.onNodeWithText("Pago habitual").performClick()
        compose.waitUntil(10000) {draft.title=="Pagar cuenta"}
        compose.onNodeWithText("Título").assertExists();assertEquals("Texto reusable",draft.description);assertEquals(Priority.HIGH,draft.priority);assertNotNull(draft.dueAt)
        assertTrue(runBlocking {app.database.backupDao().nodes().isEmpty()})
        compose.onNodeWithText("Título").performTextReplacement("Título propio");assertEquals("Título propio",draft.title)
    }
    @Test fun populatedDraftRequiresConfirmationAndCancellationPreservesEveryField() {
        seed();val draft=EditorDraft(null,null,"Original","Mis datos").apply {responsibleIds=listOf("keep")}
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {SavedTemplatesLibrary(draft) {}}}}
        await("Pago habitual");compose.onNodeWithText("Pago habitual").performClick();await("¿Reemplazar el borrador?")
        assertEquals("Original",draft.title);compose.onNodeWithText("Cancelar").performClick();assertEquals("Mis datos",draft.description);assertEquals(listOf("keep"),draft.responsibleIds)
        compose.onNodeWithText("Pago habitual").performClick();await("Aplicar plantilla");compose.onNodeWithText("Aplicar plantilla").performClick();compose.waitUntil(10000) {draft.title=="Pagar cuenta"};assertTrue(draft.responsibleIds.isEmpty())
    }
    @Test fun librarySearchEditRenameAndConfirmedDeletionWorkOnSmallScreen() {
        seed();val draft=EditorDraft(null,null,"","")
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {SavedTemplatesLibrary(draft) {}}}}
        await("Pago habitual");compose.onNodeWithText("Buscar por nombre").performTextInput("Pago");compose.onNodeWithText("Mantenimiento").assertDoesNotExist()
        compose.onNodeWithText("Editar / renombrar").performClick();await("Nombre de la plantilla")
        compose.onNodeWithText("Nombre de la plantilla").performTextReplacement("Pago renombrado")
        compose.onNodeWithText("Guardar").performClick();compose.waitUntil(10000) {runBlocking {app.database.savedTemplateDao().get("pay")!!.name=="Pago renombrado"}}
        await("Pago renombrado");compose.onNodeWithText("Eliminar").performClick();await("¿Eliminar Pago renombrado?")
        compose.onNodeWithText("Cancelar").performClick();assertNotNull(runBlocking {app.database.savedTemplateDao().get("pay")})
        compose.onNodeWithText("Eliminar").performClick();compose.onAllNodesWithText("Eliminar").onLast().performClick();compose.waitUntil(10000) {runBlocking {app.database.savedTemplateDao().get("pay")==null}}
    }
    @Test fun taskContextSavesIndependentEditableConfigurationWithoutCompletingTask() {
        val node=runBlocking {val p=app.projectRepository.createProject("P");app.nodeRepository.createNode(p.id,null,"Original","Contenido")}
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {SaveTaskTemplateDialog(node.id) {}}}}
        await("Nombre de la plantilla");compose.onNodeWithText("Nombre de la plantilla").performTextReplacement("Mi plantilla")
        compose.onNodeWithText("Guardar").performClick();compose.waitUntil(10000) {runBlocking {app.database.savedTemplateDao().all().isNotEmpty()}}
        val saved=runBlocking {app.database.savedTemplateDao().all().single()};assertNotEquals(node.id,saved.id);assertEquals("Mi plantilla",saved.name);assertFalse(runBlocking {app.database.nodeDao().getById(node.id)!!.isCompleted})
    }
}
