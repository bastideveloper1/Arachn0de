package com.r0ybt.arachn0de.visual

import androidx.compose.ui.geometry.Offset
import kotlin.math.min
internal data class CanvasViewport(val left:Float,val top:Float,val width:Float,val height:Float) {
    fun point(p:Offset)=Offset(((p.x-left)/width).coerceIn(0f,1f),((p.y-top)/height).coerceIn(0f,1f))
    fun contains(p:Offset)=p.x in left..left+width && p.y in top..top+height
}
internal fun canvasViewport(w:Float,h:Float,imageW:Int,imageH:Int,zoom:Float,pan:Offset):CanvasViewport {
    val fit=min(w/imageW,h/imageH);val width=imageW*fit*zoom;val height=imageH*fit*zoom
    return CanvasViewport((w-width)/2+pan.x,(h-height)/2+pan.y,width,height)
}
