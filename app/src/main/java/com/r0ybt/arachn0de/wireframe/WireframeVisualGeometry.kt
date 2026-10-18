package com.r0ybt.arachn0de.wireframe

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.roundToInt

/** All editor layers and pointer input share this transform; document coordinates never include chrome. */
internal data class WireViewport(val origin:Offset,val scale:Float,val width:Float,val height:Float,
    val frame:Rect,val toolbar:Rect?,val verticalToolbar:Boolean) {
    val sheet get()=Rect(origin,androidx.compose.ui.geometry.Size(width*scale,height*scale))
    fun screen(point:Offset)=origin+Offset(point.x*width*scale,point.y*height*scale)
    fun document(point:Offset)=Offset((point.x-origin.x)/(width*scale),(point.y-origin.y)/(height*scale))
}
internal fun wireViewport(size:IntSize,format:WireFormat,density:Float,device:Boolean=false,tools:Boolean=false):WireViewport {
    val width=format.width.toFloat();val height=format.height.toFloat()
    val side=if(!device) 0f else if(format==WireFormat.MOBILE) 40f else 45f
    val top=if(!device) 0f else if(format==WireFormat.MOBILE) 110f else 60f
    val bottom=if(!device) 0f else if(format==WireFormat.MOBILE) 110f else 160f
    val outerW=width+2*side;val outerH=height+top+bottom
    val hostW=size.width.toFloat();val hostH=size.height.toFloat()
    val natural=minOf(hostW/outerW,hostH/outerH)*.94f
    val gutterX=(hostW-outerW*natural)/2;val gutterY=(hostH-outerH*natural)/2
    val button=48f*density;val gap=4f*density
    var reserved=0f
    var vertical=false
    val toolbar=if(!tools || hostW<button || hostH<button*1.5f+gap) null else when {
        gutterX>=button+gap && hostH>=button*4->{vertical=true;Rect(gap,(hostH-button*4)/2,gap+button,(hostH+button*4)/2)}
        gutterY>=button+gap && hostW>=button*4->Rect((hostW-button*4)/2,gap,(hostW+button*4)/2,gap+button)
        else->{reserved=button+gap;val toolsWidth=minOf(hostW,button*4);Rect((hostW-toolsWidth)/2,0f,(hostW+toolsWidth)/2,button)}
    }
    val scale=minOf(hostW/outerW,(hostH-reserved).coerceAtLeast(0f)/outerH)*.94f
    val left=(hostW-outerW*scale)/2;val y=reserved+(hostH-reserved-outerH*scale)/2
    return WireViewport(Offset(left+side*scale,y+top*scale),scale,width,height,
        Rect(left,y,left+outerW*scale,y+outerH*scale),toolbar,vertical)
}

internal data class WireSpacing(val from:Offset,val to:Offset,val pixels:Float,val equal:Boolean=false)
internal data class WireAlignment(val element:WireElement,val vertical:Float?,val horizontal:Float?,val distances:List<WireSpacing>)
private data class WireAxisMatch(val position:Float,val guide:Float,val error:Float,val priority:Int)

/** Pick at most one axis per direction. Thresholds are converted from screen pixels by the caller. */
internal fun wireSmartAlign(document:WireDocument,moved:WireElement,toleranceX:Float,toleranceY:Float,magnetic:Boolean):WireAlignment {
    fun match(horizontal:Boolean):WireAxisMatch? {
        val start=if(horizontal) moved.x else moved.y
        val extent=if(horizontal) moved.w else moved.h
        val tolerance=if(horizontal) toleranceX else toleranceY
        val candidates=mutableListOf<WireAxisMatch>()
        fun offer(position:Float,guide:Float,priority:Int) {
            val error=abs(position-start)
            if(error<=tolerance && position>=0 && position+extent<=1.000001f) candidates+=WireAxisMatch(position.coerceIn(0f,1-extent),guide,error,priority)
        }
        offer(.5f-extent/2,.5f,0)
        document.elements.filter {it.id!=moved.id}.forEach {other->
            val a=if(horizontal) other.x else other.y;val b=if(horizontal) other.w else other.h
            offer(a+b/2-extent/2,a+b/2,1)
            for(target in listOf(a,a+b)) for(anchor in listOf(0f,extent)) offer(target-anchor,target,2)
        }
        return candidates.minWithOrNull(compareBy<WireAxisMatch> {it.error}.thenBy {it.priority})
    }
    val x=match(true);val y=match(false)
    val element=if(magnetic) moved.copy(x=x?.position ?: moved.x,y=y?.position ?: moved.y) else moved
    return WireAlignment(element,x?.guide,y?.guide,wireSpacing(document,element,toleranceX,toleranceY))
}

