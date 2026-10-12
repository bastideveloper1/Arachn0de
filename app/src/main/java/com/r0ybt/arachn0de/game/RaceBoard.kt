package com.r0ybt.arachn0de.game

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.R
import kotlinx.coroutines.launch

@Composable internal fun RaceBoard(s: RaceSession,viewer: Int,art: Map<String,ImageBitmap>,modifier: Modifier=Modifier,
    selectable: Set<Int> = emptySet(),selected: Set<Int> = emptySet(),preview: Set<Int> = emptySet(),onSelect: ((Int)->Unit)?=null) {
    val horizontal=rememberScrollState(); val vertical=rememberScrollState(); val scope=rememberCoroutineScope()
    val density=LocalDensity.current
    var follow by remember { mutableStateOf(true) }; var occupants by remember { mutableStateOf<Int?>(null) }
    val cell=64.dp; val visible=s.visible(viewer); val local=s.players[viewer]
    val focus=s.track.tile(local.tile)
    val centerX=with(density) { (focus.x*cell.toPx()-120.dp.toPx()).toInt().coerceAtLeast(0) }
    val centerY=with(density) { (focus.y*cell.toPx()-100.dp.toPx()).toInt().coerceAtLeast(0) }
    LaunchedEffect(local.tile,follow) { if(follow) { horizontal.animateScrollTo(centerX); vertical.animateScrollTo(centerY) } }
    LaunchedEffect(viewer) { occupants=null; follow=true }
    val eyeZone=s.eyes.firstOrNull { it.owner==viewer }?.zone.orEmpty()
    val blastZone=s.dynamites.flatMap { it.zone }.toSet()
    Column(modifier) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={follow=!follow}) { Text(if(follow)"Seguir · activado" else "Explorar") }
            TextButton(onClick={follow=true; scope.launch { horizontal.animateScrollTo(centerX); vertical.animateScrollTo(centerY) }}) { Text("Centrar en ti") }
            Text("→ Sentido de avance",style=MaterialTheme.typography.labelSmall)
        }
        Box(Modifier.weight(1f).fillMaxWidth().border(1.dp,MaterialTheme.colorScheme.outline)) {
            Box(Modifier.fillMaxSize().horizontalScroll(horizontal).verticalScroll(vertical)) {
                Box(Modifier.requiredSize(cell*13,cell*13)) {
                    Image(painterResource(R.drawable.fondo_cesped),null,Modifier.matchParentSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
                    val coords=s.track.tiles.map { it.x to it.y }.toSet()
                    for(x in 0..12) for(y in 0..12) if(x to y !in coords && s.track.tiles.any { kotlin.math.abs(it.x-x)+kotlin.math.abs(it.y-y)<=2 }) {
                        Image(painterResource(R.drawable.bosque),null,Modifier.offset(cell*x,cell*y).size(cell),contentScale=androidx.compose.ui.layout.ContentScale.Fit)
                    }
                    s.track.tiles.forEach { t ->
                        val highlighted=t.id in selectable || t.id in preview || t.id in eyeZone || t.id in blastZone
                        val border=when { t.id in selected -> Color.Yellow; t.id in preview || t.id in blastZone -> Color(0xFFFF7043); t.id in eyeZone -> Color(0xFF42A5F5); t.id in selectable -> Color.White; else -> Color.Transparent }
                        Box(Modifier.offset(cell*t.x,cell*t.y).size(cell).testTag("race-tile-${t.id}").border(if(highlighted || t.id in selected)3.dp else 0.dp,border).clickable { if(onSelect!=null)onSelect(t.id) else occupants=t.id }) {
                            RaceImage(art,if(t.id in s.track.boxes)"sendero_con_caja.png" else "sendero.png",if(t.id==0)"Salida y meta" else "Casilla ${t.id+1}",Modifier.fillMaxSize())
                            Text(if(t.id==0)"META\nSALIDA" else "${t.id+1}",Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha=.7f)),color=Color.White,fontSize=9.sp,lineHeight=10.sp)
                            if(t.next.size==2) Text("⑂",Modifier.align(Alignment.BottomEnd).background(Color.Black),color=Color.White)
                            s.traps.filter { it.tile==t.id }.firstOrNull()?.let { RaceImage(art,RaceItem.CALTROPS.file,"Trampa · ${it.expiresRound-s.round} rondas",Modifier.align(Alignment.BottomStart).size(23.dp)) }
                            s.dynamites.filter { it.center==t.id }.forEach { d -> Row(Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha=.7f))) { RaceImage(art,RaceItem.DYNAMITE.file,"Dinamita",Modifier.size(22.dp)); Text("#${d.owner+1}:1",color=Color.White,fontSize=9.sp) } }
                            s.eyes.filter { it.owner==viewer && it.center==t.id }.forEach { RaceImage(art,RaceItem.EYE.file,"Tu vigilancia",Modifier.align(Alignment.BottomCenter).size(22.dp)) }
                        }
                    }
                    val line=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f)
                    Canvas(Modifier.matchParentSize()) {
                        val px=cell.toPx()
                        s.track.tiles.forEach { t -> t.next.forEach { id -> val end=s.track.tile(id)
                            val a=Offset((t.x+.5f)*px,(t.y+.5f)*px); val b=Offset((end.x+.5f)*px,(end.y+.5f)*px)
                            drawLine(line,a,b,3.dp.toPx()); val mid=Offset(a.x*.25f+b.x*.75f,a.y*.25f+b.y*.75f)
                            val dx=b.x-a.x; val dy=b.y-a.y; val norm=kotlin.math.sqrt(dx*dx+dy*dy)
                            if(norm>0) { val ux=dx/norm; val uy=dy/norm; val size=8.dp.toPx()
                                drawLine(line,mid,Offset(mid.x-ux*size-uy*size*.5f,mid.y-uy*size+ux*size*.5f),2.dp.toPx())
                                drawLine(line,mid,Offset(mid.x-ux*size+uy*size*.5f,mid.y-uy*size-ux*size*.5f),2.dp.toPx()) }
                        } }
                    }
                    // Only the visibility projection determines occupancy, layout, animation and camera focus.
                    visible.forEach { p ->
                        val t=s.track.tile(p.tile); val others=visible.filter { it.tile==p.tile && it.id!=viewer }.sortedBy { it.id }
                        val index=others.indexOfFirst { it.id==p.id }.coerceAtLeast(0)
                        val angle=index*2*Math.PI/maxOf(others.size,1)
                        val isLocal=p.id==viewer
                        val dx=if(isLocal)12.dp else (23+22*kotlin.math.cos(angle)).dp
                        val dy=if(isLocal)12.dp else (23+22*kotlin.math.sin(angle)).dp
                        val x by animateDpAsState(cell*t.x+dx,label="racer x")
                        val y by animateDpAsState(cell*t.y+dy,label="racer y")
                        val alpha by animateFloatAsState(if(p.invisible && isLocal).5f else 1f,label="cloak")
                        var shake by remember(p.id) { mutableStateOf(0f) }; var flash by remember(p.id) { mutableStateOf(false) }
                        LaunchedEffect(p.stunned,s.event) { if(p.stunned) { repeat(4) { shake=if(it%2==0)3f else -3f; flash=!flash; delay(75) }; shake=0f; flash=false } }
                        val hit by animateFloatAsState(shake,label="impact")
                        Box(Modifier.offset(x+hit.dp,y).size(if(isLocal)40.dp else 22.dp).alpha(alpha).testTag("race-token-${p.id}")
                            .then(if(p.barrier || flash) Modifier.border(2.dp,Color.White,CircleShape).padding(2.dp) else Modifier)
                            .border(if(isLocal)3.dp else 2.dp,RaceColors[p.id],CircleShape)
                            .clickable { if(onSelect!=null)onSelect(p.tile) else occupants=p.tile }) {
                            RaceImage(art,p.character.file,"#${p.id+1} ${p.character.label}${if(isLocal)" · Tú" else ""}${if(p.stunned)" · aturdido" else ""}",Modifier.fillMaxSize())
                            Text("${p.id+1}${if(isLocal)" Tú" else ""}",Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha=.8f)),color=Color.White,fontSize=if(isLocal)9.sp else 8.sp,lineHeight=9.sp)
                        }
                    }
                }
            }
        }
        if(onSelect!=null) Text("Blanco: alcanzable · amarillo: elegido · naranja: zona fijada. Toca casillas.",style=MaterialTheme.typography.labelSmall)
    }
    occupants?.let { tile -> AlertDialog(onDismissRequest={occupants=null},title={Text("Ocupantes · ${if(tile==0)"Salida/meta" else "casilla ${tile+1}"}")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())) {
        val here=visible.filter { it.tile==tile }
        if(here.isEmpty())Text("Sin corredores visibles")
        here.forEach { p -> Row(Modifier.fillMaxWidth().padding(4.dp),verticalAlignment=Alignment.CenterVertically) {
            RaceImage(art,p.character.file,p.character.label,Modifier.size(44.dp).border(2.dp,RaceColors[p.id],CircleShape))
            Column(Modifier.padding(start=8.dp)) { Text("#${p.id+1} ${p.character.label}${if(p.id==viewer)" · Tú" else ""}"); Text(listOfNotNull(if(p.stunned)"Aturdido" else null,if(p.barrier)"Barrera" else null,if(p.invisible)"Invisible · detección privada" else null).joinToString(" · ").ifEmpty { "Preparado" },style=MaterialTheme.typography.bodySmall) }
        } }
    }},confirmButton={TextButton(onClick={occupants=null}) { Text("Cerrar") }}) }
}
