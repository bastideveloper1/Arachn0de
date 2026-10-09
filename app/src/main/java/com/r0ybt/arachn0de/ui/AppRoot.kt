package com.r0ybt.arachn0de.ui

import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
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
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showMetro by rememberSaveable { mutableStateOf(false) }
    var metroRequestToken by rememberSaveable { mutableStateOf(0L) }
    var metroNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { com.r0ybt.arachn0de.metro.MetroNavigation.requests.collect { request -> if(request!=null) { metroNodeId=request.nodeId;metroRequestToken=request.token;showMetro=true;com.r0ybt.arachn0de.metro.MetroNavigation.requests.value=null } } }
    var showGame by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showTags by rememberSaveable {mutableStateOf(false)}
    var showTechnologies by rememberSaveable { mutableStateOf(false) }
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
    val sortContext = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication
    val sortScope = androidx.compose.runtime.rememberCoroutineScope()
    val sortRepository = remember(sortContext) { com.r0ybt.arachn0de.data.repository.NodeSortPreferenceRepository(sortContext.database) }
    val sortMutex = remember { kotlinx.coroutines.sync.Mutex() }
    val sortPreferences = remember(sortRepository) {
        com.r0ybt.arachn0de.ui.state.NodeSortPreferences(onWrite = { context, mode ->
            sortScope.launch { sortMutex.withLock { sortRepository.set(context, mode?.name) } }
        }, onLayersWrite={context,enabled,mode->sortScope.launch {sortMutex.withLock {sortRepository.set(context,mode.name,enabled)}}})
    }
    LaunchedEffect(sortRepository) { sortRepository.observe().collect { sortPreferences.replace(it) } }
    val conversionScope = androidx.compose.runtime.rememberCoroutineScope()
    val projectsListState = rememberLazyListState()
    var selectedProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var projects by remember(projectRepository) { mutableStateOf<List<Project>?>(null) }
    var allState by remember(nodeRepository) { mutableStateOf(NodeTreeSnapshot(emptyList())) }
    val metroRepository=(androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).metroRepository
    val metroSnapshot by remember(metroRepository) {metroRepository.observe()}.collectAsState(initial=null)
    val metroContext=androidx.compose.ui.platform.LocalContext.current
    val metroTime by androidx.compose.runtime.produceState(com.r0ybt.arachn0de.metro.metroTime(metroContext),metroRepository) {while(true) {value=com.r0ybt.arachn0de.metro.metroTime(metroContext);kotlinx.coroutines.delay(30_000)}}
    val metroActivity by androidx.compose.runtime.produceState<Map<String,List<com.r0ybt.arachn0de.metro.MetroActivityEntry>>>(emptyMap(),allState,metroSnapshot,metroTime) {
        value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {metroSnapshot?.let {com.r0ybt.arachn0de.metro.MetroActivity.hierarchy(allState.nodes,it,metroTime)}.orEmpty()}
    }
    val metroPreviews by androidx.compose.runtime.produceState<Map<String,String>>(emptyMap(),metroSnapshot,metroTime) {
        value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {metroSnapshot?.let {snapshot->val network=snapshot.preferences.network;snapshot.journeys.mapNotNull {j->j.row.nodeId?.let {it to com.r0ybt.arachn0de.metro.MetroActivity.summary(j,network,metroTime)}}.toMap()}.orEmpty()}
    }
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
    if(showTags) TagManager(nodeRepository.tags,tagState,showAccess=false,initiallyOpen=true,onClose={showTags=false})
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
    val globalView = !showMetro && !showGame && !showAppearance && !showAbout && !showPeople && !showTechnologies && (showObligations || showCalendar || showAttention || selectedProjectId == null)
    val projectsView = globalView && !showCalendar && !showObligations && !showAttention
    val now = if (globalView) rememberTaskScreenNow(allState.nodes, clock, onRefresh = { zoneId = java.util.TimeZone.getDefault().id }, dayOnly = projectsView) else 0L
    val calendar = if (showCalendar) {
        val snapshot by com.r0ybt.arachn0de.ui.state.rememberCalendar(allState, zoneId)
        snapshot
    } else null
    val attention = if (globalView && showAttention) {
        val state by rememberAttention(allState, now, zoneId)
        state
    } else null

    val projectAlerts = if (projectsView) {
        val alerts by com.r0ybt.arachn0de.ui.state.rememberProjectCardAlerts(allState, now, zoneId)
        alerts
    } else emptyMap()

    val selectedProject = projects?.firstOrNull { it.id == selectedProjectId }
    androidx.compose.runtime.CompositionLocalProvider(com.r0ybt.arachn0de.metro.LocalMetroActivity provides metroActivity,com.r0ybt.arachn0de.metro.LocalMetroPreview provides metroPreviews) {
    if (showMetro) {
        screenStates.SaveableStateProvider("metro:${metroNodeId ?: "main"}") { com.r0ybt.arachn0de.metro.MetroScreen(allState.nodes,projects.orEmpty(),metroNodeId,requestToken=metroRequestToken,onBack={showMetro=false;metroNodeId=null}) }
    } else if (showGame) {
        screenStates.SaveableStateProvider("game") {
            com.r0ybt.arachn0de.game.GameScreen(onBack = { showGame = false })
        }
    } else if (showAppearance) {
        screenStates.SaveableStateProvider("appearance") {
            AppearanceScreen(onBack = { showAppearance = false })
        }
    } else if (showAbout) {
        screenStates.SaveableStateProvider("about") { AboutScreen(onBack = { showAbout = false }, onRestored = onRestored) }
    } else if (showTechnologies) {
        screenStates.SaveableStateProvider("technologies") {
            val repository = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).technologyRepository
            TechnologiesScreen(repository) { showTechnologies = false }
        }
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
            recurrenceContent = { RecurrenceManager(nodeRepository.recurrence, "", emptyList(), people, financialPeopleLoaded) },
            repository = projectRepository,
            listState = projectsListState,
            projects = projects.orEmpty(),
            projectAlertsById = projectAlerts,
            conversionScope = conversionScope,
            onConverted = { id, node -> screenStates.removeState("project:$id"); selectedProjectId = id; openNodeId = node; returnToAttention = false; returnToCalendar = false; returnToObligations = false },
            onOpenProject = { screenStates.removeState("project:${it.id}"); openNodeId = null; selectedProjectId = it.id; returnToAttention = false; returnToCalendar = false; returnToObligations = false },
            onOpenPeople = { showPeople = true },
            onOpenTechnologies = { showTechnologies = true },
            onOpenTags={showTags=true},
            onOpenAttention = { showAttention = true },
            onOpenCalendar = { showCalendar = true },
            onOpenObligations = { showObligations = true },
            onOpenAppearance = { showAppearance = true },
            onOpenAbout = { showAbout = true },
            onOpenGame = { showGame = true },
            onOpenMetro = { metroNodeId=null;metroRequestToken=0L;showMetro=true },
        )
    } else if (selectedProject != null) {
        screenStates.SaveableStateProvider("project:${selectedProject.id}") {
            ProjectNodeScreen(
                project = selectedProject,
                conversionScope = conversionScope,
                onConverted = { id, node -> screenStates.removeState("project:$id"); selectedProjectId = id; openNodeId = node; returnToAttention = false; returnToCalendar = false; returnToObligations = false },
                sortPreferences = sortPreferences,
                onOpenProjects = { screenStates.removeState("project:${selectedProject.id}"); selectedProjectId = null; openNodeId = null; returnToCalendar = false; returnToAttention = false; returnToObligations = false },
                nodeRepository = nodeRepository,
                projectRepository = projectRepository,
                personRepository = personRepository,
                clock = clock,
                openNodeId = openNodeId,
                onOpenNodeHandled = { openNodeId = null },
                onOpenPeople = { showPeople = true },
                onOpenTechnologies = { showTechnologies = true },
            onOpenTags={showTags=true},
                onOpenAttention = { showAttention = true },
                onOpenCalendar = { showCalendar = true },
                onOpenObligations = { showObligations = true },
                onOpenAppearance = { showAppearance = true },
                onOpenAbout = { showAbout = true },
                onOpenGame = { showGame = true },
            onOpenMetro = { metroNodeId=null;metroRequestToken=0L;showMetro=true },
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
}
