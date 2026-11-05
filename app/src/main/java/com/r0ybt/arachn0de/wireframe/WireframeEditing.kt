package com.r0ybt.arachn0de.wireframe

import androidx.compose.ui.geometry.Offset
import java.util.UUID
import kotlin.math.abs

/** A documentary layer; never a visual component or an image asset. Coordinates are relative to the anchor. */
internal data class WireAnnotation(val id:String=UUID.randomUUID().toString(),val x:Float=.5f,val y:Float=.5f,
    val title:String="",val text:String="",val elementId:String?=null)
internal fun WireAnnotation.position(document:WireDocument):Offset {
    val anchor=document.elements.firstOrNull {it.id==elementId}
    return if(anchor==null) Offset(x,y) else Offset((anchor.x+x).coerceIn(0f,1f),(anchor.y+y).coerceIn(0f,1f))
}
internal fun WireAnnotation.at(document:WireDocument,point:Offset):WireAnnotation {
    val anchor=document.elements.firstOrNull {it.id==elementId}
    return if(anchor==null) copy(x=point.x.coerceIn(0f,1f),y=point.y.coerceIn(0f,1f),elementId=null)
    else copy(x=point.x.coerceIn(0f,1f)-anchor.x,y=point.y.coerceIn(0f,1f)-anchor.y)
}
/** Removing an anchor preserves its observations at their last absolute positions. Undo restores the link. */
internal fun WireDocument.preserveAnnotations(previous:WireDocument)=copy(annotations=annotations.map {a->
    if(a.elementId!=null && elements.none {it.id==a.elementId}) a.position(previous).let {a.copy(x=it.x,y=it.y,elementId=null)} else a
})
internal fun wireIconDefaults(e:WireElement):List<String> = when(e.template) {
    "toolbar"->listOf("plus","image","trash","settings")
    "side"->listOf("user")+e.text.split('\n').take(6).mapIndexed {i,_->if(i==2) "settings" else "menu"}
    "dropdown"->e.text.split('\n').take(6).mapIndexed {i,_->if(i==2) "settings" else "menu"}
    "bottom","bottom_labels"->listOf("menu","search","user").take(e.text.split('\n').take(3).size)
    "circle_button","fab","text_icon","counter"->listOf("plus")
    "search"->listOf("search");"leading","avatar","list_icon"->listOf("user")
    "password"->listOf("eye");"back"->listOf("arrow_left");"menu","snackbar"->listOf("menu")
    "check_on","success"->listOf("check");"warning","error"->listOf("warning");"info"->listOf("info")
    "date"->listOf("calendar");"time"->listOf("clock");"card_image","placeholder","empty"->listOf("image")
    "icon"->listOf(e.icon)
    else->if(e.template==null && e.kind.iconCapable) listOf(e.icon) else emptyList()
}
internal enum class WireHandle {LEFT,TOP,RIGHT,BOTTOM,CORNER}
internal fun wireHandleAt(e:WireElement,point:Offset,width:Float,height:Float,tolerance:Float):WireHandle? {
    val handles=listOf(WireHandle.CORNER to Offset(e.x+e.w,e.y+e.h),WireHandle.LEFT to Offset(e.x,e.y+e.h/2),WireHandle.TOP to Offset(e.x+e.w/2,e.y),WireHandle.RIGHT to Offset(e.x+e.w,e.y+e.h/2),WireHandle.BOTTOM to Offset(e.x+e.w/2,e.y+e.h))
    return handles.minByOrNull {(_,p)->(p.x-point.x)*(p.x-point.x)*width*width+(p.y-point.y)*(p.y-point.y)*height*height}
        ?.takeIf {(_,p)->abs(p.x-point.x)*width<=tolerance && abs(p.y-point.y)*height<=tolerance}?.first
}
internal fun wireResizeEdge(e:WireElement,dx:Float,dy:Float,edge:WireHandle,format:WireFormat):WireElement {
    if(edge==WireHandle.CORNER) return wireTransform(e,dx,dy,true)
    val minW=minOf(e.w,.035f);val minH=minOf(e.h,.02f)
    var x=e.x;var y=e.y;var w=e.w;var h=e.h
    when(edge) {
        WireHandle.LEFT->{x=(e.x+dx).coerceIn(0f,e.x+e.w-minW);w=e.x+e.w-x}
        WireHandle.RIGHT->w=(e.w+dx).coerceIn(minW,1-e.x)
        WireHandle.TOP->{y=(e.y+dy).coerceIn(0f,e.y+e.h-minH);h=e.y+e.h-y}
        WireHandle.BOTTOM->h=(e.h+dy).coerceIn(minH,1-e.y)
        else->Unit
    }
    if(e.lockAspect) {
        val horizontal=edge==WireHandle.LEFT || edge==WireHandle.RIGHT
        val ratio=maxOf(if(horizontal) w/e.w else h/e.h,minW/e.w,minH/e.h)
        if(horizontal) {w=e.w*ratio;if(edge==WireHandle.LEFT) x=e.x+e.w-w} else {h=e.h*ratio;if(edge==WireHandle.TOP) y=e.y+e.h-h}
        if(horizontal) {h=e.h*ratio;y=e.y+(e.h-h)/2} else {w=e.w*ratio;x=e.x+(e.w-w)/2}
        if(x<0 || y<0 || x+w>1 || y+h>1) return e
    }
    if(x==e.x && y==e.y && w==e.w && h==e.h) return e
    return e.copy(x=x,y=y,w=w,h=h,contentUnit=e.contentUnit ?: minOf(e.w*format.width/400f,e.h*format.height/112f).coerceIn(.35f,1.5f),contentW=e.contentW ?: e.w,contentH=e.contentH ?: e.h)
}

