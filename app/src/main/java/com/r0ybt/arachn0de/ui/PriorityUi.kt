package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.ui.theme.SemanticColors
import com.r0ybt.arachn0de.ui.theme.ContentTypography

internal fun priorityLabel(priority: Priority): String = if(priority==Priority.HIGH) "Prioritario" else "No prioritario"
@Composable internal fun PrioritySelector(value: Priority?, onChange: (Priority?) -> Unit, filter: Boolean = false, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.testTag("priority-selector")) { Text("Prioridad: ${value?.let(::priorityLabel) ?: "Todas"}") }
        DropdownMenu(open, { open = false }) {
            if (filter) DropdownMenuItem(text = { Text("Todas") }, onClick = { onChange(null); open = false })
            listOf(Priority.NONE,Priority.HIGH).forEach { priority -> DropdownMenuItem(text = { Text(priorityLabel(priority)) }, onClick = { onChange(priority); open = false }, modifier = Modifier.testTag("priority-option:${priority.name}")) }
        }
    }
}
@Composable internal fun PriorityIndicator(node: Node) {
    if(node.effectivePriority==Priority.HIGH) Text("Prioritario",color=com.r0ybt.arachn0de.ui.theme.semanticColor(SemanticColors.HighPriority),fontSize=ContentTypography.Metadata)
}
internal fun attentionReasonLabel(reason: AttentionReason): String = when (reason) {
    AttentionReason.OVERDUE -> "Atrasada"; AttentionReason.DUE_TODAY -> "Vence hoy"; AttentionReason.UPCOMING -> "Vence pronto"
    AttentionReason.HIGH_PRIORITY -> "Prioritario"; AttentionReason.MEDIUM_PRIORITY -> "No prioritario"
}
