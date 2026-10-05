package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Row
import com.r0ybt.arachn0de.domain.model.NodePurpose
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.domain.model.TitleLimits
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

@Composable
internal fun NodeDialog(
    draft: EditorDraft,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    editDates: Boolean = true,
    people: List<com.r0ybt.arachn0de.domain.model.Person> = emptyList(),
    peopleLoaded: Boolean = true,
    tagRepository: com.r0ybt.arachn0de.data.repository.TagRepository? = null,
    onDiscard: () -> Unit = onDismiss,
) {
    val hasImages = com.r0ybt.arachn0de.domain.model.AttachmentReferences.ids(draft.description).isNotEmpty()
    val imageModeValid = draft.id != null || !hasImages || (!draft.batchEnabled && draft.recurrenceFrequency == "NONE")
    var confirmDiscard by rememberSaveable(draft.creationId) { mutableStateOf(false) }
    val datesValid = !editDates || draft.purpose != NodePurpose.ACTION ||
        ((!draft.startEnabled || draft.batchEnabled || draft.startAt != null) && (!draft.dueEnabled || draft.dueAt != null))
    val batchValid = draft.purpose == NodePurpose.LAYER || !draft.batchEnabled || runCatching { com.r0ybt.arachn0de.domain.model.NodeBatchGenerator.generate(draft.batchDraft().parameters(), java.util.TimeZone.getDefault()) }.isSuccess
    var confirmFinancialRemoval by rememberSaveable(draft.creationId) { mutableStateOf(false) }
    val financialValid = !editDates || draft.purpose != NodePurpose.ACTION || runCatching { draft.obligation() }.isSuccess
    val recurrenceValid = draft.recurrenceFrequency == "NONE" || draft.purpose != NodePurpose.ACTION || runCatching { draft.recurrenceRule("validation") }.isSuccess
    val datePicker = rememberSaveable(draft.creationId, saver = TaskDatePickerDraft.Saver) { TaskDatePickerDraft() }
    var title by draft::title
    var description by draft::description
    val titleFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val shouldAutoFocusForNewNode = draft.id == null && title.isEmpty() && description.isEmpty()

    LaunchedEffect(draft.id, title, description) {
        if (shouldAutoFocusForNewNode) {
            titleFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    NodeFullscreenDialog(
        containerColor = Arachn0deColors.Surface,
        titleContentColor = Arachn0deColors.TextPrimary,
        textContentColor = Arachn0deColors.TextSecondary,
        onDismissRequest = if (isSubmitting) ({}) else onDismiss,
        title = { Text(if (draft.id == null) "Nuevo elemento" else "Editar elemento") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberSaveable(draft.creationId, saver = androidx.compose.foundation.ScrollState.Saver) { androidx.compose.foundation.ScrollState(0) }), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormSection("General")
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (!isSubmitting) title = it },
                    label = { Text("Título") },
                    supportingText = { Text("${TitleLimits.count(title)} / ${TitleLimits.NODE}") },
                    isError = TitleLimits.count(title) > TitleLimits.NODE,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester),
                    enabled = !isSubmitting,
                )
                AttachmentDescriptionEditor(draft, !isSubmitting)
                if (draft.id == null) FormChoices(listOf(NodePurpose.ACTION to "Tarea", NodePurpose.NOTE to "Nota", NodePurpose.LAYER to "Capa"), draft.purpose, !isSubmitting) { draft.purpose = it }
                else Text("Tipo: ${when(draft.purpose) { NodePurpose.NOTE -> "Nota";NodePurpose.ACTION -> "Tarea";NodePurpose.LAYER -> "Capa" }}")
                if (editDates && draft.purpose == NodePurpose.ACTION) {
                    FormSection("Pago")
                    ObligationFields(draft, enabled = !isSubmitting)
                    FormSection("Planificación")
                    TaskDatesEditor(draft, enabled = !isSubmitting, picker = datePicker, includeStart = !draft.batchEnabled, progressive = true)
                    if (!datesValid) Text("Elige una fecha para cada opción activada o desactívala.", color = Arachn0deColors.Destructive)
                    if (draft.id == null) {
                        FormToggle("Recurrente", draft.recurrenceFrequency != "NONE", !isSubmitting && (!draft.batchEnabled || draft.recurrenceFrequency != "NONE")) { draft.toggleRecurrence(it) }
                        if (draft.batchEnabled) Text("Crear varios genera un lote finito; desactívalo para configurar una regla recurrente.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        if (draft.recurrenceFrequency != "NONE") RecurrenceFields(draft, !isSubmitting)
                    } else Text("Editar este elemento no modifica una regla recurrente ni otras ocurrencias.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    if (draft.activeStart != null && draft.activeDue != null && draft.activeDue!! < draft.activeStart!!) {
                        Text("El vencimiento no puede ser anterior al inicio. Corrige las fechas para guardar.", color = Arachn0deColors.Destructive)
                    }
                }
                FormSection("Organización")
                if (editDates && draft.purpose == NodePurpose.ACTION) PrioritySelector(draft.priority, { draft.priority = checkNotNull(it) }, enabled = !isSubmitting)
                tagRepository?.let { repo -> val state by remember(repo) { repo.observe() }.collectAsState(initial = com.r0ybt.arachn0de.domain.model.TagState())
                    TagSelector(state.tags, draft.tagIds.toSet(), { draft.tagIds = it.toList() }, repository = repo, enabled = !isSubmitting) }
                androidx.compose.material3.OutlinedButton(enabled = !isSubmitting && peopleLoaded, onClick = { draft.showResponsible = true }) {
                    Text("Responsables (${draft.responsibleIds.size})")
                }
                if (draft.id == null && draft.purpose != NodePurpose.LAYER) {
                    FormSection("Creación")
                    FormToggle("Crear varios", draft.batchEnabled, !isSubmitting && (draft.batchEnabled || draft.purpose != NodePurpose.ACTION || draft.recurrenceFrequency == "NONE")) { draft.batchEnabled = it }
                    if (draft.purpose == NodePurpose.ACTION && draft.recurrenceFrequency != "NONE") Text("Una regla recurrente y un lote finito son modalidades distintas. Desactiva Recurrente para crear varios.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    if (draft.batchEnabled) CreationBatchFields(draft, !isSubmitting)
                }
                if (draft.hasWork) DestructiveAction("Descartar", { confirmDiscard = true }, enabled = !isSubmitting && !draft.attachmentBusy)
                Text(
                    text = if(draft.purpose==NodePurpose.LAYER) "Una capa admite elementos y permanece como capa aunque esté vacía." else if (draft.purpose == NodePurpose.NOTE) "Una nota conserva información. Para añadir hijos, conviértela primero en capa." else if (draft.financialEnabled) "Para convertir en capa, confirma primero quitar los datos de pago." else "Para añadir elementos, convierte la tarea en capa desde ⋮.",
                    color = Arachn0deColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                enabled = !isSubmitting && draft.attachmentsLoaded && !draft.attachmentBusy && imageModeValid && !(draft.batchEnabled && draft.purpose == NodePurpose.ACTION && draft.recurrenceFrequency != "NONE") && batchValid && datesValid && peopleLoaded && financialValid && recurrenceValid && title.trim().isNotEmpty() && TitleLimits.count(title) <= TitleLimits.NODE &&
                    (!editDates || draft.purpose != NodePurpose.ACTION || draft.batchEnabled || draft.activeStart == null || draft.activeDue == null || draft.activeDue!! >= draft.activeStart!!),
                onClick = {
                    val cleanTitle = title.trim()
                    if (!isSubmitting && cleanTitle.isNotEmpty() && TitleLimits.count(cleanTitle) <= TitleLimits.NODE) {
                        if (draft.hadObligation && !draft.financialEnabled && !draft.financialRemovalConfirmed) confirmFinancialRemoval = true
                        else onSave(cleanTitle, description.trim())
                    }
                },
            ) {
                Text(if (isSubmitting) "Guardando..." else if (draft.id == null) "Crear" else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cerrar")
            }
        },
    )
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("¿Descartar borrador?") },
        text = { Text("Se eliminarán los cambios de este formulario.") },
        confirmButton = { DestructiveAction("Descartar borrador", onDiscard) },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Continuar editando") } })
    if (draft.showResponsible && peopleLoaded) ResponsibleDialog(
        nodeId = draft.creationId, people = people, assigned = people.filter { it.id in draft.responsibleIds }, busy = isSubmitting,
        onDismiss = { draft.showResponsible = false },
        onSave = { draft.responsibleIds = it.toList(); draft.showResponsible = false },
    )
    if (confirmFinancialRemoval) RemoveObligationDialog(isSubmitting, false,
        onDismiss = { confirmFinancialRemoval = false }, onConfirm = {
            draft.financialRemovalConfirmed = true
            onSave(title.trim(), description.trim())
        })
}

