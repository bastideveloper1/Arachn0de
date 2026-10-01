package com.r0ybt.arachn0de

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.domain.model.NodeProgress
import com.r0ybt.arachn0de.domain.model.NodeProgressState
import com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot
import com.r0ybt.arachn0de.domain.model.Project
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val database by lazy { Arachn0deDatabase.create(applicationContext) }
    private val projectRepository by lazy { ProjectRepository(database.projectDao()) }
    private val nodeRepository by lazy { NodeRepository(database) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Arachn0deTheme(darkTheme = true, dynamicColor = false) {
                AppRoot(projectRepository, nodeRepository)
            }
        }
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }
}

@Composable
private fun AppRoot(projectRepository: ProjectRepository, nodeRepository: NodeRepository) {
    var selectedProject by remember { mutableStateOf<Project?>(null) }

    if (selectedProject == null) {
        ProjectDashboardScreen(
            repository = projectRepository,
            onOpenProject = { selectedProject = it },
        )
    } else {
        ProjectNodeScreen(
            project = selectedProject!!,
            nodeRepository = nodeRepository,
            onBackToProjects = { selectedProject = null },
        )
    }
}

@Composable
fun ProjectDashboardScreen(repository: ProjectRepository, onOpenProject: (Project) -> Unit = {}) {
    val projects = remember { mutableStateListOf<Project>() }
    val scope = rememberCoroutineScope()
    var showProjectDialog by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<Project?>(null) }
    var deletingProject by remember { mutableStateOf<Project?>(null) }
    var showDrawer by remember { mutableStateOf(false) }

    LaunchedEffect(repository) {
        repository.observeProjects().collect { items ->
            projects.clear()
            projects.addAll(items)
        }
    }

    val background = Brush.linearGradient(
        listOf(
            Color(0xFF000000),
            Color(0xFF050505),
            Color(0xFF0A0A0A),
        ),
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF000000),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .padding(horizontal = 2.dp),
            ) {
                HeaderBar(
                    onMenuClick = { showDrawer = true },
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Proyectos",
                    color = Color(0xFFF3F6FF),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SummaryCard(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 90.dp),
                        title = "Proyectos",
                        value = projects.size.toString(),
                        icon = "□",
                    )
                    SummaryCard(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 90.dp),
                        title = "Activos",
                        value = "0",
                        icon = "✓",
                    )
                    SummaryCard(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 90.dp),
                        title = "Hoy",
                        value = "0",
                        icon = "◔",
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (projects.isEmpty()) {
                    EmptyProjectsState(
                        onCreateProject = { showProjectDialog = true },
                    )
                } else {
                    ProjectList(
                        projects = projects,
                        onCreateProject = { showProjectDialog = true },
                        onEdit = { project ->
                            editingProject = project
                            showProjectDialog = true
                        },
                        onDelete = { project -> deletingProject = project },
                        onOpenProject = onOpenProject,
                    )
                }
            }

            if (showDrawer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF090A0D).copy(alpha = 0.45f))
                        .clickable { showDrawer = false },
                ) {
                    FutureNavigationDrawer(
                        onDismiss = { showDrawer = false },
                    )
                }
            }
        }
    }

    if (showProjectDialog) {
        ProjectDialog(
            project = editingProject,
            onDismiss = {
                showProjectDialog = false
                editingProject = null
            },
            onSave = { name, description ->
                scope.launch {
                    val currentProject = editingProject
                    try {
                        if (currentProject == null) {
                            repository.createProject(name, description)
                        } else {
                            repository.updateProject(currentProject.id, name, description)
                        }
                    } finally {
                        showProjectDialog = false
                        editingProject = null
                    }
                }
            },
        )
    }

    deletingProject?.let { project ->
        AlertDialog(
            containerColor = Color(0xFF101D35),
            titleContentColor = Color(0xFFE5EAFF),
            textContentColor = Color(0xFFB9C6E8),
            onDismissRequest = { deletingProject = null },
            title = { Text("Eliminar proyecto") },
            text = { Text("¿Seguro que quieres eliminar “${project.name}”? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteProject(project.id)
                            deletingProject = null
                        }
                    },
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProject = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun ProjectNodeScreen(
    project: Project,
    nodeRepository: NodeRepository,
    onBackToProjects: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentPath = remember { mutableStateListOf<String>() }
    var projectState by remember(project.id) { mutableStateOf(NodeTreeSnapshot(emptyList())) }
    var showNodeDialog by remember { mutableStateOf(false) }
    var editingNode by remember { mutableStateOf<Node?>(null) }
    var deletingNode by remember { mutableStateOf<Node?>(null) }
    var showDrawer by remember { mutableStateOf(false) }
    var showPathDialog by remember { mutableStateOf(false) }
    var showLayerMapDialog by remember { mutableStateOf(false) }
    var isSubmittingNode by remember { mutableStateOf(false) }
    val currentNodeId = currentPath.lastOrNull()
    val currentNode = projectState.nodesById[currentNodeId]
    val currentNodes = projectState.childrenOf(currentNodeId)
    val currentProgress = projectState.progressById[currentNodeId]
    val pathNodes = currentPath.mapNotNull { projectState.nodesById[it] }
    val currentLayer = pathNodes.size
    val projectLayerMap = projectState.nodes
    val progressMap = projectState.progressById

    LaunchedEffect(project.id, nodeRepository) {
        nodeRepository.observeProjectState(project.id).collect { projectState = it }
    }

    BackHandler(enabled = currentPath.isNotEmpty()) {
        if (currentPath.isNotEmpty()) {
            currentPath.removeAt(currentPath.lastIndex)
        } else {
            onBackToProjects()
        }
    }

    val background = Brush.linearGradient(
        listOf(
            Color(0xFF000000),
            Color(0xFF050505),
            Color(0xFF0A0A0A),
        ),
    )

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF000000)) {
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
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null, tint = Color(0xFFD8D9FF))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = project.name,
                        color = Color(0xFFF3F6FF),
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
                        color = Color(0xFFB9C6E8),
                        fontSize = 12.sp,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "CAPA $currentLayer",
                            color = Color(0xFFB9C6E8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentNode!!.title,
                            color = Color(0xFFE9E1FF),
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
                                color = Color(0xFFE9E1FF),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    TextButton(
                        onClick = { showLayerMapDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "Mapa de capas",
                            color = Color(0xFFE9E1FF),
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
                    EmptyLayerState(onCreateNode = { showNodeDialog = true })
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        currentNodes.forEach { node ->
                            NodeCard(
                                node = node,
                                progress = progressMap[node.id],
                                hasChildren = node.hasChildren,
                                canToggleComplete = node.isCompletable,
                                onOpen = {
                                    currentPath.add(node.id)
                                },
                                onEdit = {
                                    editingNode = node
                                    showNodeDialog = true
                                },
                                onDelete = {
                                    deletingNode = node
                                },
                                onToggleComplete = {
                                    scope.launch {
                                        if (node.isCompletable) {
                                            nodeRepository.toggleCompleted(node.id)
                                        }
                                    }
                                },
                            )
                        }

                        Button(
                            onClick = {
                                if (!showNodeDialog && !isSubmittingNode) showNodeDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E61FF)),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isSubmittingNode,
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Nuevo elemento")
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onBackToProjects,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B1B1D)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Volver a proyectos")
                }
            }

            if (showDrawer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF090A0D).copy(alpha = 0.45f))
                        .clickable { showDrawer = false },
                ) {
                    FutureNavigationDrawer(onDismiss = { showDrawer = false })
                }
            }
        }
    }

    if (showNodeDialog) {
        NodeDialog(
            projectId = project.id,
            currentNodeId = currentPath.lastOrNull(),
            node = editingNode,
            isSubmitting = isSubmittingNode,
            onDismiss = {
                if (!isSubmittingNode) {
                    showNodeDialog = false
                    editingNode = null
                }
            },
            onSave = { title, description ->
                if (isSubmittingNode) return@NodeDialog
                isSubmittingNode = true
                scope.launch {
                    try {
                        if (editingNode == null) {
                            nodeRepository.createNode(
                                projectId = project.id,
                                parentId = currentPath.lastOrNull(),
                                title = title,
                                description = description,
                            )
                        } else {
                            nodeRepository.updateNode(
                                id = editingNode!!.id,
                                title = title,
                                description = description,
                            )
                        }
                    } finally {
                        isSubmittingNode = false
                        showNodeDialog = false
                        editingNode = null
                    }
                }
            },
        )
    }

    if (showPathDialog && pathNodes.isNotEmpty()) {
        AlertDialog(
            containerColor = Color(0xFF050505),
            titleContentColor = Color(0xFFFFA45B),
            textContentColor = Color(0xFFE6E1F4),
            onDismissRequest = { showPathDialog = false },
            title = { Text("Capas de cebolla") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    LayerTrailRoute(
                        projectName = project.name,
                        pathNodes = pathNodes,
                        currentPath = currentPath,
                        onProjectClick = {
                            currentPath.clear()
                            showPathDialog = false
                        },
                        onLayerClick = { index ->
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
            containerColor = Color(0xFF17181C),
            titleContentColor = Color(0xFFF3F6FF),
            textContentColor = Color(0xFFB7C5E7),
            onDismissRequest = { showLayerMapDialog = false },
            title = { Text("Mapa de capas") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Proyecto · ${project.name}",
                        color = Color(0xFFB9C6E8),
                        fontSize = 12.sp,
                    )

                    if (projectLayerMap.isEmpty()) {
                        Text(
                            text = "Todavía no hay capas en este proyecto.",
                            color = Color(0xFFE9E1FF),
                            fontSize = 14.sp,
                        )
                    } else {
                        LayerMapTree(
                            roots = buildLayerTree(projectLayerMap),
                            currentPath = currentPath,
                            onNavigateTo = { targetId ->
                                scope.launch {
                                    currentPath.clear()
                                    currentPath.addAll(nodeRepository.getNodePath(targetId).map { it.id })
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

    deletingNode?.let { node ->
        AlertDialog(
            containerColor = Color(0xFF17181C),
            titleContentColor = Color(0xFFE5EAFF),
            textContentColor = Color(0xFFB9C6E8),
            onDismissRequest = { deletingNode = null },
            title = { Text("Eliminar nodo") },
            text = { Text("¿Seguro que quieres eliminar “${node.title}”? Esta acción también eliminará cualquier hijo.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            nodeRepository.deleteNode(node.id)
                            deletingNode = null
                        }
                    },
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingNode = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun EmptyLayerState(onCreateNode: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C).copy(alpha = 0.96f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Esta capa está vacía",
                color = Color(0xFFF0F5FF),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Añade un elemento para empezar a construir la siguiente capa.",
                color = Color(0xFFB9C4E3),
                fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onCreateNode,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8863FF)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nuevo elemento")
            }
        }
    }
}

@Composable
private fun NodeProgressCard(progress: NodeProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Progreso",
                    color = Color(0xFFB9C6E8),
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (progress.state) {
                        NodeProgressState.NO_WORK -> "Sin trabajo"
                        NodeProgressState.NOT_STARTED -> "No empezado"
                        NodeProgressState.PARTIAL -> "En progreso"
                        NodeProgressState.COMPLETE -> "Completado"
                    },
                    color = Color(0xFFF3F6FF),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = if (progress.total == 0) "0%" else "${progress.percentage}%",
                color = Color(0xFFB99BFF),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NodeCard(
    node: Node,
    progress: NodeProgress?,
    hasChildren: Boolean,
    canToggleComplete: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleComplete: () -> Unit,
) {
    var showContextMenu by remember { mutableStateOf(false) }

    val completedTint = if (node.isCompleted) Color(0xFF6A4AC6) else Color(0xFF7A4AEF)
    val displayTextColor = if (node.isCompleted) Color(0xFFE3DCF7) else Color(0xFFF1F6FF)
    val rowAlpha = if (node.isCompleted) 0.92f else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .border(1.dp, Color(0xFF3A2C52).copy(alpha = 0.9f), RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canToggleComplete) {
                IconButton(
                    onClick = onToggleComplete,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (node.isCompleted) completedTint else Color(0xFF2A2A2D)),
                ) {
                    if (node.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .border(2.dp, Color(0xFFB9C6E8), RoundedCornerShape(4.dp)),
                        )
                    }
                }
            } else if (hasChildren) {
                Image(
                    painter = painterResource(id = R.drawable.cebolla_icon),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color(0xFFB99BFF)),
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = node.title,
                    color = displayTextColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (node.isCompleted) TextDecoration.LineThrough else null,
                )

                if (progress != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (progress.state) {
                            NodeProgressState.NO_WORK -> "Sin trabajo"
                            NodeProgressState.NOT_STARTED -> "0%"
                            NodeProgressState.PARTIAL -> "${progress.percentage}%"
                            NodeProgressState.COMPLETE -> "100%"
                        },
                        color = Color(0xFFB99BFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else if (hasChildren) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Capa",
                        color = Color(0xFFB9C6E8),
                        fontSize = 11.sp,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasChildren) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFFB9C6E8),
                    )
                }

                IconButton(
                    onClick = { showContextMenu = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Más opciones",
                        tint = Color(0xFFB9C6E8),
                    )
                }
            }
        }
    }

    if (showContextMenu) {
        AlertDialog(
            containerColor = Color(0xFF17181C),
            titleContentColor = Color(0xFFF3F6FF),
            textContentColor = Color(0xFFB7C5E7),
            onDismissRequest = { showContextMenu = false },
            title = { Text(node.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            onEdit()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Editar", textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = {
                            showContextMenu = false
                            onDelete()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Eliminar", color = Color(0xFFFF8C8C), textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = { showContextMenu = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cancelar", textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun NodeDialog(
    projectId: String,
    currentNodeId: String?,
    node: Node?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember(node?.id) { mutableStateOf(node?.title ?: "") }
    var description by remember(node?.id) { mutableStateOf(node?.description ?: "") }

    AlertDialog(
        containerColor = Color(0xFF17181C),
        titleContentColor = Color(0xFFF3F6FF),
        textContentColor = Color(0xFFB7C5E7),
        onDismissRequest = if (isSubmitting) ({}) else onDismiss,
        title = { Text(if (node == null) "Nuevo elemento" else "Editar elemento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (!isSubmitting) title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (!isSubmitting) description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                )
                Text(
                    text = "Un elemento sin hijos funciona como tarea. Si añade hijos, se convierte automáticamente en capa.",
                    color = Color(0xFFB9C6E8),
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting && title.trim().isNotEmpty(),
                onClick = {
                    val cleanTitle = title.trim()
                    if (!isSubmitting && cleanTitle.isNotEmpty()) {
                        onSave(cleanTitle, description.trim())
                    }
                },
            ) {
                Text(if (isSubmitting) "Guardando..." else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancelar")
            }
        },
    )
}

private data class LayerTreeNode(
    val node: Node,
    val children: List<LayerTreeNode> = emptyList(),
)

private fun buildLayerTree(nodes: List<Node>): List<LayerTreeNode> {
    val byParent = nodes.groupBy { it.parentId }

    fun build(parentId: String?): List<LayerTreeNode> {
        return byParent[parentId].orEmpty()
            .sortedBy { it.position }
            .map { node ->
                LayerTreeNode(
                    node = node,
                    children = build(node.id),
                )
            }
    }

    return build(null)
}

@Composable
private fun LayerMapTree(
    roots: List<LayerTreeNode>,
    currentPath: List<String>,
    onNavigateTo: (String) -> Unit,
) {
    val expandedById = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        roots.forEach { root ->
            LayerMapBranch(
                branch = root,
                depth = 0,
                currentPath = currentPath,
                expandedById = expandedById,
                onNavigateTo = onNavigateTo,
            )
        }
    }
}

@Composable
private fun LayerMapBranch(
    branch: LayerTreeNode,
    depth: Int,
    currentPath: List<String>,
    expandedById: MutableMap<String, Boolean>,
    onNavigateTo: (String) -> Unit,
) {
    val isExpanded = expandedById[branch.node.id] ?: true

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(22.dp),
            ) {
                if (depth > 0) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(18.dp)
                            .background(Color(0xFF7C5CFF)),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (currentPath.contains(branch.node.id)) Color(0xFFFFA45B) else Color(0xFF7C5CFF),
                        ),
                )
                if (branch.children.isNotEmpty() && depth < 4) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(18.dp)
                            .background(Color(0xFF7C5CFF)),
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateTo(branch.node.id) },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (branch.children.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2B2B2B)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.cebolla_icon),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(Color(0xFFB99BFF)),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(2.dp, Color(0xFFB99BFF), RoundedCornerShape(6.dp)),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = branch.node.title,
                            color = if (currentPath.contains(branch.node.id)) Color(0xFFFFC08A) else Color(0xFFF3F0FF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (branch.children.isNotEmpty()) "Capa ${depth + 1}" else "Tarea",
                            color = Color(0xFFB9B4C9),
                            fontSize = 11.sp,
                        )
                    }

                    if (branch.children.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFFFFA45B),
                        )
                    }
                }
            }
        }

        if (branch.children.isNotEmpty() && isExpanded) {
            branch.children.forEach { child ->
                LayerMapBranch(
                    branch = child,
                    depth = depth + 1,
                    currentPath = currentPath,
                    expandedById = expandedById,
                    onNavigateTo = onNavigateTo,
                )
            }
        }
    }
}

