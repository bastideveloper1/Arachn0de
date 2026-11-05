package com.r0ybt.arachn0de.diagrams

import androidx.compose.ui.geometry.Offset
import kotlin.math.*

internal enum class DiagramHandle(val fx:Int,val fy:Int) { LEFT(-1,0),RIGHT(1,0),TOP(0,-1),BOTTOM(0,1),TL(-1,-1),TR(1,-1),BL(-1,1),BR(1,1) }
internal fun nodeHandles(n:DiagramNode)=DiagramHandle.entries.associateWith {h->Offset(n.x+(h.fx+1)*n.w/2,n.y+(h.fy+1)*n.h/2)}
internal fun diagramHandleAt(n:DiagramNode,at:Offset,v:com.r0ybt.arachn0de.visual.CanvasViewport,tolerance:Float):DiagramHandle? {
    val p=Offset((at.x-v.left)/v.width,(at.y-v.top)/v.height)
    // A small card's center remains a MOVE target even if touch areas of handles overlap it.
    if(p.x in n.x+n.w*.18f..n.x+n.w*.82f&&p.y in n.y+n.h*.18f..n.y+n.h*.82f)return null
    return nodeHandles(n).minByOrNull {(_,p)->(Offset(v.left+p.x*v.width,v.top+p.y*v.height)-at).getDistance()}
        ?.takeIf {(_,p)->(Offset(v.left+p.x*v.width,v.top+p.y*v.height)-at).getDistance()<=tolerance}?.key
}
private fun diagramResizeRequest(n:DiagramNode,dx:Float,dy:Float,handle:DiagramHandle):android.graphics.RectF {
    val multiplier=if(n.centeredResize)2 else 1
    var w=if(handle.fx==0)n.w else (n.w+dx*handle.fx*multiplier).coerceAtLeast(minOf(n.w,.025f))
    var h=if(handle.fy==0)n.h else (n.h+dy*handle.fy*multiplier).coerceAtLeast(minOf(n.h,.025f))
    if(n.lockAspect) {
        val factor=if(handle.fx==0)h/n.h else if(handle.fy==0)w/n.w else max(w/n.w,h/n.h)
        w=n.w*factor;h=n.h*factor
    }
    val centeredX=n.centeredResize||(n.lockAspect&&handle.fx==0)
    val centeredY=n.centeredResize||(n.lockAspect&&handle.fy==0)
    val x=if(centeredX)n.x+(n.w-w)/2 else if(handle.fx<0)n.x+n.w-w else n.x
    val y=if(centeredY)n.y+(n.h-h)/2 else if(handle.fy<0)n.y+n.h-h else n.y
    return android.graphics.RectF(x,y,x+w,y+h)
}
internal fun diagramResizeLimits(n:DiagramNode,dx:Float,dy:Float,handle:DiagramHandle):Set<DiagramHandle> {
    val r=diagramResizeRequest(n,dx,dy,handle)
    return buildSet {if(r.left<-.000001f)add(DiagramHandle.LEFT);if(r.right>1.000001f)add(DiagramHandle.RIGHT);if(r.top<-.000001f)add(DiagramHandle.TOP);if(r.bottom>1.000001f)add(DiagramHandle.BOTTOM)}
}
internal fun resizeDiagramNode(n:DiagramNode,dx:Float,dy:Float,handle:DiagramHandle):DiagramNode {
    val request=diagramResizeRequest(n,dx,dy,handle);var w=request.width();var h=request.height()
    val cx=n.x+n.w/2;val cy=n.y+n.h/2
    val centeredX=n.centeredResize||(n.lockAspect&&handle.fx==0)
    val centeredY=n.centeredResize||(n.lockAspect&&handle.fy==0)
    val maxW=if(centeredX)2*min(cx,1-cx) else if(handle.fx<0)n.x+n.w else 1-n.x
    val maxH=if(centeredY)2*min(cy,1-cy) else if(handle.fy<0)n.y+n.h else 1-n.y
    if(n.lockAspect) {val cap=minOf(1f,maxW/w,maxH/h);w*=cap;h*=cap} else {w=w.coerceAtMost(maxW);h=h.coerceAtMost(maxH)}
    if(w<minOf(n.w,.025f)||h<minOf(n.h,.025f))return n
    val x=if(centeredX)cx-w/2 else if(handle.fx<0)n.x+n.w-w else n.x
    val y=if(centeredY)cy-h/2 else if(handle.fy<0)n.y+n.h-h else n.y
    val font=if(n.resizeContent==DiagramResizeContent.SCALE)(n.font*min(w/n.w,h/n.h)).coerceIn(12f,192f) else n.font
    return n.copy(x=x.coerceAtLeast(0f),y=y.coerceAtLeast(0f),w=w,h=h,font=font)
}
internal enum class DiagramDirection(val label:String,val dx:Int,val dy:Int) { AUTO("Automática",0,0),DOWN("Abajo",0,1),RIGHT("Derecha",1,0),UP("Arriba",0,-1),LEFT("Izquierda",-1,0) }
internal data class DiagramDuplication(val document:DiagramDocument,val selected:String)
internal fun duplicateDiagramNode(d:DiagramDocument,id:String,connected:Boolean,direction:DiagramDirection=DiagramDirection.AUTO,lastEdge:String?=null,manual:Offset?=null):DiagramDuplication? {
    require(d.nodes.size<128) {"Máximo 128 nodos por diagrama."}
    require(!connected||d.edges.size<192) {"Máximo 192 conexiones por diagrama."}
    val source=d.nodes.first {it.id==id}
    val inherited=d.edges.firstOrNull {it.id==lastEdge} ?: d.edges.lastOrNull {it.to==id||it.from==id}
    val travel=inherited?.let {e->val a=d.nodes.first {it.id==e.from};val b=d.nodes.first {it.id==e.to};val sign=if(e.flow.direction==FlowDirection.REVERSE)-1 else 1;Offset((b.x-a.x)*sign,(b.y-a.y)*sign)}
    val preferred=if(direction!=DiagramDirection.AUTO)direction else if(travel!=null&&travel.getDistance()>.001f) {
        if(abs(travel.x)>abs(travel.y)) {if(travel.x>0)DiagramDirection.RIGHT else DiagramDirection.LEFT} else if(travel.y>0)DiagramDirection.DOWN else DiagramDirection.UP
    } else if(d.height>=d.width)DiagramDirection.DOWN else DiagramDirection.RIGHT
    fun fits(n:DiagramNode)=n.x>=0&&n.y>=0&&n.x+n.w<=1.000001f&&n.y+n.h<=1.000001f&&d.nodes.none {n.x<it.x+it.w+.004f&&n.x+n.w>it.x-.004f&&n.y<it.y+it.h+.004f&&n.y+n.h>it.y-.004f}
    val gapX=24f/d.width;val gapY=24f/d.height
    val candidates=buildList {
        val dirs=(listOf(preferred)+listOf(DiagramDirection.DOWN,DiagramDirection.RIGHT,DiagramDirection.UP,DiagramDirection.LEFT)).distinct()
        for(dir in dirs)for(i in 1..12)add(source.copy(x=source.x+dir.dx*(source.w+gapX)*i,y=source.y+dir.dy*(source.h+gapY)*i))
        // Search vacant slots, preserving every existing node's position.
        var y=gapY;while(y+source.h<=1) {var x=gapX;while(x+source.w<=1) {add(source.copy(x=x,y=y));x+=source.w+gapX};y+=source.h+gapY}
    }
    val placed=if(manual!=null)source.copy(x=(manual.x-source.w/2).coerceIn(0f,1-source.w),y=(manual.y-source.h/2).coerceIn(0f,1-source.h)) else candidates.firstOrNull(::fits) ?: return null
    val copy=placed.copy(id=diagramId())
    val edge=if(connected)(inherited ?: DiagramEdge(from=id,to=copy.id)).copy(id=diagramId(),from=id,to=copy.id,points=emptyList(),startArrow=false,endArrow=true,flow=(inherited?.flow ?: DiagramFlow()).copy(direction=FlowDirection.FORWARD)) else null
    val playback=if(edge!=null&&d.playback.mode==DiagramAnimationMode.SEQUENTIAL&&d.playback.route.lastOrNull()?.let {last->flowTarget(d.edges.first {it.id==last})==id}==true)d.playback.copy(route=d.playback.route+edge.id) else d.playback
    val result=d.copy(nodes=d.nodes+copy,edges=if(edge!=null)d.edges+edge else d.edges,playback=playback)
    DiagramCodec.validate(result);return DiagramDuplication(result,copy.id)
}
/** Convert logical dimensions without stretching nodes. Content fitting is explicit and uniform. */
internal fun changeDiagramPage(d:DiagramDocument,page:DiagramPage,fit:Boolean=false):DiagramDocument {
    val b=diagramBounds(d);val factor=if(fit&&d.nodes.isNotEmpty())minOf((page.width-48)/b.width().coerceAtLeast(1f),(page.height-48)/b.height().coerceAtLeast(1f),1f) else 1f
    val ox=if(fit)(page.width-b.width()*factor)/2-b.left*factor else 0f
    val oy=if(fit)(page.height-b.height()*factor)/2-b.top*factor else 0f
    val nodes=d.nodes.map {n->n.copy(x=(n.x*d.width*factor+ox)/page.width,y=(n.y*d.height*factor+oy)/page.height,w=n.w*d.width*factor/page.width,h=n.h*d.height*factor/page.height,font=(n.font*factor).coerceIn(12f,192f),stroke=n.stroke*factor,radius=n.radius*factor)}
    val edges=d.edges.map {e->e.copy(points=e.points.map {p->DPoint((p.x*d.width*factor+ox)/page.width,(p.y*d.height*factor+oy)/page.height)},width=(e.width*factor).coerceIn(1f,20f),flow=e.flow.copy(size=(e.flow.size*factor).coerceIn(6f,100f)))}
    return d.copy(page=page,nodes=nodes,edges=edges,camera=d.camera?.let {it.copy(cx=(it.cx*d.width/page.width).coerceIn(-1f,2f),cy=(it.cy*d.height/page.height).coerceIn(-1f,2f))}).also(DiagramCodec::validate)
}

/** A rigid translation of document content, including manually routed connection points.
 * Camera, dimensions, assets and all styling remain unchanged. */
internal fun translateDiagramContent(d:DiagramDocument,dx:Float,dy:Float):DiagramDocument {
    if(d.nodes.isEmpty())return d
    val xs=d.nodes.flatMap {listOf(it.x,it.x+it.w)}+d.edges.flatMap {it.points.map {p->p.x}}
    val ys=d.nodes.flatMap {listOf(it.y,it.y+it.h)}+d.edges.flatMap {it.points.map {p->p.y}}
    val x=dx.coerceIn(-xs.min(),1-xs.max());val y=dy.coerceIn(-ys.min(),1-ys.max())
    return d.copy(nodes=d.nodes.map {it.copy(x=it.x+x,y=it.y+y)},edges=d.edges.map {it.copy(points=it.points.map {p->DPoint(p.x+x,p.y+y)})}).also(DiagramCodec::validate)
}
internal fun centerDiagramContent(d:DiagramDocument):DiagramDocument {
    if(d.nodes.isEmpty())return d
    val b=diagramBounds(d)
    return translateDiagramContent(d,(d.width/2f-b.centerX())/d.width,(d.height/2f-b.centerY())/d.height)
}
