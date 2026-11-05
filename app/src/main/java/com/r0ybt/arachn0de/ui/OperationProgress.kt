package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Transient operation feedback. Callers own execution; the visual can be replaced independently. */
@Composable
internal fun OperationProgress(busy:Boolean) {
    if(busy) LinearProgressIndicator(
        modifier=Modifier.fillMaxWidth().testTag("operation-progress"),
        color=MaterialTheme.colorScheme.primary,
        trackColor=MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
internal fun NavigationIndexLoading(onDismiss:()->Unit) {
    ModularPanelDialog(onDismissRequest=onDismiss,compact=true,
        title={androidx.compose.material3.Text("Cargando destinos…")},
        text={OperationProgress(true)},
        confirmButton={androidx.compose.material3.TextButton(onClick=onDismiss) {androidx.compose.material3.Text("Cerrar")}})
}
