package com.r0ybt.arachn0de.diagrams

import android.graphics.*
import android.text.*
import android.util.Base64
import java.io.OutputStream
import kotlin.math.*

internal const val DIAGRAM_W=1600
internal const val DIAGRAM_H=1000
internal fun edgePath(d:DiagramDocument,e:DiagramEdge):Path {
    val a=d.nodes.first {it.id==e.from};val b=d.nodes.first {it.id==e.to}
    fun center(n:DiagramNode)=PointF((n.x+n.w/2)*d.width,(n.y+n.h/2)*d.height)
    fun automatic(n:DiagramNode,t:PointF):DiagramAnchor {
        val c=center(n);val dx=(t.x-c.x)/(n.w*d.width);val dy=(t.y-c.y)/(n.h*d.height)
        return if(abs(dx)>abs(dy)) {if(dx>=0)DiagramAnchor.RIGHT else DiagramAnchor.LEFT}
        else if(dy>=0)DiagramAnchor.BOTTOM else DiagramAnchor.TOP
    }
    val controls=e.points.map {PointF(it.x*d.width,it.y*d.height)}
    val from=e.fromAnchor ?: automatic(a,controls.firstOrNull() ?: center(b))
    val to=e.toAnchor ?: automatic(b,controls.lastOrNull() ?: center(a))
    fun point(n:DiagramNode,side:DiagramAnchor)=diagramAnchorPoint(n,side).let {PointF(it.x*d.width,it.y*d.height)}
    val start=point(a,from);val end=point(b,to)
    fun vertical(side:DiagramAnchor)=side==DiagramAnchor.TOP||side==DiagramAnchor.BOTTOM
    fun normal(side:DiagramAnchor)=when(side) {DiagramAnchor.TOP->PointF(0f,-1f);DiagramAnchor.BOTTOM->PointF(0f,1f);DiagramAnchor.LEFT->PointF(-1f,0f);DiagramAnchor.RIGHT->PointF(1f,0f)}
    return Path().apply {moveTo(start.x,start.y);when(e.route) {
        DiagramRoute.STRAIGHT->{controls.forEach {lineTo(it.x,it.y)};lineTo(end.x,end.y)}
        DiagramRoute.ORTHOGONAL->{
            if(controls.isNotEmpty()) {
                var last=start
                (controls+end).forEach {p->if(vertical(from)) {lineTo(last.x,(last.y+p.y)/2);lineTo(p.x,(last.y+p.y)/2)} else {lineTo((last.x+p.x)/2,last.y);lineTo((last.x+p.x)/2,p.y)};lineTo(p.x,p.y);last=p}
            } else {
                val f=normal(from);val t=normal(to)
                val distance=hypot(end.x-start.x,end.y-start.y)
                val stub=min(24f,distance/3)
                val x1=start.x+f.x*stub;val y1=start.y+f.y*stub
                val x2=end.x+t.x*stub;val y2=end.y+t.y*stub
                lineTo(x1,y1)
                if(vertical(from)&&vertical(to)) {val y=if(from==to) {if(from==DiagramAnchor.TOP)min(y1,y2)-stub else max(y1,y2)+stub} else (y1+y2)/2;lineTo(x1,y);lineTo(x2,y)}
                else if(!vertical(from)&&!vertical(to)) {val x=if(from==to) {if(from==DiagramAnchor.LEFT)min(x1,x2)-stub else max(x1,x2)+stub} else (x1+x2)/2;lineTo(x,y1);lineTo(x,y2)}
                else if(vertical(from))lineTo(x1,y2) else lineTo(x2,y1)
                lineTo(x2,y2);lineTo(end.x,end.y)
            }
        }
        DiagramRoute.CURVE->if(controls.isEmpty()) {
            val length=hypot(end.x-start.x,end.y-start.y)/3
            val f=normal(from);val t=normal(to)
            // Curva remains an intentional visible curve for legacy aligned links.
            // Explicit anchors retain face-normal tangents; straight/orthogonal links never bow.
            val dx=end.x-start.x;val dy=end.y-start.y
            if(e.fromAnchor==null&&e.toAnchor==null&&(abs(dx)<.001f||abs(dy)<.001f)) {
                val distance=hypot(dx,dy).coerceAtLeast(.001f);val bend=min(120f,distance*.25f)
                val ox=-dy/distance*bend;val oy=dx/distance*bend
                cubicTo(start.x+dx/3+ox,start.y+dy/3+oy,end.x-dx/3+ox,end.y-dy/3+oy,end.x,end.y)
            } else cubicTo(start.x+f.x*length,start.y+f.y*length,end.x+t.x*length,end.y+t.y*length,end.x,end.y)
        } else {var last=start;controls.forEach {p->quadTo(last.x,p.y,p.x,p.y);last=p};quadTo(end.x,last.y,end.x,end.y)}
    }}
}

