package com.r0ybt.arachn0de.gantt

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class GanttScope(val label:String) { WEEK("Semana"), MONTH("Mes"), SPRINT("Sprint"), ALL("Todos") }
internal enum class GanttScale(val label:String) { DAY("Días"), WEEK("Semanas"), MONTH("Meses") }
internal data class GanttSelection(val scope:GanttScope=GanttScope.SPRINT,val scale:GanttScale=GanttScale.DAY,val sprint:String?=null,val anchor:LocalDate=LocalDate.now(),val people:Boolean=false,val person:String?=null,val expanded:Boolean=true,val sliding:Boolean=false)
internal data class GanttItem(val id:String,val title:String,val start:LocalDate?,val end:LocalDate?,val progress:Float,val complete:Boolean,val people:Set<String>,val sprint:Boolean=false,val parent:String?=null,val status:String="",val names:Set<String> = emptySet(),val kind:String="Tarea")
internal data class GanttLane(val id:String,val title:String,val items:List<GanttItem>)
internal data class GanttTick(val start:LocalDate,val end:LocalDate,val label:String)
internal data class GanttDocument(val title:String,val period:String,val first:LocalDate,val last:LocalDate,val today:LocalDate,val ticks:List<GanttTick>,val lanes:List<GanttLane>,val items:List<GanttItem>,val completed:Int,val total:Int,val undated:Int,val elapsed:Float,val annotations:List<GanttAnnotationEntity>,val sprintProgress:Double?=null,val storyLabels:Map<String,String> = emptyMap(),val storyOwners:Map<String,String> = emptyMap(),val heading:String=title)
internal val ganttLocale=Locale.forLanguageTag("es-CL")
internal fun ganttDate(day:LocalDate)=day.format(DateTimeFormatter.ofPattern("d MMM yyyy",ganttLocale))
internal fun ganttTicks(first:LocalDate,last:LocalDate,scale:GanttScale):List<GanttTick> {
    val result=mutableListOf<GanttTick>();var day=first
    while(day<=last) {
        val end=minOf(last,when(scale) {GanttScale.DAY->day;GanttScale.WEEK->day.plusDays(6-day.dayOfWeek.value.toLong()+1);GanttScale.MONTH->day.withDayOfMonth(day.lengthOfMonth())})
        val label=when(scale) {GanttScale.DAY->day.format(DateTimeFormatter.ofPattern("EEE dd MMM",ganttLocale)).replaceFirstChar {it.uppercase()};GanttScale.WEEK->"Semana ${result.size+1} · ${ganttDate(day)}–${ganttDate(end)}";GanttScale.MONTH->day.format(DateTimeFormatter.ofPattern("MMMM yyyy",ganttLocale))}
        result+=GanttTick(day,end,label);day=end.plusDays(1)
        require(result.size<=3660) {"Consulta un período menor para mostrar el calendario."}
    };return result
}
internal fun ganttDocument(state:IterationState,context:String,selection:GanttSelection,responsible:Map<String,Set<String>>,annotations:List<GanttAnnotationEntity>,title:String,today:LocalDate=LocalDate.now(),zone:ZoneId=ZoneId.systemDefault(),sprintStories:List<SprintStoryEntity> = emptyList()):GanttDocument {
    val tree=state.work.nodes;val byId=tree.associateBy {it.id};val parents=tree.mapTo(hashSetOf()) {it.parentId}
    val snapshot=NodeTreeSnapshot(tree.map {it.toNode(it.id in parents)})
    fun date(time:Long?)=time?.let {Instant.ofEpochMilli(it).atZone(zone).toLocalDate()}
    val plans=state.records.plans.associateBy {it.sprintId};val sprints=tree.filter {it.sprintMode && state.work.owners[it.id]==context}
    val selected=if(selection.scope==GanttScope.ALL || selection.sprint==null) sprints else sprints.filter {it.id==selection.sprint}
    val sprintIds=selected.mapTo(hashSetOf()) {it.id}
    val storyIds=state.work.records.roles.filter {it.role=="STORY" && it.contextId==context}.mapTo(hashSetOf()) {it.nodeId}
    val taskIds=if(sprints.isEmpty()) tree.filter {it.purpose=="ACTION" && state.work.owners[it.id]==context}.mapTo(hashSetOf()) {it.id} else selected.flatMap {state.current(it.id)}.mapTo(hashSetOf()) {it.id}
    val stories=sprintStories.filter {it.sprintId in sprintIds}.mapTo(hashSetOf()) {it.storyId}+storyIds.filter {id->tree.any {it.id in taskIds && generateSequence(it.parentId) {p->byId[p]?.parentId}.any {it==id}}}.toSet()
    val ancestors=taskIds.flatMap {id->generateSequence(byId[id]?.parentId) {byId[it]?.parentId}.takeWhile {it !in storyIds && it !in sprintIds}.toList()}.toSet()
    val ids=taskIds+stories+sprintIds+ancestors
    val semanticRoles=state.work.records.roles.associate {it.nodeId to it.role}
    val all=tree.filter {it.id in ids}.map {n->val p=plans[n.id];GanttItem(n.id,n.title,date(p?.startAt ?: n.startAt),date(p?.endAt ?: n.dueAt),if(n.sprintMode) ((state.progress(n.id) ?: 0.0)/100).toFloat() else (snapshot.progressById[n.id]?.percentage ?: 0)/100f,n.isCompleted,responsible[n.id].orEmpty(),n.sprintMode,n.parentId,when {n.sprintMode && p!=null -> when(p.state) {"CLOSED"->"Cerrado";"ACTIVE"->"En curso";else->"Previsto"};n.workState!=null -> com.r0ybt.arachn0de.domain.model.WorkState.valueOf(n.workState).label;n.purpose=="LAYER" -> "Capa";n.isCompleted -> "Completada";else -> "Pendiente"},responsible[n.id].orEmpty().mapNotNull {id->state.people.people.firstOrNull {it.id==id}?.name}.toSet(),when {n.sprintMode->"Sprint";semanticRoles[n.id]=="STORY"->"Historia";semanticRoles[n.id]=="EPIC"->"Épica";n.purpose=="LAYER"->"Capa";else->"Tarea"})}
    val first:LocalDate;val last:LocalDate
    when(selection.scope) {
        GanttScope.WEEK->{first=if(selection.sliding) selection.anchor else selection.anchor.minusDays(selection.anchor.dayOfWeek.value.toLong()-1);last=first.plusDays(6)}
        GanttScope.MONTH->{first=if(selection.sliding) selection.anchor else selection.anchor.withDayOfMonth(1);last=if(selection.sliding) first.plusMonths(1).minusDays(1) else first.withDayOfMonth(first.lengthOfMonth())}
        else->{first=selected.mapNotNull {date(plans[it.id]?.startAt ?: it.startAt)}.minOrNull() ?: all.mapNotNull {it.start ?: it.end}.minOrNull() ?: today;last=maxOf(first,selected.mapNotNull {date(plans[it.id]?.endAt ?: it.dueAt)}.maxOrNull() ?: all.mapNotNull {it.end ?: it.start}.maxOrNull() ?: first)}
    }
    val work=all.filter {!it.sprint && (selection.scope !in listOf(GanttScope.WEEK,GanttScope.MONTH) || ((it.start==null && it.end==null) || ((it.start ?: it.end!!)<=last && (it.end ?: it.start!!)>=first))) && (selection.person==null || selection.person in it.people)}
    val visible=if(selection.expanded) work else work.filter {it.id in stories || it.parent in sprintIds}
    val displayed=if(selection.scope==GanttScope.ALL && !selection.people) all.filter {it.sprint}+visible else visible
    val people=state.people.people.associateBy {it.id}
    val lanes=if(selection.people) {
        val members=state.people.records.participants.filter {it.contextId==context}.map {it.personId}.toSet()+work.flatMap {it.people}
        (members.filter {selection.person==null || it==selection.person}.map {GanttLane(it,people[it]?.name ?: "Persona",visible.filter {w->it in w.people})}+if(selection.person==null && visible.any {it.people.isEmpty()}) listOf(GanttLane("unassigned","Sin responsable",visible.filter {it.people.isEmpty()})) else emptyList())
    } else displayed.map {GanttLane(it.id,(if(it.parent in ids && !it.sprint) "↳ " else "")+it.title,listOf(it))}
    val leaves=work.filter {byId[it.id]?.purpose=="ACTION"}.distinctBy {it.id}
    val labels=storyLabels(tree,state.work.records.roles)
    val storyOwners=tree.mapNotNull {n->
        val ancestor=generateSequence(n.id) {byId[it]?.parentId}.firstOrNull {it in storyIds}
            ?: state.work.records.storyTasks.firstOrNull {it.taskId==n.id && it.storyId in storyIds}?.storyId
        ancestor?.let {n.id to it}
    }.toMap()
    val days=last.toEpochDay()-first.toEpochDay()+1
    return GanttDocument(title,selection.scope.label+" · "+ganttDate(first)+" → "+ganttDate(last),first,last,today,ganttTicks(first,last,selection.scale),lanes,work,leaves.count {it.complete},leaves.size,all.count {!it.sprint && it.end==null},((today.toEpochDay()-first.toEpochDay()).toFloat()/days).coerceIn(0f,1f),annotations.filter {it.contextId==context && it.epochDay in first.toEpochDay()..last.toEpochDay()},if(selected.isEmpty()) null else com.r0ybt.arachn0de.ui.state.sprintProgressPercentage(tree.map {it.toNode(it.id in parents)},"",taskIds),labels,storyOwners,if(selected.size==1) selected.single().title else title)
}
