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
import com.r0ybt.arachn0de.data.repository.TechnologyRepository
import com.r0ybt.arachn0de.data.local.TechnologyEntity
import com.r0ybt.arachn0de.ui.state.LoadState
import com.r0ybt.arachn0de.ui.state.TechnologyActions
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun TechnologiesScreen(repository: TechnologyRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val actions = remember(repository, scope) { TechnologyActions(repository, scope) }
    val load = remember(repository) { LoadState() }
    var catalogReady by remember { mutableStateOf(false) }
    var catalogFailed by remember { mutableStateOf(false) }
    var technologies by remember { mutableStateOf(emptyList<TechnologyEntity>()) }
    LaunchedEffect(repository, load.attempt) { load.collect(repository.state) { technologies = it.catalog; catalogReady = it.loaded; catalogFailed = it.failed } }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var isNew by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(editorId, icon) { editorId?.let { repository.retainIcon(icon, it) } }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val busy = actions.operation.busy
    fun discardDraft() {
        val file = icon
        val owner = editorId
        editorId = null
        scope.launch { repository.discardIcon(file, owner) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && editorId != null) actions.import(uri) { imported ->
            val old = icon
            icon = imported
            scope.launch { repository.discardIcon(old, editorId) }
        }
    }
    BackHandler { if (!busy) { if (editorId != null) discardDraft() else if (deletingId != null) deletingId = null else onBack() } }
    Surface(Modifier.fillMaxSize(), color = Arachn0deColors.Background) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Tecnologías", style = MaterialTheme.typography.headlineSmall)
                TextButton(enabled = !busy, onClick = onBack) { Text("Volver") }
            }
            Button(enabled = !busy && catalogReady, onClick = { editorId = UUID.randomUUID().toString(); isNew = true; name = ""; icon = null }) { Text("Nueva tecnología") }
            if (catalogFailed) Text("No se pudo cargar el catálogo. Reintentando…")
            if (catalogReady && technologies.isEmpty()) Text("Aún no hay tecnologías.")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(technologies, key = { it.id }) { technology ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Arachn0deColors.Surface)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            TechnologyIcon(technology)
                            Spacer(Modifier.width(8.dp))
                            Text(technology.name, modifier = Modifier.weight(1f))
                            TextButton(enabled = !busy && catalogReady, onClick = { editorId = technology.id; isNew = false; name = technology.name; icon = technology.iconFile }) { Text("Editar") }
                            TextButton(enabled = !busy, onClick = { deletingId = technology.id }) { Text("Eliminar") }
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
            title = { Text(if (isNew) "Nueva tecnología" else "Editar tecnología") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, enabled = !busy, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    TechnologyIcon(TechnologyEntity(id, name.ifBlank { "?" }, icon))
                    TextButton(enabled = !busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Seleccionar icono") }
                    if (icon != null) TextButton(enabled = !busy, onClick = {
                        val old = icon; icon = null
                        scope.launch { repository.discardIcon(old, editorId) }
                    }) { Text("Quitar icono") }
                }
            },
            confirmButton = { TextButton(enabled = !busy && name.isNotBlank(), onClick = { actions.save(id, name, icon, isNew) { editorId = null; scope.launch { repository.discardIcon(icon, id) } } }) { Text("Guardar") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { discardDraft() }) { Text("Cancelar") } },
        )
    }
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!busy) deletingId = null }, containerColor = Arachn0deColors.Surface,
            title = { Text("Eliminar tecnología") },
            text = { Text("¿Eliminar ${technologies.firstOrNull { it.id == id }?.name.orEmpty()}? Se desvinculará de proyectos, capas y tareas. Los elementos se conservan.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { actions.delete(id) { deletingId = null } }) { Text("Eliminar") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deletingId = null }) { Text("Cancelar") } },
        )
    }
    OperationErrorDialog(actions.operation)
    LoadErrorDialog(load)
}
