package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.domain.model.Person
import com.r0ybt.arachn0de.ui.state.LoadState
import com.r0ybt.arachn0de.ui.state.PersonActions
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun PeopleScreen(repository: PersonRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val actions = remember(repository, scope) { PersonActions(repository, scope) }
    val load = remember(repository) { LoadState() }
    var people by remember { mutableStateOf(emptyList<Person>()) }
    LaunchedEffect(repository, load.attempt) { load.collect(repository.observePeople()) { people = it } }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var isNew by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val busy = actions.operation.busy
    fun discardDraft() {
        val file = avatar
        editorId = null
        scope.launch { repository.discardAvatar(file) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && editorId != null) actions.import(uri) { imported ->
            val old = avatar
            avatar = imported
            scope.launch { repository.discardAvatar(old) }
        }
    }
    BackHandler { if (!busy) { if (editorId != null) discardDraft() else if (deletingId != null) deletingId = null else onBack() } }
    Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Personas", style = MaterialTheme.typography.headlineSmall)
                TextButton(enabled = !busy, onClick = onBack) { Text("Volver") }
            }
            Button(enabled = !busy, onClick = { editorId = UUID.randomUUID().toString(); isNew = true; name = ""; avatar = null }) { Text("Nueva Persona") }
            if (people.isEmpty()) Text("Aún no hay Personas.")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(people, key = { it.id }) { person ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            PersonAvatar(person)
                            Spacer(Modifier.width(8.dp))
                            Text(person.name, modifier = Modifier.weight(1f))
                            TextButton(enabled = !busy, onClick = { editorId = person.id; isNew = false; name = person.name; avatar = person.avatarFile }) { Text("Editar") }
                            TextButton(enabled = !busy, onClick = { deletingId = person.id }) { Text("Eliminar") }
                        }
                    }
                }
            }
        }
    }
    editorId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!busy) discardDraft() },
            containerColor = Arachn0deColors.Surface,
            title = { Text(if (isNew) "Nueva Persona" else "Editar Persona") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, enabled = !busy, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    PersonAvatar(Person(id, name.ifBlank { "?" }, avatar))
                    TextButton(enabled = !busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Seleccionar avatar") }
                    if (avatar != null) TextButton(enabled = !busy, onClick = {
                        val old = avatar; avatar = null
                        scope.launch { repository.discardAvatar(old) }
                    }) { Text("Quitar avatar") }
                }
            },
            confirmButton = { TextButton(enabled = !busy && name.isNotBlank(), onClick = { actions.save(id, name, avatar, isNew) { editorId = null } }) { Text("Guardar") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { discardDraft() }) { Text("Cancelar") } },
        )
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!busy) deletingId = null }, containerColor = Arachn0deColors.Surface,
            title = { Text("Eliminar Persona") },
            text = { Text("¿Eliminar ${people.firstOrNull { it.id == id }?.name.orEmpty()}? Se quitarán sus asignaciones. Los elementos se conservan.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { actions.delete(id) { deletingId = null } }) { Text("Eliminar") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deletingId = null }) { Text("Cancelar") } },
        )
    }
    OperationErrorDialog(actions.operation)
    LoadErrorDialog(load)
}