/** Nearest aligned neighbour on each side; an equal pair takes precedence over unrelated measures. */
private fun wireSpacing(document:WireDocument,e:WireElement,tx:Float,ty:Float):List<WireSpacing> {
    val others=document.elements.filter {it.id!=e.id}
    fun axis(vertical:Boolean):List<WireSpacing> {
        val tolerance=if(vertical) tx else ty
        val maxGap=minOf(.25f,(if(vertical) ty else tx)*16)
        val extent=if(vertical) document.format.height else document.format.width
        fun crossStart(item:WireElement)=if(vertical) item.x else item.y
        fun crossSize(item:WireElement)=if(vertical) item.w else item.h
        fun start(item:WireElement)=if(vertical) item.y else item.x
        fun length(item:WireElement)=if(vertical) item.h else item.w
        val aligned=others.filter {other->
            val a=crossStart(e);val b=crossStart(other);val w=crossSize(e);val v=crossSize(other)
            abs(a-b)<=tolerance || abs(a+w-b-v)<=tolerance || abs(a+w/2-b-v/2)<=tolerance
        }
        val before=aligned.filter {start(it)+length(it)<=start(e)}.maxByOrNull {start(it)+length(it)}
        val after=aligned.filter {start(it)>=start(e)+length(e)}.minByOrNull {start(it)}
        fun spacing(other:WireElement?,before:Boolean):WireSpacing? {
            if(other==null) return null
            val a=if(before) start(other)+length(other) else start(e)+length(e)
            val b=if(before) start(e) else start(other)
            if(b-a<=0 || b-a>maxGap) return null
            val cross=(maxOf(crossStart(e),crossStart(other))+minOf(crossStart(e)+crossSize(e),crossStart(other)+crossSize(other)))/2
            return if(vertical) WireSpacing(Offset(cross,a),Offset(cross,b),(b-a)*extent)
                else WireSpacing(Offset(a,cross),Offset(b,cross),(b-a)*extent)
        }
        val pair=listOfNotNull(spacing(before,true),spacing(after,false))
        val same=pair.size==2 && pair[0].pixels.roundToInt()==pair[1].pixels.roundToInt()
        return pair.map {it.copy(equal=same)}
    }
    val distances=axis(true)+axis(false)
    return if(distances.any {it.equal}) distances.filter {it.equal}.take(2) else distances.sortedBy {it.pixels}.take(2)
}

internal enum class WireComposition(val label:String) { NONE("Ninguna"),GRID("Cuadrícula"),CENTER("Centro"),THIRDS("Tercios") }
internal fun wireCompositionLines(mode:WireComposition,width:Float,height:Float):List<Pair<Offset,Offset>> = buildList {
    fun vertical(x:Float) {add(Offset(x,0f) to Offset(x,1f))}
    fun horizontal(y:Float) {add(Offset(0f,y) to Offset(1f,y))}
    when(mode) {
        WireComposition.NONE->Unit
        WireComposition.CENTER->{vertical(.5f);horizontal(.5f)}
        WireComposition.THIRDS->{vertical(1f/3);vertical(2f/3);horizontal(1f/3);horizontal(2f/3)}
        WireComposition.GRID->{
            val step=minOf(width,height)/12f
            if(step>0) {
                for(i in 1 until kotlin.math.ceil(width/step).toInt()) vertical(i*step/width)
                for(i in 1 until kotlin.math.ceil(height/step).toInt()) horizontal(i*step/height)
            }
        }
    }
}