internal fun wireScaleContent(before:WireElement,after:WireElement):WireElement {
    if(before.contentUnit==null) return after
    val scale=minOf(after.w/before.w,after.h/before.h)
    return after.copy(contentUnit=(before.contentUnit*scale).coerceIn(.001f,64f),contentW=(before.contentW!!*after.w/before.w).coerceAtMost(64f),contentH=(before.contentH!!*after.h/before.h).coerceAtMost(64f))
}

/** Match only the dragged edge; snapping must not translate or rescale the component's content. */
internal fun wireResizeAlign(document:WireDocument,moved:WireElement,edge:WireHandle,tx:Float,ty:Float,magnetic:Boolean):WireAlignment {
    if(edge==WireHandle.CORNER) return WireAlignment(moved,null,null,emptyList())
    val horizontal=edge==WireHandle.LEFT || edge==WireHandle.RIGHT
    val value=when(edge) {WireHandle.LEFT->moved.x;WireHandle.RIGHT->moved.x+moved.w;WireHandle.TOP->moved.y;else->moved.y+moved.h}
    val candidates=mutableListOf(0f,.5f,1f)
    document.elements.filter {it.id!=moved.id}.forEach {e->if(horizontal) candidates+=listOf(e.x,e.x+e.w/2,e.x+e.w) else candidates+=listOf(e.y,e.y+e.h/2,e.y+e.h)}
    val target=candidates.minByOrNull {abs(it-value)}?.takeIf {abs(it-value)<=if(horizontal) tx else ty}
    val snapped=if(magnetic && target!=null) wireResizeEdge(moved,if(horizontal) target-value else 0f,if(horizontal) 0f else target-value,edge,document.format) else moved
    val visible=target?.takeIf {!magnetic || abs((when(edge) {WireHandle.LEFT->snapped.x;WireHandle.RIGHT->snapped.x+snapped.w;WireHandle.TOP->snapped.y;else->snapped.y+snapped.h})-it)<.00001f}
    return WireAlignment(snapped,if(horizontal) visible else null,if(horizontal) null else visible,wireSmartAlign(document,snapped,tx,ty,false).distances)
}
