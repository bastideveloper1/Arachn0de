package com.r0ybt.arachn0de.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.TechnologyEntity
import com.r0ybt.arachn0de.data.local.TechnologyIconStore
import com.r0ybt.arachn0de.ui.state.TechnologyActions
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun TechnologyIcon(technology: TechnologyEntity) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, technology.iconFile) {
        value = withContext(Dispatchers.IO) { technology.iconFile?.let { runCatching { TechnologyIconStore(context).readThumbnail(it) }.getOrNull() } }
    }
    Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Arachn0deColors.ControlSurface)
        .semantics { contentDescription = technology.name }, contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Text(technology.name.take(1).uppercase(), style = MaterialTheme.typography.labelSmall)
    }
}

/** A single shared catalog state serves every card; selection writes only reference tables. */
@Composable
internal fun TechnologyOwnerControl(ownerId: String, project: Boolean, ownerName: String,
    repositoryOverride: com.r0ybt.arachn0de.data.repository.TechnologyRepository? = null) {
    val repository = repositoryOverride ?: (LocalContext.current.applicationContext as? Arachn0deApplication)?.technologyRepository ?: return
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val actions = remember(repository, scope) { TechnologyActions(repository, scope) }
    var open by rememberSaveable(ownerId, project) { mutableStateOf(false) }
    val assigned = state.forOwner(ownerId, project)
    TextButton(onClick = { open = true }, enabled = state.loaded && !actions.operation.busy,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp), modifier = Modifier.heightIn(min = 40.dp)
            .semantics { contentDescription = "Tecnologías de $ownerName" }) {
        Icon(Icons.Default.Code, null, Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        if (assigned.isEmpty()) Text("Tecnologías", style = MaterialTheme.typography.labelSmall)
        else Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            assigned.take(3).forEach { TechnologyIcon(it) }
            if (assigned.size > 3) Text("+${assigned.size - 3}", style = MaterialTheme.typography.labelSmall)
        }
    }
    if (open && state.loaded) TechnologyAssignmentDialog(ownerId, project, state.catalog, assigned,
        actions.operation.busy, { open = false }) { ids -> actions.assign(ownerId, project, ids) { open = false } }
    OperationErrorDialog(actions.operation)
}

@Composable
internal fun TechnologyAssignmentDialog(ownerId: String, project: Boolean, catalog: List<TechnologyEntity>,
    assigned: List<TechnologyEntity>, busy: Boolean, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    var selected by rememberSaveable(ownerId, project, stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it })) {
        mutableStateOf(assigned.map { it.id })
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, containerColor = Arachn0deColors.Surface,
        title = { Text("Tecnologías") }, text = {
            if (catalog.isEmpty()) Text("No hay tecnologías. Puedes crearlas desde Tecnologías en el menú.")
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(catalog, key = { it.id }) { technology ->
                    Row(Modifier.fillMaxWidth().toggleable(technology.id in selected, enabled = !busy, role = Role.Checkbox) {
                        selected = if (it) selected + technology.id else selected - technology.id
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(technology.id in selected, onCheckedChange = null)
                        TechnologyIcon(technology); Spacer(Modifier.width(8.dp)); Text(technology.name)
                    }
                }
            }
        }, confirmButton = { TextButton(enabled = !busy, onClick = { onSave(selected.filter { id -> catalog.any { it.id == id } }.toSet()) }) { Text("Guardar") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}
