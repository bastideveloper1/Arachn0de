package com.r0ybt.arachn0de.ui

import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.border
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.ui.state.OperationState
import com.r0ybt.arachn0de.ui.theme.*

internal val effortNames=listOf("Sin definir","Muy baja","Baja","Media","Alta","Muy alta")
internal fun effortLabel(level:Int)=if(level==0)effortNames[0] else "$level/5 · ${effortNames[level]}"
@Composable internal fun effortColor(level:Int):Color {
    val neutral=Arachn0deColors.ControlSurface
    return when(level) {
        1->Arachn0deColors.Accent.copy(alpha=.45f).compositeOver(neutral)
        2->Arachn0deColors.Accent.copy(alpha=.72f).compositeOver(neutral)
        3->Arachn0deColors.Accent
        4->if(LocalAppearancePalette.current.light)Color(0xffd84315) else Color(0xffff7043)
        5->if(LocalAppearancePalette.current.light)Color(0xffc62828) else Color(0xffff5252)
        else->neutral
    }
}
@Composable internal fun effortIndicatorColor(level:Int):Color=cardIndicatorColor("effort_$level",level in 1..5)

/** Keep the former slot width so the overlaid badge never moves neighbouring metadata. */
@Composable internal fun TaskEffortMini(id:String,level:Int) {
    val density=LocalDensity.current
    val number=rememberTextMeasurer().measure(level.toString(),MaterialTheme.typography.labelSmall).size.width
    val slot=if(level>0)18.dp+with(density) {number.toDp()} else 16.dp
    Box(Modifier.width(slot).height(16.dp).testTag("task-effort:$id").semantics {contentDescription="Esfuerzo: ${effortLabel(level)}"}) {
        Box(Modifier.size(16.dp)) {
            Icon(androidx.compose.material.icons.Icons.Default.LocalFireDepartment,null,Modifier.size(16.dp),tint=effortIndicatorColor(level))
            if(level>0) Box(Modifier.align(androidx.compose.ui.Alignment.BottomEnd).size(9.dp)
                .background(Arachn0deColors.ControlSurface,RoundedCornerShape(1.dp)).border(.5.dp,Arachn0deColors.Outline,RoundedCornerShape(1.dp))
                .testTag("task-effort-badge:$id").semantics(mergeDescendants=true) {},contentAlignment=androidx.compose.ui.Alignment.Center) {
                Text(level.toString(),fontSize=(8f/density.fontScale).sp,lineHeight=(8f/density.fontScale).sp,
                    fontWeight=androidx.compose.ui.text.font.FontWeight.Bold,color=Arachn0deColors.TextPrimary,maxLines=1,
                    style=androidx.compose.ui.text.TextStyle(platformStyle=androidx.compose.ui.text.PlatformTextStyle(includeFontPadding=false)))
            }
        }
    }
}

@Composable internal fun TaskEffortControls(level:Int,enabled:Boolean,onSelect:(Int)->Unit) {
    val active=effortColor(level)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        for(value in 1..5) TextButton(onClick={onSelect(if(value==level)0 else value)},enabled=enabled,
            modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("effort-level:$value")
                .semantics {contentDescription="Esfuerzo $value: ${effortNames[value]}";selected=value==level},
            contentPadding=PaddingValues(horizontal=2.dp)) {
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(3.dp))
                .background(if(value<=level)active else Arachn0deColors.ControlSurface))
        }
    }
    Text(effortLabel(level),style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(bottom=8.dp).testTag("effort-label"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun TaskEffortPanel(id:String,initial:Int,repository:NodeRepository,onClose:()->Unit) {
    val scope=rememberCoroutineScope();val operation=remember(id) {OperationState(scope)}
    var level by remember(id) {mutableIntStateOf(initial)}
    BottomModulesSheet(onDismissRequest={if(!operation.busy)onClose()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=Arachn0deColors.Panel,contentColor=Arachn0deColors.TextPrimary) {
        Column(Modifier.fillMaxWidth().padding(horizontal=12.dp).testTag("effort-panel")) {
            Text("ESFUERZO",style=MaterialTheme.typography.titleMedium)
            TaskEffortControls(level,!operation.busy) {next->
                if(!operation.busy) {
                val before=level;level=next
                operation.submit("No se pudo guardar el esfuerzo.",{
                    var saved=false
                    try {saved=repository.setEffort(id,next);saved} finally {if(!saved)level=before}
                },onSuccess=onClose)
                }
            }
            operation.error?.let {Text(it,color=Arachn0deColors.Destructive,style=MaterialTheme.typography.bodySmall)}
        }
    }
}
