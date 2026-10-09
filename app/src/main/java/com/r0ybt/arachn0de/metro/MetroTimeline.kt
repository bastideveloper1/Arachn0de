package com.r0ybt.arachn0de.metro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal val MetroRed=Color(0xffff7369)
internal val MetroGreen=Color(0xff69d695)
internal data class MetroTimelineNode(val station: String, val line: String, val detail: String = "", val emphasized: Boolean = false)

/** No item spacing: every segment ends exactly at its measured row boundary.
 * Nodes have one consistent inset; row height comes from accessible Compose content.
 * The marker uses LazyList measured offsets (including offscreen neighbour geometry). */
@Composable
internal fun MetroVerticalTimeline(
    rows: List<MetroTimelineNode>, net: MetroNetwork, modifier: Modifier = Modifier,
    preferences: MetroPreferences? = null, list: LazyListState = rememberLazyListState(),
    progress: Pair<Int,Float>? = null, avatar: (@Composable ()->Unit)? = null,
    onStation: (String)->Unit, extra: @Composable (Int)->Unit = {}
) {
    val density=LocalDensity.current
    val nodeInset=with(density) { 30.dp.toPx() }
    val trackX=with(density) { 22.dp.toPx() }
    val surface=Arachn0deColors.Surface
    val markerSize=with(density) { 36.dp.toPx() }
    Box(modifier.clipToBounds()) {
        LazyColumn(Modifier.fillMaxSize().testTag("metro-timeline"),state=list) {
            itemsIndexed(rows,key={ i,row->"$i:${row.station}:${row.line}" }) { index,row->
                val line=net.lines.getValue(row.line)
                val classification=line.express.getOrNull(line.stations.indexOf(row.station))
                val complete=progress!=null && index<progress.first
                val fraction=if(progress?.first==index) progress.second else 0f
                Row(Modifier.fillMaxWidth().testTag("metro-row-$index").drawBehind {
                    val color=Color(line.color)
                    val top=if(index==0) nodeInset else 0f
                    val bottom=if(index==rows.lastIndex) nodeInset else size.height
                    val done=androidx.compose.ui.graphics.lerp(color,surface,0.62f)
                    drawLine(if(complete) done else color,Offset(trackX,top),Offset(trackX,bottom),5.dp.toPx())
                    if(progress!=null && progress.first==index) {
                        drawLine(done,Offset(trackX,top),Offset(trackX,(nodeInset+size.height*fraction).coerceAtMost(bottom)),5.dp.toPx())
                    } else if(progress!=null && index==progress.first+1) {
                        val previous=list.layoutInfo.visibleItemsInfo.firstOrNull {it.index==progress.first}
                        if(previous!=null) {
                            val edge=nodeInset-previous.size*(1-progress.second)
                            if(edge>top) drawLine(done,Offset(trackX,top),Offset(trackX,edge.coerceAtMost(nodeInset)),5.dp.toPx())
                        }
                    }
                    when(classification) {
                        "R" -> drawCircle(MetroRed,8.dp.toPx(),Offset(trackX,nodeInset))
                        "V" -> drawCircle(MetroGreen,8.dp.toPx(),Offset(trackX,nodeInset))
                        "C" -> {
                            val radius=8.dp.toPx()
                            drawArc(MetroRed,90f,180f,true,topLeft=Offset(trackX-radius,nodeInset-radius),size=androidx.compose.ui.geometry.Size(radius*2,radius*2))
                            drawArc(MetroGreen,-90f,180f,true,topLeft=Offset(trackX-radius,nodeInset-radius),size=androidx.compose.ui.geometry.Size(radius*2,radius*2))
                        }
                        else -> drawCircle(color,8.dp.toPx(),Offset(trackX,nodeInset))
                    }
                }) {
                    Spacer(Modifier.width(48.dp))
                    OutlinedCard(Modifier.weight(1f).padding(bottom=6.dp),
                        shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,if(row.emphasized) Color(line.color) else MaterialTheme.colorScheme.outlineVariant),
                        colors=CardDefaults.outlinedCardColors(containerColor=if(row.emphasized) Arachn0deColors.SurfaceRaised else Arachn0deColors.Surface)) {
                        Column(Modifier.padding(horizontal=12.dp,vertical=10.dp).heightIn(min=40.dp)) {
                            Column(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable {onStation(row.station)}) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(net.stations.getValue(row.station).name+(if(preferences?.home==row.station) " · Casa" else "")+(if(row.station in preferences?.favorites.orEmpty()) " ★" else ""),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                                if(classification!=null) Column(Modifier.padding(start=6.dp)) {MetroClassification(classification)}
                                Text("›",Modifier.padding(start=6.dp),style=MaterialTheme.typography.titleLarge)
                            }
                            LineBadges(net,row.station)
                            if(row.station in preferences?.restrictions?.closed.orEmpty()) Text("Cerrada a pasajeros",color=MetroRed,style=MaterialTheme.typography.labelMedium)
                            if(row.station in preferences?.restrictions?.avoided.orEmpty()) Text("Preferencia: evitar",style=MaterialTheme.typography.labelMedium)
                            if(row.detail.isNotEmpty()) Text(row.detail,style=MaterialTheme.typography.bodySmall)
                            }
                            extra(index)
                        }
                    }
                }
            }
        }
        if(progress!=null && avatar!=null) {
            val item=list.layoutInfo.visibleItemsInfo.firstOrNull { it.index==progress.first }
            if(item!=null) {
                val next=list.layoutInfo.visibleItemsInfo.firstOrNull { it.index==progress.first+1 }
                val start=item.offset+nodeInset
                val end=(next?.offset ?: (item.offset+item.size))+nodeInset
                val y=if(progress.first==rows.lastIndex) start else start+(end-start)*progress.second.coerceIn(0f,1f)
                Box(Modifier.offset {IntOffset((trackX-markerSize/2).roundToInt(),(y-markerSize/2).roundToInt())}.size(36.dp).testTag("metro-avatar"),contentAlignment=Alignment.Center) {avatar()}
            }
        }
    }
}

@Composable internal fun MetroClassification(value: String) {
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        if(value=="R" || value=="C") Text("Roja",color=MetroRed,style=MaterialTheme.typography.labelMedium)
        if(value=="C") Text("/",color=MetroRed,style=MaterialTheme.typography.labelMedium)
        if(value=="V" || value=="C") Text("Verde",color=MetroGreen,style=MaterialTheme.typography.labelMedium)
    }
}
@Composable internal fun LineBadges(net: MetroNetwork, station: String) {
    val lines=net.accesses(station)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
        lines.forEach { line->Surface(color=Color(line.color).copy(alpha=0.18f),shape=RoundedCornerShape(5.dp)) {Text(line.id,Modifier.padding(horizontal=5.dp,vertical=2.dp),color=Color(line.color),style=MaterialTheme.typography.labelMedium)} }
        if(lines.size>1) Text("Combinación",style=MaterialTheme.typography.labelSmall)
    }
}
