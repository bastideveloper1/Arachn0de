package com.r0ybt.arachn0de.ui

import android.graphics.Bitmap
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
    val bitmap by produceState<Bitmap?>(null, person.avatarFile) {
        value = withContext(Dispatchers.IO) {
            person.avatarFile?.let { runCatching { AvatarStore(context).readThumbnail(it) }.getOrNull() }
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        people.take(3).forEach { PersonAvatar(it) }
        if (people.size > 3) Text("+${people.size - 3}", color = Arachn0deColors.TextSecondary, fontSize = 12.sp)
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
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(people, key = { it.id }) { person ->
                    Row(Modifier.fillMaxWidth().toggleable(value = person.id in selected, enabled = !busy, role = Role.Checkbox) {
                        selected = if (it) selected + person.id else selected - person.id
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(person.id in selected, onCheckedChange = null)
                        PersonAvatar(person)
                        Spacer(Modifier.width(8.dp))
                        Text(person.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { onSave(selected.filter { id -> people.any { it.id == id } }.toSet()) }) { Text("Guardar") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } },
    )
}
