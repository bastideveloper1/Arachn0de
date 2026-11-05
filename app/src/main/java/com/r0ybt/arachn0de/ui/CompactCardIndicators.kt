package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.ui.theme.BashColors
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
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
    // Notes retain date metadata; attention on tasks/containers requires pending work.
    val hasDue=node.dueAt!=null && (pending || node.purpose==NodePurpose.NOTE)
    return CompactCardFacts(due=hasDue,today=pending && node.dueAt?.let {CalendarDates.localDay(it,zone)==CalendarDates.localDay(now,zone)}==true,
        high=pending && node.effectivePriority==Priority.HIGH,
        notes=if(node.description.isNotBlank()) setOf(node.id) else emptySet(),
        files=if(AttachmentReferences.ids(node.description).isNotEmpty()) setOf(node.id) else emptySet(),
        deadlines=if(pending && node.dueAt?.let {it<=now+24*60*60*1000L}==true) setOf(node.id) else emptySet(),
        priorities=if(pending && node.effectivePriority==Priority.HIGH) setOf(node.id) else emptySet(),
        dueNodes=if(hasDue) setOf(node.id) else emptySet(),
        todayNodes=if(pending && node.dueAt?.let {CalendarDates.localDay(it,zone)==CalendarDates.localDay(now,zone)}==true) setOf(node.id) else emptySet())
}
internal fun compactCardFacts(nodes:List<Node>,people:Map<String,List<Person>>,technologies:TechnologyState,
    metro:MetroSnapshot?,now:Long,zone:TimeZone=TimeZone.getDefault(),participation:com.r0ybt.arachn0de.data.repository.ParticipationState?=null):Map<String,CompactCardFacts> {
    val result=mutableMapOf<String,CompactCardFacts>();val byId=nodes.associateBy {it.id}
    val pendingTaskIds=nodes.filter {it.isCompletable && !it.isCompleted}.mapTo(hashSetOf()) {it.id}
    val pendingAncestors=hashSetOf<String>()
    nodes.filter {it.id in pendingTaskIds}.forEach {task->
        var parent=task.parentId?.let(byId::get)
        while(parent!=null && parent.projectId==task.projectId && pendingAncestors.add(parent.id)) parent=parent.parentId?.let(byId::get)
    }
    val journeys=metro?.journeys.orEmpty().filter {it.row.nodeId!=null}.groupBy {it.row.nodeId}
    fun add(key:String,facts:CompactCardFacts) {result[key]=(result[key] ?: CompactCardFacts())+facts}
    val participantNodes=mutableMapOf<String,Set<String>>()
    participation?.records?.nodes.orEmpty().forEach {row->participantNodes[row.nodeId]=participantNodes[row.nodeId].orEmpty()+row.personId}
    val relatedTasks=participation?.work?.records?.storyTasks.orEmpty().groupBy {it.storyId}.mapValues {(_,rows)->rows.mapNotNullTo(hashSetOf()) {it.taskId.takeIf(pendingTaskIds::contains)}}
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
            add("node:${current.id}",facts.copy(tasks=facts.tasks+if(current.id!=node.id && node.id in pendingTaskIds) setOf(node.id) else emptySet()))
            current=current.parentId?.let(byId::get)?.takeIf {it.projectId==node.projectId}
        }
        add("project:${node.projectId}",facts.copy(tasks=facts.tasks+if(node.id in pendingTaskIds) setOf(node.id) else emptySet()))
    }
    return result
}

/** Presentation-only split; existing detail totals and stored statuses remain untouched. */
internal fun previewTaskStates(facts:CompactCardFacts,nodes:Map<String,Node>,count:Int):Pair<Set<String>,Set<String>> {
    val tasks=facts.tasks.mapNotNull(nodes::get).filter {it.isCompletable}
    return tasks.filter {if(count==3)it.taskStatus==TaskStatus.PENDING else !it.isCompleted}.mapTo(hashSetOf()) {it.id} to
        tasks.filter {it.taskStatus==TaskStatus.VERIFY}.mapTo(hashSetOf()) {it.id}
}

