package com.r0ybt.arachn0de.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import com.r0ybt.arachn0de.domain.model.DescriptionParser
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import com.r0ybt.arachn0de.ui.state.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AttachmentDescriptionEditor(draft: EditorDraft, enabled: Boolean, project: Boolean = false, focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    repository: com.r0ybt.arachn0de.data.repository.AttachmentRepository = (LocalContext.current.applicationContext as Arachn0deApplication).attachmentRepository) {
    val scope = rememberCoroutineScope()
    val files by remember(repository) { repository.observeFiles() }.collectAsState(emptyList())
    val byId = files.associateBy { it.id }
    var restored by remember(draft.attachmentDraftId) { mutableStateOf(false) }
    LaunchedEffect(draft.attachmentDraftId) {
        draft.attachmentsLoaded = false
        try {
            repository.pendingImports(draft.attachmentDraftId).forEach {
                if (it.id !in AttachmentReferences.ids(draft.description) && it.id !in draft.removedAttachmentIds) draft.insertImage(it.id)
            }
            restored = true; draft.attachmentsLoaded = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { draft.attachmentError = "No se pudieron recuperar las imágenes. Cierra y reabre el editor." }
    }
    LaunchedEffect(restored, focusRequester) { if (restored) focusRequester?.requestFocus() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.size > 10) draft.attachmentError = "Selecciona como máximo 10 imágenes cada vez."
        else if (uris.isNotEmpty()) scope.launch {
            draft.attachmentBusy = true; draft.attachmentError = null
            try {
                for (uri in uris) draft.insertImage(repository.import(uri, draft.attachmentDraftId).id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { draft.attachmentError = "No se pudo importar una imagen. Usa PNG/JPEG de hasta 20 MiB; las anteriores se conservan." }
            finally { draft.attachmentBusy = false }
        }
    }
    var inputValue by remember(draft.attachmentDraftId) { mutableStateOf(draft.descriptionValue()) }
    val draftValue = draft.descriptionValue()
    val fieldValue = if (inputValue.text == draftValue.text && inputValue.selection == draftValue.selection) inputValue else draftValue
    var viewingAt by androidx.compose.runtime.saveable.rememberSaveable(draft.attachmentDraftId) { mutableStateOf<Int?>(null) }
    AttachmentDescriptionField(value = fieldValue, onValueChange = { value ->
        if (draft.editDescription(value)) inputValue = value
        else draft.attachmentError = "Para quitar una imagen, toca su referencia y usa Quitar en el visor."
    }, enabled = enabled && restored && !draft.attachmentBusy,
        focusRequester = focusRequester, transformation = AttachmentVisualTransformation(byId), onReference = { viewingAt = it })
    val supportsMode = project || draft.id != null || (!draft.batchEnabled && draft.recurrenceFrequency == "NONE")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DescriptionParser.Format.entries.forEach { format ->
            val name = when (format) {
                DescriptionParser.Format.Bold -> "Negrita"
                DescriptionParser.Format.Italic -> "Cursiva"
                DescriptionParser.Format.Underline -> "Subrayado"
            }
            TextButton(modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = name },
                enabled = enabled && restored && !draft.attachmentBusy && draft.descriptionSelectionStart != draft.descriptionSelectionEnd,
                onClick = {
                    if (!draft.toggleDescriptionFormat(format)) draft.attachmentError = "Selecciona texto o una referencia completa para aplicar formato."
                }) {
                Text(when (format) { DescriptionParser.Format.Bold -> "B"; DescriptionParser.Format.Italic -> "I"; DescriptionParser.Format.Underline -> "U" },
                    fontWeight = if (format == DescriptionParser.Format.Bold) FontWeight.Bold else null,
                    fontStyle = if (format == DescriptionParser.Format.Italic) FontStyle.Italic else null,
                    textDecoration = if (format == DescriptionParser.Format.Underline) TextDecoration.Underline else null)
            }
        }
        TextButton(enabled = enabled && restored && !draft.attachmentBusy && supportsMode,
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Text(if (draft.attachmentBusy) "Importando imágenes…" else "Imagen", color = Arachn0deColors.Accent)
        }
    }
    if (!supportsMode) Text("En lotes o recurrencias, añade las imágenes a cada elemento después de crearlo.", style = MaterialTheme.typography.bodySmall)
    var labelAt by remember(draft.attachmentDraftId) { mutableStateOf<Int?>(null) }
    var labelText by remember(draft.attachmentDraftId) { mutableStateOf("") }
    viewingAt?.let { at ->
        AttachmentReferences.matches(draft.description).firstOrNull { it.range.first == at }?.let { reference ->
            AttachmentViewer(reference.groupValues[1], repository,
                onEditName = if (enabled && !draft.attachmentBusy) ({ labelAt = at; labelText = AttachmentReferences.label(reference) ?: "" }) else null,
                onRemove = if (enabled && !draft.attachmentBusy) ({
                    draft.removeImageUse(at); viewingAt = null
                }) else null,
                onClose = { viewingAt = null })
        }
    }
    labelAt?.let { at ->
        AlertDialog(onDismissRequest = { labelAt = null }, title = { Text("Etiqueta de imagen") },
            text = {
                OutlinedTextField(value = labelText, onValueChange = { labelText = it }, singleLine = true,
                    label = { Text("Etiqueta visible") }, supportingText = { Text("Vacía: usar el nombre original.") },
                    modifier = Modifier.fillMaxWidth().testTag("image-label-input"))
            },
            confirmButton = { TextButton(onClick = { draft.setImageLabel(at, labelText); labelAt = null }) { Text("Aplicar") } },
            dismissButton = { TextButton(onClick = { labelAt = null }) { Text("Cancelar") } })
    }
    draft.attachmentError?.let { Text(it, color = Arachn0deColors.Destructive, style = MaterialTheme.typography.bodySmall) }
}
