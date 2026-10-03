package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.domain.model.NodeEvent
import com.r0ybt.arachn0de.domain.model.NodeEventType
import com.r0ybt.arachn0de.ui.state.LoadState
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable internal fun NodeHistoryDialog(node: Node, repository: NodeRepository, onDismiss: () -> Unit) {
    var events by remember(node.id) { mutableStateOf<List<NodeEvent>?>(null) }
    val load = remember(node.id, repository) { LoadState() }
    LaunchedEffect(node.id, repository, load.attempt) {
        load.collect(repository.observeHistory(node.id)) { events = it }
    }
    NodeHistoryContent(node, events, load.failed, load::retry, onDismiss)
}

/** Presentation is read-only. COMPLETED is displayed as Pagada for the current obligation. */
@Composable internal fun NodeHistoryContent(node: Node, events: List<NodeEvent>?, failed: Boolean = false,
    onRetry: () -> Unit = {}, onDismiss: () -> Unit, locale: Locale = Locale.getDefault(), zone: TimeZone = TimeZone.getDefault()) {
    val format = remember(locale, zone.id) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).apply { timeZone = zone } }
    AlertDialog(containerColor = Arachn0deColors.Surface, titleContentColor = Arachn0deColors.TextPrimary,
        textContentColor = Arachn0deColors.TextSecondary, onDismissRequest = onDismiss,
        title = { Text("Historial · ${node.title}", maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        text = { Column {
            when {
                failed -> { Text("No se pudo cargar el historial."); TextButton(onClick = onRetry) { Text("Reintentar") } }
                events == null -> Text("Cargando historial…")
                events.isEmpty() -> Text("Aún no hay eventos registrados.")
                else -> LazyColumn(Modifier.heightIn(max = 360.dp).testTag("node-history-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(events, key = { it.id }) { event -> Column(Modifier.testTag("node-event:${event.id}")) {
                        Text(format.format(Date(event.occurredAt)), style = MaterialTheme.typography.bodySmall)
                        Text(when (event.type) {
                            NodeEventType.CREATED -> "Creada"
                            NodeEventType.COMPLETED -> if (node.obligation != null) "Pagada" else "Completada"
                            NodeEventType.REOPENED -> "Reabierta"
                        }, color = Arachn0deColors.TextPrimary)
                    } }
                }
            }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}
