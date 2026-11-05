package com.r0ybt.arachn0de.diagrams

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import com.r0ybt.arachn0de.visual.CanvasViewport
import kotlin.math.min

/** Includes curves, arrow/particle extents and image labels, not only node centers. */
internal fun diagramBounds(d:DiagramDocument):RectF {
    if(d.nodes.isEmpty())return RectF(0f,0f,d.width.toFloat(),d.height.toFloat())
    val bounds=RectF(Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY)
    fun include(r:RectF) {bounds.left=minOf(bounds.left,r.left);bounds.top=minOf(bounds.top,r.top);bounds.right=maxOf(bounds.right,r.right);bounds.bottom=maxOf(bounds.bottom,r.bottom)}
    d.nodes.forEach {n->include(RectF(n.x*d.width-12,n.y*d.height-12,(n.x+n.w)*d.width+12,(n.y+n.h)*d.height+if(n.shape==DiagramShape.IMAGE&&n.text.isNotBlank())6f+diagramTextLayout(n,d).height else 12f))}
    d.edges.forEach {e->val r=RectF();edgePath(d,e).computeBounds(r,true);val pad=maxOf(24f,e.flow.size/2);r.inset(-pad,-pad);include(r)}
    return bounds
}
internal fun fitDiagram(d:DiagramDocument,widthDp:Float,heightDp:Float):DiagramCamera {
    val b=diagramBounds(d)
    val scale=min((widthDp-32).coerceAtLeast(1f)/b.width().coerceAtLeast(1f),(heightDp-32).coerceAtLeast(1f)/b.height().coerceAtLeast(1f)).coerceIn(.01f,8f)
    return DiagramCamera(b.centerX()/d.width,b.centerY()/d.height,scale)
}
internal fun diagramViewport(w:Float,h:Float,density:Float,c:DiagramCamera,documentWidth:Int=DIAGRAM_W,documentHeight:Int=DIAGRAM_H):CanvasViewport {
    val width=documentWidth*c.scale*density;val height=documentHeight*c.scale*density
    return CanvasViewport(w/2-c.cx*width,h/2-c.cy*height,width,height)
}
internal fun panDiagram(c:DiagramCamera,pan:Offset,zoom:Float,centroid:Offset,w:Float,h:Float,density:Float,documentWidth:Int=DIAGRAM_W,documentHeight:Int=DIAGRAM_H):DiagramCamera {
    val v=diagramViewport(w,h,density,c,documentWidth,documentHeight)
    val next=(c.scale*zoom).coerceIn(.01f,8f)
    val worldX=(centroid.x-v.left)/v.width;val worldY=(centroid.y-v.top)/v.height
    val nw=documentWidth*next*density;val nh=documentHeight*next*density
    return DiagramCamera((worldX-(centroid.x+pan.x-w/2)/nw).coerceIn(-1f,2f),(worldY-(centroid.y+pan.y-h/2)/nh).coerceIn(-1f,2f),next)
}

internal fun fitDiagramPage(d:DiagramDocument,w:Float,h:Float)=DiagramCamera(.5f,.5f,min((w-32).coerceAtLeast(1f)/d.width,(h-32).coerceAtLeast(1f)/d.height).coerceIn(.01f,8f))