internal enum class IndicatorTone { PEOPLE, CONTENT, ALERT }
internal data class CardIndicator(val name:String,val icon:ImageVector,val label:String,val tone:IndicatorTone,val ids:Set<String> = emptySet())
internal fun cardIndicators(f:CompactCardFacts):List<CardIndicator> = buildList {
    if(f.people.isNotEmpty()) add(CardIndicator("people",if(f.people.size==1) Icons.Default.Person else Icons.Default.Groups,"Personas",IndicatorTone.PEOPLE,f.people))
    if(f.notes.isNotEmpty()) add(CardIndicator("notes",Icons.Default.Description,"Notas",IndicatorTone.CONTENT,f.notes))
    if(f.files.isNotEmpty()) add(CardIndicator("files",Icons.Default.AttachFile,"Archivos en el contenido",IndicatorTone.CONTENT,f.files))
    if(f.tasks.isNotEmpty()) add(CardIndicator("tasks",Icons.Default.Checklist,"Tareas",IndicatorTone.CONTENT,f.tasks))
    if(f.due) add(CardIndicator("due",Icons.Default.DateRange,if(f.deadlines.isEmpty()) "Vencimiento" else "Vencimiento próximo o vencido",if(f.deadlines.isEmpty()) IndicatorTone.CONTENT else IndicatorTone.ALERT,if(f.deadlines.isEmpty()) f.dueNodes else f.deadlines))
    if(f.today) add(CardIndicator("today",Icons.Default.HourglassBottom,"Pendientes para hoy",IndicatorTone.ALERT,f.todayNodes))
    if(f.high) add(CardIndicator("high",Icons.Default.Flag,"Prioritario",IndicatorTone.ALERT,f.priorities))
}
internal val optionalAssociationIndicator=CardIndicator("associations",Icons.Default.Link,"Asociaciones opcionales",IndicatorTone.CONTENT)

@Composable internal fun indicatorColor(tone:IndicatorTone):Color = Arachn0deColors.TextPrimary

