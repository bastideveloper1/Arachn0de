package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.ui.state.LoadState
import com.r0ybt.arachn0de.ui.state.rememberAttention
import com.r0ybt.arachn0de.ui.state.rememberTaskScreenNow

@Composable
internal fun AppRoot(projectRepository: ProjectRepository, nodeRepository: NodeRepository, clock: () -> Long = System::currentTimeMillis) {
    val personRepository = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).personRepository
    val screenStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var showPeople by rememberSaveable { mutableStateOf(false) }
    var showAttention by rememberSaveable { mutableStateOf(false) }
    var returnToAttention by rememberSaveable { mutableStateOf(false) }
    var openNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    val projectsListState = rememberLazyListState()
    var selectedProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var projects by remember(projectRepository) { mutableStateOf<List<Project>?>(null) }
    var allState by remember(nodeRepository) { mutableStateOf(NodeTreeSnapshot(emptyList())) }
    var nodesLoaded by remember(nodeRepository) { mutableStateOf(false) }
    var assignmentsLoaded by remember { mutableStateOf(false) }
    var responsibleByNode by remember { mutableStateOf(emptyMap<String, List<Person>>()) }
    val load = remember(projectRepository) { LoadState() }
    val nodesLoad = remember(nodeRepository) { LoadState() }
    val peopleLoad = remember(personRepository) { LoadState() }
    LaunchedEffect(projectRepository, load.attempt) {
        load.collect(projectRepository.observeProjects()) { items ->
            projects = items
            if (selectedProjectId != null && items.none { it.id == selectedProjectId }) {
                selectedProjectId = null
                openNodeId = null
            }
        }
    }
    // A coherent global read powers the existing progress and the transversal projection.
    LaunchedEffect(nodeRepository, nodesLoad.attempt) {
        nodesLoad.collect(nodeRepository.observeAllState()) { allState = it; nodesLoaded = true }
    }
    LaunchedEffect(showAttention, personRepository, peopleLoad.attempt) {
        if (showAttention) {
            assignmentsLoaded = false
            peopleLoad.collect(personRepository.observeAllAssignments()) { responsibleByNode = it; assignmentsLoaded = true }
        }
    }
    val projectProgressById = projects.orEmpty().associate { project ->
        project.id to (allState.projectProgressById[project.id] ?: NodeProgress(project.id, 0, 0, 0, NodeProgressState.NO_WORK))
    }
    val globalView = !showPeople && (showAttention || selectedProjectId == null)
    val attention = if (globalView) {
        val now = rememberTaskScreenNow(allState.nodes, clock)
        val state by rememberAttention(allState, now)
        state
    } else null

    val selectedProject = projects?.firstOrNull { it.id == selectedProjectId }
    if (showPeople) {
        screenStates.SaveableStateProvider("people") { PeopleScreen(personRepository) { showPeople = false } }
    } else if (showAttention) {
        screenStates.SaveableStateProvider("attention") {
            AttentionScreen(attention, projects.orEmpty(), responsibleByNode, nodesLoaded && projects != null && assignmentsLoaded,
                onOpen = { node ->
                    val real = allState.nodesById[node.id]
                    if (real != null && projects.orEmpty().any { it.id == real.projectId }) {
                        selectedProjectId = real.projectId
                        openNodeId = real.id
                        returnToAttention = true
                        showAttention = false
                    }
                },
                onBack = { showAttention = false },
            )
        }
    } else if (selectedProjectId == null) {
        ProjectDashboardScreen(
            repository = projectRepository,
            listState = projectsListState,
            projects = projects.orEmpty(),
            projectProgressById = projectProgressById,
            projectAttentionById = attention?.byProjectId.orEmpty(),
            onOpenProject = { selectedProjectId = it.id; returnToAttention = false },
            onOpenPeople = { showPeople = true },
            onOpenAttention = { showAttention = true },
        )
    } else if (selectedProject != null) {
        screenStates.SaveableStateProvider("project:${selectedProject.id}") {
            ProjectNodeScreen(
                project = selectedProject,
                nodeRepository = nodeRepository,
                personRepository = personRepository,
                clock = clock,
                openNodeId = openNodeId,
                onOpenNodeHandled = { openNodeId = null },
                onOpenPeople = { showPeople = true },
                onOpenAttention = { showAttention = true },
                onBackToProjects = {
                    screenStates.removeState("project:${selectedProject.id}")
                    selectedProjectId = null
                    if (returnToAttention) { showAttention = true; returnToAttention = false }
                },
            )
        }
    } else {
        BackHandler { selectedProjectId = null; openNodeId = null }
        Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {}
    }
    LoadErrorDialog(load)
    LoadErrorDialog(nodesLoad)
    if (showAttention) LoadErrorDialog(peopleLoad)
}
