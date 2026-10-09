package com.r0ybt.arachn0de.metro

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow

/** Consultation only: this action neither saves a plan nor starts a session. */
@Composable
internal fun MetroRoutePreview(nodeId: String, label: String) {
    TextButton(onClick = { MetroNavigation.open(nodeId) }, modifier = Modifier.fillMaxWidth().testTag("metro-preview:$nodeId")) {
        Column {
            Text("Ver recorrido · $label", maxLines = 2, overflow = TextOverflow.Ellipsis)
            LocalMetroPreview.current[nodeId]?.let {Text(it,maxLines=2,overflow=TextOverflow.Ellipsis,style=androidx.compose.material3.MaterialTheme.typography.labelSmall)}
        }
    }
}
