package com.r0ybt.arachn0de.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.r0ybt.arachn0de.ui.state.NodeSortMode

@Composable
internal fun NodeSortMenu(mode: NodeSortMode, onSelect: (NodeSortMode) -> Unit, onDismiss: () -> Unit, projectDefault: Boolean = false, onInherit: (() -> Unit)? = null) {
    ActionMenu(if (projectDefault) "Orden predeterminado del Proyecto" else "Ordenar", onDismiss) {
        if (projectDefault) Text("Las Capas sin orden propio utilizan este criterio.", style = MaterialTheme.typography.bodySmall)
        onInherit?.let { inherit -> ActionMenuItem("Usar orden predeterminado del Proyecto", Icons.AutoMirrored.Filled.Sort, { inherit(); onDismiss() }) }
        Text("El orden de visualización conserva las posiciones manuales.", style = MaterialTheme.typography.bodySmall)
        NodeSortMode.entries.forEach { option ->
            ActionMenuItem(option.label, if (option == mode) Icons.Default.Check else Icons.AutoMirrored.Filled.Sort,
                { onSelect(option); onDismiss() }, modifier = Modifier.semantics { selected = option == mode })
        }
    }
}
