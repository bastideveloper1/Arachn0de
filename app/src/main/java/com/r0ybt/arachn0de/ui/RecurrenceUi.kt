package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
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
internal fun RecurrenceFields(draft: EditorDraft, enabled: Boolean) {
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
                    if (value != "NONE" && draft.recurrenceStart.isBlank()) draft.recurrenceStart = RecurrenceSchedule.format(
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
@Composable
internal fun RecurrenceManager(repository: RecurrenceRepository, projectId: String, nodes: List<Node>, people: List<Person>, peopleLoaded: Boolean, externalRule: String? = null, showLauncher: Boolean = true, onConsumed: () -> Unit = {}) {
    var open by rememberSaveable(projectId) { mutableStateOf(false) }
    var selected by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    LaunchedEffect(externalRule) { if (externalRule != null) { open = true; selected = externalRule.takeIf { it.isNotEmpty() }; onConsumed() } }
    var projects by remember(repository) { mutableStateOf(emptyList<com.r0ybt.arachn0de.data.local.ProjectEntity>()) }
    var rules by remember(repository) { mutableStateOf(emptyList<RecurrenceRuleEntity>()) }
    val projectsLoad = remember(repository) { LoadState() }
    val rulesLoad = remember(repository) { LoadState() }
    LaunchedEffect(repository, projectsLoad.attempt) { projectsLoad.collect(repository.destinationProjects) { projects = it } }
    LaunchedEffect(repository, rulesLoad.attempt) { rulesLoad.collect(repository.rules) { rules = it } }
    val scope = rememberCoroutineScope()
    val operation = remember(repository, scope) { OperationState(scope) }

    if(showLauncher) OutlinedButton(onClick={ open=true }) { Text("Recurrencias") }
    if (open && selected == null) AlertDialog(
        containerColor = Arachn0deColors.Surface, onDismissRequest = { open = false }, title = { Text("Recurrencias") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            val current = rules.filter { projectId.isEmpty() || it.projectId == projectId || projects.none { project -> project.id == it.projectId } }
            if (current.isEmpty()) Text("Crea una regla desde Nuevo elemento → Tarea → Recurrencia.")
            current.forEach { rule -> TextButton(onClick = { selected = rule.id }) { Text("${rule.title} · ${statusLabel(rule.status)}") } }
        } }, confirmButton = { TextButton(onClick = { open = false }) { Text("Cerrar") } })
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
