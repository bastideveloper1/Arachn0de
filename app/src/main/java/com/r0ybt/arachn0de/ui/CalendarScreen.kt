package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val CalendarDaySaver = listSaver<CalendarDay, Int>(
    save = { listOf(it.year, it.month, it.day) }, restore = { CalendarDay(it[0], it[1], it[2]) },
)

@Composable
internal fun CalendarScreen(
    snapshot: CalendarSnapshot?, projects: List<Project>, responsibleByNode: Map<String, List<Person>>,
    loaded: Boolean, now: Long, zoneId: String, onOpen: (Node) -> Unit, onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember(zoneId) { TimeZone.getTimeZone(zoneId) }
    val today = remember(now, zoneId) { CalendarDates.localDay(now, zone) }
    var selected by rememberSaveable(stateSaver = CalendarDaySaver) { mutableStateOf(today) }
    val month = selected.calendarMonth
    val firstWeekday = remember(locale) { GregorianCalendar(zone, locale).firstDayOfWeek }
    val cells = remember(month, firstWeekday) { month.cells(firstWeekday) }
    val weekdayNames = remember(locale, firstWeekday) {
        val names = DateFormatSymbols(locale).shortWeekdays
        List(7) { names[(firstWeekday - 1 + it) % 7 + 1] }
    }
    val monthFormat = remember(locale) { SimpleDateFormat("MMMM yyyy", locale).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    val dayFormat = remember(locale) { DateFormat.getDateInstance(DateFormat.FULL, locale).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    val timeFormat = remember(locale, zoneId) { DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply { timeZone = zone } }
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val projectById = remember(projects) { projects.associateBy { it.id } }
    val tasks = remember(snapshot, selected, projectById, zoneId) { snapshot?.takeIf { it.zoneId == zoneId }?.tasksOn(selected).orEmpty().filter { it.projectId in projectById } }
    val listState = rememberLazyListState()
    Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Calendario", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Volver") }
            }
            LazyColumn(Modifier.weight(1f).testTag("calendar-list"), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "month", contentType = "month") {
                    Card(colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface)) {
                        Column(Modifier.fillMaxWidth().padding(6.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(enabled = month != CalendarMonth(1,1), onClick = { selected = month.shifted(-1).selecting(selected.day) }) {
                                    Icon(Icons.Default.ChevronLeft, "Mes anterior")
                                }
                                Text(monthFormat.format(Date(CalendarDates.labelInstant(month.selecting(1)))),
                                    Modifier.weight(1f).testTag("calendar-month"), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                IconButton(enabled = month != CalendarMonth(9999,12), onClick = { selected = month.shifted(1).selecting(selected.day) }) {
                                    Icon(Icons.Default.ChevronRight, "Mes siguiente")
                                }
                            }
                            Row(Modifier.fillMaxWidth()) {
                                weekdayNames.forEach { Text(it, Modifier.weight(1f), fontSize = 12.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = Arachn0deColors.TextSecondary) }
                            }
                            cells.chunked(7).forEach { week ->
                                Row(Modifier.fillMaxWidth()) {
                                    week.forEach { day ->
                                        if (day == null) Spacer(Modifier.weight(1f).height(48.dp))
                                        else {
                                            val count = snapshot?.tasksOn(day)?.size ?: 0
                                            val isSelected = day == selected
                                            val shape = RoundedCornerShape(8.dp)
                                            val label = dayFormat.format(Date(CalendarDates.labelInstant(day)))
                                            Column(Modifier.weight(1f).heightIn(min = 48.dp)
                                                .background(if (day == selected) Arachn0deColors.Primary else Arachn0deColors.Surface, shape)
                                                .then(if (day == today) Modifier.border(1.dp, Arachn0deColors.PathHighlight, shape) else Modifier)
                                                .clickable(role = Role.Button) { selected = day }
                                                .testTag("calendar-day:${day.key}").semantics {
                                                    contentDescription = label
                                                    this.selected = isSelected
                                                    stateDescription = listOfNotNull(if (day == today) "Hoy" else null,
                                                        if (count > 0) "$count ${if (count == 1) "tarea" else "tareas"}" else null).joinToString(" · ")
                                                }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center) {
                                                Text(numbers.format(day.day), fontSize = 14.sp, color = Arachn0deColors.TextPrimary)
                                                if (count > 0) Box(Modifier.size(4.dp).background(Arachn0deColors.PathHighlight, RoundedCornerShape(2.dp)))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "selected-day", contentType = "heading") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(dayFormat.format(Date(CalendarDates.labelInstant(selected))), Modifier.weight(1f).testTag("calendar-selected-date"),
                            fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { selected = today }) { Text("Hoy") }
                    }
                }
                if (!loaded || snapshot == null || snapshot.zoneId != zoneId) item(key = "loading") { Text("Cargando Calendario…") }
                else if (tasks.isEmpty()) item(key = "empty") { Text("Sin tareas para este día", color = Arachn0deColors.TextSecondary) }
                items(tasks, key = { it.id }, contentType = { "task" }) { node ->
                    val project = projectById.getValue(node.projectId)
                    val path = remember(snapshot!!.tree, node.id, project.name) {
                        (listOf(project.name) + snapshot.pathTo(node.id).dropLast(1).map { it.title }).joinToString(" › ")
                    }
                    Card(Modifier.fillMaxWidth().testTag("calendar-task:${node.id}").clickable { onOpen(node) },
                        colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(node.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(timeFormat.format(Date(checkNotNull(node.dueAt))), fontSize = 12.sp, color = Arachn0deColors.TextSecondary)
                            Text(path, fontSize = 12.sp, color = Arachn0deColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            ObligationIndicator(node)
                            TaskDateIndicator(node, now)
                            ResponsibleAvatars(responsibleByNode[node.id].orEmpty())
                        }
                    }
                }
            }
        }
    }
}
