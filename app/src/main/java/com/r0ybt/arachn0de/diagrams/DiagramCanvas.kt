package com.r0ybt.arachn0de.diagrams

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

internal enum class DiagramGesture { MOVE,SELECT,PAN,CONNECT,MULTI,GROUP }
internal fun moveDiagramNode(n:DiagramNode,dx:Float,dy:Float,resize:Boolean,snap:Boolean,step:Float=.025f):DiagramNode {
    fun grid(v:Float)=if(snap)(v/step).roundToInt()*step else v
    return if(resize)n.copy(w=grid(n.w+dx).coerceIn(minOf(n.w,.025f),1-n.x),h=grid(n.h+dy).coerceIn(minOf(n.h,.025f),1-n.y))
    else n.copy(x=grid(n.x+dx).coerceIn(0f,1-n.w),y=grid(n.y+dy).coerceIn(0f,1-n.h))
}
internal fun moveDiagramSelection(d:DiagramDocument,ids:Set<String>,dx:Float,dy:Float,snap:Boolean=false,step:Float=.025f):DiagramDocument {
    val group=d.nodes.filter {it.id in ids};if(group.isEmpty())return d
    val first=group.first();val gx=if(snap)((first.x+dx)/step).roundToInt()*step-first.x else dx;val gy=if(snap)((first.y+dy)/step).roundToInt()*step-first.y else dy
    val x=gx.coerceIn(-group.minOf {it.x},1-group.maxOf {it.x+it.w});val y=gy.coerceIn(-group.minOf {it.y},1-group.maxOf {it.y+it.h})
    return d.copy(nodes=d.nodes.map {if(it.id in ids)it.copy(x=(it.x+x).coerceAtLeast(0f),y=(it.y+y).coerceAtLeast(0f)) else it})
}
internal fun diagramMoveLimits(d:DiagramDocument,ids:Set<String>,dx:Float,dy:Float):Set<DiagramHandle> {
    val group=d.nodes.filter {it.id in ids};if(group.isEmpty())return emptySet()
    return buildSet {if(group.minOf {it.x}+dx<0)add(DiagramHandle.LEFT);if(group.maxOf {it.x+it.w}+dx>1)add(DiagramHandle.RIGHT);if(group.minOf {it.y}+dy<0)add(DiagramHandle.TOP);if(group.maxOf {it.y+it.h}+dy>1)add(DiagramHandle.BOTTOM)}
}
@Composable internal fun DiagramCanvas(d:DiagramDocument,renderer:DiagramRenderer,selected:String?,edge:String?,gesture:DiagramGesture,presenting:Boolean,playing:Boolean,snap:Boolean,onSelect:(String?,String?)->Unit,onChange:(DiagramDocument)->Unit,modifier:Modifier,fitRequest:Int=0,previewEdge:String?=null,live:Boolean=false,playbackToken:Int=0,
    selection:Set<String> = emptySet(),showNumbers:Boolean=false,edgeOrder:List<String> = d.edges.map {it.id},grid:Boolean=false,gridStep:Float=.025f,guides:Boolean=false,magnetic:Boolean=false,
    fitPage:Boolean=false,onBoundary:()->Unit={},placing:Boolean=false,onPlace:(Offset)->Unit={}) {
    val density=LocalDensity.current.density;val palette=com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette.current
    var stage by remember {mutableStateOf(IntSize.Zero)}
    var cx by rememberSaveable(d.id) {mutableFloatStateOf(d.camera?.cx ?: .5f)};var cy by rememberSaveable(d.id) {mutableFloatStateOf(d.camera?.cy ?: .5f)};var scale by rememberSaveable(d.id) {mutableFloatStateOf(d.camera?.scale ?: 0f)}
    fun camera()=DiagramCamera(cx,cy,scale.coerceAtLeast(.01f))
    fun setCamera(c:DiagramCamera) {cx=c.cx;cy=c.cy;scale=c.scale}
    fun viewport(w:Float,h:Float)=diagramViewport(w,h,density,camera(),d.width,d.height)
    var lastFit by rememberSaveable(d.id) {mutableIntStateOf(0)}
    LaunchedEffect(stage,fitRequest) {if(stage.width>0&&stage.height>0&&(scale==0f||lastFit!=fitRequest)) {
        val c=if(fitPage)fitDiagramPage(d,stage.width/density,stage.height/density) else fitDiagram(d,stage.width/density,stage.height/density);setCamera(c);onChange(d.copy(camera=c));lastFit=fitRequest
    }}
    LaunchedEffect(d.camera,d.page) {d.camera?.let(::setCamera)}
    var seconds by rememberSaveable(d.id) {mutableFloatStateOf(0f)}
    val previewDocument=remember(d,previewEdge) {if(previewEdge==null)d else d.copy(playback=d.playback.copy(mode=DiagramAnimationMode.SEGMENT,route=listOf(previewEdge)))}
    val timeline=remember(previewDocument) {diagramTimeline(previewDocument)}
    val animating=presenting||live;val animated=previewDocument.playback.mode!=DiagramAnimationMode.OFF&&previewDocument.edges.any {it.flow.enabled}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(playing,animating,animated,lifecycle,previewDocument.playback,d.edges) {
        if(playing&&animating&&animated&&android.animation.ValueAnimator.areAnimatorsEnabled())lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last=0L
            while(android.animation.ValueAnimator.areAnimatorsEnabled()) {withFrameNanos {now->if(last!=0L)seconds+=(now-last)/1_000_000_000f;last=now}
                if(!previewDocument.playback.loop&&seconds>timeline.onceSeconds())break
                if(previewDocument.playback.mode!=DiagramAnimationMode.SEQUENTIAL&&previewDocument.edges.filter {it.flow.enabled}.none {it.flow.loop}&&seconds>timeline.onceSeconds())break
            }
        }
    }
    LaunchedEffect(animating,playbackToken) {seconds=0f}
    var draft by remember(d.id) {mutableStateOf<DiagramDocument?>(null)}
    var dragged by remember {mutableStateOf<String?>(null)};var limits by remember {mutableStateOf(emptySet<DiagramHandle>())}
    var verticalGuide by remember {mutableStateOf<Float?>(null)};var horizontalGuide by remember {mutableStateOf<Float?>(null)}
    var pendingLine by remember {mutableStateOf<Pair<Offset,Offset>?>(null)}
    val current by rememberUpdatedState(d);val select by rememberUpdatedState(onSelect);val change by rememberUpdatedState(onChange);val chosen by rememberUpdatedState(selected);val chosenEdge by rememberUpdatedState(edge);val ids by rememberUpdatedState(selection);val boundary by rememberUpdatedState(onBoundary);val place by rememberUpdatedState(onPlace)
    val effective=if(presenting)DiagramGesture.PAN else gesture
    val badges=remember(d,edgeOrder,stage,cx,cy,scale,showNumbers,presenting) {if(showNumbers&&!presenting)diagramBadges(d,edgeOrder,viewport(stage.width.toFloat(),stage.height.toFloat()),stage.width.toFloat(),stage.height.toFloat(),12*density) else emptyList()}
    val activeBadges by rememberUpdatedState(badges)
    val gestures=Modifier.pointerInput(effective,snap,gridStep,guides,magnetic,d.width,d.height,placing) {
        awaitEachGesture {
            val down=awaitFirstDown();val original=current;val view=viewport(size.width.toFloat(),size.height.toFloat());val downAt=down.position
            fun point(at:Offset)=Offset((at.x-view.left)/view.width,(at.y-view.top)/view.height)
            if(effective==DiagramGesture.PAN) {
                val before=camera()
                try {do {val event=awaitPointerEvent();val z=event.calculateZoom();val pan=event.calculatePan();if(z!=1f||pan!=Offset.Zero) {setCamera(panDiagram(camera(),pan,z,event.calculateCentroid(false),size.width.toFloat(),size.height.toFloat(),density,original.width,original.height));event.changes.forEach {it.consume()}}}while(event.changes.any {it.pressed})
                    if(!presenting)change(original.copy(camera=camera()))
                } catch(t:Throwable) {setCamera(before);throw t}
                return@awaitEachGesture
            }
            val p=point(downAt)
            val hit=original.nodes.lastOrNull {p.x in it.x..it.x+it.w&&p.y in it.y..it.y+it.h}
            val selectedNode=original.nodes.firstOrNull {it.id==chosen}
            val handle=if(effective==DiagramGesture.SELECT&&(hit==null||hit.id==selectedNode?.id))selectedNode?.let {diagramHandleAt(it,downAt,view,20.dp.toPx())} else null
            val node=if(handle!=null)selectedNode else hit
            val connection=original.edges.firstOrNull {it.id==chosenEdge}
            val bend=if(node==null)connection?.points?.indexOfFirst {q->(Offset(view.left+q.x*view.width,view.top+q.y*view.height)-downAt).getDistance()<20.dp.toPx()}?.takeIf {it>=0} else null
            fun screen(p:Offset)=Offset(view.left+p.x*view.width,view.top+p.y*view.height)
            fun endpoint(start:Boolean):Offset?=connection?.let {e->val m=android.graphics.PathMeasure(edgePath(original,e),false);val out=FloatArray(2);m.getPosTan(if(start)0f else m.length,out,null);Offset(out[0]/original.width,out[1]/original.height)}
            val endpointStart=if(effective==DiagramGesture.CONNECT&&connection!=null)listOf(true,false).minByOrNull {start->(screen(endpoint(start)!!)-downAt).getDistance()}?.takeIf {start->(screen(endpoint(start)!!)-downAt).getDistance()<=24.dp.toPx()} else null
            val sourceSide=if(effective==DiagramGesture.CONNECT&&endpointStart==null)selectedNode?.let {n->DiagramAnchor.entries.minByOrNull {side->(screen(diagramAnchorPoint(n,side))-downAt).getDistance()}?.takeIf {side->(screen(diagramAnchorPoint(n,side))-downAt).getDistance()<=24.dp.toPx()}} else null
            val source=if(sourceSide!=null)selectedNode else null
            val moving=if(effective==DiagramGesture.MULTI&&node?.id in ids)ids else setOfNotNull(node?.id)
            var last=downAt;var started=false;var released=false;var notified=false
            try {
                while(true) {
                    val event=awaitPointerEvent();val eventPointer=event.changes.firstOrNull {it.id==down.id} ?: break
                    if(eventPointer.isConsumed||event.changes.count {it.pressed}>1)break
                    last=eventPointer.position
                    if(!eventPointer.pressed) {released=true;break}
                    val delta=last-downAt
                    if(!started&&delta.getDistance()>viewConfiguration.touchSlop) {started=true;dragged=node?.id}
                    if(!started||placing)continue
                    val dx=delta.x/view.width;val dy=delta.y/view.height
                    if(effective==DiagramGesture.CONNECT&&(source!=null||endpointStart!=null)) {
                        val from=if(endpointStart!=null)endpoint(!endpointStart) else source?.let {diagramAnchorPoint(it,sourceSide!!)}
                        if(from!=null) {pendingLine=from to point(last);eventPointer.consume()};continue
                    }
                    if(effective==DiagramGesture.CONNECT&&bend==null)continue
                    if(effective==DiagramGesture.GROUP) {
                        draft=translateDiagramContent(original,dx,dy);eventPointer.consume()
                    } else if(handle!=null&&node!=null) {
                        val aligned=diagramGestureAlign(original,node,dx,dy,handle,snap,gridStep,magnetic,8.dp.toPx()/view.width,8.dp.toPx()/view.height)
                        verticalGuide=aligned.second;horizontalGuide=aligned.third
                        draft=original.copy(nodes=original.nodes.map {if(it.id==node.id)aligned.first else it})
                        limits=diagramResizeLimits(node,dx,dy,handle)
                        eventPointer.consume()
                    } else if(node!=null) {
                        var moved=moveDiagramSelection(original,moving,dx,dy,snap,gridStep)
                        if(moving.size==1) {val aligned=diagramGestureAlign(original,node,dx,dy,null,snap,gridStep,magnetic,8.dp.toPx()/view.width,8.dp.toPx()/view.height);verticalGuide=aligned.second;horizontalGuide=aligned.third;moved=moved.copy(nodes=moved.nodes.map {if(it.id==node.id)aligned.first else it})}
                        draft=moved;limits=diagramMoveLimits(original,moving,dx,dy);eventPointer.consume()
                    } else if(bend!=null&&connection!=null) {
                        val q=point(last);draft=original.copy(edges=original.edges.map {if(it.id==connection.id)it.copy(points=it.points.mapIndexed {i,v->if(i==bend)DPoint(q.x.coerceIn(0f,1f),q.y.coerceIn(0f,1f)) else v}) else it});eventPointer.consume()
                    }
                    // Blank SELECT drags never pan. PAN is an explicit, visible tool.
                    if(limits.isNotEmpty()&&!notified) {boundary();notified=true}
                }
                if(released) {
                    if(placing&&!started&&view.contains(last))place(point(last))
                    else if(effective==DiagramGesture.CONNECT&&started&&(source!=null||endpointStart!=null)) {
                        val q=point(last)
                        val target=original.nodes.lastOrNull {n->q.x in (n.x-24.dp.toPx()/view.width)..(n.x+n.w+24.dp.toPx()/view.width)&&q.y in (n.y-24.dp.toPx()/view.height)..(n.y+n.h+24.dp.toPx()/view.height)}
                        if(target!=null) {
                            val side=diagramNearestAnchor(target,q,view.width,view.height)
                            if(endpointStart!=null&&connection!=null) {val next=reconnectDiagram(original,connection,endpointStart,target.id,side);if(next!=original)change(next)}
                            else if(source!=null&&target.id!=source.id) {
                                val e=DiagramEdge(from=source.id,to=target.id,fromAnchor=sourceSide,toAnchor=side)
                                change(original.copy(edges=original.edges+e));select(null,e.id)
                            }
                        }
                    } else if(started) {draft?.let {change(it);if(effective!=DiagramGesture.MULTI&&effective!=DiagramGesture.GROUP&&node!=null)select(node.id,null)}}
                    else {
                        val badge=activeBadges.lastOrNull {(it.point-last).getDistance()<24.dp.toPx()}
                        val edgeHit=if(hit==null&&view.contains(last))original.edges.lastOrNull {e->val m=android.graphics.PathMeasure(edgePath(original,e),false);val out=FloatArray(2);(0..80).any {i->m.getPosTan(m.length*i/80,out,null);hypot((out[0]/original.width-p.x)*view.width,(out[1]/original.height-p.y)*view.height)<20.dp.toPx()}} else null
                        select(if(badge==null)hit?.id else null,badge?.edgeId ?: edgeHit?.id)
                    }
                }
            } finally {draft=null;pendingLine=null;dragged=null;limits=emptySet();verticalGuide=null;horizontalGuide=null}
        }
    }
    Canvas(modifier.onSizeChanged {stage=it}.clipToBounds().then(gestures).testTag("diagram-canvas")) {
        drawRect(palette.background)
        val v=viewport(size.width,size.height);val shown=draft ?: d
        drawContext.canvas.nativeCanvas.apply {save();translate(v.left,v.top);scale(v.width/d.width,v.height/d.height)
            val renderDoc=if(animating)shown.copy(playback=previewDocument.playback) else shown
            renderer.draw(this,renderDoc,if(animating)seconds else null);restore()}
        if(!presenting||fitPage) {
            val outline=if(android.graphics.Color.luminance(d.background)>.45f)Color(0xff65758b) else Color(0xff8b9db2)
            drawRect(outline,Offset(v.left,v.top),androidx.compose.ui.geometry.Size(v.width,v.height),style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        }
        if(!presenting) {
            fun border(h:DiagramHandle,a:Offset,b:Offset) {if(h in limits)drawLine(Color(0xffffb454),a,b,4.dp.toPx())}
            border(DiagramHandle.LEFT,Offset(v.left,v.top),Offset(v.left,v.top+v.height));border(DiagramHandle.RIGHT,Offset(v.left+v.width,v.top),Offset(v.left+v.width,v.top+v.height));border(DiagramHandle.TOP,Offset(v.left,v.top),Offset(v.left+v.width,v.top));border(DiagramHandle.BOTTOM,Offset(v.left,v.top+v.height),Offset(v.left+v.width,v.top+v.height))
            if(grid) {var sx=v.width*gridStep;var sy=v.height*gridStep;while(sx<12.dp.toPx())sx*=2;while(sy<12.dp.toPx())sy*=2;var x=((v.left%sx)+sx)%sx;while(x<size.width) {drawLine(palette.muted.copy(alpha=.3f),Offset(x,0f),Offset(x,size.height));x+=sx};var y=((v.top%sy)+sy)%sy;while(y<size.height) {drawLine(palette.muted.copy(alpha=.3f),Offset(0f,y),Offset(size.width,y));y+=sy}}
            if(guides) {com.r0ybt.arachn0de.wireframe.wireCompositionLines(com.r0ybt.arachn0de.wireframe.WireComposition.CENTER,size.width,size.height).forEach {(a,b)->drawLine(palette.primary.copy(alpha=.4f),Offset(a.x*size.width,a.y*size.height),Offset(b.x*size.width,b.y*size.height))}}
            verticalGuide?.let {x->drawLine(palette.primary,Offset(v.left+x*v.width,0f),Offset(v.left+x*v.width,size.height))};horizontalGuide?.let {y->drawLine(palette.primary,Offset(0f,v.top+y*v.height),Offset(size.width,v.top+y*v.height))}
            pendingLine?.let {(a,b)->drawLine(palette.primary,Offset(v.left+a.x*v.width,v.top+a.y*v.height),Offset(v.left+b.x*v.width,v.top+b.y*v.height),2.dp.toPx())}
            shown.nodes.filter {it.id in selection||it.id==selected||it.id==dragged}.forEach {n->
                drawRect(palette.primary,Offset(v.left+n.x*v.width,v.top+n.y*v.height),androidx.compose.ui.geometry.Size(n.w*v.width,n.h*v.height),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                if(n.id==(dragged ?: selected)&&(effective==DiagramGesture.CONNECT||effective==DiagramGesture.SELECT))(if(effective==DiagramGesture.CONNECT)DiagramAnchor.entries.map {diagramAnchorPoint(n,it)} else nodeHandles(n).values).forEach {p->val at=Offset(v.left+p.x*v.width,v.top+p.y*v.height);drawCircle(palette.raised,5.dp.toPx(),at);drawCircle(palette.primary,5.dp.toPx(),at,style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))}
            }
            shown.edges.firstOrNull {it.id==edge}?.let {e->drawContext.canvas.nativeCanvas.apply {save();translate(v.left,v.top);scale(v.width/d.width,v.height/d.height);drawPath(edgePath(shown,e),android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {color=palette.primary.toArgb();style=android.graphics.Paint.Style.STROKE;strokeWidth=3.dp.toPx()*d.width/v.width;alpha=140});restore()};e.points.forEach {p->drawCircle(palette.primary,6.dp.toPx(),Offset(v.left+p.x*v.width,v.top+p.y*v.height))}}
            if(effective==DiagramGesture.CONNECT)shown.edges.firstOrNull {it.id==edge}?.let {e->
                val m=android.graphics.PathMeasure(edgePath(shown,e),false);val out=FloatArray(2)
                for(distance in listOf(0f,m.length)) {m.getPosTan(distance,out,null);drawCircle(palette.primary,7.dp.toPx(),Offset(v.left+out[0]/shown.width*v.width,v.top+out[1]/shown.height*v.height))}
            }
            badges.forEach {badge->drawCircle(if(badge.edgeId==edge)palette.selection else palette.raised,12.dp.toPx(),badge.point);drawCircle(palette.primary,12.dp.toPx(),badge.point,style=androidx.compose.ui.graphics.drawscope.Stroke((if(badge.edgeId==edge)3 else 1).dp.toPx()));drawContext.canvas.nativeCanvas.drawText(badge.number.toString(),badge.point.x,badge.point.y+4.5.dp.toPx(),android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {color=palette.text.toArgb();textSize=13.dp.toPx();textAlign=android.graphics.Paint.Align.CENTER;isFakeBoldText=true})}
        }
    }
}
