package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    val mapListState = rememberLazyListState()
    val trailListState = rememberLazyListState()
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
    var showPathDialog by rememberSaveable(project.id) { mutableStateOf(false) }
    var showLayerMapDialog by rememberSaveable(project.id) { mutableStateOf(false) }
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
    val projectLayerMap = projectState.nodes
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
            if (validPath.isEmpty()) showPathDialog = false
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
                            modifier = Modifier.size(42.dp),
                        ) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Volver a la capa anterior", tint = Arachn0deColors.Accent)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = project.name,
                        color = Arachn0deColors.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentNode!!.title,
                            color = Arachn0deColors.Accent,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (pathNodes.isNotEmpty()) {
                        TextButton(
                            onClick = { showPathDialog = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "Capas de cebolla",
                                color = Arachn0deColors.Accent,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            expandedLayerIds = (expandedLayerIds.toSet() + currentPath).toList()
                            showLayerMapDialog = true
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "Mapa de capas",
                            color = Arachn0deColors.Accent,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (currentProgress != null) {
                    NodeProgressCard(progress = currentProgress!!)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (currentNodes.isEmpty()) {
                    EmptyLayerState(onCreateNode = { draft = EditorDraft(null, currentNodeId, "", "") })
                } else {
                    layerScrollStates.SaveableStateProvider(currentNodeId?.let { "node:$it" } ?: "project-root") {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("nodes-list"),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(currentNodes, key = { "node:${it.id}" }, contentType = { "node" }) { node ->
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
                                    canMoveUp = node.id != currentNodes.firstOrNull()?.id && !isSubmittingNode,
                                    canMoveDown = node.id != currentNodes.lastOrNull()?.id && !isSubmittingNode,
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
                                )
                            }

                            item(key = "create-node", contentType = "action") {
                                Button(
                                    onClick = {
                                        if (draft == null && !isSubmittingNode) draft = EditorDraft(null, currentNodeId, "", "")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Arachn0deColors.Primary),
                                    shape = RoundedCornerShape(14.dp),
                                    enabled = !isSubmittingNode,
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Nuevo elemento")
                                }
                            }
                        }
                    }
                }

                if (currentNodes.isEmpty()) Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onBackToProjects,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Arachn0deColors.Surface),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Volver a proyectos")
                }
            }

            if (showDrawer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Arachn0deColors.Scrim.copy(alpha = 0.45f))
                        .clickable { showDrawer = false },
                ) {
                    FutureNavigationDrawer(onDismiss = { showDrawer = false })
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

    if (showPathDialog && pathNodes.isNotEmpty()) {
        AlertDialog(
            containerColor = Arachn0deColors.BackgroundMiddle,
            titleContentColor = Arachn0deColors.PathHighlight,
            textContentColor = Arachn0deColors.TextCompleted,
            onDismissRequest = { showPathDialog = false },
            title = { Text("Capas de cebolla") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    LayerTrailRoute(
                        projectName = project.name,
                        listState = trailListState,
                        pathNodes = pathNodes,
                        onProjectClick = {
                            currentPath.clear()
                            showPathDialog = false
                        },
                        onLayerClick = { id ->
                            val index = currentPath.indexOf(id)
                            if (index < 0) return@LayerTrailRoute
                            while (currentPath.size > index + 1) {
                                currentPath.removeAt(currentPath.lastIndex)
                            }
                            showPathDialog = false
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPathDialog = false }) {
                    Text("Cerrar")
                }
            },
        )
    }

    if (showLayerMapDialog) {
        AlertDialog(
            containerColor = Arachn0deColors.Surface,
            titleContentColor = Arachn0deColors.TextPrimary,
            textContentColor = Arachn0deColors.TextSecondary,
            onDismissRequest = { showLayerMapDialog = false },
            title = { Text("Mapa de capas") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Proyecto · ${project.name}",
                        color = Arachn0deColors.TextSecondary,
                        fontSize = 12.sp,
                    )

                    if (projectLayerMap.isEmpty()) {
                        Text(
                            text = "Todavía no hay capas en este proyecto.",
                            color = Arachn0deColors.Accent,
                            fontSize = 14.sp,
                        )
                    } else {
                        LayerMapTree(
                            nodes = projectLayerMap,
                            listState = mapListState,
                            expandedIds = expandedLayerIds,
                            onToggle = { id ->
                                val ids = expandedLayerIds.toSet()
                                expandedLayerIds = (if (id in ids) ids - id else ids + id).toList()
                            },
                            currentPath = currentPath,
                            onNavigateTo = { targetId ->
                                actions.navigate(targetId) { path ->
                                    currentPath.clear()
                                    currentPath.addAll(path)
                                    showLayerMapDialog = false
                                }
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLayerMapDialog = false }) {
                    Text("Cerrar")
                }
            },
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

