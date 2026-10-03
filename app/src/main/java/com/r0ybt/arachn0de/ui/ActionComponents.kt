package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Small action vocabulary; navigation, state controls and links retain their own semantics. */
@Composable
internal fun SecondaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled) { Text(label) }
}

@Composable
internal fun DestructiveAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = modifier.heightIn(min = 48.dp).semantics { stateDescription = "Acción destructiva" }) {
        Icon(Icons.Default.Delete, contentDescription = null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp)); Text(label)
    }
}

/** Existing overflow uses a scrollable modal action list, including on compact phones. */
@Composable
internal fun ActionMenu(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), content = content) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
internal fun ActionMenuItem(label: String, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(text = { Text(label, color = color) }, leadingIcon = { Icon(icon, null, tint = color) },
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp).semantics {
            role = Role.Button
            if (destructive) stateDescription = "Acción destructiva"
        })
}
