package com.r0ybt.arachn0de.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.ui.state.OperationState
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

/** Every surface uses the existing encrypted PersonFavorites record. */
@Composable internal fun PersonFavoriteButton(id:String,favorites:Set<String>?,repository:PersonRepository) {
    val persisted=id in favorites.orEmpty()
    var selected by remember(id,persisted) {mutableStateOf(persisted)}
    val scope=rememberCoroutineScope()
    val operation=remember(id,repository) {OperationState(scope)}
    IconButton(enabled=favorites!=null && !operation.busy,onClick={
        if(!operation.busy) {
            val before=selected;selected=!before
            operation.submit("No se pudo cambiar el favorito.",{
                var saved=false
                try {repository.toggleFavorite(id);saved=true;true} finally {if(!saved)selected=before}
            })
        }
    },modifier=Modifier.size(48.dp).testTag("person-favorite:$id").semantics {this.selected=selected}) {
        Icon(if(selected)Icons.Default.Star else Icons.Default.StarBorder,
            if(selected)"Quitar de favoritos" else "Añadir a favoritos",Modifier.size(24.dp),
            tint=if(selected)Arachn0deColors.Icon else Arachn0deColors.TextSecondary)
    }
    OperationErrorDialog(operation)
}
