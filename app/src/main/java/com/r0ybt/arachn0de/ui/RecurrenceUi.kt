package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.local.RecurrenceRuleEntity
import com.r0ybt.arachn0de.data.repository.RecurrenceRepository
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.state.OperationState
import com.r0ybt.arachn0de.ui.state.LoadState
import kotlinx.coroutines.flow.flow
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

@Composable
internal fun RecurrenceFields(draft: EditorDraft, enabled: Boolean, initializeStart: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("NONE" to "Ninguna", "DAILY" to "Diaria", "WEEKLY" to "Semanal", "MONTHLY" to "Mensual", "YEARLY" to "Anual")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            OutlinedButton(enabled = enabled, onClick = { expanded = true }) {
                Text("Recurrencia: ${options.first { it.first == draft.recurrenceFrequency }.second}")
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) }, onClick = {
                    if (value == "NONE") draft.toggleRecurrence(false) else { draft.recurrenceFrequency = value; draft.latentFrequency = value }
                    if (initializeStart && value != "NONE" && draft.recurrenceStart.isBlank()) draft.recurrenceStart = RecurrenceSchedule.format(
                        RecurrenceSchedule.localDay(draft.dueAt ?: System.currentTimeMillis(), java.util.TimeZone.getDefault().id))
                    expanded = false
                }) }
            }
        }
        if (draft.recurrenceFrequency != "NONE") {
            val frequency = RecurrenceFrequency.valueOf(draft.recurrenceFrequency)
            val unit = when(frequency) { RecurrenceFrequency.DAILY -> "día"; RecurrenceFrequency.WEEKLY -> "semana"; RecurrenceFrequency.MONTHLY -> "mes"; RecurrenceFrequency.YEARLY -> "año" }
            OutlinedTextField(draft.recurrenceInterval, { draft.recurrenceInterval = it }, enabled = enabled,
                label = { Text("Cada (cantidad de ${if(unit=="mes") "meses" else unit+"s"})") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            RecurrenceDateButton("Comienza", draft.recurrenceStart, enabled) { draft.recurrenceStart=it }
            RecurrenceDateButton("Termina", draft.recurrenceEnd, enabled) { draft.recurrenceEnd=it }
            if(draft.recurrenceEnd.isNotBlank()) TextButton(enabled=enabled,onClick={ draft.recurrenceEnd="" }) { Text("Sin fecha final") }
            val day = runCatching { RecurrenceSchedule.parse(draft.recurrenceStart) }.getOrNull()
            if(day != null) Text(recurrenceSummary(frequency,draft.recurrenceInterval,day),style=MaterialTheme.typography.bodySmall)
            if(frequency==RecurrenceFrequency.MONTHLY) Text("En meses más cortos se usa el último día. Cambia Comienza para elegir el día mensual.",style=MaterialTheme.typography.bodySmall)
            Text("Vence define la hora (24 h) de las tareas; Inicio conserva su distancia respecto de Vence.",style=MaterialTheme.typography.bodySmall)
            if (draft.startAt != null && draft.dueAt == null) Text("Para repetir una fecha de inicio, configura también Vence.", color = Arachn0deColors.Destructive)
        }
    }
}

