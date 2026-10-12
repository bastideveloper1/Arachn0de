package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.r0ybt.arachn0de.data.local.PersonEntity
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.Person
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun ResponsibilitiesAccess(context:String,target:String,objective:Boolean,repository:ParticipationRepository) {
    var open by remember(context,target) {mutableStateOf(false)}
    OutlinedButton(onClick={open=true}) {Text("Responsabilidades de gestión")}
    if(open) ParticipationDialog(context,repository,target,objective) {open=false}
}

@Composable internal fun ParticipantCard(person:Person,summary:String,incorporated:Boolean,enabled:Boolean=true,modifier:Modifier=Modifier,onClick:()->Unit) {
    Card(onClick=onClick,enabled=enabled,shape=androidx.compose.ui.graphics.RectangleShape,modifier=modifier.fillMaxWidth().semantics {selected=incorporated},
        border=BorderStroke(if(incorporated) 2.dp else 1.dp,if(incorporated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            PersonPhoto(person)
            Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal=6.dp,vertical=8.dp)) {
                    Text(person.name,style=MaterialTheme.typography.labelLarge,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.testTag("person-name:${person.id}"))
                    Text(summary,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Same assignments and catalog; selection changes presentation, never the meaning of a role. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ParticipationDialog(context:String,repository:ParticipationRepository,target:String?=null,objective:Boolean=false,initialPersonId:String?=null,initialAdding:Boolean=false,onDismiss:()->Unit) {
    val state by remember(repository,context) {repository.observe(context)}.collectAsState(initial=ParticipationState())
    val rows=state.records
    var query by rememberSaveable {mutableStateOf("")}
    var adding by rememberSaveable {mutableStateOf(initialAdding)}
    var personId by rememberSaveable {mutableStateOf(initialPersonId)}
    var detail by remember {mutableStateOf<String?>(null)}
    var removing by remember {mutableStateOf<String?>(null)}
    var roleHelp by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    fun perform(action:suspend ()->Unit) {if(busy) return;busy=true;error=null;scope.launch {
        try {action()} catch(c:CancellationException) {throw c} catch(e:Exception) {error=e.message ?: "No se pudo guardar la responsabilidad."} finally {busy=false}
    }}
    fun back() {if(!busy) {if(personId!=null) personId=null else if(adding) {if(initialAdding) onDismiss() else {adding=false;query=""}} else onDismiss()}}
    val members=rows.participants.filter {it.contextId==context}.mapTo(hashSetOf()) {it.personId}
    fun count(id:String)=rows.roles.count {it.contextId==context && it.personId==id}+rows.objectives.count {it.contextId==context && it.personId==id}+rows.nodes.count {it.contextId==context && it.personId==id}
    fun person(row:PersonEntity)=Person(row.id,row.name,row.avatarFile,row.avatarZoom,row.avatarX,row.avatarY)
    fun generalRoles(id:String)=ResponsibilityRole.entries.filter {role->rows.roles.any {it.contextId==context && it.personId==id && it.role==role.name}}.joinToString {it.label}
    val eligible=state.work.nodes.filter {state.work.owners[it.id]==context && (it.purpose=="ACTION" || state.work.records.roles.any {r->r.contextId==context && r.nodeId==it.id})}
    val selected=state.people.firstOrNull {it.id==personId && it.id in members}
    Dialog(onDismissRequest={back()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        BackHandler {back()}
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(if(selected!=null) "Responsabilidades" else if(adding) "Incorporar al equipo" else if(target!=null) "Participantes del proyecto" else "Equipo",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick={roleHelp=true}) {Text("Ayuda de roles")}
            }
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            LazyVerticalGrid(columns=GridCells.Fixed(3),modifier=Modifier.weight(1f).fillMaxWidth().testTag("participation-list"),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                item(span={GridItemSpan(maxLineSpan)}) {
                    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(selected==null) {
                        Text(if(adding) "Personas del catálogo global. Las incorporadas siguen visibles y aparecen primero." else if(target!=null) "Participantes del proyecto. Toca una Persona para asignar responsabilidades sobre este elemento." else "Participantes de este proyecto. Abre una tarjeta para configurar sus responsabilidades.",style=MaterialTheme.typography.bodySmall)
                        OutlinedTextField(query,{query=it},label={Text("Buscar Personas existentes")},modifier=Modifier.fillMaxWidth())
                        if(target==null && !adding) Button(enabled=!busy,onClick={adding=true;query=""},modifier=Modifier.fillMaxWidth()) {Text("Incorporar Personas")}
                        if(target!=null) Text(state.work.objectives.firstOrNull {objective && it.id==target}?.title ?: state.work.nodes.firstOrNull {it.id==target}?.title.orEmpty())
                    } else {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {PersonAvatar(person(selected),size=48.dp);Text(selected.name,style=MaterialTheme.typography.titleMedium)}
                        OutlinedButton(onClick={detail=selected.id}) {Text("Detalle de Persona")}
                        if(target==null) {
                            Text("Roles generales del proyecto",style=MaterialTheme.typography.titleMedium)
                            Text("Función dentro del equipo. Roles actuales: "+generalRoles(selected.id).ifBlank {"Sin asignar"},style=MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {ResponsibilityRole.entries.forEach {role->
                                val assigned=rows.roles.any {it.contextId==context && it.personId==selected.id && it.role==role.name}
                                FilterChip(selected=assigned,enabled=!busy,onClick={perform {repository.role(context,selected.id,role,!assigned)}},label={Text(role.label)},modifier=Modifier.heightIn(min=48.dp))
                            }}
                            OutlinedButton(enabled=!busy,onClick={removing=selected.id}) {Text("Quitar del equipo")}
                        }
                        HorizontalDivider(Modifier.padding(vertical=12.dp))
                        Text("Responsabilidades sobre el trabajo",style=MaterialTheme.typography.titleMedium)
                        Text("Participación en cada resultado o elemento. No modifica los roles generales del proyecto.",style=MaterialTheme.typography.bodySmall)
                    }
                }
                }
                if(selected==null) {
                    val people=state.people.filter {it.name.contains(query.trim(),true) && (adding && target==null || it.id in members)}
                        .sortedWith(compareBy<PersonEntity> {it.id !in members}.thenBy {it.name.lowercase()}.thenBy {it.id})
                    if(people.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {Text(if(adding) "No hay Personas en el catálogo que coincidan. Registra Personas en el catálogo global para incorporarlas aquí." else if(members.isEmpty()) "Sin participantes. Incorpora Personas del catálogo para formar el equipo." else "Sin coincidencias.")}
                    items(people,key={it.id}) {row->
                        val member=row.id in members
                        val summary=if(member) "Incorporada · "+if(count(row.id)==0) "Sin responsabilidad asignada" else "${count(row.id)} roles y responsabilidades" else "Disponible · Toca para incorporar"
                        ParticipantCard(person(row),summary,member,!busy) {if(member) personId=row.id else perform {repository.add(context,row.id)}}
                    }
                } else {
                    val targets=(if(target!=null) listOf(target to objective) else state.work.objectives.filter {it.contextId==context}.map {it.id to true}+eligible.map {it.id to false}).distinct()
                    if(targets.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {Text("No hay elementos gestionados disponibles.")}
                    items(targets,key={"${it.second}:${it.first}"},span={GridItemSpan(maxLineSpan)}) {(id,goal)->
                        val title=if(goal) state.work.objectives.firstOrNull {it.id==id}?.title else state.work.nodes.firstOrNull {it.id==id}?.title
                        Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(title.orEmpty(),style=MaterialTheme.typography.titleMedium)
                            Text(if(goal) "Objetivo" else state.work.records.roles.firstOrNull {it.nodeId==id}?.role?.let {LayerRole.valueOf(it).label} ?: "Tarea",style=MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {ResponsibilityRole.entries.forEach {role->
                                val assigned=if(goal) rows.objectives.any {it.contextId==context && it.personId==selected.id && it.objectiveId==id && it.role==role.name} else rows.nodes.any {it.contextId==context && it.personId==selected.id && it.nodeId==id && it.role==role.name}
                                FilterChip(selected=assigned,enabled=!busy,onClick={perform {repository.assign(context,selected.id,id,goal,role,!assigned)}},label={Text(role.label)},modifier=Modifier.heightIn(min=48.dp))
                            }}
                        }}
                    }
                }
            }
            OutlinedButton(enabled=!busy,onClick={back()},modifier=Modifier.fillMaxWidth()) {Text(if(selected!=null) "Volver al equipo" else if(adding) "Volver al equipo" else "Cerrar")}
        }}
    }
    if(roleHelp) ManagementHelpScreen("Roles y responsabilidades",responsibilityHelpParagraphs) {roleHelp=false}
    removing?.let {id->AlertDialog(onDismissRequest={if(!busy) removing=null},title={Text("Quitar del equipo")},text={Text("Se retirarán ${count(id)} roles y responsabilidades de esta persona en este proyecto. Su ficha global y todos los elementos de trabajo se conservarán.")},confirmButton={TextButton(enabled=!busy,onClick={perform {repository.remove(context,id,true);removing=null;personId=null}}) {Text("Confirmar retirada")}},dismissButton={TextButton(enabled=!busy,onClick={removing=null}) {Text("Cancelar")}})}
    detail?.let {id->PersonDetailDialog(id) {detail=null}}
}
