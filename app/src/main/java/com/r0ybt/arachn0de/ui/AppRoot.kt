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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    var metroJourneyId by rememberSaveable {mutableStateOf<String?>(null)}
    var metroNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { com.r0ybt.arachn0de.metro.MetroNavigation.requests.collect { request -> if(request!=null) { metroNodeId=request.nodeId;metroJourneyId=request.journeyId;metroRequestToken=request.token;showMetro=true;com.r0ybt.arachn0de.metro.MetroNavigation.requests.value=null } } }
    var showGame by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showTags by rememberSaveable {mutableStateOf(false)}
    var showTechnologies by rememberSaveable { mutableStateOf(false) }
    var personReturnId by rememberSaveable {mutableStateOf<String?>(null)}
    var returnToPerson by rememberSaveable {mutableStateOf(false)}
    var personReturnToList by rememberSaveable {mutableStateOf(false)}
    var personNavigationToken by rememberSaveable {mutableStateOf(0L)}
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
    val indicatorApp=metroContext.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication
    val indicatorTechnologies by indicatorApp.technologyRepository.state.collectAsState()
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
                if(returnToPerson) {showPeople=true;returnToPerson=false}
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

    val accessDb=(androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).database
    val accessRepo=remember(accessDb) {com.r0ybt.arachn0de.access.AccessRepository(accessDb)}
    val accessState by remember(accessRepo) {accessRepo.observe()}.collectAsState(initial=com.r0ybt.arachn0de.access.AccessState())
    val accessIndex by androidx.compose.runtime.produceState<List<com.r0ybt.arachn0de.access.ElementResult>?>(null,projects,allState,nodesLoaded) {
        if(projects!=null && nodesLoaded) value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {com.r0ybt.arachn0de.access.ElementSearch.index(projects.orEmpty(),allState.nodes)}
    }
    LaunchedEffect(selectedProjectId,accessRepo) {selectedProjectId?.let {accessRepo.opened("project:$it")}}
    var drawerOpen by rememberSaveable {mutableStateOf(false)}
    var searchOpen by rememberSaveable {mutableStateOf(false)}
    var searchMode by rememberSaveable {mutableStateOf("Buscar")}
    var searchQuery by rememberSaveable {mutableStateOf("")}
    val searchListState=rememberLazyListState()
    var searchProjectId by rememberSaveable {mutableStateOf<String?>(null)}
    var searchNodeId by rememberSaveable {mutableStateOf<String?>(null)}
    var searchPendingNode by rememberSaveable {mutableStateOf<String?>(null)}
    var searchDestinationToken by rememberSaveable {androidx.compose.runtime.mutableLongStateOf(0)}
    var backupsOpen by rememberSaveable {mutableStateOf(false)}
    var recurrenceRequest by remember {mutableStateOf<String?>(null)}
    var quickOpen by rememberSaveable {mutableStateOf(false)}
    var securityOpen by rememberSaveable {mutableStateOf(false)}
    val securityPage=LocalSecurityPage.current
    val openSecurity=LocalOpenSecurity.current
    val rootProjectActions=projects.orEmpty().any {it.id==selectedProjectId} && !showMetro && !showGame && !showAppearance && !showAbout && !showPeople && !showTechnologies && !showAttention && !showCalendar && !showObligations
    val visibleProjectActions=if(searchOpen) projects.orEmpty().any {it.id==searchProjectId} else rootProjectActions && !backupsOpen && !securityOpen
    val navigation=GlobalNavigation(menu={drawerOpen=true},showLock=!visibleProjectActions)
    fun openSearch(mode:String?=null) {
        securityOpen=false;searchOpen=true;searchProjectId=null;searchNodeId=null;searchPendingNode=null
        if(mode!=null) searchMode=mode
    }
    fun navigate(destination:String) {
        searchOpen=false;searchProjectId=null;searchNodeId=null;backupsOpen=false;securityOpen=false
        showPeople=destination=="people";showTechnologies=destination=="technologies"
        showAppearance=destination=="appearance";showAbout=destination=="about"
        showMetro=destination=="metro";showGame=destination=="game"
        showAttention=destination=="attention";showCalendar=destination=="calendar";showObligations=destination=="obligations"
        if(destination=="projects") {selectedProjectId=null;openNodeId=null;returnToPerson=false;returnToCalendar=false;returnToObligations=false;returnToAttention=false}
        if(destination=="people") {personReturnId=null;returnToPerson=false}
        if(destination=="metro") {metroNodeId=null;metroJourneyId=null;metroRequestToken=0L}
        if(destination=="tags") showTags=true
    }
    LaunchedEffect(searchProjectId,searchDestinationToken,accessRepo) {searchProjectId?.let {accessRepo.opened("project:$it")}}
    val indicatorParticipation by remember(nodeRepository) {nodeRepository.management.participation.observe("")}.collectAsState(initial=com.r0ybt.arachn0de.data.repository.ParticipationState())
    val indicatorMinute=metroTime.wall / 60_000L
    val indicatorFacts by androidx.compose.runtime.produceState<Map<String,CompactCardFacts>>(emptyMap(),allState,responsibleByNode,indicatorTechnologies,metroSnapshot,indicatorMinute,indicatorParticipation) {
        value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {compactCardFacts(allState.nodes,responsibleByNode,indicatorTechnologies,metroSnapshot,indicatorMinute*60_000L,participation=indicatorParticipation)}
    }
    val indicatorDirectory=remember(allState,people,responsibleByNode,indicatorTechnologies,metroSnapshot,indicatorParticipation) {
        IndicatorDirectory(allState.nodesById,(people+responsibleByNode.values.flatten()+indicatorParticipation.people.map {Person(it.id,it.name,it.avatarFile,it.avatarZoom,it.avatarX,it.avatarY)}).associateBy {it.id},indicatorTechnologies.catalog.associateBy {it.id},metroSnapshot?.journeys.orEmpty().mapNotNull {j->j.row.nodeId?.let {j.row.id to it}}.toMap())
    }
    val reportActions=LocalProjectActionsVisibility.current
    androidx.compose.runtime.SideEffect {reportActions(visibleProjectActions)}
    androidx.compose.runtime.CompositionLocalProvider(LocalGlobalNavigation provides navigation,LocalStoryLabels provides com.r0ybt.arachn0de.data.repository.storyLabels(indicatorParticipation.work.nodes,indicatorParticipation.work.records.roles),LocalQuickCaptureIndex provides accessIndex.orEmpty(),LocalElementAccess provides accessRepo,LocalElementAccessState provides accessState,LocalPersonNavigation provides {destination:PersonDestination->
        if(projects.orEmpty().any {it.id==destination.projectId} && (destination.nodeId==null || allState.nodesById[destination.nodeId]?.projectId==destination.projectId)) {
            searchOpen=false;searchProjectId=null;backupsOpen=false
            personReturnToList=showPeople && (personReturnId==null || personReturnToList)
            personNavigationToken++;personReturnId=destination.personId;returnToPerson=true;showPeople=false;showAttention=false;showCalendar=false;showObligations=false
            returnToAttention=false;returnToCalendar=false;returnToObligations=false
            screenStates.removeState("project:${destination.projectId}");selectedProjectId=destination.projectId;openNodeId=destination.nodeId
        }
    }) {
    Box(Modifier.fillMaxSize()) {
        NavigationContent(active=!searchOpen && !backupsOpen && !drawerOpen && !securityOpen) {
        Box(Modifier.fillMaxSize().then(if(searchOpen || backupsOpen || drawerOpen || securityOpen) Modifier.clearAndSetSemantics {} else Modifier)) {
    val selectedProject = projects?.firstOrNull { it.id == selectedProjectId }
    androidx.compose.runtime.CompositionLocalProvider(com.r0ybt.arachn0de.metro.LocalMetroActivity provides metroActivity,com.r0ybt.arachn0de.metro.LocalMetroPreview provides metroPreviews,LocalCompactCardFacts provides indicatorFacts,LocalIndicatorDirectory provides indicatorDirectory) {
    if (showMetro) {
        screenStates.SaveableStateProvider("metro:${metroJourneyId ?: metroNodeId ?: "main"}") { com.r0ybt.arachn0de.metro.MetroScreen(allState.nodes,projects.orEmpty(),metroNodeId,requestToken=metroRequestToken,journeyId=metroJourneyId,onBack={showMetro=false;metroNodeId=null;metroJourneyId=null}) }
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
        screenStates.SaveableStateProvider("people:${if(personReturnToList) "list" else personReturnId ?: "list"}") { PeopleScreen(personRepository,initialPersonId=if(personReturnToList) null else personReturnId,initialSection=if(personReturnId!=null && !personReturnToList) "Proyectos y trabajo" else "General") { showPeople = false;personReturnId=null;personReturnToList=false } }
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

            repository = projectRepository,
            listState = projectsListState,
            projects = projects.orEmpty(),
            projectAlertsById = projectAlerts,
            conversionScope = conversionScope,
            onConverted = { id, node -> screenStates.removeState("project:$id"); selectedProjectId = id; openNodeId = node; returnToAttention = false; returnToCalendar = false; returnToObligations = false },
            onOpenProject = { screenStates.removeState("project:${it.id}"); openNodeId = null; selectedProjectId = it.id; returnToAttention = false; returnToCalendar = false; returnToObligations = false },
            onOpenPeople = { personReturnId=null;returnToPerson=false;showPeople = true },
            onOpenTechnologies = { showTechnologies = true },
            onOpenTags={showTags=true},
            onOpenAttention = { showAttention = true },
            onOpenCalendar = { showCalendar = true },
            onOpenObligations = { showObligations = true },
            onOpenAppearance = { showAppearance = true },
            onOpenAbout = { showAbout = true },
            onOpenGame = { showGame = true },
            onOpenMetro = { metroNodeId=null;metroJourneyId=null;metroRequestToken=0L;showMetro=true },
        )
    } else if (selectedProject != null) {
        // Recreate the destination even within the same project, dismissing source dialogs.
        androidx.compose.runtime.key(personNavigationToken) {
        screenStates.SaveableStateProvider("project:${selectedProject.id}") {
            ProjectNodeScreen(
                project = selectedProject,
                conversionScope = conversionScope,
                onConverted = { id, node -> screenStates.removeState("project:$id"); selectedProjectId = id; openNodeId = node; returnToAttention = false; returnToCalendar = false; returnToObligations = false },
                sortPreferences = sortPreferences,
                onOpenProjects = { screenStates.removeState("project:${selectedProject.id}"); selectedProjectId = null; openNodeId = null; returnToCalendar = false; returnToAttention = false; returnToObligations = false;returnToPerson=false;personReturnId=null },
                nodeRepository = nodeRepository,
                projectRepository = projectRepository,
                personRepository = personRepository,
                clock = clock,
                openNodeId = openNodeId,
                onOpenNodeHandled = { openNodeId = null },
                onOpenPeople = { personReturnId=null;returnToPerson=false;showPeople = true },
                onOpenTechnologies = { showTechnologies = true },
            onOpenTags={showTags=true},
                onOpenAttention = { showAttention = true },
                onOpenCalendar = { showCalendar = true },
                onOpenObligations = { showObligations = true },
                onOpenAppearance = { showAppearance = true },
                onOpenAbout = { showAbout = true },
                onOpenGame = { showGame = true },
            onOpenMetro = { metroNodeId=null;metroJourneyId=null;metroRequestToken=0L;showMetro=true },
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
                    if(returnToPerson) {showPeople=true;returnToPerson=false}
                    if (returnToObligations) { showObligations = true; returnToObligations = false }
                    if (returnToAttention) { showAttention = true; returnToAttention = false }
                    if (returnToCalendar) { showCalendar = true; returnToCalendar = false }
                },
            )
        }
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
        }
        RecurrenceManager(nodeRepository.recurrence,"",emptyList(),people,financialPeopleLoaded,externalRule=recurrenceRequest,showLauncher=false,onConsumed={recurrenceRequest=null})
        if(securityOpen && securityPage!=null) NavigationContent(active=!drawerOpen) {
            Box(Modifier.fillMaxSize().then(if(drawerOpen) Modifier.clearAndSetSemantics {} else Modifier)) {
                securityPage {securityOpen=false}
            }
        }
        if(backupsOpen) NavigationContent(active=!drawerOpen && !searchOpen) {
            Box(Modifier.fillMaxSize().then(if(drawerOpen || searchOpen) Modifier.clearAndSetSemantics {} else Modifier)) {
                BackupsScreen(onBack={backupsOpen=false},onRestored=onRestored)
            }
        }
        if(searchOpen) NavigationContent(active=!drawerOpen) {
            val destination=projects.orEmpty().firstOrNull {it.id==searchProjectId}
            Box(Modifier.fillMaxSize().then(if(drawerOpen) Modifier.clearAndSetSemantics {} else Modifier)) {
                if(searchProjectId==null || destination==null) {
                    ElementSearchScreen(accessIndex.orEmpty(),searchMode,searchQuery,{searchQuery=it},{searchMode=it},
                        onBack={searchOpen=false},listState=searchListState,ready=accessIndex!=null,onOpen={result->
                            if(projects.orEmpty().any {it.id==result.projectId} && (result.nodeId==null || allState.nodesById[result.nodeId]?.projectId==result.projectId)) {
                                searchProjectId=result.projectId;searchNodeId=result.nodeId;searchPendingNode=result.nodeId;searchDestinationToken++
                            }
                        })
                } else {
                    androidx.compose.runtime.CompositionLocalProvider(LocalCompactCardFacts provides indicatorFacts,LocalIndicatorDirectory provides indicatorDirectory,com.r0ybt.arachn0de.metro.LocalMetroActivity provides metroActivity) {
                    screenStates.SaveableStateProvider("search-result:$searchDestinationToken:${destination.id}") {
                        ProjectNodeScreen(project=destination,nodeRepository=nodeRepository,projectRepository=projectRepository,
                            personRepository=personRepository,sortPreferences=sortPreferences,clock=clock,conversionScope=conversionScope,
                            onBackToProjects={searchProjectId=null;searchNodeId=null;searchPendingNode=null},
                            onBackToSearch={searchProjectId=null;searchNodeId=null;searchPendingNode=null},
                            openNodeId=searchPendingNode,onOpenNodeHandled={searchPendingNode=null},
                            onConverted={id,node->searchProjectId=id;searchNodeId=node;searchPendingNode=node;searchDestinationToken++})
                    }
                    }
                }
            }
        }
        if(quickOpen) QuickCaptureDialog(accessIndex.orEmpty(),accessRepo) {quickOpen=false}
        if(drawerOpen) {
            Box(Modifier.fillMaxSize().background(Arachn0deColors.Background.copy(alpha=0.65f)).clickable {drawerOpen=false})
            AppIdentityDrawer(onDismiss={drawerOpen=false},onSearch={openSearch()},onFavorites={openSearch("Favoritos")},
                onSecurity=if(securityPage!=null) ({searchOpen=false;backupsOpen=false;securityOpen=true}) else openSecurity,onBackups={securityOpen=false;searchOpen=false;backupsOpen=true},
                onQuickCapture=if(visibleProjectActions) null else ({quickOpen=true}),
                onProjects={navigate("projects")},projectsSelected=projectsView && !searchOpen && !backupsOpen,
                onPeople={navigate("people")},onTechnologies={navigate("technologies")},onTags={navigate("tags")},
                onAttention={navigate("attention")},onCalendar={navigate("calendar")},onObligations={navigate("obligations")},onRecurrences={recurrenceRequest=""},
                onAppearance={navigate("appearance")},onAbout={navigate("about")},onMetro={navigate("metro")},onGame={navigate("game")})
        }
    }
    }
}
