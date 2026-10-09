package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

@Composable
internal fun CardSignals(future:Int,today:Int,high:Int,source:String) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        if(future>0) Icon(Icons.Default.Timer,"$source: $future con vencimiento futuro",Modifier.size(20.dp),tint=Arachn0deColors.Primary)
        if(today>0) Icon(Icons.Default.HourglassBottom,"$source: $today vencen hoy",Modifier.size(20.dp),tint=Arachn0deColors.Primary)
        if(high>0) Icon(Icons.Default.LocalFireDepartment,"$source: $high de prioridad alta",Modifier.size(20.dp),tint=Arachn0deColors.Primary)
    }
}

@Composable
internal fun NodeCardSignals(node:Node,now:Long) {
    if(!node.isCompletable || node.isCompleted) return
    val zone=java.util.TimeZone.getDefault()
    val today=CalendarDates.localDay(now,zone)
    val day=node.dueAt?.let {CalendarDates.localDay(it,zone)}
    CardSignals(if(node.dueAt!=null && node.dueAt>=TemporalRanges.day(today,zone).endExclusive) 1 else 0,if(day==today) 1 else 0,
        if(node.effectivePriority==Priority.HIGH) 1 else 0,"Esta tarea")
}
