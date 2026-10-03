package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
    var generation by rememberSaveable { mutableStateOf(0) }
    androidx.compose.runtime.key(generation) {
        AppRootContent(projectRepository, nodeRepository, clock) { generation++ }
    }
}

@Composable
private fun AppRootContent(projectRepository: ProjectRepository, nodeRepository: NodeRepository, clock: () -> Long, onRestored: () -> Unit) {
    val tagState by remember(nodeRepository) { nodeRepository.tags.observe() }.collectAsState(initial = com.r0ybt.arachn0de.domain.model.TagState())
    val personRepository = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).personRepository
    val screenStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showPeople by rememberSaveable { mutableStateOf(false) }
    var showObligations by rememberSaveable { mutableStateOf(false) }
    var returnToObligations by rememberSaveable { mutableStateOf(false) }
    var people by remember { mutableStateOf(emptyList<Person>()) }
    var financialPeopleLoaded by remember { mutableStateOf(false) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var returnToCalendar by rememberSaveable { mutableStateOf(false) }
    var zoneId by remember { mutableStateOf(java.util.TimeZone.getDefault().id) }
    var showAttention by rememberSaveable { mutableStateOf(false) }
    var returnToAttention by rememberSaveable { mutableStateOf(false) }
    var openNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    val sortPreferences = rememberSaveable(saver = com.r0ybt.arachn0de.ui.state.NodeSortPreferences.Saver) {
        com.r0ybt.arachn0de.ui.state.NodeSortPreferences()
    }
    val projectsListState = rememberLazyListState()
    var selectedProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var projects by remember(projectRepository) { mutableStateOf<List<Project>?>(null) }
    var allState by remember(nodeRepository) { mutableStateOf(NodeTreeSnapshot(emptyList())) }
    var nodesLoaded by remember(nodeRepository) { mutableStateOf(false) }
    var assignmentsLoaded by remember { mutableStateOf(false) }
    var responsibleByNode by remember { mutableStateOf(emptyMap<String, List<Person>>()) }
    val load = remember(projectRepository) { LoadState() }
    val nodesLoad = remember(nodeRepository) { LoadState() }
    val financialPeopleLoad = remember(personRepository) { LoadState() }
    val peopleLoad = remember(personRepository) { LoadState() }
    LaunchedEffect(projectRepository, load.attempt) {
        load.collect(projectRepository.observeProjects()) { items ->
            projects = items
            if (selectedProjectId != null && items.none { it.id == selectedProjectId }) {
                selectedProjectId = null
                openNodeId = null
                if (returnToCalendar) { showCalendar = true; returnToCalendar = false }
                if (returnToObligations) { showObligations = true; returnToObligations = false }
            }
        }
    }
    // A coherent global read powers the existing progress and the transversal projection.
    LaunchedEffect(nodeRepository, nodesLoad.attempt) {
        nodesLoad.collect(nodeRepository.observeAllState()) { allState = it; nodesLoaded = true }
    }
    LaunchedEffect(showAttention, showCalendar, showObligations, personRepository, peopleLoad.attempt) {
        if (showAttention || showCalendar || showObligations) {
            assignmentsLoaded = false
            peopleLoad.collect(personRepository.observeAllAssignments()) { responsibleByNode = it; assignmentsLoaded = true }
        }
    }
    LaunchedEffect(showAttention, showObligations, showCalendar, selectedProjectId, personRepository, financialPeopleLoad.attempt) {
        if (showAttention || showObligations || showCalendar || selectedProjectId == null) {
            financialPeopleLoaded = false
            financialPeopleLoad.collect(personRepository.observePeople()) { people = it; financialPeopleLoaded = true }
        }
    }
    val projectProgressById = projects.orEmpty().associate { project ->
        project.id to (allState.projectProgressById[project.id] ?: NodeProgress(project.id, 0, 0, 0, NodeProgressState.NO_WORK))
    }
    val globalView = !showSettings && !showAbout && !showPeople && (showObligations || showCalendar || showAttention || selectedProjectId == null)
    val now = if (globalView) rememberTaskScreenNow(allState.nodes, clock, onRefresh = { zoneId = java.util.TimeZone.getDefault().id }) else 0L
    val calendar = if (showCalendar) {
        val snapshot by com.r0ybt.arachn0de.ui.state.rememberCalendar(allState, zoneId)
        snapshot
    } else null
    val attention = if (globalView && !showCalendar && !showObligations) {
        val state by rememberAttention(allState, now, zoneId)
        state
    } else null

    val selectedProject = projects?.firstOrNull { it.id == selectedProjectId }
    if (showSettings) {
        screenStates.SaveableStateProvider("settings") {
            val settingsPeople by remember(personRepository) { personRepository.observePeople() }.collectAsState(initial = emptyList())
            CreationDefaultsScreen(nodeRepository.creationDefaults, com.r0ybt.arachn0de.domain.defaults.DefaultsScope.Global, "Global", tagState.tags, settingsPeople) {
                showSettings = false; screenStates.removeState("settings")
            }
        }
    } else if (showAbout) {
        screenStates.SaveableStateProvider("about") { AboutScreen(onBack = { showAbout = false }, onRestored = onRestored) }
    } else if (showPeople) {
        screenStates.SaveableStateProvider("people") { PeopleScreen(personRepository) { showPeople = false } }
    } else if (showObligations) {
        screenStates.SaveableStateProvider("obligations") {
            ObligationsScreen(allState, projects.orEmpty(), people, responsibleByNode,
                nodesLoaded && projects != null && assignmentsLoaded && financialPeopleLoaded, now, zoneId,
                onOpen = { node ->
                    val real = allState.nodesById[node.id]
                    if (real != null && real.isCompletable && real.obligation != null && projects.orEmpty().any { it.id == real.projectId }) {
                        selectedProjectId = real.projectId; openNodeId = real.id
                        returnToObligations = true; returnToCalendar = false; returnToAttention = false
                        showObligations = false
                    }
                }, onBack = { showObligations = false })
        }
    } else if (showCalendar) {
        screenStates.SaveableStateProvider("calendar") {
            CalendarScreen(calendar, projects.orEmpty(), responsibleByNode, nodesLoaded && projects != null && assignmentsLoaded,
                now, zoneId,
                onOpen = { node ->
                    val real = allState.nodesById[node.id]
                    if (real != null && real.isCompletable && real.dueAt != null && projects.orEmpty().any { it.id == real.projectId }) {
                        selectedProjectId = real.projectId
                        openNodeId = real.id
                        returnToCalendar = true
                        returnToObligations = false
                        returnToAttention = false
                        showCalendar = false
                    }
                },
                people = people, peopleLoaded = financialPeopleLoaded, tagState = tagState,
                onBack = { showCalendar = false },
            )
        }
    } else if (showAttention) {
        screenStates.SaveableStateProvider("attention") {
            AttentionScreen(attention, projects.orEmpty(), responsibleByNode, nodesLoaded && projects != null && assignmentsLoaded, tagState = tagState, people = people,
                onOpen = { node ->
                    val real = allState.nodesById[node.id]
                    if (real != null && projects.orEmpty().any { it.id == real.projectId }) {
                        selectedProjectId = real.projectId
                        openNodeId = real.id
                        returnToAttention = true
                        returnToObligations = false
                        returnToCalendar = false
                        showAttention = false
                    }
                },
                onBack = { showAttention = false },
            )
        }
    } else if (selectedProjectId == null) {
        ProjectDashboardScreen(
            exportTree = allState,
            copyDescendantsReady = nodesLoaded,
            recurrenceContent = { TagManager(nodeRepository.tags, tagState); RecurrenceManager(nodeRepository.recurrence, "", emptyList(), people, financialPeopleLoaded) },
            repository = projectRepository,
            listState = projectsListState,
            projects = projects.orEmpty(),
            projectProgressById = projectProgressById,
            projectAttentionById = attention?.byProjectId.orEmpty(),
            onOpenProject = { selectedProjectId = it.id; returnToAttention = false; returnToCalendar = false; returnToObligations = false },
            onOpenPeople = { showPeople = true },
            onOpenAttention = { showAttention = true },
            onOpenCalendar = { showCalendar = true },
            onOpenObligations = { showObligations = true },
            onOpenSettings = { showSettings = true },
            onOpenAbout = { showAbout = true },
        )
    } else if (selectedProject != null) {
        screenStates.SaveableStateProvider("project:${selectedProject.id}") {
            ProjectNodeScreen(
                project = selectedProject,
                sortPreferences = sortPreferences,
                onOpenProjects = { selectedProjectId = null; openNodeId = null; returnToCalendar = false; returnToAttention = false; returnToObligations = false },
                nodeRepository = nodeRepository,
                personRepository = personRepository,
                clock = clock,
                openNodeId = openNodeId,
                onOpenNodeHandled = { openNodeId = null },
                onOpenPeople = { showPeople = true },
                onOpenAttention = { showAttention = true },
                onOpenCalendar = { showCalendar = true },
                onOpenObligations = { showObligations = true },
                onOpenSettings = { showSettings = true },
                onOpenAbout = { showAbout = true },
                onBackToObligations = if (returnToObligations) ({
                    screenStates.removeState("project:${selectedProject.id}")
                    selectedProjectId = null; openNodeId = null
                    showObligations = true; returnToObligations = false
                }) else null,
                onBackToCalendar = if (returnToCalendar) ({
                    screenStates.removeState("project:${selectedProject.id}")
                    selectedProjectId = null
                    openNodeId = null
                    showCalendar = true
                    returnToCalendar = false
                }) else null,
                onBackToProjects = {
                    screenStates.removeState("project:${selectedProject.id}")
                    selectedProjectId = null
                    if (returnToObligations) { showObligations = true; returnToObligations = false }
                    if (returnToAttention) { showAttention = true; returnToAttention = false }
                    if (returnToCalendar) { showCalendar = true; returnToCalendar = false }
                },
            )
        }
    } else {
        BackHandler { selectedProjectId = null; openNodeId = null }
        Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {}
    }
    LoadErrorDialog(load)
    LoadErrorDialog(nodesLoad)
    if (showAttention || showObligations || showCalendar || selectedProjectId == null) LoadErrorDialog(financialPeopleLoad)
    if (showAttention || showCalendar || showObligations) LoadErrorDialog(peopleLoad)
}
