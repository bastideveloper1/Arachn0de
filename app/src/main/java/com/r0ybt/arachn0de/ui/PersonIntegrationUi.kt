package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.data.repository.ResponsibilityRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class PersonDestination(val personId:String,val projectId:String,val nodeId:String?=null)
internal val LocalPersonNavigation=staticCompositionLocalOf<((PersonDestination)->Unit)?> {null}
internal val personKinds=linkedMapOf("ALL" to "Todos los tipos","ACTION" to "Tareas","NOTE" to "Notas","LAYER" to "Capas","SPRINT" to "Sprints","EPIC" to "Épicas","STORY" to "Historias","OBJECTIVE" to "Objetivos")
internal fun personRole(role:String)=when(role) {"TRADITIONAL"->"Responsable tradicional";"MEMBER"->"Participante";else->ResponsibilityRole.entries.firstOrNull {it.name==role}?.label ?: role}
internal fun filterPersonWork(rows:List<PersonWorkRow>,kind:String,state:String)=rows.filter {it.kind!="PROJECT" && (kind=="ALL" || it.kind==kind) && when(state) {"PENDING"->it.completed==false;"COMPLETED"->it.completed==true;else->true}}

@Composable internal fun PersonTechnologiesSection(person:String,repository:PersonRepository) {
    val catalog by remember(repository) {repository.observeTechnologyCatalog()}.collectAsState(initial=null)
    val all by remember(repository) {repository.observePersonTechnologies()}.collectAsState(initial=null)
    var choosing by remember {mutableStateOf(false)};var selected by remember {mutableStateOf<String?>(null)}
    var query by rememberSaveable(person) {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    fun perform(action:suspend ()->Unit) {if(busy) return;busy=true;scope.launch {try {action();selected=null} catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message ?: "No se pudo guardar."} finally {busy=false}}}
    val assigned=all.orEmpty().filter {it.personId==person}.associateBy {it.technologyId}
    Column(Modifier.fillMaxSize()) {
        TextButton(enabled=!busy && catalog!=null && all!=null,onClick={choosing=true}) {Text("Asociar Tecnología")}
        if(catalog==null || all==null) Text("Cargando Tecnologías…")
        else if(assigned.isEmpty()) Text("Sin Tecnologías asociadas.")
        LazyVerticalGrid(GridCells.Adaptive(96.dp),Modifier.weight(1f).testTag("person-technology-grid"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(catalog.orEmpty().filter {it.id in assigned},key={it.id}) {tech->OutlinedButton(enabled=!busy,onClick={selected=tech.id},modifier=Modifier.heightIn(min=88.dp),contentPadding=PaddingValues(8.dp)) {Column {TechnologyIcon(tech,36.dp);Text(tech.name);Text("Dominio: ${PersonProficiency.valueOf(assigned.getValue(tech.id).proficiency).label}",style=MaterialTheme.typography.labelSmall)}}}
        }
    }
    if(choosing) Dialog(onDismissRequest={choosing=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.8f).padding(12.dp),shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp)) {
                Text("Tecnologías existentes",style=MaterialTheme.typography.titleLarge)
                OutlinedTextField(query,{query=it},label={Text("Buscar Tecnologías")},modifier=Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    val available=catalog.orEmpty().filter {it.id !in assigned && it.name.contains(query,true)}
                    if(available.isEmpty()) item {Text("Sin coincidencias disponibles.")}
                    items(available,key={it.id}) {t->TextButton(onClick={selected=t.id;choosing=false},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {TechnologyIcon(t);Spacer(Modifier.width(8.dp));Text(t.name)}}
                }
                TextButton(onClick={choosing=false}) {Text("Cancelar")}
            }
        }
    }
    selected?.let {id->val tech=catalog.orEmpty().firstOrNull {it.id==id};if(tech!=null) {
        var level by rememberSaveable(id) {mutableStateOf(assigned[id]?.proficiency ?: "UNEVALUATED")}
        AlertDialog(onDismissRequest={if(!busy) selected=null},title={Text(tech.name)},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())) {TechnologyIcon(tech,40.dp);Text("Dominio declarado por la Persona");PersonProficiency.entries.forEach {p->TextButton(enabled=!busy,onClick={level=p.name},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("${if(level==p.name) "✓ " else ""}${p.label}")}}
            if(id in assigned) TextButton(enabled=!busy,onClick={perform {repository.removeTechnology(person,id)}}) {Text("Retirar asociación")}
        }},confirmButton={TextButton(enabled=!busy,onClick={perform {repository.saveTechnology(person,id,PersonProficiency.valueOf(level))}}) {Text("Guardar dominio")}},dismissButton={TextButton(enabled=!busy,onClick={selected=null}) {Text("Cancelar")}})
    } else AlertDialog(onDismissRequest={selected=null},text={Text("La Tecnología ya no está en el catálogo.")},confirmButton={TextButton(onClick={selected=null}) {Text("Cerrar")}})}
    error?.let {AlertDialog(onDismissRequest={error=null},text={Text(it)},confirmButton={TextButton(onClick={error=null}) {Text("Cerrar")}})}
}

