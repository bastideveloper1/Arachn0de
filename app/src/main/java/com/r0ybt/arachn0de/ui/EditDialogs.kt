package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.SideEffect
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun NodeDialog(
    draft: EditorDraft,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    editDates: Boolean = true,
    initialField: String? = null,
    hasUnsavedChanges: Boolean = draft.hasWork,
    people: List<com.r0ybt.arachn0de.domain.model.Person> = emptyList(),
    peopleLoaded: Boolean = true,
    tagRepository: com.r0ybt.arachn0de.data.repository.TagRepository? = null,
    onDiscard: () -> Unit = onDismiss,
    templateEditing: Boolean = false,
    extraFields: @Composable () -> Unit = {},
    extraValid: Boolean = true,
) {
    val hasImages = com.r0ybt.arachn0de.domain.model.AttachmentReferences.ids(draft.description).isNotEmpty()
    val imageModeValid = draft.id != null || !hasImages || (!draft.batchEnabled && draft.recurrenceFrequency == "NONE")
    var confirmDiscard by rememberSaveable(draft.creationId) { mutableStateOf(false) }
    val specializedCreationValid = !(draft.id==null && (draft.batchEnabled || draft.recurrenceFrequency!="NONE") && (draft.technologyIds.isNotEmpty() || draft.templateMetroPlan!=null))
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
    val descriptionFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val shouldAutoFocusForNewNode = draft.id == null && title.isEmpty() && description.isEmpty()

    LaunchedEffect(draft.attachmentDraftId, initialField) {
        if (shouldAutoFocusForNewNode || initialField == "title") {
            titleFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val pinnedNoteTools = !templateEditing
    val currentSubmitting by androidx.compose.runtime.rememberUpdatedState(isSubmitting)
    val noteTools = remember(draft) {
        androidx.compose.runtime.movableContentOf<Boolean> { compact ->
            AttachmentDescriptionTools(draft, !currentSubmitting, compact = compact)
        }
    }
    NodeFullscreenDialog(
        containerColor = Arachn0deColors.Surface,
        titleContentColor = Arachn0deColors.TextPrimary,
        textContentColor = Arachn0deColors.TextSecondary,
        onDismissRequest = if (isSubmitting) ({}) else onDismiss,
        title = { Text(if(templateEditing) "Guardar plantilla" else if (draft.id == null) "Nuevo elemento" else "Editar elemento") },
        text = {
            Column {
                if (draft.id == null || templateEditing) FormChoices((if(templateEditing) listOf(NodePurpose.ACTION to "Tarea", NodePurpose.NOTE to "Nota") else listOf(NodePurpose.ACTION to "Tarea", NodePurpose.NOTE to "Nota", NodePurpose.LAYER to "Capa")), draft.purpose, !isSubmitting) { draft.purpose = it }
                else Text("Tipo: ${when(draft.purpose) { NodePurpose.NOTE -> "Nota";NodePurpose.ACTION -> "Tarea";NodePurpose.LAYER -> "Capa" }}")
                if(!templateEditing) SavedTemplatesAccess(draft,!isSubmitting && !draft.attachmentBusy)
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberSaveable(draft.creationId, saver = androidx.compose.foundation.ScrollState.Saver) { androidx.compose.foundation.ScrollState(0) }), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (!isSubmitting) title = it },
                        label = { Text("Título") },
                        supportingText = { Text("${TitleLimits.count(title)} / ${TitleLimits.NODE}") },
                        isError = TitleLimits.count(title) > TitleLimits.NODE,
                        singleLine = false, minLines = 1, maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(titleFocusRequester),
                        enabled = !isSubmitting,
                    )
                    extraFields()
                    if(templateEditing) OutlinedTextField(description,{description=it},label={Text("Descripción")},modifier=Modifier.fillMaxWidth())
                    else AttachmentDescriptionEditor(draft, !isSubmitting,
                        focusRequester = descriptionFocusRequester.takeIf { initialField == "description" },
                        maxLines = if(draft.purpose == NodePurpose.NOTE && !templateEditing && WindowInsets.isImeVisible) 5 else Int.MAX_VALUE,
                        tools = if(pinnedNoteTools || draft.purpose == NodePurpose.NOTE) ({ if(!pinnedNoteTools) noteTools(false) }) else null)
                    if(!specializedCreationValid) Text("Las tecnologías ordenadas y el plan Metro requieren creación individual. Desactiva Crear varios / Recurrente o quita estas propiedades para continuar.",color=Arachn0deColors.Destructive)
                    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(0.dp),itemVerticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.IconButton(enabled=!isSubmitting && peopleLoaded,onClick={draft.showResponsible=true},modifier=Modifier.size(48.dp)) {
                            LucideIcon(androidx.compose.material.icons.Icons.Default.Groups,"Responsables (${draft.responsibleIds.size})",tint=if(draft.responsibleIds.isEmpty()) Arachn0deColors.TextSecondary else Arachn0deColors.TextPrimary)
                        }
                        DraftTechnologies(draft,!isSubmitting,iconOnly=true)
                        tagRepository?.let {repo->val state by remember(repo) {repo.observe()}.collectAsState(initial=com.r0ybt.arachn0de.domain.model.TagState())
                            TagSelector(state.tags,draft.tagIds.toSet(),{draft.tagIds=it.toList()},repository=repo,enabled=!isSubmitting,iconOnly=true,singleAssignment=true)
                        }
                    }
                    if (editDates && draft.purpose == NodePurpose.ACTION) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            val prioritized = draft.priority == com.r0ybt.arachn0de.domain.model.Priority.HIGH
                            FilterChip(
                                selected = prioritized,
                                onClick = { draft.priority = if (prioritized) com.r0ybt.arachn0de.domain.model.Priority.NONE else com.r0ybt.arachn0de.domain.model.Priority.HIGH },
                                enabled = !isSubmitting,
                                label = { Text(if (prioritized) "Prioritario" else "Común") },
                                leadingIcon = if (prioritized) ({ LucideIcon(androidx.compose.material.icons.Icons.Default.LocalFireDepartment, "Prioritario") }) else null,
                            )
                            FilterChip(
                                selected = draft.financialEnabled,
                                onClick = {
                                    draft.financialEnabled = !draft.financialEnabled
                                    draft.financialRemovalConfirmed = false
                                },
                                enabled = !isSubmitting,
                                label = { Text(if (draft.financialEnabled) "Pago" else "No pago") },
                            )
                        }
                    }
                    if (editDates && draft.purpose == NodePurpose.ACTION) {
                        if(!templateEditing) FormSection("Planificación")
                        if(!templateEditing) TaskDatesEditor(draft, enabled = !isSubmitting, picker = datePicker, includeStart = !draft.batchEnabled, progressive = true)
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
                    if (editDates && draft.purpose == NodePurpose.ACTION) {
                        FormSection("Pago")
                        ObligationFields(draft, enabled = !isSubmitting, showToggle = false)
                    }
                    if (draft.id == null && draft.purpose != NodePurpose.LAYER) {
                        FormSection("Creación")
                        FormToggle("Crear varios", draft.batchEnabled, !isSubmitting && (draft.batchEnabled || draft.purpose != NodePurpose.ACTION || draft.recurrenceFrequency == "NONE")) { draft.batchEnabled = it }
                        if (draft.purpose == NodePurpose.ACTION && draft.recurrenceFrequency != "NONE") Text("Una regla recurrente y un lote finito son modalidades distintas. Desactiva Recurrente para crear varios.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        if (draft.batchEnabled) CreationBatchFields(draft, !isSubmitting)
                    }
                    if (hasUnsavedChanges || draft.technologyEdited || draft.metroEdited) DestructiveAction(if (draft.id == null) "Descartar borrador" else "Descartar cambios", { confirmDiscard = true }, enabled = !isSubmitting && !draft.attachmentBusy)
                    Text(
                        text = if(draft.purpose==NodePurpose.LAYER) "Una capa admite elementos y permanece como capa aunque esté vacía." else if (draft.purpose == NodePurpose.NOTE) "Una nota conserva información. Para añadir hijos, conviértela primero en capa." else if (draft.financialEnabled) "Para convertir en capa, confirma primero quitar los datos de pago." else "Para añadir elementos, convierte la tarea en capa desde ⋮.",
                        color = Arachn0deColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        editingTools = if(pinnedNoteTools) ({ actions -> AttachmentDescriptionTools(draft,!isSubmitting,compact=true,trailingActions=actions) }) else null,
        confirmButton = {
            androidx.compose.material3.Button(
                modifier=if(pinnedNoteTools) Modifier.heightIn(min=48.dp) else Modifier,
                contentPadding=if(pinnedNoteTools) androidx.compose.foundation.layout.PaddingValues(horizontal=12.dp,vertical=8.dp) else androidx.compose.material3.ButtonDefaults.ContentPadding,
                enabled = (draft.id!=null || draft.tagIds.size<=1) && extraValid && specializedCreationValid && !isSubmitting && draft.attachmentsLoaded && !draft.attachmentBusy && imageModeValid && !(draft.batchEnabled && draft.purpose == NodePurpose.ACTION && draft.recurrenceFrequency != "NONE") && batchValid && datesValid && peopleLoaded && financialValid && recurrenceValid && title.trim().isNotEmpty() && TitleLimits.count(title) <= TitleLimits.NODE &&
                    (!editDates || draft.purpose != NodePurpose.ACTION || draft.batchEnabled || draft.activeStart == null || draft.activeDue == null || draft.activeDue!! >= draft.activeStart!!),
                onClick = {
                    val cleanTitle = title.trim()
                    if (!isSubmitting && cleanTitle.isNotEmpty() && TitleLimits.count(cleanTitle) <= TitleLimits.NODE) {
                        if (draft.hadObligation && !draft.financialEnabled && !draft.financialRemovalConfirmed) confirmFinancialRemoval = true
                        else onSave(cleanTitle, description.trim())
                    }
                },
            ) {
                Text(if (isSubmitting) "Guardando..." else if (draft.id == null) "Crear" else "Guardar",maxLines=1)
            }
        },
        dismissButton = {
            TextButton(modifier=if(pinnedNoteTools) Modifier.size(48.dp) else Modifier,
                contentPadding=if(pinnedNoteTools) androidx.compose.foundation.layout.PaddingValues(0.dp) else androidx.compose.material3.ButtonDefaults.TextButtonContentPadding, onClick = {
                if (initialField == null) onDismiss()
                else if (hasUnsavedChanges || draft.technologyEdited || draft.metroEdited) confirmDiscard = true
                else onDiscard()
            }, enabled = !isSubmitting && !draft.attachmentBusy) {
                if(pinnedNoteTools) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Close,if(initialField!=null) "Cancelar" else "Cerrar")
                else Text(if (initialField != null) "Cancelar" else "Cerrar")
            }
        },
    )
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text(if (draft.id == null) "¿Descartar borrador?" else "¿Descartar cambios?") },
        text = { Text("Se eliminarán los cambios de este formulario. El contenido guardado se conserva.") },
        confirmButton = { DestructiveAction(if (draft.id == null) "Descartar borrador" else "Descartar cambios", onDiscard) },
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun ProjectDialog(
    draft: EditorDraft,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDiscard: () -> Unit = onDismiss,
    initialField: String? = null,
) {
    val titleFocus=remember(draft.attachmentDraftId) { FocusRequester() }
    val descriptionFocus=remember(draft.attachmentDraftId) { FocusRequester() }
    var confirmDiscard by rememberSaveable(draft.attachmentDraftId) { mutableStateOf(false) }
    val projectApp=androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication
    var projectPropertiesLoaded by rememberSaveable(draft.attachmentDraftId) {mutableStateOf(projectApp==null)}
    LaunchedEffect(draft.attachmentDraftId) {
        if(!projectPropertiesLoaded && projectApp!=null) {
            val metadata=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val conversion=projectApp.database.conversionDao()
                val root=draft.id?.let {conversion.project(it)}
                Triple(root,root?.let {r->conversion.tags().filter {it.rootId==r.id}.map {it.tagId}}.orEmpty(),root?.let {r->conversion.people().filter {it.rootId==r.id}.map {it.personId}}.orEmpty())
            }
            draft.tagIds=metadata.second;draft.responsibleIds=metadata.third
            draft.priority=metadata.first?.priority?.let {com.r0ybt.arachn0de.domain.model.Priority.valueOf(it)} ?: com.r0ybt.arachn0de.domain.model.Priority.NONE
            projectPropertiesLoaded=true
        }
    }
    SideEffect {draft.projectPropertiesLoaded=projectPropertiesLoaded && projectApp!=null}
    var name by draft::title
    var description by draft::description

    AlertDialog(
        containerColor = Arachn0deColors.Surface,
        titleContentColor = Arachn0deColors.TextPrimary,
        textContentColor = Arachn0deColors.TextSecondary,
        onDismissRequest = if (isSubmitting) ({}) else onDismiss,
        title = { Text(if (draft.id == null) "Nuevo Núcleo" else "Editar proyecto") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val windowFocused=androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
                var initialFocusRequested by remember(draft.attachmentDraftId,initialField) {mutableStateOf(false)}
                LaunchedEffect(windowFocused,draft.attachmentsLoaded,initialField) {
                    if(windowFocused && draft.attachmentsLoaded && !initialFocusRequested && initialField!=null) {
                        if(initialField=="title") titleFocus.requestFocus()
                        else if(initialField=="description") descriptionFocus.requestFocus()
                        initialFocusRequested=true
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = !isSubmitting,
                    label = { Text("Nombre") },
                    supportingText = { Text("${TitleLimits.count(name)} / ${TitleLimits.PROJECT}") },
                    isError = TitleLimits.count(name) > TitleLimits.PROJECT,
                    singleLine = false, minLines = 1, maxLines = 5,
                    modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
                )
                AttachmentDescriptionEditor(draft, !isSubmitting, project = true,
                    focusRequester=descriptionFocus.takeIf {initialField=="description"})
                if(projectApp!=null) {
                    val projectPeople by remember(projectApp) {projectApp.personRepository.observePeople()}.collectAsState(initial=null)
                    val projectTags by remember(projectApp) {projectApp.nodeRepository.tags.observe()}.collectAsState(initial=com.r0ybt.arachn0de.domain.model.TagState())
                    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(),itemVerticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.IconButton(enabled=!isSubmitting && projectPropertiesLoaded && projectPeople!=null,onClick={draft.showResponsible=true},modifier=Modifier.size(48.dp)) {LucideIcon(androidx.compose.material.icons.Icons.Default.Groups,"Responsables (${draft.responsibleIds.size})",tint=if(draft.responsibleIds.isEmpty()) Arachn0deColors.TextSecondary else Arachn0deColors.TextPrimary)}
                        DraftTechnologies(draft,!isSubmitting,project=true,iconOnly=true)
                        TagSelector(projectTags.tags,draft.tagIds.toSet(),{draft.tagIds=it.toList()},repository=projectApp.nodeRepository.tags,enabled=!isSubmitting && projectPropertiesLoaded,iconOnly=true,singleAssignment=true)
                        val prioritized = draft.priority == com.r0ybt.arachn0de.domain.model.Priority.HIGH
                        FilterChip(selected=prioritized,
                            onClick={draft.priority=if(prioritized) com.r0ybt.arachn0de.domain.model.Priority.NONE else com.r0ybt.arachn0de.domain.model.Priority.HIGH},
                            enabled=!isSubmitting && projectPropertiesLoaded,
                            label={Text(if(prioritized) "Prioritario" else "Común")},
                            leadingIcon=if(prioritized) ({LucideIcon(androidx.compose.material.icons.Icons.Default.LocalFireDepartment,"Prioritario")}) else null)
                    }
                    if(draft.showResponsible && projectPeople!=null) ResponsibleDialog(draft.creationId,projectPeople.orEmpty(),projectPeople.orEmpty().filter {it.id in draft.responsibleIds},isSubmitting,{draft.showResponsible=false}) {draft.responsibleIds=it.toList();draft.showResponsible=false}
                }
                if (draft.hasWork) DestructiveAction("Descartar", { confirmDiscard = true }, enabled = !isSubmitting && !draft.attachmentBusy)
            }
        },
        confirmButton = {
            TextButton(
                enabled = projectPropertiesLoaded && (draft.id!=null || draft.tagIds.size<=1) && !isSubmitting && draft.attachmentsLoaded && !draft.attachmentBusy && name.trim().isNotEmpty() && TitleLimits.count(name) <= TitleLimits.PROJECT,
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
    confirmButton: @Composable () -> Unit, dismissButton: @Composable () -> Unit,
    editingTools: (@Composable (@Composable () -> Unit) -> Unit)? = null) {
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
                Column(Modifier.fillMaxWidth()) {
                    AudioBar(inEditor=true)
                    if(editingTools!=null) editingTools {dismissButton();confirmButton()}
                    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        dismissButton(); confirmButton()
                    }
                }
            }
        }
    }
}
