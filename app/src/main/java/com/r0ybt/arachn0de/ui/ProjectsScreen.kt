package com.r0ybt.arachn0de.ui

import kotlinx.coroutines.flow.first

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
import com.r0ybt.arachn0de.domain.model.ProjectCardAlerts
import androidx.compose.ui.semantics.clearAndSetSemantics
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
    projectAlertsById: Map<String, ProjectCardAlerts>,
    onOpenProject: (Project) -> Unit = {},
    onConverted: (String, String?) -> Unit = { id, _ -> projects.firstOrNull { it.id == id }?.let(onOpenProject) },
    conversionScope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope(),
    listState: LazyListState = rememberLazyListState(),
    onOpenPeople: () -> Unit = {},
    onOpenAttention: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenObligations: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenGame: () -> Unit = {},
    onOpenMetro: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    recurrenceContent: @Composable () -> Unit = {},
    exportTree: com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot = com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot(emptyList()),
    copyDescendantsReady: Boolean = false,
    onOpenTechnologies: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val copyActions = remember(context,scope) { com.r0ybt.arachn0de.ui.state.NodeCopyActions(context,scope) { (context.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).personRepository.observeAllAssignments().first() } }
    var photoProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    projects.firstOrNull { it.id == photoProjectId }?.let { ProjectPhotoDialog(it, onClose = { photoProjectId = null }) }
    var moveProject by rememberSaveable { mutableStateOf<String?>(null) }
    projects.firstOrNull { it.id==moveProject }?.let { moving -> ProjectMoveDialog(repository,moving,{ moveProject=null },{ moveProject=null }, onConverted = { id, layer -> moveProject=null; onConverted(id, layer) }, operationScope = conversionScope) }
    val actions = remember(repository, scope) { ProjectActions(repository, scope, (context.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).attachmentRepository) }
    val drafts = rememberSaveable(saver = com.r0ybt.arachn0de.ui.state.EditorDraftStore.Saver) { com.r0ybt.arachn0de.ui.state.EditorDraftStore() }
    val draft = drafts.active
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
                    .then(if(showDrawer) Modifier.clearAndSetSemantics {} else Modifier)
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

                recurrenceContent()
                Spacer(modifier = Modifier.height(12.dp))

                if (projects.isEmpty()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        EmptyProjectsState(onCreateProject = { drafts.open(null) { EditorDraft(null, null, "", "") } })
                    }
                } else {
                    ProjectList(
                        projects = projects,
                        onPhoto = { photoProjectId = it.id },
                        onMoveInside = { moveProject=it.id },
                        onCopy = { project, descendants -> copyActions.copyProject(project,exportTree,descendants) },
                        canCopy = !copyActions.busy,
                        canCopyDescendants = copyDescendantsReady,
                        projectAlertsById = projectAlertsById,
                        reorderBusy = actions.operation.busy,
                        reorderError = actions.operation.error,
                        listState = listState,
                        onCreateProject = { drafts.open(null) { EditorDraft(null, null, "", "") } },
                        onEdit = { project ->
                            drafts.open(null, project.id) { EditorDraft(project.id, null, project.name, project.description) }
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
                        .fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize().background(Arachn0deColors.Scrim.copy(alpha = 0.45f)).clickable { showDrawer = false }.clearAndSetSemantics {})
                    AppIdentityDrawer(
                        onDismiss = { showDrawer = false },
                        onProjects = {},
                        projectsSelected = true,
                        onPeople = { showDrawer = false; onOpenPeople() }, onTechnologies = { showDrawer = false; onOpenTechnologies() },
                        onAttention = { showDrawer = false; onOpenAttention() },
                        onCalendar = { showDrawer = false; onOpenCalendar() },
                        onObligations = { showDrawer = false; onOpenObligations() },
                        onAppearance = { showDrawer = false; onOpenAppearance() }, onMetro = { showDrawer = false; onOpenMetro() }, onGame = { showDrawer = false; onOpenGame() }, onAbout = { showDrawer = false; onOpenAbout() },
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
                drafts.close()
            },
            onDiscard = { actions.discardDraft(editor) { drafts.clear() } },
            onSave = { name, description ->
                actions.save(editor.id, name, description, editor.creationId, draft = editor) {
                    drafts.clear()
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
                DestructiveAction(
                    label = "Eliminar",
                    enabled = !actions.operation.busy,
                    onClick = {
                        actions.delete(deletingId) { deletingProjectId = null }
                    },
                )
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

