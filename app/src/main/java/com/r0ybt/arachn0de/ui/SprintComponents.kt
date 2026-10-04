package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.WorkState
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

@Composable
internal fun SprintSectionHeader(label: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
        contentDescription = "$label, $count ${if (label == "Capas y notas") "elementos" else "tareas"}"
        stateDescription = if (expanded) "Expandida" else "Contraída"
    }) { Text("${if (expanded) "▼" else "▶"} $label · $count", color = Arachn0deColors.TextSecondary) }
}

@Composable
internal fun WorkStateDialog(title: String, current: WorkState, busy: Boolean, onDismiss: () -> Unit, onSelect: (WorkState) -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Cambiar estado · $title") },
        text = { Column { WorkState.entries.forEach { state ->
            TextButton(enabled = !busy, onClick = { onSelect(state) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { selected = state == current }) {
                Text(state.label)
            }
        } } }, confirmButton = {}, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
internal fun SprintModeDialog(enabled: Boolean, busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (enabled) "Desactivar Modo Sprint" else "Activar Modo Sprint") },
        text = { Text(if (enabled) "Las tareas No planificada, Planificada y Haciendo quedarán pendientes. Terminada y Validada quedarán completadas. Se conservarán sus demás atributos."
            else "Las tareas pendientes pasarán a No planificada y las completadas a Terminada. Podrás moverlas entre etapas después. Solo afecta a las tareas directas de esta capa.") },
        confirmButton = { TextButton(enabled = !busy, onClick = onConfirm) { Text(if (enabled) "Desactivar" else "Activar") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}
