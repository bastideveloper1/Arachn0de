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
import com.r0ybt.arachn0de.ui.state.EditorDraft
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.r0ybt.arachn0de.domain.model.NodeProgress
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
internal fun ProjectDashboardScreen(
    repository: ProjectRepository,
    projects: List<Project>,
    projectProgressById: Map<String, NodeProgress>,
    onOpenProject: (Project) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    onOpenPeople: () -> Unit = {},
    onOpenAttention: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenObligations: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    projectAttentionById: Map<String, com.r0ybt.arachn0de.domain.model.AttentionSummary> = emptyMap(),
) {
    val scope = rememberCoroutineScope()
    val actions = remember(repository, scope) { ProjectActions(repository, scope) }
    var draft by rememberSaveable(stateSaver = EditorDraft.Saver) { mutableStateOf<EditorDraft?>(null) }
    var deletingProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingProjectName by rememberSaveable { mutableStateOf("") }
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

                if (projects.isEmpty()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        EmptyProjectsState(onCreateProject = { draft = EditorDraft(null, null, "", "") })
                    }
                } else {
                    ProjectList(
                        projects = projects,
                        projectProgressById = projectProgressById,
                        projectAttentionById = projectAttentionById,
                        reorderBusy = actions.operation.busy,
                        reorderError = actions.operation.error,
                        listState = listState,
                        onCreateProject = { draft = EditorDraft(null, null, "", "") },
                        onEdit = { project ->
                            draft = EditorDraft(project.id, null, project.name, project.description)
                        },
                        onDelete = { project ->
                            deletingProjectId = project.id
                            deletingProjectName = project.name
                        },
                        onOpenProject = onOpenProject,
                        onReorderTo = { fromId, toId ->
                            actions.reorderTo(fromId, toId)
                        },
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
                    AppIdentityDrawer(
                        onDismiss = { showDrawer = false },
                        onPeople = { showDrawer = false; onOpenPeople() },
                        onAttention = { showDrawer = false; onOpenAttention() },
                        onCalendar = { showDrawer = false; onOpenCalendar() },
                        onObligations = { showDrawer = false; onOpenObligations() },
                        onAbout = { showDrawer = false; onOpenAbout() },
                    )
                }
            }
        }
    }

    draft?.let { editor ->
        ProjectDialog(
            draft = editor,
            isSubmitting = actions.operation.busy,
            onDismiss = {
                draft = null
            },
            onSave = { name, description ->
                actions.save(editor.id, name, description, editor.creationId) {
                    draft = null
                }
            },
        )
    }

    deletingProjectId?.let { deletingId ->
        AlertDialog(
            containerColor = Arachn0deColors.Surface,
            titleContentColor = Arachn0deColors.TextPrimary,
            textContentColor = Arachn0deColors.TextSecondary,
            onDismissRequest = { if (!actions.operation.busy) deletingProjectId = null },
            title = { Text("Eliminar proyecto") },
            text = { Text("¿Seguro que quieres eliminar “${deletingProjectName}”? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    enabled = !actions.operation.busy,
                    onClick = {
                        actions.delete(deletingId) { deletingProjectId = null }
                    },
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(enabled = !actions.operation.busy, onClick = { deletingProjectId = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
    OperationErrorDialog(actions.operation)
}

