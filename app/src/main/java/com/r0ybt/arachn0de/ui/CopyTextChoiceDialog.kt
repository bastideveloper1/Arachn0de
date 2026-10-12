package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.state.NodeCopyActions

@Composable
internal fun CopyTextChoiceDialog(actions: NodeCopyActions) {
    val request = actions.pendingCopy ?: return
    var onlyPending by remember(request) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = actions::cancelChoice,
        title = { Text("Copiar texto con descendientes") },
        text = { Column {
            listOf(false to "Todas", true to "Solo pendientes").forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onlyPending = value },
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = onlyPending == value, onClick = { onlyPending = value })
                    Text(label)
                }
            }
        } },
        confirmButton = { TextButton(onClick = { actions.confirmChoice(onlyPending) }) { Text("Copiar") } },
        dismissButton = { TextButton(onClick = actions::cancelChoice) { Text("Cancelar") } })
}
