package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Controls belong to the real layer being opened; normal hierarchy remains the navigator. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ManagementCascadeTools(nodeId:String,mapping:ManagementLayerEntity?,role:ManagementRoleEntity?,repository:ManagementRepository,contextId:String?=null,sprint:Boolean=false,enabled:Boolean=true,onLeaveLayer:()->Unit={},onOpenNode:(String)->Unit) {
    val context=mapping?.contextId ?: role?.contextId ?: contextId ?: return
    if(mapping==null && role==null && !sprint) return
    val kind=mapping?.kind
    var panel by rememberSaveable(nodeId) {mutableStateOf(enabled && kind=="KANBAN")}
    var relationships by rememberSaveable(nodeId) {mutableStateOf(false)}
    var addingSection by rememberSaveable(nodeId) {mutableStateOf(false)}
    var help by rememberSaveable(nodeId) {mutableStateOf(false)}
    val label=when {kind=="OBJECTIVES"->"Objetivo";kind=="BACKLOG"->"Épica";kind=="SPRINTS"->"Sprint";role?.role=="EPIC"->"Historia";else->null}
    val layers by remember(repository) {repository.cascade.layers}.collectAsState(initial=emptyList())
    val objectiveRefs by remember(repository) {repository.relations.objectives}.collectAsState(initial=emptyList())
    val storyRefs by remember(repository) {repository.relations.stories}.collectAsState(initial=emptyList())
    val work by remember(repository,context) {repository.iterations.observe(context)}.collectAsState(initial=IterationState())
    if(!enabled) {Text("Gestión desactivada · datos conservados",style=MaterialTheme.typography.bodySmall);return}
    if(addingSection) CharterSectionEditor(context,repository) {addingSection=false}
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(kind=="ACTA") Button(onClick={addingSection=true}) {Text("Añadir sección")}
        if(kind in listOf("ACTA") || kind?.startsWith("OBJECTIVE:")==true) OutlinedButton(onClick={panel=true}) {Text(when(kind) {"TEAM"->"Participantes";"ACTA"->"Leer acta";else->"Criterios"})}
        if(kind=="KANBAN") TextButton(onClick={panel=true}) {Text("Tablero")}

        if(role!=null && role.role!="STORY") {ResponsibilitiesAccess(context,nodeId,false,repository.participation);TextButton(onClick={relationships=true}) {Text("Vínculos")}}
        if(kind!="TEAM" && !sprint) TextButton(onClick={help=true}) {Text("?")}
    }
    if(kind=="TEAM") TeamContext(context,repository.participation)
    if(sprint) SprintContext(nodeId,context,repository) {panel=true}
    if(kind?.startsWith("OBJECTIVE:")==true) ObjectiveWorkTools(kind.removePrefix("OBJECTIVE:"),context,repository,onOpenNode)
    if(role?.role=="STORY") {
        StoryContext(nodeId,context,repository,onOpenNode) {relationships=true}
        objectiveRefs.filter {it.nodeId==nodeId}.forEach {ref->work.work.objectives.firstOrNull {it.id==ref.objectiveId}?.let {goal->OutlinedButton(onClick={onOpenNode(goal.id)}) {Text("Objetivo · "+goal.title)}}}

        storyRefs.firstOrNull {it.storyId==nodeId}?.let {ref->
            val plan=work.records.plans.firstOrNull {it.sprintId==ref.sprintId}
            OutlinedButton(onClick={onOpenNode(ref.sprintId)}) {Text("Sprint · "+(work.work.nodes.firstOrNull {it.id==ref.sprintId}?.title.orEmpty())+" · "+when(plan?.state) {"ACTIVE"->"Activo";"CLOSED"->"Terminado";else->"Planificado"})}
        }
    }
    if(label!=null && work.work.nodes.none {it.parentId==nodeId}) Text(when(label) {"Épica"->"Crea una épica para organizar las historias.";"Historia"->"Crea una historia; podrás añadir notas, tareas y capas.";"Sprint"->"Crea un Sprint y selecciona trabajo existente.";else->"Crea un objetivo y vincula su trabajo."},style=MaterialTheme.typography.bodySmall)
    if(help && kind=="OBJECTIVES") ManagementHelpScreen("Cómo redactar objetivos",objectiveHelpParagraphs) {help=false}
    else if(help) AlertDialog(onDismissRequest={help=false},title={Text(mapping?.kind?.let {ManagementLayerKind.entries.firstOrNull {k->k.name==it}?.title} ?: role?.role.orEmpty())},text={Text(when(kind) {
        "BACKLOG"->"Organiza las historias en épicas. Sus referencias en Sprint y Kanban abren siempre el original."
        "SPRINTS"->"Crea una iteración, define fechas y selecciona trabajo existente. Las fases y el check son independientes."
        "KANBAN"->"Trabaja sobre el Sprint activo. Abrir una tarjeta conserva su identidad y descendientes."
        "TEAM"->"Añade Personas del catálogo y asigna responsabilidades. Quitar una participación conserva su ficha."
        "SCOPE"->"Describe qué incluye el proyecto y qué queda fuera."
        "PURPOSE"->"Explica por qué se realiza el proyecto y qué necesidad resuelve."
        "DELIVERABLES"->"Describe los resultados concretos que se entregarán."
        "EXCLUSIONS"->"Registra límites y restricciones del proyecto."
        "RISKS"->"Registra situaciones inciertas que podrían afectar al proyecto."
        "SUCCESS"->"Describe las condiciones verificables de éxito."
        "OBJECTIVES"->"Define el objetivo general en la descripción y objetivos específicos con trabajo asociado."
        else->"Esta es una capa real. Sus notas, tareas y capas se editan en la jerarquía habitual."
    })},confirmButton={TextButton(onClick={help=false}) {Text("Cerrar")}})
    if(relationships) BacklogDialog(context,repository.backlog,onOpenNode,true,nodeId) {relationships=false}
    if(panel && sprint) IterationDialog(context,repository.iterations,{onOpenNode(it)},initialSprintId=nodeId) {panel=false}
    else if(panel) when(kind) {
        "KANBAN"->ManagedKanbanDialog(context,repository.kanban,{onOpenNode(it)},{panel=false;layers.firstOrNull {it.contextId==context && it.kind=="SPRINTS"}?.let {onOpenNode(it.nodeId)}}) {panel=false}
        "TEAM"->ParticipationDialog(context,repository.participation) {panel=false}
        "OBJECTIVES"->PlanningDialog(context,"Objetivos","",repository.planning) {panel=false}
        "ACTA"->CascadeCharterReader(context,repository) {panel=false}
        else->if(kind?.startsWith("OBJECTIVE:")==true) PlanningDialog(context,"Objetivo","",repository.planning,initialObjectiveId=kind.removePrefix("OBJECTIVE:")) {panel=false} else panel=false
    }
}
@Composable private fun CascadeCharterReader(context:String,repository:ManagementRepository,onDismiss:()->Unit) {
    val state by remember(repository,context) {repository.backlog.observe(context)}.collectAsState(initial=BacklogState())
    val layers by remember(repository) {repository.cascade.layers}.collectAsState(initial=emptyList())
    val acta=layers.firstOrNull {it.contextId==context && it.kind=="ACTA"}?.nodeId
    val notes=state.nodes.filter {it.parentId==acta && it.purpose=="NOTE"}.sortedBy {it.position}
    val objectives=layers.firstOrNull {it.contextId==context && it.kind=="OBJECTIVES"}?.let {row->state.nodes.firstOrNull {it.id==row.nodeId}}
    androidx.compose.ui.window.Dialog(onDismissRequest=onDismiss,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item {Text("Acta de constitución",style=MaterialTheme.typography.headlineSmall)}
                objectives?.takeIf {it.description.isNotBlank()}?.let {goal->item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {Text("Objetivo general",style=MaterialTheme.typography.titleMedium);Text(goal.description,style=MaterialTheme.typography.bodyLarge)}}}
                notes.filter {it.description.isNotBlank()}.forEach {note->item(key=note.id) {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(note.title,style=MaterialTheme.typography.titleMedium);Text(note.description,style=MaterialTheme.typography.bodyLarge)}}}
                item {TextButton(onClick=onDismiss) {Text("Volver")}}
            }
        }
    }
}

