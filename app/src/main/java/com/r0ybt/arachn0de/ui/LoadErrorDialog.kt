package com.r0ybt.arachn0de.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.r0ybt.arachn0de.ui.state.LoadState

@Composable
internal fun LoadErrorDialog(state: LoadState) {
    if (state.failed) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("No se pudieron cargar los datos") },
            text = { Text("La última información disponible se conserva. Vuelve a intentar la lectura.") },
            confirmButton = {
                TextButton(onClick = state::retry) { Text("Reintentar") }
            },
        )
    }
}
