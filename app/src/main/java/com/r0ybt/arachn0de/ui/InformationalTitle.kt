package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Read-only panels: editors keep their save/cancel actions. */
@Composable internal fun InformationalTitle(title:String,onClose:()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
        IconButton(onClick=onClose,modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Close,"Cerrar")}
    }
}