internal fun flowProgress(seconds:Float,f:DiagramFlow,index:Int):Float? {
    val t=seconds*f.speed/4-index.toFloat()/f.count
    if(t<0&&!f.loop)return null
    if(!f.loop&&t>(if(f.direction==FlowDirection.BOUNCE)2f else 1f))return null
    val phase=if(f.direction==FlowDirection.BOUNCE) {val u=(t%2+2)%2;if(u<=1)u else 2-u} else if(!f.loop)t.coerceIn(0f,1f) else (t%1+1)%1
    return if(f.direction==FlowDirection.REVERSE)1-phase else phase
}
internal fun diagramShader(g:DiagramGradient?,rect:RectF):Shader?=g?.let {
    val angle=Math.toRadians(it.angle.toDouble());val dx=cos(angle).toFloat()*rect.width()/2;val dy=sin(angle).toFloat()*rect.height()/2
    LinearGradient(rect.centerX()-dx,rect.centerY()-dy,rect.centerX()+dx,rect.centerY()+dy,it.colors.toIntArray(),null,Shader.TileMode.CLAMP)
}
internal fun diagramTextLayout(n:DiagramNode,d:DiagramDocument)=StaticLayout.Builder.obtain(n.text,0,n.text.length,TextPaint(Paint.ANTI_ALIAS_FLAG).apply {color=n.ink;textSize=n.font},(n.w*d.width-16).toInt().coerceAtLeast(1)).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
private data class DiagramTextKey(val text:String,val font:Float,val ink:Int,val width:Int)
internal class DiagramRenderer(assets:Map<String,String>):AutoCloseable {
    private val images=assets.mapValues {(_,raw)->val bytes=Base64.decode(raw,Base64.NO_WRAP);requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size))}
    private val dashed=DashPathEffect(floatArrayOf(24f,16f),0f);private val dotted=DashPathEffect(floatArrayOf(2f,12f),0f)
    private var texts=emptyMap<String,Pair<DiagramTextKey,StaticLayout>>()
    private var cached:DiagramDocument?=null;private var paths=emptyMap<String,Path>();private var measures=emptyMap<String,PathMeasure>();private var timeline:DiagramTimeline?=null
    fun draw(canvas:Canvas,d:DiagramDocument,seconds:Float?=null) {
        if(cached!=d) {
            texts=d.nodes.filter {it.text.isNotEmpty()}.associate {n->val key=DiagramTextKey(n.text,n.font,n.ink,(n.w*d.width-16).toInt().coerceAtLeast(1));n.id to (texts[n.id]?.takeIf {it.first==key} ?: (key to diagramTextLayout(n,d)))}
            paths=d.edges.associate {it.id to edgePath(d,it)};measures=paths.mapValues {PathMeasure(it.value,false)};timeline=DiagramTimeline(d,measures);cached=d}
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG);val sheet=RectF(0f,0f,d.width.toFloat(),d.height.toFloat())
        canvas.save();canvas.clipRect(sheet)
        canvas.drawColor(d.background,PorterDuff.Mode.SRC)
        paint.shader=diagramShader(d.backgroundGradient,sheet);if(paint.shader!=null)canvas.drawRect(sheet,paint);paint.shader=null
        d.backgroundAsset?.let(images::get)?.let {image->val scale=if(d.backgroundFit==DiagramBackgroundFit.FILL)max(sheet.width()/image.width,sheet.height()/image.height) else min(sheet.width()/image.width,sheet.height()/image.height);val w=image.width*scale;val h=image.height*scale;canvas.drawBitmap(image,null,RectF((d.width-w)/2,(d.height-h)/2,(d.width+w)/2,(d.height+h)/2),paint)}
        d.edges.forEach {e->val path=paths.getValue(e.id);val measure=measures.getValue(e.id);if(measure.length<=.001f)return@forEach
            paint.color=e.color;paint.style=Paint.Style.STROKE;paint.strokeWidth=e.width;paint.pathEffect=when(e.stroke) {DiagramStroke.SOLID->null;DiagramStroke.DASHED->dashed;DiagramStroke.DOTTED->dotted};paint.strokeCap=if(e.stroke==DiagramStroke.DOTTED)Paint.Cap.ROUND else Paint.Cap.BUTT
            canvas.drawPath(path,paint);paint.pathEffect=null;paint.strokeCap=Paint.Cap.BUTT
            fun arrow(at:Float,reverse:Boolean) {val p=FloatArray(2);val t=FloatArray(2);measure.getPosTan(at,p,t);val angle=atan2(t[1],t[0])+(if(reverse)PI.toFloat() else 0f);for(sign in listOf(-1,1))canvas.drawLine(p[0],p[1],p[0]-cos(angle+sign*.5f)*18,p[1]-sin(angle+sign*.5f)*18,paint)}
            if(e.endArrow)arrow(measure.length,false);if(e.startArrow)arrow(0f,true)
            val middle=FloatArray(2);measure.getPosTan(measure.length/2,middle,null);paint.style=Paint.Style.FILL;paint.textSize=24f;paint.textAlign=Paint.Align.CENTER;canvas.drawText(e.label,middle[0],middle[1]-10,paint)
        }
        if(seconds!=null)timeline?.particles(seconds)?.forEach {sample->drawParticle(canvas,sample,seconds,paint)}
        d.nodes.forEach {n->
            val rect=RectF(n.x*d.width,n.y*d.height,(n.x+n.w)*d.width,(n.y+n.h)*d.height)
            val diamond=Path().apply {moveTo(rect.centerX(),rect.top);lineTo(rect.right,rect.centerY());lineTo(rect.centerX(),rect.bottom);lineTo(rect.left,rect.centerY());close()}
            fun shape() {when(n.shape) {DiagramShape.RECT,DiagramShape.TABLE->canvas.drawRect(rect,paint);DiagramShape.ROUND->canvas.drawRoundRect(rect,n.radius,n.radius,paint);DiagramShape.ELLIPSE->canvas.drawOval(rect,paint);DiagramShape.DIAMOND->canvas.drawPath(diamond,paint);else->Unit}}
            paint.shader=null;paint.alpha=255;paint.style=Paint.Style.FILL
            if(n.shape !in setOf(DiagramShape.IMAGE,DiagramShape.TEXT)) {paint.color=n.fill;paint.shader=diagramShader(n.gradient,rect);shape();paint.shader=null;if(n.stroke>0) {paint.color=n.border;paint.strokeWidth=n.stroke;paint.style=Paint.Style.STROKE;shape()}}
            paint.style=Paint.Style.FILL
            if(n.shape==DiagramShape.IMAGE)n.asset?.let(images::get)?.let {image->val scale=min(rect.width()/image.width,rect.height()/image.height);val w=image.width*scale;val h=image.height*scale;paint.alpha=255;canvas.drawBitmap(image,null,RectF(rect.centerX()-w/2,rect.centerY()-h/2,rect.centerX()+w/2,rect.centerY()+h/2),paint)}
            if(n.shape==DiagramShape.TABLE) {
                val lines=listOf(n.text)+n.fields.map {f->listOfNotNull(f.name,f.type.takeIf {it.isNotBlank()}).joinToString("  ")}
                val rowHeight=rect.height()/lines.size
                canvas.save();canvas.clipRect(rect)
                lines.forEachIndexed {i,line->
                    paint.style=Paint.Style.FILL;paint.color=n.ink;paint.textAlign=Paint.Align.LEFT;paint.isFakeBoldText=i==0
                    paint.textSize=n.font
                    val field=if(i==0)null else n.fields[i-1]
                    val keySize=min(20f,rowHeight*.48f)
                    val keyCount=(if(field?.primaryKey==true)1 else 0)+(if(field?.foreignKey==true)1 else 0)
                    val keySpace=keyCount*(keySize+5)
                    val natural=paint.measureText(line).coerceAtLeast(1f)
                    paint.textSize=min(n.font,min(rowHeight*.65f,n.font*(rect.width()-16-keySpace).coerceAtLeast(1f)/natural))
                    val y=rect.top+rowHeight*i
                    val metrics=paint.fontMetrics
                    var keyX=rect.left+8
                    if(field?.primaryKey==true) {diagramKey(canvas,paint,keyX,y+rowHeight/2,keySize,false);keyX+=keySize+5}
                    if(field?.foreignKey==true)diagramKey(canvas,paint,keyX,y+rowHeight/2,keySize,true)
                    canvas.drawText(line,rect.left+8+keySpace,y+rowHeight/2-(metrics.ascent+metrics.descent)/2,paint)
                    if(i>0) {paint.color=n.border;paint.strokeWidth=max(1f,n.stroke);canvas.drawLine(rect.left,y,rect.right,y,paint)}
                }
                paint.isFakeBoldText=false;canvas.restore()
            } else if(n.text.isNotEmpty()) {
                val layout=texts.getValue(n.id).second
                canvas.save();if(n.shape!=DiagramShape.IMAGE)canvas.clipRect(rect)
                canvas.translate(rect.left+8,if(n.shape==DiagramShape.IMAGE)rect.bottom+6 else rect.top+max(0f,(rect.height()-layout.height)/2));layout.draw(canvas);canvas.restore()
            }
        }
        canvas.restore()
    }
    private fun drawParticle(canvas:Canvas,p:DiagramParticle,time:Float,paint:Paint) {
        val f=p.flow;val color=f.color ?: if(f.objectType==FlowObject.SPHERE)0xffffa000.toInt() else p.edge.color
        val pulse=if(f.particleStyle==DiagramParticleStyle.PULSE) .9f+.1f*sin(time*2*PI.toFloat()) else 1f
        val r=f.size/2*pulse;val halo=f.haloColor ?: color
        val image=f.objectType==FlowObject.IMAGE
        paint.style=Paint.Style.FILL;paint.shader=null;paint.alpha=255
        if((f.particleStyle!=DiagramParticleStyle.NORMAL||image&&f.imageHalo)&&(!image||f.imageHalo)) {
            val alpha=(f.glow*120).toInt();val glowColor=(halo and 0x00ffffff) or (alpha shl 24)
            paint.shader=RadialGradient(p.x,p.y,r*1.9f,intArrayOf(glowColor,halo and 0x00ffffff),floatArrayOf(0f,1f),Shader.TileMode.CLAMP);canvas.drawCircle(p.x,p.y,r*1.9f,paint);paint.shader=null
        }
        val bitmap=f.asset?.let(images::get)
        if(image&&bitmap!=null) {val scale=min(2*r/bitmap.width,2*r/bitmap.height);val w=bitmap.width*scale/2;val h=bitmap.height*scale/2;canvas.drawBitmap(bitmap,null,RectF(p.x-w,p.y-h,p.x+w,p.y+h),paint)}
        else if(f.objectType==FlowObject.ICON) {paint.color=color;paint.textSize=f.size;paint.textAlign=Paint.Align.CENTER;canvas.drawText("▣",p.x,p.y+r*.6f,paint)}
        else {paint.color=color;if(f.particleStyle!=DiagramParticleStyle.NORMAL||f.objectType==FlowObject.SPHERE)paint.shader=RadialGradient(p.x-r*.15f,p.y-r*.15f,r,intArrayOf(Color.WHITE,color),floatArrayOf(0f,1f),Shader.TileMode.CLAMP);canvas.drawCircle(p.x,p.y,r,paint);paint.shader=null}
    }
    fun png(d:DiagramDocument,output:OutputStream,width:Int=d.width,height:Int=d.height) {val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);try {renderDiagramFrame(this,d,bitmap,null);check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output))} finally {bitmap.recycle()}}
    override fun close() {images.values.forEach {it.recycle()};paths=emptyMap();measures=emptyMap();timeline=null;texts=emptyMap();cached=null}
}

/** PK: solid head / one tooth. FK: hollow head / two teeth. Shares the document ink. */
private fun diagramKey(canvas:Canvas,paint:Paint,x:Float,cy:Float,size:Float,foreign:Boolean) {
    val oldStyle=paint.style;val oldStroke=paint.strokeWidth
    paint.strokeWidth=max(1f,size*.09f)
    paint.style=if(foreign)Paint.Style.STROKE else Paint.Style.FILL
    canvas.drawCircle(x+size*.24f,cy,size*.17f,paint)
    paint.style=Paint.Style.STROKE
    canvas.drawLine(x+size*.4f,cy,x+size*.94f,cy,paint)
    canvas.drawLine(x+size*.88f,cy,x+size*.88f,cy+size*.2f,paint)
    if(foreign)canvas.drawLine(x+size*.66f,cy,x+size*.66f,cy+size*.2f,paint)
    paint.style=oldStyle;paint.strokeWidth=oldStroke
}
