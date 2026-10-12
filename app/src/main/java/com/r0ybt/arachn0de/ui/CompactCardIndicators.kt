package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.ui.theme.BlackWhite90
import com.r0ybt.arachn0de.data.repository.TechnologyState
import com.r0ybt.arachn0de.metro.*
import java.util.TimeZone

/** Presentation only; distinct identities include direct assignments and descendants. */
internal data class CompactCardFacts(val people:Set<String> = emptySet(),val technologies:Set<String> = emptySet(),
    val journeys:Set<String> = emptySet(),val due:Boolean=false,val today:Boolean=false,val high:Boolean=false,
    val notes:Set<String> = emptySet(),val tasks:Set<String> = emptySet(),val files:Set<String> = emptySet(),
    val deadlines:Set<String> = emptySet(),val priorities:Set<String> = emptySet(),val dueNodes:Set<String> = emptySet(),val todayNodes:Set<String> = emptySet()) {
    val empty get()=people.isEmpty() && technologies.isEmpty() && journeys.isEmpty() && !due && !today && !high && notes.isEmpty() && tasks.isEmpty() && files.isEmpty()
    operator fun plus(other:CompactCardFacts)=CompactCardFacts(people+other.people,technologies+other.technologies,journeys+other.journeys,due||other.due,today||other.today,high||other.high,notes+other.notes,tasks+other.tasks,files+other.files,deadlines+other.deadlines,priorities+other.priorities,dueNodes+other.dueNodes,todayNodes+other.todayNodes)
}
internal val LocalCompactCardFacts=staticCompositionLocalOf<Map<String,CompactCardFacts>> {emptyMap()}
internal fun compactNodeFacts(node:Node,now:Long,zone:TimeZone=TimeZone.getDefault(),pendingDescendants:Boolean=false):CompactCardFacts {
    val pending=(node.isCompletable && !node.isCompleted) || (node.isStructural && pendingDescendants)
    return CompactCardFacts(due=node.dueAt!=null,today=pending && node.dueAt?.let {CalendarDates.localDay(it,zone)==CalendarDates.localDay(now,zone)}==true,
        high=pending && node.effectivePriority==Priority.HIGH,
        notes=if(node.purpose==NodePurpose.NOTE) setOf(node.id) else emptySet(),
        files=if(AttachmentReferences.ids(node.description).isNotEmpty()) setOf(node.id) else emptySet(),
        deadlines=if(pending && node.dueAt?.let {it<=now+24*60*60*1000L}==true) setOf(node.id) else emptySet(),
        priorities=if(pending && node.effectivePriority==Priority.HIGH) setOf(node.id) else emptySet(),
        dueNodes=if(node.dueAt!=null) setOf(node.id) else emptySet(),
        todayNodes=if(pending && node.dueAt?.let {CalendarDates.localDay(it,zone)==CalendarDates.localDay(now,zone)}==true) setOf(node.id) else emptySet())
}
internal fun compactCardFacts(nodes:List<Node>,people:Map<String,List<Person>>,technologies:TechnologyState,
    metro:MetroSnapshot?,now:Long,zone:TimeZone=TimeZone.getDefault(),participation:com.r0ybt.arachn0de.data.repository.ParticipationState?=null):Map<String,CompactCardFacts> {
    val result=mutableMapOf<String,CompactCardFacts>();val byId=nodes.associateBy {it.id}
    val pendingAncestors=hashSetOf<String>()
    nodes.filter {it.isCompletable && !it.isCompleted}.forEach {task->
        var parent=task.parentId?.let(byId::get)
        while(parent!=null && parent.projectId==task.projectId && pendingAncestors.add(parent.id)) parent=parent.parentId?.let(byId::get)
    }
    val journeys=metro?.journeys.orEmpty().filter {it.row.nodeId!=null}.groupBy {it.row.nodeId}
    fun add(key:String,facts:CompactCardFacts) {result[key]=(result[key] ?: CompactCardFacts())+facts}
    val participantNodes=mutableMapOf<String,Set<String>>()
    participation?.records?.nodes.orEmpty().forEach {row->participantNodes[row.nodeId]=participantNodes[row.nodeId].orEmpty()+row.personId}
    val relatedTasks=participation?.work?.records?.storyTasks.orEmpty().groupBy {it.storyId}.mapValues {(_,rows)->rows.mapTo(hashSetOf()) {it.taskId}}
    val members=participation?.records?.participants.orEmpty().groupBy {it.contextId}
    participation?.work?.roots.orEmpty().forEach {root->
        val ids=members[root.id].orEmpty().mapTo(hashSetOf()) {it.personId}
        root.projectId?.let {add("project:$it",CompactCardFacts(people=ids))}
        root.nodeId?.let {participantNodes[it]=participantNodes[it].orEmpty()+ids}
    }
    technologies.projectIds.forEach {(id,ids)->add("project:$id",CompactCardFacts(technologies=ids))}
    nodes.forEach {node->
        val facts=compactNodeFacts(node,now,zone,node.id in pendingAncestors).copy(people=people[node.id].orEmpty().mapTo(hashSetOf()) {it.id}+participantNodes[node.id].orEmpty(),
            technologies=technologies.nodeIds[node.id].orEmpty(),tasks=relatedTasks[node.id].orEmpty(),journeys=journeys[node.id].orEmpty().mapTo(hashSetOf()) {it.row.id})
        var current:Node?=node;val visited=hashSetOf<String>()
        while(current!=null && visited.add(current.id)) {
            add("node:${current.id}",facts.copy(tasks=facts.tasks+if(current.id!=node.id && node.purpose==NodePurpose.ACTION) setOf(node.id) else emptySet()))
            current=current.parentId?.let(byId::get)?.takeIf {it.projectId==node.projectId}
        }
        add("project:${node.projectId}",facts.copy(tasks=facts.tasks+if(node.purpose==NodePurpose.ACTION) setOf(node.id) else emptySet()))
    }
    return result
}

