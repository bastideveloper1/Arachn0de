package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

internal enum class HeadingKind { CORE, LAYER, TASK, NOTE, SECTION }
internal data class HeadingLocation(val name:String,val kind:HeadingKind,val core:String?=null)
internal val LocalReturnHeading=staticCompositionLocalOf<HeadingLocation?> {null}

/** Presentation mirror only; names and validity always come from the current catalog. */
internal fun headingLocation(project:Project?,nodeId:String?,nodes:Map<String,Node>):HeadingLocation? {
    project ?: return null
    if(nodeId==null) return HeadingLocation(project.name,HeadingKind.CORE)
    val node=nodes[nodeId]?.takeIf {it.projectId==project.id} ?: return null
    return HeadingLocation(node.title,when(node.purpose) {
        NodePurpose.LAYER->HeadingKind.LAYER
        NodePurpose.NOTE->HeadingKind.NOTE
        NodePurpose.ACTION->HeadingKind.TASK
    },project.name.takeIf {it!=node.title})
}

@Composable internal fun NavigationHeading(title:String,modifier:Modifier=Modifier,
    style:TextStyle=MaterialTheme.typography.titleMedium.copy(fontSize=16.sp),color:Color=LocalContentColor.current,
    context:HeadingLocation?=LocalReturnHeading.current,returning:Boolean=LocalReturnHeading.current!=null,titleTag:String="navigation-heading",showTitle:Boolean=true) {
    val shownContext=context?.takeIf {returning || it.name!=title}
    val returnLine=returning && shownContext!=null
    Column(modifier,verticalArrangement=Arrangement.spacedBy(if(returnLine) 2.dp else 0.dp,Alignment.CenterVertically)) {
        if(showTitle) Row {
            // Same space as the two 12-dp contextual icons and their 3-dp gaps.
            if(returnLine) Spacer(Modifier.width(30.dp))
            Text(title,maxLines=1,overflow=TextOverflow.Ellipsis,
                style=if(returning) style.copy(fontSize=16.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold) else style,
                color=if(returning) Arachn0deColors.TextPrimary else color,modifier=Modifier.weight(1f).testTag(titleTag))
        }
        shownContext?.let {location->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp),modifier=Modifier.testTag("navigation-context")) {
                if(returning) Icon(Icons.Default.Undo,"Regresar a",Modifier.size(12.dp),tint=Arachn0deColors.TextSecondary)
                else Icon(Icons.Default.SubdirectoryArrowRight,null,Modifier.size(12.dp),tint=Arachn0deColors.TextSecondary)
                val icon=when(location.kind) {
                    HeadingKind.CORE->Icons.Default.Layers
                    HeadingKind.LAYER->Icons.Default.AccountTree
                    HeadingKind.TASK->Icons.Default.Checklist
                    HeadingKind.NOTE->Icons.Default.Description
                    HeadingKind.SECTION->Icons.Default.Menu
                }
                Icon(icon,null,Modifier.size(12.dp),tint=Arachn0deColors.TextSecondary)
                Text(location.name+(location.core?.let {"  ›  ◈ $it"} ?: ""),maxLines=1,overflow=TextOverflow.Ellipsis,
                    fontSize=if(returning) 16.sp else 11.sp,lineHeight=if(returning) 20.sp else 13.sp,
                    fontWeight=FontWeight.Normal,color=Arachn0deColors.TextSecondary,modifier=Modifier.weight(1f).testTag("navigation-context-text"))
            }
        }
    }
}