@Composable
internal fun ProjectDialog(
    draft: EditorDraft,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDiscard: () -> Unit = onDismiss,
) {
    var confirmDiscard by rememberSaveable(draft.attachmentDraftId) { mutableStateOf(false) }
    var name by draft::title
    var description by draft::description

    AlertDialog(
        containerColor = Arachn0deColors.Surface,
        titleContentColor = Arachn0deColors.TextPrimary,
        textContentColor = Arachn0deColors.TextSecondary,
        onDismissRequest = if (isSubmitting) ({}) else onDismiss,
        title = { Text(if (draft.id == null) "Nuevo proyecto" else "Editar proyecto") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = !isSubmitting,
                    label = { Text("Nombre") },
                    supportingText = { Text("${TitleLimits.count(name)} / ${TitleLimits.PROJECT}") },
                    isError = TitleLimits.count(name) > TitleLimits.PROJECT,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AttachmentDescriptionEditor(draft, !isSubmitting, project = true)
                if (draft.hasWork) DestructiveAction("Descartar", { confirmDiscard = true }, enabled = !isSubmitting && !draft.attachmentBusy)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting && draft.attachmentsLoaded && !draft.attachmentBusy && name.trim().isNotEmpty() && TitleLimits.count(name) <= TitleLimits.PROJECT,
                onClick = {
                    val cleanName = name.trim()
                    if (!isSubmitting && cleanName.isNotEmpty() && TitleLimits.count(cleanName) <= TitleLimits.PROJECT) {
                        onSave(cleanName, description.trim())
                    }
                },
            ) {
                Text(if (isSubmitting) "Guardando..." else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cerrar")
            }
        },
    )
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("¿Descartar borrador?") },
        text = { Text("Se eliminarán los cambios de este formulario.") },
        confirmButton = { DestructiveAction("Descartar borrador", onDiscard) },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Continuar editando") } })

}


/** Only the node editor uses this shell; the form and its saved scroll state stay unchanged. */
@Composable
private fun NodeFullscreenDialog(containerColor: androidx.compose.ui.graphics.Color,
    titleContentColor: androidx.compose.ui.graphics.Color, textContentColor: androidx.compose.ui.graphics.Color,
    onDismissRequest: () -> Unit, title: @Composable () -> Unit, text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit, dismissButton: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize().testTag("node-editor"), color = containerColor) {
            Column(Modifier.safeDrawingPadding().imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides titleContentColor) {
                    androidx.compose.material3.ProvideTextStyle(androidx.compose.material3.MaterialTheme.typography.headlineSmall, title)
                }
                androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxWidth()) {
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides textContentColor) {
                        androidx.compose.material3.ProvideTextStyle(androidx.compose.material3.MaterialTheme.typography.bodyMedium, text)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    dismissButton(); confirmButton()
                }
            }
        }
    }
}