internal enum class IndicatorTone { PEOPLE, CONTENT, ALERT }
internal data class CardIndicator(val name:String,val icon:ImageVector,val label:String,val tone:IndicatorTone,val ids:Set<String> = emptySet())
internal fun cardIndicators(f:CompactCardFacts):List<CardIndicator> = buildList {
    if(f.people.isNotEmpty()) add(CardIndicator("people",if(f.people.size==1) Icons.Default.Person else Icons.Default.Groups,"Personas",IndicatorTone.PEOPLE,f.people))
    if(f.technologies.isNotEmpty()) add(CardIndicator("technologies",Icons.Default.Build,"Tecnologías",IndicatorTone.CONTENT,f.technologies))
    if(f.notes.isNotEmpty()) add(CardIndicator("notes",Icons.Default.Description,"Notas",IndicatorTone.CONTENT,f.notes))
    if(f.tasks.isNotEmpty()) add(CardIndicator("tasks",Icons.Default.Checklist,"Tareas",IndicatorTone.CONTENT,f.tasks))
    if(f.files.isNotEmpty()) add(CardIndicator("files",Icons.Default.AttachFile,"Archivos en el contenido",IndicatorTone.CONTENT,f.files))
    if(f.due) add(CardIndicator("due",Icons.Default.DateRange,if(f.deadlines.isEmpty()) "Vencimiento" else "Vencimiento próximo o vencido",if(f.deadlines.isEmpty()) IndicatorTone.CONTENT else IndicatorTone.ALERT,if(f.deadlines.isEmpty()) f.dueNodes else f.deadlines))
    if(f.today) add(CardIndicator("today",Icons.Default.HourglassBottom,"Pendientes para hoy",IndicatorTone.ALERT,f.todayNodes))
    if(f.high) add(CardIndicator("high",Icons.Default.LocalFireDepartment,"Prioridad alta",IndicatorTone.ALERT,f.priorities))
}
internal val optionalAssociationIndicator=CardIndicator("associations",Icons.Default.Link,"Asociaciones opcionales",IndicatorTone.CONTENT)

@Composable internal fun indicatorColor(tone:IndicatorTone):Color = Arachn0deColors.TextPrimary

internal data class IndicatorDirectory(val nodes:Map<String,Node> = emptyMap(),val people:Map<String,Person> = emptyMap(),val technologies:Map<String,com.r0ybt.arachn0de.data.local.TechnologyEntity> = emptyMap(),val journeyNodes:Map<String,String> = emptyMap())
internal fun indicatorOrigin(key:String,node:Node,nodes:Map<String,Node>):String {
    if(key=="node:${node.id}") return "Propio"
    if(key=="project:${node.projectId}") return "Descendiente"
    val visited=hashSetOf<String>()
    var parent=node.parentId?.let(nodes::get)
    while(parent!=null && parent.projectId==node.projectId && visited.add(parent.id)) {
        if(key=="node:${parent.id}") return "Descendiente"
        parent=parent.parentId?.let(nodes::get)
    }
    return "Relacionado"
}
internal val LocalIndicatorDirectory=staticCompositionLocalOf {IndicatorDirectory()}

