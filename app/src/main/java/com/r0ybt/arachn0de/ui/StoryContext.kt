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

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun StoryContext(story:String,context:String,repository:ManagementRepository,onOpenNode:(String)->Unit,onRelationships:()->Unit) {
    val work by remember(repository,context) {repository.backlog.observe(context)}.collectAsState(initial=BacklogState())
    val participation by remember(repository,context) {repository.participation.observe(context)}.collectAsState(initial=ParticipationState())
    val nodes=remember(work.nodes) {work.nodes.associateBy {it.id}}
    val epic=nodes[nodes[story]?.parentId]?.takeIf {parent->work.records.roles.any {it.nodeId==parent.id && it.role=="EPIC"}}
    val extraEpics=work.records.epicStories.filter {it.storyId==story && it.epicId!=epic?.id}.mapNotNull {nodes[it.epicId]}.distinctBy {it.id}
    var selectedPerson by remember {mutableStateOf<String?>(null)}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Épica principal",style=MaterialTheme.typography.titleMedium)
        if(epic!=null) ManagementNavigationCard(ManagementToolVisual(epic.title,"Contiene esta Historia",Icons.Default.Workspaces)) {onOpenNode(epic.id)}
        else Text("Sin épica contenedora. Los vínculos adicionales no cambian la ubicación de la Historia.",style=MaterialTheme.typography.bodySmall)
        Text("Responsables de la Historia",style=MaterialTheme.typography.titleMedium)
        val assignments=participation.records.nodes.filter {it.contextId==context && it.nodeId==story}
        val people=assignments.map {it.personId}.distinct().mapNotNull {id->participation.people.firstOrNull {it.id==id}}
        if(people.isEmpty()) Text("Sin responsabilidades específicas asignadas.",style=MaterialTheme.typography.bodySmall)
        // Compact story summaries keep their small avatars; full participant lists use the grid.
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            people.forEach {row->AssistChip(onClick={selectedPerson=row.id},leadingIcon={PersonAvatar(Person(row.id,row.name,row.avatarFile,row.avatarZoom,row.avatarX,row.avatarY))},label={
                Column(Modifier.widthIn(max=180.dp)) {
                    Text(row.name,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(assignments.filter {it.personId==row.id}.mapNotNull {a->ResponsibilityRole.entries.firstOrNull {it.name==a.role}?.label}.distinct().joinToString(),style=MaterialTheme.typography.labelSmall)
                }
            })}
        }
        ResponsibilitiesAccess(context,story,false,repository.participation)
        if(extraEpics.isNotEmpty()) {
            Text("Vínculos adicionales con épicas",style=MaterialTheme.typography.titleMedium)
            extraEpics.forEach {other->ManagementNavigationCard(ManagementToolVisual(other.title,"Relación adicional · No cambia la épica principal",Icons.Default.Workspaces)) {onOpenNode(other.id)}}
        }
        OutlinedButton(onClick=onRelationships) {Text("Gestionar vínculos adicionales")}
        Text("Estas relaciones conectan elementos existentes sin moverlos ni cambiar sus fases.",style=MaterialTheme.typography.bodySmall)
        Text("Elementos de trabajo",style=MaterialTheme.typography.titleMedium)
    }
    selectedPerson?.let {id->ParticipationDialog(context,repository.participation,target=story,initialPersonId=id) {selectedPerson=null}}
}
