package com.r0ybt.arachn0de.ui

import androidx.compose.material.icons.filled.Share
import com.r0ybt.arachn0de.domain.model.TaskStatePolicy
import com.r0ybt.arachn0de.domain.model.TaskStatus
import com.r0ybt.arachn0de.domain.model.TaskViewProjection

import com.r0ybt.arachn0de.ui.theme.BashColors
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.BoxWithConstraints

import androidx.compose.material.icons.filled.PushPin

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight

import androidx.compose.material3.MaterialTheme
import com.r0ybt.arachn0de.domain.model.Node

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette
import kotlinx.coroutines.flow.first
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.History

import androidx.compose.material.icons.filled.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.Icons
import com.r0ybt.arachn0de.ui.state.NodeSortMode
import com.r0ybt.arachn0de.ui.state.NodeSortPreferences
import com.r0ybt.arachn0de.ui.state.NodePresentationSort
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.ui.state.NodeBatchDraft
import com.r0ybt.arachn0de.ui.state.EditorDraft
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot
import com.r0ybt.arachn0de.domain.model.Project
import com.r0ybt.arachn0de.ui.theme.*
import com.r0ybt.arachn0de.ui.theme.ContentTypography
import com.r0ybt.arachn0de.ui.state.LoadState
import com.r0ybt.arachn0de.ui.state.NodeActions