/** Preview is one informational row. Only the full detail owns touch targets. */
@Composable internal fun CompactCardIndicators(key:String,fallback:CompactCardFacts=CompactCardFacts(),modifier:Modifier=Modifier,showTechnologies:Boolean=true) {
    val facts=LocalCompactCardFacts.current[key] ?: fallback
    val active=cardIndicators(facts)
    val baseline=listOf(CardIndicator("people",Icons.Default.Groups,"Personas",IndicatorTone.PEOPLE),CardIndicator("technologies",Icons.Default.Code,"Tecnologías",IndicatorTone.CONTENT),CardIndicator("notes",Icons.Default.Description,"Notas",IndicatorTone.CONTENT),CardIndicator("due",Icons.Default.Event,"Vencimiento",IndicatorTone.CONTENT))
    val signals=(baseline.map {inactive->active.firstOrNull {it.name==inactive.name} ?: inactive}+active.filter {row->baseline.none {it.name==row.name}}).filter {showTechnologies || it.name!="technologies"}
    val metro=facts.journeys.isNotEmpty() || LocalMetroActivity.current[key].orEmpty().isNotEmpty()
    if(signals.isEmpty() && !metro) return
    BoxWithConstraints(modifier.testTag("card-indicators:$key")) {
        val total=signals.size+if(metro) 1 else 0
        val rowWidth=maxWidth
        val slots=(rowWidth.value/22f).toInt().coerceAtLeast(1)
        val overflowWidth=(12f*androidx.compose.ui.platform.LocalDensity.current.fontScale*("+$total".length)*.7f).coerceAtLeast(22f)
        val visible=if(total>slots) ((maxWidth.value-overflowWidth-6f)/22f).toInt().coerceIn(0,(slots-1).coerceAtLeast(0)) else total
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            signals.take(visible).forEach {signal->LucideIcon(signal.icon,signal.label,Modifier.size(16.dp).testTag("card-signal:$key:${signal.name}"),tint=if(active.any {it.name==signal.name}) Arachn0deColors.TextPrimary else BlackWhite90.Inactive)}
            if(metro && visible>signals.size) Box(Modifier.size(16.dp)) {MetroActivityIndicator(key,configured=facts.journeys.isNotEmpty(),informative=true,iconSize=16.dp,iconTint=indicatorColor(IndicatorTone.CONTENT))}
            if(total>visible) Text("+${total-visible}",style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Clip,modifier=Modifier.widthIn(max=rowWidth).testTag("card-overflow:$key"))
        }
    }
}

