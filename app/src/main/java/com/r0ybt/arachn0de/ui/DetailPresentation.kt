package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.NodeProgress
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.ui.theme.ContentTypography

@Composable
internal fun DetailScopeTitle(title: String) {
    Text(title, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("detail-scope-title"),
        color = Arachn0deColors.TextPrimary, fontSize = ContentTypography.ProjectTitle, fontWeight = FontWeight.SemiBold)
}

/** Distinguish actual content from derived work; no extra Room queries or hierarchy traversal. */
internal fun layerContentMessage(hasContent: Boolean, progress: NodeProgress?): String? = when {
    !hasContent -> "Esta capa está vacía"
    progress == null -> null
    progress.total == 0 -> "No hay tareas por realizar"
    !progress.hasPending -> "No hay tareas pendientes"
    else -> null
}

@Composable
internal fun LayerContentStatus(message: String) {
    Text(message, color = Arachn0deColors.TextSecondary, fontSize = ContentTypography.Metadata,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("layer-content-status"))
}
