package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.domain.model.TaskTemporal
import com.r0ybt.arachn0de.domain.model.TaskTemporalState
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

internal fun formatTaskDate(millis: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

@Composable
internal fun TaskDateIndicator(node: Node, now: Long) {
    if (node.hasChildren || (node.startAt == null && node.dueAt == null)) return
    val state = TaskTemporal.state(node, now)
    val label = when (state) {
        TaskTemporalState.SCHEDULED -> "Programada"
        TaskTemporalState.ACTIVE -> "Activa"
        TaskTemporalState.UPCOMING -> "Próxima"
        TaskTemporalState.OVERDUE -> "Vencida"
        TaskTemporalState.COMPLETED -> "Completada"
        null -> return
    }
    val instant = if (state == TaskTemporalState.SCHEDULED) node.startAt else node.dueAt ?: node.startAt
    val color = when (state) {
        TaskTemporalState.OVERDUE -> Arachn0deColors.Destructive
        TaskTemporalState.UPCOMING -> Arachn0deColors.PathHighlight
        else -> Arachn0deColors.TextSecondary
    }
    Text("$label · ${formatTaskDate(checkNotNull(instant))}", color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Material pickers use UTC calendar dates; combine their components with local wall time. */
internal fun localTaskInstant(utcDay: Long, hour: Int, minute: Int, zone: TimeZone = TimeZone.getDefault()): Long {
    val day = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDay }
    return Calendar.getInstance(zone).apply {
        clear()
        isLenient = false
        set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH), hour, minute)
    }.timeInMillis
}

/** Hoisted above dialog windows so selection also survives restoration of its owner. */
internal class TaskDatePickerDraft {
    var field by mutableStateOf<String?>(null)
    var timeStage by mutableStateOf(false)
    var selectedDay by mutableStateOf<Long?>(null)
    var initialInstant by mutableStateOf(0L)
    var hour by mutableIntStateOf(0)
    var minute by mutableIntStateOf(0)
    var invalidLocalTime by mutableStateOf(false)

    companion object {
        val Saver = listSaver<TaskDatePickerDraft, String>(
            save = { listOf(it.field.orEmpty(), it.timeStage.toString(), it.selectedDay?.toString().orEmpty(), it.initialInstant.toString(), it.hour.toString(), it.minute.toString()) },
            restore = { values -> TaskDatePickerDraft().apply {
                field = values[0].ifEmpty { null }; timeStage = values[1].toBoolean()
                selectedDay = values[2].toLongOrNull(); initialInstant = values[3].toLong()
                hour = values[4].toInt(); minute = values[5].toInt()
            } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDatesEditor(draft: EditorDraft, enabled: Boolean, picker: TaskDatePickerDraft, includeStart: Boolean = true, progressive: Boolean = false) {
    var field by picker::field
    var timeStage by picker::timeStage
    var selectedDay by picker::selectedDay
    var initialInstant by picker::initialInstant
    var invalidLocalTime by picker::invalidLocalTime
    val context = LocalContext.current
    Column {
        (if (includeStart) listOf("Inicio" to draft.startAt, "Vencimiento" to draft.dueAt) else listOf("Vencimiento" to draft.dueAt)).forEach { (label, value) ->
            val active = if (label == "Inicio") draft.startEnabled else draft.dueEnabled
            if (progressive) FormToggle(if (label == "Inicio") "Fecha de inicio" else "Vencimiento", active, enabled) {
                if (label == "Inicio") draft.startEnabled = it else draft.dueEnabled = it
                if (!it && picker.field == label) picker.field = null
            }
            if (!progressive || active) Row(Modifier.fillMaxWidth()) {
                TextButton(enabled = enabled, modifier = Modifier.weight(1f), onClick = {
                    initialInstant = value ?: System.currentTimeMillis()
                    val initial = Calendar.getInstance().apply { timeInMillis = initialInstant }
                    picker.hour = initial.get(Calendar.HOUR_OF_DAY); picker.minute = initial.get(Calendar.MINUTE)
                    timeStage = false; selectedDay = null; invalidLocalTime = false; field = label
                }) { Text("$label: ${value?.let(::formatTaskDate) ?: "Sin fecha"}") }
                if (value != null) TextButton(enabled = enabled, onClick = {
                    if (label == "Inicio") { draft.startAt = null; if (progressive) draft.startEnabled = false } else { draft.dueAt = null; if (progressive) draft.dueEnabled = false }
                }) { Text("Quitar $label") }
            }
        }
    }
    if (field != null) {
        val initial = Calendar.getInstance().apply { timeInMillis = initialInstant }
        if (!timeStage) {
            val utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear(); set(initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis
            val state = rememberDatePickerState(initialSelectedDateMillis = selectedDay ?: utcDate)
            val pickedDay = state.selectedDateMillis
            SideEffect { selectedDay = pickedDay }
            DatePickerDialog(
                onDismissRequest = { field = null },
                confirmButton = { TextButton(enabled = state.selectedDateMillis != null, onClick = { selectedDay = state.selectedDateMillis; timeStage = true }) { Text("Elegir hora") } },
                dismissButton = { TextButton(onClick = { field = null }) { Text("Cancelar") } },
            ) { DatePicker(state, title = { Text("Fecha de $field") }) }
        } else {
            val state = rememberTimePickerState(initialHour = picker.hour, initialMinute = picker.minute, is24Hour = true)
            val pickedHour = state.hour
            val pickedMinute = state.minute
            SideEffect { picker.hour = pickedHour; picker.minute = pickedMinute }
            AlertDialog(
                onDismissRequest = { field = null }, containerColor = Arachn0deColors.Surface,
                title = { Text("Hora de $field (24 h)") },
                text = {
                    Column {
                        TimeInput(state, modifier = Modifier.testTag("task-time-input"))
                        if (invalidLocalTime) Text("Esta hora no existe en la zona horaria local. Elige otra hora.", color = Arachn0deColors.Destructive)
                    }
                },
                confirmButton = { TextButton(onClick = {
                    try {
                        val value = localTaskInstant(checkNotNull(selectedDay), state.hour, state.minute)
                        if (field == "Inicio") draft.startAt = value else draft.dueAt = value
                        field = null
                    } catch (_: IllegalArgumentException) {
                        invalidLocalTime = true
                    }
                }) { Text("Aplicar") } },
                dismissButton = { TextButton(onClick = { field = null }) { Text("Cancelar") } },
            )
        }
    }
}
