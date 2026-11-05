package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.FirstBaseline
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.NodeProgress
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.ui.theme.ContentTypography

@Composable
internal fun DetailScopeTitle(title: String, compact:Boolean=false, level:Int?=null, layer:Boolean=false, onEdit: (() -> Unit)? = null) {
    val bashCursor=rememberBashTextCursor()
    var firstLineCenter by remember { mutableFloatStateOf(0f) }
    Row(Modifier.fillMaxWidth().padding(top=0.dp).heightIn(min=48.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        if(layer) OfficialIcon(com.r0ybt.arachn0de.R.drawable.bw90_layer,"Capa",size=16.dp,
            modifier=Modifier.size(16.dp).alignBy {it.measuredHeight/2})
        else PropertyIdentityIcon(modifier=Modifier.size(16.dp).alignBy {it.measuredHeight/2})
        Text(if(LocalBashPresentation.current) "title=$title" else title, modifier = Modifier.weight(1f).testTag("detail-scope-title").then(bashCursor.modifier).heightIn(min=if(compact)0.dp else 48.dp)
            .alignBy {if(firstLineCenter>0f) firstLineCenter.roundToInt() else it[FirstBaseline]}
            .then(if (onEdit != null) Modifier.clickable(onClickLabel = "Editar título", onClick = onEdit) else Modifier),
        onTextLayout={layout->bashCursor.layout=layout;firstLineCenter=(layout.getLineTop(0)+layout.getLineBottom(0))/2f},
        color = Arachn0deColors.TextPrimary, fontSize = ContentTypography.ProjectTitle, fontWeight = FontWeight.SemiBold)
    }
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
