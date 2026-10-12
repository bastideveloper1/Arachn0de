package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal data class ManagementToolVisual(val title: String, val description: String, val icon: ImageVector)

internal fun managementToolVisual(kind: String): ManagementToolVisual? = when(kind) {
    "ACTA" -> ManagementToolVisual("Acta de constitución", "Define el propósito, alcance y objetivos del proyecto", Icons.Default.Description)
    "BACKLOG" -> ManagementToolVisual("Backlog", "Organiza las épicas y sus historias", Icons.Default.List)
    "SPRINTS" -> ManagementToolVisual("Sprints", "Planifica los ciclos de trabajo", Icons.Default.DateRange)
    "KANBAN" -> ManagementToolVisual("Kanban", "Visualiza el trabajo y sus fases", Icons.Default.ViewColumn)
    "OBJECTIVES" -> ManagementToolVisual("Objetivos", "Define resultados claros y verificables", Icons.Default.Flag)
    "TEAM" -> ManagementToolVisual("Equipo", "Participantes y responsabilidades del proyecto", Icons.Default.Groups)
    "GANTT" -> ManagementToolVisual("Gantt", "Consulta el cronograma del proyecto", Icons.Default.Timeline)
    else -> null
}

@Composable internal fun ManagementNavigationCard(tool: ManagementToolVisual, onClick: () -> Unit) {
    Card(onClick=onClick, modifier=Modifier.fillMaxWidth().heightIn(min=72.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(tool.icon, contentDescription=null, tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(tool.title, style=MaterialTheme.typography.titleMedium)
                Text(tool.description, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
