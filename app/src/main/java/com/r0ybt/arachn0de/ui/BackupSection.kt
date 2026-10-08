package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.backup.BackupDocuments
import com.r0ybt.arachn0de.ui.state.BackupActions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun BackupSection(onRestored: () -> Unit, backupRepository: com.r0ybt.arachn0de.backup.BackupRepository? = null) {
    val context = LocalContext.current
    val repository = backupRepository ?: (context.applicationContext as Arachn0deApplication).backupRepository
    val documents = remember(context) { BackupDocuments(context.contentResolver) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val scope = rememberCoroutineScope()
    val actions = remember(repository, documents, scope) { BackupActions(repository, documents, scope) }
    var pendingName by rememberSaveable { mutableStateOf<String?>(null) }
    val create = rememberLauncherForActivityResult(com.r0ybt.arachn0de.backup.CreateBackupDocument()) { uri ->
        val name = pendingName
        pendingName = null
        if (uri == null) actions.cancelSave(name)
        else if (name != null) actions.save(name, uri)
    }
    val open = rememberLauncherForActivityResult(com.r0ybt.arachn0de.backup.OpenBackupDocument()) { uri ->
        if (uri != null) actions.inspect(uri)
    }
    LaunchedEffect(actions) { actions.recover() }
    BackHandler(enabled = actions.busy) { }
    HorizontalDivider()
    Text("Backup y restauración", style = MaterialTheme.typography.titleMedium)
    Text("Incluye todos tus datos y avatares. El archivo no está cifrado.")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !actions.busy && pendingName == null, onClick = {
            actions.create { file ->
                pendingName = file.name
                try {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                    create.launch("Arachn0de-Backup-$date.arachnode")
                } catch (failure: Exception) { pendingName = null; throw failure }
            }
        }) { Text("Crear backup") }
        OutlinedButton(enabled = !actions.busy && pendingName == null, onClick = {
            try { open.launch(arrayOf("application/octet-stream", "application/json", "*/*")) }
            catch (_: Exception) { actions.noticeSelectionFailed() }
        }) { Text("Restaurar backup") }
    }
    if (actions.busy) Text("Procesando backup…")
    actions.notice?.let { Text(it) }
    actions.candidate?.let { data ->
        AlertDialog(
            onDismissRequest = actions::dismiss,
            title = { Text("¿Reemplazar todos los datos?") },
            text = { Text("Este backup contiene ${data.projects.size} proyectos, ${data.nodes.size} elementos y ${data.persons.size} personas, ${data.recurrenceRules.size} reglas de recurrencia y ${data.attachmentFiles.size} adjuntos y ${data.technologies.size} tecnologías. Restaurarlo reemplazará todos los datos actuales de Arachn0de.\n\nSi quieres conservar el estado actual, cancela y pulsa Crear backup antes de restaurar.") },
            confirmButton = { Button(enabled = !actions.busy, onClick = { actions.restore {
                check(mainHandler.post {
                    android.widget.Toast.makeText(context, "Backup restaurado correctamente.", android.widget.Toast.LENGTH_LONG).show()
                    onRestored()
                })
            } }) { Text("Reemplazar y restaurar") } },
            dismissButton = { TextButton(enabled = !actions.busy, onClick = actions::dismiss) { Text("Cancelar") } },
        )
    }
}
