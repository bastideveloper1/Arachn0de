package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.Person

/** The surrounding real Equipo layer owns description, scrolling and navigation. */
@Composable internal fun TeamContext(context:String,repository:ParticipationRepository) {
    val state by remember(repository,context) {repository.observe(context)}.collectAsState(initial=ParticipationState())
    var adding by remember {mutableStateOf(false)}
    var person by remember {mutableStateOf<String?>(null)}
    var help by remember {mutableStateOf(false)}
    val members=state.records.participants.filter {it.contextId==context}.mapTo(hashSetOf()) {it.personId}
    val people=state.people.filter {it.id in members}.sortedWith(compareBy({it.name.lowercase()},{it.id}))
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Participantes",style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={adding=true},modifier=Modifier.weight(1f)) {Text("Incorporar Personas")}
            OutlinedButton(onClick={help=true}) {Text("Ayuda de roles")}
        }
        if(people.isEmpty()) Text("Sin participantes. Incorpora Personas del catálogo para formar el equipo.")
        people.chunked(3).forEach {row->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                row.forEach {p->
                    val count=state.records.roles.count {it.contextId==context && it.personId==p.id}+state.records.nodes.count {it.contextId==context && it.personId==p.id}+state.records.objectives.count {it.contextId==context && it.personId==p.id}
                    ParticipantCard(Person(p.id,p.name,p.avatarFile,p.avatarZoom,p.avatarX,p.avatarY),"$count roles y responsabilidades",true,modifier=Modifier.weight(1f)) {person=p.id}
                }
                repeat(3-row.size) {Spacer(Modifier.weight(1f))}
            }
        }
    }
    if(adding) ParticipationDialog(context,repository,initialAdding=true) {adding=false}
    person?.let {id->ParticipationDialog(context,repository,initialPersonId=id) {person=null}}
    if(help) ManagementHelpScreen("Roles y responsabilidades",responsibilityHelpParagraphs) {help=false}
}