@Composable private fun PersonFilter(label:String,selected:String,options:Map<String,String>,onChange:(String)->Unit) {
    var open by remember {mutableStateOf(false)}
    Box {TextButton(onClick={open=true}) {Text("$label: ${options[selected]}")};DropdownMenu(open,{open=false}) {options.forEach {(key,value)->DropdownMenuItem(text={Text(value)},onClick={onChange(key);open=false})}}}
}
@Composable internal fun PersonWorkSection(person:String,repository:PersonRepository) {
    val rows by remember(repository,person) {repository.observeWork(person)}.collectAsState(initial=null)
    var kind by rememberSaveable(person) {mutableStateOf("ALL")};var state by rememberSaveable(person) {mutableStateOf("ALL")}
    var project by rememberSaveable(person) {mutableStateOf("")}
    var goal by remember {mutableStateOf<PersonWorkRow?>(null)}
    val navigate=LocalPersonNavigation.current
    val groups=rows.orEmpty().groupBy {it.projectId}
    LaunchedEffect(rows,project) {if(rows!=null && project.isNotEmpty() && project !in groups) project=""}
    Column(Modifier.fillMaxSize()) {
        Text("Asignaciones y responsabilidades; no acreditan trabajo realizado.",style=MaterialTheme.typography.bodySmall)
        PersonFilter("Proyecto",project,mapOf("" to "Todos los proyectos")+groups.mapValues {it.value.first().projectName}) {project=it}
        Column {PersonFilter("Tipo",kind,personKinds) {kind=it};PersonFilter("Estado",state,mapOf("ALL" to "Todos","PENDING" to "Pendiente","COMPLETED" to "Completada")) {state=it}}
        if(rows==null) Text("Cargando trabajo…") else if(groups.isEmpty()) Text("Sin proyectos ni trabajo asociados.")
        LazyColumn(Modifier.weight(1f).testTag("person-work-list"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            groups.filterKeys {project.isEmpty() || it==project}.forEach {(id,all)->
                item(key="project:$id") {Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(10.dp)) {
                    TextButton(enabled=navigate!=null,onClick={navigate?.invoke(PersonDestination(person,id))}) {Text("Abrir proyecto: ${all.first().projectName}")}
                    all.filter {it.kind=="PROJECT"}.forEach {r->Text("${personRole(r.role)} · ${r.scopeName} · ${if(r.contextEnabled==false) "Gestión desactivada" else "Gestión"}")}
                }}}
                val work=filterPersonWork(all,kind,state).groupBy {it.kind to it.targetId}
                if(work.isEmpty()) item {Text("Sin elementos para estos filtros.",style=MaterialTheme.typography.bodySmall)}
                items(work.values.toList(),key={"${it.first().projectId}:${it.first().kind}:${it.first().targetId}"}) {responsibilities->val row=responsibilities.first()
                    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(10.dp)) {
                        Text(personKinds[row.kind]?.removeSuffix("s") ?: row.kind,style=MaterialTheme.typography.labelLarge)
                        TextButton(enabled=row.kind=="OBJECTIVE" || navigate!=null,onClick={if(row.kind=="OBJECTIVE") goal=row else navigate?.invoke(PersonDestination(person,row.projectId,row.nodeId))}) {Text(row.title)}
                        responsibilities.map {personRole(it.role)+(it.scopeName?.let {scope->" · $scope"} ?: "")+(if(it.contextEnabled==false) " · Gestión desactivada" else "")}.distinct().forEach {Text(it)}
                        row.completed?.let {Text(if(it) "Completada" else "Pendiente")};row.phase?.let {Text("Fase Sprint: ${com.r0ybt.arachn0de.domain.model.WorkState.entries.firstOrNull {p->p.name==it}?.label ?: it}")}
                    }}
                }
            }
        }
    }
    goal?.let {row->val app=LocalContext.current.applicationContext as Arachn0deApplication
        PlanningDialog(row.contextId!!,row.scopeName ?: row.projectName,"",app.nodeRepository.management.planning,initialObjectiveId=row.targetId) {goal=null}
    }
}
