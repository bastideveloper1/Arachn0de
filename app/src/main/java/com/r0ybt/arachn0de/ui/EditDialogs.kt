package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

@Composable
internal fun NodeDialog(
    draft: EditorDraft,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by draft::title
    var description by draft::description

    AlertDialog(
        containerColor = Arachn0deColors.Surface,
        titleContentColor = Arachn0deColors.TextPrimary,
        textContentColor = Arachn0deColors.TextSecondary,
        onDismissRequest = if (isSubmitting) ({}) else onDismiss,
        title = { Text(if (draft.id == null) "Nuevo elemento" else "Editar elemento") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (!isSubmitting) title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (!isSubmitting) description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                )
                Text(
                    text = "Un elemento sin hijos funciona como tarea. Si añade hijos, se convierte automáticamente en capa.",
                    color = Arachn0deColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting && title.trim().isNotEmpty(),
                onClick = {
                    val cleanTitle = title.trim()
                    if (!isSubmitting && cleanTitle.isNotEmpty()) {
                        onSave(cleanTitle, description.trim())
                    }
                },
            ) {
                Text(if (isSubmitting) "Guardando..." else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
internal fun ProjectDialog(
    draft: EditorDraft,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by draft::title
    var description by draft::description

    AlertDialog(
        containerColor = Arachn0deColors.Surface,
        titleContentColor = Arachn0deColors.TextPrimary,
        textContentColor = Arachn0deColors.TextSecondary,
        onDismissRequest = if (isSubmitting) ({}) else onDismiss,
        title = { Text(if (draft.id == null) "Nuevo proyecto" else "Editar proyecto") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = !isSubmitting,
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    enabled = !isSubmitting,
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting && name.trim().isNotEmpty(),
                onClick = {
                    val cleanName = name.trim()
                    if (!isSubmitting && cleanName.isNotEmpty()) {
                        onSave(cleanName, description.trim())
                    }
                },
            ) {
                Text(if (isSubmitting) "Guardando..." else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancelar")
            }
        },
    )
}

