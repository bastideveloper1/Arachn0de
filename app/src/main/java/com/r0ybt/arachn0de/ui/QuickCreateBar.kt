package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.domain.model.TitleLimits
import com.r0ybt.arachn0de.domain.model.RecurrenceSchedule
import com.r0ybt.arachn0de.ui.state.EditorDraft

/** Presentation of the existing destination-owned draft; all writes use NodeActions.saveDraft. */
@Composable
internal fun QuickCreateBar(draft: EditorDraft, busy: Boolean, onSave: () -> Unit,
    onCancel: () -> Unit, onFullForm: () -> Unit, onAudio:(()->Unit)?=null) {
    val focus = remember(draft.creationId) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var selector by rememberSaveable(draft.creationId) { mutableStateOf<String?>(null) }
    val picker = rememberSaveable(draft.creationId, saver = TaskDatePickerDraft.Saver) { TaskDatePickerDraft() }
    LaunchedEffect(draft.creationId, selector) {
        if(selector == null) { focus.requestFocus(); keyboard?.show() }
        else keyboard?.hide()
    }
    DisposableEffect(keyboard) { onDispose { keyboard?.hide() } }
    BackHandler(enabled = !busy) { keyboard?.hide(); onCancel() }
    val task = draft.purpose == NodePurpose.ACTION
    val recurrenceValid = !task || draft.recurrenceFrequency == "NONE" ||
        runCatching { draft.recurrenceRule("validation") }.isSuccess
    val financialValid = !task || runCatching { draft.obligation() }.isSuccess
    val valid = draft.title.trim().isNotEmpty() && TitleLimits.count(draft.title) <= TitleLimits.NODE && recurrenceValid &&
        (!task || draft.activeStart == null || draft.activeDue == null || draft.activeDue!! >= draft.activeStart!!) &&
        financialValid
    ModularPanelSurface(Modifier.fillMaxWidth().testTag("quick-create")) {
        ModularPanelLayout(header={},body={
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(draft.title, { if(!busy) draft.title = it },
                    placeholder = { Text(if(task) "Nueva tarea…" else "Nueva nota…") },
                    modifier = modularPanelFieldModifier().weight(1f).focusRequester(focus).testTag("quick-create-title"),
                    enabled = !busy, singleLine = false, minLines = 1, maxLines = 3,
                    isError = TitleLimits.count(draft.title) > TitleLimits.NODE,
                    supportingText = if(!financialValid) ({ Text("Completa Pago en Formulario completo.") }) else null)

            }
        },actions={Column {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {

                IconButton(enabled = !busy, modifier=Modifier.size(48.dp),onClick = { draft.purpose = if(task) NodePurpose.NOTE else NodePurpose.ACTION }) {
                    LucideIcon(Icons.Default.Description, if(task) "Nota" else "Nota activada")
                }
                IconButton(enabled = !busy && task, modifier=Modifier.size(48.dp),onClick = { selector = "due" }) {
                    LucideIcon(Icons.Default.Event, if(task && draft.activeDue != null) "Vence activado" else "Vence")
                }
                IconButton(enabled = !busy && task, modifier=Modifier.size(48.dp),onClick = {
                    if(draft.recurrenceFrequency == "NONE") {
                        draft.recurrenceFrequency = draft.latentFrequency
                        if(draft.recurrenceStart.isBlank() && draft.activeDue != null)
                            draft.recurrenceStart = RecurrenceSchedule.format(RecurrenceSchedule.localDay(draft.activeDue!!, java.util.TimeZone.getDefault().id))
                    }
                    selector = "repeat"
                }) {
                    LucideIcon(Icons.Default.Refresh, if(task && draft.recurrenceFrequency != "NONE") "Repetir activado" else "Repetir")
                }
                AudioMicButton(enabled=!busy && task && financialValid && draft.recurrenceFrequency=="NONE" && !draft.batchEnabled && TitleLimits.count(draft.title)<=TitleLimits.NODE && onAudio!=null) {onAudio?.invoke()}
                IconButton(onClick = onFullForm, enabled = !busy, modifier = Modifier.size(48.dp)) {
                    LucideIcon(Icons.Default.Settings, "Formulario completo")
                }

            }
            ModularPanelActions(cancel={IconButton(onClick = { keyboard?.hide(); onCancel() }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                    LucideIcon(Icons.Default.Close, "Cancelar creación")
                }},confirm={IconButton(onClick = { if(valid && !busy) onSave() }, enabled = valid && !busy,
                    modifier = Modifier.size(48.dp).testTag("quick-create-save")) {
                    LucideIcon(Icons.Default.Check, if(busy) "Guardando" else "Guardar elemento")
                }})
        }})
    }
    if(selector == "due") ModularPanelDialog(onDismissRequest = { selector = null },
        title = { Text("Vence") }, text = {
            Column { TaskDatesEditor(draft, !busy, picker, includeStart = false, openImmediately = true) }
            SideEffect { draft.dueEnabled = draft.dueAt != null }
        }, confirmButton = { TextButton(onClick = { selector = null }) { Text("Listo") } })
    if(selector == "repeat") ModularPanelDialog(onDismissRequest = { selector = null },
        title = { Text("Repetir") }, text = {
            Column {
                if(draft.recurrenceFrequency != "NONE" && draft.recurrenceStart.isBlank())
                    Text("Elige Comienza para establecer la fecha de repetición.", style = MaterialTheme.typography.bodySmall)
                RecurrenceFields(draft, !busy, initializeStart = false)
            }
        }, confirmButton = { TextButton(onClick = { selector = null }, enabled = recurrenceValid) { Text("Listo") } })
}
