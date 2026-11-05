package com.r0ybt.arachn0de.ui

import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    AppRoot(projectRepository,nodeRepository,initialProjects=null,clock=clock)
}

@Composable
internal fun AppRoot(projectRepository: ProjectRepository, nodeRepository: NodeRepository, initialProjects:List<Project>?, clock: () -> Long = System::currentTimeMillis) {
    var initialSnapshot by remember(projectRepository) {mutableStateOf(initialProjects)}
    var generation by rememberSaveable { mutableStateOf(0) }
    androidx.compose.runtime.key(generation) {
        AppRootContent(projectRepository, nodeRepository, clock,initialSnapshot) { initialSnapshot=null;generation++ }
    }
}

@Composable
private fun AppRootContent(projectRepository: ProjectRepository, nodeRepository: NodeRepository, clock: () -> Long, initialProjects:List<Project>?, onRestored: () -> Unit) {
    val bankDb=(androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).database
    val bankRoots by remember(bankDb) {bankDb.bankDao().observeRoots()}.collectAsState(initial=emptyList())
    val bankRootIds=remember(bankRoots) {bankRoots.mapTo(hashSetOf()) {it.id}}
    val bankPaid by remember(bankDb) {bankDb.bankDao().observePaid()}.collectAsState(initial=emptyList())
    val bankPaidByNode=remember(bankPaid) {bankPaid.associate {it.nodeId to it.paid}}
    val tagState by remember(nodeRepository) { nodeRepository.tags.observe() }.collectAsState(initial = com.r0ybt.arachn0de.domain.model.TagState())
    val personRepository = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).personRepository
    val screenStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val pomodoro=(androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).pomodoro
    val bottomContext=androidx.compose.ui.platform.LocalContext.current
    val completionSounds=(bottomContext.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).security.current.value?.completionSounds
    val completionLifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(completionSounds,completionLifecycle) {
        completionSounds?.foreground(completionLifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME)completionSounds?.foreground(true)
            if(event==androidx.lifecycle.Lifecycle.Event.ON_PAUSE)completionSounds?.foreground(false)
        }
        completionLifecycle.addObserver(observer)
        onDispose {completionLifecycle.removeObserver(observer);completionSounds?.foreground(false)}
    }
    val bottomModules=rememberBottomModulesState()
    val bottomActions=rememberCoroutineScope {kotlinx.coroutines.CoroutineExceptionHandler {_,_->
        val audio=(bottomContext.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication)?.security?.current?.value?.audio
        audio?.let {it.state.value=it.state.value.copy(error="No se pudo completar la acción de audio. El archivo se conserva.")}
    }}
    // A single lifecycle owner for playback, independent of footer/dialog/fullscreen composition.
    val pomodoroLifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(pomodoro,pomodoroLifecycle) {
        pomodoroLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {pomodoro.observeForeground()}
    }
    val pomodoroAudio=(bottomContext.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).security.current.value?.audio
    LaunchedEffect(pomodoro,pomodoroAudio) {
        try {pomodoroAudio?.state?.map {it.recording}?.distinctUntilChanged()?.collect {
            pomodoro.sounds.recording=it;pomodoro.sounds.update(pomodoro.state.value)
        }} finally {pomodoro.sounds.recording=false;pomodoro.sounds.update(pomodoro.state.value)}
    }
    androidx.compose.runtime.DisposableEffect(pomodoro,bottomContext) {
        val receiver=object:android.content.BroadcastReceiver() {
            override fun onReceive(context:android.content.Context,intent:android.content.Intent) {pomodoro.sounds.update(pomodoro.state.value)}
        }
        val filter=android.content.IntentFilter(android.media.AudioManager.RINGER_MODE_CHANGED_ACTION).apply {
            addAction(android.app.NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        androidx.core.content.ContextCompat.registerReceiver(bottomContext,receiver,filter,androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        onDispose {bottomContext.unregisterReceiver(receiver)}
    }
    val pomodoroData by pomodoro.state.collectAsState()
    val pomodoroError by pomodoro.error.collectAsState()
    var confirmPomodoroFinish by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(pomodoroData.visible) {if(!pomodoroData.visible) confirmPomodoroFinish=false}
    var showDiagrams by rememberSaveable {mutableStateOf(false)}
    var showWireframe by rememberSaveable {mutableStateOf(false)}
    var showPomodoro by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(Unit) {com.r0ybt.arachn0de.pomodoro.PomodoroNavigation.requested.collect {requested->if(requested) {showDiagrams=false;showWireframe=false;showPomodoro=true;com.r0ybt.arachn0de.pomodoro.PomodoroNavigation.requested.value=false}}}
    var showMetroStatistics by rememberSaveable { mutableStateOf(false) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showMetro by rememberSaveable { mutableStateOf(false) }
    var metroRequestToken by rememberSaveable { mutableStateOf(0L) }
    var metroJourneyId by rememberSaveable {mutableStateOf<String?>(null)}
    var metroNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { com.r0ybt.arachn0de.metro.MetroNavigation.requests.collect { request -> if(request!=null) { metroNodeId=request.nodeId;metroJourneyId=request.journeyId;metroRequestToken=request.token;showMetro=true;com.r0ybt.arachn0de.metro.MetroNavigation.requests.value=null } } }
    var showSlots by rememberSaveable { mutableStateOf(false) }
    var slotsSession by remember {mutableStateOf(com.r0ybt.arachn0de.slots.SlotsSession())}
    var returnToSlots by remember {mutableStateOf(false)}
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showTags by rememberSaveable {mutableStateOf(false)}
    var showTechnologies by rememberSaveable { mutableStateOf(false) }
    var personReturnId by rememberSaveable {mutableStateOf<String?>(null)}
    var returnToPerson by rememberSaveable {mutableStateOf(false)}
    var personReturnToList by rememberSaveable {mutableStateOf(false)}
    var personNavigationToken by rememberSaveable {mutableStateOf(0L)}
    var showPeople by rememberSaveable { mutableStateOf(false) }
    val guestPerson by com.r0ybt.arachn0de.guest.GuestCaptureImporter.returnToPerson.collectAsState()
    LaunchedEffect(guestPerson) {guestPerson?.let {personReturnId=it;showPeople=true;com.r0ybt.arachn0de.guest.GuestCaptureImporter.returnToPerson.value=null}}

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
    val fallbackSortScope = androidx.compose.runtime.rememberCoroutineScope()
    val sortScope = sortContext.security.current.value?.scope ?: fallbackSortScope
    val sortRepository = remember(sortContext) { com.r0ybt.arachn0de.data.repository.NodeSortPreferenceRepository(sortContext.database) }
    val sortMutex = nodeRepository.sortWriteMutex
    var sortError by remember {mutableStateOf<String?>(null)}
    val sortPreferences = remember(sortRepository) {
        com.r0ybt.arachn0de.ui.state.NodeSortPreferences(onWrite = { context, mode ->
            sortScope.launch { sortMutex.withLock { sortRepository.set(context, mode?.name) } }
        }, onLayersWrite={context,enabled,mode->sortScope.launch {sortMutex.withLock {sortRepository.set(context,mode.name,enabled)}}},
            onConfigWrite={context,value->sortScope.launch {
                try {sortMutex.withLock {sortRepository.configure(context,value)}}
                catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
                catch(_:Exception) {kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {sortError="No se pudo guardar el orden. Vuelve a intentarlo antes de salir."}}
            }})
    }
    androidx.compose.runtime.SideEffect {sortPreferences.applicationHandler={context,value,ids,done->
        sortScope.launch {
            try {sortMutex.withLock {nodeRepository.applyConfiguredOrder(context,value,ids)};kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {sortPreferences.applied(context,value);done(null)}}
            catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
            catch(failure:Exception) {kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {done(failure)}}
        }
    }}
    if(sortError!=null) ModularPanelDialog(onDismissRequest={sortError=null},compact=true,
        title={androidx.compose.material3.Text("Ordenación")},text={androidx.compose.material3.Text(sortError.orEmpty())},
        confirmButton={PrimaryControlButton(onClick={sortError=null}) {androidx.compose.material3.Text("Cerrar")}})
    LaunchedEffect(sortRepository) { sortRepository.observe().collect { sortPreferences.replace(it) } }
    val conversionScope = androidx.compose.runtime.rememberCoroutineScope()
    val projectsListState = rememberLazyListState()
    val projectsCalendarListState = rememberLazyListState()
    var selectedProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var contentProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var headingNodeId by rememberSaveable(selectedProjectId) {mutableStateOf<String?>(null)}
    var projects by remember(projectRepository) { mutableStateOf<List<Project>?>(initialProjects) }
    var allState by remember(nodeRepository) { mutableStateOf(NodeTreeSnapshot(emptyList())) }
    val metroRepository=(androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).metroRepository
    val metroLifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val metroSnapshot by androidx.compose.runtime.produceState<com.r0ybt.arachn0de.metro.MetroSnapshot?>(null,metroRepository,metroLifecycle) {
        metroLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {metroRepository.observe().collect {value=it}}
    }
    val metroContext=androidx.compose.ui.platform.LocalContext.current
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
    LaunchedEffect(showTechnologies) {if(showTechnologies)showTechnologies=false}
    if(showTags) TagManager(nodeRepository.tags,tagState,showAccess=false,initiallyOpen=true,onClose={showTags=false})
    // A coherent global read powers the existing progress and the transversal projection.
    LaunchedEffect(nodeRepository, nodesLoad.attempt) {
        nodesLoad.collect(nodeRepository.observeAllState()) { allState = it; nodesLoaded = true }
    }
    // Card previews in Projects and layers use this same global assignment projection.
    // Keep it subscribed independently of transversal screens, including while editing a task.
    LaunchedEffect(personRepository, peopleLoad.attempt) {
        assignmentsLoaded = false
        peopleLoad.collect(personRepository.observeAllAssignments()) { responsibleByNode = it; assignmentsLoaded = true }
    }
    LaunchedEffect(showAttention, showObligations, showCalendar, selectedProjectId, personRepository, financialPeopleLoad.attempt) {
        if (showAttention || showObligations || showCalendar || selectedProjectId == null) {
            financialPeopleLoaded = false
            financialPeopleLoad.collect(personRepository.observePeople()) { people = it; financialPeopleLoaded = true }
        }
    }
    val globalView = !showDiagrams && !showWireframe && !showPomodoro && !showMetro && !showMetroStatistics && !showSlots && !showAppearance && !showAbout && !showPeople && !showTechnologies &&
        (showObligations || showCalendar || showAttention || selectedProjectId == null)
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
    val dashboardCalendarStore=rememberContextCalendarStore()
    val accessRepo=remember(accessDb) {com.r0ybt.arachn0de.access.AccessRepository(accessDb)}
    val accessState by remember(accessRepo) {accessRepo.observe()}.collectAsState(initial=com.r0ybt.arachn0de.access.AccessState())
    val indexScope=rememberCoroutineScope()
    val draftNodesSource=remember(nodeRepository) {
        nodeRepository.observeDrafts().map<List<Node>,List<Node>?> {it}.stateIn(indexScope,SharingStarted.WhileSubscribed(stopTimeoutMillis=0,replayExpirationMillis=0),null)
    }
    val accessIndexSource=remember(projectRepository,nodeRepository) {
        navigationIndex(combine(androidx.compose.runtime.snapshotFlow {
            if(projects!=null && nodesLoaded) projects.orEmpty() to allState.nodes else null
        },draftNodesSource) {input,drafts->if(input==null || drafts==null)null else input.first to (input.second+drafts)},indexScope)
    }
    var projectVisitToken by rememberSaveable {androidx.compose.runtime.mutableLongStateOf(0)}
    var recordedProject by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(selectedProjectId,projectVisitToken,accessRepo) {
        val visit=selectedProjectId?.let {"$projectVisitToken:$it"}
        if(recordedProject!=visit) {
            recordedProject=visit
            selectedProjectId?.let {accessRepo.opened("project:$it")}
        }
    }
    var drawerOpen by rememberSaveable {mutableStateOf(false)}
    val draftEditors=rememberSaveable(saver=com.r0ybt.arachn0de.ui.state.EditorDraftStore.Saver) {com.r0ybt.arachn0de.ui.state.EditorDraftStore()}
    var draftsOpen by rememberSaveable {mutableStateOf(false)}
    var dashboardDraftOpen by rememberSaveable {mutableStateOf(false)}
    var dashboardDraftChoice by remember {mutableStateOf(false)}
    var favoriteDraft by rememberSaveable {mutableStateOf<String?>(null)}
    var searchOpen by rememberSaveable {mutableStateOf(false)}
    var searchSpecialReturn by rememberSaveable {mutableStateOf<String?>(null)}

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
    val rootProjectActions=projects.orEmpty().any {it.id==selectedProjectId} && !showDiagrams && !showWireframe && !showPomodoro && !showMetro && !showMetroStatistics && !showSlots && !showAppearance && !showAbout && !showPeople && !showTechnologies && !showAttention && !showCalendar && !showObligations
    val visibleProjectActions=if(draftsOpen) false else if(searchOpen) projects.orEmpty().any {it.id==searchProjectId} else rootProjectActions && !backupsOpen && !securityOpen
    val returnHeading=if(selectedProjectId==null) {
        if(projects!=null) HeadingLocation("Núcleos",HeadingKind.SECTION) else null
    } else headingLocation(projects?.firstOrNull {it.id==selectedProjectId},headingNodeId,allState.nodesById)
    // Same priority as the existing rendering branches below; labels only, never navigation state.
    val visibleSectionNames=listOfNotNull(
        "Diagramas".takeIf {showDiagrams},
        "Wireframe".takeIf {showWireframe},
        "Pomodoro".takeIf {showPomodoro},
        "Metro".takeIf {showMetro},"Estadísticas de Metro".takeIf {showMetroStatistics},
        "Tragaperras".takeIf {showSlots},"Apariencia".takeIf {showAppearance},"Acerca de".takeIf {showAbout},
        "Tecnologías".takeIf {showTechnologies},"Antisocial".takeIf {showPeople},
        "Pagos".takeIf {showObligations},"Calendario".takeIf {showCalendar},"Atención".takeIf {showAttention})
    val sectionReturnHeading=visibleSectionNames.getOrNull(1)?.let {HeadingLocation(it,HeadingKind.SECTION)} ?: returnHeading
    val overlayReturnHeading=visibleSectionNames.firstOrNull()?.let {HeadingLocation(it,HeadingKind.SECTION)} ?: returnHeading
    val menuGate=remember {GlobalMenuGate()}
    val globalActions=remember {GlobalBarActions()}
    var searchHeadingNodeId by rememberSaveable(searchProjectId) {mutableStateOf<String?>(null)}
    val toolWithoutGlobalBar=visibleSectionNames.firstOrNull() in setOf("Diagramas","Wireframe","Metro","Estadísticas de Metro","Tragaperras","Antisocial")
    val showGlobalBar=!toolWithoutGlobalBar || draftsOpen || searchOpen || backupsOpen || securityOpen
    val navigation=GlobalNavigation(menu={if(menuGate.enabled) drawerOpen=true},showLock=!visibleProjectActions,
        bottomAccess=EXPERIMENTAL_BOTTOM_GLOBAL_MENU,gate=menuGate,actions=globalActions,chromeVisible=showGlobalBar,
        search=if(showGlobalBar)globalActions.searching.values.lastOrNull() else null)
    fun openFavorites() {
        draftsOpen=false;securityOpen=false;searchOpen=true;searchProjectId=null;searchNodeId=null;searchPendingNode=null
    }
    fun navigate(destination:String) {
        searchSpecialReturn=null
        returnToSlots=false
        if(showSlots && destination!="slots")slotsSession=com.r0ybt.arachn0de.slots.SlotsSession()
        draftsOpen=false;searchOpen=false;searchProjectId=null;searchNodeId=null;backupsOpen=false;securityOpen=false
        showPeople=destination=="people";showTechnologies=false
        showAppearance=destination=="appearance";showAbout=destination=="about"
        showDiagrams=destination=="diagrams"
        showWireframe=destination=="wireframe"
        showPomodoro=destination=="pomodoro"
        showMetroStatistics=destination=="metro-statistics"
        showMetro=destination=="metro";showSlots=destination=="slots"
        showAttention=destination=="attention";showCalendar=destination=="calendar";showObligations=destination=="obligations"
        if(destination=="projects") {selectedProjectId=null;openNodeId=null;returnToPerson=false;returnToCalendar=false;returnToObligations=false;returnToAttention=false}
        if(destination=="people") {personReturnId=null;personReturnToList=false;returnToPerson=false;screenStates.removeState("people:list")}
        if(destination=="metro") {metroNodeId=null;metroJourneyId=null;metroRequestToken=0L}
        if(destination=="tags") showTags=true
    }
    var recordedSearchVisit by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(searchProjectId,searchDestinationToken,accessRepo) {
        val visit=searchProjectId?.let {"$searchDestinationToken:$it"}
        if(recordedSearchVisit!=visit) {
            recordedSearchVisit=visit
            searchProjectId?.let {accessRepo.opened("project:$it")}
        }
    }
    val metroLifecycleState by metroLifecycle.currentStateFlow.collectAsState()
    val hierarchyVisible=metroLifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) &&
        (searchOpen || draftsOpen || quickOpen ||
            (!showDiagrams && !showWireframe && !showAppearance && !showAbout))
    val metroMoving=metroSnapshot?.journeys.orEmpty().any {it.data.active?.let {s->s.pausedAt==null}==true}
    val metroTime by androidx.compose.runtime.produceState(com.r0ybt.arachn0de.metro.metroTime(metroContext),metroRepository,metroLifecycle,hierarchyVisible,metroMoving) {
        metroLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            value=com.r0ybt.arachn0de.metro.metroTime(metroContext)
            if(hierarchyVisible) while(true) {
                val untilMinute=60_000L-Math.floorMod(value.wall,60_000L)
                kotlinx.coroutines.delay(if(metroMoving) minOf(30_000L,untilMinute) else untilMinute)
                value=com.r0ybt.arachn0de.metro.metroTime(metroContext)
            }
        }
    }
    val metroActivity by androidx.compose.runtime.produceState<Map<String,List<com.r0ybt.arachn0de.metro.MetroActivityEntry>>>(emptyMap(),allState,metroSnapshot,metroTime,hierarchyVisible) {
        if(!hierarchyVisible) {value=emptyMap();return@produceState}
        value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {metroSnapshot?.let {com.r0ybt.arachn0de.metro.MetroActivity.hierarchy(allState.nodes,it,metroTime)}.orEmpty()}
    }
    val metroPreviews by androidx.compose.runtime.produceState<Map<String,String>>(emptyMap(),metroSnapshot,metroTime,hierarchyVisible) {
        if(!hierarchyVisible) {value=emptyMap();return@produceState}
        value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {metroSnapshot?.let {snapshot->val network=snapshot.preferences.network;snapshot.journeys.mapNotNull {j->j.row.nodeId?.let {it to com.r0ybt.arachn0de.metro.MetroActivity.summary(j,network,metroTime)}}.toMap()}.orEmpty()}
    }
    val indicatorParticipation by remember(nodeRepository) {nodeRepository.management.participation.observe("")}.collectAsState(initial=com.r0ybt.arachn0de.data.repository.ParticipationState())
    val indicatorMinute=metroTime.wall / 60_000L
    val indicatorFacts by androidx.compose.runtime.produceState<Map<String,CompactCardFacts>>(emptyMap(),allState,responsibleByNode,indicatorTechnologies,metroSnapshot,indicatorMinute,indicatorParticipation) {
        value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {compactCardFacts(allState.nodes,responsibleByNode,indicatorTechnologies,metroSnapshot,indicatorMinute*60_000L,participation=indicatorParticipation)}
    }
    val indicatorDirectory=remember(allState,projects,people,responsibleByNode,indicatorTechnologies,metroSnapshot,indicatorParticipation) {
        IndicatorDirectory(allState.nodesById,(people+responsibleByNode.values.flatten()+indicatorParticipation.people.map {Person(it.id,it.name,it.avatarFile,it.avatarZoom,it.avatarX,it.avatarY)}).associateBy {it.id},indicatorTechnologies.catalog.associateBy {it.id},metroSnapshot?.journeys.orEmpty().mapNotNull {j->j.row.nodeId?.let {j.row.id to it}}.toMap(),projects=projects.orEmpty().associateBy {it.id})
    }
    val reportActions=LocalProjectActionsVisibility.current
    androidx.compose.runtime.SideEffect {reportActions(visibleProjectActions)}
    androidx.compose.runtime.CompositionLocalProvider(LocalPomodoroFinishRequest provides {confirmPomodoroFinish=true},LocalBottomModulesActionScope provides bottomActions,LocalBottomModulesState provides bottomModules,LocalBottomModulesContent provides {compact:Boolean->
        FoldedBottomPanels()
        AudioBar(compact=compact || bottomModules.audioCompact,onCompactChange={bottomModules.audioCompact=it})
        if(!showPomodoro || searchOpen || backupsOpen || securityOpen || drawerOpen || bottomModulesInDialog()) PomodoroBar(pomodoro,pomodoroData,
            compact=compact || bottomModules.pomodoroCompact,onCompactChange={bottomModules.pomodoroCompact=it},
            onExpand={searchOpen=false;backupsOpen=false;securityOpen=false;drawerOpen=false;showDiagrams=false;showWireframe=false;showPomodoro=true})
        ContextualBottomPanels()
        GlobalBottomBar()
    },LocalOpenPomodoro provides {draftsOpen=false;showDiagrams=false;showWireframe=false;showPomodoro=true},LocalCaptureGate provides remember(accessDb) {CaptureGate()},LocalGlobalNavigation provides navigation,LocalStoryLabels provides com.r0ybt.arachn0de.data.repository.storyLabels(indicatorParticipation.work.nodes,indicatorParticipation.work.records.roles),LocalQuickCaptureIndexSource provides accessIndexSource,LocalContainerProgress provides remember(allState,projects,nodesLoaded) {if(!nodesLoaded) emptyMap() else allState.progressById.mapKeys {"node:${it.key}"}+projects.orEmpty().associate {p->"project:${p.id}" to (allState.projectProgressById[p.id] ?: com.r0ybt.arachn0de.domain.model.NodeProgress(p.id,0,0,0,com.r0ybt.arachn0de.domain.model.NodeProgressState.NO_WORK))}},LocalElementAccess provides accessRepo,LocalElementAccessState provides accessState,LocalPersonNavigation provides {destination:PersonDestination->
        if(projects.orEmpty().any {it.id==destination.projectId} && (destination.nodeId==null || allState.nodesById[destination.nodeId]?.projectId==destination.projectId)) {
            searchOpen=false;searchProjectId=null;backupsOpen=false
            personReturnToList=showPeople && (personReturnId==null || personReturnToList)
            personNavigationToken++;personReturnId=destination.personId;returnToPerson=true;showPeople=false;showAttention=false;showCalendar=false;showObligations=false
            returnToAttention=false;returnToCalendar=false;returnToObligations=false
            screenStates.removeState("project:${destination.projectId}");selectedProjectId=destination.projectId; projectVisitToken++;openNodeId=destination.nodeId
        }
    }) {
    androidx.compose.runtime.CompositionLocalProvider(LocalReturnHeading provides if(draftsOpen || securityOpen || backupsOpen || searchOpen) overlayReturnHeading else if(visibleSectionNames.isNotEmpty()) sectionReturnHeading else null) {
    BottomModulesFrame(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxWidth().weight(1f)) {
        NavigationContent(active=!draftsOpen && !searchOpen && !backupsOpen && !drawerOpen && !securityOpen) {
        Box(Modifier.fillMaxSize().then(if(draftsOpen || searchOpen || backupsOpen || drawerOpen || securityOpen) Modifier.clearAndSetSemantics {} else Modifier)) {
    val selectedProject = projects?.firstOrNull { it.id == selectedProjectId }
    androidx.compose.runtime.CompositionLocalProvider(LocalBankRootIds provides bankRootIds,LocalBankPaid provides bankPaidByNode,com.r0ybt.arachn0de.metro.LocalMetroActivity provides metroActivity,com.r0ybt.arachn0de.metro.LocalMetroPreview provides metroPreviews,LocalCompactCardFacts provides indicatorFacts,LocalIndicatorDirectory provides indicatorDirectory) {
    if (showDiagrams) {
        screenStates.SaveableStateProvider("diagrams") {com.r0ybt.arachn0de.diagrams.DiagramScreen {showDiagrams=false}}
    } else if (showWireframe) {
        screenStates.SaveableStateProvider("wireframe") {com.r0ybt.arachn0de.wireframe.WireframeScreen {showWireframe=false}}
    } else if (showPomodoro) {
        PomodoroScreen(pomodoro,pomodoroData,onBack={showPomodoro=false},allowLandscape=!searchOpen && !backupsOpen && !securityOpen && !drawerOpen)
    } else if (showMetro) {
        screenStates.SaveableStateProvider("metro:${metroJourneyId ?: metroNodeId ?: "main"}") { com.r0ybt.arachn0de.metro.MetroScreen(allState.nodes,projects.orEmpty(),metroNodeId,requestToken=metroRequestToken,journeyId=metroJourneyId,onBack={showMetro=false;metroNodeId=null;metroJourneyId=null}) }
    } else if (showMetroStatistics) {
        screenStates.SaveableStateProvider("metro-statistics") {
            com.r0ybt.arachn0de.metro.MetroStatisticsScreen(onBack={showMetroStatistics=false})
        }
    } else if (showSlots) {
        com.r0ybt.arachn0de.slots.SlotScreen(session=slotsSession,
            candidates=com.r0ybt.arachn0de.slots.SlotCandidates(projects.orEmpty(),allState.nodes),
            tags=tagState,people=responsibleByNode,
            loadCandidates={com.r0ybt.arachn0de.slots.SlotCandidates(projectRepository.observeProjects().first(),nodeRepository.observeAllState().first().nodes)},
            onOpenTask={task->
                if(task.projectId!=null && allState.nodesById[task.id]?.isCompleted==false) {
                    selectedProjectId=task.projectId;openNodeId=task.id;projectVisitToken++
                    screenStates.removeState("project:${task.projectId}")
                    returnToAttention=false;returnToCalendar=false;returnToObligations=false;returnToPerson=false
                    returnToSlots=true;showSlots=false
                }
            },onOpenBank={core,account->
                selectedProjectId=core;openNodeId=account;projectVisitToken++
                screenStates.removeState("project:$core")
                returnToAttention=false;returnToCalendar=false;returnToObligations=false;returnToPerson=false
                returnToSlots=true;showSlots=false
            },onBack = { showSlots = false;slotsSession=com.r0ybt.arachn0de.slots.SlotsSession() })
    } else if (showAppearance) {
        screenStates.SaveableStateProvider("appearance") {
            AppearanceScreen(onBack = { showAppearance = false })
        }
    } else if (showAbout) {
        screenStates.SaveableStateProvider("about") { AboutScreen(onBack = { showAbout = false }, onRestored = onRestored) }
    } else if (showPeople) {
        screenStates.SaveableStateProvider("people:${if(personReturnToList) "list" else personReturnId ?: "list"}") { PeopleScreen(personRepository,initialPersonId=if(personReturnToList) null else personReturnId,initialSection=if(personReturnId!=null && !personReturnToList) "Proyectos y trabajo" else "Inicio") { showPeople = false;personReturnId=null;personReturnToList=false } }
    } else if (showObligations) {
        screenStates.SaveableStateProvider("obligations") {
            ObligationsScreen(allState, projects.orEmpty(), people, responsibleByNode,
                nodesLoaded && projects != null && assignmentsLoaded && financialPeopleLoaded, now, zoneId,
                onOpen = { node ->
                    val real = allState.nodesById[node.id]
                    if (real != null && real.isCompletable && real.obligation != null && projects.orEmpty().any { it.id == real.projectId }) {
                        selectedProjectId = real.projectId; projectVisitToken++; openNodeId = real.id
                        returnToObligations = true; returnToCalendar = false; returnToAttention = false
                        showObligations = false
                    }
                }, onBack = { showObligations = false;if(searchSpecialReturn=="obligations") {searchOpen=true;searchSpecialReturn=null} })
        }
    } else if (showCalendar) {
        screenStates.SaveableStateProvider("calendar") {
            CalendarScreen(calendar, projects.orEmpty(), responsibleByNode, nodesLoaded && projects != null && assignmentsLoaded,
                now, zoneId,
                onOpen = { node ->
                    val real = allState.nodesById[node.id]
                    if (real != null && real.isCompletable && real.dueAt != null && projects.orEmpty().any { it.id == real.projectId }) {
                        selectedProjectId = real.projectId; projectVisitToken++
                        openNodeId = real.id
                        returnToCalendar = true
                        returnToObligations = false
                        returnToAttention = false
                        showCalendar = false
                    }
                },
                people = people, peopleLoaded = financialPeopleLoaded, tagState = tagState,
                eventRepository = indicatorApp.security.current.value?.calendarEvents,
                birthdayRepository = personRepository,
                onBack = { showCalendar = false },
            )
        }
    } else if (showAttention) {
        screenStates.SaveableStateProvider("attention") {
            AttentionScreen(attention, projects.orEmpty(), responsibleByNode, nodesLoaded && projects != null && assignmentsLoaded, tagState = tagState, people = people,
                onOpen = { node ->
                    val real = allState.nodesById[node.id]
                    if (real != null && projects.orEmpty().any { it.id == real.projectId }) {
                        selectedProjectId = real.projectId; projectVisitToken++
                        openNodeId = real.id
                        returnToAttention = true
                        returnToObligations = false
                        returnToCalendar = false
                        showAttention = false
                    }
                },
                onBack = { showAttention = false;if(searchSpecialReturn=="attention") {searchOpen=true;searchSpecialReturn=null} },
            )
        }
    } else if (selectedProjectId == null) {
        if(projects==null) androidx.compose.material3.Text("Cargando Núcleos…") else BashGeneralScope { ProjectDashboardScreen(
            sortPreferences=sortPreferences,
            exportTree = allState,
            calendarResponsible=responsibleByNode,
            calendarStore=dashboardCalendarStore,calendarNow=now,calendarZone=zoneId,
            copyDescendantsReady = nodesLoaded,

            repository = projectRepository,
            onCreateDraft = {dashboardDraftChoice=true},
            listState = projectsListState,calendarListState=projectsCalendarListState,
            projects = projects.orEmpty(),
            projectAlertsById = projectAlerts,
            conversionScope = conversionScope,
            onConverted = { id, node -> screenStates.removeState("project:$id"); selectedProjectId = id; projectVisitToken++; openNodeId = node; returnToAttention = false; returnToCalendar = false; returnToObligations = false },
            onOpenProject = { contentProjectId=null; screenStates.removeState("project:${it.id}"); openNodeId = null; selectedProjectId = it.id; projectVisitToken++; returnToAttention = false; returnToCalendar = false; returnToObligations = false },
            onOpenContent = { contentProjectId=it.id; screenStates.removeState("project:${it.id}"); openNodeId = null; selectedProjectId = it.id; projectVisitToken++; returnToAttention = false; returnToCalendar = false; returnToObligations = false },
            onOpenPeople = { navigate("people") },
            onOpenTechnologies = {},
            onOpenTags={showTags=true},
            onOpenAttention = { showAttention = true },
            onOpenCalendar = { showCalendar = true },
            onOpenObligations = { showObligations = true },
            onOpenAppearance = { showAppearance = true },
            onOpenAbout = { showAbout = true },
            onOpenMetro = { metroNodeId=null;metroJourneyId=null;metroRequestToken=0L;showMetro=true },
        ) }
    } else if (selectedProject != null) {
        // Recreate the destination even within the same project, dismissing source dialogs.
        androidx.compose.runtime.key(personNavigationToken) {
        screenStates.SaveableStateProvider("project:${selectedProject.id}") {
            BashGeneralScope { ProjectNodeScreen(
                project = selectedProject,
                sharedAssignments=responsibleByNode,sharedAssignmentsReady=assignmentsLoaded,
                calendarStore=dashboardCalendarStore,
                entryAction=if(contentProjectId==selectedProject.id) "content" else null,
                onEntryActionHandled={contentProjectId=null},
                onHeadingNodeChanged={headingNodeId=it},
                conversionScope = conversionScope,
                onConverted = { id, node -> screenStates.removeState("project:$id"); selectedProjectId = id; projectVisitToken++; openNodeId = node; returnToAttention = false; returnToCalendar = false; returnToObligations = false },
                sortPreferences = sortPreferences,
                onOpenProjects = { screenStates.removeState("project:${selectedProject.id}"); selectedProjectId = null; openNodeId = null; returnToCalendar = false; returnToAttention = false; returnToObligations = false;returnToPerson=false;personReturnId=null },
                nodeRepository = nodeRepository,
                projectRepository = projectRepository,
                personRepository = personRepository,
                clock = clock,
                openNodeId = openNodeId,
                onOpenNodeHandled = { openNodeId = null },
                onBackToSearch=if(returnToSlots) ({showSlots=true;returnToSlots=false;selectedProjectId=null;openNodeId=null}) else null,
                onOpenPeople = { navigate("people") },
                onOpenTechnologies = {},
            onOpenTags={showTags=true},
                onOpenAttention = { showAttention = true },
                onOpenCalendar = { showCalendar = true },
                onOpenObligations = { showObligations = true },
                onOpenAppearance = { showAppearance = true },
                onOpenAbout = { showAbout = true },
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
            ) }
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
                androidx.compose.runtime.CompositionLocalProvider(LocalBackupsRestored provides onRestored) {securityPage {securityOpen=false}}
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
                    screenStates.SaveableStateProvider("unified-search") {
                    ElementSearchScreen(quickCaptureIndex().orEmpty(),"Favoritos","",{}, {},
                        onBack={searchOpen=false},listState=searchListState,ready=quickCaptureIndex()!=null,onOpen={result->
                            if(result.projectId==null && result.nodeId!=null) favoriteDraft=result.nodeId
                            else if(projects.orEmpty().any {it.id==result.projectId} && (result.nodeId==null || allState.nodesById[result.nodeId]?.projectId==result.projectId)) {
                                searchProjectId=result.projectId;searchNodeId=result.nodeId;searchPendingNode=result.nodeId;searchDestinationToken++
                            }
                        })
                    }
                } else {
                    androidx.compose.runtime.CompositionLocalProvider(LocalCompactCardFacts provides indicatorFacts,LocalIndicatorDirectory provides indicatorDirectory,com.r0ybt.arachn0de.metro.LocalMetroActivity provides metroActivity) {
                    screenStates.SaveableStateProvider("search-result:$searchDestinationToken:${destination.id}") {
                        BashGeneralScope { ProjectNodeScreen(project=destination,nodeRepository=nodeRepository,projectRepository=projectRepository,
                            sharedAssignments=responsibleByNode,sharedAssignmentsReady=assignmentsLoaded,
                            calendarStore=dashboardCalendarStore,
                            personRepository=personRepository,sortPreferences=sortPreferences,clock=clock,conversionScope=conversionScope,
                            onBackToProjects={searchProjectId=null;searchNodeId=null;searchPendingNode=null},
                            onBackToSearch={searchProjectId=null;searchNodeId=null;searchPendingNode=null},
                            onHeadingNodeChanged={searchHeadingNodeId=it},
                            openNodeId=searchPendingNode,onOpenNodeHandled={searchPendingNode=null},
                            onConverted={id,node->searchProjectId=id;searchNodeId=node;searchPendingNode=node;searchDestinationToken++}) }
                    }
                    }
                }
            }
        }
        if(com.r0ybt.arachn0de.sharing.SHARE_ENABLED) SharedCaptureHost(null,accessRepo,quickOpen)
        if(draftsOpen) NavigationContent(active=!drawerOpen) {
            screenStates.SaveableStateProvider("drafts") {
                DraftsScreen(nodeRepository,personRepository,quickCaptureIndex().orEmpty(),draftEditors,onBack={draftsOpen=false})
            }
        }
        if(dashboardDraftChoice) StructureCreationPanel(null,null,onNew={dashboardDraftChoice=false;dashboardDraftOpen=true},draftDestination=true,onDismiss={dashboardDraftChoice=false})
        if(dashboardDraftOpen) DraftElementEditor(null,nodeRepository,personRepository,draftEditors,quickCapture=true) {dashboardDraftOpen=false}
        favoriteDraft?.let {id->DraftElementEditor(id,nodeRepository,personRepository,draftEditors) {favoriteDraft=null}}
        if(quickOpen) QuickCaptureDialog(quickCaptureIndex(),accessRepo) {quickOpen=false}
        if(drawerOpen) {
            val menuContent:@Composable ()->Unit={
            AppIdentityDrawer(experimentalSheet=EXPERIMENTAL_BOTTOM_GLOBAL_MENU,onDiagrams={navigate("diagrams")},onWireframe={navigate("wireframe")},onDismiss={drawerOpen=false},onSearch=null,onFavorites={openFavorites()},onDrafts={navigate("drafts");draftsOpen=true},
                onSecurity=if(securityPage!=null) ({draftsOpen=false;searchOpen=false;backupsOpen=false;securityOpen=true}) else openSecurity,onBackups={draftsOpen=false;securityOpen=false;searchOpen=false;backupsOpen=true},
                onQuickCapture=if(visibleProjectActions) null else ({quickOpen=true}),
                onProjects={navigate("projects")},projectsSelected=projectsView && !draftsOpen && !searchOpen && !backupsOpen && !securityOpen,
                onPeople={navigate("people")},peopleSelected=showPeople && !draftsOpen && !searchOpen && !securityOpen && !backupsOpen,onTechnologies={navigate("technologies")},onTags={navigate("tags")},
                onAttention={navigate("attention")},onCalendar={navigate("calendar")},onObligations={navigate("obligations")},onRecurrences={recurrenceRequest=""},
                onAppearance={navigate("appearance")}, onMetroStatistics={navigate("metro-statistics")},onAbout={navigate("about")},onMetro={navigate("metro")},onSlots={navigate("slots")})
            }
            if(EXPERIMENTAL_BOTTOM_GLOBAL_MENU) menuContent()
            else {
            androidx.compose.ui.window.Dialog(onDismissRequest={drawerOpen=false},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
            val drawerView=androidx.compose.ui.platform.LocalView.current
            val drawerPalette=com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette.current
            androidx.compose.runtime.SideEffect {
                (drawerView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let {window->
                    window.statusBarColor=android.graphics.Color.TRANSPARENT
                    if(android.os.Build.VERSION.SDK_INT>=29)window.isStatusBarContrastEnforced=false
                    androidx.core.view.WindowCompat.getInsetsController(window,drawerView).isAppearanceLightStatusBars=drawerPalette.light
                }
            }
            Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Arachn0deColors.Background.copy(alpha=0.65f)).clickable {drawerOpen=false})
            menuContent()
            }
            }
            }
        }
    }

    if(confirmPomodoroFinish && pomodoroData.visible) PomodoroFinishConfirmation(pomodoro) {confirmPomodoroFinish=false}
    if(pomodoroError!=null) ModularPanelDialog(onDismissRequest={pomodoro.error.value=null},title={androidx.compose.material3.Text("Pomodoro")},
        text={androidx.compose.material3.Text(pomodoroError.orEmpty())},confirmButton={androidx.compose.material3.TextButton(onClick={pomodoro.error.value=null}) {androidx.compose.material3.Text("Aceptar")}},compact=true)
    }
    }
    }
}
