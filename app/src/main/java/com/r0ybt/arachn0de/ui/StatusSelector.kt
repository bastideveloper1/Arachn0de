package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.TaskStatus
import com.r0ybt.arachn0de.domain.model.WorkState
import com.r0ybt.arachn0de.ui.theme.*

internal data class StatusSelectorItem(val key:String,val label:String,val color:Color,val icon:ImageVector)
@Composable internal fun taskStatusItems(enabled:Boolean):List<StatusSelectorItem> {
    val clock=ImageVector.vectorResource(com.r0ybt.arachn0de.R.drawable.lucide_clock)
    val check=ImageVector.vectorResource(com.r0ybt.arachn0de.R.drawable.lucide_check)
    return TaskStatus.available(enabled).map {
    StatusSelectorItem(it.name,it.label,when(it) {TaskStatus.PENDING->Color(0xFFB5BBC6);TaskStatus.VERIFY->Color(0xFFE2AD55);TaskStatus.COMPLETED->Color(0xFF70BC90)},when(it) {TaskStatus.PENDING->Icons.Default.RadioButtonUnchecked;TaskStatus.VERIFY->clock;TaskStatus.COMPLETED->check})
}
}
@Composable internal fun workStatusItems()=WorkState.entries.map {
    StatusSelectorItem(it.name,it.label,semanticColor(when(it) {WorkState.UNPLANNED->SemanticColors.SprintUnplanned;WorkState.PLANNED->SemanticColors.SprintPlanned;WorkState.DOING->SemanticColors.SprintDoing;WorkState.DONE->SemanticColors.SprintDone;WorkState.VALIDATED->SemanticColors.SprintValidated}),when(it) {WorkState.UNPLANNED->Icons.Default.RadioButtonUnchecked;WorkState.PLANNED->Icons.Default.Event;WorkState.DOING->Icons.Default.PlayArrow;WorkState.DONE->Icons.Default.Check;WorkState.VALIDATED->Icons.Default.DoneAll})
}

/** Only this wrap-content capsule owns pointer input. A selected item toggles back to all. */
@Composable internal fun StatusSelector(items:List<StatusSelectorItem>,selected:String?,onSelect:(String?)->Unit,modifier:Modifier=Modifier,
    direction:StatusSelectorDirection=LocalAppearancePreferences.current?.settings?.statusSelectorDirection ?: StatusSelectorDirection.VERTICAL) {
    BoxWithConstraints(modifier) {
    val horizontalCount=((maxWidth.value-8)/50).toInt().coerceAtLeast(1)
    val rows=if(direction==StatusSelectorDirection.HORIZONTAL) items.chunked(horizontalCount) else if(items.size>3) items.chunked(minOf(2,horizontalCount)) else items.map {listOf(it)}
    Column(Modifier.background(Color(0xFF30343B),RoundedCornerShape(28.dp)).padding(4.dp).testTag("status-selector"),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        rows.forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(2.dp)) {row.forEach {item->
            Box(Modifier.size(48.dp).background(if(selected==item.key) Color.White.copy(alpha=.13f) else Color.Transparent,CircleShape)
                .selectable(selected==item.key,role=Role.RadioButton,onClick={onSelect(item.key.takeUnless {it==selected})})
                .semantics {contentDescription="Filtrar: ${item.label}";stateDescription=if(selected==item.key) "Filtro activo; tocar para mostrar todos" else "Filtro inactivo"}
                .testTag("status-filter:${item.key}"),contentAlignment=Alignment.Center) {
                Box(Modifier.size(24.dp).background(item.color,CircleShape),contentAlignment=Alignment.Center) {
                    Icon(item.icon,null,Modifier.size(18.dp),tint=if(item.color.luminance()>.35f) Color(0xFF22252A) else Color.White)
                }
            }
        }}}
    }
}
}
