package com.r0ybt.arachn0de.ui

import androidx.compose.material.icons.filled.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
    maxLines: Int = Int.MAX_VALUE, tools: (@Composable () -> Unit)? = null,
    repository: com.r0ybt.arachn0de.data.repository.AttachmentRepository = (LocalContext.current.applicationContext as Arachn0deApplication).attachmentRepository) {
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
    LaunchedEffect(files,draft.id) {
        if(!project && draft.id!=null) repository.filesForNode(draft.id).filter {it.mimeType=="audio/aac"}.forEach {
            if(it.id !in AttachmentReferences.ids(draft.description) && it.id !in draft.removedAttachmentIds)
                draft.description += "\n"+AttachmentReferences.audioToken(it.id)
        }
    }
    LaunchedEffect(restored, focusRequester) { if (restored) focusRequester?.requestFocus() }
    var inputValue by remember(draft.attachmentDraftId) { mutableStateOf(draft.descriptionValue()) }
    val draftValue = draft.descriptionValue()
    val fieldValue = if (inputValue.text == draftValue.text && inputValue.selection == draftValue.selection) inputValue else draftValue
    var viewingAt by androidx.compose.runtime.saveable.rememberSaveable(draft.attachmentDraftId) { mutableStateOf<Int?>(null) }
    AttachmentDescriptionField(value = fieldValue, onValueChange = { value ->
        if (draft.editDescription(value)) inputValue = value
        else draft.attachmentError = "Para quitar una imagen, toca su referencia y usa Quitar en el visor."
    }, enabled = enabled && restored && !draft.attachmentBusy,
        focusRequester = focusRequester, maxLines = maxLines, transformation = AttachmentVisualTransformation(byId,com.r0ybt.arachn0de.ui.theme.Arachn0deColors.ImageLink), onReference = { viewingAt = it })
    tools?.invoke() ?: AttachmentDescriptionTools(draft, enabled, project, repository = repository)
    var labelAt by remember(draft.attachmentDraftId) { mutableStateOf<Int?>(null) }
    var labelText by remember(draft.attachmentDraftId) { mutableStateOf("") }
    viewingAt?.let { at ->
        AttachmentReferences.matches(draft.description).firstOrNull { it.range.first == at }?.let { reference ->
            AttachmentViewer(reference.groupValues[1], repository,
                onEditName = if (!AttachmentReferences.isAudio(reference) && enabled && !draft.attachmentBusy) ({ labelAt = at; labelText = AttachmentReferences.label(reference) ?: "" }) else null,
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

/** Existing tools can move with the note keyboard footer without moving editor state. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AttachmentDescriptionTools(draft: EditorDraft, enabled: Boolean, project: Boolean = false, compact: Boolean = false,
    trailingActions: (@Composable () -> Unit)? = null,
    repository: com.r0ybt.arachn0de.data.repository.AttachmentRepository = (LocalContext.current.applicationContext as Arachn0deApplication).attachmentRepository) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val restored = draft.attachmentsLoaded
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
    val audioTools = !project && draft.id!=null && draft.purpose==com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION
    val supportsMode = project || draft.id != null || (!draft.batchEnabled && draft.recurrenceFrequency == "NONE")
    val actions: @Composable (Modifier) -> Unit = { formatModifier ->
        DescriptionParser.Format.entries.forEach { format ->
            val name = when (format) {
                DescriptionParser.Format.Bold -> "Negrita"
                DescriptionParser.Format.Italic -> "Cursiva"
                DescriptionParser.Format.Underline -> "Subrayado"
            }
            TextButton(modifier = (if(trailingActions!=null) formatModifier.heightIn(min=48.dp) else Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)).semantics { contentDescription = name },
                contentPadding=if(trailingActions!=null) PaddingValues(0.dp) else ButtonDefaults.TextButtonContentPadding,
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
        TextButton(modifier = if(!project) Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp) else if(trailingActions!=null) Modifier.widthIn(min=56.dp).heightIn(min=48.dp) else if(compact) Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp) else Modifier,
            contentPadding=if(trailingActions!=null) PaddingValues(4.dp) else ButtonDefaults.TextButtonContentPadding,
            enabled = enabled && restored && !draft.attachmentBusy && supportsMode,
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            if(!project) Icon(androidx.compose.material.icons.Icons.Default.Image,if(draft.attachmentBusy) "Importando imágenes" else "Imagen",tint=Arachn0deColors.Accent)
            else Text(if (draft.attachmentBusy) "Importando imágenes…" else "Imagen", color = Arachn0deColors.Accent,
                maxLines=if(trailingActions!=null) 1 else Int.MAX_VALUE,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        if(audioTools) AudioMicButton(enabled=enabled && restored && !draft.attachmentBusy) {
            (context.applicationContext as Arachn0deApplication).security.current.value?.audio?.startRecording(requireNotNull(draft.id))
        }
    }
    if(trailingActions!=null) Row(Modifier.fillMaxWidth().testTag("description-toolbar"),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).testTag("description-tools"),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
            actions(Modifier.width(48.dp))
        }
        trailingActions()
    }
    else if (compact) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { actions(Modifier) }
    else FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { actions(Modifier) }
    if (!supportsMode) Text("En lotes o recurrencias, añade las imágenes a cada elemento después de crearlo.", style = MaterialTheme.typography.bodySmall)

}