@Composable internal fun CardIndicatorDetail(key:String,onOpenNode:(String)->Unit,modifier:Modifier=Modifier,onOpenPeople:(()->Unit)?=null,onOpenTechnologies:(()->Unit)?=null,managementContext:String?=null,participationRepository:com.r0ybt.arachn0de.data.repository.ParticipationRepository?=null,showPeople:Boolean=true,fillWidth:Boolean=true) {
    val participation=if(managementContext!=null && participationRepository!=null) {
        val value by remember(managementContext,participationRepository) {participationRepository.observe(managementContext)}.collectAsState(initial=com.r0ybt.arachn0de.data.repository.ParticipationState())
        value
    } else null
    val members=participation?.records?.participants.orEmpty().filter {it.contextId==managementContext}.mapTo(hashSetOf()) {it.personId}
    val facts=(LocalCompactCardFacts.current[key] ?: CompactCardFacts()).let {it.copy(people=it.people+members)}
    val activeSignals=cardIndicators(facts).filter {showPeople || it.name!="people"}
    val signals=if(key.startsWith("project:") && activeSignals.none {it.name=="technologies"})
        activeSignals+CardIndicator("technologies",Icons.Default.Code,"Tecnologías",IndicatorTone.CONTENT) else activeSignals
    val directory=LocalIndicatorDirectory.current
    var selected by remember(key) {mutableStateOf<CardIndicator?>(null)}
    var technology by remember(key) {mutableStateOf<com.r0ybt.arachn0de.data.local.TechnologyEntity?>(null)}
    var person by remember(key) {mutableStateOf<String?>(null)}
    var responsibilityPerson by remember(key) {mutableStateOf<String?>(null)}
    val metro=facts.journeys.isNotEmpty() || LocalMetroActivity.current[key].orEmpty().isNotEmpty()
    if(signals.isEmpty() && !metro) return
    FlowRow(modifier.then(if(fillWidth) Modifier.fillMaxWidth() else Modifier).testTag("detail-indicators:$key"),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(0.dp),itemVerticalAlignment=Alignment.CenterVertically) {
        signals.forEach {signal->CompactCardIconButton(signal.icon,signal.label,{selected=signal},Modifier.testTag("detail-signal:$key:${signal.name}"),enabled=signal in activeSignals,tint=if(signal in activeSignals) indicatorColor(signal.tone) else BlackWhite90.Inactive)}
        if(metro) {
            if(LocalMetroActivity.current[key].orEmpty().isNotEmpty()) MetroActivityIndicator(key,iconSize=16.dp,iconTint=indicatorColor(IndicatorTone.CONTENT))
            else IconButton(onClick={selected=CardIndicator("metro",Icons.Default.DirectionsSubway,"Viajes Metro asociados",IndicatorTone.CONTENT,facts.journeys)},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.DirectionsSubway,"Viajes Metro asociados",Modifier.size(16.dp),tint=indicatorColor(IndicatorTone.CONTENT))}
        }
    }
    selected?.let {signal->
        if(signal.name=="people") androidx.compose.ui.window.Dialog(onDismissRequest={selected=null},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(Modifier.fillMaxSize().safeDrawingPadding()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Personas",style=MaterialTheme.typography.titleLarge)
                Text(if(managementContext!=null) "Participantes del proyecto y personas asociadas a este contenido." else "Personas asociadas a este contenido.",style=MaterialTheme.typography.bodySmall)
                LazyVerticalGrid(columns=GridCells.Fixed(3),modifier=Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(signal.ids.sortedWith(compareBy<String> {it !in members}.thenBy {directory.people[it]?.name.orEmpty()}),key={it}) {id->
                        val row=directory.people[id] ?: participation?.people?.firstOrNull {it.id==id}?.let {Person(it.id,it.name,it.avatarFile,it.avatarZoom,it.avatarX,it.avatarY)}
                        if(row!=null) ParticipantCard(row,if(id in members) "Participante · Ver responsabilidades" else "Persona asociada al contenido",id in members) {
                            selected=null
                            if(id in members && participationRepository!=null) responsibilityPerson=id else person=id
                        }
                    }
                }
                if(onOpenPeople!=null) OutlinedButton(onClick={selected=null;onOpenPeople()}) {Text("Catálogo de Personas")}
                OutlinedButton(onClick={selected=null},modifier=Modifier.fillMaxWidth()) {Text("Cerrar")}
            }}
        } else AlertDialog(onDismissRequest={selected=null},title={Text(signal.label)},text={Column {
            Text(if(signal.tone==IndicatorTone.ALERT) "Requiere atención; no cambia fases ni checks." else "Información asociada a este contenido.",style=MaterialTheme.typography.bodySmall)
            if(signal.name=="due" && signal.tone==IndicatorTone.ALERT) Text("Próximo: dentro de las siguientes 24 horas.",style=MaterialTheme.typography.bodySmall)
            if(signal.name=="people" && onOpenPeople!=null) TextButton(onClick={selected=null;onOpenPeople()}) {Text("Abrir Personas")}
            if(signal.name=="technologies" && onOpenTechnologies!=null) TextButton(onClick={selected=null;onOpenTechnologies()}) {Text("Abrir Tecnologías")}
            LazyColumn(Modifier.heightIn(max=320.dp)) {
                items(signal.ids.sorted(),key={it}) {id->
                    val node=directory.nodes[if(signal.name=="metro") directory.journeyNodes[id] else id]
                    val label=when(signal.name) {"people"->directory.people[id]?.name;"technologies"->directory.technologies[id]?.name;else->node?.title} ?: id
                    if(node!=null) TextButton(onClick={selected=null;onOpenNode(node.id)}) {Column(Modifier.fillMaxWidth()) {Text(label);Text(indicatorOrigin(key,node,directory.nodes),style=MaterialTheme.typography.labelSmall)}}
                    else if(signal.name=="people" && id in directory.people) TextButton(onClick={selected=null;person=id}) {Text(label)}
                    else if(signal.name=="technologies" && id in directory.technologies) TextButton(onClick={selected=null;technology=directory.technologies[id]}) {Text(label)}
                    else Text(label,Modifier.padding(vertical=8.dp))
                }
            }
        }},confirmButton={TextButton(onClick={selected=null}) {Text("Cerrar")}})
    }
    if(managementContext!=null && participationRepository!=null) responsibilityPerson?.let {id->ParticipationDialog(managementContext,participationRepository,initialPersonId=id) {responsibilityPerson=null}}
    person?.let {PersonDetailDialog(it) {person=null}}
    technology?.let {row->AlertDialog(onDismissRequest={technology=null},title={Text(row.name)},text={Column {Text("Tecnología del catálogo asociada a este contenido.");TechnologyIcon(row)}},confirmButton={TextButton(onClick={technology=null}) {Text("Cerrar")}})}
}
