package com.r0ybt.arachn0de.ui

import android.app.Activity
import android.content.Intent
import android.content.ClipData
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.AttachmentStore
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import com.r0ybt.arachn0de.ui.state.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AttachmentEditorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = compose.activity.application as Arachn0deApplication
    private val repository get() = AttachmentRepository(app.database,
        AttachmentStore(app, syncDirectory = com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory))
    private fun input(): File {
        val file = File(app.cacheDir, "bug.png")
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        return file
    }
    private fun await(text: String) = compose.waitUntil(10000) {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }
    private fun openReference(draft: EditorDraft, at: Int) {
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNode(hasSetTextAction() and hasText("Descripción")).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val files = runBlocking { app.database.attachmentDao().files() }.associateBy { it.id }
        val transformed = AttachmentVisualTransformation(files).filter(androidx.compose.ui.text.AnnotatedString(draft.description))
        val offset = transformed.offsetMapping.originalToTransformed(at)
        val bounds = layouts.single().getBoundingBox(offset)
        compose.onNodeWithTag("description-inner", useUnmergedTree = true).performTouchInput { click(bounds.center) }
        compose.onNodeWithTag("attachment-viewer").assertExists()
    }
    @Test fun photoPickerImportCloseReopenAndExplicitDiscard() {
        val repo = repository
        val store = EditorDraftStore().apply { open(null) { EditorDraft(null, null, "Task", "Before") } }
        val identity = store.active!!.attachmentDraftId
        compose.setContent {
            val scope = rememberCoroutineScope()
            Column {
                store.active?.let { draft ->
                    AttachmentDescriptionEditor(draft, true, repository = repo)
                    Button(onClick = { store.close() }) { Text("Close draft") }
                    Button(onClick = { scope.launch { repo.discardDraft(draft.attachmentDraftId); store.clear() } }) { Text("Discard draft") }
                } ?: Button(onClick = { store.open(null) { error("Draft must survive") } }) { Text("Reopen draft") }
            }
        }
        compose.onNodeWithText("Imagen").assertIsEnabled().performClick()
        val started = Shadows.shadowOf(compose.activity).nextStartedActivityForResult
        assertNotNull(started)
        val first = input()
        val second = File(app.cacheDir, "second.png").apply { writeBytes(first.readBytes()) }
        val clips = ClipData.newUri(app.contentResolver, "images", Uri.fromFile(first)).apply { addItem(ClipData.Item(Uri.fromFile(second))) }
        Shadows.shadowOf(compose.activity).receiveResult(started.intent, Activity.RESULT_OK, Intent().apply { clipData = clips })
        await("bug.png"); await("second.png")
        assertEquals(2, AttachmentReferences.ids(store.active!!.description).size)
        compose.onNodeWithText("Close draft").performClick()
        assertEquals(2, runBlocking { repo.pendingImports(identity).size })
        compose.onNodeWithText("Reopen draft").performClick(); await("bug.png")
        assertEquals(identity, store.active!!.attachmentDraftId)
        compose.onNodeWithText("Discard draft").performClick()
        compose.waitUntil(10000) { store.active == null }
        assertTrue(runBlocking { repo.pendingImports(identity).isEmpty() })
    }
    @Test fun renderedReferenceUsesFilenameAndViewerClosesWithButtonAndBack() {
        val repo = repository
        val file = runBlocking { repo.import(Uri.fromFile(input()), "viewer-draft") }
        compose.setContent { AttachmentText(AttachmentReferences.token(file.id)) }
        await("bug.png")
        compose.onAllNodesWithText(file.id, substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("arachnode:image", substring = true).assertCountEquals(0)
        compose.onNodeWithText("bug.png").performTouchInput { click(center) }
        compose.onNodeWithTag("attachment-viewer").assertExists()
        compose.onNode(hasText("Cerrar") and hasAnyAncestor(hasTestTag("attachment-viewer"))).performClick()
        compose.onNodeWithTag("attachment-viewer").assertDoesNotExist()
        compose.onNodeWithText("bug.png").performTouchInput { click(center) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("attachment-viewer").assertDoesNotExist()
    }
    @Test fun nonexistentReferenceIsCompactAndKeepsMarkerOutOfCardPreview() {
        val id = "00000000-0000-0000-0000-000000000009"
        val task = com.r0ybt.arachn0de.domain.model.Node("task", "project", null, "Title", AttachmentReferences.token(id), false, 0, 0, 0, false)
        compose.setContent { NodeCard(task, null, false, true, {}, {}, {}, false, false, { _, done -> done() }, {}) }
        await("Imagen no disponible")
        compose.onAllNodesWithText(id, substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("arachnode:image", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }
    @Test fun nodeDialogSaveConfirmsImagesAndReopensWithoutInternalMarkers() {
        val repo = repository
        val project = runBlocking { app.projectRepository.createProject("Work") }
        val file = runBlocking { repo.import(Uri.fromFile(input()), "node-draft") }
        val draft = EditorDraft(null, null, "Task", "Before", attachmentDraftId = "node-draft").apply { insertImage(file.id) }
        compose.setContent {
            val scope = rememberCoroutineScope()
            val actions = remember { NodeActions(app.nodeRepository, scope, repo) }
            var saved by remember { mutableStateOf(false) }
            var current by remember { mutableStateOf(draft) }
            if (saved) Column {
                Text("Saved node")
                Button(onClick = { scope.launch {
                    val row = app.nodeRepository.getNode(draft.creationId)!!
                    current = EditorDraft(row.id, row.parentId, row.title, row.description)
                    saved = false
                } }) { Text("Reopen node") }
            } else NodeDialog(current, actions.operation.busy, {}, { _, _ ->
                actions.saveDraft(project.id, current, true) { saved = true }
            })
        }
        await("bug.png")
        compose.onNodeWithText("Crear").assertIsEnabled().performClick(); await("Saved node")
        val node = runBlocking { app.nodeRepository.getNode(draft.creationId)!! }
        assertEquals(draft.description.trim(), node.description)
        assertEquals(listOf(file.id), runBlocking { repo.filesForNode(node.id).map { it.id } })
        assertTrue(runBlocking { repo.pendingImports("node-draft").isEmpty() })
        compose.onNodeWithText("Reopen node").performClick(); await("bug.png")
        compose.onNodeWithText("Guardar").assertIsEnabled()
    }
    @Test fun projectDialogSaveConfirmsImagesAndRemovalIsDeferredUntilSave() {
        val repo = repository
        val file = runBlocking { repo.import(Uri.fromFile(input()), "project-draft") }
        val draft = EditorDraft(null, null, "Project", "Before", attachmentDraftId = "project-draft").apply { insertImage(file.id) }
        compose.setContent {
            val scope = rememberCoroutineScope()
            val actions = remember { ProjectActions(app.projectRepository, scope, repo) }
            var saved by remember { mutableStateOf(false) }
            var current by remember { mutableStateOf(draft) }
            if (saved) Column {
                Text("Saved project")
                Button(onClick = { scope.launch {
                    val row = app.projectRepository.getProject(draft.creationId)!!
                    current = EditorDraft(row.id, null, row.name, row.description)
                    saved = false
                } }) { Text("Reopen project") }
            } else ProjectDialog(current, actions.operation.busy, {}, { name, description ->
                actions.save(current.id, name, description, current.creationId, draft = current) { saved = true }
            })
        }
        await("bug.png")
        compose.onNodeWithText("Guardar").assertIsEnabled().performClick(); await("Saved project")
        val project = runBlocking { app.projectRepository.getProject(draft.creationId)!! }
        assertEquals(draft.description.trim(), project.description)
        assertEquals(listOf(file.id), runBlocking { repo.filesForProject(project.id).map { it.id } })
        compose.onNodeWithText("Reopen project").performClick(); await("bug.png")
        val row = runBlocking { app.projectRepository.getProject(project.id)!! }
        // Reopened field contains the same stored occurrence.
        openReference(EditorDraft(row.id, null, row.name, row.description), AttachmentReferences.matches(row.description).first().range.first)
        compose.onNodeWithText("Quitar").performClick()
        assertEquals(1, runBlocking { repo.filesForProject(project.id).size })
        compose.onNodeWithText("Guardar").performClick(); await("Saved project")
        assertTrue(runBlocking { repo.filesForProject(project.id).isEmpty() })
    }

    @Test fun labelDialogEditsOneUseAndEmptyLabelRestoresFilename() {
        val repo = repository
        val file = runBlocking { repo.import(Uri.fromFile(input()), "label-draft") }
        val draft = EditorDraft(null, null, "Task", "${AttachmentReferences.token(file.id)} ${AttachmentReferences.token(file.id, "Otro uso")}", attachmentDraftId = "label-draft")
        compose.setContent { Column { AttachmentDescriptionEditor(draft, true, repository = repo) } }
        await("Imagen")
        fun setLabel(label: String) {
            openReference(draft, 0)
            compose.onNodeWithText("Editar nombre").performClick()
            compose.onNodeWithTag("image-label-input").performTextReplacement(label)
            compose.onNodeWithText("Aplicar").performClick()
            compose.onNode(hasText("Cerrar") and hasAnyAncestor(hasTestTag("attachment-viewer"))).performClick()
        }
        setLabel("Gráfico")
        assertEquals(listOf("Gráfico", "Otro uso"), AttachmentReferences.matches(draft.description).map { AttachmentReferences.label(it) }.toList())
        setLabel("Resultado esperado")
        assertEquals("Resultado esperado", AttachmentReferences.label(AttachmentReferences.matches(draft.description).first()))
        setLabel("")
        assertNull(AttachmentReferences.label(AttachmentReferences.matches(draft.description).first()))
        await("bug.png")
        assertEquals(file, runBlocking { app.database.attachmentDao().get(file.id) })
        openReference(draft, 0)
        compose.onNodeWithText("Quitar").performClick()
        assertEquals(setOf(file.id), AttachmentReferences.ids(draft.description))
        assertTrue(draft.removedAttachmentIds.isEmpty())
        assertEquals("Otro uso", AttachmentReferences.label(AttachmentReferences.matches(draft.description).single()))
        compose.onNodeWithTag("attachment-viewer").assertDoesNotExist()
    }
    @Test fun formattingButtonsApplyAndRemoveSelectedText() {
        val draft = EditorDraft(null, null, "Task", "Texto normal")
        compose.setContent { Column { AttachmentDescriptionEditor(draft, true, repository = repository) } }
        await("Imagen")
        compose.onNodeWithContentDescription("Negrita").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInputSelection(androidx.compose.ui.text.TextRange(0, 5))
        compose.onNodeWithContentDescription("Negrita").performClick()
        assertEquals("**Texto** normal", draft.description)
        compose.onNodeWithContentDescription("Cursiva").performClick()
        compose.onNodeWithContentDescription("Subrayado").performClick()
        assertEquals(3, com.r0ybt.arachn0de.domain.model.DescriptionParser.parse(draft.description).first().formats.size)
        compose.onNodeWithContentDescription("Negrita").performClick()
        compose.onNodeWithContentDescription("Cursiva").performClick()
        compose.onNodeWithContentDescription("Subrayado").performClick()
        assertEquals("Texto normal", draft.description)
        assertEquals(0, draft.descriptionSelectionStart); assertEquals(5, draft.descriptionSelectionEnd)
    }
    @Test fun customLabelWithFormattingKeepsInteractiveViewerAndHidesSyntax() {
        val repo = repository
        val file = runBlocking { repo.import(Uri.fromFile(input()), "caption-viewer") }
        compose.setContent { AttachmentText("**Antes** ${AttachmentReferences.token(file.id, "Gráfico")} __Después__", maxLines = 2) }
        await("Antes Gráfico Después")
        compose.onAllNodesWithText(file.id, substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("**", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("__", substring = true).assertCountEquals(0)
        // Click the middle of the linked range in this single-line preview.
        val node = compose.onNodeWithText("Antes Gráfico Después")
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val bounds = layouts.single().getBoundingBox(9)
        node.performTouchInput { click(bounds.center) }
        compose.onNodeWithTag("attachment-viewer").assertExists()
        compose.onNode(hasText("Cerrar") and hasAnyAncestor(hasTestTag("attachment-viewer"))).performClick()
    }
    @Test fun cardPreviewRendersFormattingWithoutVisibleMarkers() {
        val task = com.r0ybt.arachn0de.domain.model.Node("task", "project", null, "Title", "**Antes** *medio* __después__", false, 0, 0, 0, false)
        compose.setContent { NodeCard(task, null, false, true, {}, {}, {}, false, false, { _, done -> done() }, {}) }
        compose.onNodeWithText("Antes medio después", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("**", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("__", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }
    @Config(sdk = [33])
    @Test fun nodeEditorFillsWindowKeepsScrollableFieldsAndDraftThroughViewer() {
        val repo = repository
        val file = runBlocking { repo.import(Uri.fromFile(input()), "full-node") }
        val draft = EditorDraft(null, null, "Task", "Before ${AttachmentReferences.token(file.id, "Gráfico")} After", attachmentDraftId = "full-node")
        compose.setContent { NodeDialog(draft, false, {}, { _, _ -> }) }
        await("Gráfico")
        val bounds = compose.onNodeWithTag("node-editor").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.height >= compose.activity.window.decorView.height * .95f)
        assertTrue(bounds.width >= compose.activity.window.decorView.width * .95f)
        val editorWindow = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
        val before = draft.description
        openReference(draft, AttachmentReferences.matches(draft.description).first().range.first)
        compose.onNode(hasText("Cerrar") and hasAnyAncestor(hasTestTag("attachment-viewer"))).performClick()
        assertEquals(before, draft.description)
        compose.onNodeWithText("Responsables (0)").performScrollTo().assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Descripción")).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Descripción")).performTextInput("Texto con teclado ")
        assertTrue(draft.description.contains("Texto con teclado"))
        compose.runOnIdle {
            val insets = androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(), androidx.core.graphics.Insets.of(0, 24, 0, 24))
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.of(0, 0, 0, 160))
                .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(), true).build()
            fun composeView(view: android.view.View): android.view.View? {
                if (view.javaClass.simpleName == "AndroidComposeView") return view
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) {
                    composeView(view.getChildAt(i))?.let { return it }
                }
                return null
            }
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(checkNotNull(composeView(editorWindow)), insets)
        }
        compose.onNodeWithText("Crear").assertIsDisplayed().assertIsEnabled()
        val actionBottom = compose.onNodeWithText("Crear").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("Action bottom $actionBottom, window height ${bounds.height}", actionBottom <= bounds.height - 160)
        compose.onNodeWithText("Responsables (0)").performScrollTo().assertIsDisplayed()
    }
    @Test fun clipboardBothScopesUseHumanDescriptionsWithFileNameFallback() {
        val application = app
        val repo = repository
        val file = runBlocking { repo.import(Uri.fromFile(input()), "copy-draft") }
        val setup = runBlocking {
            val project = application.projectRepository.createProject("Copy project")
            val parent = application.nodeRepository.createNode(project.id, null, "Layer", purpose = com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
            val child = application.nodeRepository.createNode(project.id, parent.id, "Child")
            repo.saveNodeDraft("copy-draft", listOf(parent.id), "**Antes** ${AttachmentReferences.token(file.id, "Gráfico")} __Después__", emptySet()) {
                application.nodeRepository.updateNode(parent.id, "Layer", it)
            }
            repo.saveNodeDraft("child-draft", listOf(child.id), "*Inicio* ${AttachmentReferences.token(file.id)} **Fin**", emptySet()) {
                application.nodeRepository.updateNode(child.id, "Child", it)
            }
            project to parent.id
        }
        val tree = runBlocking { com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot(application.nodeRepository.getProjectNodes(setup.first.id)) }
        compose.setContent {
            val scope = rememberCoroutineScope()
            val actions = remember { NodeCopyActions(application, scope) }
            Column {
                Button(onClick = { actions.copy(tree, setup.second, false) }) { Text("Copy single") }
                Button(onClick = { actions.copy(tree, setup.second, true) }) { Text("Copy descendants") }
            }
        }
        val clipboard = application.getSystemService(android.content.ClipboardManager::class.java)
        fun copied(button: String, expectChild: Boolean) {
            clipboard.clearPrimaryClip()
            compose.onNodeWithText(button).performClick()
            compose.waitUntil(10000) {
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                clipboard.primaryClip != null
            }
            val text = clipboard.primaryClip!!.getItemAt(0).text.toString()
            assertFalse(text.contains(file.id)); assertFalse(text.contains("arachnode:image")); assertFalse(text.contains("**")); assertFalse(text.contains("__"))
            assertTrue(text.contains("Antes [Imagen: Gráfico] Después"))
            assertEquals(expectChild, text.contains("Inicio [Imagen: bug.png] Fin"))
        }
        copied("Copy single", false); copied("Copy descendants", true)
        assertEquals(tree.nodes, runBlocking { application.nodeRepository.getProjectNodes(setup.first.id) })
    }
    @Test fun duplicateLabelsInEditorKeepIndependentReferenceActionsAndPendingFiles() {
        val repo = repository
        val first = runBlocking { repo.import(Uri.fromFile(input()), "duplicate-draft") }
        val second = runBlocking { repo.import(Uri.fromFile(input()), "duplicate-draft") }
        val draft = EditorDraft(null, null, "Task", "${AttachmentReferences.token(first.id, "Gráfico")} ${AttachmentReferences.token(second.id, "Gráfico")}", attachmentDraftId = "duplicate-draft")
        compose.setContent { Column { AttachmentDescriptionEditor(draft, true, repository = repo) } }
        await("Imagen")
        val secondAt = AttachmentReferences.matches(draft.description).last().range.first
        openReference(draft, secondAt)
        compose.onNodeWithText("Editar nombre").performClick()
        compose.onNodeWithTag("image-label-input").performTextReplacement("Resultado")
        compose.onNodeWithText("Aplicar").performClick()
        assertEquals(listOf("Gráfico", "Resultado"), AttachmentReferences.matches(draft.description).map { AttachmentReferences.label(it) }.toList())
        compose.onNodeWithText("Quitar").performClick()
        assertEquals(setOf(first.id), AttachmentReferences.ids(draft.description))
        assertEquals(listOf(second.id), draft.removedAttachmentIds)
        assertEquals(setOf(first.id, second.id), runBlocking { repo.pendingImports("duplicate-draft").map { it.id }.toSet() })
        assertEquals(first, runBlocking { app.database.attachmentDao().get(first.id) })
        assertEquals(second, runBlocking { app.database.attachmentDao().get(second.id) })
    }
}
