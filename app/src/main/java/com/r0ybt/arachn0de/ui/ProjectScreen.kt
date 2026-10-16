package com.r0ybt.arachn0de.ui

import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.BoxWithConstraints

import androidx.compose.material.icons.filled.PushPin

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight

import androidx.compose.material3.MaterialTheme
import com.r0ybt.arachn0de.domain.model.Node

import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot
import com.r0ybt.arachn0de.domain.model.Project
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
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
    onOpenGame: () -> Unit = {},
    onOpenMetro: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onBackToObligations: (() -> Unit)? = null,
    onBackToCalendar: (() -> Unit)? = null,
    onBackToSearch: (() -> Unit)? = null,
    openNodeId: String? = null,
    onOpenNodeHandled: () -> Unit = {},
    clock: () -> Long = System::currentTimeMillis,
    onOpenProjects: () -> Unit = onBackToProjects,
    sortPreferences: NodeSortPreferences = rememberSaveable(project.id, saver = NodeSortPreferences.Saver) { NodeSortPreferences() },
    projectRepository: com.r0ybt.arachn0de.data.repository.ProjectRepository = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).projectRepository,
    onOpenTechnologies: () -> Unit = {},
    onOpenTags:()->Unit={},
    onConverted: (String, String?) -> Unit = { _, _ -> onBackToProjects() },
    conversionScope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope(),
) {
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
    var responsibleByNode by remember(project.id) { mutableStateOf(emptyMap<String, List<com.r0ybt.arachn0de.domain.model.Person>>()) }
    var peopleLoaded by remember { mutableStateOf(false) }
    var assignmentsLoaded by remember(project.id) { mutableStateOf(false) }
    var responsibleNodeId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    val peopleLoad = remember(personRepository) { LoadState() }
    val assignmentsLoad = remember(personRepository, project.id) { LoadState() }
    LaunchedEffect(personRepository, peopleLoad.attempt) {
        peopleLoad.collect(personRepository.observePeople()) { people = it; peopleLoaded = true }
    }
    LaunchedEffect(personRepository, project.id, assignmentsLoad.attempt) {
        assignmentsLoad.collect(personRepository.observeAssignments(project.id)) { responsibleByNode = it; assignmentsLoaded = true }
    }
    val layerScrollStates = rememberSaveableStateHolder()
    val navigatorListState = rememberLazyListState()
    val currentPath = rememberSaveable(project.id, saver = listSaver<SnapshotStateList<String>, String>(
        save = { it.toList() },
        restore = { it.toMutableStateList() },
    )) { mutableStateListOf<String>() }
    var hasLoaded by remember(project.id) { mutableStateOf(false) }
    var projectState by remember(project.id) { mutableStateOf(NodeTreeSnapshot(emptyList())) }
    val drafts = rememberSaveable(project.id, saver = com.r0ybt.arachn0de.ui.state.EditorDraftStore.Saver) { com.r0ybt.arachn0de.ui.state.EditorDraftStore() }
    val draft = drafts.active
    var quickCreation by rememberSaveable(project.id) { mutableStateOf(false) }
    var convertingPurpose by remember { mutableStateOf(NodePurpose.NOTE) }
    var convertingObligationId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var deletingNodeId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var deletingNodeName by rememberSaveable(project.id) { mutableStateOf("") }
    var movingNodeId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var showDrawer by remember { mutableStateOf(false) }
    var showNavigator by rememberSaveable(project.id) { mutableStateOf(false) }
    var expandedLayerIds by rememberSaveable(
        project.id,
        stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it.toList() }),
    ) { mutableStateOf(emptyList<String>()) }
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
    var coreContext by remember(project.id,currentNodeId) {mutableStateOf(CoreBottomContext.Creation)}
    var planningOpen by remember(currentNodeId) {mutableStateOf(false)}
    val currentNode = projectState.nodesById[currentNodeId]
    var projectEditField by rememberSaveable(project.id) {mutableStateOf<String?>(null)}
    var projectFieldOriginal by rememberSaveable(project.id) {mutableStateOf("")}
    var projectFieldValue by rememberSaveable(project.id) {mutableStateOf("")}
    val projectEditingActions=remember(projectRepository,scope,attachmentRepository) {com.r0ybt.arachn0de.ui.state.ProjectActions(projectRepository,scope,attachmentRepository)}
    OperationErrorDialog(projectEditingActions.operation)
    val finishProjectEdit:()->Unit={
        projectEditField=null;projectFieldOriginal="";projectFieldValue=""
        coreContext=CoreBottomContext.Content
    }
    val editProject:(String)->Unit={field->
        projectFieldOriginal=if(field=="title") project.name else project.description
        projectFieldValue=projectFieldOriginal
        projectEditField=field
    }
    LaunchedEffect(projectEditField) {
        if(projectEditField!=null) coreContext=CoreBottomContext.Content
    }
    val cleanCompleted=if(currentNode==null || currentNode.isStructural) completedCleanupLauncher(project.id,currentNode?.id) else null
    val scopeDescription = currentNode?.description ?: project.description
    val sprintScope = currentNode?.sprintMode == true

    val sprintLinks by remember(nodeRepository) {nodeRepository.sprintLinks.links}.collectAsState(initial=emptyList())
    val managementContexts by remember(nodeRepository) {nodeRepository.management.contexts}.collectAsState(initial=emptyList())
    val cascadeLayers by remember(nodeRepository) {nodeRepository.management.cascade.layers}.collectAsState(initial=emptyList())
    val cascadeRoles by remember(nodeRepository) {nodeRepository.management.backlog.observe(project.id)}.collectAsState(initial=com.r0ybt.arachn0de.data.repository.BacklogState())
    val managementRoots by remember(nodeRepository) {nodeRepository.management.roots}.collectAsState(initial=emptyList())
    val managementRoot=managementRoots.firstOrNull {if(currentNodeId==null) it.projectId==project.id else it.nodeId==currentNodeId}
    val managementContext=managementContexts.firstOrNull {it.id==managementRoot?.id}
    var managementCreationRequest by remember(currentNodeId) {mutableStateOf(0)}
    val creationMapping=cascadeLayers.firstOrNull {it.nodeId==currentNodeId}
    val creationRole=cascadeRoles.records.roles.firstOrNull {it.nodeId==currentNodeId}
    val iterationOverview by remember(nodeRepository.management.iterations,creationMapping?.contextId) {nodeRepository.management.iterations.observe(creationMapping?.contextId ?: "")}.collectAsState(initial=com.r0ybt.arachn0de.data.repository.IterationState())
    val sprintOverviewRefs by nodeRepository.management.relations.stories.collectAsState(initial=emptyList())
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
        val parent = currentNodeId
        if(drafts.hasNew(parent)) {
            drafts.open(parent) { error("Borrador existente") }
            quickCreation = quick && drafts.active?.let { it.purpose != NodePurpose.LAYER && !it.batchEnabled && it.description.isBlank() && it.technologyIds.isEmpty() && it.templateMetroPlan == null } == true
        }
        else {
            var seed: EditorDraft? = null
            defaultsOperation.submit("No se pudieron cargar los valores predeterminados. Puedes reintentar.", {
                val defaults = nodeRepository.creationDefaults.resolve(project.id,parent)
                seed = com.r0ybt.arachn0de.ui.state.CreationDefaultsDraftFactory.create(parent,defaults,clock(),java.util.TimeZone.getDefault())
                true
            }, {
                drafts.open(parent) { checkNotNull(seed) }
                quickCreation = quick && checkNotNull(seed).let { it.purpose != NodePurpose.LAYER }
            })
        }
    }
    var cascadeError by remember(project.id) {mutableStateOf<String?>(null)}
    LaunchedEffect(managementContexts,managementRoots,hasLoaded) {
        for(root in managementRoots.filter {it.projectId==project.id || projectState.nodesById[it.nodeId]?.projectId==project.id}) {
            if(managementContexts.any {it.id==root.id && it.enabled}) try {nodeRepository.management.cascade.ensure(root.id)}
            catch(c:kotlinx.coroutines.CancellationException) {throw c}
            catch(e:Exception) {cascadeError=e.message ?: "No se pudo preparar la gestión. Los datos se conservaron."}
        }
    }

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
    val scopeFilterStore = rememberSaveable(project.id, saver = ScopeFilterStore.Saver) { ScopeFilterStore() }
    val scopeFilters = scopeFilterStore.scope(currentNodeId ?: "project-root")
    val scopedFilter = com.r0ybt.arachn0de.domain.model.NodeFilter(
        com.r0ybt.arachn0de.domain.model.TemporalRanges.resolve(scopeFilters.time, now, java.util.TimeZone.getDefault(), java.util.Locale.getDefault()),
        scopeFilters.person, scopeFilters.completion, scopeFilters.tag, scopeFilters.priority)
    val completedAt by remember(nodeRepository, project.id) { nodeRepository.observeCompletionTimes(project.id) }.collectAsState(initial = emptyMap())
    val sortContext = "${project.id}:${currentNodeId ?: "project-root"}"
    val layersFirst=sortPreferences.layersFirst(sortContext)
    val sortMode = sortPreferences.mode(sortContext)
    var showSortMenu by remember(currentNodeId) { mutableStateOf(false) }
    val filterActive = scopeFilters.active
    val automaticNodes by androidx.compose.runtime.produceState<List<com.r0ybt.arachn0de.domain.model.Node>?>(null, projectState, currentNodeId, sortMode,layersFirst) {
        value = null
        if (sortMode == NodeSortMode.MANUAL) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            NodePresentationSort.children(projectState.nodes.filter { it.projectId == project.id && it.parentId == currentNodeId }, sortMode,layersFirst=layersFirst)
        }
    }
    // Manual keeps the existing coherent projection, without a new asynchronous loading phase.
    val currentNodes = if (sortMode == NodeSortMode.MANUAL) remember(projectState, currentNodeId, completedAt,layersFirst) {
        NodePresentationSort.children(projectState.childrenOf(currentNodeId), NodeSortMode.MANUAL, completedAt,layersFirst)
    } else automaticNodes
    val sortChoices=projectState.nodes.filter {it.isStructural}.associate {it.id as String? to (sortPreferences.mode("${project.id}:${it.id}") to sortPreferences.layersFirst("${project.id}:${it.id}"))} +
        (null to (sortPreferences.mode("${project.id}:project-root") to sortPreferences.layersFirst("${project.id}:project-root")))
    val filteredRows by androidx.compose.runtime.produceState<List<com.r0ybt.arachn0de.domain.model.FilteredNodeRow>?>(null, projectState, currentNodeId, scopedFilter, sortMode,layersFirst,sortChoices, tagState, responsibleByNode, completedAt, linkedIds) {
        value = null
        if (!filterActive) { value = emptyList(); return@produceState }
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val physicalRows = com.r0ybt.arachn0de.domain.model.ScopedNodeFilter.apply(projectState, project.id, currentNodeId, scopedFilter,
                responsibleByNode.mapValues { (_, people) -> people.map { it.id }.toSet() }, tagState.nodeIds)
            val externalRows = projectState.nodes.filter { it.id in linkedIds && scopedFilter.matches(it,
                responsibleByNode[it.id].orEmpty().map { person -> person.id }.toSet(), tagIds = tagState.nodeIds[it.id].orEmpty()) }
                .map { com.r0ybt.arachn0de.domain.model.FilteredNodeRow(it, 0, true) }
            val rows = (physicalRows + externalRows).distinctBy { it.node.id }
            NodePresentationSort.filtered(rows, sortMode, completedAt,layersFirst) {parent->sortChoices[parent] ?: (sortMode to layersFirst)}
        }
    }
    val filteredIds = remember(filteredRows) { filteredRows.orEmpty().mapTo(hashSetOf()) { it.node.id } }
    if (scopeFilters.open) ScopeFiltersDialog(scopeFilters, people, tagState.tags)
    if (showSortMenu) NodeSortMenu(sortMode, { sortPreferences.set(sortContext, it) }, { showSortMenu = false },
        projectDefault = currentNodeId == null,
        onInherit = if (currentNodeId == null) null else ({ sortPreferences.inherit(sortContext) }),
        layersFirst=layersFirst,onLayersFirst={sortPreferences.setLayersFirst(sortContext,it)})
    val selection = remember(project.id, currentNodeId, scopedFilter) { com.r0ybt.arachn0de.ui.state.NodeSelection() }
    val visibleSelectionIds = if(scopeFilters.active) filteredRows.orEmpty().map { it.node.id }.toSet() else currentNodes.orEmpty().map { it.id }.toSet()
    LaunchedEffect(visibleSelectionIds, filteredRows != null, currentNodes != null) { if((scopeFilters.active && filteredRows != null) || (!scopeFilters.active && currentNodes != null)) selection.retain(visibleSelectionIds) }
    var bulkDelete by remember(selection) { mutableStateOf(false) }
    var bulkMove by remember(selection) { mutableStateOf(false) }
    var groupReview by remember { mutableStateOf<List<com.r0ybt.arachn0de.domain.model.Node>?>(null) }

    LaunchedEffect(draft?.creationId) { groupReview = null }

    val currentProgress = projectState.progressById[currentNodeId]
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
            expandedLayerIds = expandedLayerIds.filter { it in snapshot.nodesById }
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

    BackHandler(enabled=currentNodeId==null && coreContext!=CoreBottomContext.Creation && draft==null && projectEditField==null) {coreContext=CoreBottomContext.Creation}
    BackHandler(enabled = selection.ids.isNotEmpty()) { selection.clear() }
    BackHandler(enabled = showDrawer) { showDrawer = false }

    if (!hasLoaded) {
        Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {}
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
    val contextActions:ContextActions.()->Unit={
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
        if(currentNode==null || managementRoot!=null) action("Planificación",androidx.compose.material.icons.Icons.Default.Settings,{showContextActions=false;planningOpen=true})
        if (currentNode == null) action("Fotografía", androidx.compose.material.icons.Icons.Default.Image, {
            showContextActions = false; showProjectPhoto = true
        }, enabled = !isSubmittingNode)
        action("Editar tecnologías", androidx.compose.material.icons.Icons.Default.Code, {showContextActions=false;if(useNodeModule) nodeEditRequest=com.r0ybt.arachn0de.domain.model.NodeProperty.TECHNOLOGIES else editingTechnologies=true})
        action("Recurrencias", androidx.compose.material.icons.Icons.Default.Refresh, { showContextActions=false; recurrenceSelected="" })
        if(currentNode?.isStructural == true) action(if(currentNode.sprintMode) "Desactivar Modo Sprint" else "Modo Sprint", androidx.compose.material.icons.Icons.Default.SwapHoriz, { showContextActions=false;showSprintMode=true }, enabled=!isSubmittingNode)
        if(currentNode?.workState != null) action("Cambiar estado", androidx.compose.material.icons.Icons.Default.SwapHoriz, { showContextActions=false;changingWorkStateId=currentNode.id }, enabled=!isSubmittingNode)
        if(currentNode != null) {
            // Inline Kanban does not expose the ordinary editable context title.
            if(creationMapping?.kind=="KANBAN" && managementContexts.any {it.id==creationMapping.contextId && it.enabled})
                action("Editar",androidx.compose.material.icons.Icons.Default.Edit,{
                    showContextActions=false;openNodeEditor(currentNode)
                },enabled=!isSubmittingNode && assignmentsLoaded && peopleLoaded)
            action("Eliminar", androidx.compose.material.icons.Icons.Default.Delete, { showContextActions=false;deletingNodeId=currentNode.id;deletingNodeName=currentNode.title }, enabled=!isSubmittingNode,destructive=true)
            action("Responsables", androidx.compose.material.icons.Icons.Default.People, { showContextActions=false;if(useNodeModule) nodeEditRequest=com.r0ybt.arachn0de.domain.model.NodeProperty.PEOPLE else responsibleNodeId=currentNode.id }, enabled=!isSubmittingNode && !personActions.operation.busy && peopleLoaded && assignmentsLoaded)
            action("Mover a…", androidx.compose.material.icons.Icons.Default.DriveFileMove, { showContextActions=false;movingNodeId=currentNode.id }, enabled=!isSubmittingNode)
            action("Historial", androidx.compose.material.icons.Icons.Default.History, { showContextActions=false;historyNodeId=currentNode.id })
        }
        if(currentNode == null || (currentNode.isStructural)) action("Valores predeterminados", androidx.compose.material.icons.Icons.Default.Settings, {
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
        action(if(currentNode == null) "Copiar" else "Copiar este elemento", androidx.compose.material.icons.Icons.Default.ContentCopy, {
            showContextActions = false
            if(currentNode == null) copyActions.copyProject(project,projectState,false) else copyActions.copy(projectState,currentNode.id,false)
        },enabled = !copyActions.busy)
        if(currentNode == null || currentNode.isStructural) action("Copiar texto con descendientes", androidx.compose.material.icons.Icons.Default.CopyAll, {
            showContextActions = false
            if(currentNode == null) copyActions.copyProject(project,projectState,true) else copyActions.copy(projectState,currentNode.id,true)
        },enabled = !copyActions.busy)
    }
    val creationActions:@Composable ()->Unit={
                Row(Modifier.fillMaxWidth().padding(top=6.dp).background(Arachn0deColors.Surface)
                    .testTag("project-bottom-actions"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={if(contextualCreationLabel!=null) managementCreationRequest++ else if(!isSubmittingNode && (currentNode==null || currentNode.canReceiveChildren)) openTraditionalElement(quick = true)},
                        modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(8.dp),
                        colors=ButtonDefaults.textButtonColors(contentColor=Arachn0deColors.TextPrimary),
                        enabled=!isSubmittingNode && (currentNode==null || currentNode.canReceiveChildren)) {
                        Icon(androidx.compose.material.icons.Icons.Default.Add,null,Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Crear",maxLines=1,style=MaterialTheme.typography.labelSmall)
                    }
                    VerticalDivider(Modifier.height(24.dp),thickness=.5.dp,color=Arachn0deColors.Outline)
                    TextButton(onClick={expandedLayerIds=(expandedLayerIds.toSet()+currentPath).toList();showNavigator=true},
                        modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(8.dp),
                        colors=ButtonDefaults.textButtonColors(contentColor=Arachn0deColors.TextPrimary)) {
                        Icon(androidx.compose.material.icons.Icons.Default.Layers,null,Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Capas",maxLines=1,style=MaterialTheme.typography.labelSmall)
                    }
                }
    }
    val bottomControlsDensity=androidx.compose.ui.platform.LocalDensity.current
    var bottomControlsHeight by remember { mutableStateOf(70.dp) }
    Surface(modifier = Modifier.fillMaxSize(), color = Arachn0deColors.Background) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .then(if(projectEditField!=null) Modifier.imePadding() else Modifier),
        ) {
            val coreModuleMaxHeight=(maxHeight*.5f).coerceAtLeast(120.dp).coerceAtMost(maxHeight)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if(showDrawer) Modifier.clearAndSetSemantics {} else Modifier)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                HeaderBar(onMenuClick = { showDrawer = true }, title = project.name, compactLock=true,
                    onBack=if(useNodeModule) ({nodeBackDispatcher?.onBackPressed();Unit}) else if(referenceReturns.lastOrNull()?.let {org.json.JSONObject(it).getString("target")==currentNodeId}==true) ({restoreReference();Unit}) else if(onBackToSearch!=null) onBackToSearch else if(currentPath.isNotEmpty()) ({
                        if(onBackToObligations!=null) onBackToObligations()
                        else if(onBackToCalendar!=null) onBackToCalendar()
                        else currentPath.removeAt(currentPath.lastIndex)
                    }) else null,
                    backDescription=if(onBackToSearch!=null) "Volver a búsqueda" else if(onBackToObligations!=null) "Volver a Obligaciones" else if(onBackToCalendar!=null) "Volver al Calendario" else "Volver a la capa anterior",
                    trailingAction={
                        IconButton(onClick={showSortMenu=true},modifier=Modifier.size(48.dp).semantics {stateDescription="Orden: ${sortMode.label}" + if(layersFirst) "; Capas primero: arrastre de Capas" else if(sortMode!=NodeSortMode.MANUAL) "; arrastre deshabilitado" else ""}) {LucideIcon(Icons.AutoMirrored.Filled.Sort,"Ordenar")}
                        IconButton(onClick={scopeFilters.open=true},modifier=Modifier.size(48.dp)) {LucideIcon(Icons.Default.FilterList,if(scopeFilters.active) "Filtros activos" else "Filtros",tint=if(scopeFilters.active) Arachn0deColors.Accent else Arachn0deColors.TextSecondary)}
                        if(currentNodeId!=null && !useNodeModule) IconButton(onClick={showContextActions=true},modifier=Modifier.size(48.dp)) {LucideIcon(Icons.Default.MoreVert,"Opciones del elemento")}
                    })
                if(selection.ids.isNotEmpty()) Column(Modifier.fillMaxWidth()) {
                    Text("${selection.ids.size} seleccionados", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryAction("Mover", { bulkMove = true }, enabled = !isSubmittingNode)
                        DestructiveAction("Eliminar", { bulkDelete = true }, enabled = !isSubmittingNode)
                        TextButton(onClick = { selection.clear() }) { Text("Salir") }
                    }
                }

                layerScrollStates.SaveableStateProvider(currentNodeId?.let { "node:$it" } ?: "project-root") {
                    val nodeListState = rememberLazyListState()
                    val groups: Map<Any,List<String>> = if(layersFirst) mapOf(
                        "pinned-layers" to currentNodes.orEmpty().filter {it.isStructural && it.isPinned}.map {it.id},
                        "layers" to currentNodes.orEmpty().filter {it.isStructural && !it.isPinned}.map {it.id},
                        "pending" to currentNodes.orEmpty().filter {sortMode==NodeSortMode.MANUAL && !it.isStructural && !it.isCompleted}.map {it.id},
                        "completed" to currentNodes.orEmpty().filter {sortMode==NodeSortMode.MANUAL && !it.isStructural && it.isCompleted}.map {it.id})
                    else listOf(false,true).flatMap {completed->listOf(("pinned:$completed" as Any) to currentNodes.orEmpty().filter {it.isCompleted==completed && it.isStructural && it.isPinned}.map {it.id},(completed as Any) to currentNodes.orEmpty().filter {it.isCompleted==completed && !it.isPinned}.map {it.id})}.toMap()
                    val dragDisabled = sprintScope || isSubmittingNode || selection.ids.isNotEmpty() || filterActive || (sortMode != NodeSortMode.MANUAL && !layersFirst) || currentNodes == null
                    val drag = rememberDragReorderState(nodeListState, "node:", groups, dragDisabled, actions.operation.error, "$currentNodeId:${sortMode.name}:$layersFirst:$filterActive") { source, target, _ ->
                        if(layersFirst && projectState.nodesById[source]?.isStructural==true && !filterActive) actions.reorderLayersTo(source,currentNodeId,target)
                        else if(layersFirst && sortMode==NodeSortMode.MANUAL && !filterActive) actions.reorderContentTo(source,currentNodeId,target)
                        else if (sortMode == NodeSortMode.MANUAL && !filterActive) actions.reorderTo(source, currentNodeId, target)
                    }
                    val boardMapping=creationMapping?.takeIf {it.kind=="KANBAN" && managementContexts.any {c->c.id==it.contextId && c.enabled}}
                    if(boardMapping!=null) ManagedKanbanDialog(boardMapping.contextId,nodeRepository.management.kanban,{openReference(it)},onPlanning={cascadeLayers.firstOrNull {it.contextId==boardMapping.contextId && it.kind=="SPRINTS"}?.let {openReference(it.nodeId)}},onDismiss={if(!restoreReference() && currentPath.isNotEmpty()) currentPath.removeAt(currentPath.lastIndex)},inline=true,modifier=Modifier.weight(1f),description=scopeDescription,onEditDescription={currentNode?.let {openNodeEditor(it,"description")}})
                    else LazyColumn(
                        state = nodeListState,
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("nodes-list").then(drag.gestureModifier),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom=bottomControlsHeight),
                    ) {
                        item(key = "layer-context", contentType = "context") {
                            Column {
                                Spacer(modifier = Modifier.height(14.dp))


                                if (currentNode == null) Row(verticalAlignment = Alignment.CenterVertically) {
                                    ProjectIdentityIcon(project)
                                    Spacer(Modifier.width(8.dp))
                                    Box(Modifier.weight(1f)) { DetailScopeTitle(project.name) }
                                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    val nodeTitle:@Composable ()->Unit={
                                        Box(Modifier.weight(1f)) {
                                            DetailScopeTitle(com.r0ybt.arachn0de.data.repository.storyDisplayTitle(currentNode.id,currentNode.title,LocalStoryLabels.current), onEdit =
                                                ({ if(useNodeModule) nodeEditRequest=com.r0ybt.arachn0de.domain.model.NodeProperty.TITLE else openNodeEditor(currentNode,"title") }).takeIf { !isSubmittingNode && assignmentsLoaded && peopleLoaded })
                                        }
                                    }
                                    if(!useNodeModule) nodeTitle()
                                    if (currentNode.isCompletable && currentNode.workState == null) {
                                        val colors = completionControlColors(false, currentNode.isCompleted, !isSubmittingNode)
                                        androidx.compose.material3.IconButton(
                                            onClick = { actions.setCompleted(currentNode.id, !currentNode.isCompleted) },
                                            enabled = !isSubmittingNode,
                                            modifier = Modifier.size(48.dp).testTag("detail-completion:${currentNode.id}")
                                                .semantics { stateDescription = if (currentNode.isCompleted) "Completada" else "Pendiente" }
                                                .background(if (currentNode.isCompleted) colors.fill else Arachn0deColors.ControlSurface,
                                                    androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
                                        ) {
                                            if (currentNode.isCompleted) LucideIcon(androidx.compose.material.icons.Icons.Default.Check,
                                                "Marcar pendiente: ${currentNode.title}", tint = colors.mark)
                                            else Box(Modifier.size(14.dp).border(2.dp, colors.border, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                                .semantics { contentDescription = "Completar: ${currentNode.title}" })
                                        }
                                    }
                                    if(useNodeModule) nodeTitle()
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                if (currentNode == null) Text("Proyecto raíz", color = Arachn0deColors.TextSecondary, fontSize = 12.sp)

                                if (currentNode != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "CAPA $currentLayer",
                                            color = Arachn0deColors.TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )

                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (currentNode == null) AttentionIndicator(attention?.byProjectId?.get(project.id))
                                if (scopeDescription.isNotBlank() || currentNode != null) {
                                    androidx.compose.material3.Card(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("detail-description")
                                            .clickable(enabled = currentNode != null && !isSubmittingNode && assignmentsLoaded && peopleLoaded,
                                                onClickLabel = "Editar descripción") { currentNode?.let { if(useNodeModule) nodeEditRequest=com.r0ybt.arachn0de.domain.model.NodeProperty.DESCRIPTION else openNodeEditor(it,"description") } },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = androidx.compose.material3.CardDefaults.cardColors(
                                            containerColor = Arachn0deColors.ControlSurface,
                                        ),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        ) {
                                            Text(
                                                text = "DESCRIPCIÓN",
                                                color = Arachn0deColors.TextSecondary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = 0.08.sp,
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            AttachmentText(
                                                text = scopeDescription.ifBlank { "Añadir descripción" },
                                                ownerNodeId=currentNodeId,
                                                webLinks=currentNode?.purpose in setOf(NodePurpose.ACTION,NodePurpose.NOTE),
                                                color = Arachn0deColors.TextPrimary,
                                                fontSize = ContentTypography.Detail,
                                                lineHeight = ContentTypography.DetailLine,
                                            )
                                        }
                                    }
                                }

                                cascadeError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                                currentNodeId?.let {id->ManagementCascadeTools(id,cascadeLayers.firstOrNull {it.nodeId==id},cascadeRoles.records.roles.firstOrNull {it.nodeId==id},nodeRepository.management,contextId=cascadeRoles.owners[id],sprint=currentNode?.sprintMode==true,onLeaveLayer={if(!restoreReference() && currentPath.lastOrNull()==id) currentPath.removeAt(currentPath.lastIndex)},enabled=managementContexts.any {it.id==cascadeRoles.owners[id] && it.enabled}) {target->openReference(target)}}
                                if(currentNodeId==null || managementRoot!=null) ManagementSection(
                                    if(currentNodeId==null) project.id else null,currentNodeId,managementContext,nodeRepository.management,currentNode?.title ?: project.name,scopeDescription, onOpenNode={id->openReference(id)},configurationOpen=planningOpen,onConfigurationDismiss={planningOpen=false})
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
                                if (currentNode == null || currentNode.isStructural) FinancialSummaryCard(
                                    if (currentNode == null) financial?.byProjectId?.get(project.id) else financial?.byNodeId?.get(currentNode.id), title = "Obligaciones · Todo el período",
                                    overview = if (currentNode == null) financial?.overviewByProjectId?.get(project.id) else financial?.overviewByNodeId?.get(currentNode.id))
                                if (sprintScope && !managedSprint) {
                                    SprintProgressCard(com.r0ybt.arachn0de.ui.state.sprintProgressPercentage(projectState.nodes, currentNode!!.id,linkedIds))
                                    TextButton(onClick={showLinkTasks=true},enabled=!isSubmittingNode) {Text("Vincular tareas")}
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                if (currentNode != null && currentNode.isStructural && !sprintScope && !teamScope && currentProgress != null) {
                                    NodeProgressCard(progress = currentProgress!!)
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(0.dp),itemVerticalAlignment=Alignment.CenterVertically) {
                                    CardIndicatorDetail(currentNode?.id?.let {"node:$it"} ?: "project:${project.id}",onOpenNode={id->openReference(id)},onOpenPeople=onOpenPeople,onOpenTechnologies=onOpenTechnologies,managementContext=managementContexts.firstOrNull {it.enabled && it.id==(cascadeRoles.owners[currentNodeId] ?: managementContext?.id)}?.id,participationRepository=nodeRepository.management.participation,showPeople=!managedSprint,fillWidth=false)
                                    if(teamScope && responsibleByNode[currentNodeId].orEmpty().isNotEmpty()) Text("Responsables del contenido Equipo",style=MaterialTheme.typography.labelLarge)
                                    if(!managedSprint) CompactAssignments(currentNode?.id ?: project.id, currentNode?.let {responsibleByNode[it.id]}.orEmpty(), project=currentNode==null,showTechnologies=currentNode!=null,fillWidth=false,compactSpacing=true,showPeople=currentNode?.isStructural!=true)
                                }
                                if (currentNode != null) {
                                    androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if(currentNode.workState != null) SecondaryAction("Estado: ${currentNode.workState.label}", { changingWorkStateId = currentNode.id }, enabled = !isSubmittingNode)
                                    }
                                    if (currentNode.purpose == NodePurpose.NOTE && cascadeLayers.none {it.nodeId==currentNode.parentId && it.kind=="ACTA"}) Text("Nota · Convierte en capa para añadir hijos", color = Arachn0deColors.TextSecondary)
                                    PriorityIndicator(currentNode)
                                    ObligationIndicator(currentNode)
                                    if (currentNode.obligation != null) Text("Convierte esta obligación en una tarea antes de usarla como capa.", color = Arachn0deColors.TextSecondary)
                                    TaskDateIndicator(currentNode, now)
                                    TagChips(tagState.forNode(currentNode.id))

                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                            }
                        }
                        if (scopeFilters.active && !sprintScope) {
                            if (filteredRows == null) item { Text("Cargando resultados…") }
                            else if (filteredRows!!.none { it.isMatch }) item { Text("Sin coincidencias") }
                            items(filteredRows.orEmpty(), key = { "filtered:${it.node.id}" }) { row ->
                                if (scopeFilters.person != null && row.isMatch && row.node.obligation != null) {
                                    androidx.compose.material3.Card(
                                        modifier = Modifier.fillMaxWidth().padding(start = minOf(row.depth, 6).times(12).dp)
                                            .testTag("person-obligation:${row.node.id}")
                                            .semantics { selected = row.node.id in selection.ids; stateDescription = if (row.node.id in selection.ids) "Seleccionado" else "Sin seleccionar" },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Arachn0deColors.Surface),
                                    ) {
                                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                            TextButton(modifier = Modifier.fillMaxWidth(), onClick = { if(selection.ids.isNotEmpty()) selection.toggle(row.node.id) else actions.navigate(row.node.id) { path -> currentPath.clear(); currentPath.addAll(path) } }) { Text(row.node.title, modifier = Modifier.fillMaxWidth(), color = Arachn0deColors.TextPrimary, fontSize = ContentTypography.ProjectTitle, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                            CompactCardIndicators("node:${row.node.id}")
                                            PriorityIndicator(row.node)
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
                                            TextButton(onClick = { if(selection.ids.isNotEmpty()) selection.toggle(row.node.id) else actions.navigate(row.node.id) { path -> currentPath.clear(); currentPath.addAll(path) } }) { Text(row.node.title) }
                                            CompactCardIndicators("node:${row.node.id}")
                                            PriorityIndicator(row.node)
                                            TagChips(tagState.forNode(row.node.id))
                                            TextButton(onClick = { historyNodeId = row.node.id }) { Text("Historial") }
                                            TextButton(onClick = { if (assignmentsLoaded && peopleLoaded) drafts.open(row.node.parentId, row.node.id) { EditorDraft(row.node.id, row.node.parentId, row.node.title, row.node.description, startAt = row.node.startAt, dueAt = row.node.dueAt, purpose = row.node.purpose, obligation = row.node.obligation, priority = row.node.priority).apply { tagIds = tagState.nodeIds[row.node.id].orEmpty().toList(); responsibleIds = responsibleByNode[row.node.id].orEmpty().map { it.id }; creationGroupId = row.node.creationGroupId; captureSharedBaseline() } } }) { Text("Editar") }
                                        }
                                    }
                                }
                            }
                        } else {
                        if(creationRole?.role=="EPIC" && !scopeFilters.active) {
                            val related=storiesByEpic[currentNodeId].orEmpty().mapNotNull {projectState.nodesById[it]}.filter {it.parentId!=currentNodeId}.sortedWith(compareBy<Node> {it.position}.thenBy {it.id})
                            if(related.isNotEmpty()) item {Text("Historias vinculadas desde otras épicas",style=MaterialTheme.typography.titleMedium)}
                            items(related,key={"related-story:${it.id}"}) {story->ManagementNavigationCard(ManagementToolVisual(story.title,"Vínculo adicional · "+story.parentId?.let {projectState.nodesById[it]?.title}.orEmpty(),Icons.Default.Article)) {openReference(story.id)}}
                        }
                        if (currentNodes == null) item { Text("Cargando orden…") }
                        else if (!teamScope && !managedSprint && (currentNode == null || currentNode.isStructural)) {
                            val message = layerContentMessage(currentNodes.orEmpty().isNotEmpty(), currentProgress ?: projectState.projectProgressById[project.id])
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
                            } else if (groupNodes.isNotEmpty()) {
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
                                NodeCard(
                                    navigationTool=cascadeLayers.firstOrNull {it.nodeId==node.id}?.kind?.let {kind->
                                        if(kind.startsWith("OBJECTIVE:")) ManagementToolVisual(node.title,"Resultado del proyecto",Icons.Default.Flag) else managementToolVisual(kind)
                                    } ?: if(node.sprintMode && creationMapping?.kind=="SPRINTS") sprintCardVisual(node.id,node.title,iterationOverview,sprintOverviewRefs.count {it.sprintId==node.id}) else null
                                    ?: cascadeRoles.records.roles.firstOrNull {it.nodeId==node.id}?.let {role->
                                        if(role.role=="EPIC") ManagementToolVisual(node.title,"${storiesByEpic[node.id].orEmpty().size} historias asociadas",Icons.Default.Workspaces)
                                        else ManagementToolVisual(node.title,"Historia de usuario",Icons.Default.Article)
                                    } ?: if(node.purpose==NodePurpose.NOTE && creationMapping?.kind=="ACTA") ManagementToolVisual(node.title,"Sección del Acta",Icons.Default.Description) else null,
                                    onAdvanceWorkState = node.workState?.let { ({ actions.advanceWorkState(node.id) }) },
                                    onChangeWorkState = node.workState?.let { ({ changingWorkStateId = node.id }) },
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
                                    onOpen = {
                                        if(sprintScope && node.id in linkedIds) {
                                            val path=mutableListOf<String>();var cursor:Node?=node
                                            while(cursor!=null) {path.add(cursor.id);cursor=projectState.nodesById[cursor.parentId]}
                                            openReference(node.id)
                                        } else currentPath.add(node.id)
                                    },
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
                                    canDragReorder = !dragDisabled && !layersFirst,
                                    onCopy = { descendants -> copyActions.copy(projectState, node.id, descendants) },
                                    onEdit = { openNodeEditor(node) },
                                    canMoveUp = !sprintScope && !isSubmittingNode && (if(layersFirst && node.isStructural) node.id!=renderNodes.firstOrNull {it.isStructural && it.isPinned==node.isPinned}?.id else sortMode==NodeSortMode.MANUAL && node.id!=renderNodes.firstOrNull {it.isPinned==node.isPinned && (if(layersFirst) !it.isStructural else true)}?.id),
                                    canMoveDown = !sprintScope && !isSubmittingNode && (if(layersFirst && node.isStructural) node.id!=renderNodes.lastOrNull {it.isStructural && it.isPinned==node.isPinned}?.id else sortMode==NodeSortMode.MANUAL && node.id!=renderNodes.lastOrNull {it.isPinned==node.isPinned && (if(layersFirst) !it.isStructural else true)}?.id),
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
                                        if (node.workState != null) actions.confirmWorkState(node.id) else if (node.isCompletable) actions.setCompleted(node.id, !node.isCompleted)
                                    },
                                    dragging = dragging,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = null, fadeOutSpec = null,
                                        placementSpec = if (dragging) null else androidx.compose.animation.core.spring(),
                                    ).then(drag.cardModifier(node.id)),
                                )
                            }
                        }
                        if (sprintScope && filterActive) {
                            val nestedMatches = filteredRows.orEmpty().filter { it.depth > 0 }
                            if (nestedMatches.isNotEmpty()) item { Text("Coincidencias en subcapas", color = Arachn0deColors.TextSecondary) }
                            items(nestedMatches, key = { "sprint-filtered:${it.node.id}" }) { row ->
                                Column(Modifier.padding(start = minOf(row.depth, 6).times(12).dp)) {
                                    if (!row.isMatch) Text("Contexto · ${row.node.title}", color = Arachn0deColors.TextSecondary)
                                    TextButton(onClick = { if (selection.ids.isNotEmpty()) selection.toggle(row.node.id) else actions.navigate(row.node.id) { path -> currentPath.clear();currentPath.addAll(path) } }) { Text(row.node.title) }
                                    row.node.workState?.let { Text(it.label, color = Arachn0deColors.TextSecondary) }
                                    SecondaryAction(if (row.node.id in selection.ids) "✓ Seleccionado" else "Seleccionar", { selection.toggle(row.node.id) }, enabled = !isSubmittingNode)
                                }
                            }
                        }
                        }
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(horizontal=12.dp,vertical=10.dp)
                .then(if(showDrawer) Modifier.clearAndSetSemantics {} else Modifier)
                .onSizeChanged {bottomControlsHeight=with(bottomControlsDensity) {it.height.toDp()}}) {
                androidx.compose.material3.SnackbarHost(actions.snackbar, Modifier.fillMaxWidth()) { data ->
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
                AudioBar()
                // Transparent layout only: the list reserves this height; AppSafeArea owns system insets.
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
                if(currentNodeId==null && projectEditField!=null) {
                    CorePropertyEditor(projectEditField!!,projectFieldValue,{projectFieldValue=it},
                        enabled=!projectEditingActions.operation.busy,maxHeight=coreModuleMaxHeight,
                        onCancel=finishProjectEdit,onConfirm={
                            projectEditingActions.saveField(project.id,projectEditField!!,projectFieldOriginal,projectFieldValue,finishProjectEdit)
                        })
                } else if(currentNodeId==null) CoreBottomModule(coreContext,{coreContext=it},coreModuleMaxHeight,
                    contextActions=contextActions,creationActions=creationActions,onEditProject=editProject)
                else if(useNodeModule && currentNode!=null) androidx.compose.runtime.key(currentNode.id) {
                    NodeBottomModule(currentNode,nodeRepository,actions,people,responsibleByNode[currentNode.id].orEmpty(),
                        peopleLoaded && assignmentsLoaded,tagState,nodeTechnologies,coreModuleMaxHeight,contextActions,creationActions,
                        nodeEditRequest,{nodeEditRequest=null})
                } else creationActions()

            }

            if (showDrawer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize().background(Arachn0deColors.Scrim.copy(alpha = 0.45f)).clickable { showDrawer = false }.clearAndSetSemantics {})
                    AppIdentityDrawer(onDismiss = { showDrawer = false }, onProjects = onOpenProjects, projectsSelected = true, onPeople = { showDrawer = false; onOpenPeople() }, onTechnologies = { showDrawer = false; onOpenTechnologies() },onTags={showDrawer=false;onOpenTags()}, onAttention = { showDrawer = false; onOpenAttention() }, onCalendar = { showDrawer = false; onOpenCalendar() }, onObligations = { showDrawer = false; onOpenObligations() }, onAppearance = { showDrawer = false; onOpenAppearance() }, onMetro = { showDrawer = false; onOpenMetro() }, onGame = { showDrawer = false; onOpenGame() }, onAbout = { showDrawer = false; onOpenAbout() })
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

    if(showDefaults) androidx.compose.ui.window.Dialog(onDismissRequest = {}, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, dismissOnBackPress = false)) {
        AppSafeArea { CreationDefaultsScreen(nodeRepository.creationDefaults,
            currentNodeId?.let { com.r0ybt.arachn0de.domain.defaults.DefaultsScope.Layer(project.id,it) } ?: com.r0ybt.arachn0de.domain.defaults.DefaultsScope.Project(project.id),
            currentNode?.title ?: project.name, tagState.tags, people) { showDefaults = false } }
    }

    if (showSprintMode && currentNode?.isStructural == true) SprintModeDialog(currentNode.sprintMode, isSubmittingNode,
        { showSprintMode = false }, { actions.sprintMode(currentNode.id, !currentNode.sprintMode) { showSprintMode = false } })
    changingWorkStateId?.let { id -> projectState.nodesById[id]?.let { node -> node.workState?.let { state ->
        WorkStateDialog(node.title, state, isSubmittingNode, { changingWorkStateId = null }, { chosen -> actions.workState(id, chosen) { changingWorkStateId = null } })
    } } }
    if(showContextActions && currentNodeId!=null) ContextActionMenu(currentNode?.title ?: project.name,{ showContextActions = false },bottomAligned=currentNode?.purpose==NodePurpose.ACTION && currentNode.isStructural==false,content=contextActions)


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
        var expanded by remember { mutableStateOf(currentPath.toList()) }
        AlertDialog(onDismissRequest = { if(!isSubmittingNode) bulkMove = false }, title = { Text("Mover ${selection.ids.size} elementos") },
            text = { LayerNavigator(nodes = projectState.nodes, expandedIds = expanded, onToggle = { id -> expanded = if(id in expanded) expanded - id else expanded + id },
                currentNodeId = currentNodeId, projectName = project.name, movingIds = selection.ids, enabled = !isSubmittingNode,
                onHome = {}, onProject = { actions.moveSelected(project.id,selection.ids,null) { bulkMove = false; selection.clear() } },
                onNavigateTo = { target -> actions.moveSelected(project.id,selection.ids,target) { bulkMove = false; selection.clear() } }) },
            confirmButton = {}, dismissButton = { TextButton(enabled = !isSubmittingNode, onClick = { bulkMove = false }) { Text("Cancelar") } })
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
        val source = projectState.nodesById[sourceId]
        var moveExpanded by rememberSaveable(
            sourceId,
            stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it.toList() }),
        ) { mutableStateOf(currentPath.toList()) }
        AlertDialog(
            containerColor = Arachn0deColors.Surface,
            titleContentColor = Arachn0deColors.TextPrimary,
            textContentColor = Arachn0deColors.TextSecondary,
            onDismissRequest = { if (!isSubmittingNode) movingNodeId = null },
            title = { Text("Mover a…") },
            text = {
                Column {
                    Text(source?.title ?: "El elemento ya no existe", maxLines = 2)
                    Text("Selecciona un destino. Una tarea que reciba hijos pasará a ser Capa.")
                    LayerNavigator(
                        nodes = projectState.nodes,
                        expandedIds = moveExpanded,
                        onToggle = { id -> moveExpanded = if (id in moveExpanded) moveExpanded - id else moveExpanded + id },
                        currentNodeId = source?.parentId,
                        projectName = project.name,
                        movingId = sourceId,
                        enabled = !isSubmittingNode && source != null,
                        onHome = {},
                        onProject = { actions.move(sourceId, null) { movingNodeId = null } },
                        onNavigateTo = { target -> actions.move(sourceId, target) { movingNodeId = null } },
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(enabled = !isSubmittingNode, onClick = { movingNodeId = null }) { Text("Cancelar") }
            },
        )
    }

    if (showNavigator) {
        AlertDialog(
            containerColor = Arachn0deColors.Surface,
            titleContentColor = Arachn0deColors.TextPrimary,
            onDismissRequest = { showNavigator = false },
            title = { Text("Capas de cebolla") },
            text = {
                LayerNavigator(
                    projectName = project.name,
                    nodes = projectState.nodes,
                    expandedIds = expandedLayerIds,
                    onToggle = { id ->
                        val ids = expandedLayerIds.toSet()
                        expandedLayerIds = (if (id in ids) ids - id else ids + id).toList()
                    },
                    currentNodeId = currentNodeId,
                    onHome = { showNavigator = false; onBackToProjects() },
                    onProject = { currentPath.clear();referenceReturns=emptyList(); showNavigator = false },
                    onNavigateTo = { targetId ->
                        actions.navigate(targetId) { path ->
                            currentPath.clear()
                            currentPath.addAll(path)
                            showNavigator = false
                        }
                    },
                    listState = navigatorListState,
                )
            },
            confirmButton = { TextButton(onClick = { showNavigator = false }) { Text("Cerrar") } },
        )
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

