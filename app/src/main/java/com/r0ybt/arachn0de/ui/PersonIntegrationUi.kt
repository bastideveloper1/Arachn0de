package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
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
import androidx.compose.ui.draw.clip
import com.r0ybt.arachn0de.domain.model.Person
import androidx.compose.ui.graphics.vector.ImageVector
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
internal val personKinds=linkedMapOf("ALL" to "Todos los tipos","ACTION" to "Tareas","NOTE" to "Notas","LAYER" to "Capas")
internal fun personRole(role:String)=when(role) {"TRADITIONAL"->"Responsable tradicional";"MEMBER"->"Participante";else->ResponsibilityRole.entries.firstOrNull {it.name==role}?.label ?: role}
internal fun filterPersonWork(rows:List<PersonWorkRow>,kind:String,state:String)=rows.filter {it.kind in setOf("ACTION","NOTE","LAYER") && (kind=="ALL" || it.kind==kind) && when(state) {"PENDING"->it.completed==false;"COMPLETED"->it.completed==true;else->true}}

@Composable internal fun PersonTechnologiesSection(person:String,repository:PersonRepository,openOnEntry:Boolean=false,openRequest:Int=0,panelVisible:Boolean=true,contentModifier:Modifier=Modifier,menuFooter:(@Composable ()->Unit)?=null) {
    val catalog by remember(repository) {repository.observeTechnologyCatalog()}.collectAsState(initial=null)
    val all by remember(repository) {repository.observePersonTechnologies()}.collectAsState(initial=null)
    var choosing by rememberSaveable(person) {mutableStateOf(false)};var selected by rememberSaveable(person) {mutableStateOf<String?>(null)}
    var query by rememberSaveable(person) {mutableStateOf("")}
    var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    fun perform(action:suspend ()->Unit) {if(busy) return;busy=true;scope.launch {try {action();selected=null} catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message ?: "No se pudo guardar."} finally {busy=false}}}
    var lastRequest by rememberSaveable(person) {mutableIntStateOf(openRequest)}
    LaunchedEffect(openRequest) {if(openRequest!=lastRequest) {choosing=false;selected=null;lastRequest=openRequest}}
    val assigned=all.orEmpty().filter {it.personId==person}.associateBy {it.technologyId}
    Column(Modifier.fillMaxSize()) {
        if(catalog==null || all==null) Text("Cargando Tecnologías…",modifier=contentModifier)
        else if(assigned.isEmpty()) Text("Sin Tecnologías asociadas.",modifier=contentModifier)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().then(contentModifier)) {
            val scale=androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
            val columns=((maxWidth.value+8)/(96*scale+8)).toInt().coerceIn(1,3)
            LazyVerticalGrid(GridCells.Fixed(columns),Modifier.fillMaxSize().testTag("person-technology-grid"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(catalog.orEmpty().filter {it.id in assigned},key={it.id}) {tech->
                    Card(enabled=!busy,onClick={selected=tech.id},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("person-technology:${tech.id}"),
                        shape=MaterialTheme.shapes.medium,colors=CardDefaults.cardColors(containerColor=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Surface)) {
                        Column(Modifier.fillMaxWidth().padding(8.dp),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            TechnologyIcon(tech,36.dp)
                            Text(tech.name,maxLines=3,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.bodySmall)
                            if(assigned.getValue(tech.id).proficiency!="UNEVALUATED") Text(PersonProficiency.valueOf(assigned.getValue(tech.id).proficiency).label,style=MaterialTheme.typography.labelSmall,
                                textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
        BackHandler(enabled=panelVisible && (choosing || selected!=null)) {if(!busy) {if(selected!=null) selected=null else choosing=false}}
        val technology=catalog.orEmpty().firstOrNull {it.id==selected}
        var level by rememberSaveable(person,selected) {mutableStateOf(assigned[selected]?.proficiency ?: "UNEVALUATED")}
        if(panelVisible) ModularPanelSurface(Modifier.fillMaxWidth().testTag("person-technology-panel"),shadowElevation=0.dp) {
            ModularPanelLayout(compact=true,outlinedBody=false,headerDivider=false,
                showActions=choosing || selected!=null,fieldOwnsScroll=choosing,
                header={Text(technology?.name ?: if(choosing) "Agregar Tecnología" else "Tecnologías",style=MaterialTheme.typography.titleMedium)},
                actions={
                    ModularPanelActions(cancel={PrimaryControlButton(enabled=!busy,onClick={if(selected!=null) selected=null else choosing=false},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Cancelar")}},confirm={
                        if(technology!=null) PrimaryControlButton(enabled=!busy,onClick={val id=technology.id;perform {repository.saveTechnology(person,id,PersonProficiency.valueOf(level));choosing=false}},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Guardar dominio")}
                    })
                }) {
                if(selected!=null && technology!=null) key(selected) {
                    TechnologyIcon(technology,20.dp)
                    Text("Dominio declarado por la Persona",style=MaterialTheme.typography.bodySmall)
                    PersonProficiency.entries.forEach {p->TextButton(enabled=!busy,onClick={level=p.name},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("${if(level==p.name) "✓ " else ""}${p.label}")}}
                    if(selected in assigned) TextButton(enabled=!busy,onClick={val id=technology.id;perform {repository.removeTechnology(person,id);choosing=false}}) {Text("Retirar asociación")}
                } else if(choosing) {
                    OutlinedTextField(query,{query=it},label={Text("Buscar Tecnologías")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
                    val available=catalog.orEmpty().filter {it.id !in assigned && it.name.contains(query,true)}
                    LazyColumn(Modifier.heightIn(max=280.dp).fillMaxWidth().testTag("person-technology-selector")) {
                        if(available.isEmpty()) item {Text(when {
                            query.isNotBlank()->"Sin coincidencias disponibles."
                            catalog.orEmpty().isEmpty()->"No hay Tecnologías en el catálogo."
                            else->"Todas las Tecnologías ya están asociadas."
                        },style=MaterialTheme.typography.bodySmall)}
                        items(available,key={it.id}) {t->TextButton(enabled=!busy,onClick={selected=t.id},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {TechnologyIcon(t,20.dp);Spacer(Modifier.width(8.dp));Text(t.name,Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Start)}}
                    }
                } else {
                    ModularFieldMenu(listOf(ModularPanelOption("Agregar Tecnología",Icons.Default.Add,"person-add-technology",!busy && catalog!=null && all!=null) {choosing=true}),compact=true)
                    menuFooter?.invoke()
                }
            }
        }
    }

    error?.let {AlertDialog(onDismissRequest={error=null},text={Text(it)},confirmButton={TextButton(onClick={error=null}) {Text("Cerrar")}})}
}

@Composable private fun PersonFilter(label:String,selected:String,options:Map<String,String>,onChange:(String)->Unit) {
    var open by remember {mutableStateOf(false)}
    Box {TextButton(onClick={open=true}) {Text("$label: ${options[selected]}")};DropdownMenu(open,{open=false}) {options.forEach {(key,value)->DropdownMenuItem(text={Text(value)},onClick={onChange(key);open=false})}}}
}
@Composable internal fun PersonWorkSection(person:String,repository:PersonRepository) {
    val app=LocalContext.current.applicationContext as Arachn0deApplication
    val participants=app.database.participationDao()
    val people by remember(repository) {repository.observePeople()}.collectAsState(initial=emptyList())
    val assigned by remember(repository) {repository.observeAllAssignments()}.collectAsState(initial=emptyMap())
    val nodeRoles by remember(participants) {participants.observeNodes()}.collectAsState(initial=emptyList())
    val goalRoles by remember(participants) {participants.observeObjectives()}.collectAsState(initial=emptyList())
    val projectRoles by remember(participants) {participants.observeRoles()}.collectAsState(initial=emptyList())
    fun responsible(row:PersonWorkRow):Person? {
        if(row.kind!="PROJECT") assigned[row.nodeId]?.firstOrNull()?.let {return it}
        val id=when(row.kind) {
            "PROJECT"->projectRoles.firstOrNull {it.contextId==row.contextId && it.role=="PROJECT_LEAD"}?.personId
                ?: projectRoles.firstOrNull {it.contextId==row.contextId}?.personId
            "OBJECTIVE"->goalRoles.firstOrNull {it.contextId==row.contextId && it.objectiveId==row.targetId}?.personId
            else->nodeRoles.firstOrNull {it.contextId==row.contextId && it.nodeId==row.nodeId}?.personId
        }
        return people.firstOrNull {it.id==id}
    }
    val rows by remember(repository,person) {repository.observeWork(person)}.collectAsState(initial=null)
    var kind by rememberSaveable(person) {mutableStateOf("ALL")};var state by rememberSaveable(person) {mutableStateOf("ALL")}
    var project by rememberSaveable(person) {mutableStateOf("")}
    var goal by remember {mutableStateOf<PersonWorkRow?>(null)}
    val navigate=LocalPersonNavigation.current
    val groups=rows.orEmpty().groupBy {it.projectId}
    LaunchedEffect(rows,project) {if(rows!=null && project.isNotEmpty() && project !in groups) project=""}
    Column(Modifier.fillMaxSize()) {
        PersonFilter("Proyecto",project,mapOf("" to "Todos los proyectos")+groups.mapValues {it.value.first().projectName}) {project=it}
        Column {PersonFilter("Tipo",kind,personKinds) {kind=it};PersonFilter("Estado",state,mapOf("ALL" to "Todos","PENDING" to "Pendiente","COMPLETED" to "Completada")) {state=it}}
        if(rows==null) Text("Cargando trabajo…") else if(groups.isEmpty()) Text("Sin proyectos ni trabajo asociados.")
        LazyColumn(Modifier.weight(1f).testTag("person-work-list"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            groups.filterKeys {project.isEmpty() || it==project}.forEach {(id,all)->
                item(key="project:$id") {
                    val roots=all.filter {it.kind=="PROJECT"}
                    PersonWorkPreview(all.first().projectName,"Núcleo",roots,roots.firstOrNull()?.let(::responsible),Icons.Default.Layers,
                        Modifier.testTag("person-work-project:$id"),navigate!=null) {navigate?.invoke(PersonDestination(person,id))}
                }
                val work=filterPersonWork(all,kind,state).groupBy {it.kind to it.targetId}
                if(work.isEmpty()) item {Text("Sin elementos para estos filtros.",style=MaterialTheme.typography.bodySmall)}
                items(work.values.toList(),key={"${it.first().projectId}:${it.first().kind}:${it.first().targetId}"}) {responsibilities->val row=responsibilities.first()
                    val type=when(row.kind) {"ACTION"->"Tarea";"NOTE"->"Nota";"LAYER"->"Capa";"SPRINT"->"Sprint";"EPIC"->"Épica";"STORY"->"Historia";"OBJECTIVE"->"Objetivo";else->row.kind}
                    val icon=when(row.kind) {"NOTE"->Icons.Default.Description;"LAYER","EPIC","STORY"->Icons.Default.Layers;"OBJECTIVE"->Icons.Default.Flag;"SPRINT"->Icons.Default.DateRange;else->Icons.Default.CheckBoxOutlineBlank}
                    PersonWorkPreview(row.title,type,responsibilities,responsible(row),icon,Modifier.testTag("person-work-item:${row.targetId}"),row.kind=="OBJECTIVE" || navigate!=null) {
                        if(row.kind=="OBJECTIVE") goal=row else navigate?.invoke(PersonDestination(person,row.projectId,row.nodeId))
                    }
                }
            }
        }
    }
    goal?.let {row->val app=LocalContext.current.applicationContext as Arachn0deApplication
        PlanningDialog(row.contextId!!,row.scopeName ?: row.projectName,"",app.nodeRepository.management.planning,initialObjectiveId=row.targetId) {goal=null}
    }
}

/** Same charcoal Card, spacing and rounded avatar used in existing compact previews. */
@Composable private fun PersonWorkPreview(title:String,type:String,rows:List<PersonWorkRow>,responsible:Person?,icon:ImageVector,
    modifier:Modifier=Modifier,enabled:Boolean=true,onOpen:()->Unit) {
    Card(onClick=onOpen,enabled=enabled,modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(8.dp),
        colors=CardDefaults.cardColors(containerColor=Arachn0deColors.Surface),border=BorderStroke(1.dp,Arachn0deColors.Outline)) {
        Row(Modifier.fillMaxWidth().heightIn(min=88.dp).padding(horizontal=12.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),contentAlignment=Alignment.Center) {
                if(responsible!=null) PersonAvatar(responsible,40.dp,rounded=true)
                else Icon(icon,type,Modifier.size(28.dp),tint=Arachn0deColors.TextSecondary)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                val status=rows.firstOrNull()?.completed?.let {if(it) "Completada" else "Pendiente"}
                Text(listOfNotNull(type,status).joinToString(" · "),style=MaterialTheme.typography.labelSmall,color=Arachn0deColors.TextSecondary)
                val roles=rows.map {personRole(it.role)}.distinct()
                if(roles.isNotEmpty()) Text(roles.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary,maxLines=2,overflow=TextOverflow.Ellipsis)
                val context=rows.mapNotNull {it.scopeName}.filter {it.isNotBlank() && it!=title}.distinct()
                if(context.isNotEmpty()) Text(context.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(rows.any {it.contextEnabled==false}) Text("Gestión desactivada",style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary)
                rows.firstOrNull()?.phase?.let {phase->Text("Fase Sprint: ${com.r0ybt.arachn0de.domain.model.WorkState.entries.firstOrNull {it.name==phase}?.label ?: phase}",style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary,maxLines=1,overflow=TextOverflow.Ellipsis)}
            }
        }
    }
}