internal data class IndicatorDirectory(val nodes:Map<String,Node> = emptyMap(),val people:Map<String,Person> = emptyMap(),val technologies:Map<String,com.r0ybt.arachn0de.data.local.TechnologyEntity> = emptyMap(),val journeyNodes:Map<String,String> = emptyMap(),val projects:Map<String,Project> = emptyMap())
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
@Composable internal fun CompactCardIndicators(key:String,fallback:CompactCardFacts=CompactCardFacts(),modifier:Modifier=Modifier,showTechnologies:Boolean=true,hasOwnNotes:Boolean=false,onRecurrence:(()->Unit)?=null,taskStateCount:Int?=null,showContainedTasks:Boolean=LocalIndicatorDirectory.current.nodes[key.removePrefix("node:")]?.isStructural ?: true,effort:Int?=null,ownPriority:Boolean=false) {
    val original=LocalCompactCardFacts.current[key] ?: fallback
    val facts=original.copy(notes=original.notes+if(hasOwnNotes) setOf(key) else emptySet(),
        high=original.high || ownPriority,priorities=original.priorities+if(ownPriority)setOf(key) else emptySet())
    val taskStates=taskStateCount?.let {previewTaskStates(facts,LocalIndicatorDirectory.current.nodes,it)}
    val active=cardIndicators(if(taskStates==null)facts else facts.copy(tasks=taskStates.first))+
        if(taskStates?.second?.isNotEmpty()==true) listOf(CardIndicator("verify",Icons.Default.Visibility,"Por verificar",IndicatorTone.CONTENT,taskStates.second)) else emptyList()
    // Keep inactive slots visible, with attachments before pending/verification indicators.
    val baseline=listOf(
        CardIndicator("people",Icons.Default.Groups,"Personas",IndicatorTone.PEOPLE),
        CardIndicator("notes",Icons.Default.Description,"Notas",IndicatorTone.CONTENT),
        CardIndicator("due",Icons.Default.Event,"Vencimiento",IndicatorTone.CONTENT),
        CardIndicator("files",Icons.Default.AttachFile,"Archivos en el contenido",IndicatorTone.CONTENT),
        CardIndicator("tasks",Icons.Default.Checklist,"Tareas",IndicatorTone.CONTENT),
        CardIndicator("today",Icons.Default.HourglassBottom,"Pendientes para hoy",IndicatorTone.ALERT),
        CardIndicator("high",Icons.Default.Flag,"Prioritario",IndicatorTone.ALERT))
    val previewBaseline=if(taskStateCount==3)baseline.flatMap {if(it.name=="tasks")listOf(it,CardIndicator("verify",Icons.Default.Visibility,"Por verificar",IndicatorTone.CONTENT)) else listOf(it)} else baseline
    val signals=(previewBaseline+if(onRecurrence!=null)listOf(CardIndicator("recurrence",Icons.Default.Refresh,"Recurrente",IndicatorTone.CONTENT)) else emptyList()).map {inactive->active.firstOrNull {it.name==inactive.name} ?: inactive}
        .filter {it.name!="technologies"}
        .filter {showContainedTasks || it.name !in setOf("tasks","verify")}
    BoxWithConstraints(modifier.testTag("card-indicators:$key")) {
        // Informational glyphs stay uniform and grouped; only the recurrence action owns a touch target.
        val iconSize=16.dp
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically) {
            FavoriteCardIndicator(key)
            if(effort!=null) TaskEffortMini(key.removePrefix("node:"),effort)
            signals.forEach {signal->
                val present=signal.name=="recurrence" || active.any {it.name==signal.name}
                val iconModifier=Modifier.size(iconSize).testTag("card-signal:$key:${signal.name}")
                    .then(if(signal.name=="notes") Modifier else Modifier.semantics {stateDescription=if(present) "Configurado" else "Sin datos"})
                val tint=if(signal.name=="recurrence") Arachn0deColors.Icon else cardIndicatorColor(signal.name,present)
                if(signal.name=="notes") NoteContentIcon(present,signal.label,iconModifier,tint)
                else if(signal.name=="verify") Icon(Icons.Default.Visibility,signal.label,iconModifier,tint=tint)
                // Compose expands the clickable glyph's touch bounds to the platform minimum
                // without adding an otherwise empty 48dp row to the card.
                else LucideIcon(signal.icon,signal.label,iconModifier.then(
                    if(signal.name=="recurrence" && onRecurrence!=null) Modifier.clickable(onClickLabel="Abrir recurrencia",onClick=onRecurrence)
                    else Modifier),tint=tint)
            }
        }
    }
}

