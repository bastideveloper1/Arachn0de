package com.r0ybt.arachn0de.ui

import android.graphics.Bitmap
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.data.local.AvatarStore
import com.r0ybt.arachn0de.domain.model.Person
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.ui.theme.ContentTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val PersonAvatarShape = androidx.compose.foundation.shape.CircleShape

@Composable
internal fun PersonAvatar(person: Person, size: androidx.compose.ui.unit.Dp = 28.dp, rounded: Boolean = false) {
    if(rounded) {PersonPhoto(person,Modifier.width(size));return}
    val context = LocalContext.current
    val storageContext=privateStorageContext(context)
    val density=androidx.compose.ui.platform.LocalDensity.current.density
    val thumbnailSize=if(size==28.dp) personThumbnailSize(density,person.avatarZoom) else (size.value*density*person.avatarZoom).toInt().coerceIn(96,2048)
    val bitmap by produceState<Bitmap?>(null, storageContext, person.avatarFile,thumbnailSize) {
        value = withContext(Dispatchers.IO) {
            person.avatarFile?.let { runCatching { AvatarStore(storageContext).readThumbnail(it,thumbnailSize) }.getOrNull() }
        }
    }
    Box(Modifier.size(size).clip(if(rounded) MaterialTheme.shapes.large else PersonAvatarShape).background(Arachn0deColors.ControlSurface).semantics { contentDescription = person.name }, contentAlignment = Alignment.Center) {
        if (bitmap != null) FramedAvatar(bitmap!!, com.r0ybt.arachn0de.domain.model.AvatarFraming(person.avatarZoom, person.avatarX, person.avatarY), Modifier.fillMaxSize(),shape=if(rounded) MaterialTheme.shapes.large else PersonAvatarShape)
        else Text(person.name.take(1).uppercase(), color = Arachn0deColors.PathHighlight, fontSize = 12.sp)
    }
}

@Composable
internal fun ResponsibleAvatars(people: List<Person>, interactive: Boolean = true) {
    if (people.isEmpty()) return
    FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        people.take(3).forEach { person -> var info by remember { mutableStateOf(false) }; if(interactive) TextButton(onClick={info=true},contentPadding=PaddingValues(4.dp)) { PersonAvatar(person) } else PersonAvatar(person);if(info) PersonDetailDialog(person.id) {info=false} }
        if (people.size > 3) {
            var all by remember {mutableStateOf(false)}
            if(interactive) TextButton(onClick={all=true}) {Text("+${people.size - 3}")} else Text("+${people.size - 3}")
            if(all) AlertDialog(onDismissRequest={all=false},title={InformationalTitle("Participantes") {all=false}},text={androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max=360.dp)) {
                items(people.size) {index->ResponsiblePersonLabel(people[index])}
            }},confirmButton={})
        }
    }
}

/** Explicit detail presentation; compact cards deliberately keep their +N treatment. */
@Composable
internal fun ResponsiblePeopleDetail(people: List<Person>) {
    if (people.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag("responsibles-detail")) {
        Text("Responsables", color = Arachn0deColors.TextSecondary, fontSize = 12.sp)
        FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            people.forEach { person -> ResponsiblePersonLabel(person) }
        }
    }
}

/** One shared identity label; future role groups can reuse it without changing avatar rendering. */
@Composable
private fun ResponsiblePersonLabel(person: Person) {
    var detail by remember {mutableStateOf(false)}
    if(detail) PersonDetailDialog(person.id) {detail=false}
    Row(Modifier.heightIn(min=48.dp).clickable {detail=true}.widthIn(max = 240.dp), verticalAlignment = Alignment.CenterVertically) {
        PersonAvatar(person)
        Spacer(Modifier.width(6.dp))
        Text(person.name, color = Arachn0deColors.TextPrimary, fontSize = ContentTypography.Metadata, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
internal fun ResponsibleDialog(nodeId: String, people: List<Person>, assigned: List<Person>, busy: Boolean, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    var selected by rememberSaveable(nodeId, stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it.toList() })) {
        mutableStateOf(assigned.map { it.id })
    }
    var query by rememberSaveable(nodeId) { mutableStateOf("") }
    val filtered = remember(people, query) { people.filter { it.name.contains(query.trim(), ignoreCase = true) } }
    androidx.compose.ui.window.Dialog(onDismissRequest={if(!busy) onDismiss()},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Responsables",style=MaterialTheme.typography.titleLarge)
                OutlinedTextField(query,{query=it},enabled=!busy,label={Text("Buscar personas")},singleLine=true,modifier=Modifier.fillMaxWidth())
                if(people.isEmpty()) Text("No hay Personas. Puedes crearlas desde Personas en el menú.")
                else if(filtered.isEmpty()) Text("No hay Personas que coincidan")
                LazyVerticalGrid(columns=GridCells.Fixed(3),modifier=Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(filtered.sortedWith(compareBy<Person> {it.id !in selected}.thenBy {it.name.lowercase()}.thenBy {it.id}),key={it.id}) {person->
                        ParticipantCard(person,if(person.id in selected) "Seleccionada" else "Disponible",person.id in selected,!busy,modifier=Modifier.testTag("person-choice:${person.id}")) {
                            selected=if(person.id in selected) selected-person.id else selected+person.id
                        }
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled=!busy,onClick=onDismiss,modifier=Modifier.weight(1f)) {Text("Cancelar")}
                    Button(enabled=!busy,onClick={onSave(selected.filter {id->people.any {it.id==id}}.toSet())},modifier=Modifier.weight(1f)) {Text("Guardar")}
                }
            }
        }
    }
}

/** Quantized zoom-aware list decoding, at most 1 MiB of RGBA pixels per thumbnail. */
internal fun personThumbnailSize(density:Float,zoom:Float):Int {
    val needed=28f*density*zoom
    return listOf(96,192,384,512).firstOrNull {it>=needed} ?: 512
}