@Composable internal fun ObjectiveWorkTools(goal:String,context:String,repository:ManagementRepository,onOpenNode:(String)->Unit) {
    val work by remember(repository,context) {repository.iterations.observe(context)}.collectAsState(initial=IterationState())
    val links by repository.relations.objectives.collectAsState(initial=emptyList())
    val progress=remember(goal,work.work.nodes,links,work.work.records) {objectiveWorkProgress(goal,work.work.nodes,links,work.work.records)}
    var picking by rememberSaveable(goal) {mutableStateOf(false)}
    var query by rememberSaveable(goal) {mutableStateOf("")}
    var error by remember {mutableStateOf<String?>(null)};var busy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun link(node:String,add:Boolean) {busy=true;scope.launch {try {repository.relations.objective(goal,node,add)} catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message} finally {busy=false}}}
    Column {
        Text(if(progress.complete) "Trabajo completado · "+progress.label else progress.label,style=MaterialTheme.typography.labelLarge)
        TextButton(onClick={picking=true}) {Text("Vincular trabajo")}
        links.filter {it.objectiveId==goal}.forEach {ref->work.work.nodes.firstOrNull {it.id==ref.nodeId}?.let {node->
            Row {TextButton(onClick={onOpenNode(node.id)},modifier=Modifier.weight(1f)) {Text(node.title)};TextButton(enabled=!busy,onClick={link(node.id,false)}) {Text("Retirar")}}
        }}
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }
    if(picking) androidx.compose.ui.window.Dialog(onDismissRequest={picking=false},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.padding(16.dp)) {
                Text("Vincular trabajo",style=MaterialTheme.typography.titleLarge)
                OutlinedTextField(query,{query=it},label={Text("Buscar")},modifier=Modifier.fillMaxWidth())
                val roles=work.work.records.roles.filter {it.role=="STORY"}.mapTo(hashSetOf()) {it.nodeId}
                val candidates=work.work.nodes.filter {work.work.owners[it.id]==context && (it.purpose=="ACTION" || it.id in roles) && it.title.contains(query.trim(),true)}
                androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
                    if(candidates.isEmpty()) item {Text("Crea historias en Backlog o tareas en las capas del proyecto.")}
                    candidates.forEach {node->item(key=node.id) {
                        val selected=links.any {it.objectiveId==goal && it.nodeId==node.id}
                        TextButton(enabled=!busy,onClick={link(node.id,!selected)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text((if(selected) "✓ " else "")+node.title)}
                    }}
                }
                TextButton(onClick={picking=false}) {Text("Volver")}
            }
        }
    }
}


@Composable internal fun ManagementCreationDialog(nodeId:String,kind:String?,role:String?,context:String,label:String,repository:ManagementRepository,onDismiss:()->Unit) {
    var title by rememberSaveable(nodeId) {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    androidx.compose.ui.window.Dialog(onDismissRequest={if(!busy) onDismiss()},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().padding(24.dp),shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("Crear $label",style=MaterialTheme.typography.titleLarge)
                OutlinedTextField(title,{title=it},label={Text("Título")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
                error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    OutlinedButton(enabled=!busy,onClick=onDismiss) {Text("Cancelar")}
                    Spacer(Modifier.width(8.dp))
                    Button(enabled=!busy && title.isNotBlank(),onClick={
                        busy=true;scope.launch {try {
                            if(kind=="OBJECTIVES") repository.planning.createObjective(context,title)
                            else repository.cascade.create(context,nodeId,title,when {kind=="BACKLOG"->LayerRole.EPIC;role=="EPIC"->LayerRole.STORY;else->null},sprint=kind=="SPRINTS")
                            onDismiss()
                        } catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message} finally {busy=false}}
                    }) {Text("Crear")}
                }
            }
        }
    }
}
