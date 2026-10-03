package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.data.repository.TagRepository
import kotlinx.coroutines.launch

@Composable internal fun TagChips(tags: List<Tag>) {
    if (tags.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.take(2).forEach { SuggestionChip(onClick = {}, label = { Text(it.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 100.dp)) }) }
        if (tags.size > 2) SuggestionChip(onClick = {}, label = { Text("+${tags.size - 2}") })
    }
}
@Composable internal fun TagSelector(tags: List<Tag>, selected: Set<String>, onChange: (Set<String>) -> Unit,
    single: Boolean = false, repository: TagRepository? = null, enabled: Boolean = true) {
    var open by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    OutlinedButton(enabled = enabled, onClick = { open = true }) { Text("Etiquetas: " + (tags.filter { it.id in selected }.let { picked -> picked.take(2).joinToString { it.name } + if (picked.size > 2) " +${picked.size - 2}" else "" }.ifEmpty { if (single) "Todas" else "Ninguna" }), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Etiquetas") }, text = {
        Column {
            OutlinedTextField(search, { search = it }, label = { Text("Buscar o crear etiqueta") }, singleLine = true)
            LazyColumn(Modifier.heightIn(max = 240.dp)) {
                items(tags.filter { it.normalizedName.contains(search.trim().lowercase(java.util.Locale.ROOT)) }, key = { it.id }) { tag ->
                    TextButton(onClick = { onChange(if (single) setOf(tag.id) else if (tag.id in selected) selected - tag.id else selected + tag.id) }) {
                        Text((if (tag.id in selected) "✓ " else "") + tag.name)
                    }
                }
            }
            TextButton(onClick = { onChange(emptySet()) }) { Text(if (single) "Todas las etiquetas" else "Quitar todas") }
            if (repository != null && search.isNotBlank()) TextButton(onClick = { scope.launch {
                runCatching { repository.create(search) }.onSuccess { onChange(if (single) setOf(it.id) else selected + it.id); search = "" }
                    .onFailure { error = it.message }
            } }) { Text("Crear y seleccionar") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text("Listo") } })
}
@Composable internal fun TagManager(repository: TagRepository, state: TagState) {
    var open by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Tag?>(null) }
    var deleting by remember { mutableStateOf<Tag?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    TextButton(onClick = { open = true }) { Text("Gestionar etiquetas") }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Etiquetas globales") }, text = { Column {
        OutlinedTextField(search, { search = it }, label = { Text("Buscar") }, singleLine = true)
        LazyColumn(Modifier.heightIn(max = 320.dp)) { items(state.tags.filter { it.name.contains(search, true) }, key = { it.id }) { tag ->
            Column { Text("${tag.name} · ${state.usage(tag.id)} usos")
                Row { TextButton(onClick = { editing = tag; name = tag.name }) { Text("Renombrar") }
                    TextButton(onClick = { deleting = tag }) { Text("Eliminar") } }
            }
        } }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(onClick = { open = false }) { Text("Cerrar") } })
    editing?.let { tag -> AlertDialog(onDismissRequest = { editing = null }, title = { Text("Renombrar etiqueta") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { scope.launch {
            runCatching { repository.rename(tag.id, name) }.onSuccess { editing = null; error = null }.onFailure { error = it.message; editing = null }
        } }) { Text("Guardar") } }) }
    deleting?.let { tag -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Eliminar etiqueta") },
        text = { Text("${tag.name}: se quitará de ${state.usage(tag.id)} nodos y plantillas. Los nodos se conservan.") },
        confirmButton = { TextButton(onClick = { scope.launch { runCatching { repository.delete(tag.id) }.onFailure { error = it.message }; deleting = null } }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }) }
}
