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

@Composable internal fun PersonTechnologiesSection(person:String,repository:PersonRepository,openOnEntry:Boolean=false,openRequest:Int=0) {
    val catalog by remember(repository) {repository.observeTechnologyCatalog()}.collectAsState(initial=null)
    val all by remember(repository) {repository.observePersonTechnologies()}.collectAsState(initial=null)
    var choosing by remember {mutableStateOf(false)};var selected by remember {mutableStateOf<String?>(null)}
    var query by rememberSaveable(person) {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    fun perform(action:suspend ()->Unit) {if(busy) return;busy=true;scope.launch {try {action();selected=null} catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message ?: "No se pudo guardar."} finally {busy=false}}}
    LaunchedEffect(person,openRequest,catalog!=null && all!=null) {if(openOnEntry && catalog!=null && all!=null) choosing=true}
    val assigned=all.orEmpty().filter {it.personId==person}.associateBy {it.technologyId}
    Column(Modifier.fillMaxSize()) {
        if(!openOnEntry) TextButton(enabled=!busy && catalog!=null && all!=null,onClick={choosing=true}) {Text("Asociar Tecnología")}
        if(catalog==null || all==null) Text("Cargando Tecnologías…")
        else if(assigned.isEmpty()) Text("Sin Tecnologías asociadas.")
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val scale=androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
            val columns=((maxWidth.value+8)/(96*scale+8)).toInt().coerceIn(1,3)
            LazyVerticalGrid(GridCells.Fixed(columns),Modifier.fillMaxSize().testTag("person-technology-grid"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(catalog.orEmpty().filter {it.id in assigned},key={it.id}) {tech->
                    Card(enabled=!busy,onClick={selected=tech.id},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("person-technology:${tech.id}"),
                        shape=MaterialTheme.shapes.medium,colors=CardDefaults.cardColors(containerColor=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Surface)) {
                        Column(Modifier.fillMaxWidth().padding(8.dp),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            TechnologyIcon(tech,36.dp)
                            Text(tech.name,maxLines=3,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.bodySmall)
                            Text(PersonProficiency.valueOf(assigned.getValue(tech.id).proficiency).label,style=MaterialTheme.typography.labelSmall,
                                textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    if(choosing) ModularPanelDialog(onDismissRequest={choosing=false},
        title={Text("Tecnologías existentes")},compact=true,plainBody=true,fieldOwnsScroll=true,
        foldLabel="Tecnologías",
        text={
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(query,{query=it},label={Text("Buscar Tecnologías")},singleLine=true,modifier=Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f,fill=false).fillMaxWidth().testTag("person-technology-selector")) {
                    val available=catalog.orEmpty().filter {it.id !in assigned && it.name.contains(query,true)}
                    if(available.isEmpty()) item {Text(when {
                        query.isNotBlank()->"Sin coincidencias disponibles."
                        catalog.orEmpty().isEmpty()->"No hay Tecnologías en el catálogo."
                        else->"Todas las Tecnologías ya están asociadas."
                    })}
                    items(available,key={it.id}) {t->TextButton(onClick={selected=t.id;choosing=false},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {TechnologyIcon(t);Spacer(Modifier.width(8.dp));Text(t.name,Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Start)}}
                }
            }
        },dismissButton={PrimaryControlButton(onClick={choosing=false},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Cancelar")}},
        confirmButton={})
    selected?.let {id->val tech=catalog.orEmpty().firstOrNull {it.id==id};if(tech!=null) {
        var level by rememberSaveable(id) {mutableStateOf(assigned[id]?.proficiency ?: "UNEVALUATED")}
        ModularPanelDialog(onDismissRequest={if(!busy) selected=null},compact=true,plainBody=true,foldLabel="Tecnologías",foldEnabled=!busy,title={Text(tech.name)},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())) {TechnologyIcon(tech,40.dp);Text("Dominio declarado por la Persona");PersonProficiency.entries.forEach {p->TextButton(enabled=!busy,onClick={level=p.name},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("${if(level==p.name) "✓ " else ""}${p.label}")}}
            if(id in assigned) TextButton(enabled=!busy,onClick={perform {repository.removeTechnology(person,id)}}) {Text("Retirar asociación")}
        }},confirmButton={PrimaryControlButton(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!busy,onClick={perform {repository.saveTechnology(person,id,PersonProficiency.valueOf(level))}}) {Text("Guardar dominio")}},dismissButton={PrimaryControlButton(modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!busy,onClick={selected=null}) {Text("Cancelar")}})
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
