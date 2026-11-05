package com.r0ybt.arachn0de.diagrams

import android.graphics.PathMeasure
import kotlin.math.*

internal fun flowSource(e:DiagramEdge)=if(e.flow.direction==FlowDirection.REVERSE)e.to else e.from
internal fun flowTarget(e:DiagramEdge)=when(e.flow.direction) {FlowDirection.REVERSE->e.from;FlowDirection.BOUNCE->e.from;else->e.to}
internal data class DiagramRouteChoice(val edges:List<String>,val choices:List<String>)
/** Walk only unambiguous graph relationships; neither roots nor branches use creation order. */
internal fun diagramRoute(d:DiagramDocument):DiagramRouteChoice {
    val enabled=d.edges.filter {it.flow.enabled}
    val route=d.playback.route.takeWhile {id->enabled.any {it.id==id}}.toMutableList()
    if(route.isEmpty()) {
        val targets=enabled.map(::flowTarget).toSet();val roots=enabled.filter {flowSource(it) !in targets}
        if(roots.size!=1)return DiagramRouteChoice(emptyList(),(if(roots.isEmpty())enabled else roots).map {it.id}.sorted())
        route+=roots.single().id
    }
    while(route.size<=192) {
        val last=d.edges.first {it.id==route.last()};val next=enabled.filter {it.id !in route&&flowSource(it)==flowTarget(last)}.sortedBy {it.id}
        if(next.size!=1)return DiagramRouteChoice(route,next.map {it.id})
        route+=next.single().id
    }
    return DiagramRouteChoice(route,emptyList())
}
internal data class DiagramParticle(val x:Float,val y:Float,val flow:DiagramFlow,val edge:DiagramEdge)
internal class DiagramTimeline(private val d:DiagramDocument,private val paths:Map<String,PathMeasure>) {
    private data class Leg(val edge:DiagramEdge,val duration:Float,val bridgeFrom:FloatArray?=null,val bridgeTo:FloatArray?=null)
    private fun position(e:DiagramEdge,t:Float):FloatArray {
        val fraction=when(e.flow.direction) {FlowDirection.REVERSE->1-t;FlowDirection.BOUNCE->if(t<=.5f)t*2 else 2-t*2;else->t}
        return FloatArray(2).also {paths.getValue(e.id).getPosTan(paths.getValue(e.id).length*fraction.coerceIn(0f,1f),it,null)}
    }
    private val sequence=diagramRoute(d).edges.mapNotNull {id->d.edges.firstOrNull {it.id==id&&it.flow.enabled}}
    private val legs=buildList {
        sequence.forEachIndexed {i,e->
            if(i>0) {val a=position(sequence[i-1],1f);val b=position(e,0f);val distance=hypot(b[0]-a[0],b[1]-a[1]);if(distance>.001f)add(Leg(e,distance/240f,a,b))}
            add(Leg(e,(if(e.flow.direction==FlowDirection.BOUNCE)8f else 4f)/e.flow.speed))
        }
        if(d.playback.loop&&sequence.size>1&&flowTarget(sequence.last())==flowSource(sequence.first())) {
            val a=position(sequence.last(),1f);val b=position(sequence.first(),0f);val distance=hypot(b[0]-a[0],b[1]-a[1])
            if(distance>.001f)add(Leg(sequence.first(),distance/240f,a,b))
        }
    }
    val cycleSeconds=legs.sumOf {it.duration.toDouble()}.toFloat()
    fun onceSeconds():Float=when(d.playback.mode) {
        DiagramAnimationMode.OFF->0f
        DiagramAnimationMode.SEQUENTIAL->cycleSeconds*(2-1f/(sequence.firstOrNull()?.flow?.count ?: 1))
        else->d.edges.filter {it.flow.enabled&&(d.playback.mode!=DiagramAnimationMode.SEGMENT||it.id==d.playback.route.firstOrNull())}.maxOfOrNull {flowEnd(it.flow)} ?: 0f
    }
    fun particles(seconds:Float):List<DiagramParticle> {
        if(d.playback.mode==DiagramAnimationMode.OFF)return emptyList()
        if(d.playback.mode!=DiagramAnimationMode.SEQUENTIAL)return buildList {
            d.edges.filter {it.flow.enabled&&(d.playback.mode!=DiagramAnimationMode.SEGMENT||it.id==d.playback.route.firstOrNull())}.forEach {e->
                val flow=e.flow.copy(loop=e.flow.loop&&d.playback.loop)
                repeat(flow.count) {i->flowProgress(seconds,flow,i)?.let {progress->val p=FloatArray(2);val measure=paths.getValue(e.id);if(measure.length>.001f) {measure.getPosTan(measure.length*progress,p,null);add(DiagramParticle(p[0],p[1],flow,e))}}}
            }
        }
        if(cycleSeconds<=.001f||sequence.isEmpty())return emptyList()
        val first=sequence.first()
        return buildList {repeat(first.flow.count) {index->
            var t=seconds-cycleSeconds*index/first.flow.count
            if(d.playback.loop)t=((t%cycleSeconds)+cycleSeconds)%cycleSeconds else if(t<0||t>cycleSeconds)return@repeat
            for(leg in legs) {
                if(t>leg.duration) {t-=leg.duration;continue}
                val fraction=(t/leg.duration).coerceIn(0f,1f)
                val p=if(leg.bridgeFrom!=null&&leg.bridgeTo!=null)floatArrayOf(leg.bridgeFrom[0]+(leg.bridgeTo[0]-leg.bridgeFrom[0])*fraction,leg.bridgeFrom[1]+(leg.bridgeTo[1]-leg.bridgeFrom[1])*fraction) else position(leg.edge,fraction)
                // Keep the object's identity/aspect/asset across all legs; allow per-leg color.
                val flow=first.flow.copy(color=leg.edge.flow.color ?: first.flow.color,haloColor=leg.edge.flow.haloColor ?: first.flow.haloColor)
                add(DiagramParticle(p[0],p[1],flow,leg.edge));break
            }
        }}
    }
}
internal fun diagramTimeline(d:DiagramDocument)=DiagramTimeline(d,d.edges.associate {it.id to PathMeasure(edgePath(d,it),false)})
