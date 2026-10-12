package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

/** Presentation of the existing destination-owned draft; all writes use NodeActions.saveDraft. */
@Composable
internal fun QuickCreateBar(draft: EditorDraft, busy: Boolean, onSave: () -> Unit,
    onCancel: () -> Unit, onFullForm: () -> Unit) {
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
    Surface(color = Arachn0deColors.Surface, modifier = Modifier.fillMaxWidth().testTag("quick-create")) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(draft.title, { if(!busy) draft.title = it },
                    placeholder = { Text(if(task) "Nueva tarea…" else "Nueva nota…") },
                    modifier = Modifier.weight(1f).focusRequester(focus).testTag("quick-create-title"),
                    enabled = !busy, singleLine = false, minLines = 1, maxLines = 3,
                    isError = TitleLimits.count(draft.title) > TitleLimits.NODE,
                    supportingText = if(!financialValid) ({ Text("Completa Pago en Formulario completo.") }) else null)
                IconButton(onClick = { if(valid && !busy) onSave() }, enabled = valid && !busy,
                    modifier = Modifier.size(48.dp).testTag("quick-create-save")) {
                    LucideIcon(Icons.Default.Check, if(busy) "Guardando" else "Guardar elemento")
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextButton(enabled = !busy, onClick = { draft.purpose = if(task) NodePurpose.NOTE else NodePurpose.ACTION }) {
                    LucideIcon(Icons.Default.Description, null, Modifier.size(18.dp));Text(if(task) "Nota" else "Nota ✓")
                }
                TextButton(enabled = !busy && task, onClick = { selector = "due" }) {
                    LucideIcon(Icons.Default.Event, null, Modifier.size(18.dp));Text(if(task && draft.activeDue != null) "Vence ✓" else "Vence")
                }
                TextButton(enabled = !busy && task, onClick = {
                    if(draft.recurrenceFrequency == "NONE") {
                        draft.recurrenceFrequency = draft.latentFrequency
                        if(draft.recurrenceStart.isBlank() && draft.activeDue != null)
                            draft.recurrenceStart = RecurrenceSchedule.format(RecurrenceSchedule.localDay(draft.activeDue!!, java.util.TimeZone.getDefault().id))
                    }
                    selector = "repeat"
                }) {
                    LucideIcon(Icons.Default.Refresh, null, Modifier.size(18.dp));Text(if(task && draft.recurrenceFrequency != "NONE") "Repetir ✓" else "Repetir")
                }
                IconButton(onClick = onFullForm, enabled = !busy, modifier = Modifier.size(48.dp)) {
                    LucideIcon(Icons.Default.Settings, "Formulario completo")
                }
                IconButton(onClick = { keyboard?.hide(); onCancel() }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                    LucideIcon(Icons.Default.Close, "Cancelar creación")
                }
            }
        }
    }
    if(selector == "due") AlertDialog(onDismissRequest = { selector = null },
        title = { Text("Vence") }, text = {
            Column { TaskDatesEditor(draft, !busy, picker, includeStart = false) }
            SideEffect { draft.dueEnabled = draft.dueAt != null }
        }, confirmButton = { TextButton(onClick = { selector = null }) { Text("Listo") } })
    if(selector == "repeat") AlertDialog(onDismissRequest = { selector = null },
        title = { Text("Repetir") }, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                if(draft.recurrenceFrequency != "NONE" && draft.recurrenceStart.isBlank())
                    Text("Elige Comienza para establecer la fecha de repetición.", style = MaterialTheme.typography.bodySmall)
                RecurrenceFields(draft, !busy, initializeStart = false)
            }
        }, confirmButton = { TextButton(onClick = { selector = null }, enabled = recurrenceValid) { Text("Listo") } })
}
