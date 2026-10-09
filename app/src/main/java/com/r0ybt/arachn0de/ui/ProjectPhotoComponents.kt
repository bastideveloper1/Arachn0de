package com.r0ybt.arachn0de.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.AvatarStore
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.OperationState
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private val ProjectPhotoShape = RoundedCornerShape(12.dp)

/** Fixed geometry before/after decoding; only composed visible identities load a cached thumbnail. */
@Composable
internal fun ProjectIdentityIcon(project: Project, modifier: Modifier = Modifier.size(40.dp)) {
    val context = LocalContext.current
    val storageContext=privateStorageContext(context)
    val file = project.photo?.file
    val bitmap by produceState<Bitmap?>(null, file) {
        value = null
        value = withContext(Dispatchers.IO) { file?.let { runCatching { AvatarStore(storageContext, "project-photos").readThumbnail(it) }.getOrNull() } }
    }
    Box(modifier.clip(ProjectPhotoShape).background(Arachn0deColors.Primary).testTag("project-photo:${project.id}"), contentAlignment = Alignment.Center) {
        bitmap?.let { FramedAvatar(it, project.photo?.framing ?: AvatarFraming(), Modifier.fillMaxSize().semantics {
            contentDescription = "Fotografía de ${project.name}"
        }, shape = ProjectPhotoShape) } ?: Text(project.name.take(1).uppercase(), color = Arachn0deColors.OnPrimary, fontWeight = FontWeight.Bold)
    }
}

/** Separate property editor; confirmation updates its draft, Guardar commits the photograph. */
@Composable
internal fun ProjectPhotoDialog(project: Project, onClose: () -> Unit,
    repository: com.r0ybt.arachn0de.data.repository.ProjectPhotoRepository = (LocalContext.current.applicationContext as Arachn0deApplication).projectPhotoRepository) {
    val scope = rememberCoroutineScope()
    val operation = remember(scope) { OperationState(scope) }
    val owner = rememberSaveable(project.id) { UUID.randomUUID().toString() }
    var file by rememberSaveable(project.id) { mutableStateOf(project.photo?.file) }
    var pending by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var held by rememberSaveable(project.id) { mutableStateOf(listOfNotNull(project.photo?.file)) }
    var zoom by rememberSaveable(project.id) { mutableFloatStateOf(project.photo?.framing?.zoom ?: 1f) }
    var x by rememberSaveable(project.id) { mutableFloatStateOf(project.photo?.framing?.x ?: 0f) }
    var y by rememberSaveable(project.id) { mutableFloatStateOf(project.photo?.framing?.y ?: 0f) }
    var crop by rememberSaveable(project.id) { mutableStateOf(false) }
    val framing = AvatarFraming(zoom, x, y)
    val ready by produceState(false, owner) {
        try { held.forEach { repository.retain(it, owner) }; value = true }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { operation.submit("No se pudo proteger el borrador. Reabre el editor para reintentar.", { throw failure }) }
    }
    fun close() { operation.submit("No se pudo cancelar. Tus archivos siguen protegidos; reintenta.", {
        repository.discard(held.toSet(), owner); true
    }, onClose) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            var imported: String? = null
            operation.submit("No se pudo importar la fotografía. Selecciona una imagen de hasta 20 MiB.", {
                imported = repository.import(uri, owner); true
            }, { pending = imported; held = (held + checkNotNull(imported)).distinct(); crop = true })
        }
    }
    val busy = operation.busy
    val editorReady = ready
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text("Fotografía del proyecto") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            ProjectIdentityIcon(project.copy(photo = file?.let { ProjectPhoto("draft", it, framing) }), Modifier.size(80.dp))
            TextButton(enabled = !busy && editorReady, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if (file == null) "Seleccionar fotografía" else "Reemplazar fotografía") }
            if (file != null) {
                TextButton(enabled = !busy && editorReady, onClick = { crop = true }) { Text("Ajustar encuadre") }
                TextButton(enabled = !busy && editorReady, onClick = { file = null; zoom = 1f; x = 0f; y = 0f }) { Text("Quitar fotografía") }
            }
        } },
        confirmButton = { TextButton(enabled = !busy && editorReady, onClick = {
            operation.submit("No se pudo guardar la fotografía. Se conserva el borrador; reintenta.", {
                repository.save(project.id, file, framing, owner)
            }, {
                // Successful persistence must not be reported as failed because cleanup needs retry.
                operation.submit("La fotografía se guardó, pero queda limpieza pendiente. Reintenta cerrar.", {
                    repository.discard(held.toSet(), owner); true
                }, onClose)
            })
        }) { Text(if (busy) "Guardando…" else "Guardar fotografía") } },
        dismissButton = { TextButton(enabled = !busy, onClick = ::close) { Text("Cancelar") } })
    if (crop) (pending ?: file)?.let { selected ->
        AvatarEditor(selected, if (pending != null) AvatarFraming() else framing, onConfirm = {
            pending?.let { file = it }; pending = null; zoom = it.zoom; x = it.x; y = it.y; crop = false
        }, onCancel = {
            val cancelled = pending; pending = null; crop = false
            if (cancelled != null) operation.submit("No se pudo liberar la selección cancelada. Reintenta cerrar.", {
                repository.discard(setOf(cancelled), owner); true
            }, { held = held - cancelled })
        }, directoryName = "project-photos", title = "Encuadrar fotografía", shape = ProjectPhotoShape)
    }
    OperationErrorDialog(operation)
}
