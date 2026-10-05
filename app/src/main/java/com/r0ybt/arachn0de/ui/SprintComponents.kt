package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt
import com.r0ybt.arachn0de.domain.model.WorkState
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.ui.theme.SemanticColors

@Composable
internal fun SprintProgressCard(percentage: Double?) {
    if (percentage == null) {
        Text("Contenedor vacío", color = Arachn0deColors.TextSecondary, fontSize = 12.sp)
        return
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text("Progreso Sprint · ${percentage.roundToInt()} %", color = Arachn0deColors.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { (percentage / 100.0).toFloat() }, modifier = Modifier.fillMaxWidth(),
                color = SemanticColors.SprintDoing, trackColor = Arachn0deColors.ControlSurface)
        }
    }
}

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
        text = { Text(if (enabled) "Las tareas dejarán de tener una fase Sprint. Se conservarán su estado de completado y sus demás atributos."
            else "Las tareas pendientes pasarán a No planificada y las completadas a Terminada. Podrás moverlas entre etapas después. Solo afecta a las tareas directas de esta capa.") },
        confirmButton = { TextButton(enabled = !busy, onClick = onConfirm) { Text(if (enabled) "Desactivar" else "Activar") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}