@Composable
internal fun ProjectNodeScreen(
    project: Project,
    nodeRepository: NodeRepository,
    onBackToProjects: () -> Unit,
    personRepository: com.r0ybt.arachn0de.data.repository.PersonRepository = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).personRepository,
    onOpenPeople: () -> Unit = {},
    onOpenAttention: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenObligations: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenMetro: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onBackToObligations: (() -> Unit)? = null,
    onBackToCalendar: (() -> Unit)? = null,
    onBackToSearch: (() -> Unit)? = null,
    openNodeId: String? = null,
    onOpenNodeHandled: () -> Unit = {},
    onHeadingNodeChanged: (String?) -> Unit = {},
    entryAction:String?=null,
    entryNodeId:String?=openNodeId,
    onEntryActionHandled:()->Unit={},
    clock: () -> Long = System::currentTimeMillis,
    onOpenProjects: () -> Unit = onBackToProjects,
    sortPreferences: NodeSortPreferences = rememberSaveable(project.id, saver = NodeSortPreferences.Saver) { NodeSortPreferences() },
    projectRepository: com.r0ybt.arachn0de.data.repository.ProjectRepository = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).projectRepository,
    onOpenTechnologies: () -> Unit = {},
    onOpenTags:()->Unit={},
    onConverted: (String, String?) -> Unit = { _, _ -> onBackToProjects() },
    conversionScope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope(),
    calendarStore:ContextCalendarStore=rememberContextCalendarStore(),
    sharedAssignments:Map<String,List<com.r0ybt.arachn0de.domain.model.Person>>?=null,
    sharedAssignmentsReady:Boolean=false,
) {
    val bankDb=(LocalContext.current.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication)?.database
    if(bankDb!=null) {
        val bankRoots by remember(bankDb) {bankDb.bankDao().observeRoots()}.collectAsState(initial=null)
        if(bankRoots==null) {Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {androidx.compose.material3.CircularProgressIndicator()};return}
        if(bankRoots.orEmpty().any {it.id==project.id}) {
            BankWorkspace(project,bankDb,onBackToSearch ?: onBackToObligations ?: onBackToCalendar ?: onBackToProjects,openNodeId) {core,node->onConverted(core,node)}
            LaunchedEffect(openNodeId) {if(openNodeId!=null)onOpenNodeHandled()}
            return
        }
    }
    val pendingOpenNode by rememberUpdatedState(openNodeId)
    val handleOpenNode by rememberUpdatedState(onOpenNodeHandled)
    var moveProject by rememberSaveable(project.id) { mutableStateOf(false) }
    if(moveProject) ProjectMoveDialog(projectRepository,project,{ moveProject=false },{ moveProject=false }, onConverted = { id, layer -> moveProject=false; onConverted(id, layer) }, operationScope = conversionScope)
    var promotingLayerId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    promotingLayerId?.let { id ->
        LayerToProjectDialog(projectRepository, id, { promotingLayerId = null }, { promoted ->
            promotingLayerId = null; onConverted(promoted, null)
        }, conversionScope)
    }
    val scope = rememberCoroutineScope()
    val personActions = remember(personRepository, scope) { com.r0ybt.arachn0de.ui.state.PersonActions(personRepository, scope) }
    var people by remember { mutableStateOf(emptyList<com.r0ybt.arachn0de.domain.model.Person>()) }
    var localResponsibleByNode by remember(project.id) { mutableStateOf(emptyMap<String, List<com.r0ybt.arachn0de.domain.model.Person>>()) }
    var peopleLoaded by remember { mutableStateOf(false) }
    var localAssignmentsLoaded by remember(project.id) { mutableStateOf(false) }
    var responsibleNodeId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    val peopleLoad = remember(personRepository) { LoadState() }
    val assignmentsLoad = remember(personRepository, project.id) { LoadState() }
    LaunchedEffect(personRepository, peopleLoad.attempt) {
        peopleLoad.collect(personRepository.observePeople()) { people = it; peopleLoaded = true }
    }
    LaunchedEffect(personRepository, project.id, sharedAssignments!=null,assignmentsLoad.attempt) {
        if(sharedAssignments==null) assignmentsLoad.collect(personRepository.observeAssignments(project.id)) { localResponsibleByNode = it; localAssignmentsLoaded = true }
    }
    val layerScrollStates = rememberSaveableStateHolder()
    val currentPath = rememberSaveable(project.id, saver = listSaver<SnapshotStateList<String>, String>(
        save = { it.toList() },
        restore = { it.toMutableStateList() },
    )) { mutableStateListOf<String>() }
    val openedAt=remember(project.id) {ResponseTiming.start("project_composition_started")}
    var openReported by remember(project.id) {mutableStateOf(false)}
    var hasLoaded by remember(project.id) { mutableStateOf(false) }
    var projectState by remember(project.id) { mutableStateOf(NodeTreeSnapshot(emptyList())) }
    val projectNodeIds=projectState.nodesById.keys
    val sharedProjection by produceState<Map<String,List<com.r0ybt.arachn0de.domain.model.Person>>?>(null,sharedAssignments,projectNodeIds) {
        val global=sharedAssignments
        value=global?.let {kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            projectNodeIds.mapNotNull {id->global[id]?.let {id to it}}.toMap()
        }}
    }
    val responsibleByNode=if(sharedAssignments!=null)sharedProjection.orEmpty() else localResponsibleByNode
    val assignmentsLoaded=if(sharedAssignments!=null)sharedAssignmentsReady && sharedProjection!=null else localAssignmentsLoaded
    val drafts = rememberSaveable(project.id, saver = com.r0ybt.arachn0de.ui.state.EditorDraftStore.Saver) { com.r0ybt.arachn0de.ui.state.EditorDraftStore() }
    val draft = drafts.active
    var quickCreation by rememberSaveable(project.id) { mutableStateOf(false) }
    var convertingPurpose by remember { mutableStateOf(NodePurpose.NOTE) }
    var convertingObligationId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var deletingNodeId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var deletingNodeName by rememberSaveable(project.id) { mutableStateOf("") }
    var movingNodeId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var showDrawer by remember { mutableStateOf(false) }
    var recurrenceSelected by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var recurrenceReceipts by remember(nodeRepository) { mutableStateOf(emptyList<com.r0ybt.arachn0de.data.local.RecurrenceOccurrenceEntity>()) }
    val recurrenceLoad = remember(nodeRepository) { LoadState() }
    LaunchedEffect(nodeRepository, recurrenceLoad.attempt) { recurrenceLoad.collect(nodeRepository.recurrence.occurrences) { recurrenceReceipts = it } }
    val recurrenceByNode = remember(recurrenceReceipts) { recurrenceReceipts.associate { it.nodeId to it.ruleId } }
    val attachmentRepository = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).attachmentRepository
    val actions = remember(nodeRepository, scope) { NodeActions(nodeRepository, scope, attachmentRepository) }
    val copyContext = androidx.compose.ui.platform.LocalContext.current
    val copyActions = remember(copyContext, scope) { com.r0ybt.arachn0de.ui.state.NodeCopyActions(copyContext, scope) { personRepository.observeAssignments(project.id).first() } }
    CopyTextChoiceDialog(copyActions)
    val defaultsOperation = remember(scope) { com.r0ybt.arachn0de.ui.state.OperationState(scope) }
    val isSubmittingNode = actions.operation.busy || defaultsOperation.busy
    var referenceReturns by rememberSaveable(project.id) {mutableStateOf(listOf<String>())}
    val currentNodeId = currentPath.lastOrNull()
    LaunchedEffect(project.id,currentNodeId) {onHeadingNodeChanged(currentNodeId)}
    fun openReference(id:String) {
        if(id==currentNodeId) return
        val origin=currentPath.toList()
        actions.navigate(id) {path->
            referenceReturns=referenceReturns+org.json.JSONObject().put("target",id).put("path",org.json.JSONArray(origin)).toString()
            currentPath.clear();currentPath.addAll(path)
        }
    }
    fun restoreReference():Boolean {
        val saved=referenceReturns.lastOrNull()?.let {org.json.JSONObject(it)} ?: return false
        if(saved.getString("target")!=currentNodeId) return false
        val route=saved.getJSONArray("path")
        currentPath.clear();currentPath.addAll((0 until route.length()).map {route.getString(it)})
        referenceReturns=referenceReturns.dropLast(1);return true
    }
    val elementAccess=LocalElementAccess.current
    var recordedNode by rememberSaveable(project.id) {mutableStateOf<String?>(null)}
    LaunchedEffect(currentNodeId,elementAccess) {
        if(elementAccess!=null && recordedNode!=currentNodeId) {
            recordedNode=currentNodeId
            currentNodeId?.let {elementAccess.opened("node:$it")}
        }
    }
    var showDefaults by rememberSaveable(currentNodeId) { mutableStateOf(false) }
    var showProjectPhoto by rememberSaveable(project.id) { mutableStateOf(false) }
    if (showProjectPhoto) ProjectPhotoDialog(project, onClose = { showProjectPhoto = false })
    var editingTechnologies by remember { mutableStateOf(false) }
    if(editingTechnologies) EditOwnerTechnologies(currentNodeId ?: project.id,currentNodeId==null) {editingTechnologies=false}
    var showContextActions by remember(currentNodeId) { mutableStateOf(false) }
    var coreContext by remember(project.id,currentNodeId) {mutableStateOf<CoreBottomContext?>(null)}
    var planningOpen by remember(currentNodeId) {mutableStateOf(false)}
    val currentNode = projectState.nodesById[currentNodeId]
    var projectEditField by rememberSaveable(project.id) {mutableStateOf<String?>(null)}
    val editProject:(String)->Unit={field->projectEditField=field}
    LaunchedEffect(projectEditField) {
        if(projectEditField!=null) coreContext=CoreBottomContext.Content
    }
    val cleanCompleted=if(currentNode==null || currentNode.isStructural) completedCleanupLauncher(project.id,currentNode?.id) else null
    val scopeDescription = currentNode?.description ?: project.description
    val sprintScope = false // Sprint is historical data, no longer an active view.

    val sprintLinks=emptyList<com.r0ybt.arachn0de.data.local.SprintLinkEntity>()
    val managementContexts=emptyList<com.r0ybt.arachn0de.data.local.ManagementContextEntity>()
    val cascadeLayers=emptyList<com.r0ybt.arachn0de.data.local.ManagementLayerEntity>()
    val cascadeRoles=com.r0ybt.arachn0de.data.repository.BacklogState()
    val managementRoots=emptyList<com.r0ybt.arachn0de.data.local.ConversionRootEntity>()
    val managementRoot=managementRoots.firstOrNull {if(currentNodeId==null) it.projectId==project.id else it.nodeId==currentNodeId}
    val managementContext=managementContexts.firstOrNull {it.id==managementRoot?.id}
    var managementCreationRequest by remember(currentNodeId) {mutableStateOf(0)}
    val creationMapping=cascadeLayers.firstOrNull {it.nodeId==currentNodeId}
    val creationRole=cascadeRoles.records.roles.firstOrNull {it.nodeId==currentNodeId}
    val sprintOverviewActive=creationMapping?.kind=="SPRINTS"
    val iterationOverview=if(sprintOverviewActive) {
        val state by remember(nodeRepository.management.iterations,creationMapping?.contextId) {nodeRepository.management.iterations.observe(requireNotNull(creationMapping).contextId)}.collectAsState(initial=com.r0ybt.arachn0de.data.repository.IterationState())
        state
    } else com.r0ybt.arachn0de.data.repository.IterationState()
    val sprintOverviewRefs=if(sprintOverviewActive) {
        val refs by nodeRepository.management.relations.stories.collectAsState(initial=emptyList())
        refs
    } else emptyList()
    val teamScope=cascadeLayers.any {it.nodeId==currentNodeId && it.kind=="TEAM"}
    val managedSprint=sprintScope && managementContexts.any {it.enabled && it.id==cascadeRoles.owners[currentNodeId]}
    val storiesByEpic=remember(cascadeRoles.records.epicStories,cascadeRoles.records.roles,projectState.nodes) {
        val result=mutableMapOf<String,MutableSet<String>>()
        cascadeRoles.records.epicStories.forEach {result.getOrPut(it.epicId) {hashSetOf()}.add(it.storyId)}
        cascadeRoles.records.roles.filter {it.role=="STORY"}.forEach {role->projectState.nodesById[role.nodeId]?.parentId?.let {parent->result.getOrPut(parent) {hashSetOf()}.add(role.nodeId)}}
        result.mapValues {it.value.toSet()}
    }

    val contextualCreationLabel=if(managementContexts.any {it.id==(creationMapping?.contextId ?: creationRole?.contextId) && it.enabled}) when {
        creationMapping?.kind=="OBJECTIVES"->"Objetivo"
        creationMapping?.kind=="BACKLOG"->"Épica"
        creationMapping?.kind=="SPRINTS"->"Sprint"
        creationRole?.role=="EPIC"->"Historia"
        else->null
    } else null
    if(managementCreationRequest>0 && contextualCreationLabel!=null && currentNodeId!=null) ManagementCreationDialog(
        currentNodeId,creationMapping?.kind,creationRole?.role,requireNotNull(creationMapping?.contextId ?: creationRole?.contextId),contextualCreationLabel,nodeRepository.management
    ) {managementCreationRequest=0}
    fun openTraditionalElement(quick: Boolean = false) {
        if(isSubmittingNode || currentNode?.sprintMode==true)return // Preserve historical Sprint boundaries without creating new phase-managed tasks.
        val parent = currentNodeId
        if(drafts.hasNew(parent)) {
            drafts.open(parent) { error("Borrador existente") }
            quickCreation = quick && drafts.active?.let { it.purpose != NodePurpose.LAYER && !it.batchEnabled && it.description.isBlank() && it.technologyIds.isEmpty() && it.templateMetroPlan == null } == true
        }
        else {
            var seed: EditorDraft? = null
            val preparationStarted=ResponseTiming.start("create_preparation_started")
            defaultsOperation.submit("No se pudieron cargar los valores predeterminados. Puedes reintentar.", {
                val defaults = nodeRepository.creationDefaults.resolve(project.id,parent)
                seed = com.r0ybt.arachn0de.ui.state.CreationDefaultsDraftFactory.create(parent,defaults,clock(),java.util.TimeZone.getDefault())
                true
            }, {
                ResponseTiming.end("create_preparation_ready",preparationStarted)
                drafts.open(parent) { checkNotNull(seed) }
                quickCreation = quick && checkNotNull(seed).let { it.purpose != NodePurpose.LAYER }
            })
        }
    }
    var cascadeError by remember(project.id) {mutableStateOf<String?>(null)}


    val linkedIds=sprintLinks.filter {it.sprintId==currentNodeId}.mapTo(hashSetOf()) {it.taskId}
    var showLinkTasks by remember(currentNodeId) {mutableStateOf(false)}
    var unlinkTask by remember(currentNodeId) {mutableStateOf<Pair<String,com.r0ybt.arachn0de.domain.model.WorkState>?>(null)}
    if(showLinkTasks && currentNode!=null) SprintLinksDialog(currentNode,projectState,sprintLinks,nodeRepository.sprintLinks) {showLinkTasks=false}
    unlinkTask?.let {(id,phase)->projectState.nodesById[id]?.let {task->UnlinkSprintTaskDialog(requireNotNull(currentNodeId),task,phase,nodeRepository.sprintLinks) {unlinkTask=null}}}

    var showSprintMode by rememberSaveable(currentNodeId) { mutableStateOf(false) }
    var changingWorkStateId by rememberSaveable { mutableStateOf<String?>(null) }
    var collapsedSprintSections by rememberSaveable(currentNodeId) { mutableStateOf(listOf<String>()) }
    var historyNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    historyNodeId?.let { id -> projectState.nodesById[id]?.let { node -> NodeHistoryDialog(node, nodeRepository) { historyNodeId = null } } }
    val tagState by remember(nodeRepository) { nodeRepository.tags.observe() }.collectAsState(initial = com.r0ybt.arachn0de.domain.model.TagState())
    var contentOwner by rememberSaveable(project.id) {mutableStateOf<String?>(null)}
    var contentRequest by rememberSaveable(project.id) {mutableStateOf(0)}
    var directEditField by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    fun openNodeEditor(node: com.r0ybt.arachn0de.domain.model.Node, field: String? = null) {
        if (isSubmittingNode || !assignmentsLoaded || !peopleLoaded) return
        directEditField = field
        drafts.open(node.parentId, node.id) {
            EditorDraft(node.id, node.parentId, node.title, node.description, startAt = node.startAt,
                dueAt = node.dueAt, purpose = node.purpose, obligation = node.obligation, priority = node.priority).apply {
                tagIds = tagState.nodeIds[node.id].orEmpty().toList()
                responsibleIds = responsibleByNode[node.id].orEmpty().map { it.id }
                creationGroupId = node.creationGroupId
                captureSharedBaseline()
            }
        }
    }
    val now = com.r0ybt.arachn0de.ui.state.rememberTaskScreenNow(projectState.nodes, clock)
    val calendarZone=java.util.TimeZone.getDefault().id
    val calendarToday=com.r0ybt.arachn0de.domain.model.CalendarDates.localDay(now,java.util.TimeZone.getTimeZone(calendarZone))
    val contextualCalendar=calendarStore.scope("project:${project.id}:${currentNodeId ?: "root"}",calendarToday)
    val contextualBirthdays=rememberContextBirthdays(personRepository)
    val contextualIndex by if(currentNode==null || currentNode.isStructural) rememberContextCalendarIndex(projectState,calendarZone,project.id,currentNodeId)
        else remember {mutableStateOf<com.r0ybt.arachn0de.domain.model.ContextCalendarIndex?>(null)}
    val scopeFilterStore = rememberSaveable(project.id, saver = ScopeFilterStore.Saver) { ScopeFilterStore() }
    val scopeFilters = scopeFilterStore.scope(currentNodeId ?: "project-root")
    val taskViewSession=rememberTaskViewSession()
    val taskViewEnabled=currentNode==null || (currentNode.isStructural && !sprintScope && creationMapping==null && creationRole==null && managementRoot==null)
    val compactHeader=taskViewEnabled && !sprintScope && managementRoot==null
    val headerTaskCounts=remember(projectState,currentNodeId,project.id) {
        if(currentNode==null || currentNode.isStructural)interiorTaskCounts(projectState,project.id,currentNodeId) else null
    }
    var taskViewOpen by remember(project.id,currentNodeId) {mutableStateOf(false)}
    val selectedTaskStatus=if(taskViewEnabled)taskViewSession.status?.name else null
    LaunchedEffect(scopeFilters) {scopeFilters.completion=com.r0ybt.arachn0de.domain.model.CompletionFilter.ALL}
    val scopedFilter = com.r0ybt.arachn0de.domain.model.NodeFilter(
        com.r0ybt.arachn0de.domain.model.TemporalRanges.resolve(scopeFilters.time, now, java.util.TimeZone.getDefault(), java.util.Locale.getDefault()),
        scopeFilters.person, com.r0ybt.arachn0de.domain.model.CompletionFilter.ALL, scopeFilters.tag, scopeFilters.priority,selectedTaskStatus?.let(com.r0ybt.arachn0de.domain.model.TaskStatus::valueOf))
    val completedOrderOverrides=remember(project.id,currentNodeId,selectedTaskStatus) {mutableStateMapOf<String,Pair<Node,Long?>>()}
    val storedCompletedAt by remember(nodeRepository, project.id) { nodeRepository.observeCompletionTimes(project.id) }.collectAsState(initial = emptyMap())
    val completedAt=storedCompletedAt+completedOrderOverrides.mapNotNull {(id,entry)->entry.second?.takeIf {projectState.nodesById[id]==entry.first}?.let {id to it}}.toMap()
    val sortContext = "${project.id}:${currentNodeId ?: "project-root"}"
    val layersFirst=sortPreferences.layersFirst(sortContext)
    val sortConfiguration=sortPreferences.configuration(sortContext)
    val sortMode = NodeSortMode.MANUAL
    var showSortMenu by remember(currentNodeId) { mutableStateOf(false) }
    val otherFiltersActive=scopeFilters.priority!=null || scopeFilters.time!=com.r0ybt.arachn0de.domain.model.TimeFilter.ALL || scopeFilters.person!=null || scopeFilters.tag!=null || contextualCalendar.filtering
    val filterActive = otherFiltersActive || selectedTaskStatus!=null
    val currentNodes = remember(projectState,currentNodeId,layersFirst) {
        NodePresentationSort.children(projectState.childrenOf(currentNodeId),NodeSortMode.MANUAL,layersFirst=layersFirst)
    }
    val sortChoices=projectState.nodes.filter {it.isStructural}.associate {it.id as String? to (NodeSortMode.MANUAL to sortPreferences.layersFirst("${project.id}:${it.id}"))} +
        (null to (NodeSortMode.MANUAL to sortPreferences.layersFirst("${project.id}:project-root")))
    val filteredRows by androidx.compose.runtime.key(project.id,currentNodeId) {androidx.compose.runtime.produceState<List<com.r0ybt.arachn0de.domain.model.FilteredNodeRow>?>(null, projectState, currentNodeId, scopedFilter, sortMode,layersFirst,sortChoices, tagState, responsibleByNode, linkedIds) {
        value = null
        if (!filterActive) { value = emptyList(); return@produceState }
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val physicalRows = com.r0ybt.arachn0de.domain.model.ScopedNodeFilter.apply(projectState, project.id, currentNodeId, scopedFilter,
                responsibleByNode.mapValues { (_, people) -> people.map { it.id }.toSet() }, tagState.nodeIds)
            val externalRows = projectState.nodes.filter { it.id in linkedIds && scopedFilter.matches(it,
                responsibleByNode[it.id].orEmpty().map { person -> person.id }.toSet(), tagIds = tagState.nodeIds[it.id].orEmpty()) }
                .map { com.r0ybt.arachn0de.domain.model.FilteredNodeRow(it, 0, true) }
            val rows = (physicalRows + externalRows).distinctBy { it.node.id }
            NodePresentationSort.filtered(rows, sortMode, emptyMap(),layersFirst) {parent->sortChoices[parent] ?: (sortMode to layersFirst)}
        }
    }}
    val contextualFavorites=LocalElementAccessState.current.favorites
    val calendarEligible=remember(contextualIndex,contextualCalendar.selected,contextualCalendar.conditions,contextualCalendar.favorites,contextualFavorites,now,scopedFilter.copy(taskStatus=null),responsibleByNode,tagState) {
        contextualIndex?.results(contextualCalendar.selected,false,contextualCalendar.favorites,contextualFavorites,now,java.util.TimeZone.getTimeZone(calendarZone),contextualCalendar.conditions)?.filter {node->
            (if(contextualCalendar.selected!=null) scopedFilter.copy(range=null,taskStatus=null) else scopedFilter.copy(taskStatus=null)).matches(node,responsibleByNode[node.id].orEmpty().map {it.id}.toSet(),tagIds=tagState.nodeIds[node.id].orEmpty())
        }.orEmpty()
    }
    val calendarResults=calendarEligible.filter {selectedTaskStatus==null || (it.isCompletable && it.taskStatus.name==selectedTaskStatus)}
    val baseTaskFilter=scopedFilter.copy(taskStatus=null)
    val taskViewInput=TaskViewInput(projectState,project,currentNodeId,baseTaskFilter,if(contextualCalendar.filtering)calendarEligible else null,responsibleByNode,tagState,selectedTaskStatus)
    val taskViewCalculation by produceState<TaskViewCalculation?>(null,taskViewInput) {
        val input=taskViewInput
        value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {input.calculate()}
    }
    val taskViewReady=taskViewCalculation?.source==taskViewInput
    val taskViewUsable=taskViewCalculation?.source?.let {it.project.id==project.id && it.parent==currentNodeId && it.status==selectedTaskStatus}==true
    LaunchedEffect(hasLoaded,taskViewUsable) {
        if(hasLoaded && (!taskViewEnabled || taskViewUsable) && !openReported) {
            openReported=true;ResponseTiming.end("project_content_ready",openedAt)
        }
    }
    val taskProjection=if(taskViewUsable)requireNotNull(taskViewCalculation).projection else TaskViewProjection(emptyList(),emptyMap(),emptySet(),emptyMap(),selectedTaskStatus==TaskStatus.VERIFY.name)
    LaunchedEffect(taskViewEnabled,hasLoaded,taskViewReady,taskProjection.verificationAvailable) {if((currentNode==null || currentNode.isStructural) && hasLoaded && taskViewReady)taskViewSession.validate(taskProjection.verificationAvailable)}
    val taskViewAndroidView=androidx.compose.ui.platform.LocalView.current
    val taskViewControls=if(taskViewEnabled) TaskViewControls(taskViewSession.status,taskProjection.counts,taskProjection.verificationAvailable,taskViewOpen,
        {taskViewOpen=!taskViewOpen},{taskViewOpen=false},{chosen->
            taskViewSession.status=chosen;taskViewOpen=false
            val count=if(chosen==null)taskProjection.total else taskProjection.counts[chosen] ?: 0
            taskViewAndroidView.announceForAccessibility("Mostrando $count tareas${if(chosen==null) "" else " "+taskViewLabel(chosen).lowercase(java.util.Locale.getDefault())}")
        },ready=taskViewReady) else null
    SideEffect {
        actions.taskViewFeedback=if(!taskViewEnabled || selectedTaskStatus==null)null else com.r0ybt.arachn0de.ui.state.TaskViewFeedback({node,peopleOverride,tagsOverride->
            val chain=mutableListOf(node);var parent=node.parentId;val visited=hashSetOf<String>()
            while(parent!=null && visited.add(parent)) {val ancestor=projectState.nodesById[parent] ?: break;chain.add(ancestor);parent=ancestor.parentId}
            val index=com.r0ybt.arachn0de.domain.model.ContextCalendarIndex(com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot(chain),java.util.TimeZone.getTimeZone(calendarZone),project.id,currentNodeId)
            val inScope=index.results(contextualCalendar.selected,false,contextualCalendar.favorites,contextualFavorites,now,java.util.TimeZone.getTimeZone(calendarZone),contextualCalendar.conditions).any {it.id==node.id}
            inScope && (if(contextualCalendar.selected!=null)scopedFilter.copy(range=null)else scopedFilter).matches(node,
                peopleOverride ?: responsibleByNode[node.id].orEmpty().map {it.id}.toSet(),tagIds=tagsOverride ?: tagState.nodeIds[node.id].orEmpty())
        },{taskViewSession.status=null;taskViewOpen=false},{node->
            val previous=completedAt[node.id]
            val restore:(Node)->Unit={restored->completedOrderOverrides[node.id]=restored to previous}
            restore
        })
    }
    val nameSearch=rememberInlineSearch("${project.id}:${currentNodeId.orEmpty()}")
    val nameRows by androidx.compose.runtime.key(projectState,currentNodeId,nameSearch.query,scopedFilter,calendarResults,contextualCalendar.filtering,responsibleByNode,tagState,sortMode,layersFirst,sortChoices) {
        androidx.compose.runtime.produceState<List<com.r0ybt.arachn0de.domain.model.FilteredNodeRow>?>(null) {
            if(nameSearch.active) value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                inlineNameRows(projectState,project.id,currentNodeId,nameSearch.query,scopedFilter,
                    responsibleByNode.mapValues {it.value.map {person->person.id}.toSet()},tagState.nodeIds,
                    order={rows->NodePresentationSort.filtered(rows,sortMode,emptyMap(),layersFirst) {parent->sortChoices[parent] ?: (sortMode to layersFirst)}})
                    .filter {row->!contextualCalendar.filtering || calendarResults.any {it.id==row.node.id}}
            }
        }
    }
    val filteredIds = remember(filteredRows) { filteredRows.orEmpty().mapTo(hashSetOf()) { it.node.id } }
    RegisterGlobalSort(sortPreferences.loaded,filter=filterActive) {showSortMenu=true}
    if (scopeFilters.open) ScopeFiltersDialog(scopeFilters, people, tagState.tags,showState=false,taskControls=taskViewControls,cardFilters=contextualCalendar)
    if (showSortMenu) NodeSortMenu(sortConfiguration,{sortPreferences.configure(sortContext,it)},{showSortMenu=false},
        cardFilters=contextualCalendar,onApply={done->
            val ids=if(taskViewEnabled && filterActive)taskProjection.rows.mapTo(hashSetOf()) {it.node.id}
                else if(contextualCalendar.filtering)calendarResults.mapTo(hashSetOf()) {it.id}
                else if(filterActive)filteredRows.orEmpty().mapTo(hashSetOf()) {it.node.id} else null
            sortPreferences.apply(sortContext,ids,done)
        },filters=scopeFilters,people=people,tags=tagState.tags,taskControls=taskViewControls,projectDefault=currentNodeId==null,owns=sortPreferences.owns(sortContext),
        onInherit=if(currentNodeId==null)null else ({sortPreferences.inherit(sortContext)}),
        threeStates=(currentNode?.let {com.r0ybt.arachn0de.domain.model.TaskStatePolicy.count(it,projectState.nodesById,project)==3} ?: project.verificationEnabled) || (taskViewUsable && taskProjection.verificationAvailable))
    val selection = remember(project.id, currentNodeId, scopedFilter) { com.r0ybt.arachn0de.ui.state.NodeSelection() }
    val visibleSelectionIds = if(taskViewEnabled && filterActive)taskProjection.rows.mapTo(hashSetOf()) {it.node.id} else if(contextualCalendar.filtering) calendarResults.map {it.id}.toSet() else if(filterActive) filteredRows.orEmpty().map { it.node.id }.toSet() else currentNodes.orEmpty().map { it.id }.toSet()
    val selectionReady=if(taskViewEnabled && filterActive)taskViewUsable else if(contextualCalendar.filtering) contextualIndex!=null else if(filterActive) filteredRows!=null else currentNodes!=null
    LaunchedEffect(visibleSelectionIds,selectionReady) {if(selectionReady && !nameSearch.active) selection.retain(visibleSelectionIds)}
    var bulkDelete by remember(selection) { mutableStateOf(false) }
    var bulkMove by remember(selection) { mutableStateOf(false) }
    var groupReview by remember { mutableStateOf<List<com.r0ybt.arachn0de.domain.model.Node>?>(null) }

    LaunchedEffect(draft?.creationId) { groupReview = null }

    val attention by com.r0ybt.arachn0de.ui.state.rememberAttention(projectState, now)
    val currentMonth = remember(now) { com.r0ybt.arachn0de.domain.model.CalendarDates.localDay(now, java.util.TimeZone.getDefault()).calendarMonth }
    val financial by com.r0ybt.arachn0de.ui.state.rememberFinancial(projectState, responsibleByNode,
        com.r0ybt.arachn0de.domain.model.FinancialSelection(com.r0ybt.arachn0de.domain.model.FinancialPeriod.ALL), currentMonth, java.util.TimeZone.getDefault().id)
    val pathNodes = currentPath.mapNotNull { projectState.nodesById[it] }
    val currentLayer = pathNodes.size
    val progressMap = projectState.progressById

    val load = remember(project.id, nodeRepository) { LoadState() }
    LaunchedEffect(project.id, nodeRepository, load.attempt) {
        load.collect(nodeRepository.observePreparedProjectState(project.id)) { snapshot ->
            pendingOpenNode?.let { target ->
                currentPath.clear()
                if (snapshot.nodesById[target]?.projectId == project.id) currentPath.add(target)
                handleOpenNode()
            }
            // Preserve the open node when its ancestry changes, using only confirmed data.
            val openId = currentPath.lastOrNull()
            if (openId != null && openId in snapshot.nodesById) {
                val updatedPath = mutableListOf<String>()
                var cursor: String? = openId
                while (cursor != null) {
                    updatedPath.add(cursor)
                    cursor = snapshot.nodesById.getValue(cursor).parentId
                }
                currentPath.clear()
                currentPath.addAll(updatedPath.asReversed())
            }
            // Validate only against a persisted emission, never the initial empty snapshot.
            var parentId: String? = null
            val validPath = currentPath.takeWhile { id ->
                val node = snapshot.nodesById[id]
                (node != null && node.projectId == project.id && node.parentId == parentId).also {
                    if (it) parentId = id
                }
            }
            while (currentPath.size > validPath.size) currentPath.removeAt(currentPath.lastIndex)
            // Data invalidation may return to an ancestor; this is not a user visit.
            if (currentPath.lastOrNull() != openId) recordedNode = currentPath.lastOrNull()
            projectState = snapshot
            hasLoaded = true
        }
    }

    BackHandler {
        if(restoreReference()) Unit
        else if(onBackToSearch!=null) onBackToSearch()
        else if (onBackToObligations != null) onBackToObligations()
        else if (onBackToCalendar != null) onBackToCalendar()
        else if (currentPath.isNotEmpty()) {
            currentPath.removeAt(currentPath.lastIndex)
        } else {
            onBackToProjects()
        }
    }

    BackHandler(enabled=!nameSearch.open && currentNodeId==null && coreContext!=null && draft==null && projectEditField==null) {coreContext=null}
    BackHandler(enabled = !nameSearch.open && selection.ids.isNotEmpty()) { selection.clear() }
    BackHandler(enabled = showDrawer) { showDrawer = false }

    if (!hasLoaded) {
        Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {
            Column(Modifier.padding(16.dp).testTag("project-loading"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=onBackToProjects) {LucideIcon(Icons.AutoMirrored.Filled.ArrowBack,"Volver",tint=androidx.compose.material3.LocalContentColor.current)}
                    NavigationHeading(project.name,Modifier.weight(1f))
                }
                OperationProgress(!load.failed)
                Text(if(load.failed) "No se pudo cargar el contenido" else "Cargando contenido…",style=androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
        LoadErrorDialog(load)
        return
    }

    OperationErrorDialog(defaultsOperation)
    val background = Arachn0deColors.Background

    // Ordinary hierarchy elements use the shared module. Specialized management views retain their editors.
    val useNodeModule=currentNode!=null && creationMapping==null && creationRole==null && managementRoot==null && !sprintScope
    val nodeBackDispatcher=androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var nodeEditRequest by remember(currentNodeId) {mutableStateOf<com.r0ybt.arachn0de.domain.model.NodeProperty?>(null)}
    val nodeTechnologies by (LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).technologyRepository.state.collectAsState()
    var shareExport by rememberSaveable(currentNodeId) {mutableStateOf(false)}
    if(shareExport) ShareExportPanel(currentNode?.title ?: project.name,project.id,currentNode?.id,currentNode==null || currentNode.isStructural,
        !copyActions.busy,!copyActions.busy,{descendants->
            if(currentNode==null) copyActions.copyProject(project,projectState,descendants) else copyActions.copy(projectState,currentNode.id,descendants)
        }) {shareExport=false}
    var saveStructure by remember(currentNodeId) {mutableStateOf(false)}
    var createStructure by remember(currentNodeId) {mutableStateOf(false)}
    if(saveStructure) SaveStructurePanel(project.id,currentNodeId,currentNode?.title ?: project.name) {saveStructure=false}
    if(createStructure) StructureCreationPanel(project.id,currentNodeId,openLibrary=true,
        onNew={createStructure=false;openTraditionalElement(quick=true)},onDismiss={createStructure=false})
    val favoriteAction=rememberFavoriteMenuAction(if(currentNode==null)"project:${project.id}" else "node:${currentNode.id}",!isSubmittingNode)
    val contextActions:ContextActions.()->Unit={
        favoriteAction?.let {items+=it}
        action("Guardar como plantilla",Icons.Default.Description,{showContextActions=false;saveStructure=true},enabled=!isSubmittingNode)
        exportSpec=ElementOptionsExport(project.id,currentNode?.id,currentNode==null || currentNode.isStructural,!copyActions.busy,!copyActions.busy) {descendants->
            if(currentNode==null)copyActions.copyProject(project,projectState,descendants) else copyActions.copy(projectState,currentNode.id,descendants)
        }
        if(currentNode==null || currentNode.isStructural || (currentNode.isCompletable && currentNode.workState==null)) submenu("Configurar estados",androidx.compose.material.icons.Icons.Default.Settings,enabled=!isSubmittingNode) {close->TaskStateSettingsItems(project=if(currentNode==null) project else null,node=currentNode) {close();showContextActions=false}}
        if(currentNode?.isCompletable==true && currentNode.workState==null) submenu("Cambiar estado",androidx.compose.material.icons.Icons.Default.Repeat,enabled=!isSubmittingNode) {close->TaskStatusItems(currentNode,{status,done->actions.setTaskStatus(currentNode.id,status,done)}) {close();showContextActions=false}}
        action("Compartir y exportar",androidx.compose.material.icons.Icons.Default.Share,{showContextActions=false;shareExport=true},enabled=!isSubmittingNode)
        cleanCompleted?.let {clean->action("Limpiar completadas",androidx.compose.material.icons.Icons.Default.Delete,{showContextActions=false;clean()})}
        if(currentNode==null || currentNode.isStructural) action(
            if(currentNode?.isPinned ?: project.isPinned) "Desfijar" else "Fijar",
            androidx.compose.material.icons.Icons.Default.PushPin, {
                defaultsOperation.submit("No se pudo cambiar el estado fijado.", {
                    if(currentNode==null) projectRepository.setPinned(project.id,!project.isPinned)
                    else nodeRepository.setPinned(currentNode.id,!currentNode.isPinned)
                }) {showContextActions=false}
            },enabled=!isSubmittingNode)
        if(contextualCreationLabel!=null) action("Nuevo elemento",androidx.compose.material.icons.Icons.Default.Add,{showContextActions=false;openTraditionalElement()},enabled=!isSubmittingNode && currentNode?.canReceiveChildren==true)
        if (currentNode == null) action("Fotografía", androidx.compose.material.icons.Icons.Default.Image, {
            showContextActions = false; showProjectPhoto = true
        }, enabled = !isSubmittingNode)
        action("Recurrencias", androidx.compose.material.icons.Icons.Default.Refresh, { showContextActions=false; recurrenceSelected="" })
        if(currentNode != null) {
            // Inline Kanban does not expose the ordinary editable context title.
            if(creationMapping?.kind=="KANBAN" && managementContexts.any {it.id==creationMapping.contextId && it.enabled})
                action("Editar",androidx.compose.material.icons.Icons.Default.Edit,{
                    showContextActions=false;openNodeEditor(currentNode)
                },enabled=!isSubmittingNode && assignmentsLoaded && peopleLoaded)
            action("Eliminar", androidx.compose.material.icons.Icons.Default.Delete, { showContextActions=false;deletingNodeId=currentNode.id;deletingNodeName=currentNode.title }, enabled=!isSubmittingNode,destructive=true)
            action("Responsables", androidx.compose.material.icons.Icons.Default.People, { showContextActions=false;if(useNodeModule) nodeEditRequest=com.r0ybt.arachn0de.domain.model.NodeProperty.PEOPLE else responsibleNodeId=currentNode.id }, enabled=!isSubmittingNode && !personActions.operation.busy && peopleLoaded && assignmentsLoaded)
            if(!currentNode.isStructural) action("Mover a Borradores", androidx.compose.material.icons.Icons.Default.Description, {actions.moveToDrafts(currentNode.id) {showContextActions=false}}, enabled=!isSubmittingNode)
            action("Mover a…", androidx.compose.material.icons.Icons.Default.DriveFileMove, { showContextActions=false;movingNodeId=currentNode.id }, enabled=!isSubmittingNode)
            action("Historial", androidx.compose.material.icons.Icons.Default.History, { showContextActions=false;historyNodeId=currentNode.id })
        }
        if(currentNode == null || (currentNode.isStructural)) action("Valores iniciales", androidx.compose.material.icons.Icons.Default.Settings, {
            showContextActions = false; showDefaults = true
        }, enabled = !isSubmittingNode)
        if(currentNode != null && !currentNode.hasChildren) action(
            if(currentNode.purpose != NodePurpose.ACTION) "Convertir en tarea" else "Convertir en nota",
            androidx.compose.material.icons.Icons.Default.SwapHoriz, {
                if(currentNode.obligation != null) { showContextActions = false; convertingPurpose=NodePurpose.NOTE;convertingObligationId = currentNode.id }
                else actions.convert(currentNode.id,if(currentNode.purpose != NodePurpose.ACTION) NodePurpose.ACTION else NodePurpose.NOTE) { showContextActions = false }
            },enabled = !isSubmittingNode)
        if(currentNode!=null && !currentNode.isStructural) action("Convertir en capa",androidx.compose.material.icons.Icons.Default.AccountTree,{
            showContextActions=false
            if(currentNode.obligation!=null) { convertingPurpose=NodePurpose.LAYER;convertingObligationId=currentNode.id }
            else actions.convert(currentNode.id,NodePurpose.LAYER)
        },enabled=!isSubmittingNode)
        if(currentNode?.isStructural == true) action("Convertir en proyecto", androidx.compose.material.icons.Icons.Default.AccountTree, { showContextActions=false;promotingLayerId=currentNode.id }, enabled=!isSubmittingNode)
        if(currentNode==null) action("Convertir en capa",androidx.compose.material.icons.Icons.Default.AccountTree,{ showContextActions=false;moveProject=true },enabled=!isSubmittingNode)

    }
    val creationActions:@Composable ()->Unit={
                Row(Modifier.fillMaxWidth().background(navigationBarBackground)
                    .testTag("project-bottom-actions"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    val enabled=!isSubmittingNode && currentNode?.sprintMode!=true && (currentNode==null || currentNode.canReceiveChildren)
                    TextButton(onClick={openTraditionalElement(quick=true)},
                        modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(8.dp),
                        colors=ButtonDefaults.textButtonColors(contentColor=Arachn0deColors.TextPrimary),enabled=enabled) {
                        NavigationBarIcon(androidx.compose.material.icons.Icons.Default.Add,label="Nuevo elemento")
                        Spacer(Modifier.width(6.dp))
                        Text("Nuevo",maxLines=1,style=MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick={createStructure=true},
                        modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(8.dp),
                        colors=ButtonDefaults.textButtonColors(contentColor=Arachn0deColors.TextPrimary),enabled=enabled) {
                        NavigationBarIcon(androidx.compose.material.icons.Icons.Default.Description,label="Desde plantilla")
                        Spacer(Modifier.width(6.dp))
                        Text("Desde plantilla",style=MaterialTheme.typography.labelSmall)
                    }

                }
    }
    LaunchedEffect(entryAction,currentNode?.id,peopleLoaded,assignmentsLoaded,recurrenceByNode) {
        if(entryAction=="content" && entryNodeId==null && currentNodeId==null) {
            coreContext=CoreBottomContext.Content;onEntryActionHandled();return@LaunchedEffect
        }
        val node=currentNode
        if(entryAction!=null && node!=null && node.id==entryNodeId && peopleLoaded && assignmentsLoaded) {
            if(entryAction=="recurrence" && recurrenceByNode[node.id]==null) return@LaunchedEffect
            when(entryAction) {
                "recurrence"->recurrenceSelected=recurrenceByNode[node.id]
                "content"->if(useNodeModule) {contentOwner=node.id;contentRequest++} else openNodeEditor(node)
                "edit"->openNodeEditor(node)
                "delete"->{deletingNodeId=node.id;deletingNodeName=node.title}
                "move"->movingNodeId=node.id
                "people"->responsibleNodeId=node.id
                "history"->historyNodeId=node.id
                "copy","copy-descendants"->copyActions.copy(projectState,node.id,entryAction=="copy-descendants")
                "toggle"->if(node.workState==null) actions.cycleTaskStatus(node.id)
                "advance"->Unit
                "state"->if(node.workState==null)showContextActions=true
                "layer"->if(node.obligation!=null) {convertingPurpose=NodePurpose.LAYER;convertingObligationId=node.id} else actions.convert(node.id,NodePurpose.LAYER)
                "convert"->if(node.obligation!=null) {convertingPurpose=NodePurpose.NOTE;convertingObligationId=node.id} else actions.convert(node.id,NodePurpose.NOTE)
            }
            onEntryActionHandled()
        }
    }
    val bottomControlsHeight=0.dp
    Surface(modifier = Modifier.fillMaxSize(), color = Arachn0deColors.Background) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .then(if(projectEditField!=null) Modifier.imePadding() else Modifier),
        ) {
            val viewport=LocalBottomViewportHeight.current.let {if(it.value.isNaN())maxHeight else it}
            val coreModuleMaxHeight=(viewport*.5f).coerceAtLeast(120.dp).coerceAtMost(viewport)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if(showDrawer) Modifier.clearAndSetSemantics {} else Modifier)
                    .padding(horizontal = 4.dp, vertical = 10.dp),
            ) {
                OperationProgress(isSubmittingNode)
                HeaderBar(onMenuClick = { showDrawer = true }, compactLock=true, showTitle=false,
                    headingContent={
                        val ancestors=if(currentNode==null) emptyList() else
                            currentPath.dropLast(1).asReversed().mapNotNull { id ->
                                projectState.nodesById[id]?.let { AncestorLocation(id,it.title) }
                            }+AncestorLocation(null,project.name)
                        HierarchyRoute(ancestors) { targetId ->
                            if(targetId==null) {currentPath.clear();referenceReturns=emptyList()}
                            else actions.navigate(targetId) {path -> currentPath.clear();currentPath.addAll(path)}
                        }
                    },
                    onBack=if(useNodeModule) ({nodeBackDispatcher?.onBackPressed();Unit}) else if(referenceReturns.lastOrNull()?.let {org.json.JSONObject(it).getString("target")==currentNodeId}==true) ({restoreReference();Unit}) else if(onBackToSearch!=null) onBackToSearch else if(currentPath.isNotEmpty()) ({
                        if(onBackToObligations!=null) onBackToObligations()
                        else if(onBackToCalendar!=null) onBackToCalendar()
                        else currentPath.removeAt(currentPath.lastIndex)
                    }) else null,
                    backDescription=if(onBackToSearch!=null) "Volver a búsqueda" else if(onBackToObligations!=null) "Volver a Pagos" else if(onBackToCalendar!=null) "Volver al Calendario" else "Volver a la capa anterior",
                    trailingAction={
                        if(LocalGlobalNavigation.current?.bottomAccess!=true) IconButton(onClick={showSortMenu=true},enabled=sortPreferences.loaded,modifier=Modifier.size(48.dp).semantics {stateDescription="Orden: "+(if(sortConfiguration.manual) "Manual" else sortConfiguration.rules.filter {it.enabled}.joinToString(" → ") {it.criterion.label})}) {LucideIcon(Icons.AutoMirrored.Filled.Sort,"Ordenar")}
                        if(LocalGlobalNavigation.current?.bottomAccess!=true) IconButton(onClick={scopeFilters.open=true},modifier=Modifier.size(48.dp)) {LucideIcon(Icons.Default.FilterList,if(filterActive) "Filtros activos" else "Filtros",tint=if(filterActive) Arachn0deColors.Accent else Arachn0deColors.TextSecondary)}
                        if(currentNodeId!=null && !useNodeModule) IconButton(onClick={showContextActions=true},modifier=Modifier.size(48.dp)) {LucideIcon(Icons.Default.MoreVert,"Opciones del elemento")}
                    })
                BashPrompt(bashLocation(project.name,currentNode,projectState.nodesById),
                    if(currentNode==null || currentNode.isStructural) "ls -l" else "cat "+bashQuote(bashFileName(com.r0ybt.arachn0de.data.repository.storyDisplayTitle(currentNode.id,currentNode.title,LocalStoryLabels.current),currentNode.purpose)))
                if(selection.ids.isNotEmpty()) Column(Modifier.fillMaxWidth()) {
                    Text("${selection.ids.size} seleccionados", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryAction("Mover", { bulkMove = true }, enabled = !isSubmittingNode)
                        DestructiveAction("Eliminar", { bulkDelete = true }, enabled = !isSubmittingNode)
                        TextButton(onClick = { selection.clear() }) { Text("Salir") }
                    }
                }

                layerScrollStates.SaveableStateProvider(currentNodeId?.let { "node:$it" } ?: "project-root") {
                    val originalNodeListState = rememberLazyListState()
                    val searchNodeListState = rememberLazyListState()
                    val nodeListState = if(nameSearch.active)searchNodeListState else originalNodeListState
                    val groups: Map<Any,List<String>> = if(layersFirst) mapOf(
                        "pinned-layers" to currentNodes.orEmpty().filter {it.isStructural && it.isPinned}.map {it.id},
                        "layers" to currentNodes.orEmpty().filter {it.isStructural && !it.isPinned}.map {it.id},
                        "pending" to currentNodes.orEmpty().filter {sortMode==NodeSortMode.MANUAL && !it.isStructural && !it.isCompleted}.map {it.id},
                        "completed" to currentNodes.orEmpty().filter {sortMode==NodeSortMode.MANUAL && !it.isStructural && it.isCompleted}.map {it.id})
                    else listOf(false,true).flatMap {completed->listOf(("pinned:$completed" as Any) to currentNodes.orEmpty().filter {it.isCompleted==completed && it.isStructural && it.isPinned}.map {it.id},(completed as Any) to currentNodes.orEmpty().filter {it.isCompleted==completed && !it.isPinned}.map {it.id})}.toMap()
                    val dragDisabled = nameSearch.active || taskViewOpen || sprintScope || isSubmittingNode || selection.ids.isNotEmpty() || filterActive || (sortMode != NodeSortMode.MANUAL && !layersFirst) || currentNodes == null
                    val drag = rememberDragReorderState(nodeListState, "node:", groups, dragDisabled, actions.operation.error, "$currentNodeId:${sortMode.name}:$layersFirst:$filterActive") { source, target, _ ->
                        if(layersFirst && projectState.nodesById[source]?.isStructural==true && !filterActive) actions.reorderLayersTo(source,currentNodeId,target)
                        else if(layersFirst && sortMode==NodeSortMode.MANUAL && !filterActive) actions.reorderContentTo(source,currentNodeId,target)
                        else if (sortMode == NodeSortMode.MANUAL && !filterActive) actions.reorderTo(source, currentNodeId, target)
                    }
                    SideEffect {drag.onBlockedDrag=if(selectedTaskStatus!=null) ({actions.blockedReorder()}) else null}
                    val boardMapping=creationMapping?.takeIf {it.kind=="KANBAN" && managementContexts.any {c->c.id==it.contextId && c.enabled}}
                    if(boardMapping!=null && !nameSearch.active) ManagedKanbanDialog(boardMapping.contextId,nodeRepository.management.kanban,{openReference(it)},onPlanning={cascadeLayers.firstOrNull {it.contextId==boardMapping.contextId && it.kind=="SPRINTS"}?.let {openReference(it.nodeId)}},onDismiss={if(!restoreReference() && currentPath.isNotEmpty()) currentPath.removeAt(currentPath.lastIndex)},inline=true,modifier=Modifier.weight(1f),bottomInset=bottomControlsHeight,description=scopeDescription,onEditDescription={currentNode?.let {openNodeEditor(it,"description")}})
                    else if(!hasLoaded || (taskViewEnabled && !taskViewUsable) || (contextualCalendar.filtering && contextualIndex==null) || (filterActive && filteredRows==null) || (!filterActive && currentNodes==null))
                        Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center) {androidx.compose.material3.CircularProgressIndicator()}
                    else LazyColumn(
                        state = nodeListState,
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("nodes-list").then(drag.gestureModifier).ignoreTaskViewTaps(taskViewOpen),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom=if(taskViewEnabled)0.dp else bottomControlsHeight),
                    ) {
                        if(currentNode==null || currentNode.isStructural) item(key="context-calendar:$currentNodeId") {
                            ContextCalendar(contextualCalendar,contextualIndex,calendarToday,resultCount=calendarResults.size,birthdays=contextualBirthdays)
                        }
                        if(!sprintScope && contextualCalendar.selected?.let {contextualBirthdays?.on(it)?.isNotEmpty()}==true)
                            item(key="context-birthdays") {ContextBirthdayCards(contextualBirthdays,contextualCalendar.selected)}
                        item(key = "layer-context", contentType = "context") {
                            Column {
                                Spacer(modifier = Modifier.height(if(compactHeader)0.dp else 14.dp))


                                if (currentNode == null) Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) { DetailScopeTitle(project.name,compact=compactHeader) }
                                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val nodeTitle:@Composable ()->Unit={
                                        Box(Modifier.weight(1f)) {
                                            DetailScopeTitle(com.r0ybt.arachn0de.data.repository.storyDisplayTitle(currentNode.id,currentNode.title,LocalStoryLabels.current),
                                                compact=compactHeader,level=currentLayer,layer=currentNode.isStructural)
                                        }
                                    }
                                    if(!useNodeModule) nodeTitle()
                                    if (currentNode.isCompletable && currentNode.workState == null) {
                                        val colors = completionControlColors(false, currentNode.isCompleted, !isSubmittingNode)
                                        androidx.compose.material3.IconButton(
                                            onClick = rememberPaymentCheck(currentNode) { actions.cycleTaskStatus(currentNode.id) },
                                            enabled = !isSubmittingNode,
                                            modifier = Modifier.size(48.dp).testTag("detail-completion:${currentNode.id}")
                                                .semantics { stateDescription = currentNode.taskStatus.label;contentDescription="Cambiar estado: ${currentNode.title}" }
                                                .background(if (currentNode.isCompleted) colors.fill else Arachn0deColors.ControlSurface,
                                                    androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
                                        ) {
                                            if (currentNode.isCompleted) LucideIcon(androidx.compose.material.icons.Icons.Default.Check,
                                                null, tint = colors.mark)
                                            else if(currentNode.awaitingVerification) VerificationMark()
                                            else Box(Modifier.size(14.dp).border(2.dp, colors.border, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                                )
                                        }
                                    }
                                    if(useNodeModule) nodeTitle()
                                }
                                Spacer(modifier = Modifier.height(0.dp))
                                if(LocalBashPresentation.current) {
                                    if(currentNode==null) Text("id=${project.id}") else BashNodeProperties(currentNode)
                                }
                                if(currentNode==null && LocalBashPresentation.current) Text("type=directory",color=Arachn0deColors.TextSecondary,fontSize=12.sp)

                                if(currentNode==null && !compactHeader) androidx.compose.foundation.layout.FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text("${projectState.nodes.count {it.isCompletable && !it.isCompleted}} sin completar",style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary)
                                    AttentionIndicator(attention?.byProjectId?.get(project.id))
                                }
                            }
                        }
                        if(scopeDescription.isNotBlank()) item(key="scope-description:$currentNodeId",contentType="description") {
                            GeneralCard(
                                modifier = Modifier.fillMaxWidth().testTag("detail-description"),
                                shape = RoundedCornerShape(10.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = Arachn0deColors.ControlSurface,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = if(compactHeader)4.dp else 10.dp),
                                ) {
                                    if(!compactHeader) Text(
                                        text = if(LocalBashPresentation.current) "description=" else "DESCRIPCIÓN",
                                        color = Arachn0deColors.TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.08.sp,
                                    )
                                    if(!compactHeader) Spacer(modifier = Modifier.height(4.dp))
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.Top) {
                                        PropertyIdentityIcon(description=true,modifier=Modifier.padding(top=2.dp).size(16.dp))
                                        AttachmentText(
                                            modifier = Modifier.weight(1f),
                                            text = scopeDescription,
                                            ownerNodeId=currentNodeId,
                                            selectableDescription=true,
                                            webLinks=currentNode?.purpose in setOf(NodePurpose.ACTION,NodePurpose.NOTE),
                                            color = Arachn0deColors.TextPrimary,
                                            fontSize = ContentTypography.Detail,
                                            lineHeight = ContentTypography.DetailLine,
                                        )
                                    }
                                }
                            }
                        }

                        item(key="scope-details:$currentNodeId",contentType="context") {
                            Column {
                                cascadeError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                                if(currentNode?.purpose==com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION) {
                                    val byNode=projectState.nodes.associateBy {it.id}
                                    val boundaries=managementRoots.filter {root->managementContexts.any {it.id==root.id}}.associateBy {it.nodeId}
                                    var ancestor:String?=currentNode.id
                                    var owner:String?=null
                                    while(ancestor!=null) {
                                        val boundary=boundaries[ancestor]
                                        if(boundary!=null) {owner=boundary.id;break}
                                        ancestor=byNode[ancestor]?.parentId
                                    }
                                    if(owner==null) owner=managementRoots.firstOrNull {it.projectId==project.id}?.id
                                    val workContext=managementContexts.firstOrNull {it.id==owner && it.enabled}
                                    if(workContext!=null) ResponsibilitiesAccess(workContext.id,currentNode.id,false,nodeRepository.management.participation)
                                }
                                if (currentNode != null) AttentionIndicator(if (currentNode.isStructural) attention?.byNodeId?.get(currentNode.id) else null)
                                if(currentNode==null || currentNode.isStructural) FinancialSummaryCard(
                                    if (currentNode == null) financial?.byProjectId?.get(project.id) else financial?.byNodeId?.get(currentNode.id), title = "Pagos · Todo el período",
                                    overview = if (currentNode == null) financial?.overviewByProjectId?.get(project.id) else financial?.overviewByNodeId?.get(currentNode.id),modifier=Modifier.testTag("detail-payments"))
                                if (sprintScope && !managedSprint) {
                                    TextButton(onClick={showLinkTasks=true},enabled=!isSubmittingNode) {Text("Vincular tareas")}
                                    Spacer(modifier = Modifier.height(12.dp))
                                }


                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    FlowRow(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(0.dp),itemVerticalAlignment=Alignment.CenterVertically) {
                                        CardIndicatorDetail(currentNode?.id?.let {"node:$it"} ?: "project:${project.id}",onOpenNode={id->openReference(id)},onOpenPeople=onOpenPeople,onOpenTechnologies=onOpenTechnologies,managementContext=managementContexts.firstOrNull {it.enabled && it.id==(cascadeRoles.owners[currentNodeId] ?: managementContext?.id)}?.id,participationRepository=nodeRepository.management.participation,showPeople=!managedSprint,fillWidth=false,taskCounts=headerTaskCounts,verificationEnabled=currentNode?.let {TaskStatePolicy.count(it,projectState.nodesById,project)==3} ?: project.verificationEnabled)
                                        if(teamScope && responsibleByNode[currentNodeId].orEmpty().isNotEmpty()) Text("Responsables del contenido Equipo",style=MaterialTheme.typography.labelLarge)
                                        if(!managedSprint) CompactAssignments(currentNode?.id ?: project.id, currentNode?.let {responsibleByNode[it.id]}.orEmpty(), project=currentNode==null,showTechnologies=currentNode!=null,fillWidth=false,compactSpacing=true,showPeople=currentNode?.isStructural!=true)
                                    }
                                    val scopeTags=if(currentNode==null) tagState.forProject(project.id) else tagState.forNode(currentNode.id)
                                    if(scopeTags.isNotEmpty()) InteriorTagBadge(scopeTags,Modifier.widthIn(max=120.dp))
                                }
                                if (currentNode != null) {
                                    androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    }
                                    if (currentNode.purpose == NodePurpose.NOTE && cascadeLayers.none {it.nodeId==currentNode.parentId && it.kind=="ACTA"}) Text("Nota · Convierte en capa para añadir hijos", color = Arachn0deColors.TextSecondary)
                                    ObligationIndicator(currentNode,compactSummary=false)
                                    if (currentNode.obligation != null) Text("Convierte este pago en una tarea antes de usarla como capa.", color = Arachn0deColors.TextSecondary)
                                    TaskDateIndicator(currentNode, now)

                                    if(!compactHeader) Spacer(modifier = Modifier.height(12.dp))
                                }

                            }
                        }
                        // Both normal and semaphore results use the same card and action wiring.
                        val renderCard: @Composable (Node, List<Node>, Boolean, Modifier) -> Unit = { node, renderNodes, filtered, cardModifier ->
                            val enterCard:()->Unit={
                                    if(contextualCalendar.filtering) {
                                        openReference(node.id)
                                    } else if(filtered) {
                                        actions.navigate(node.id) { path -> currentPath.clear();currentPath.addAll(path) }
                                    } else if(sprintScope && node.id in linkedIds) {
                                        val path=mutableListOf<String>();var cursor:Node?=node
                                        while(cursor!=null) {path.add(cursor.id);cursor=projectState.nodesById[cursor.parentId]}
                                        openReference(node.id)
                                    } else if(currentPath.lastOrNull()==node.parentId) currentPath.add(node.id)
                                }
                            NodeCard(
                                navigationTool=cascadeLayers.firstOrNull {it.nodeId==node.id}?.kind?.let {kind->
                                    if(kind.startsWith("OBJECTIVE:")) ManagementToolVisual(node.title,"Resultado del proyecto",Icons.Default.Flag) else managementToolVisual(kind)
                                } ?: if(node.sprintMode && creationMapping?.kind=="SPRINTS") sprintCardVisual(node.id,node.title,iterationOverview,sprintOverviewRefs.count {it.sprintId==node.id}) else null
                                ?: cascadeRoles.records.roles.firstOrNull {it.nodeId==node.id}?.let {role->
                                    if(role.role=="EPIC") ManagementToolVisual(node.title,"${storiesByEpic[node.id].orEmpty().size} historias asociadas",Icons.Default.Workspaces)
                                    else ManagementToolVisual(node.title,"Historia de usuario",Icons.Default.Article)
                                } ?: if(node.purpose==NodePurpose.NOTE && creationMapping?.kind=="ACTA") ManagementToolVisual(node.title,"Sección del Acta",Icons.Default.Description) else null,
                                taskViewCount=if(taskViewEnabled && taskViewUsable && node.isStructural)taskProjection.containerCounts[node.id] ?: 0 else null,
                                onTaskStatusChange={status,done->actions.setTaskStatus(node.id,status,done)},
                                stateBusy = isSubmittingNode,
                                onHistory = { historyNodeId = node.id },
                                tags = tagState.forNode(node.id),
                                node = node,
                                onRecurrence = recurrenceByNode[node.id]?.let { ruleId -> ({ recurrenceSelected = ruleId }) },
                                progress = progressMap[node.id],
                                hasChildren = node.hasChildren,
                                onConvertToProject = if (node.isStructural) ({ promotingLayerId = node.id }) else null,
                                onMakeLayer = { if(node.obligation!=null) { convertingPurpose=NodePurpose.LAYER;convertingObligationId=node.id } else actions.convert(node.id,NodePurpose.LAYER) },
                                onConvert = { done -> if (node.obligation != null) { convertingPurpose=NodePurpose.NOTE;convertingObligationId = node.id; done() } else actions.convert(node.id, if (node.purpose != NodePurpose.ACTION) NodePurpose.ACTION else NodePurpose.NOTE, onSuccess = done) },
                                canToggleComplete = node.isCompletable,
                                onOpen = enterCard,
                                responsiblePeople = responsibleByNode[node.id].orEmpty(),
                                now = now,
                                attention = attention?.byNodeId?.get(node.id),
                                cardAlerts = attention?.cardAlertsByNodeId?.get(node.id),
                                onResponsible = { if (!isSubmittingNode && !personActions.operation.busy && peopleLoaded && assignmentsLoaded) responsibleNodeId = node.id },
                                selected = node.id in selection.ids,
                                selecting = selection.ids.isNotEmpty(),
                                onSelect = { if(!isSubmittingNode) selection.toggle(node.id) },
                                onMove = { if (!isSubmittingNode) movingNodeId = node.id },
                                canCopy = !copyActions.busy,
                                canDragReorder = !filtered && !dragDisabled && !layersFirst,
                                onCopy = { descendants -> copyActions.copy(projectState, node.id, descendants) },
                                onEdit = { openNodeEditor(node) },
                                onContent=if(node.workState==null && !node.sprintMode && !sprintScope && managementRoots.none {it.nodeId==node.id} && cascadeLayers.none {it.nodeId==node.id} && cascadeRoles.records.roles.none {it.nodeId==node.id}) ({contentOwner=node.id;contentRequest++;enterCard()}) else null,
                                canMoveUp = !filtered && !sprintScope && !isSubmittingNode && (if(layersFirst && node.isStructural) node.id!=renderNodes.firstOrNull {it.isStructural && it.isPinned==node.isPinned}?.id else sortMode==NodeSortMode.MANUAL && node.id!=renderNodes.firstOrNull {it.isPinned==node.isPinned && (if(layersFirst) !it.isStructural else true)}?.id),
                                canMoveDown = !filtered && !sprintScope && !isSubmittingNode && (if(layersFirst && node.isStructural) node.id!=renderNodes.lastOrNull {it.isStructural && it.isPinned==node.isPinned}?.id else sortMode==NodeSortMode.MANUAL && node.id!=renderNodes.lastOrNull {it.isPinned==node.isPinned && (if(layersFirst) !it.isStructural else true)}?.id),
                                onReorder = { moveUp, onSuccess ->
                                    if(layersFirst && node.isStructural) actions.reorderLayers(node.id,node.parentId,moveUp,onSuccess)
                                    else if(layersFirst && sortMode==NodeSortMode.MANUAL) actions.reorderContent(node.id,node.parentId,moveUp,onSuccess)
                                    else if (sortMode == NodeSortMode.MANUAL) actions.reorder(node.id, node.parentId, moveUp, onSuccess)
                                },
                                onDelete = {
                                    deletingNodeId = node.id
                                    deletingNodeName = node.title
                                },
                                onToggleComplete = {
                                    if (node.workState == null && node.isCompletable) actions.cycleTaskStatus(node.id)
                                },
                                paymentListState=nodeListState,
                                dragging = !filtered && drag.isDragging(node.id),
                                modifier = cardModifier,
                                footer=if(nameSearch.active && node.parentId!=currentNodeId) ({
                                    Text(originalNodePath(node,projectState,mapOf(project.id to project.name)),Modifier.testTag("search-location:${node.id}"),style=MaterialTheme.typography.labelSmall,color=Arachn0deColors.TextSecondary,maxLines=2,overflow=TextOverflow.Ellipsis)
                                }) else if(contextualCalendar.filtering) ({
                                    if(contextualCalendar.filtering)Text(contextualIndex?.location(node,mapOf(project.id to project)).orEmpty(),Modifier.testTag("context-calendar-location:${node.id}"),style=MaterialTheme.typography.labelSmall,color=Arachn0deColors.TextSecondary,maxLines=2,overflow=TextOverflow.Ellipsis)
                                }) else null,
                            )
                        }
                        if(taskViewEnabled && !compactHeader && !nameSearch.active) item(key="task-view-current-count") {Text("${taskProjection.matchingIds.size} tareas",Modifier.testTag("task-view-current-count"),style=MaterialTheme.typography.labelSmall,color=Arachn0deColors.TextSecondary)}
                        if(nameSearch.active) {
                            if(nameRows==null) item {Text("Buscando…")}
                            else if(nameRows!!.isEmpty()) item {Text("Sin coincidencias",Modifier.testTag("inline-search-empty"))}
                            items(nameRows.orEmpty(),key={"node:${it.node.id}"}) {row->renderCard(row.node,emptyList(),true,Modifier)}
                        } else if(taskViewEnabled && !taskViewUsable) item {Text("Cargando tareas…")}
                        else if(taskViewEnabled && filterActive) {
                            if(!taskViewUsable) item {Text("Cargando tareas…")}
                            else {
                                if(taskProjection.matchingIds.isEmpty() && (!compactHeader || selectedTaskStatus!=null)) item(key="task-view-empty") {Column {
                                    if(!compactHeader) Text("No hay tareas${if(taskViewSession.status==null) "" else " "+taskViewLabel(taskViewSession.status).lowercase(java.util.Locale.getDefault())}${if(otherFiltersActive) " con los filtros actuales" else ""}",Modifier.testTag("task-view-empty"))
                                    if(selectedTaskStatus!=null)TextButton(onClick={taskViewSession.status=null}) {Text("Ver todas")}
                                }}
                                val viewRows=NodePresentationSort.filtered(taskProjection.rows,sortMode,emptyMap(),layersFirst) {parent->sortChoices[parent] ?: (sortMode to layersFirst)}
                                items(viewRows,key={"node:${it.node.id}"}) {row->
                                    val modifier=Modifier.padding(start=12.dp*minOf(row.depth,6)).animateItem(fadeInSpec=androidx.compose.animation.core.tween(120),fadeOutSpec=androidx.compose.animation.core.tween(120))
                                    renderCard(row.node,emptyList(),true,modifier)
                                }
                            }
                        } else if(contextualCalendar.filtering) {
                            if(contextualIndex==null || !hasLoaded) item {Text("Cargando tareas…")}
                            else if(calendarResults.isEmpty() && !compactHeader) item {Text("No hay coincidencias con los filtros activos.",Modifier.testTag("context-calendar-empty"))}
                            items(calendarResults,key={"calendar:${it.id}"}) {node->renderCard(node,emptyList(),true,Modifier)}
                        } else if (filterActive && !sprintScope) {
                            if (filteredRows == null) item { Text("Cargando resultados…") }
                            else if (!compactHeader && filteredRows!!.none { it.isMatch }) item { Text("Sin coincidencias") }
                            items(filteredRows.orEmpty(), key = { "filtered:${it.node.id}" }) { row ->
                                if (selectedTaskStatus!=null && row.isMatch) {
                                    renderCard(row.node,emptyList(),true,Modifier.padding(start=minOf(row.depth,6).times(12).dp))
                                } else if (scopeFilters.person != null && row.isMatch && row.node.obligation != null) {
                                    GeneralCard(
                                        bashEntry = true,
                                        modifier = Modifier.fillMaxWidth().padding(start = minOf(row.depth, 6).times(12).dp)
                                            .testTag("person-obligation:${row.node.id}")
                                            .semantics { selected = row.node.id in selection.ids; stateDescription = if (row.node.id in selection.ids) "Seleccionado" else "Sin seleccionar" },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Arachn0deColors.Surface),
                                    ) {
                                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                            TextButton(modifier = Modifier.fillMaxWidth(), onClick = { if(selection.ids.isNotEmpty()) selection.toggle(row.node.id) else actions.navigate(row.node.id) { path -> currentPath.clear(); currentPath.addAll(path) } }) { Text(if(LocalBashPresentation.current) bashFileName(row.node.title,row.node.purpose) else row.node.title, modifier = Modifier.fillMaxWidth(), color = Arachn0deColors.TextPrimary, fontSize = ContentTypography.ProjectTitle, fontWeight = FontWeight.SemiBold, maxLines = if(LocalBashPresentation.current) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis) }
                                            CompactCardIndicators("node:${row.node.id}",showContainedTasks=row.node.isStructural)
                                            TagChips(tagState.forNode(row.node.id))
                                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                SecondaryAction(if(row.node.id in selection.ids) "✓ Seleccionado" else "Seleccionar", { selection.toggle(row.node.id) }, enabled = !isSubmittingNode)
                                                TextButton(onClick = { historyNodeId = row.node.id }) { Text("Historial") }
                                                TextButton(onClick = { if (assignmentsLoaded && peopleLoaded) drafts.open(row.node.parentId, row.node.id) { EditorDraft(row.node.id, row.node.parentId, row.node.title, row.node.description, startAt = row.node.startAt, dueAt = row.node.dueAt, purpose = row.node.purpose, obligation = row.node.obligation, priority = row.node.priority).apply { tagIds = tagState.nodeIds[row.node.id].orEmpty().toList(); responsibleIds = responsibleByNode[row.node.id].orEmpty().map { it.id }; creationGroupId = row.node.creationGroupId; captureSharedBaseline() } } }) { Text("Editar") }
                                            }
                                        }
                                    }
                                } else {
                                    Column(Modifier.padding(start = minOf(row.depth, 6).times(12).dp).semantics { selected = row.node.id in selection.ids; stateDescription = if(row.node.id in selection.ids) "Seleccionado" else "Sin seleccionar" }) {
                                        SecondaryAction(if(row.node.id in selection.ids) "✓ Seleccionado" else "Seleccionar", { selection.toggle(row.node.id) }, enabled = !isSubmittingNode)
                                        if (!row.isMatch) Text("Contexto · ${row.node.title}", color = Arachn0deColors.TextSecondary)
                                        else {
                                            TextButton(onClick = { if(selection.ids.isNotEmpty()) selection.toggle(row.node.id) else actions.navigate(row.node.id) { path -> currentPath.clear(); currentPath.addAll(path) } }) { Text(row.node.title,maxLines=2,overflow=TextOverflow.Ellipsis) }
                                            CompactCardIndicators("node:${row.node.id}",showContainedTasks=row.node.isStructural)
                                            TagChips(tagState.forNode(row.node.id))
                                            TextButton(onClick = { historyNodeId = row.node.id }) { Text("Historial") }
                                            TextButton(onClick = { if (assignmentsLoaded && peopleLoaded) drafts.open(row.node.parentId, row.node.id) { EditorDraft(row.node.id, row.node.parentId, row.node.title, row.node.description, startAt = row.node.startAt, dueAt = row.node.dueAt, purpose = row.node.purpose, obligation = row.node.obligation, priority = row.node.priority).apply { tagIds = tagState.nodeIds[row.node.id].orEmpty().toList(); responsibleIds = responsibleByNode[row.node.id].orEmpty().map { it.id }; creationGroupId = row.node.creationGroupId; captureSharedBaseline() } } }) { Text("Editar") }
                                        }
                                    }
                                }
                            }
                        } else {
                        if(creationRole?.role=="EPIC" && !filterActive) {
                            val related=storiesByEpic[currentNodeId].orEmpty().mapNotNull {projectState.nodesById[it]}.filter {it.parentId!=currentNodeId}.sortedWith(compareBy<Node> {it.position}.thenBy {it.id})
                            if(related.isNotEmpty()) item {Text("Historias vinculadas desde otras épicas",style=MaterialTheme.typography.titleMedium)}
                            items(related,key={"related-story:${it.id}"}) {story->ManagementNavigationCard(ManagementToolVisual(story.title,"Vínculo adicional · "+story.parentId?.let {projectState.nodesById[it]?.title}.orEmpty(),Icons.Default.Article)) {openReference(story.id)}}
                        }
                        if (currentNodes == null) item { Text("Cargando orden…") }
                        else if (!compactHeader && !teamScope && !managedSprint && (currentNode == null || currentNode.isStructural)) {
                            val message = layerContentMessage(currentNodes.orEmpty().isNotEmpty(), null)
                            if (message != null) item(key = "layer-status", contentType = "empty") { LayerContentStatus(message) }
                        }
                        if (sprintScope && filterActive) {
                            if (filteredRows == null) item { Text("Cargando resultados…") }
                            else if (filteredRows!!.none { it.isMatch }) item { Text("Sin coincidencias") }
                        }
                        val sectionKeys = if(managedSprint) emptyList() else if (sprintScope) listOf("structure") + com.r0ybt.arachn0de.domain.model.WorkState.entries.map { it.name } else listOf("pending", "completed")
                        for (sectionKey in sectionKeys) {
                            val completedGroup = sectionKey == "completed"
                            val groupNodes = if (sprintScope) sprintSectionNodes(
                                currentNodes.orEmpty() + projectState.nodes.filter {it.id in linkedIds}, sectionKey
                            ).filter { !filterActive || it.id in filteredIds }
                            else currentNodes.orEmpty().distinctBy {it.id}.filter {it.isCompleted == completedGroup}
                            val expanded = sectionKey !in collapsedSprintSections
                            val renderNodes = if (sprintScope) { if (expanded) groupNodes else emptyList() } else (if(layersFirst) {
                                val pinnedLayers=groupNodes.filter {it.isStructural && it.isPinned}.map {it.id}
                                val layers=groupNodes.filter {it.isStructural && !it.isPinned}.map {it.id}
                                val content=groupNodes.filterNot {it.isStructural}.map {it.id}
                                drag.orderFor("pinned-layers",pinnedLayers)+drag.orderFor("layers",layers)+drag.orderFor(if(completedGroup) "completed" else "pending",content)
                            } else drag.orderFor("pinned:$completedGroup",groupNodes.filter {it.isStructural && it.isPinned}.map {it.id})+drag.orderFor(completedGroup, groupNodes.filterNot {it.isPinned}.map { it.id }))
                                .mapNotNull { id -> projectState.nodesById[id] }
                            if (sprintScope && (sectionKey != "structure" || groupNodes.isNotEmpty())) {
                                item(key = "sprint-section:$sectionKey", contentType = "section") {
                                    val label = if (sectionKey == "structure") "Capas y notas" else com.r0ybt.arachn0de.domain.model.WorkState.valueOf(sectionKey).label
                                    SprintSectionHeader(label, groupNodes.size, expanded) {
                                        collapsedSprintSections = if (expanded) collapsedSprintSections + sectionKey else collapsedSprintSections - sectionKey
                                    }
                                }
                            } else if (groupNodes.isNotEmpty() && (!compactHeader || completedGroup)) {
                                item(key = "section:$completedGroup", contentType = "section") {
                                    Column {
                                        if (completedGroup) androidx.compose.material3.HorizontalDivider(color = Arachn0deColors.Outline)
                                        Text(if (completedGroup) "Completadas" else "Disponibles", color = Arachn0deColors.TextSecondary,
                                            fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                                    }
                                }
                            }
                            items(renderNodes, key = { "node:${it.id}" }, contentType = { "node" }) { node ->
                                val dragging = drag.isDragging(node.id)
                                if(sprintScope && node.id in linkedIds) Row {
                                    Text("Vinculada · Origen: ${sprintOrigin(node,projectState)}",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)
                                    TextButton(onClick={node.workState?.let {unlinkTask=node.id to it}}) {Text("Desvincular")}
                                }
                                renderCard(node,renderNodes,false,Modifier.animateItem(
                                    fadeInSpec = null, fadeOutSpec = null,
                                    placementSpec = if (dragging) null else androidx.compose.animation.core.spring(),
                                ).then(drag.cardModifier(node.id)))
                            }
                        }
                        if (sprintScope && filterActive) {
                            val nestedMatches = filteredRows.orEmpty().filter { it.depth > 0 }
                            if (nestedMatches.isNotEmpty()) item { Text("Coincidencias en subcapas", color = Arachn0deColors.TextSecondary) }
                            items(nestedMatches, key = { "sprint-filtered:${it.node.id}" }) { row ->
                                Column(Modifier.padding(start = minOf(row.depth, 6).times(12).dp)) {
                                    if (!row.isMatch) Text("Contexto · ${row.node.title}", color = Arachn0deColors.TextSecondary)
                                    TextButton(onClick = { if (selection.ids.isNotEmpty()) selection.toggle(row.node.id) else actions.navigate(row.node.id) { path -> currentPath.clear();currentPath.addAll(path) } }) { Text(row.node.title,maxLines=2,overflow=TextOverflow.Ellipsis) }
                                    row.node.workState?.let { Text(it.label, color = Arachn0deColors.TextSecondary) }
                                    SecondaryAction(if (row.node.id in selection.ids) "✓ Seleccionado" else "Seleccionar", { selection.toggle(row.node.id) }, enabled = !isSubmittingNode)
                                }
                            }
                        }
                        }
                    }
                }
            }
            // Keep inline editors above the main content, including elevated card surfaces.
            ContextualBottomPanel {
            Column(Modifier.fillMaxWidth()
                .then(if(showDrawer) Modifier.clearAndSetSemantics {} else Modifier)) {
                androidx.compose.material3.SnackbarHost(actions.snackbar, Modifier.fillMaxWidth().padding(horizontal=12.dp).testTag("task-view-snackbar")) { data ->
                    androidx.compose.material3.Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(8.dp),
                        containerColor = Arachn0deColors.Surface,
                        contentColor = Arachn0deColors.TextPrimary,
                        actionColor = Arachn0deColors.Accent,
                        dismissActionContentColor = Arachn0deColors.TextSecondary,
                    )
                }
                RecurrenceManager(nodeRepository.recurrence, project.id, projectState.nodes, people, peopleLoaded, recurrenceSelected, showLauncher = false) { recurrenceSelected = null }
                // The shared footer owns the measured space; AppSafeArea owns system insets.
                val taskPanel = if(quickCreation) draft?.purpose == NodePurpose.ACTION
                    else useNodeModule || (currentNodeId==null && projectEditField==null)
                TaskBottomPanel(enabled=taskPanel,
                    panelKey=if(quickCreation) draft?.creationId else currentNodeId,
                    title=if(quickCreation) "Nueva tarea" else currentNode?.title ?: "Tareas",
                    editRequest=nodeEditRequest,ownsMainActions=!quickCreation && (useNodeModule || currentNodeId==null)) {
                if(quickCreation && draft?.id == null && draft != null) QuickCreateBar(draft, isSubmittingNode,
                    onSave = { actions.saveDraft(project.id, draft, draft.purpose == NodePurpose.ACTION) { drafts.clear(); quickCreation = false } },
                    onCancel = { actions.discardDraft(draft) { drafts.clear(); quickCreation = false } },
                    onFullForm = { quickCreation = false },
                    onAudio = {
                        val audio=(copyContext.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication)?.security?.current?.value?.audio
                        if(audio?.canStartRecording==true) {
                            val generated=draft.title.isBlank()
                            if(generated) draft.title="Audio — "+java.text.SimpleDateFormat("d MMM, HH:mm",java.util.Locale.getDefault()).format(java.util.Date())
                            draft.audioCreationRequested=true;draft.audioProvisionalTitle=draft.title.trim().takeIf {generated}
                            actions.saveDraft(project.id,draft,true) {
                                val owner=draft.creationId
                                drafts.clear();quickCreation=false
                                audio.startRecording(owner)
                            }
                        } else if(audio!=null) audio.state.value=audio.state.value.copy(error="Finaliza el audio actual o espera a que termine su recuperación.")
                    })
                else
                if(currentNodeId==null) CoreContentModule(project,
                    androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication,
                    people,peopleLoaded,tagState,nodeTechnologies,coreModuleMaxHeight,coreContext,{coreContext=it},
                    contextActions,creationActions,taskViewControls,projectEditField,{projectEditField=null})
                else if(useNodeModule && currentNode!=null) androidx.compose.runtime.key(currentNode.id) {
                    NodeBottomModule(currentNode,nodeRepository,actions,people,responsibleByNode[currentNode.id].orEmpty(),
                        peopleLoaded && assignmentsLoaded,tagState,nodeTechnologies,coreModuleMaxHeight,contextActions,creationActions,
                        nodeEditRequest,{nodeEditRequest=null},viewControls=taskViewControls,
                        contentRequest=if(contentOwner==currentNode.id) contentRequest else 0,
                        onRecurrence=recurrenceByNode[currentNode.id]?.let {ruleId->({recurrenceSelected=ruleId})})
                } else creationActions()
                }

            }

            }

            if (showDrawer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize().background(Arachn0deColors.Scrim.copy(alpha = 0.45f)).clickable { showDrawer = false }.clearAndSetSemantics {})
                    AppIdentityDrawer(onDismiss = { showDrawer = false }, onProjects = onOpenProjects, projectsSelected = true, onPeople = { showDrawer = false; onOpenPeople() }, onTechnologies = { showDrawer = false; onOpenTechnologies() },onTags={showDrawer=false;onOpenTags()}, onAttention = { showDrawer = false; onOpenAttention() }, onCalendar = { showDrawer = false; onOpenCalendar() }, onObligations = { showDrawer = false; onOpenObligations() }, onAppearance = { showDrawer = false; onOpenAppearance() }, onMetro = { showDrawer = false; onOpenMetro() }, onAbout = { showDrawer = false; onOpenAbout() })
                }
            }
        }
    }

    convertingObligationId?.let { id ->
        RemoveObligationDialog(isSubmittingNode, true, resultLabel=if(convertingPurpose==NodePurpose.LAYER) "capa" else "nota",
            onDismiss = { convertingObligationId = null },
            onConfirm = { actions.convert(id, convertingPurpose, removeObligation = true) { convertingObligationId = null } })
    }

    draft?.takeUnless { quickCreation && it.id == null }?.let { editor ->
        NodeDialog(
            draft = editor,
            initialField = directEditField.takeIf { editor.id != null },
            hasUnsavedChanges = projectState.nodesById[editor.id]?.let { original ->
                editor.title != original.title || editor.description != original.description ||
                    editor.activeStart != original.startAt || editor.activeDue != original.dueAt ||
                    editor.priority != original.priority || editor.purpose != original.purpose ||
                    editor.tagIds.toSet() != tagState.nodeIds[original.id].orEmpty() ||
                    editor.responsibleIds.toSet() != responsibleByNode[original.id].orEmpty().map { it.id }.toSet() ||
                    runCatching { editor.obligation() }.getOrElse { return@let true } != original.obligation ||
                    editor.removedAttachmentIds.isNotEmpty()
            } ?: editor.hasWork,
            tagRepository = nodeRepository.tags,
            people = people, peopleLoaded = peopleLoaded,
            isSubmitting = isSubmittingNode,
            editDates = editor.purpose == NodePurpose.ACTION && (editor.id == null || projectState.nodesById[editor.id]?.isCompletable == true),
            onDismiss = {
                if (!isSubmittingNode) {
                    drafts.close()
                }
            },
            onDiscard = { actions.discardDraft(editor) { drafts.clear() } },
            onSave = { _, _ ->
                val dates = editor.purpose == NodePurpose.ACTION && (editor.id == null || projectState.nodesById[editor.id]?.isCompletable == true)
                if(editor.creationGroupId != null && !editor.sharedPatch().isEmpty) actions.reviewGroup(editor) { members ->
                    if(members.size > 1) groupReview = members else actions.saveDraft(project.id,editor,dates) { drafts.clear() }
                } else actions.saveDraft(project.id, editor, dates) { drafts.clear() }
            },
        )
    }

    if(showDefaults) BottomModulesDialog(onDismissRequest = {}, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, dismissOnBackPress = false)) {
        AppSafeArea { CreationDefaultsScreen(nodeRepository.creationDefaults,
            currentNodeId?.let { com.r0ybt.arachn0de.domain.defaults.DefaultsScope.Layer(project.id,it) } ?: com.r0ybt.arachn0de.domain.defaults.DefaultsScope.Project(project.id),
            currentNode?.title ?: project.name, tagState.tags, people) { showDefaults = false } }
    }

    if (showSprintMode && currentNode?.isStructural == true) SprintModeDialog(currentNode.sprintMode, isSubmittingNode,
        { showSprintMode = false }, { actions.sprintMode(currentNode.id, !currentNode.sprintMode) { showSprintMode = false } })

    changingWorkStateId?.let { id -> projectState.nodesById[id]?.let { node -> node.workState?.let { state ->
        WorkStateDialog(node.title, state, isSubmittingNode, { changingWorkStateId = null }, { chosen -> actions.workState(id, chosen) { changingWorkStateId = null } })
    } } }
    if(showContextActions && currentNodeId!=null) ContextActionMenu(currentNode?.title ?: project.name,{ showContextActions = false },bottomAligned=currentNode?.purpose==NodePurpose.ACTION && currentNode.isStructural==false,showCancel=false,groupConversions=true,cardEditingIcons=true,content=contextActions)


    groupReview?.let { members -> draft?.let { editor ->
        val dates = editor.purpose == NodePurpose.ACTION && projectState.nodesById[editor.id]?.isCompletable == true
        AlertDialog(onDismissRequest = { if(!isSubmittingNode) groupReview = null }, title = { Text("Aplicar cambios compartidos") },
            text = { Text("El grupo tiene ${members.size} elementos existentes, incluidos los movidos a otras capas. Solo se propagarán los campos compartidos modificados; títulos y fechas de los demás se conservarán.") },
            confirmButton = { Button(enabled = !isSubmittingNode, onClick = { actions.saveGroup(editor,members.map { it.id }.toSet(),dates) { groupReview = null; drafts.clear() } }) { Text("Todos los elementos del grupo") } },
            dismissButton = { Column {
                TextButton(enabled = !isSubmittingNode, onClick = { actions.saveDraft(project.id,editor,dates) { groupReview = null; drafts.clear() } }) { Text("Solo este elemento") }
                TextButton(enabled = !isSubmittingNode, onClick = { groupReview = null }) { Text("Volver al editor") }
            } })
    } }
    if(bulkDelete && selection.ids.isNotEmpty()) AlertDialog(onDismissRequest = { if(!isSubmittingNode) bulkDelete = false },
        title = { Text("¿Eliminar ${selection.ids.size} elementos?") }, text = { Text("Las capas seleccionadas también eliminarán todos sus descendientes, relaciones e historial. Esta operación no tiene Deshacer.") },
        confirmButton = { DestructiveAction("Eliminar seleccionados", { actions.deleteSelected(project.id,selection.ids) { bulkDelete = false; selection.clear() } }, enabled = !isSubmittingNode) },
        dismissButton = { TextButton(enabled = !isSubmittingNode, onClick = { bulkDelete = false }) { Text("Cancelar") } })
    if(bulkMove && selection.ids.isNotEmpty()) {
        NodeDestinationDialog(selection.ids,quickCaptureIndex(),nodeRepository,
            initialKey=currentNodeId?.let {"node:$it"} ?: "project:${project.id}",
            onDismiss={bulkMove=false},onMoved={bulkMove=false;selection.clear()})
    }

    responsibleNodeId?.let { id ->
        if (peopleLoaded && assignmentsLoaded) ResponsibleDialog(
            nodeId = id, people = people, assigned = responsibleByNode[id].orEmpty(),
            busy = personActions.operation.busy,
            onDismiss = { responsibleNodeId = null },
            onSave = { ids -> personActions.assign(id, ids) { responsibleNodeId = null } },
        )
    }
    OperationErrorDialog(personActions.operation)
    LoadErrorDialog(peopleLoad)
    LoadErrorDialog(assignmentsLoad)
    LoadErrorDialog(recurrenceLoad)

    movingNodeId?.let { sourceId ->
        NodeDestinationDialog(setOf(sourceId),quickCaptureIndex(),nodeRepository,
            initialKey=projectState.nodesById[sourceId]?.parentId?.let {"node:$it"} ?: "project:${project.id}",
            onDismiss={movingNodeId=null},onMoved={target->
                movingNodeId=null
                if(currentNodeId==sourceId && target.projectId!=project.id) onConverted(target.projectId!!,sourceId)
            })
    }

    deletingNodeId?.let { deletingId ->
        AlertDialog(
            containerColor = Arachn0deColors.Surface,
            titleContentColor = Arachn0deColors.TextPrimary,
            textContentColor = Arachn0deColors.TextSecondary,
            onDismissRequest = { if (!actions.operation.busy) deletingNodeId = null },
            title = { Text("Eliminar nodo") },
            text = { Text("¿Seguro que quieres eliminar “${deletingNodeName}”? Esta acción también eliminará cualquier hijo.") },
            confirmButton = {
                DestructiveAction(
                    label = "Eliminar",
                    enabled = !actions.operation.busy,
                    onClick = {
                        actions.delete(deletingId) {
                            deletingNodeId = null
                            if (deletingId == currentNodeId) {
                                if (currentPath.isNotEmpty()) {
                                    currentPath.removeAt(currentPath.lastIndex)
                                } else {
                                    onBackToProjects()
                                }
                            }
                        }
                    },
                )
            },
            dismissButton = {
                TextButton(enabled = !actions.operation.busy, onClick = { deletingNodeId = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
    OperationErrorDialog(actions.operation)
    LoadErrorDialog(load)
}


/** Keeps the editor composed while folded so drafts, scroll and field buffers survive. */
@Composable
internal fun TaskBottomPanel(enabled:Boolean,panelKey:String?,title:String,editRequest:com.r0ybt.arachn0de.domain.model.NodeProperty?,
    accessibilityLabel:String="tareas",ownsMainActions:Boolean=false,content:@Composable ()->Unit) {
    var minimized by rememberSaveable(panelKey) { mutableStateOf(false) }
    val canFold=enabled && !ownsMainActions
    val folded=canFold && minimized
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val palette=LocalAppearancePalette.current
    val charcoal=palette.taskPanel
    val edge=if(LocalBashPresentation.current) BashColors.Separator else androidx.compose.ui.graphics.lerp(charcoal,palette.text,.22f)
    val threshold=with(androidx.compose.ui.platform.LocalDensity.current) {24.dp.toPx()}
    fun minimize() {focus.clearFocus();keyboard?.hide();minimized=true}
    LaunchedEffect(editRequest) {if(editRequest!=null) minimized=false}
    Column(Modifier.fillMaxWidth()
        .then(if(enabled) Modifier.appearancePaint("taskPanel",charcoal).drawWithContent {
            drawContent()
            drawRect(edge,size=androidx.compose.ui.geometry.Size(size.width,1.dp.toPx()))
        } else Modifier)) {
        if(canFold) {
            val gesture=Modifier.pointerInput(folded,threshold) {
                var distance=0f
                detectVerticalDragGestures(onDragStart={distance=0f},onDragCancel={distance=0f},
                    onDragEnd={
                        if(!folded && distance>threshold) minimize()
                        else if(folded && distance < -threshold) minimized=false
                    },onVerticalDrag={change,amount->change.consume();distance+=amount})
            }
            if(folded) {
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).then(gesture)
                    .clickable(onClickLabel="Expandir panel de $accessibilityLabel") {minimized=false}
                    .padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(title,Modifier.weight(1f),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color=palette.text,style=MaterialTheme.typography.labelLarge)
                    Icon(Icons.Default.KeyboardArrowUp,"Expandir panel",tint=palette.muted)
                }
            } else {
                Box(Modifier.fillMaxWidth().height(24.dp).then(gesture)
                    .clickable(onClickLabel="Minimizar panel de $accessibilityLabel") {minimize()}
                    .padding(bottom=2.dp),contentAlignment=Alignment.BottomCenter) {
                    Box(Modifier.size(36.dp,4.dp).background(palette.muted.copy(alpha=.65f),RoundedCornerShape(2.dp)))
                }
            }
        }
        // Measure normally, but do not place hidden editors or expose their accessibility nodes.
        Box(Modifier.fillMaxWidth()
            .then(if(folded) Modifier.clearAndSetSemantics {}.layout {measurable,constraints->
                measurable.measure(constraints)
                layout(constraints.minWidth,0) {}
            } else Modifier)) {
            CompositionLocalProvider(LocalAppearancePalette provides
                if(enabled) palette.copy(surface=charcoal,panel=charcoal) else palette) {content()}
        }
    }
}