@Composable
private fun LayerTrailRoute(
    projectName: String,
    pathNodes: List<Node>,
    currentPath: List<String>,
    onProjectClick: () -> Unit,
    onLayerClick: (Int) -> Unit,
) {
    val items = buildList {
        add(ProjectRouteItem(label = projectName, isRoot = true, nodeId = null))
        pathNodes.forEachIndexed { index, node ->
            add(ProjectRouteItem(label = node.title, isRoot = false, nodeId = node.id, depth = index + 1))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (item.isRoot) onProjectClick() else onLayerClick(index - 1)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(30.dp),
                ) {
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(16.dp)
                                .background(Color(0xFF7C5CFF)),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFFA45B))
                            .border(2.dp, Color(0xFF050505), RoundedCornerShape(50)),
                    )
                    if (index < items.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(18.dp)
                                .background(Color(0xFF7C5CFF)),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(2.dp)
                        .background(Color(0xFF7C5CFF)),
                )

                Spacer(modifier = Modifier.width(10.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp, max = 72.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C)),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.isRoot) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = Color(0xFFB99BFF),
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.cebolla_icon),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(Color(0xFFB99BFF)),
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = if (item.isRoot) "Proyecto raíz" else "Capa ${item.depth}",
                                color = Color(0xFFB9B4C9),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.label,
                                color = Color(0xFFF3F0FF),
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 17.sp,
                            )
                        }

                        if (!item.isRoot) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFFFFA45B),
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ProjectRouteItem(
    val label: String,
    val isRoot: Boolean,
    val nodeId: String? = null,
    val depth: Int = 0,
)

