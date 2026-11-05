package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.local.*
import java.util.UUID

internal val MemoryPeopleSaver=listSaver<List<PersonMemoryPersonEntity>,String>(save={rows->rows.flatMap {listOf(it.id,it.memoryId,it.personId.orEmpty(),it.label,it.profileName,it.position.toString())}},restore={values->values.chunked(6).map {PersonMemoryPersonEntity(it[0],it[1],it[2].ifEmpty {null},it[3],it[4],it[5].toInt())}})

@Composable internal fun MemoryPeoplePresentation(entry:PersonMemoryEntity,state:com.r0ybt.arachn0de.data.repository.PersonMemoryState,onOpen:(String)->Unit) {
    val rows=state.visiblePeople(entry)
    if(rows.isEmpty()) return
    val names=state.profiles.associate {it.id to it.name}
    Text("Personas presentes",style=MaterialTheme.typography.labelLarge)
    rows.forEach {row->
        val profile=row.personId?.takeIf {it in names}
        if(profile==null) Text(row.name(names),modifier=Modifier.testTag("memory-person:${row.id}"))
        else TextButton(onClick={onOpen(profile)},contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=48.dp).testTag("memory-person:${row.id}")) {Text(row.name(names))}
    }
}

@Composable internal fun MemoryPeoplePanel(memory:String,rows:List<PersonMemoryPersonEntity>,profiles:List<PersonEntity>,visible:Boolean,enabled:Boolean,
    onRows:(List<PersonMemoryPersonEntity>)->Unit,onVisible:(Boolean)->Unit) {
    var text by rememberSaveable(memory) {mutableStateOf("")}
    var query by rememberSaveable(memory) {mutableStateOf("")}
    var selected by rememberSaveable(memory) {mutableStateOf<String?>(null)}
    var linking by rememberSaveable(memory) {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val names=profiles.associate {it.id to it.name}
    val current=rows.firstOrNull {it.id==selected}
    fun change(next:List<PersonMemoryPersonEntity>) {onRows(next.mapIndexed {index,row->row.copy(position=index)});error=null}
    fun choose(profile:PersonEntity) {
        if(rows.any {it.personId==profile.id && it.id!=current?.id}) {error="Este perfil ya está asociado.";return}
        if(rows.any {it.personId==null && it.id!=current?.id && memoryPersonKey(it.name(names))==memoryPersonKey(profile.name)}) {error="Este nombre ya está asociado. Selecciónalo y usa Vincular a perfil.";return}
        val value=current?.takeIf {linking}?.copy(personId=profile.id,profileName=profile.name)
            ?: PersonMemoryPersonEntity(UUID.randomUUID().toString(),memory,profile.id,profile.name,profile.name,rows.size)
        change(if(current!=null && linking) rows.map {if(it.id==current.id) value else it} else rows+value)
        selected=null;linking=false;query="";text=""
    }
    Column(Modifier.fillMaxWidth().heightIn(max=340.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Switch(visible,onVisible,enabled=enabled,modifier=Modifier.testTag("memory-people-visible"))
            Text("Mostrar personas en la publicación")
        }
        Text("Preferencia local de presentación. Ocultar conserva todas las asociaciones.",style=MaterialTheme.typography.bodySmall)
        rows.forEach {row->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TextButton(enabled=enabled,onClick={selected=if(selected==row.id) null else row.id;linking=false;text=row.name(names)},modifier=Modifier.weight(1f).testTag("memory-person-edit:${row.id}")) {Text(row.name(names))}
                TextButton(enabled=enabled,onClick={change(rows.filterNot {it.id==row.id});if(selected==row.id) selected=null}) {Text("Quitar")}
            }
        }
        if(current?.personId==null && current!=null) {
            OutlinedTextField(text,{if(it.length<=1000) text=it},label={Text("Nombre libre")},enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("memory-person-name"))
            Row {
                TextButton(enabled=enabled && text.isNotBlank(),onClick={
                    val key=memoryPersonKey(text)
                    if(rows.any {it.id!=current.id && memoryPersonKey(it.name(names))==key}) error="Esta persona ya está asociada."
                    else {change(rows.map {if(it.id==current.id) it.copy(label=text.trim(),profileName="") else it});selected=null;text=""}
                }) {Text("Aplicar nombre")}
                TextButton(enabled=enabled,onClick={linking=!linking}) {Text("Vincular a perfil")}
            }
            if(current.label!=current.name(names)) Text("Referencia original: ${current.label}",style=MaterialTheme.typography.bodySmall)
        }
        if(current==null) {
            OutlinedTextField(text,{if(it.length<=1000) text=it},enabled=enabled,label={Text("Nombre libre")},modifier=Modifier.fillMaxWidth().testTag("memory-person-name"))
            TextButton(enabled=enabled && rows.size<200 && text.isNotBlank(),onClick={
                if(rows.any {memoryPersonKey(it.name(names))==memoryPersonKey(text)}) error="Esta persona ya está asociada."
                else {change(rows+PersonMemoryPersonEntity(UUID.randomUUID().toString(),memory,null,text.trim(),position=rows.size));text=""}
            }) {Text("Añadir nombre libre")}
        }
        if(current==null || linking) {
            OutlinedTextField(query,{query=it},enabled=enabled,label={Text(if(linking) "Vincular a perfil existente" else "Buscar perfil existente")},modifier=Modifier.fillMaxWidth())
            profiles.filter {it.name.contains(query.trim(),ignoreCase=true)}.forEach {profile->
                TextButton(enabled=enabled && (rows.size<200 || linking) && rows.none {it.personId==profile.id},onClick={choose(profile)},modifier=Modifier.fillMaxWidth().testTag("memory-profile:${profile.id}")) {Text(profile.name)}
            }
        }
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }
}
