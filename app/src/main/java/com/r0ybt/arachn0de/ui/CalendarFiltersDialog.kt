package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.CalendarFilterState
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

internal fun completionLabel(value: CompletionFilter): String = when (value) { CompletionFilter.ALL -> "Todos"; CompletionFilter.PENDING -> "Pendientes"; CompletionFilter.COMPLETED -> "Completados" }
internal fun timeFilterLabel(value: TimeFilter): String = when (value) {
    TimeFilter.TODAY -> "Hoy"; TimeFilter.TOMORROW -> "Mañana"; TimeFilter.THIS_WEEK -> "Esta semana"; TimeFilter.NEXT_WEEK -> "Próxima semana"
    TimeFilter.THIS_MONTH -> "Este mes"; TimeFilter.PREVIOUS_MONTH -> "Mes anterior"; TimeFilter.NEXT_MONTH -> "Próximo mes"; TimeFilter.ALL -> "Todo"
}

@Composable
internal fun CalendarFiltersDialog(state: CalendarFilterState, today: CalendarDay, people: List<Person>, peopleLoaded: Boolean) {
    AlertDialog(containerColor = Arachn0deColors.Surface, onDismissRequest = { state.showFilters = false }, title = { Text("Filtros del Calendario") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CalendarFilterMenu("Tiempo", if (state.timePreset == "SELECTED") "Período seleccionado" else timeFilterLabel(TimeFilter.valueOf(state.timePreset)), "calendar-time-filter") {
                TimeFilter.entries.forEach { period -> DropdownMenuItem(text = { Text(timeFilterLabel(period)) }, onClick = { state.preset(period, today); it() }, modifier = Modifier.testTag("calendar-time-option:${period.name}")) }
            }
            CalendarFilterMenu("Persona", state.personId?.let { id -> if (!peopleLoaded) "Cargando personas…" else people.firstOrNull { it.id == id }?.name ?: "Persona ausente" } ?: "Todas", "calendar-person-filter", peopleLoaded) { close ->
                DropdownMenuItem(text = { Text("Todas") }, onClick = { state.personId = null; close() })
                people.forEach { person -> DropdownMenuItem(text = { Text(person.name) }, onClick = { state.personId = person.id; close() }, modifier = Modifier.testTag("calendar-person:${person.id}")) }
            }
            CalendarFilterMenu("Estado", completionLabel(state.completion), "calendar-status-filter") { close ->
                CompletionFilter.entries.forEach { status -> DropdownMenuItem(text = { Text(completionLabel(status)) }, onClick = { state.completion = status; close() }) }
            }
            Text("Los filtros se combinan: tiempo, persona y estado deben coincidir.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { state.personId = null; state.completion = CompletionFilter.ALL }, modifier = Modifier.testTag("calendar-clear-filters")) { Text("Todas las personas · Todos los estados") }
        } }, confirmButton = { TextButton(onClick = { state.showFilters = false }) { Text("Listo") } })
}

@Composable
private fun CalendarFilterMenu(label: String, value: String, tag: String, enabled: Boolean = true, content: @Composable ((() -> Unit)) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text("$label: $value") }
        DropdownMenu(open, { open = false }) { content { open = false } }
    }
}
