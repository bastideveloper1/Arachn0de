package com.r0ybt.arachn0de

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.domain.model.Project
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val database by lazy { Arachn0deDatabase.create(applicationContext) }
    private val projectRepository by lazy { ProjectRepository(database.projectDao()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Arachn0deTheme(darkTheme = true, dynamicColor = false) {
                ProjectDashboardScreen(projectRepository)
            }
        }
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }
}

@Composable
fun ProjectDashboardScreen(repository: ProjectRepository) {
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
            Color(0xFF020A18),
            Color(0xFF071326),
            Color(0xFF0A132D),
        ),
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF020A18),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .border(1.dp, Color(0xFF5647A8).copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF07152D).copy(alpha = 0.96f))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Proyectos",
                        value = projects.size.toString(),
                        icon = "□",
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Activos",
                        value = "0",
                        icon = "✓",
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
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
                    )
                }
            }

            if (showDrawer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF020A18).copy(alpha = 0.45f))
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
            .background(Color(0xFF0B1222).copy(alpha = 0.98f))
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
                        .background(if (selected) Color(0xFFB78DFF) else Color(0xFF334060)),
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
                .border(1.dp, Color(0xFF4A5E93).copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = null,
                tint = Color(0xFFD8D9FF),
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
                tint = Color(0xFFD8D9FF),
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
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101B2E)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF3F4076)),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, color = Color(0xFFB392FF), fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, color = Color(0xFFB6C6ED), fontSize = 12.sp)
                Text(
                    value,
                    color = Color(0xFFF1F4FF),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EmptyProjectsState(onCreateProject: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1830).copy(alpha = 0.9f)),
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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101B2E)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF2A3D69).copy(alpha = 0.8f), RoundedCornerShape(18.dp)),
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
                    .background(Color(0xFF8D73FF)),
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
        containerColor = Color(0xFF0E1B33),
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