package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.ui.theme.SemanticColors

@Composable
internal fun AttentionIndicator(summary: AttentionSummary?) {
    if (summary == null || summary.level == AttentionLevel.NONE) return
    val counts = buildList {
        if (summary.overdue > 0) add("${summary.overdue} ${if (summary.overdue == 1) "vencida" else "vencidas"}")
        if (summary.priorityOnly > 0) add("${summary.priorityOnly} ${if (summary.priorityOnly == 1) "de prioridad alta" else "de prioridad alta"}")
        if (summary.upcoming > 0) add("${summary.upcoming} ${if (summary.upcoming == 1) "próxima" else "próximas"}")
    }.joinToString(" · ")
    Text("Contiene $counts", color = if (summary.level == AttentionLevel.OVERDUE) Arachn0deColors.Destructive else SemanticColors.Attention,
        fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun AttentionScreen(
    attention: AttentionSnapshot?,
    projects: List<Project>,
    responsibleByNode: Map<String, List<Person>>,
    loaded: Boolean,
    onOpen: (Node) -> Unit,
    onBack: () -> Unit,
    tagState: TagState = TagState(),
    people: List<Person> = emptyList(),
) {
    BackHandler(onBack = onBack)
    val projectById = remember(projects) { projects.associateBy { it.id } }
    val filters = rememberSaveable(saver = ScopeFilters.Saver) { ScopeFilters() }
    val filter = NodeFilter(TemporalRanges.resolve(filters.time, attention?.now ?: 0L, attention?.zone ?: java.util.TimeZone.getDefault(), java.util.Locale.getDefault()), filters.person, filters.completion, filters.tag, filters.priority)
    val selection by produceState<Triple<AttentionSnapshot?, NodeFilter, List<Node>>?>(null, attention, filter, responsibleByNode, tagState, projectById) {
        value = null
        val source = attention
        val selected = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            filter.apply(attention?.tasks.orEmpty(), responsibleByNode.mapValues { (_, persons) -> persons.map { it.id }.toSet() }, tagState.nodeIds).filter { it.projectId in projectById }
        }
        value = Triple(source, filter, selected)
    }
    val ready = loaded && attention != null && selection?.first === attention && selection?.second == filter
    val tasks = if (ready) selection?.third.orEmpty() else emptyList()
    if (filters.open) ScopeFiltersDialog(filters, people, tagState.tags)
    val groups = remember(tasks, attention) { tasks.groupBy { attention!!.reasonsByNodeId.getValue(it.id).first() } }
    val listState = rememberLazyListState()
    Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Atención", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = { filters.open = true }) { Text(if (filters.active) "Filtros activos" else "Filtros") }
                TextButton(onClick = onBack) { Text("Volver") }
            }
            if (!ready) Text("Cargando Atención…")
            else if (tasks.isEmpty()) Text("No hay tareas que requieran atención.")
            LazyColumn(Modifier.weight(1f).testTag("attention-list"), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { (reason, nodes) ->
                item(key = "reason:${reason.name}") { Text(when (reason) {
                    AttentionReason.OVERDUE -> "Atrasadas"; AttentionReason.DUE_TODAY -> "Vencen hoy"; AttentionReason.UPCOMING -> "Próximas"
                    AttentionReason.HIGH_PRIORITY -> "Prioridad alta"; AttentionReason.MEDIUM_PRIORITY -> "Prioridad media"
                }, color = SemanticColors.Attention) }
                items(nodes, key = { it.id }) { node ->
                    val project = projectById.getValue(node.projectId)
                    val path = remember(attention!!.tree, node.id, project.name) {
                        (listOf(project.name) + attention.pathTo(node.id).dropLast(1).map { it.title }).joinToString(" › ")
                    }
                    Card(Modifier.fillMaxWidth().testTag("attention-task:${node.id}").clickable { onOpen(node) },
                        colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(node.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(path, color = Arachn0deColors.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(attention.reasonsByNodeId.getValue(node.id).joinToString(" · ", transform = ::attentionReasonLabel),
                                color = SemanticColors.Attention, modifier = Modifier.testTag("attention-reasons:${node.id}"))
                            if (node.effectivePriority == Priority.LOW) PriorityIndicator(node)
                            TagChips(tagState.forNode(node.id))
                            ObligationIndicator(node)
                            TaskDateIndicator(node, attention.now)
                            ResponsibleAvatars(responsibleByNode[node.id].orEmpty())
                        }
                    }
                }
                }
            }
        }
    }
}
