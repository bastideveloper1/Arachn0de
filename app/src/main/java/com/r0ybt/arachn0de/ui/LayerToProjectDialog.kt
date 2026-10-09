package com.r0ybt.arachn0de.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.ui.state.OperationState
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun LayerToProjectDialog(repository: ProjectRepository, layerId: String, onDismiss: () -> Unit,
    onConverted: (String) -> Unit, operationScope: CoroutineScope = rememberCoroutineScope()) {
    val operation = remember(repository, operationScope) { OperationState(operationScope) }
    var reason by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!operation.busy) onDismiss() }, title = { Text("Convertir en proyecto") },
        text = { Text("Esta capa dejará de pertenecer al proyecto actual y aparecerá como un proyecto independiente. Se conservarán sus descendientes actuales, relaciones y propiedades. Recuperará su fotografía si tenía una guardada. Los valores heredados se conservarán como valores propios en la nueva raíz.") },
        confirmButton = { TextButton(enabled = !operation.busy, onClick = {
            operation.submit("No se pudo convertir. El contenido permanece intacto; reintenta.", {
                reason = null
                try { result = repository.convertLayerToProject(layerId); true }
                catch (failure: IllegalArgumentException) { reason = failure.message; throw failure }
            }, { onConverted(checkNotNull(result)) })
        }) { Text("Convertir") } },
        dismissButton = { TextButton(enabled = !operation.busy, onClick = onDismiss) { Text("Cancelar") } })
    if (operation.error != null) AlertDialog(onDismissRequest = operation::clearError,
        title = { Text("No se pudo convertir") }, text = { Text(reason ?: checkNotNull(operation.error)) },
        confirmButton = { TextButton(onClick = operation::clearError) { Text("Entendido") } })
}