/** Rules remain reachable even before the first occurrence or after deleting every Node. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecurrenceManager(repository: RecurrenceRepository, projectId: String, nodes: List<Node>, people: List<Person>, peopleLoaded: Boolean, externalRule: String? = null, showLauncher: Boolean = true, onConsumed: () -> Unit = {}) {
    var open by rememberSaveable(projectId) { mutableStateOf(false) }
    var selected by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    LaunchedEffect(externalRule) { if (externalRule != null) { open = true; selected = externalRule.takeIf { it.isNotEmpty() }; onConsumed() } }
    var projects by remember(repository) { mutableStateOf(emptyList<com.r0ybt.arachn0de.data.local.ProjectEntity>()) }
    var rules by remember(repository) { mutableStateOf(emptyList<RecurrenceRuleEntity>()) }
    var rulesLoaded by remember(repository) { mutableStateOf(false) }
    val projectsLoad = remember(repository) { LoadState() }
    val rulesLoad = remember(repository) { LoadState() }
    LaunchedEffect(repository, projectsLoad.attempt) { projectsLoad.collect(repository.destinationProjects) { projects = it } }
    LaunchedEffect(repository, rulesLoad.attempt) { rulesLoad.collect(repository.rules) { rules = it; rulesLoaded = true } }
    val scope = rememberCoroutineScope()
    val operation = remember(repository, scope) { OperationState(scope) }

    if(showLauncher) OutlinedButton(onClick={ open=true }) { Text("Recurrencias") }
    if (open && selected == null && projectId.isEmpty() && !showLauncher) androidx.compose.ui.window.Dialog(
        onDismissRequest = { open = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background, contentColor = Arachn0deColors.TextPrimary) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Recurrencias", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { open = false }) { Text("Volver") }
                }
                val current = rules.filter { projectId.isEmpty() || it.projectId == projectId || projects.none { project -> project.id == it.projectId } }
                if (!rulesLoaded) Text("Cargando recurrencias…")
                else if (current.isEmpty()) Text("No hay recurrencias.", Modifier.padding(vertical = 12.dp), color = Arachn0deColors.TextSecondary)
                LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("recurrence-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(current, key = { it.id }) { rule ->
                        Card(onClick = { selected = rule.id }, modifier = Modifier.fillMaxWidth().testTag("recurrence-card:${rule.id}"),
                            colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(rule.title, style = MaterialTheme.typography.titleMedium)
                                Text(if (rule.status == "ACTIVE") "Activa" else "Inactiva", style = MaterialTheme.typography.bodyMedium)
                                val frequency = RecurrenceFrequency.valueOf(rule.frequency)
                                Text(recurrenceSummary(frequency, rule.interval.toString(), rule.startDay),
                                    color = Arachn0deColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                if (rule.status != "ACTIVE") Text(statusLabel(rule.status), color = Arachn0deColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
    else if (open && selected == null) ModalBottomSheet(
        onDismissRequest = { open = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Arachn0deColors.Surface,
        contentColor = Arachn0deColors.TextPrimary,
        tonalElevation = 0.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().heightIn(max=maxHeight * .75f).padding(horizontal=16.dp)
                .testTag("recurrence-sheet"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Recurrencias",style=MaterialTheme.typography.titleLarge)
                val current = rules.filter { projectId.isEmpty() || it.projectId == projectId || projects.none { project -> project.id == it.projectId } }
                LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false).testTag("recurrence-list")) {
                    if (current.isEmpty()) item { Text("Crea una regla desde Nuevo elemento → Tarea → Recurrencia.",Modifier.padding(vertical=12.dp)) }
                    items(current,key={it.id}) { rule ->
                        TextButton(onClick = { selected = rule.id },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                            Text("${rule.title} · ${statusLabel(rule.status)}",Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                TextButton(onClick = { open = false },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Cerrar") }
            }
        }
    }
    val editorStates = rememberSaveableStateHolder()
    val rule = rules.firstOrNull { it.id == selected }
    if (open && rule != null) editorStates.SaveableStateProvider(rule.id) { RecurrenceRuleDialog(rule, repository, nodes, people, peopleLoaded, operation, onClearDraft = { editorStates.removeState(rule.id); selected = null }) { selected = null } }
    OperationErrorDialog(operation)
    LoadErrorDialog(projectsLoad)
    LoadErrorDialog(rulesLoad)
}

private fun statusLabel(status: String) = when (status) { "ACTIVE" -> "Activa"; "PAUSED" -> "Pausada"; else -> "Finalizada" }

@Composable
private fun RecurrenceRuleDialog(rule: RecurrenceRuleEntity, repository: RecurrenceRepository, nodes: List<Node>,
    people: List<Person>, peopleLoaded: Boolean, operation: OperationState, onClearDraft: () -> Unit, onDismiss: () -> Unit) {
    var discard by rememberSaveable(rule.id) { mutableStateOf(false) }
    var edit by rememberSaveable(rule.id) { mutableStateOf(false) }
    var confirmRemoval by rememberSaveable(rule.id) { mutableStateOf(false) }
    var delete by rememberSaveable(rule.id) { mutableStateOf(false) }
    var finish by rememberSaveable(rule.id) { mutableStateOf(false) }
    val editorState = rememberSaveable(rule.id, stateSaver = EditorDraft.Saver) { mutableStateOf<EditorDraft?>(EditorDraft(rule.id, rule.parentId, rule.title, rule.description,
        priority = Priority.valueOf(rule.priority), obligation = rule.amountMinor?.let { Obligation(it, checkNotNull(rule.currencyCode)) })) }
    val editor = checkNotNull(editorState.value)
    var destinationProject by rememberSaveable(rule.id) { mutableStateOf(rule.projectId) }
    var projects by remember(repository) { mutableStateOf(emptyList<com.r0ybt.arachn0de.data.local.ProjectEntity>()) }
    val projectsLoad = remember(repository) { LoadState() }
    LaunchedEffect(repository, projectsLoad.attempt) { projectsLoad.collect(repository.destinationProjects) { projects = it } }
    var destinationNodes by remember(rule.id) { mutableStateOf(nodes) }
    val destinationLoad = remember(repository, rule.id) { LoadState() }
    LaunchedEffect(destinationProject, nodes, destinationLoad.attempt) {
        destinationLoad.collect(flow { emit(repository.destinationNodes(destinationProject)) }) { destinationNodes = it }
    }
    var destination by rememberSaveable(rule.id) { mutableStateOf(rule.parentId) }
    var assignmentsLoaded by rememberSaveable(rule.id) { mutableStateOf(false) }
    val assignmentLoad = remember(repository, rule.id) { LoadState() }
    LaunchedEffect(rule.id, assignmentLoad.attempt) { if (!assignmentsLoaded) {
        val initialTagIds = repository.tagIds(rule.id).toList()
        assignmentLoad.collect(flow { emit(repository.people(rule.id).toList()) }) { editor.responsibleIds = it; editor.tagIds = initialTagIds; assignmentsLoaded = true }
    } }
    val saveTemplate: () -> Unit = {
        operation.submit("No se pudo editar la regla. Revisa el destino y los datos.", {
            repository.editTemplate(rule.id, destinationProject, destination, editor.title, editor.description, editor.obligation(), editor.responsibleIds.toSet(), editor.tagIds.toSet(), editor.priority); true
        }, { onClearDraft() })
    }
    AlertDialog(containerColor = Arachn0deColors.Surface,
        onDismissRequest = { if (!operation.busy) onDismiss() }, title = { Text("Regla recurrente") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${rule.title} · ${statusLabel(rule.status)}")
            Text("${recurrenceSummary(RecurrenceFrequency.valueOf(rule.frequency), rule.interval.toString(), rule.startDay)}\nComienza: ${humanRecurrenceDay(rule.startDay)}\nTermina: ${rule.endDay?.let(::humanRecurrenceDay) ?: "Sin fecha final"}")
            Text("Las ocurrencias anteriores se conservan sin cambios. Primero se recuperan los vencimientos activos pendientes. Pausar omite los periodos de la pausa; finalizar es definitivo.", style = MaterialTheme.typography.bodySmall)
            if (edit) TextButton(enabled = !operation.busy, onClick = { discard = true }) { Text("Descartar cambios de plantilla") }
            if (rule.status != "FINISHED") {
                TextButton(enabled = !operation.busy, onClick = { edit = !edit }) { Text("Editar futuras ocurrencias") }
                if (edit) {
                    FormSection("General")
                    OutlinedTextField(editor.title, { editor.title = it }, enabled = !operation.busy, label = { Text("Título futuro") })
                    OutlinedTextField(editor.description, { editor.description = it }, enabled = !operation.busy, label = { Text("Descripción futura") })
                    FormSection("Pago")
                    ObligationFields(editor, !operation.busy)
                    FormSection("Organización")
                    PrioritySelector(editor.priority, { editor.priority = checkNotNull(it) }, enabled = !operation.busy)
                    val tagState by remember(repository) { repository.tags.observe() }.collectAsState(initial = TagState())
                    TagSelector(tagState.tags, editor.tagIds.toSet(), { editor.tagIds = it.toList() }, repository = repository.tags, enabled = !operation.busy)
                    FormSection("Destino")
                    var projectsOpen by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(enabled = !operation.busy, onClick = { projectsOpen = true }) { Text("Proyecto: ${projects.firstOrNull { it.id == destinationProject }?.name ?: "Ausente"}") }
                        DropdownMenu(projectsOpen, { projectsOpen = false }) {
                            projects.forEach { project -> DropdownMenuItem(text = { Text(project.name) }, onClick = { destinationProject = project.id; destination = null; projectsOpen = false }) }
                        }
                    }
                    var destinationsOpen by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(enabled = !operation.busy, onClick = { destinationsOpen = true }) { Text("Destino: ${destinationNodes.firstOrNull { it.id == destination }?.title ?: if (destination == null) "Raíz" else "Ausente"}") }
                        DropdownMenu(destinationsOpen, { destinationsOpen = false }) {
                            DropdownMenuItem(text = { Text("Raíz") }, onClick = { destination = null; destinationsOpen = false })
                            destinationNodes.filter { it.purpose == NodePurpose.LAYER && it.obligation == null }.forEach { node ->
                                DropdownMenuItem(text = { Text(node.title) }, onClick = { destination = node.id; destinationsOpen = false })
                            }
                        }
                    }
                    OutlinedButton(enabled = peopleLoaded && assignmentsLoaded && !operation.busy, onClick = { editor.showResponsible = true }) { Text("Responsables (${editor.responsibleIds.size})") }
                    Button(enabled = !operation.busy && peopleLoaded && assignmentsLoaded, onClick = {
                        if (editor.hadObligation && !editor.financialEnabled && !editor.financialRemovalConfirmed) confirmRemoval = true
                        else saveTemplate()
                    }) { Text("Guardar cambios futuros") }
                }
                TextButton(enabled = !operation.busy, onClick = {
                    operation.submit("No se pudo cambiar el estado de la recurrencia.", {
                        repository.setStatus(rule.id, if (rule.status == "ACTIVE") RecurrenceStatus.PAUSED else RecurrenceStatus.ACTIVE); true
                    })
                }) { Text(if (rule.status == "ACTIVE") "Pausar recurrencia" else "Reanudar desde hoy") }
                TextButton(enabled = !operation.busy, onClick = { finish = true }) { Text("Finalizar recurrencia", color = Arachn0deColors.Destructive) }
            } else {
                TextButton(enabled = !operation.busy, onClick = { delete = true }) { Text("Eliminar", color = Arachn0deColors.Destructive) }
            }
        } }, confirmButton = { TextButton(enabled = !operation.busy, onClick = onDismiss) { Text("Cerrar") } })
    LoadErrorDialog(projectsLoad)
    LoadErrorDialog(destinationLoad)
    LoadErrorDialog(assignmentLoad)
    if (editor.showResponsible && peopleLoaded && assignmentsLoaded) ResponsibleDialog(rule.id, people, people.filter { it.id in editor.responsibleIds }, operation.busy,
        onDismiss = { editor.showResponsible = false }, onSave = { editor.responsibleIds = it.toList(); editor.showResponsible = false })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("¿Descartar cambios de plantilla?") },
        text = { Text("La regla guardada y sus ocurrencias se conservarán.") },
        confirmButton = { TextButton(onClick = onClearDraft) { Text("Descartar cambios") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Continuar editando") } })
    if (confirmRemoval) RemoveObligationDialog(operation.busy, false, onDismiss = { confirmRemoval = false }, onConfirm = {
        editor.financialRemovalConfirmed = true; saveTemplate()
    })
    if (delete) AlertDialog(onDismissRequest = { if (!operation.busy) delete = false }, title = { Text("¿Eliminar recurrencia finalizada?") },
        text = { Text("Se retirará de la gestión. Se conservarán las ocurrencias históricas y las Tareas generadas.") },
        confirmButton = { TextButton(enabled = !operation.busy, onClick = { operation.submit("No se pudo eliminar la recurrencia.", {
            repository.deleteFinished(rule.id); true
        }, { delete = false; onClearDraft() }) }) { Text("Eliminar", color = Arachn0deColors.Destructive) } },
        dismissButton = { TextButton(enabled = !operation.busy, onClick = { delete = false }) { Text("Cancelar") } })
    if (finish) AlertDialog(onDismissRequest = { if (!operation.busy) finish = false }, title = { Text("¿Finalizar recurrencia?") },
        text = { Text("Es definitivo. Se conservan las ocurrencias anteriores y no se generarán nuevas.") },
        confirmButton = { TextButton(enabled = !operation.busy, onClick = { operation.submit("No se pudo finalizar la recurrencia.", {
            repository.setStatus(rule.id, RecurrenceStatus.FINISHED); true
        }, { finish = false }) }) { Text("Finalizar") } }, dismissButton = { TextButton(enabled = !operation.busy, onClick = { finish = false }) { Text("Cancelar") } })
}

internal fun humanRecurrenceDay(day: Long): String = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).apply { timeZone=java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(day*86400000L))

internal fun recurrenceSummary(frequency: RecurrenceFrequency, interval: String, day: Long): String {
    val cal=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis=day*86400000L }
    val unit=when(frequency) { RecurrenceFrequency.DAILY->if(interval=="1") "día" else "días"; RecurrenceFrequency.WEEKLY->if(interval=="1") "semana" else "semanas"; RecurrenceFrequency.MONTHLY->if(interval=="1") "mes" else "meses"; RecurrenceFrequency.YEARLY->if(interval=="1") "año" else "años" }
    val anchor=when(frequency) { RecurrenceFrequency.DAILY->"";RecurrenceFrequency.WEEKLY->", el ${java.text.SimpleDateFormat("EEEE",java.util.Locale.getDefault()).apply { timeZone=cal.timeZone }.format(cal.time)}";RecurrenceFrequency.MONTHLY->", el día ${cal.get(java.util.Calendar.DAY_OF_MONTH)}";RecurrenceFrequency.YEARLY->", el ${java.text.SimpleDateFormat("d MMMM",java.util.Locale.getDefault()).apply { timeZone=cal.timeZone }.format(cal.time)}" }
    return "Cada $interval $unit$anchor"
}

@Composable
private fun RecurrenceDateButton(label:String, value:String, enabled:Boolean, onSelect:(String)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val day=runCatching { RecurrenceSchedule.parse(value) }.getOrNull()
    OutlinedButton(enabled=enabled,onClick={
        val cal=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis=(day ?: RecurrenceSchedule.localDay(System.currentTimeMillis(),java.util.TimeZone.getDefault().id))*86400000L }
        android.app.DatePickerDialog(context,{ _,year,month,date -> onSelect(RecurrenceSchedule.format(RecurrenceSchedule.parse(String.format(java.util.Locale.ROOT,"%04d-%02d-%02d",year,month+1,date)))) },cal.get(java.util.Calendar.YEAR),cal.get(java.util.Calendar.MONTH),cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
    }) { Text("$label: ${day?.let(::humanRecurrenceDay) ?: "Sin fecha final"}") }
}
