package com.r0ybt.arachn0de.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.data.local.AvatarStore
import com.r0ybt.arachn0de.domain.model.Person
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PersonAvatar(person: Person) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, person.avatarFile) {
        value = withContext(Dispatchers.IO) {
            person.avatarFile?.let { runCatching { AvatarStore(context).read(it) }.getOrNull() }
        }
    }
    Box(Modifier.size(28.dp).clip(CircleShape).background(Arachn0deColors.ControlSurface).semantics { contentDescription = person.name }, contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
