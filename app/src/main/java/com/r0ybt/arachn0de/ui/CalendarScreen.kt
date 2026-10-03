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
import com.r0ybt.arachn0de.ui.state.CalendarView
import com.r0ybt.arachn0de.ui.state.CalendarFilterState
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
internal fun CalendarScreen(
    snapshot: CalendarSnapshot?, projects: List<Project>, responsibleByNode: Map<String, List<Person>>,
    loaded: Boolean, now: Long, zoneId: String, onOpen: (Node) -> Unit, onBack: () -> Unit,
    people: List<Person> = emptyList(), peopleLoaded: Boolean = true,
) {
    BackHandler(onBack = onBack)
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember(zoneId) { TimeZone.getTimeZone(zoneId) }
    val today = remember(now, zoneId) { CalendarDates.localDay(now, zone) }
    val state = rememberSaveable(saver = CalendarFilterState.Saver) {
        CalendarFilterState(today)
    }
    val selected = state.anchor(today)
    val month = selected.calendarMonth
    val firstWeekday = remember(locale) { GregorianCalendar(zone, locale).firstDayOfWeek }
    val range = remember(state.view, state.timePreset, selected, today, zoneId, firstWeekday) { state.range(today, zone, firstWeekday) }
    val filter = NodeFilter(range, state.personId, state.completion)
    val filtered by produceState<FilteredCalendar?>(null, snapshot, filter, responsibleByNode) {
        value = null
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            snapshot?.let { source -> FilteredCalendar(source, filter, responsibleByNode.mapValues { (_, persons) -> persons.map { it.id }.toSet() }) }
        }
    }
    val ready = loaded && snapshot?.zoneId == zoneId && filtered?.source === snapshot && filtered?.filter == filter
    val projectById = remember(projects) { projects.associateBy { it.id } }
    val dayFormat = remember(locale) { DateFormat.getDateInstance(DateFormat.FULL, locale).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    val monthFormat = remember(locale) { SimpleDateFormat("MMMM yyyy", locale).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    val timeFormat = remember(locale, zoneId) { DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply { timeZone = zone } }
    val weekDays = remember(selected, firstWeekday) { val start = TemporalRanges.weekStart(selected, firstWeekday); List(7) { TemporalRanges.shift(start, it) } }
    val listState = rememberLazyListState()
    val all = state.timePreset == "ALL"
    val extraTime = state.timePreset in setOf("TOMORROW", "NEXT_WEEK", "PREVIOUS_MONTH", "NEXT_MONTH", "ALL")
    val tasks = remember(filtered, selected, state.view, all, projectById) {
        (if (!all && state.view == CalendarView.MONTH) filtered?.tasksOn(selected) else filtered?.tasks)
            .orEmpty().filter { it.projectId in projectById }
    }
    val groups = remember(tasks, all, state.view, selected, weekDays, zoneId) {
        val byDay = tasks.groupBy { CalendarDates.localDay(checkNotNull(it.dueAt), zone) }
        when {
            all -> byDay.toList()
            state.view == CalendarView.WEEK -> weekDays.map { day -> day to byDay[day].orEmpty() }
            else -> listOf(selected to tasks)
        }
    }
    Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Calendario", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Volver") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((view, label) in listOf(CalendarView.DAY to "Hoy", CalendarView.WEEK to "Semana", CalendarView.MONTH to "Mes")) {
                    FilterChip(selected = !all && state.view == view, onClick = { state.selectView(view, today) }, label = { Text(label) }, modifier = Modifier.testTag("calendar-view:${view.name}"))
                }
                TextButton(onClick = { state.showFilters = true }, modifier = Modifier.testTag("calendar-filters")) {
                    Text(if (state.personId != null || state.completion != CompletionFilter.ALL || extraTime) "Filtros •" else "Filtros")
                }
            }
            if (state.personId != null || state.completion != CompletionFilter.ALL || extraTime) Text(
                listOfNotNull(if (extraTime) timeFilterLabel(TimeFilter.valueOf(state.timePreset)) else null, state.personId?.let { id -> if (!peopleLoaded) "Cargando personas…" else people.firstOrNull { it.id == id }?.name ?: "Persona ausente" },
                    state.completion.takeIf { it != CompletionFilter.ALL }?.let(::completionLabel)).joinToString(" · "),
                fontSize = 12.sp, color = Arachn0deColors.PathHighlight, modifier = Modifier.testTag("calendar-active-filters"))
            LazyColumn(Modifier.weight(1f).testTag("calendar-list"), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!all && state.view == CalendarView.MONTH) item(key = "month", contentType = "month") {
                    CalendarMonthGrid(month, selected, today, firstWeekday, locale, filtered.takeIf { ready }, dayFormat, monthFormat,
                        onPrevious = { state.navigate(-1, today) }, onNext = { state.navigate(1, today) }, onSelect = state::select)
                }
                if (!all && state.view != CalendarView.MONTH) item(key = "period", contentType = "heading") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val week = state.view == CalendarView.WEEK
                        IconButton(enabled = selected.year > 1 || selected.month > 1 || selected.day > if (week) 7 else 1,
                            onClick = { state.navigate(-1, today) }) { Icon(Icons.Default.ChevronLeft, if (week) "Semana anterior" else "Día anterior") }
                        Text(if (week) "${dayFormat.format(Date(CalendarDates.labelInstant(weekDays.first())))} – ${dayFormat.format(Date(CalendarDates.labelInstant(weekDays.last())))}"
                            else dayFormat.format(Date(CalendarDates.labelInstant(selected))), Modifier.weight(1f).testTag("calendar-period"), fontSize = 14.sp)
                        IconButton(enabled = selected.year < 9999 || selected.month < 12 || selected.day < if (week) 25 else 31,
                            onClick = { state.navigate(1, today) }) { Icon(Icons.Default.ChevronRight, if (week) "Semana siguiente" else "Día siguiente") }
                    }
                }
                item(key = "selected-day", contentType = "heading") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (all) "Todo el período" else if (state.view == CalendarView.WEEK) "Elementos de la semana"
                            else dayFormat.format(Date(CalendarDates.labelInstant(selected))), Modifier.weight(1f).testTag("calendar-selected-date"), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { state.current(today) }, modifier = Modifier.testTag("calendar-current-period")) { Text("Actual") }
                    }
                }
                if (!ready) item(key = "loading") { Text("Cargando Calendario…") }
                else if (tasks.isEmpty()) item(key = "empty") {
                    Text(if (state.personId != null || state.completion != CompletionFilter.ALL) "No hay elementos que coincidan con los filtros."
                        else if (state.view == CalendarView.DAY && selected == today && !all) "No hay elementos para hoy."
                        else if (state.view == CalendarView.WEEK && !all) "No hay elementos para esta semana."
                        else if (all) "No hay elementos con vencimiento." else "Sin tareas para este día", color = Arachn0deColors.TextSecondary)
                }
                if (ready) {
                    groups.forEach { (day, dayTasks) ->
                        if (all || state.view == CalendarView.WEEK) item(key = "day:${day.key}", contentType = "heading") {
                            Text(dayFormat.format(Date(CalendarDates.labelInstant(day))), Modifier.testTag("calendar-week-day:${day.key}"), color = Arachn0deColors.PathHighlight, fontSize = 14.sp)
                        }
                        if (dayTasks.isEmpty() && tasks.isNotEmpty()) item(key = "empty:${day.key}") { Text("Sin elementos", fontSize = 12.sp) }
                        items(dayTasks, key = { it.id }, contentType = { "task" }) { node ->
                            val project = projectById.getValue(node.projectId)
                            val path = remember(snapshot!!.tree, node.id, project.name) { (listOf(project.name) + snapshot.pathTo(node.id).dropLast(1).map { it.title }).joinToString(" › ") }
                            Card(Modifier.fillMaxWidth().testTag("calendar-task:${node.id}").clickable { onOpen(node) }, colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface)) {
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
    }
    if (state.showFilters) CalendarFiltersDialog(state, today, people, peopleLoaded)
}

@Composable
private fun CalendarMonthGrid(month: CalendarMonth, selected: CalendarDay, today: CalendarDay, firstWeekday: Int, locale: Locale,
    filtered: FilteredCalendar?, dayFormat: DateFormat, monthFormat: DateFormat, onPrevious: () -> Unit, onNext: () -> Unit, onSelect: (CalendarDay) -> Unit) {
    val cells = remember(month, firstWeekday) { month.cells(firstWeekday) }
    val weekdays = remember(locale, firstWeekday) { val names = DateFormatSymbols(locale).shortWeekdays; List(7) { names[(firstWeekday - 1 + it) % 7 + 1] } }
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    Card(colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface)) {
        Column(Modifier.fillMaxWidth().padding(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = month != CalendarMonth(1,1), onClick = onPrevious) { Icon(Icons.Default.ChevronLeft, "Mes anterior") }
                Text(monthFormat.format(Date(CalendarDates.labelInstant(month.selecting(1)))), Modifier.weight(1f).testTag("calendar-month"), maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(enabled = month != CalendarMonth(9999,12), onClick = onNext) { Icon(Icons.Default.ChevronRight, "Mes siguiente") }
            }
            Row(Modifier.fillMaxWidth()) { weekdays.forEach { Text(it, Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Arachn0deColors.TextSecondary) } }
            cells.chunked(7).forEach { week -> Row(Modifier.fillMaxWidth()) { week.forEach { day ->
                if (day == null) Spacer(Modifier.weight(1f).height(48.dp))
                else {
                    val count = filtered?.tasksOn(day)?.size ?: 0
                    val isSelected = day == selected; val shape = RoundedCornerShape(8.dp)
                    Column(Modifier.weight(1f).heightIn(min = 48.dp).background(if (isSelected) Arachn0deColors.Primary else Arachn0deColors.Surface, shape)
                        .then(if (day == today) Modifier.border(1.dp, Arachn0deColors.PathHighlight, shape) else Modifier)
                        .clickable(role = Role.Button) { onSelect(day) }.testTag("calendar-day:${day.key}").semantics {
                            contentDescription = dayFormat.format(Date(CalendarDates.labelInstant(day))); this.selected = isSelected
                            stateDescription = listOfNotNull(if (day == today) "Hoy" else null, if (count > 0) "$count ${if (count == 1) "tarea" else "tareas"}" else null).joinToString(" · ")
                        }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(numbers.format(day.day), fontSize = 14.sp, color = Arachn0deColors.TextPrimary)
                        if (count > 0) Box(Modifier.size(4.dp).background(Arachn0deColors.PathHighlight, RoundedCornerShape(2.dp)))
                    }
                }
            } } }
        }
    }
}
