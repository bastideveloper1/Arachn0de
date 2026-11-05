package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.calendar.CalendarBirthdays
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.r0ybt.arachn0de.ui.state.EditorDraft
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
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
    calendarListState: LazyListState = rememberLazyListState(),
    onOpenPeople: () -> Unit = {},
    onOpenAttention: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenObligations: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenMetro: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    exportTree: com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot = com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot(emptyList()),
    copyDescendantsReady: Boolean = false,
    onOpenTechnologies: () -> Unit = {},
    onOpenTags:()->Unit={},
    onCreateDraft:()->Unit={},
    calendarResponsible:Map<String,List<com.r0ybt.arachn0de.domain.model.Person>> = emptyMap(),
    calendarStore:ContextCalendarStore=rememberContextCalendarStore(),
    calendarNow:Long=com.r0ybt.arachn0de.ui.state.rememberTaskScreenNow(exportTree.nodes),
    calendarZone:String=java.util.TimeZone.getDefault().id,
    onOpenContent:((Project)->Unit)?=null,
    sortPreferences:com.r0ybt.arachn0de.ui.state.NodeSortPreferences=remember {com.r0ybt.arachn0de.ui.state.NodeSortPreferences()},
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val projectTagState by remember(context) {(context.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).nodeRepository.tags.observe()}.collectAsState(initial=com.r0ybt.arachn0de.domain.model.TagState())
    val copyActions = remember(context,scope) { com.r0ybt.arachn0de.ui.state.NodeCopyActions(context,scope) { (context.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).personRepository.observeAllAssignments().first() } }
    CopyTextChoiceDialog(copyActions)
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
    var createStructure by remember {mutableStateOf(false)}
    if(createStructure) StructureCreationPanel(null,null,
        onNew={createStructure=false;drafts.open(null) {EditorDraft(null,null,"","")}},onDismiss={createStructure=false})
    var demoMenu by remember { mutableStateOf(false) }
    var demoConfirm by remember { mutableStateOf(false) }
    var demoBusy by remember { mutableStateOf(false) }
    var demoError by remember { mutableStateOf<String?>(null) }
    if(demoConfirm || demoBusy || demoError!=null) AlertDialog(
        onDismissRequest={if(!demoBusy) {demoConfirm=false;demoError=null}},
        title={Text("Crear proyecto de demostración")},
        text={Text(demoError ?: if(demoBusy) "Creando demostración…" else "Creará un proyecto nuevo con 6 Personas ficticias, 3 épicas, 9 historias y 3 Sprints. No asociará Personas reales. Las Personas ficticias permanecen en el catálogo al eliminar el proyecto y pueden borrarse allí.")},
        confirmButton={TextButton(enabled=!demoBusy,onClick={
            if(demoError!=null) {demoError=null;demoConfirm=false} else {
                demoBusy=true
                scope.launch {
                    try {
                        val app=context.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication
                        com.r0ybt.arachn0de.gantt.GanttDemo.create(app.database,app.personRepository)
                        demoConfirm=false
                    } catch(c:kotlinx.coroutines.CancellationException) {throw c}
                    catch(e:Exception) {demoError=e.message ?: "No se pudo crear la demostración."}
                    finally {demoBusy=false}
                }
            }
        }) {Text(if(demoError!=null) "Cerrar" else "Crear demostración")}},
        dismissButton={if(!demoBusy) TextButton(onClick={demoConfirm=false;demoError=null}) {Text("Cancelar")}}
    )

    val calendarToday=com.r0ybt.arachn0de.domain.model.CalendarDates.localDay(calendarNow,java.util.TimeZone.getTimeZone(calendarZone))
    val contextualCalendar=calendarStore.scope("dashboard",calendarToday)
    val contextualBirthdays=rememberContextBirthdays((context.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).personRepository)
    val contextualIndex by rememberContextCalendarIndex(exportTree,calendarZone)
    val contextualFavorites=LocalElementAccessState.current.favorites
    val contextualResults=remember(contextualIndex,contextualCalendar.selected,contextualCalendar.conditions,contextualCalendar.favorites,contextualFavorites,calendarNow) {
        contextualIndex?.results(contextualCalendar.selected,false,contextualCalendar.favorites,contextualFavorites,calendarNow,java.util.TimeZone.getTimeZone(calendarZone),contextualCalendar.conditions).orEmpty()
    }
    val favoriteProjects=if(contextualCalendar.favorites && !contextualCalendar.conditions.active && contextualCalendar.selected==null) projects.filter {"project:${it.id}" in contextualFavorites} else emptyList()
    val projectsById=remember(projects) {projects.associateBy {it.id}}
    var calendarTaskId by rememberSaveable {mutableStateOf<String?>(null)}
    var calendarEntryAction by rememberSaveable {mutableStateOf<String?>(null)}
    val calendarTask=exportTree.nodesById[calendarTaskId]
    val calendarProject=calendarTask?.projectId?.let(projectsById::get)
    val calendarApp=context.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication
    if(calendarTask!=null && calendarProject!=null) {
        val closeCalendarTask:()->Unit={calendarTaskId=null;calendarEntryAction=null}
        var pendingCalendarOpen by rememberSaveable(calendarTaskId) {mutableStateOf(calendarTaskId)}
        ProjectNodeScreen(calendarProject,calendarApp.nodeRepository,closeCalendarTask,
            calendarStore=calendarStore,
            openNodeId=pendingCalendarOpen,onOpenNodeHandled={pendingCalendarOpen=null},onBackToSearch=closeCalendarTask,
            entryNodeId=calendarTask.id,
            onOpenPeople=onOpenPeople,onOpenAttention=onOpenAttention,onOpenCalendar=onOpenCalendar,
            onOpenObligations=onOpenObligations,onOpenAbout=onOpenAbout,
            onOpenMetro=onOpenMetro,onOpenAppearance=onOpenAppearance,onOpenTechnologies=onOpenTechnologies,onOpenTags=onOpenTags,
            entryAction=calendarEntryAction,onEntryActionHandled={calendarEntryAction=null},
            onConverted={id,layer->closeCalendarTask();onConverted(id,layer)})
        return
    }

    val nameSearch=rememberInlineSearch("dashboard")
    val nameIndex=if(nameSearch.open)quickCaptureIndex() else null
    val nameMatches by androidx.compose.runtime.key(nameIndex,nameSearch.query,contextualCalendar.filtering,contextualResults,favoriteProjects) {
        androidx.compose.runtime.produceState<List<com.r0ybt.arachn0de.access.ElementResult>?>(null) {
            if(nameSearch.active && nameIndex!=null)value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                com.r0ybt.arachn0de.ui.contextualResults(nameIndex,nameSearch.query,null,null).filter {result->
                    !contextualCalendar.filtering || (if(result.nodeId==null)favoriteProjects.any {it.id==result.projectId} else contextualResults.any {it.id==result.nodeId})
                }
            }
        }
    }
    val searchListState=rememberLazyListState()
    val sortContext=com.r0ybt.arachn0de.domain.model.SortConfigurationCodec.PROJECTS_CONTEXT
    val sortConfiguration=sortPreferences.configuration(sortContext)
    val sortedProjects=projects.sortedWith(Comparator {a,b->sortConfiguration.favorites.compare("project:${a.id}" in contextualFavorites,"project:${b.id}" in contextualFavorites)})
    DashboardSortAccess(contextualCalendar,contextualIndex,calendarToday,contextualBirthdays,contextualResults.size+favoriteProjects.size,
        sortConfiguration={sortConfiguration},onSortChange={sortPreferences.configure(sortContext,it)},sortReady=sortPreferences.loaded)

    BackHandler(enabled = showDrawer) { showDrawer = false }

    val bottomControlsHeight=0.dp
    val background = Arachn0deColors.Background

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Arachn0deColors.Background,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if(showDrawer) Modifier.clearAndSetSemantics {} else Modifier)
                    .padding(horizontal = 4.dp, vertical = 10.dp),
            ) {
                HeaderBar(compactLock=true,showBrandMark=false,title="Núcleos",
                    onMenuClick = { showDrawer = true },
                )
                BashPrompt("~/General","ls -l")
                Spacer(modifier = Modifier.height(6.dp))


                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if(contextualCalendar.filtering && !nameSearch.active) {
                        val results=contextualResults
                        if(contextualIndex==null || !copyDescendantsReady) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {Text("Cargando tareas…")}
                        else LazyColumn(Modifier.fillMaxSize().testTag("context-calendar-results"),state=calendarListState,verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=bottomControlsHeight)) {
                            item(key="context-calendar") {ContextCalendar(contextualCalendar,contextualIndex,calendarToday,resultCount=results.size+favoriteProjects.size,birthdays=contextualBirthdays)}
                            if(contextualCalendar.selected?.let {contextualBirthdays?.on(it)?.isNotEmpty()}==true)
                                item(key="context-birthdays") {ContextBirthdayCards(contextualBirthdays,contextualCalendar.selected)}
                            if(contextualIndex==null || !copyDescendantsReady) item {Text("Cargando tareas…")}
                            else if(results.isEmpty() && favoriteProjects.isEmpty()) item {Text("No hay coincidencias con los filtros activos.",Modifier.testTag("context-calendar-empty"))}
                            items(favoriteProjects,key={"calendar-project:${it.id}"}) {core->
                                ProjectCard(core,projectAlertsById[core.id] ?: com.r0ybt.arachn0de.domain.model.ProjectCardAlerts(),
                                    onContent=onOpenContent?.let {open->({open(core)})},
                                    onOpen={onOpenProject(core)},onEdit={drafts.open(null,core.id) {EditorDraft(core.id,null,core.name,core.description)}},
                                    onDelete={deletingProjectId=core.id;deletingProjectName=core.name},
                                    onPhoto={photoProjectId=core.id},onMoveInside={moveProject=core.id},
                                    onCopy={descendants->copyActions.copyProject(core,exportTree,descendants)},canCopy=!copyActions.busy,canCopyDescendants=copyDescendantsReady,
                                    tags=projectTagState.forProject(core.id))
                            }
                            items(results,key={"calendar:${it.id}"}) {node->
                                ContextCalendarTaskCard(node,contextualIndex?.location(node,projectsById).orEmpty(),projectTagState.forNode(node.id),calendarNow,calendarResponsible[node.id].orEmpty(),paymentListState=calendarListState) {task,action->calendarEntryAction=action;calendarTaskId=task.id}
                            }
                        }
                    } else if (projects.isEmpty() && !nameSearch.active) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=bottomControlsHeight)) {
                            ContextCalendar(contextualCalendar,contextualIndex,calendarToday,birthdays=contextualBirthdays)
                            EmptyProjectsState(onCreateProject = { drafts.open(null) { EditorDraft(null, null, "", "") } })
                        }
                    } else {
                        ProjectList(
                            projects = if(nameSearch.active)sortedProjects.filter {core->nameMatches.orEmpty().any {it.nodeId==null && it.projectId==core.id}} else sortedProjects,
                            additionalRows={
                                if(nameSearch.active) {
                                    if(nameMatches==null) item {Text("Buscando…")}
                                    else if(nameMatches!!.isEmpty()) item {Text("Sin coincidencias",Modifier.testTag("inline-search-empty"))}
                                    items(nameMatches.orEmpty().mapNotNull {it.nodeId?.let(exportTree.nodesById::get)},key={"node:${it.id}"}) {node->
                                        ContextCalendarTaskCard(node,originalNodePath(node,exportTree,projectsById.mapValues {it.value.name}),projectTagState.forNode(node.id),calendarNow,calendarResponsible[node.id].orEmpty(),paymentListState=searchListState) {task,action->calendarEntryAction=action;calendarTaskId=task.id}
                                    }
                                }
                            },
                            header={ContextCalendar(contextualCalendar,contextualIndex,calendarToday,birthdays=contextualBirthdays)},
                            onPhoto = { photoProjectId = it.id },
                            onMoveInside = { moveProject=it.id },
                            onCopy = { project, descendants -> copyActions.copyProject(project,exportTree,descendants) },
                            canCopy = !copyActions.busy,
                            canCopyDescendants = copyDescendantsReady,
                            projectAlertsById = projectAlertsById,
                            projectTagsById = projects.associate {it.id to projectTagState.forProject(it.id)},
                            reorderBusy = nameSearch.active || actions.operation.busy,
                            reorderError = actions.operation.error,
                            listState = if(nameSearch.active)searchListState else listState,
                            bottomContentPadding = bottomControlsHeight,
                            onCreateProject = { drafts.open(null) { EditorDraft(null, null, "", "") } },
                            onEdit = { project ->
                                drafts.open(null, project.id) { EditorDraft(project.id, null, project.name, project.description) }
                            },
                            onDelete = { project ->
                                deletingProjectId = project.id
                                deletingProjectName = project.name
                            },
                            onOpenProject = onOpenProject,
                            onContent = onOpenContent,
                            onReorderTo = { fromId, toId ->
                                actions.reorderTo(fromId, toId)
                            },
                        )
                    }
                }
            }
            ContextualBottomPanel {
                AlternatingBottomBar {
                    Row(Modifier.fillMaxWidth().then(if(showDrawer)Modifier.clearAndSetSemantics {} else Modifier)
                        .background(navigationBarBackground).testTag("dashboard-bottom-actions")) {
                        CompactBarActions(listOf(
                            BottomBarAction("Crear núcleo",androidx.compose.material.icons.Icons.Default.Add,tag="dashboard-create-core") {createStructure=true},
                            BottomBarAction("Crear borrador",androidx.compose.material.icons.Icons.Default.Description,tag="dashboard-create-draft",run=onCreateDraft)
                        ),iconAbove=false) {a->
                            TextButton(onClick=a.run,enabled=a.enabled,modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag(a.tag),
                                contentPadding=PaddingValues(8.dp),colors=androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor=Arachn0deColors.TextPrimary)) {
                                NavigationBarIcon(a.icon,label=a.label);Spacer(Modifier.width(6.dp))
                                Text(a.label,style=androidx.compose.material3.MaterialTheme.typography.labelSmall,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
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
                        onPeople = { showDrawer = false; onOpenPeople() }, onTechnologies = { showDrawer = false; onOpenTechnologies() },onTags={showDrawer=false;onOpenTags()},
                        onAttention = { showDrawer = false; onOpenAttention() },
                        onCalendar = { showDrawer = false; onOpenCalendar() },
                        onObligations = { showDrawer = false; onOpenObligations() },
                        onAppearance = { showDrawer = false; onOpenAppearance() }, onMetro = { showDrawer = false; onOpenMetro() }, onAbout = { showDrawer = false; onOpenAbout() },
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


/** Shares the existing panel and the dashboard calendar state; no alternative ordering is created. */
@Composable internal fun DashboardSortAccess(calendar:ContextCalendarState,index:com.r0ybt.arachn0de.domain.model.ContextCalendarIndex?,
    today:com.r0ybt.arachn0de.domain.model.CalendarDay,birthdays:CalendarBirthdays?=null,resultCount:Int?=null,
    sortConfiguration:(()->com.r0ybt.arachn0de.domain.model.SortConfiguration)?=null,onSortChange:((com.r0ybt.arachn0de.domain.model.SortConfiguration)->Unit)?=null,sortReady:Boolean=true) {
    var open by remember {mutableStateOf(false)}
    RegisterGlobalSort(sortReady,filter=sortConfiguration==null) {open=true}
    fun clear() {calendar.selected=null;calendar.clearCardFilters();open=false}
    if(open) OrderingPanel(onDismiss={open=false},title=if(sortConfiguration==null)"Filtrar" else "Ordenar y filtrar",body={
        Column(Modifier.fillMaxWidth().heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
            sortConfiguration?.invoke()?.let {configuration->FavoriteSortSelector(configuration.favorites) {onSortChange?.invoke(configuration.copy(favorites=it))}}
            ScopeFiltersDialog(remember {ScopeFilters()},emptyList(),emptyList(),inline=true,externalActions=true,cardFilters=calendar,cardOnly=true)
            ContextCalendar(calendar,index,today,resultCount=resultCount,birthdays=birthdays)
        }
    },actions={
        OrderingPanelButton("Limpiar","dashboard-clear-filters") {clear()}
        OrderingPanelButton("Aplicar","dashboard-apply-filters") {open=false}
    })
}
