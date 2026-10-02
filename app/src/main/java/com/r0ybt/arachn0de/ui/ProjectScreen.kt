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
) {
    val scope = rememberCoroutineScope()
    val layerScrollStates = rememberSaveableStateHolder()
    val navigatorListState = rememberLazyListState()
    val currentPath = rememberSaveable(project.id, saver = listSaver<SnapshotStateList<String>, String>(
        save = { it.toList() },
        restore = { it.toMutableStateList() },
    )) { mutableStateListOf<String>() }
    var hasLoaded by remember(project.id) { mutableStateOf(false) }
    var projectState by remember(project.id) { mutableStateOf(NodeTreeSnapshot(emptyList())) }
    var draft by rememberSaveable(project.id, stateSaver = EditorDraft.Saver) { mutableStateOf<EditorDraft?>(null) }
    var deletingNodeId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var deletingNodeName by rememberSaveable(project.id) { mutableStateOf("") }
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
    val pathNodes = currentPath.mapNotNull { projectState.nodesById[it] }
    val currentLayer = pathNodes.size
    val progressMap = projectState.progressById

    val load = remember(project.id, nodeRepository) { LoadState() }
    LaunchedEffect(project.id, nodeRepository, load.attempt) {
        load.collect(nodeRepository.observePreparedProjectState(project.id)) { snapshot ->
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
        if (currentPath.isNotEmpty()) {
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
                    LazyColumn(
                        state = nodeListState,
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("nodes-list"),
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
                                                if (currentPath.isNotEmpty()) currentPath.removeAt(currentPath.lastIndex)
                                            },
                                            modifier = Modifier.size(48.dp),
                                        ) {
                                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Volver a la capa anterior", tint = Arachn0deColors.Accent)
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
                                    Text(currentNode!!.description, color = Arachn0deColors.TextSecondary,
                                        modifier = Modifier.padding(vertical = 8.dp))
                                }

                                if (currentProgress != null) {
                                    NodeProgressCard(progress = currentProgress!!)
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                            }
                        }
                        if (currentNodes.isEmpty()) {
                            item(key = "empty-layer", contentType = "empty") { EmptyLayerState() }
                        }
                        for (completedGroup in listOf(false, true)) {
                            val groupNodes = currentNodes.filter { it.isCompleted == completedGroup }
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
                        items(groupNodes, key = { "node:${it.id}" }, contentType = { "node" }) { node ->
                            NodeCard(
                                node = node,
                                progress = progressMap[node.id],
                                hasChildren = node.hasChildren,
                                canToggleComplete = node.isCompletable,
                                onOpen = {
                                    currentPath.add(node.id)
                                },
                                onEdit = {
                                    draft = EditorDraft(node.id, node.parentId, node.title, node.description)
                                },
                                canMoveUp = node.id != groupNodes.firstOrNull()?.id && !isSubmittingNode,
                                canMoveDown = node.id != groupNodes.lastOrNull()?.id && !isSubmittingNode,
                                onReorder = { moveUp, onSuccess ->
                                    actions.reorder(node.id, node.parentId, moveUp, onSuccess)
                                },
                                onDrop = { offset ->
                                    val visible = nodeListState.layoutInfo.visibleItemsInfo
                                    val source = visible.firstOrNull { it.key == "node:${node.id}" }
                                    if (source != null) {
                                        val center = source.offset + source.size / 2f + offset
                                        val siblings = currentNodes.filter { it.isCompleted == node.isCompleted }.associateBy { "node:${it.id}" }
                                        val target = visible.filter { it.key in siblings }
                                            .minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - center) }
                                        target?.let { siblings[it.key] }?.let {
                                            actions.reorderTo(node.id, node.parentId, it.id)
                                        }
                                    }
                                },
                                onDelete = {
                                    deletingNodeId = node.id
                                    deletingNodeName = node.title
                                },
                                onToggleComplete = {
                                    if (node.isCompletable) actions.toggle(node.id)
                                },
                                listState = nodeListState,
                            )
                        }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { if (!isSubmittingNode) draft = EditorDraft(null, currentNodeId, "", "") },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Arachn0deColors.Primary),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isSubmittingNode,
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
                    AppIdentityDrawer(onDismiss = { showDrawer = false })
                }
            }
        }
    }

    draft?.let { editor ->
        NodeDialog(
            draft = editor,
            isSubmitting = isSubmittingNode,
            onDismiss = {
                if (!isSubmittingNode) {
                    draft = null
                }
            },
            onSave = { title, description ->
                actions.save(project.id, editor.parentId, editor.id, title, description, editor.creationId) {
                    draft = null
                }
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
                        actions.delete(deletingId) { deletingNodeId = null }
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

