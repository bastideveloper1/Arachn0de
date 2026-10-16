package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.rememberFinancial
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ObligationsScreen(
    tree: NodeTreeSnapshot, projects: List<Project>, people: List<Person>, responsibleByNode: Map<String, List<Person>>,
    loaded: Boolean, now: Long, zoneId: String, onOpen: (Node) -> Unit, onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var completedView by remember { mutableStateOf(false) }
    var selectedMonth by remember { mutableStateOf<CalendarMonth?>(null) }
    var selectedPersonId by remember { mutableStateOf<String?>(null) }
    var chooseMonth by remember { mutableStateOf(false) }
    var choosePerson by remember { mutableStateOf(false) }
    val period = FinancialPeriod.ALL
    val selection = FinancialSelection(period, selectedPersonId, selectedMonth)
    val month = remember(now, zoneId) { CalendarDates.localDay(now, TimeZone.getTimeZone(zoneId)).calendarMonth }
    val financial by rememberFinancial(tree, responsibleByNode, selection, month, zoneId)
    val ready = loaded && financial != null && financial!!.selection == selection && financial!!.periodMonth == selectedMonth && financial!!.zoneId == zoneId
    val locale = LocalConfiguration.current.locales[0]
    val selectedPerson = people.firstOrNull {it.id==selectedPersonId}
    val monthLabel = selectedMonth?.let {
        java.text.SimpleDateFormat("MMMM yyyy", locale).apply {timeZone=TimeZone.getTimeZone("UTC")}
            .format(Date(CalendarDates.labelInstant(it.selecting(1))))
    } ?: "Todos los períodos"
    val visible = remember(financial, completedView) {
        financial?.tasks.orEmpty().filter { it.isCompleted == completedView }.sortedWith(
            compareBy<Node> { it.dueAt == null }.thenBy { it.dueAt }
                .thenBy { it.effectivePriority != Priority.HIGH }.thenBy { it.createdAt }.thenBy { it.id })
    }
    val projectById = remember(projects) { projects.associateBy { it.id } }
    val listState = rememberLazyListState()
    Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GlobalChromeActions(leading=true)
                TextButton(onClick = onBack) { Text("Volver") }
                Text("Obligaciones",modifier=Modifier.weight(1f),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                GlobalChromeActions()
            }
            ObligationReportActions(if (ready) financial else null, projects, selectedPerson?.name, locale, completed=completedView)
            LazyColumn(Modifier.weight(1f).testTag("obligations-list"), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "status") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(!completedView, onClick = { completedView = false }, label = { Text("Pendientes") })
                        FilterChip(completedView, onClick = { completedView = true }, label = { Text("Completadas") })
                    }
                }
                item(key = "filters") {
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick={chooseMonth=true}) {Text(monthLabel)}
                        OutlinedButton(onClick={choosePerson=true}) {Text("Persona: ${selectedPerson?.name ?: if(selectedPersonId==null) "Todas" else "Persona eliminada"}")}
                    }
                }
                if (!ready) item(key = "loading") { Text("Cargando Obligaciones…") }
                else {
                    item(key = "summary") {
                        Card(Modifier.fillMaxWidth().testTag("financial-summary"), colors = CardDefaults.cardColors(containerColor = Arachn0deColors.ControlSurface)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Total pendiente", style = MaterialTheme.typography.titleSmall)
                                val totals = financial!!.summary.byCurrency.toSortedMap().filterValues { it.pendingCount > 0 }
                                if (totals.isEmpty()) Text("Sin obligaciones pendientes", style = MaterialTheme.typography.bodySmall)
                                totals.forEach { (currency, amounts) ->
                                    Text(Money.format(amounts.pendingMinor, currency, locale), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    if (visible.isEmpty()) item(key = "empty") { Text(if (completedView) "No hay obligaciones completadas." else "No hay obligaciones pendientes.", color = Arachn0deColors.TextSecondary) }
                    items(visible, key = { it.id }, contentType = { "obligation" }) { node ->
                        val project = projectById[node.projectId]
                        val path = remember(financial!!.tree, node.id, project?.name) {
                            (listOf(project?.name ?: "Proyecto eliminado") + financial!!.pathTo(node.id).dropLast(1).map { it.title }).joinToString(" › ")
                        }
                        Card(Modifier.fillMaxWidth().testTag("obligation-task:${node.id}").clickable { onOpen(node) },
                            colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(node.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                ObligationIndicator(node)
                                if (node.effectivePriority == Priority.HIGH) PriorityIndicator(node)
                                Text(if (node.isCompleted) "Completada" else "Pendiente", fontSize = 12.sp)
                                Text(path, fontSize = 12.sp, color = Arachn0deColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (node.dueAt == null) Text("Sin vencimiento", fontSize = 12.sp, color = Arachn0deColors.TextSecondary)
                                else TaskDateIndicator(node, now)
                                val assigned = financial!!.responsibleByNode[node.id].orEmpty()
                                if (assigned.isNotEmpty()) {
                                    ResponsibleAvatars(assigned,interactive=false)
                                    Text("Responsables: ${assigned.joinToString(" · ") { it.name }}", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if(chooseMonth) {
        val initial=CalendarDates.labelInstant((selectedMonth ?: month).selecting(1))
        val picker=rememberDatePickerState(initialSelectedDateMillis=initial)
        DatePickerDialog(onDismissRequest={chooseMonth=false},
            confirmButton={TextButton(enabled=picker.selectedDateMillis!=null,onClick={
                selectedMonth=picker.selectedDateMillis?.let {CalendarDates.localDay(it,TimeZone.getTimeZone("UTC")).calendarMonth}
                chooseMonth=false
            }) {Text("Seleccionar mes")}},
            dismissButton={Row {
                TextButton(onClick={selectedMonth=null;chooseMonth=false}) {Text("Todos los períodos")}
                TextButton(onClick={chooseMonth=false}) {Text("Cancelar")}
            }}) {
            DatePicker(picker,title={Text("Elige una fecha del mes",Modifier.padding(16.dp))})
        }
    }
    if(choosePerson) AlertDialog(onDismissRequest={choosePerson=false},containerColor=Arachn0deColors.Surface,
        title={Text("Persona responsable")},text={LazyColumn(Modifier.heightIn(max=320.dp)) {
            item {TextButton(onClick={selectedPersonId=null;choosePerson=false}) {Text("Todas las Personas")}}
            items(people,key={it.id}) {person->TextButton(onClick={selectedPersonId=person.id;choosePerson=false}) {Text(person.name)}}
        }},confirmButton={TextButton(onClick={choosePerson=false}) {Text("Cerrar")}})
}