@Composable internal fun CardIndicatorDetail(key:String,onOpenNode:(String)->Unit,modifier:Modifier=Modifier,onOpenPeople:(()->Unit)?=null,onOpenTechnologies:(()->Unit)?=null,managementContext:String?=null,participationRepository:com.r0ybt.arachn0de.data.repository.ParticipationRepository?=null,showPeople:Boolean=true,fillWidth:Boolean=true,taskCounts:InteriorTaskCounts?=null,verificationEnabled:Boolean=false) {
    val participation=if(managementContext!=null && participationRepository!=null) {
        val value by remember(managementContext,participationRepository) {participationRepository.observe(managementContext)}.collectAsState(initial=com.r0ybt.arachn0de.data.repository.ParticipationState())
        value
    } else null
    val members=participation?.records?.participants.orEmpty().filter {it.contextId==managementContext}.mapTo(hashSetOf()) {it.personId}
    val facts=(LocalCompactCardFacts.current[key] ?: CompactCardFacts()).let {original->
        val scoped=taskCounts?.let {original.copy(tasks=it.pending,priorities=it.highPriority,high=it.highPriority.isNotEmpty())} ?: original
        scoped.copy(people=scoped.people+members)
    }
    val activeSignals=cardIndicators(facts).filter {showPeople || it.name!="people"}
        .filter {taskCounts==null || it.name!="tasks"}
    val signals=activeSignals.filter {it.name!="technologies"}
    val directory=LocalIndicatorDirectory.current
    var selected by remember(key) {mutableStateOf<CardIndicator?>(null)}
    var technology by remember(key) {mutableStateOf<com.r0ybt.arachn0de.data.local.TechnologyEntity?>(null)}
    var person by remember(key) {mutableStateOf<String?>(null)}
    var responsibilityPerson by remember(key) {mutableStateOf<String?>(null)}
    if(signals.isEmpty() && taskCounts==null) return
    FlowRow(modifier.then(if(fillWidth) Modifier.fillMaxWidth() else Modifier).testTag("detail-indicators:$key"),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(0.dp),itemVerticalAlignment=Alignment.CenterVertically) {
        if(taskCounts!=null) InteriorTaskStateIndicators(taskCounts,verificationEnabled,key)
        signals.forEach {signal->
            val button:@Composable ()->Unit={CompactCardIconButton(signal.icon,signal.label,{selected=signal},Modifier.testTag("detail-signal:$key:${signal.name}"),enabled=signal in activeSignals,tint=if(signal in activeSignals) indicatorColor(signal.tone) else BlackWhite90.Inactive)}
            val count=when(signal.name) {"tasks"->taskCounts?.pending?.size;"high"->taskCounts?.highPriority?.size;else->null}
            if(count==null)button() else InteriorIndicatorCount(count,if(signal.name=="tasks")"tareas sin completar" else "tareas de prioridad alta sin completar","detail-count:$key:${signal.name}",button)
        }

    }
    selected?.let {signal->
        if(signal.name=="people") BottomModulesDialog(onDismissRequest={selected=null},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
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
                    val node=directory.nodes[id]
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

internal val LocalIndicatorBackground=staticCompositionLocalOf<Color?> {null}

/** Presentation only: preserve the chosen tint whenever its rendered contrast is sufficient. */
internal fun contrastingIndicator(color:Color,background:Color,active:Boolean):Color {
    fun ratio(value:Color)=androidx.core.graphics.ColorUtils.calculateContrast(value.compositeOver(background).toArgb(),background.toArgb())
    if(ratio(color)>=3.0)return color
    val light=background.luminance()<.18f
    val target=if(active) {if(light)Color.White else Color.Black} else {if(light)Color(0xffaaaaaa) else Color(0xff555555)}
    if(!active || maxOf(color.red,color.green,color.blue)-minOf(color.red,color.green,color.blue)<.08f)return target
    var low=0f;var high=1f
    repeat(10) {val mid=(low+high)/2;if(ratio(androidx.compose.ui.graphics.lerp(color,target,mid))>=3.0)high=mid else low=mid}
    return androidx.compose.ui.graphics.lerp(color,target,high)
}

@Composable internal fun cardIndicatorColor(name:String,active:Boolean):Color {
    val chosen=if(!active) (if(LocalBashPresentation.current) BashColors.Muted else BlackWhite90.Inactive) else com.r0ybt.arachn0de.ui.theme.LocalAppearancePreferences.current
        ?.settings?.indicatorColors?.get(name)?.color ?: Arachn0deColors.TextPrimary
    return contrastingIndicator(chosen,(LocalIndicatorBackground.current ?: Arachn0deColors.Surface).compositeOver(Arachn0deColors.Background.copy(alpha=1f)),active)
}

internal val customizableCardIndicators=listOf(
    CardIndicator("people",Icons.Default.Groups,"Personas",IndicatorTone.PEOPLE),
    CardIndicator("notes",Icons.Default.Description,"Notas",IndicatorTone.CONTENT),
    CardIndicator("tasks",Icons.Default.Checklist,"Tareas",IndicatorTone.CONTENT),
    CardIndicator("files",Icons.Default.AttachFile,"Archivos en el contenido",IndicatorTone.CONTENT),
    CardIndicator("due",Icons.Default.DateRange,"Vencimiento",IndicatorTone.CONTENT),
    CardIndicator("today",Icons.Default.HourglassBottom,"Pendientes para hoy",IndicatorTone.ALERT),
    CardIndicator("high",Icons.Default.Flag,"Prioritario",IndicatorTone.ALERT),
    CardIndicator("verify",Icons.Default.Visibility,"Por verificar",IndicatorTone.CONTENT),
    CardIndicator("birthday",Icons.Default.Cake,"Cumpleaños",IndicatorTone.CONTENT),
)
