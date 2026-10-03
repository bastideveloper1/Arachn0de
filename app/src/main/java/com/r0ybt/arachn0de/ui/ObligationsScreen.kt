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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.rememberFinancial
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ObligationsScreen(
    tree: NodeTreeSnapshot, projects: List<Project>, people: List<Person>, responsibleByNode: Map<String, List<Person>>,
    loaded: Boolean, now: Long, zoneId: String, onOpen: (Node) -> Unit, onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var periodName by rememberSaveable { mutableStateOf(FinancialPeriod.THIS_MONTH.name) }
    var personId by rememberSaveable { mutableStateOf<String?>(null) }
    var choosePerson by rememberSaveable { mutableStateOf(false) }
    val period = FinancialPeriod.valueOf(periodName)
    val selection = FinancialSelection(period, personId)
    val month = remember(now, zoneId) { CalendarDates.localDay(now, TimeZone.getTimeZone(zoneId)).calendarMonth }
    val financial by rememberFinancial(tree, responsibleByNode, selection, month, zoneId)
    val ready = loaded && financial != null && financial!!.selection == selection && financial!!.periodMonth == period.month(month) && financial!!.zoneId == zoneId
    val selectedPerson = people.firstOrNull { it.id == personId }
    val locale = LocalConfiguration.current.locales[0]
    val periodLabel = remember(period, month, locale) { period.month(month)?.let {
        SimpleDateFormat("MMMM yyyy", locale).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(CalendarDates.labelInstant(it.selecting(1))))
    } ?: "Todo el período" }
    val projectById = remember(projects) { projects.associateBy { it.id } }
    val listState = rememberLazyListState()
    Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Obligaciones", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Volver") }
            }
            ObligationReportActions(if (ready) financial else null, projects, selectedPerson?.name, locale)
            LazyColumn(Modifier.weight(1f).testTag("obligations-list"), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "filters") {
                    Column {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(FinancialPeriod.THIS_MONTH to "Este mes", FinancialPeriod.PREVIOUS_MONTH to "Mes anterior",
                                FinancialPeriod.NEXT_MONTH to "Próximo mes", FinancialPeriod.ALL to "Todo").forEach { (value, label) ->
                                FilterChip(period == value, onClick = { periodName = value.name }, label = { Text(label) })
                            }
                        }
                        Text(periodLabel, Modifier.testTag("financial-period"), fontSize = 14.sp)
                        TextButton(onClick = { choosePerson = true }) { Text("Persona: ${selectedPerson?.name ?: if (personId == null) "Todas" else "Persona eliminada"}") }
                    }
                }
                if (!ready) item(key = "loading") { Text("Cargando Obligaciones…") }
                else {
                    item(key = "summary") { FinancialSummaryCard(financial!!.summary, Modifier.testTag("financial-summary")) }
                    if (financial!!.tasks.isEmpty()) item(key = "empty") { Text("No hay obligaciones para este filtro.", color = Arachn0deColors.TextSecondary) }
                    items(financial!!.tasks, key = { it.id }, contentType = { "obligation" }) { node ->
                        val project = projectById[node.projectId]
                        val path = remember(financial!!.tree, node.id, project?.name) {
                            (listOf(project?.name ?: "Proyecto eliminado") + financial!!.pathTo(node.id).dropLast(1).map { it.title }).joinToString(" › ")
                        }
                        Card(Modifier.fillMaxWidth().testTag("obligation-task:${node.id}").clickable { onOpen(node) },
                            colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(node.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                ObligationIndicator(node)
                                Text(if (node.isCompleted) "Completada" else "Pendiente", fontSize = 12.sp)
                                Text(path, fontSize = 12.sp, color = Arachn0deColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (node.dueAt == null) Text("Sin vencimiento", fontSize = 12.sp, color = Arachn0deColors.TextSecondary)
                                else TaskDateIndicator(node, now)
                                val assigned = financial!!.responsibleByNode[node.id].orEmpty()
                                if (assigned.isNotEmpty()) {
                                    ResponsibleAvatars(assigned)
                                    Text("Responsables: ${assigned.joinToString(" · ") { it.name }}", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (choosePerson) AlertDialog(onDismissRequest = { choosePerson = false }, containerColor = Arachn0deColors.Surface,
        title = { Text("Filtrar por Persona") }, text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                item { TextButton(onClick = { personId = null; choosePerson = false }) { Text("Todas las Personas") } }
                items(people, key = { it.id }) { person -> TextButton(onClick = { personId = person.id; choosePerson = false }) { Text(person.name) } }
            }
        }, confirmButton = { TextButton(onClick = { choosePerson = false }) { Text("Cerrar") } })
}
