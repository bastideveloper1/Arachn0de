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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.domain.model.Project
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.ui.state.ProjectActions

@Composable
internal fun ProjectDashboardScreen(repository: ProjectRepository, projects: List<Project>, onOpenProject: (Project) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val actions = remember(repository, scope) { ProjectActions(repository, scope) }
    var showProjectDialog by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<Project?>(null) }
    var deletingProject by remember { mutableStateOf<Project?>(null) }
    var showDrawer by remember { mutableStateOf(false) }

    BackHandler(enabled = showDrawer) { showDrawer = false }

    val background = Brush.linearGradient(
        listOf(
            Arachn0deColors.Background,
            Arachn0deColors.BackgroundMiddle,
            Arachn0deColors.BackgroundEnd,
        ),
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Arachn0deColors.Background,
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
                    color = Arachn0deColors.TextPrimary,
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
                        .background(Arachn0deColors.Scrim.copy(alpha = 0.45f))
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
            isSubmitting = actions.operation.busy,
            onDismiss = {
                showProjectDialog = false
                editingProject = null
            },
            onSave = { name, description ->
                actions.save(editingProject?.id, name, description) {
                    showProjectDialog = false
                    editingProject = null
                }
            },
        )
    }

    deletingProject?.let { project ->
        AlertDialog(
            containerColor = Arachn0deColors.Surface,
            titleContentColor = Arachn0deColors.TextPrimary,
            textContentColor = Arachn0deColors.TextSecondary,
            onDismissRequest = { if (!actions.operation.busy) deletingProject = null },
            title = { Text("Eliminar proyecto") },
            text = { Text("¿Seguro que quieres eliminar “${project.name}”? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    enabled = !actions.operation.busy,
                    onClick = {
                        actions.delete(project.id) { deletingProject = null }
                    },
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(enabled = !actions.operation.busy, onClick = { deletingProject = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
    OperationErrorDialog(actions.operation)
}

