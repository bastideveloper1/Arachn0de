package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.Person

internal fun sprintStateLabel(state:String?)=when(state) {"ACTIVE"->"Activa";"CLOSED"->"Cerrada";else->"Preparada"}
internal fun sprintCardVisual(id:String,title:String,state:IterationState,stories:Int):ManagementToolVisual {
    val plan=state.records.plans.firstOrNull {it.sprintId==id}
    return ManagementToolVisual(title,listOf(sprintStateLabel(plan?.state),"$stories historias",listOfNotNull(plan?.startAt?.let {planDate(it)},plan?.endAt?.let {planDate(it)}).joinToString(" → "),state.progress(id)?.let {"${kotlin.math.round(it).toInt()} %"}.orEmpty()).filter {it.isNotBlank()}.joinToString(" · "),Icons.Default.DateRange)
}
@Composable internal fun SprintContext(sprint:String,context:String,repository:ManagementRepository,onPlan:()->Unit) {
    val state by remember(repository,context) {repository.iterations.observe(context)}.collectAsState(initial=IterationState())
    val refs by repository.relations.stories.collectAsState(initial=emptyList())
    val traditional by repository.iterations.assignments.collectAsState(initial=emptyList())
    var person by remember {mutableStateOf<String?>(null)}
    val plan=state.records.plans.firstOrNull {it.sprintId==sprint}
    val incorporated=refs.filter {it.sprintId==sprint}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Estado: ${sprintStateLabel(plan?.state)}",style=MaterialTheme.typography.titleMedium)
        plan?.let {p->
            Text("Inicio previsto: ${planDate(p.startAt).ifBlank {"Sin definir"}} · Término previsto: ${planDate(p.endAt).ifBlank {"Sin definir"}}")
            Text("Objetivo del Sprint",style=MaterialTheme.typography.titleMedium)
            Text(p.goal.ifBlank {"Sin objetivo definido"})
        }
        Text("Historias incorporadas: ${incorporated.size}",style=MaterialTheme.typography.titleMedium)
        incorporated.take(3).forEach {r->state.work.nodes.firstOrNull {it.id==r.storyId}?.let {n->Text("• ${n.title}",maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}}
        if(incorporated.size>3) Text("Y ${incorporated.size-3} más. Consulta todas en Gestionar historias.")
        SprintProgressCard(state.progress(sprint))
        Text("Responsables del Sprint",style=MaterialTheme.typography.titleMedium)
        val scoped=state.people.records.nodes.filter {it.contextId==context && it.nodeId==sprint}
        val ids=scoped.map {it.personId}.toSet()+traditional.filter {it.nodeId==sprint}.map {it.id}
        val people=state.people.people.filter {it.id in ids}
        if(people.isEmpty()) Text("Sin responsables asignados al Sprint.")
        people.chunked(3).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            row.forEach {p->ParticipantCard(Person(p.id,p.name,p.avatarFile,p.avatarZoom,p.avatarX,p.avatarY),scoped.filter {it.personId==p.id}.joinToString {ResponsibilityRole.valueOf(it.role).label}.ifBlank {"Responsable tradicional"},true,modifier=Modifier.weight(1f)) {person=p.id}}
            repeat(3-row.size) {Spacer(Modifier.weight(1f))}
        }}
        Button(onClick=onPlan,modifier=Modifier.fillMaxWidth()) {Text("Planificar")}
        Text("Las fases y checks se consultan aquí; el trabajo se ejecuta en Kanban o en su ubicación original.",style=MaterialTheme.typography.bodySmall)
    }
    person?.let {id->if(state.people.records.participants.any {it.contextId==context && it.personId==id}) ParticipationDialog(context,repository.participation,target=sprint,initialPersonId=id) {person=null} else PersonDetailDialog(id) {person=null}}
}
