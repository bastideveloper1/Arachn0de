package com.r0ybt.arachn0de.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.r0ybt.arachn0de.ui.state.OperationState

@Composable
internal fun OperationErrorDialog(operation: OperationState) {
    operation.error?.let { message ->
        AlertDialog(
            onDismissRequest = operation::clearError,
            title = { Text("No se pudo completar la operación") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = operation::clearError) { Text("Entendido") }
            },
        )
    }
}