@Composable
private fun FutureNavigationDrawer(onDismiss: () -> Unit) {
    val items = listOf(
        "Inicio",
        "Inbox",
        "Hoy",
        "Próximas",
        "Proyectos",
        "Etiquetas",
        "Personas",
        "Calendario",
        "Panel",
        "Notas",
        "Archivos",
        "Configuración",
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(Color(0xFF121316).copy(alpha = 0.98f))
            .padding(start = 18.dp, end = 12.dp, top = 18.dp, bottom = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.arachn0de_logo),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Arachn0de",
                    color = Color(0xFFE4D8FF),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                    contentDescription = null,
                    tint = Color(0xFFD8D9FF),
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        items.forEachIndexed { index, item ->
            val selected = index == 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color(0xFF4B3E76).copy(alpha = 0.75f) else Color.Transparent)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) Color(0xFFB78DFF) else Color(0xFF2A2A2D)),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = item,
                    color = if (selected) Color(0xFFF5F1FF) else Color(0xFFE0E7FF).copy(alpha = 0.8f),
                    fontSize = 16.sp,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "v0.1.0",
            color = Color(0xFFB5C4E8),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun HeaderBar(onMenuClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .size(42.dp)
                .background(Color(0xFF121316), RoundedCornerShape(12.dp)),
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = null,
                tint = Color(0xFFB99BFF),
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.arachn0de_logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "Arachn0de",
                color = Color(0xFFF3F6FF),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
            )
        }

        IconButton(
            onClick = {},
            modifier = Modifier.size(42.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color(0xFFB99BFF),
            )
        }
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: String,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C)),
    ) {
        Row(
            modifier = Modifier
                .wrapContentHeight()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A1F39)),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, color = Color(0xFFB99BFF), fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.wrapContentHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    color = Color(0xFFB6C6ED),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    color = Color(0xFFF1F4FF),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyProjectsState(onCreateProject: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C).copy(alpha = 0.96f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Todavía no hay proyectos",
                color = Color(0xFFF0F5FF),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Crea tu primer proyecto para empezar a organizar ideas, objetivos y seguimiento.",
                color = Color(0xFFB9C4E3),
                fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onCreateProject,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8863FF)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nuevo proyecto")
            }
        }
    }
}

