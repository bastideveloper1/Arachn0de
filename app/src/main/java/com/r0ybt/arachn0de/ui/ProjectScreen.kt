package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    onBackToObligations: (() -> Unit)? = null,
    onBackToCalendar: (() -> Unit)? = null,
    openNodeId: String? = null,
    onOpenNodeHandled: () -> Unit = {},
    clock: () -> Long = System::currentTimeMillis,
) {
    val pendingOpenNode by rememberUpdatedState(openNodeId)
    val handleOpenNode by rememberUpdatedState(onOpenNodeHandled)
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
    var draft by rememberSaveable(project.id, stateSaver = EditorDraft.Saver) { mutableStateOf<EditorDraft?>(null) }
    var convertingObligationId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var batchDraft by rememberSaveable(project.id, stateSaver = NodeBatchDraft.Saver) { mutableStateOf<NodeBatchDraft?>(null) }
    var deletingNodeId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var deletingNodeName by rememberSaveable(project.id) { mutableStateOf("") }
    var movingNodeId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var showDrawer by remember { mutableStateOf(false) }
    var showNavigator by rememberSaveable(project.id) { mutableStateOf(false) }
    var expandedLayerIds by rememberSaveable(
        project.id,
        stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it.toList() }),
    ) { mutableStateOf(emptyList<String>()) }
    val actions = remember(nodeRepository, scope) { NodeActions(nodeRepository, scope) }
    val isSubmittingNode = actions.operation.busy
    val currentNodeId = currentPath.lastOrNull()
    val currentNode = projectState.nodesById[currentNodeId]
    val currentNodes = projectState.childrenOf(currentNodeId)
    val currentProgress = projectState.progressById[currentNodeId]
    val now = com.r0ybt.arachn0de.ui.state.rememberTaskScreenNow(projectState.nodes, clock)
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

    BackHandler(enabled = showDrawer) { showDrawer = false }

    if (!hasLoaded) {
        Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {}
        LoadErrorDialog(load)
        return
    }

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
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                HeaderBar(onMenuClick = { showDrawer = true })

                layerScrollStates.SaveableStateProvider(currentNodeId?.let { "node:$it" } ?: "project-root") {
                    val nodeListState = rememberLazyListState()
                    val groups = listOf(false, true).associateWith { completed ->
                        currentNodes.filter { it.projectId == project.id && it.parentId == currentNodeId && it.isCompleted == completed }.map { it.id }
                    }
                    val drag = rememberDragReorderState(nodeListState, "node:", groups, isSubmittingNode, actions.operation.error, currentNodeId) { source, target, _ ->
                        actions.reorderTo(source, currentNodeId, target)
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
                                        text = currentNode?.title ?: project.name,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Arachn0deColors.TextPrimary,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (currentNode == null) {
                                    Text(
                                        text = "Proyecto raíz",
                                        color = Arachn0deColors.TextSecondary,
                                        fontSize = 12.sp,
                                    )
                                } else {
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

                                AttentionIndicator(if (currentNode == null) attention?.byProjectId?.get(project.id) else if (currentNode.hasChildren) attention?.byNodeId?.get(currentNode.id) else null)
                                if (currentNode == null || currentNode.hasChildren) FinancialSummaryCard(
                                    if (currentNode == null) financial?.byProjectId?.get(project.id) else financial?.byNodeId?.get(currentNode.id), title = "Obligaciones · Todo el período")
                                if (currentNode != null && currentNode.hasChildren && currentProgress != null) {
                                    NodeProgressCard(progress = currentProgress!!)
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                if (currentNode != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Button(
                                            onClick = {
                                                if (!isSubmittingNode) {
                                                    draft = EditorDraft(currentNode.id, currentNode.parentId, currentNode.title, currentNode.description, startAt = currentNode.startAt, dueAt = currentNode.dueAt, purpose = currentNode.purpose, obligation = currentNode.obligation)
                                                }
                                            },
                                            enabled = !isSubmittingNode,
                                            modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Arachn0deColors.ControlSurface,
                                                contentColor = Arachn0deColors.TextPrimary,
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                        ) {
                                            Text("Editar")
                                        }

                                        Button(
                                            onClick = {
                                                if (!isSubmittingNode) {
                                                    deletingNodeId = currentNode.id
                                                    deletingNodeName = currentNode.title
                                                }
                                            },
                                            enabled = !isSubmittingNode,
                                            modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Arachn0deColors.ControlSurface,
                                                contentColor = Arachn0deColors.Destructive,
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                        ) {
                                            Text("Eliminar")
                                        }

                                        if (currentNode.isCompletable) {
                                            Button(
                                                onClick = {
                                                    if (!isSubmittingNode) {
                                                        actions.toggle(currentNode.id)
                                                    }
                                                },
                                                enabled = !isSubmittingNode,
                                                modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Arachn0deColors.Primary),
                                                shape = RoundedCornerShape(10.dp),
                                            ) {
                                                Text(if (currentNode.isCompleted) "Reabrir" else "Completar")
                                            }
                                        }
                                    }
                                    if (!currentNode.hasChildren) {
                                        TextButton(enabled = !isSubmittingNode, onClick = {
                                            if (currentNode.obligation != null) convertingObligationId = currentNode.id
                                            else actions.convert(currentNode.id, if (currentNode.purpose == NodePurpose.NOTE) NodePurpose.ACTION else NodePurpose.NOTE)
                                        }) { Text(if (currentNode.purpose == NodePurpose.NOTE) "Convertir en tarea" else "Convertir en nota") }
                                    }
                                    if (currentNode.purpose == NodePurpose.NOTE) Text("Nota · Convierte en tarea para añadir hijos", color = Arachn0deColors.TextSecondary)
                                    ObligationIndicator(currentNode)
                                    if (currentNode.obligation != null) Text("Convierte esta obligación en una tarea antes de usarla como capa.", color = Arachn0deColors.TextSecondary)
                                    TaskDateIndicator(currentNode, now)
                                    ResponsibleAvatars(responsibleByNode[currentNode.id].orEmpty())
                                    TextButton(
                                        enabled = !isSubmittingNode && !personActions.operation.busy && peopleLoaded && assignmentsLoaded,
                                        onClick = { responsibleNodeId = currentNode.id },
                                    ) { Text("Responsables") }
                                    TextButton(
                                        onClick = { movingNodeId = currentNode.id },
                                        enabled = !isSubmittingNode,
                                    ) { Text("Mover a…") }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                            }
                        }
                        if (currentNodes.isEmpty()) {
                            item(key = "empty-layer", contentType = "empty") { if (currentNode?.purpose != NodePurpose.NOTE && currentNode?.obligation == null) EmptyLayerState() }
                        }
                        for (completedGroup in listOf(false, true)) {
                            val groupNodes = currentNodes.filter { it.isCompleted == completedGroup }
                            val renderNodes = drag.orderFor(completedGroup, groupNodes.map { it.id })
                                .mapNotNull { id -> groupNodes.firstOrNull { it.id == id } }
                            if (groupNodes.isNotEmpty()) {
                                item(key = "section:$completedGroup", contentType = "section") {
                                    Column {
                                        if (completedGroup) androidx.compose.material3.HorizontalDivider(color = Arachn0deColors.Outline)
                                        Text(
                                            if (completedGroup) "Completadas" else "Disponibles",
                                            color = Arachn0deColors.TextSecondary,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                            items(renderNodes, key = { "node:${it.id}" }, contentType = { "node" }) { node ->
                                val dragging = drag.isDragging(node.id)
                                NodeCard(
                                    node = node,
                                    progress = progressMap[node.id],
                                    hasChildren = node.hasChildren,
                                    onConvert = { done -> if (node.obligation != null) { convertingObligationId = node.id; done() } else actions.convert(node.id, if (node.purpose == NodePurpose.NOTE) NodePurpose.ACTION else NodePurpose.NOTE, onSuccess = done) },
                                    canToggleComplete = node.isCompletable,
                                    onOpen = {
                                        currentPath.add(node.id)
                                    },
                                    responsiblePeople = responsibleByNode[node.id].orEmpty(),
                                    now = now,
                                    attention = attention?.byNodeId?.get(node.id),
                                    onResponsible = { if (!isSubmittingNode && !personActions.operation.busy && peopleLoaded && assignmentsLoaded) responsibleNodeId = node.id },
                                    onMove = { if (!isSubmittingNode) movingNodeId = node.id },
                                    onEdit = {
                                        draft = EditorDraft(node.id, node.parentId, node.title, node.description, startAt = node.startAt, dueAt = node.dueAt, purpose = node.purpose, obligation = node.obligation)
                                    },
                                    canMoveUp = node.id != renderNodes.firstOrNull()?.id && !isSubmittingNode,
                                    canMoveDown = node.id != renderNodes.lastOrNull()?.id && !isSubmittingNode,
                                    onReorder = { moveUp, onSuccess ->
                                        actions.reorder(node.id, node.parentId, moveUp, onSuccess)
                                    },
                                    onDelete = {
                                        deletingNodeId = node.id
                                        deletingNodeName = node.title
                                    },
                                    onToggleComplete = {
                                        if (node.isCompletable) actions.toggle(node.id)
                                    },
                                    dragging = dragging,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = null, fadeOutSpec = null,
                                        placementSpec = if (dragging) null else androidx.compose.animation.core.spring(),
                                    ).then(drag.cardModifier(node.id)),
                                )
                            }
                        }
                    }
                }
                TextButton(enabled = !isSubmittingNode && (currentNode?.purpose != NodePurpose.NOTE && currentNode?.obligation == null), onClick = { batchDraft = NodeBatchDraft(currentNodeId) }) { Text("Crear varios") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { if (!isSubmittingNode && (currentNode?.purpose != NodePurpose.NOTE && currentNode?.obligation == null)) draft = EditorDraft(null, currentNodeId, "", "") },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Arachn0deColors.Primary),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isSubmittingNode && (currentNode?.purpose != NodePurpose.NOTE && currentNode?.obligation == null),
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
                        .fillMaxSize()
                        .background(Arachn0deColors.Scrim.copy(alpha = 0.45f))
                        .clickable { showDrawer = false },
                ) {
                    AppIdentityDrawer(onDismiss = { showDrawer = false }, onPeople = { showDrawer = false; onOpenPeople() }, onAttention = { showDrawer = false; onOpenAttention() }, onCalendar = { showDrawer = false; onOpenCalendar() }, onObligations = { showDrawer = false; onOpenObligations() })
                }
            }
        }
    }

    convertingObligationId?.let { id ->
        RemoveObligationDialog(isSubmittingNode, true,
            onDismiss = { convertingObligationId = null },
            onConfirm = { actions.convert(id, NodePurpose.NOTE, removeObligation = true) { convertingObligationId = null } })
    }

    batchDraft?.let { batch ->
        NodeBatchDialog(batch, people, peopleLoaded, isSubmittingNode,
            onDismiss = { if (!isSubmittingNode) batchDraft = null },
            onCreate = { specs, ids -> actions.createBatch(project.id, batch, specs, ids) { batchDraft = null } })
    }

    draft?.let { editor ->
        NodeDialog(
            draft = editor,
            isSubmitting = isSubmittingNode,
            editDates = editor.purpose == NodePurpose.ACTION && (editor.id == null || projectState.nodesById[editor.id]?.isCompletable == true),
            onDismiss = {
                if (!isSubmittingNode) {
                    draft = null
                }
            },
            onSave = { title, description ->
                actions.save(project.id, editor.parentId, editor.id, title, description, editor.creationId, editor.startAt, editor.dueAt,
                    editDates = editor.purpose == NodePurpose.ACTION && (editor.id == null || projectState.nodesById[editor.id]?.isCompletable == true), purpose = editor.purpose, obligation = editor.obligation(), removeObligation = editor.financialRemovalConfirmed) {
                    draft = null
                }
            },
        )
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
                TextButton(
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
                ) {
                    Text("Eliminar")
                }
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

