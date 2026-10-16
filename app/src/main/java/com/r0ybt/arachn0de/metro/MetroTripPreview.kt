package com.r0ybt.arachn0de.metro

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal object MetroTripPreview {
    fun facts(route:MetroRoute):String {
        val lines=route.steps.filter {it.kind==MetroStepKind.RIDE}.map {it.line}.distinct()
        val changes=route.steps.count {it.kind==MetroStepKind.TRANSFER || it.kind==MetroStepKind.CHANGE}
        return "≈ ${route.minutes} min · ${lines.joinToString(" / ")} · $changes combinaciones"
    }
    fun status(journey:MetroJourney)=when {
        journey.data.active?.pausedAt!=null->"Pausado"
        journey.data.active!=null->"Activo"
        journey.data.sessions.lastOrNull()?.cancelled==true->"Cancelado"
        journey.data.sessions.lastOrNull()?.ended!=null->"Finalizado"
        else->"Guardado"
    }
}
@Composable internal fun MetroActions(tag:String,busy:Boolean,actions:List<Pair<String,()->Unit>>) {
    var open by remember(tag) {mutableStateOf(false)}
    Box {
        IconButton(enabled=!busy,onClick={open=true},modifier=Modifier.size(48.dp).testTag("metro-actions:$tag")) {Icon(Icons.Default.MoreVert,"Opciones · $tag")}
        DropdownMenu(expanded=open,onDismissRequest={open=false}) {
            actions.forEach {(label,action)->DropdownMenuItem(text={Text(label)},onClick={open=false;action()})}
        }
    }
}
@Composable internal fun JourneyMenu(journey:MetroJourney,busy:Boolean,onEdit:()->Unit,onDuplicate:()->Unit,onDelete:()->Unit) {
    val actions=buildList {
        add("Editar viaje" to onEdit);add("Duplicar" to onDuplicate)
        if(journey.data.active==null) add("Eliminar viaje" to onDelete)
    }
    MetroActions(journey.row.id,busy,actions)
}