@Composable
private fun ProjectList(
    projects: List<Project>,
    onCreateProject: () -> Unit,
    onEdit: (Project) -> Unit,
    onDelete: (Project) -> Unit,
    onOpenProject: (Project) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        projects.forEach { project ->
            ProjectCard(
                project = project,
                onOpen = { onOpenProject(project) },
                onEdit = { onEdit(project) },
                onDelete = { onDelete(project) },
            )
        }

        Button(
            onClick = onCreateProject,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E61FF)),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Nuevo proyecto")
        }
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF3A2C52).copy(alpha = 0.85f), RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF7A4AEF)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = project.name.take(1).uppercase(Locale.getDefault()),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = project.name,
                    color = Color(0xFFF1F6FF),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = true,
                )
                if (project.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = project.description,
                        color = Color(0xFFB9C8E8),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = true,
                        fontSize = 12.sp,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar proyecto", tint = Color(0xFFB99BFF))
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar proyecto", tint = Color(0xFFFA8B8B))
                }
            }
        }
    }
}

@Composable
private fun ProjectDialog(
    project: Project?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember(project?.id) { mutableStateOf(project?.name ?: "") }
    var description by remember(project?.id) { mutableStateOf(project?.description ?: "") }

    AlertDialog(
        containerColor = Color(0xFF17181C),
        titleContentColor = Color(0xFFF3F6FF),
        textContentColor = Color(0xFFB7C5E7),
        onDismissRequest = onDismiss,
        title = { Text(if (project == null) "Nuevo proyecto" else "Editar proyecto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanName = name.trim()
                    if (cleanName.isNotEmpty()) {
                        onSave(cleanName, description.trim())
                    }
                },
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

private fun formatProjectDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return formatter.format(timestamp)
}