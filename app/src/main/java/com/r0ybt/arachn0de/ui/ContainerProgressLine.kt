package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.NodeProgress
import com.r0ybt.arachn0de.ui.theme.*

/** Presentation only: retain the supplied totals, percentage and optional filtered-view count. */
@Composable internal fun ContainerProgressLine(owner:String,progress:NodeProgress?,visibleTasks:Int?=null) {
    val countTag="task-view-count:${owner.removePrefix("node:")}"
    if(progress==null || progress.total==0) {
        if(visibleTasks!=null) Text("$visibleTasks tareas",Modifier.testTag(countTag),
            style=MaterialTheme.typography.labelSmall,color=Arachn0deColors.TextSecondary)
        return
    }
    val full=if(visibleTasks==null) "${progress.completed}/${progress.total} tareas · ${progress.percentage} %"
        else "$visibleTasks en vista · ${progress.percentage} %"
    val short=if(visibleTasks==null) "${progress.completed}/${progress.total} · ${progress.percentage} %"
        else "$visibleTasks vista · ${progress.percentage} %"
    val style=MaterialTheme.typography.labelSmall
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    BoxWithConstraints(Modifier.widthIn(max=220.dp).padding(top=2.dp,bottom=2.dp)
        .testTag("container-progress:$owner")) {
        val fullWidth=with(density) {measurer.measure(full,style,softWrap=false).size.width.toDp()}
        val shortWidth=with(density) {measurer.measure(short,style,softWrap=false).size.width.toDp()}
        val label=when {
            fullWidth+20.dp<=maxWidth->full
            shortWidth+16.dp<=maxWidth->short
            visibleTasks==null->"${progress.completed}/${progress.total}·${progress.percentage}%"
            else->"$visibleTasks·${progress.percentage}%"
        }
        // Reserve one physical pixel for fractional glyph advances at the trailing edge.
        val labelWidth=with(density) {(measurer.measure(label,style,softWrap=false).size.width+1).toDp()}
        val barWidth=(maxWidth-labelWidth-4.dp).coerceIn(12.dp,48.dp)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
            LinearProgressIndicator(progress={progress.percentage/100f},
                modifier=Modifier.width(barWidth).height(4.dp).testTag("container-progress-bar:$owner"),
                color=Arachn0deColors.Accent,trackColor=Arachn0deColors.ControlSurface,
                strokeCap=androidx.compose.ui.graphics.StrokeCap.Round,drawStopIndicator={})
            Text(label,style=style,color=Arachn0deColors.TextSecondary,maxLines=1,softWrap=false,
                modifier=Modifier.width(labelWidth).testTag(if(visibleTasks!=null)countTag else "container-progress-count:$owner").semantics {
                    contentDescription="${progress.completed} de ${progress.total} tareas completadas · ${progress.percentage} %"+
                        (visibleTasks?.let {" · $it tareas en esta vista"} ?: "")
                })
        }
    }
}
