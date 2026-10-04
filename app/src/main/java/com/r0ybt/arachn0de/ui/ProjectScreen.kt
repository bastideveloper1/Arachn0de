package com.r0ybt.arachn0de.ui

import kotlinx.coroutines.flow.first
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.History

import androidx.compose.material.icons.filled.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import com.r0ybt.arachn0de.ui.state.NodeSortMode
import com.r0ybt.arachn0de.ui.state.NodeSortPreferences
import com.r0ybt.arachn0de.ui.state.NodePresentationSort
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.ui.state.NodeBatchDraft
import com.r0ybt.arachn0de.ui.state.EditorDraft
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot
import com.r0ybt.arachn0de.domain.model.Project
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.ui.state.LoadState
import com.r0ybt.arachn0de.ui.state.NodeActions

@Composable
internal fun ProjectNodeScreen(
    project: Project,
    nodeRepository: NodeRepository,
    onBackToProjects: () -> Unit,
    personRepository: com.r0ybt.arachn0de.data.repository.PersonRepository = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).personRepository,
    onOpenPeople: () -> Unit = {},
    onOpenAttention: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenObligations: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onBackToObligations: (() -> Unit)? = null,
    onBackToCalendar: (() -> Unit)? = null,
    openNodeId: String? = null,
    onOpenNodeHandled: () -> Unit = {},
    clock: () -> Long = System::currentTimeMillis,
    onOpenProjects: () -> Unit = onBackToProjects,
    sortPreferences: NodeSortPreferences = rememberSaveable(project.id, saver = NodeSortPreferences.Saver) { NodeSortPreferences() },
    projectRepository: com.r0ybt.arachn0de.data.repository.ProjectRepository = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).projectRepository,
) {
    val pendingOpenNode by rememberUpdatedState(openNodeId)
    val handleOpenNode by rememberUpdatedState(onOpenNodeHandled)
    var moveProject by rememberSaveable(project.id) { mutableStateOf(false) }
    if(moveProject) ProjectMoveDialog(projectRepository,project,{ moveProject=false },{ moveProject=false;onBackToProjects() })
    val scope = rememberCoroutineScope()
    val personActions = remember(personRepository, scope) { com.r0ybt.arachn0de.ui.state.PersonActions(personRepository, scope) }
    var people by remember { mutableStateOf(emptyList<com.r0ybt.arachn0de.domain.model.Person>()) }
    var responsibleByNode by remember(project.id) { mutableStateOf(emptyMap<String, List<com.r0ybt.arachn0de.domain.model.Person>>()) }
    var peopleLoaded by remember { mutableStateOf(false) }
    var assignmentsLoaded by remember(project.id) { mutableStateOf(false) }
    var responsibleNodeId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    val peopleLoad = remember(personRepository) { LoadState() }
    val assignmentsLoad = remember(personRepository, project.id) { LoadState() }
    LaunchedEffect(personRepository, peopleLoad.attempt) {
        peopleLoad.collect(personRepository.observePeople()) { people = it; peopleLoaded = true }
    }
    LaunchedEffect(personRepository, project.id, assignmentsLoad.attempt) {
        assignmentsLoad.collect(personRepository.observeAssignments(project.id)) { responsibleByNode = it; assignmentsLoaded = true }
    }
    val layerScrollStates = rememberSaveableStateHolder()
    val navigatorListState = rememberLazyListState()
    val currentPath = rememberSaveable(project.id, saver = listSaver<SnapshotStateList<String>, String>(
        save = { it.toList() },
        restore = { it.toMutableStateList() },
    )) { mutableStateListOf<String>() }
    var hasLoaded by remember(project.id) { mutableStateOf(false) }
    var projectState by remember(project.id) { mutableStateOf(NodeTreeSnapshot(emptyList())) }
    val drafts = rememberSaveable(project.id, saver = com.r0ybt.arachn0de.ui.state.EditorDraftStore.Saver) { com.r0ybt.arachn0de.ui.state.EditorDraftStore() }
    val draft = drafts.active
    var convertingPurpose by remember { mutableStateOf(NodePurpose.NOTE) }
    var convertingObligationId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var deletingNodeId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var deletingNodeName by rememberSaveable(project.id) { mutableStateOf("") }
    var movingNodeId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var showDrawer by remember { mutableStateOf(false) }
    var showNavigator by rememberSaveable(project.id) { mutableStateOf(false) }
    var expandedLayerIds by rememberSaveable(
        project.id,
        stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it.toList() }),
    ) { mutableStateOf(emptyList<String>()) }
    var recurrenceSelected by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var recurrenceReceipts by remember(nodeRepository) { mutableStateOf(emptyList<com.r0ybt.arachn0de.data.local.RecurrenceOccurrenceEntity>()) }
    val recurrenceLoad = remember(nodeRepository) { LoadState() }
    LaunchedEffect(nodeRepository, recurrenceLoad.attempt) { recurrenceLoad.collect(nodeRepository.recurrence.occurrences) { recurrenceReceipts = it } }
    val recurrenceByNode = remember(recurrenceReceipts) { recurrenceReceipts.associate { it.nodeId to it.ruleId } }
    val actions = remember(nodeRepository, scope) { NodeActions(nodeRepository, scope) }
    val copyContext = androidx.compose.ui.platform.LocalContext.current
    val copyActions = remember(copyContext, scope) { com.r0ybt.arachn0de.ui.state.NodeCopyActions(copyContext, scope) { personRepository.observeAssignments(project.id).first() } }
    val defaultsOperation = remember(scope) { com.r0ybt.arachn0de.ui.state.OperationState(scope) }
    val isSubmittingNode = actions.operation.busy || defaultsOperation.busy
    val currentNodeId = currentPath.lastOrNull()
    var showDefaults by rememberSaveable(currentNodeId) { mutableStateOf(false) }
    var showContextActions by remember(currentNodeId) { mutableStateOf(false) }
    val currentNode = projectState.nodesById[currentNodeId]
    val sprintScope = currentNode?.sprintMode == true
    var showSprintMode by rememberSaveable(currentNodeId) { mutableStateOf(false) }
    var changingWorkStateId by rememberSaveable { mutableStateOf<String?>(null) }
    var collapsedSprintSections by rememberSaveable(currentNodeId) { mutableStateOf(listOf<String>()) }
    var historyNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    historyNodeId?.let { id -> projectState.nodesById[id]?.let { node -> NodeHistoryDialog(node, nodeRepository) { historyNodeId = null } } }
    val tagState by remember(nodeRepository) { nodeRepository.tags.observe() }.collectAsState(initial = com.r0ybt.arachn0de.domain.model.TagState())
    val now = com.r0ybt.arachn0de.ui.state.rememberTaskScreenNow(projectState.nodes, clock)
    val scopeFilterStore = rememberSaveable(project.id, saver = ScopeFilterStore.Saver) { ScopeFilterStore() }
    val scopeFilters = scopeFilterStore.scope(currentNodeId ?: "project-root")
    val scopedFilter = com.r0ybt.arachn0de.domain.model.NodeFilter(
        com.r0ybt.arachn0de.domain.model.TemporalRanges.resolve(scopeFilters.time, now, java.util.TimeZone.getDefault(), java.util.Locale.getDefault()),
        scopeFilters.person, scopeFilters.completion, scopeFilters.tag, scopeFilters.priority)
    val completedAt by remember(nodeRepository, project.id) { nodeRepository.observeCompletionTimes(project.id) }.collectAsState(initial = emptyMap())
    val sortContext = "${project.id}:${currentNodeId ?: "project-root"}"
    val sortMode = sortPreferences.mode(sortContext)
    var showSortMenu by remember(currentNodeId) { mutableStateOf(false) }
    val filterActive = scopeFilters.active
    val automaticNodes by androidx.compose.runtime.produceState<List<com.r0ybt.arachn0de.domain.model.Node>?>(null, projectState, currentNodeId, sortMode) {
        value = null
        if (sortMode == NodeSortMode.MANUAL) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            NodePresentationSort.children(projectState.nodes.filter { it.projectId == project.id && it.parentId == currentNodeId }, sortMode)
        }
    }
    // Manual keeps the existing coherent projection, without a new asynchronous loading phase.
    val currentNodes = if (sortMode == NodeSortMode.MANUAL) remember(projectState, currentNodeId, completedAt) {
        NodePresentationSort.children(projectState.childrenOf(currentNodeId), NodeSortMode.MANUAL, completedAt)
    } else automaticNodes
    val filteredRows by androidx.compose.runtime.produceState<List<com.r0ybt.arachn0de.domain.model.FilteredNodeRow>?>(null, projectState, currentNodeId, scopedFilter, sortMode, tagState, responsibleByNode, completedAt) {
        value = null
        if (!filterActive) { value = emptyList(); return@produceState }
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val rows = com.r0ybt.arachn0de.domain.model.ScopedNodeFilter.apply(projectState, project.id, currentNodeId, scopedFilter,
                responsibleByNode.mapValues { (_, people) -> people.map { it.id }.toSet() }, tagState.nodeIds)
            NodePresentationSort.filtered(rows, sortMode, completedAt)
        }
    }
    val filteredIds = remember(filteredRows) { filteredRows.orEmpty().mapTo(hashSetOf()) { it.node.id } }
    if (scopeFilters.open) ScopeFiltersDialog(scopeFilters, people, tagState.tags)
    if (showSortMenu) NodeSortMenu(sortMode, { sortPreferences.set(sortContext, it) }, { showSortMenu = false })
    val selection = remember(project.id, currentNodeId, scopedFilter) { com.r0ybt.arachn0de.ui.state.NodeSelection() }
    val visibleSelectionIds = if(scopeFilters.active) filteredRows.orEmpty().map { it.node.id }.toSet() else currentNodes.orEmpty().map { it.id }.toSet()
    LaunchedEffect(visibleSelectionIds, filteredRows != null, currentNodes != null) { if((scopeFilters.active && filteredRows != null) || (!scopeFilters.active && currentNodes != null)) selection.retain(visibleSelectionIds) }
    var bulkDelete by remember(selection) { mutableStateOf(false) }
    var bulkMove by remember(selection) { mutableStateOf(false) }
    var groupReview by remember { mutableStateOf<List<com.r0ybt.arachn0de.domain.model.Node>?>(null) }

    LaunchedEffect(draft?.creationId) { groupReview = null }

    val currentProgress = projectState.progressById[currentNodeId]
    val attention by com.r0ybt.arachn0de.ui.state.rememberAttention(projectState, now)
    val currentMonth = remember(now) { com.r0ybt.arachn0de.domain.model.CalendarDates.localDay(now, java.util.TimeZone.getDefault()).calendarMonth }
    val financial by com.r0ybt.arachn0de.ui.state.rememberFinancial(projectState, responsibleByNode,
        com.r0ybt.arachn0de.domain.model.FinancialSelection(com.r0ybt.arachn0de.domain.model.FinancialPeriod.ALL), currentMonth, java.util.TimeZone.getDefault().id)
    val pathNodes = currentPath.mapNotNull { projectState.nodesById[it] }
    val currentLayer = pathNodes.size
    val progressMap = projectState.progressById

    val load = remember(project.id, nodeRepository) { LoadState() }
    LaunchedEffect(project.id, nodeRepository, load.attempt) {
        load.collect(nodeRepository.observePreparedProjectState(project.id)) { snapshot ->
            pendingOpenNode?.let { target ->
                currentPath.clear()
                if (snapshot.nodesById[target]?.projectId == project.id) currentPath.add(target)
                handleOpenNode()
            }
            // Preserve the open node when its ancestry changes, using only confirmed data.
            val openId = currentPath.lastOrNull()
            if (openId != null && openId in snapshot.nodesById) {
                val updatedPath = mutableListOf<String>()
                var cursor: String? = openId
                while (cursor != null) {
                    updatedPath.add(cursor)
                    cursor = snapshot.nodesById.getValue(cursor).parentId
                }
                currentPath.clear()
                currentPath.addAll(updatedPath.asReversed())
            }
            // Validate only against a persisted emission, never the initial empty snapshot.
            var parentId: String? = null
            val validPath = currentPath.takeWhile { id ->
                val node = snapshot.nodesById[id]
                (node != null && node.projectId == project.id && node.parentId == parentId).also {
                    if (it) parentId = id
                }
            }
            while (currentPath.size > validPath.size) currentPath.removeAt(currentPath.lastIndex)
            expandedLayerIds = expandedLayerIds.filter { it in snapshot.nodesById }
            projectState = snapshot
            hasLoaded = true
        }
    }

    BackHandler {
        if (onBackToObligations != null) onBackToObligations()
        else if (onBackToCalendar != null) onBackToCalendar()
        else if (currentPath.isNotEmpty()) {
            currentPath.removeAt(currentPath.lastIndex)
        } else {
            onBackToProjects()
        }
    }

    BackHandler(enabled = selection.ids.isNotEmpty()) { selection.clear() }
    BackHandler(enabled = showDrawer) { showDrawer = false }

    if (!hasLoaded) {
        Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {}
        LoadErrorDialog(load)
        return
    }

    OperationErrorDialog(defaultsOperation)
    val background = Brush.linearGradient(
        listOf(
            Arachn0deColors.Background,
            Arachn0deColors.BackgroundMiddle,
            Arachn0deColors.BackgroundEnd,
        ),
    )

    Surface(modifier = Modifier.fillMaxSize(), color = Arachn0deColors.Background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if(showDrawer) Modifier.clearAndSetSemantics {} else Modifier)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                HeaderBar(onMenuClick = { showDrawer = true }, title = project.name)
                if(selection.ids.isNotEmpty()) Column(Modifier.fillMaxWidth()) {
                    Text("${selection.ids.size} seleccionados", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryAction("Mover", { bulkMove = true }, enabled = !isSubmittingNode)
                        DestructiveAction("Eliminar", { bulkDelete = true }, enabled = !isSubmittingNode)
                        TextButton(onClick = { selection.clear() }) { Text("Salir") }
                    }
                }

                layerScrollStates.SaveableStateProvider(currentNodeId?.let { "node:$it" } ?: "project-root") {
                    val nodeListState = rememberLazyListState()
                    val groups = listOf(false, true).associateWith { completed ->
                        currentNodes.orEmpty().filter { it.projectId == project.id && it.parentId == currentNodeId && it.isCompleted == completed }.map { it.id }
                    }
                    val drag = rememberDragReorderState(nodeListState, "node:", groups, sprintScope || isSubmittingNode || selection.ids.isNotEmpty() || filterActive || sortMode != NodeSortMode.MANUAL || currentNodes == null, actions.operation.error, "$currentNodeId:${sortMode.name}:$filterActive") { source, target, _ ->
                        if (sortMode == NodeSortMode.MANUAL && !filterActive) actions.reorderTo(source, currentNodeId, target)
                    }
                    LazyColumn(
                        state = nodeListState,
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("nodes-list").then(drag.gestureModifier),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "layer-context", contentType = "context") {
                            Column {
                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (currentPath.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                if (onBackToObligations != null) onBackToObligations()
                                                else if (onBackToCalendar != null) onBackToCalendar()
                                                else if (currentPath.isNotEmpty()) currentPath.removeAt(currentPath.lastIndex)
                                            },
                                            modifier = Modifier.size(48.dp),
                                        ) {
                                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = if (onBackToObligations != null) "Volver a Obligaciones" else if (onBackToCalendar != null) "Volver al Calendario" else "Volver a la capa anterior", tint = Arachn0deColors.Accent)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = currentNode?.title ?: "Proyecto raíz",
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Arachn0deColors.TextPrimary,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(48.dp).semantics { stateDescription="Orden: ${sortMode.label}" + if(sortMode!=NodeSortMode.MANUAL) "; arrastre deshabilitado" else "" }) {
                                        Icon(Icons.AutoMirrored.Filled.Sort, "Ordenar")
                                    }
                                    IconButton(onClick = { showContextActions = true }, modifier = Modifier.size(48.dp)) {
                                        Icon(androidx.compose.material.icons.Icons.Default.MoreVert,
                                            contentDescription = if(currentNode == null) "Opciones del proyecto" else "Opciones del elemento")
                                    }
                                    androidx.compose.material3.IconButton(onClick = { scopeFilters.open = true }, modifier = Modifier.size(48.dp)) {
                                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.FilterList,
                                            contentDescription = if (scopeFilters.active) "Filtros activos" else "Filtros",
                                            tint = if (scopeFilters.active) Arachn0deColors.Accent else Arachn0deColors.TextSecondary)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (currentNode != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "CAPA $currentLayer",
                                            color = Arachn0deColors.TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )

                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (!currentNode?.description.isNullOrBlank()) {
                                    androidx.compose.material3.Card(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = androidx.compose.material3.CardDefaults.cardColors(
                                            containerColor = Arachn0deColors.ControlSurface,
                                        ),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        ) {
                                            Text(
                                                text = "DESCRIPCIÓN",
                                                color = Arachn0deColors.TextSecondary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = 0.08.sp,
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = currentNode!!.description,
                                                color = Arachn0deColors.TextPrimary,
                                                fontSize = 14.sp,
                                                lineHeight = 20.sp,
                                            )
                                        }
                                    }
                                }

                                AttentionIndicator(if (currentNode == null) attention?.byProjectId?.get(project.id) else if (currentNode.isStructural) attention?.byNodeId?.get(currentNode.id) else null)
                                if (currentNode == null || currentNode.isStructural) FinancialSummaryCard(
                                    if (currentNode == null) financial?.byProjectId?.get(project.id) else financial?.byNodeId?.get(currentNode.id), title = "Obligaciones · Todo el período")
                                if (currentNode != null && currentNode.isStructural && currentProgress != null) {
                                    NodeProgressCard(progress = currentProgress!!)
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                if (currentNode != null) {
                                    androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if(currentNode.workState != null) SecondaryAction("Estado: ${currentNode.workState.label}", { changingWorkStateId = currentNode.id }, enabled = !isSubmittingNode)
                                        else if(currentNode.isCompletable) SecondaryAction(if(currentNode.isCompleted) "Reabrir" else "Completar",
                                            { actions.setCompleted(currentNode.id,!currentNode.isCompleted) },enabled = !isSubmittingNode)
                                    }
                                    if (currentNode.purpose == NodePurpose.ACTION && currentNode.canReceiveChildren) Text("Al añadir un elemento, esta tarea se convierte en capa.", color = Arachn0deColors.TextSecondary)
                                    if (currentNode.purpose == NodePurpose.NOTE) Text("Nota · Convierte en capa para añadir hijos", color = Arachn0deColors.TextSecondary)
                                    PriorityIndicator(currentNode)
                                    ObligationIndicator(currentNode)
                                    if (currentNode.obligation != null) Text("Convierte esta obligación en una tarea antes de usarla como capa.", color = Arachn0deColors.TextSecondary)
                                    TaskDateIndicator(currentNode, now)
                                    TagChips(tagState.forNode(currentNode.id))
                                    ResponsibleAvatars(responsibleByNode[currentNode.id].orEmpty())
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                            }
                        }
                        if (scopeFilters.active && !sprintScope) {
                            if (filteredRows == null) item { Text("Cargando resultados…") }
                            else if (filteredRows!!.none { it.isMatch }) item { Text("Sin coincidencias") }
                            items(filteredRows.orEmpty(), key = { "filtered:${it.node.id}" }) { row ->
                                Column(Modifier.padding(start = minOf(row.depth, 6).times(12).dp).semantics { selected = row.node.id in selection.ids; stateDescription = if(row.node.id in selection.ids) "Seleccionado" else "Sin seleccionar" }) {
                                    SecondaryAction(if(row.node.id in selection.ids) "✓ Seleccionado" else "Seleccionar", { selection.toggle(row.node.id) }, enabled = !isSubmittingNode)
                                    if (!row.isMatch) Text("Contexto · ${row.node.title}", color = Arachn0deColors.TextSecondary)
                                    else {
                                        TextButton(onClick = { if(selection.ids.isNotEmpty()) selection.toggle(row.node.id) else actions.navigate(row.node.id) { path -> currentPath.clear(); currentPath.addAll(path) } }) { Text(row.node.title) }
                                        PriorityIndicator(row.node)
                                        TagChips(tagState.forNode(row.node.id))
                                        TextButton(onClick = { historyNodeId = row.node.id }) { Text("Historial") }
                                        TextButton(onClick = { if (assignmentsLoaded && peopleLoaded) drafts.open(row.node.parentId, row.node.id) { EditorDraft(row.node.id, row.node.parentId, row.node.title, row.node.description, startAt = row.node.startAt, dueAt = row.node.dueAt, purpose = row.node.purpose, obligation = row.node.obligation, priority = row.node.priority).apply { tagIds = tagState.nodeIds[row.node.id].orEmpty().toList(); responsibleIds = responsibleByNode[row.node.id].orEmpty().map { it.id }; creationGroupId = row.node.creationGroupId; captureSharedBaseline() } } }) { Text("Editar") }
                                    }
                                }
                            }
                        } else {
                        if (currentNodes == null) item { Text("Cargando orden…") }
                        else if (currentNodes.orEmpty().isEmpty()) {
                            item(key = "empty-layer", contentType = "empty") { if (currentNode == null || currentNode.isStructural) EmptyLayerState() }
                        }
                        if (sprintScope && filterActive) {
                            if (filteredRows == null) item { Text("Cargando resultados…") }
                            else if (filteredRows!!.none { it.isMatch }) item { Text("Sin coincidencias") }
                        }
                        val sectionKeys = if (sprintScope) listOf("structure") + com.r0ybt.arachn0de.domain.model.WorkState.entries.map { it.name } else listOf("pending", "completed")
                        for (sectionKey in sectionKeys) {
                            val completedGroup = sectionKey == "completed"
                            val groupNodes = currentNodes.orEmpty().filter { node ->
                                if (!sprintScope) node.isCompleted == completedGroup else
                                    (if (sectionKey == "structure") node.purpose != NodePurpose.ACTION else node.workState?.name == sectionKey) &&
                                    (!filterActive || node.id in filteredIds)
                            }
                            val expanded = sectionKey !in collapsedSprintSections
                            val renderNodes = if (sprintScope) { if (expanded) groupNodes else emptyList() } else drag.orderFor(completedGroup, groupNodes.map { it.id })
                                .mapNotNull { id -> projectState.nodesById[id] }
                            if (sprintScope && (sectionKey != "structure" || groupNodes.isNotEmpty())) {
                                item(key = "sprint-section:$sectionKey", contentType = "section") {
                                    val label = if (sectionKey == "structure") "Capas y notas" else com.r0ybt.arachn0de.domain.model.WorkState.valueOf(sectionKey).label
                                    SprintSectionHeader(label, groupNodes.size, expanded) {
                                        collapsedSprintSections = if (expanded) collapsedSprintSections + sectionKey else collapsedSprintSections - sectionKey
                                    }
                                }
                            } else if (groupNodes.isNotEmpty()) {
                                item(key = "section:$completedGroup", contentType = "section") {
                                    Column {
                                        if (completedGroup) androidx.compose.material3.HorizontalDivider(color = Arachn0deColors.Outline)
                                        Text(if (completedGroup) "Completadas" else "Disponibles", color = Arachn0deColors.TextSecondary,
                                            fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                                    }
                                }
                            }
                            items(renderNodes, key = { "node:${it.id}" }, contentType = { "node" }) { node ->
                                val dragging = drag.isDragging(node.id)
                                NodeCard(
                                    onAdvanceWorkState = node.workState?.let { ({ actions.advanceWorkState(node.id) }) },
                                    onChangeWorkState = node.workState?.let { ({ changingWorkStateId = node.id }) },
                                    stateBusy = isSubmittingNode,
                                    onHistory = { historyNodeId = node.id },
                                    tags = tagState.forNode(node.id),
                                    node = node,
                                    onRecurrence = recurrenceByNode[node.id]?.let { ruleId -> ({ recurrenceSelected = ruleId }) },
                                    progress = progressMap[node.id],
                                    hasChildren = node.hasChildren,
                                    onMakeLayer = { if(node.obligation!=null) { convertingPurpose=NodePurpose.LAYER;convertingObligationId=node.id } else actions.convert(node.id,NodePurpose.LAYER) },
                                    onConvert = { done -> if (node.obligation != null) { convertingPurpose=NodePurpose.NOTE;convertingObligationId = node.id; done() } else actions.convert(node.id, if (node.purpose != NodePurpose.ACTION) NodePurpose.ACTION else NodePurpose.NOTE, onSuccess = done) },
                                    canToggleComplete = node.isCompletable,
                                    onOpen = {
                                        currentPath.add(node.id)
                                    },
                                    responsiblePeople = responsibleByNode[node.id].orEmpty(),
                                    now = now,
                                    attention = attention?.byNodeId?.get(node.id),
                                    onResponsible = { if (!isSubmittingNode && !personActions.operation.busy && peopleLoaded && assignmentsLoaded) responsibleNodeId = node.id },
                                    selected = node.id in selection.ids,
                                    selecting = selection.ids.isNotEmpty(),
                                    onSelect = { if(!isSubmittingNode) selection.toggle(node.id) },
                                    onMove = { if (!isSubmittingNode) movingNodeId = node.id },
                                    canCopy = !copyActions.busy,
                                    onCopy = { descendants -> copyActions.copy(projectState, node.id, descendants) },
                                    onEdit = {
                                        if (assignmentsLoaded && peopleLoaded) drafts.open(node.parentId, node.id) { EditorDraft(node.id, node.parentId, node.title, node.description, startAt = node.startAt, dueAt = node.dueAt, purpose = node.purpose, obligation = node.obligation, priority = node.priority).apply { tagIds = tagState.nodeIds[node.id].orEmpty().toList(); responsibleIds = responsibleByNode[node.id].orEmpty().map { it.id }; creationGroupId = node.creationGroupId; captureSharedBaseline() } }
                                    },
                                    canMoveUp = !sprintScope && node.id != renderNodes.firstOrNull()?.id && !isSubmittingNode && sortMode == NodeSortMode.MANUAL,
                                    canMoveDown = !sprintScope && node.id != renderNodes.lastOrNull()?.id && !isSubmittingNode && sortMode == NodeSortMode.MANUAL,
                                    onReorder = { moveUp, onSuccess ->
                                        if (sortMode == NodeSortMode.MANUAL) actions.reorder(node.id, node.parentId, moveUp, onSuccess)
                                    },
                                    onDelete = {
                                        deletingNodeId = node.id
                                        deletingNodeName = node.title
                                    },
                                    onToggleComplete = {
                                        if (node.workState != null) actions.advanceWorkState(node.id) else if (node.isCompletable) actions.setCompleted(node.id, !node.isCompleted)
                                    },
                                    dragging = dragging,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = null, fadeOutSpec = null,
                                        placementSpec = if (dragging) null else androidx.compose.animation.core.spring(),
                                    ).then(drag.cardModifier(node.id)),
                                )
                            }
                        }
                        if (sprintScope && filterActive) {
                            val nestedMatches = filteredRows.orEmpty().filter { it.depth > 0 }
                            if (nestedMatches.isNotEmpty()) item { Text("Coincidencias en subcapas", color = Arachn0deColors.TextSecondary) }
                            items(nestedMatches, key = { "sprint-filtered:${it.node.id}" }) { row ->
                                Column(Modifier.padding(start = minOf(row.depth, 6).times(12).dp)) {
                                    if (!row.isMatch) Text("Contexto · ${row.node.title}", color = Arachn0deColors.TextSecondary)
                                    TextButton(onClick = { if (selection.ids.isNotEmpty()) selection.toggle(row.node.id) else actions.navigate(row.node.id) { path -> currentPath.clear();currentPath.addAll(path) } }) { Text(row.node.title) }
                                    row.node.workState?.let { Text(it.label, color = Arachn0deColors.TextSecondary) }
                                    SecondaryAction(if (row.node.id in selection.ids) "✓ Seleccionado" else "Seleccionar", { selection.toggle(row.node.id) }, enabled = !isSubmittingNode)
                                }
                            }
                        }
                        }
                    }
                }
                androidx.compose.material3.SnackbarHost(actions.snackbar, Modifier.fillMaxWidth())
                RecurrenceManager(nodeRepository.recurrence, project.id, projectState.nodes, people, peopleLoaded, recurrenceSelected, showLauncher = false) { recurrenceSelected = null }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { if (!isSubmittingNode && (currentNode == null || currentNode.canReceiveChildren)) {
                            val parent = currentNodeId
                            if(drafts.hasNew(parent)) drafts.open(parent) { error("Borrador existente") }
                            else {
                                var seed: EditorDraft? = null
                                defaultsOperation.submit("No se pudieron cargar los valores predeterminados. Puedes reintentar.", {
                                    val defaults = nodeRepository.creationDefaults.resolve(project.id,parent)
                                    seed = com.r0ybt.arachn0de.ui.state.CreationDefaultsDraftFactory.create(parent,defaults,clock(),java.util.TimeZone.getDefault())
                                    true
                                }, { drafts.open(parent) { checkNotNull(seed) } })
                            }
                        } },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Arachn0deColors.Primary),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isSubmittingNode && (currentNode == null || currentNode.canReceiveChildren),
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Nuevo elemento")
                    }
                    Button(
                        onClick = {
                            expandedLayerIds = (expandedLayerIds.toSet() + currentPath).toList()
                            showNavigator = true
                        },
                        modifier = Modifier.size(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Arachn0deColors.ControlSurface),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Image(
                            painter = painterResource(com.r0ybt.arachn0de.R.drawable.iconovercapas),
                            contentDescription = "Capas de cebolla",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            if (showDrawer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize().background(Arachn0deColors.Scrim.copy(alpha = 0.45f)).clickable { showDrawer = false }.clearAndSetSemantics {})
                    AppIdentityDrawer(onDismiss = { showDrawer = false }, onProjects = onOpenProjects, projectsSelected = true, onPeople = { showDrawer = false; onOpenPeople() }, onAttention = { showDrawer = false; onOpenAttention() }, onCalendar = { showDrawer = false; onOpenCalendar() }, onObligations = { showDrawer = false; onOpenObligations() }, onSettings = { showDrawer = false; onOpenSettings() }, onAbout = { showDrawer = false; onOpenAbout() })
                }
            }
        }
    }

    convertingObligationId?.let { id ->
        RemoveObligationDialog(isSubmittingNode, true, resultLabel=if(convertingPurpose==NodePurpose.LAYER) "capa" else "nota",
            onDismiss = { convertingObligationId = null },
            onConfirm = { actions.convert(id, convertingPurpose, removeObligation = true) { convertingObligationId = null } })
    }

    draft?.let { editor ->
        NodeDialog(
            draft = editor,
            tagRepository = nodeRepository.tags,
            people = people, peopleLoaded = peopleLoaded,
            isSubmitting = isSubmittingNode,
            editDates = editor.purpose == NodePurpose.ACTION && (editor.id == null || projectState.nodesById[editor.id]?.isCompletable == true),
            onDismiss = {
                if (!isSubmittingNode) {
                    drafts.close()
                }
            },
            onDiscard = { drafts.clear() },
            onSave = { _, _ ->
                val dates = editor.purpose == NodePurpose.ACTION && (editor.id == null || projectState.nodesById[editor.id]?.isCompletable == true)
                if(editor.creationGroupId != null && !editor.sharedPatch().isEmpty) actions.reviewGroup(editor) { members ->
                    if(members.size > 1) groupReview = members else actions.saveDraft(project.id,editor,dates) { drafts.clear() }
                } else actions.saveDraft(project.id, editor, dates) { drafts.clear() }
            },
        )
    }

    if(showDefaults) androidx.compose.ui.window.Dialog(onDismissRequest = {}, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, dismissOnBackPress = false)) {
        AppSafeArea { CreationDefaultsScreen(nodeRepository.creationDefaults,
            currentNodeId?.let { com.r0ybt.arachn0de.domain.defaults.DefaultsScope.Layer(project.id,it) } ?: com.r0ybt.arachn0de.domain.defaults.DefaultsScope.Project(project.id),
            currentNode?.title ?: project.name, tagState.tags, people) { showDefaults = false } }
    }

    if (showSprintMode && currentNode?.isStructural == true) SprintModeDialog(currentNode.sprintMode, isSubmittingNode,
        { showSprintMode = false }, { actions.sprintMode(currentNode.id, !currentNode.sprintMode) { showSprintMode = false } })
    changingWorkStateId?.let { id -> projectState.nodesById[id]?.let { node -> node.workState?.let { state ->
        WorkStateDialog(node.title, state, isSubmittingNode, { changingWorkStateId = null }, { chosen -> actions.workState(id, chosen) { changingWorkStateId = null } })
    } } }
    if(showContextActions) ActionMenu(currentNode?.title ?: project.name,{ showContextActions = false }) {
        ActionMenuItem("Recurrencias", androidx.compose.material.icons.Icons.Default.Refresh, { showContextActions=false; recurrenceSelected="" })
        if(currentNode?.isStructural == true) ActionMenuItem(if(currentNode.sprintMode) "Desactivar Modo Sprint" else "Modo Sprint", androidx.compose.material.icons.Icons.Default.SwapHoriz, { showContextActions=false;showSprintMode=true }, enabled=!isSubmittingNode)
        if(currentNode?.workState != null) ActionMenuItem("Cambiar estado", androidx.compose.material.icons.Icons.Default.SwapHoriz, { showContextActions=false;changingWorkStateId=currentNode.id }, enabled=!isSubmittingNode)
        if(currentNode != null) {
            ActionMenuItem("Editar", androidx.compose.material.icons.Icons.Default.Edit, {
                showContextActions=false
                drafts.open(currentNode.parentId,currentNode.id) {
                    EditorDraft(currentNode.id,currentNode.parentId,currentNode.title,currentNode.description,startAt=currentNode.startAt,dueAt=currentNode.dueAt,purpose=currentNode.purpose,obligation=currentNode.obligation,priority=currentNode.priority).apply {
                        tagIds=tagState.nodeIds[currentNode.id].orEmpty().toList();responsibleIds=responsibleByNode[currentNode.id].orEmpty().map { it.id };creationGroupId=currentNode.creationGroupId;captureSharedBaseline()
                    }
                }
            }, enabled=!isSubmittingNode && assignmentsLoaded && peopleLoaded)
            ActionMenuItem("Eliminar", androidx.compose.material.icons.Icons.Default.Delete, { showContextActions=false;deletingNodeId=currentNode.id;deletingNodeName=currentNode.title }, enabled=!isSubmittingNode)
            ActionMenuItem("Responsables", androidx.compose.material.icons.Icons.Default.People, { showContextActions=false;responsibleNodeId=currentNode.id }, enabled=!isSubmittingNode && !personActions.operation.busy && peopleLoaded && assignmentsLoaded)
            ActionMenuItem("Mover a…", androidx.compose.material.icons.Icons.Default.AccountTree, { showContextActions=false;movingNodeId=currentNode.id }, enabled=!isSubmittingNode)
            ActionMenuItem("Historial", androidx.compose.material.icons.Icons.Default.History, { showContextActions=false;historyNodeId=currentNode.id })
        }
        if(currentNode == null || (currentNode.isStructural)) ActionMenuItem("Valores predeterminados", androidx.compose.material.icons.Icons.Default.Settings, {
            showContextActions = false; showDefaults = true
        }, enabled = !isSubmittingNode)
        if(currentNode != null && !currentNode.hasChildren) ActionMenuItem(
            if(currentNode.purpose != NodePurpose.ACTION) "Convertir en tarea" else "Convertir en nota",
            androidx.compose.material.icons.Icons.Default.SwapHoriz, {
                if(currentNode.obligation != null) { showContextActions = false; convertingPurpose=NodePurpose.NOTE;convertingObligationId = currentNode.id }
                else actions.convert(currentNode.id,if(currentNode.purpose != NodePurpose.ACTION) NodePurpose.ACTION else NodePurpose.NOTE) { showContextActions = false }
            },enabled = !isSubmittingNode)
        if(currentNode!=null && !currentNode.isStructural) ActionMenuItem("Convertir en capa",androidx.compose.material.icons.Icons.Default.AccountTree,{
            showContextActions=false
            if(currentNode.obligation!=null) { convertingPurpose=NodePurpose.LAYER;convertingObligationId=currentNode.id }
            else actions.convert(currentNode.id,NodePurpose.LAYER)
        },enabled=!isSubmittingNode)
        if(currentNode==null) ActionMenuItem("Mover dentro de…",androidx.compose.material.icons.Icons.Default.AccountTree,{ showContextActions=false;moveProject=true },enabled=!isSubmittingNode)
        ActionMenuItem(if(currentNode == null) "Copiar" else "Copiar este elemento", androidx.compose.material.icons.Icons.Default.ContentCopy, {
            showContextActions = false
            if(currentNode == null) copyActions.copyProject(project,projectState,false) else copyActions.copy(projectState,currentNode.id,false)
        },enabled = !copyActions.busy)
        if(currentNode == null || currentNode.isStructural) ActionMenuItem("Copiar con descendientes", androidx.compose.material.icons.Icons.Default.AccountTree, {
            showContextActions = false
            if(currentNode == null) copyActions.copyProject(project,projectState,true) else copyActions.copy(projectState,currentNode.id,true)
        },enabled = !copyActions.busy)
    }

    groupReview?.let { members -> draft?.let { editor ->
        val dates = editor.purpose == NodePurpose.ACTION && projectState.nodesById[editor.id]?.isCompletable == true
        AlertDialog(onDismissRequest = { if(!isSubmittingNode) groupReview = null }, title = { Text("Aplicar cambios compartidos") },
            text = { Text("El grupo tiene ${members.size} elementos existentes, incluidos los movidos a otras capas. Solo se propagarán los campos compartidos modificados; títulos y fechas de los demás se conservarán.") },
            confirmButton = { Button(enabled = !isSubmittingNode, onClick = { actions.saveGroup(editor,members.map { it.id }.toSet(),dates) { groupReview = null; drafts.clear() } }) { Text("Todos los elementos del grupo") } },
            dismissButton = { Column {
                TextButton(enabled = !isSubmittingNode, onClick = { actions.saveDraft(project.id,editor,dates) { groupReview = null; drafts.clear() } }) { Text("Solo este elemento") }
                TextButton(enabled = !isSubmittingNode, onClick = { groupReview = null }) { Text("Volver al editor") }
            } })
    } }
    if(bulkDelete && selection.ids.isNotEmpty()) AlertDialog(onDismissRequest = { if(!isSubmittingNode) bulkDelete = false },
        title = { Text("¿Eliminar ${selection.ids.size} elementos?") }, text = { Text("Las capas seleccionadas también eliminarán todos sus descendientes, relaciones e historial. Esta operación no tiene Deshacer.") },
        confirmButton = { DestructiveAction("Eliminar seleccionados", { actions.deleteSelected(project.id,selection.ids) { bulkDelete = false; selection.clear() } }, enabled = !isSubmittingNode) },
        dismissButton = { TextButton(enabled = !isSubmittingNode, onClick = { bulkDelete = false }) { Text("Cancelar") } })
    if(bulkMove && selection.ids.isNotEmpty()) {
        var expanded by remember { mutableStateOf(currentPath.toList()) }
        AlertDialog(onDismissRequest = { if(!isSubmittingNode) bulkMove = false }, title = { Text("Mover ${selection.ids.size} elementos") },
            text = { LayerNavigator(nodes = projectState.nodes, expandedIds = expanded, onToggle = { id -> expanded = if(id in expanded) expanded - id else expanded + id },
                currentNodeId = currentNodeId, projectName = project.name, movingIds = selection.ids, enabled = !isSubmittingNode,
                onHome = {}, onProject = { actions.moveSelected(project.id,selection.ids,null) { bulkMove = false; selection.clear() } },
                onNavigateTo = { target -> actions.moveSelected(project.id,selection.ids,target) { bulkMove = false; selection.clear() } }) },
            confirmButton = {}, dismissButton = { TextButton(enabled = !isSubmittingNode, onClick = { bulkMove = false }) { Text("Cancelar") } })
    }

    responsibleNodeId?.let { id ->
        if (peopleLoaded && assignmentsLoaded) ResponsibleDialog(
            nodeId = id, people = people, assigned = responsibleByNode[id].orEmpty(),
            busy = personActions.operation.busy,
            onDismiss = { responsibleNodeId = null },
            onSave = { ids -> personActions.assign(id, ids) { responsibleNodeId = null } },
        )
    }
    OperationErrorDialog(personActions.operation)
    LoadErrorDialog(peopleLoad)
    LoadErrorDialog(assignmentsLoad)
    LoadErrorDialog(recurrenceLoad)

    movingNodeId?.let { sourceId ->
        val source = projectState.nodesById[sourceId]
        var moveExpanded by rememberSaveable(
            sourceId,
            stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it.toList() }),
        ) { mutableStateOf(currentPath.toList()) }
        AlertDialog(
            containerColor = Arachn0deColors.Surface,
            titleContentColor = Arachn0deColors.TextPrimary,
            textContentColor = Arachn0deColors.TextSecondary,
            onDismissRequest = { if (!isSubmittingNode) movingNodeId = null },
            title = { Text("Mover a…") },
            text = {
                Column {
                    Text(source?.title ?: "El elemento ya no existe", maxLines = 2)
                    Text("Selecciona un destino. Una tarea que reciba hijos pasará a ser Capa.")
                    LayerNavigator(
                        nodes = projectState.nodes,
                        expandedIds = moveExpanded,
                        onToggle = { id -> moveExpanded = if (id in moveExpanded) moveExpanded - id else moveExpanded + id },
                        currentNodeId = source?.parentId,
                        projectName = project.name,
                        movingId = sourceId,
                        enabled = !isSubmittingNode && source != null,
                        onHome = {},
                        onProject = { actions.move(sourceId, null) { movingNodeId = null } },
                        onNavigateTo = { target -> actions.move(sourceId, target) { movingNodeId = null } },
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(enabled = !isSubmittingNode, onClick = { movingNodeId = null }) { Text("Cancelar") }
            },
        )
    }

    if (showNavigator) {
        AlertDialog(
            containerColor = Arachn0deColors.Surface,
            titleContentColor = Arachn0deColors.TextPrimary,
            onDismissRequest = { showNavigator = false },
            title = { Text("Capas de cebolla") },
            text = {
                LayerNavigator(
                    projectName = project.name,
                    nodes = projectState.nodes,
                    expandedIds = expandedLayerIds,
                    onToggle = { id ->
                        val ids = expandedLayerIds.toSet()
                        expandedLayerIds = (if (id in ids) ids - id else ids + id).toList()
                    },
                    currentNodeId = currentNodeId,
                    onHome = { showNavigator = false; onBackToProjects() },
                    onProject = { currentPath.clear(); showNavigator = false },
                    onNavigateTo = { targetId ->
                        actions.navigate(targetId) { path ->
                            currentPath.clear()
                            currentPath.addAll(path)
                            showNavigator = false
                        }
                    },
                    listState = navigatorListState,
                )
            },
            confirmButton = { TextButton(onClick = { showNavigator = false }) { Text("Cerrar") } },
        )
    }

    deletingNodeId?.let { deletingId ->
        AlertDialog(
            containerColor = Arachn0deColors.Surface,
            titleContentColor = Arachn0deColors.TextPrimary,
            textContentColor = Arachn0deColors.TextSecondary,
            onDismissRequest = { if (!actions.operation.busy) deletingNodeId = null },
            title = { Text("Eliminar nodo") },
            text = { Text("¿Seguro que quieres eliminar “${deletingNodeName}”? Esta acción también eliminará cualquier hijo.") },
            confirmButton = {
                DestructiveAction(
                    label = "Eliminar",
                    enabled = !actions.operation.busy,
                    onClick = {
                        actions.delete(deletingId) {
                            deletingNodeId = null
                            if (deletingId == currentNodeId) {
                                if (currentPath.isNotEmpty()) {
                                    currentPath.removeAt(currentPath.lastIndex)
                                } else {
                                    onBackToProjects()
                                }
                            }
                        }
                    },
                )
            },
            dismissButton = {
                TextButton(enabled = !actions.operation.busy, onClick = { deletingNodeId = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
    OperationErrorDialog(actions.operation)
    LoadErrorDialog(load)
}

