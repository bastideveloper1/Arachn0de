package com.r0ybt.arachn0de.ui

import android.graphics.Bitmap
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
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
internal fun PersonAvatar(person: Person) {
    val context = LocalContext.current
    val storageContext=privateStorageContext(context)
    val bitmap by produceState<Bitmap?>(null, person.avatarFile) {
        value = withContext(Dispatchers.IO) {
            person.avatarFile?.let { runCatching { AvatarStore(storageContext).readThumbnail(it) }.getOrNull() }
        }
    }
    Box(Modifier.size(28.dp).clip(PersonAvatarShape).background(Arachn0deColors.ControlSurface).semantics { contentDescription = person.name }, contentAlignment = Alignment.Center) {
        if (bitmap != null) FramedAvatar(bitmap!!, com.r0ybt.arachn0de.domain.model.AvatarFraming(person.avatarZoom, person.avatarX, person.avatarY), Modifier.fillMaxSize())
        else Text(person.name.take(1).uppercase(), color = Arachn0deColors.PathHighlight, fontSize = 12.sp)
    }
}

@Composable
internal fun ResponsibleAvatars(people: List<Person>) {
    if (people.isEmpty()) return
    FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        people.take(3).forEach { person -> var info by remember { mutableStateOf(false) }; TextButton(onClick={info=true},contentPadding=PaddingValues(4.dp)) { PersonAvatar(person) };if(info) AlertDialog(onDismissRequest={info=false},title={Text(person.name)},text={Text(people.joinToString {it.name})},confirmButton={TextButton(onClick={info=false}) {Text("Cerrar")}}) }
        if (people.size > 3) {
            var all by remember {mutableStateOf(false)}
            TextButton(onClick={all=true}) {Text("+${people.size - 3}")}
            if(all) AlertDialog(onDismissRequest={all=false},title={Text("Participantes")},text={androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max=360.dp)) {
                items(people.size) {index->ResponsiblePersonLabel(people[index])}
            }},confirmButton={TextButton(onClick={all=false}) {Text("Cerrar")}})
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
    Row(Modifier.widthIn(max = 240.dp), verticalAlignment = Alignment.CenterVertically) {
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
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Arachn0deColors.Surface,
        title = { Text("Responsables") },
        text = {
            if (people.isEmpty()) Text("No hay Personas. Puedes crearlas desde Personas en el menú.")
            else FlowRow(Modifier.fillMaxWidth().heightIn(max=360.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                people.forEach { person ->
                    InputChip(selected=person.id in selected,enabled=!busy,
                        onClick={selected=if(person.id in selected) selected-person.id else selected+person.id},
                        avatar={PersonAvatar(person)},label={Text(person.name,Modifier.widthIn(max=200.dp))},
                        modifier=Modifier.heightIn(min=48.dp))
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { onSave(selected.filter { id -> people.any { it.id == id } }.toSet()) }) { Text("Guardar") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } },
    )
}
