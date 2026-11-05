package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Badge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Checklist
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

/** Header-only projection of the actual hierarchy, independent of list/calendar filters. */
internal data class InteriorTaskCounts(val pending:Set<String>,val highPriority:Set<String>,val verification:Set<String> = emptySet())
internal fun interiorTaskCounts(tree:NodeTreeSnapshot,projectId:String,parentId:String?):InteriorTaskCounts {
    val nodes=tree.nodes.filter {it.projectId==projectId}
    val scope=if(parentId==null)nodes else {
        val children=nodes.groupBy {it.parentId}
        val queue=java.util.ArrayDeque<Node>()
        children[parentId].orEmpty().forEach(queue::addLast)
        val visited=hashSetOf<String>()
        buildList {while(queue.isNotEmpty()) {
            val node=queue.removeFirst()
            if(visited.add(node.id)) {add(node);children[node.id].orEmpty().forEach(queue::addLast)}
        }}
    }
    val unfinished=scope.filter {it.isCompletable && !it.isCompleted}
    val pending=unfinished.filter {it.taskStatus==TaskStatus.PENDING}
    return InteriorTaskCounts(pending.mapTo(hashSetOf()) {it.id},unfinished.filter {it.effectivePriority==Priority.HIGH}.mapTo(hashSetOf()) {it.id},
        unfinished.filter {it.taskStatus==TaskStatus.VERIFY}.mapTo(hashSetOf()) {it.id})
}

@Composable internal fun InteriorIndicatorCount(count:Int,label:String,tag:String,content:@Composable ()->Unit) {
    Box(Modifier.widthIn(min=48.dp).heightIn(min=48.dp).semantics(mergeDescendants=true) {stateDescription="$count $label"}) {
        content()
        if(count>0) Badge(Modifier.align(Alignment.BottomEnd).offset(x=(-2).dp,y=(-2).dp).testTag(tag),
            containerColor=Arachn0deColors.AccentSurface,contentColor=Arachn0deColors.TextPrimary) {
            Text(count.toString(),fontSize=10.sp,maxLines=1,softWrap=false)
        }
    }
}

/** Informational state totals; zero remains explicit and neither entry has a click action. */
@Composable internal fun InteriorTaskStateIndicators(counts:InteriorTaskCounts,verificationEnabled:Boolean,key:String) {
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        @Composable fun entry(status:TaskStatus,count:Int,label:String) {
            InteriorIndicatorCount(count,label,"detail-state-count:$key:${status.name}") {
                Box(Modifier.size(48.dp).testTag("detail-task-state:$key:${status.name}")
                    .semantics(mergeDescendants=true) {stateDescription="$count $label"},contentAlignment=Alignment.Center) {
                    CompactCardGlyph {
                        val tint=cardIndicatorColor(if(status==TaskStatus.VERIFY)"verify" else "tasks",count>0)
                        if(status==TaskStatus.VERIFY) androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(com.r0ybt.arachn0de.R.drawable.lucide_scan_eye),label,Modifier.size(18.dp),tint=tint)
                        else LucideIcon(androidx.compose.material.icons.Icons.Default.Checklist,label,Modifier.size(18.dp),tint=tint)
                    }
                }
            }
        }
        entry(TaskStatus.PENDING,counts.pending.size,"tareas pendientes")
        if(verificationEnabled) entry(TaskStatus.VERIFY,counts.verification.size,"tareas por verificar")
    }
}
