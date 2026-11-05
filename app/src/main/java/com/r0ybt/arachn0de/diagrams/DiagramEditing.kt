package com.r0ybt.arachn0de.diagrams

import androidx.compose.ui.geometry.Offset
import com.r0ybt.arachn0de.visual.CanvasViewport
import com.r0ybt.arachn0de.wireframe.*
import kotlin.math.*

internal enum class DiagramArrange { LEFT,HCENTER,RIGHT,TOP,VCENTER,BOTTOM,HSPACE,VSPACE }
internal fun arrangeDiagram(d:DiagramDocument,selection:Set<String>,action:DiagramArrange):DiagramDocument {
    val nodes=d.nodes.filter {it.id in selection};if(nodes.size<2)return d
    val left=nodes.minOf {it.x};val right=nodes.maxOf {it.x+it.w};val top=nodes.minOf {it.y};val bottom=nodes.maxOf {it.y+it.h}
    val positions=mutableMapOf<String,DiagramNode>()
    if(action==DiagramArrange.HSPACE||action==DiagramArrange.VSPACE) {
        if(nodes.size<3)return d
        val horizontal=action==DiagramArrange.HSPACE
        val sorted=nodes.sortedBy {if(horizontal)it.x else it.y}
        val start=if(horizontal)left else top;val end=if(horizontal)right else bottom
        val gap=(end-start-nodes.sumOf {if(horizontal)it.w.toDouble() else it.h.toDouble()}.toFloat())/(nodes.size-1)
        if(gap<0)return d // Never resolve overlapping bounds by moving nodes out of the document.
        var position=start
        sorted.forEach {n->positions[n.id]=if(horizontal)n.copy(x=position) else n.copy(y=position);position+=(if(horizontal)n.w else n.h)+gap}
    } else nodes.forEach {n->positions[n.id]=when(action) {
        DiagramArrange.LEFT->n.copy(x=left);DiagramArrange.HCENTER->n.copy(x=(left+right-n.w)/2);DiagramArrange.RIGHT->n.copy(x=right-n.w)
        DiagramArrange.TOP->n.copy(y=top);DiagramArrange.VCENTER->n.copy(y=(top+bottom-n.h)/2);DiagramArrange.BOTTOM->n.copy(y=bottom-n.h)
        else->n
    }}
    return d.copy(nodes=d.nodes.map {positions[it.id] ?: it}).also(DiagramCodec::validate)
}
internal fun canArrange(d:DiagramDocument,ids:Set<String>,action:DiagramArrange)=arrangeDiagram(d,ids,action)!=d

/** Adapter to Wireframe's established magnetic alignment; its model and behavior stay unchanged. */
internal fun diagramSmartAlign(d:DiagramDocument,n:DiagramNode,tx:Float,ty:Float):Triple<DiagramNode,Float?,Float?> {
    fun wire(n:DiagramNode)=WireElement(id=n.id,x=n.x,y=n.y,w=n.w,h=n.h)
    val result=wireSmartAlign(WireDocument(elements=d.nodes.map(::wire)),wire(n),tx,ty,true)
    return Triple(n.copy(x=result.element.x,y=result.element.y),result.vertical,result.horizontal)
}
internal data class DiagramBadge(val edgeId:String,val number:Int,val point:Offset)
internal fun diagramBadges(d:DiagramDocument,order:List<String>,v:CanvasViewport,width:Float,height:Float,radius:Float):List<DiagramBadge> {
    val result=mutableListOf<DiagramBadge>();val out=FloatArray(2);val tangent=FloatArray(2)
    fun screen(x:Float,y:Float)=Offset(v.left+x/d.width*v.width,v.top+y/d.height*v.height)
    d.edges.forEach {edge->
        val m=android.graphics.PathMeasure(edgePath(d,edge),false)
        if(m.length<.001f)return@forEach
        val candidates=buildList {
            for(f in listOf(.5f,.35f,.65f,.2f,.8f,.05f,.95f)) {
                m.getPosTan(m.length*f,out,tangent)
                val p=screen(out[0],out[1]);val t=Offset(tangent[0]*v.width/d.width,tangent[1]*v.height/d.height)
                val length=t.getDistance().coerceAtLeast(.001f);val normal=Offset(-t.y/length,t.x/length)
                val clearance=radius*1.8f+if(edge.flow.enabled)edge.flow.size/2*max(v.width/d.width,v.height/d.height) else 0f
                for(side in listOf(1,-1))add(p+normal*(clearance*side))
            }
        }
        fun penalty(p:Offset):Int {
            var score=if(p.x<radius||p.x>width-radius||p.y<radius||p.y>height-radius)100 else 0
            score+=d.nodes.count {n->p.x in (v.left+n.x*v.width-radius)..(v.left+(n.x+n.w)*v.width+radius)&&p.y in (v.top+n.y*v.height-radius)..(v.top+(n.y+n.h)*v.height+radius)}*10
            score+=result.count {(it.point-p).getDistance()<radius*2.5f}*8
            score+=edge.points.count {(screen(it.x*d.width,it.y*d.height)-p).getDistance()<radius*2}*8
            return score
        }
        val point=candidates.minByOrNull(::penalty) ?: return@forEach
        result+=DiagramBadge(edge.id,order.indexOf(edge.id)+1,point)
    }
    return result
}

internal fun adaptDiagramContrast(d:DiagramDocument):DiagramDocument {
    fun light(c:Int)=android.graphics.Color.luminance(c)>.18f
    return d.copy(nodes=d.nodes.map {n->if((n.shape in setOf(DiagramShape.TEXT,DiagramShape.IMAGE)||android.graphics.Color.alpha(n.fill)<128)&&!light(n.ink))n.copy(ink=android.graphics.Color.WHITE) else n},
        edges=d.edges.map {e->if(!light(e.color))e.copy(color=0xffb3ddff.toInt()) else e})
}
internal fun diagramArrangeLabel(action:DiagramArrange)=when(action) {
    DiagramArrange.LEFT->"Alinear izquierda";DiagramArrange.HCENTER->"Centrar horizontalmente";DiagramArrange.RIGHT->"Alinear derecha"
    DiagramArrange.TOP->"Alinear arriba";DiagramArrange.VCENTER->"Centrar verticalmente";DiagramArrange.BOTTOM->"Alinear abajo"
    DiagramArrange.HSPACE->"Distribuir horizontalmente";DiagramArrange.VSPACE->"Distribuir verticalmente"
}
internal fun diagramPanelTitle(detail:String?,panel:String)=when(detail) {
    "node:ink"->"Color del texto";"duplicate:direction"->"Dirección de duplicación";"format"->"Formato y encuadre";"animation"->"Animación global";"flow:appearance"->"Estilo y brillo";"node:text"->"Texto";"node:fill"->"Fondo del elemento";"node:border"->"Borde"
    "edge:menu"->"Conexión";"edge:route"->"Trayectoria";"edge:stroke"->"Trazo";"edge:arrows"->"Flechas";"edge:color"->"Color"
    "edge:width"->"Grosor";"edge:text"->"Etiqueta";"edge:points"->"Puntos de recorrido";"edge:flow"->"Animar"
    "flow:object"->"Objeto móvil";"flow:direction"->"Dirección";"flow:timing"->"Movimiento"
    "grid"->"Cuadrícula";"align"->"Alineación";"view"->"Vista";"background"->"Fondo"
    "document:menu"->"Documento";"document:name"->"Nombre";"document:export"->"Exportar";"import"->"Importar"
    else->if(detail?.startsWith("export:")==true)detail.substringAfter(':') else panel
}
internal fun diagramPanelHelp(detail:String?)=when {
    detail?.startsWith("export:")==true->"PNG conserva transparencia y proporción. Las dimensiones finales aparecen en el perfil elegido. GIF: 10 fps, paleta limitada y alpha binario. MP4: H.264, 20 fps, fondo blanco sin transparencia, desde Android 8 y con una carpeta compatible. Se completan los recorridos Una vez hasta 60 s. Máximo 96 MiB."
    detail=="align"->"Activa Selección múltiple y toca los nodos. Alineación requiere dos; distribución requiere tres y espacio suficiente. Un toque más retira un nodo de la selección."
    detail=="grid"->"Mostrar cuadrícula y ajustar son independientes. El espaciado es una fracción del documento; al alejar, se simplifica solo la cuadrícula visible."
    detail=="background"->"La imagen queda fija detrás de los elementos. Ajustar conserva toda la imagen; Rellenar recorta sin deformar. Cambiar fondo no cambia colores; Adaptar contraste es opcional."
    detail=="edge:points"->"Añade un punto y arrastra su tirador azul en el lienzo."
    detail?.contains("flow")==true->"Activa Flujo animado y elige un objeto. Probar conexión reproduce en vivo sin salir de edición; detener no modifica el documento."
    else->"Mantén pulsado un icono para ver su nombre. Conectar: arrastra el puerto azul del origen hasta otro nodo. Los ajustes se aplican en vivo. Confirmar o Atrás vuelve un nivel; minimizar conserva la edición."
}

internal fun diagramAnchorPoint(n:DiagramNode,side:DiagramAnchor)=when(side) {
    DiagramAnchor.TOP->Offset(n.x+n.w/2,n.y);DiagramAnchor.BOTTOM->Offset(n.x+n.w/2,n.y+n.h)
    DiagramAnchor.LEFT->Offset(n.x,n.y+n.h/2);DiagramAnchor.RIGHT->Offset(n.x+n.w,n.y+n.h/2)
}
internal fun diagramNearestAnchor(n:DiagramNode,at:Offset,width:Float,height:Float)=DiagramAnchor.entries.minBy {side->
    val p=diagramAnchorPoint(n,side);kotlin.math.hypot((p.x-at.x)*width,(p.y-at.y)*height)
}
internal fun reconnectDiagram(d:DiagramDocument,e:DiagramEdge,source:Boolean,node:String,side:DiagramAnchor):DiagramDocument {
    val updated=if(source)e.copy(from=node,fromAnchor=side) else e.copy(to=node,toAnchor=side)
    if(updated.from==updated.to)return d
    val next=d.copy(edges=d.edges.map {if(it.id==e.id)updated else it})
    val route=next.playback.route.map {id->next.edges.first {it.id==id}}
    return if(route.zipWithNext().all {(a,b)->flowTarget(a)==flowSource(b)})next else next.copy(playback=next.playback.copy(route=emptyList()))
}

/** The pointer delta and all anchors live in document coordinates, never in handle bounds. */
internal fun diagramGestureAlign(d:DiagramDocument,n:DiagramNode,dx:Float,dy:Float,handle:DiagramHandle?,snap:Boolean,step:Float,magnetic:Boolean,tx:Float,ty:Float):Triple<DiagramNode,Float?,Float?> {
    var x=dx;var y=dy
    fun build(a:Float=x,b:Float=y)=if(handle==null)moveDiagramNode(n,a,b,false,false) else resizeDiagramNode(n,a,b,handle)
    fun anchors(value:DiagramNode,horizontal:Boolean)=if(horizontal)listOf(value.x,value.x+value.w/2,value.x+value.w) else listOf(value.y,value.y+value.h/2,value.y+value.h)
    fun targets(horizontal:Boolean)=listOf(.5f)+d.nodes.filter {it.id!=n.id}.flatMap {anchors(it,horizontal)}
    if(snap) {
        if(handle==null) {x=((n.x+x)/step).roundToInt()*step-n.x;y=((n.y+y)/step).roundToInt()*step-n.y}
        else {
            val v=build()
            if(handle.fx!=0) {val edge=if(handle.fx<0)v.x else v.x+v.w;x+=((edge/step).roundToInt()*step-edge)}
            if(handle.fy!=0) {val edge=if(handle.fy<0)v.y else v.y+v.h;y+=((edge/step).roundToInt()*step-edge)}
        }
    }
    fun align(horizontal:Boolean) {
        val value=build();val before=anchors(value,horizontal)
        val probe=if(horizontal)build(x+.001f,y) else build(x,y+.001f)
        val after=anchors(probe,horizontal);val tolerance=if(horizontal)tx else ty
        var bestError=Float.POSITIVE_INFINITY;var correction=0f
        for(target in targets(horizontal))for(i in before.indices) {
            val slope=(after[i]-before[i])/.001f;val error=target-before[i]
            if(abs(slope)<.01f||abs(error)>tolerance||abs(error)>=bestError)continue
            val delta=error/slope
            val candidate=if(horizontal)build(x+delta,y) else build(x,y+delta)
            if(abs(anchors(candidate,horizontal)[i]-target)>.00001f)continue
            bestError=abs(error);correction=delta
        }
        if(horizontal)x+=correction else y+=correction
    }
    if(magnetic) {align(true);align(false)}
    val result=build()
    fun guide(horizontal:Boolean):Float? {
        val tolerance=if(magnetic).00001f else if(horizontal)tx else ty
        val actual=anchors(result,horizontal);val probe=anchors(build(x+.001f,y+.001f),horizontal)
        return targets(horizontal).flatMap {target->actual.mapIndexedNotNull {i,anchor->
            val error=abs(anchor-target)
            if(error>tolerance)null else Triple(target,if(abs(probe[i]-anchor)>.000001f)0 else 1,error)
        }}.minWithOrNull(compareBy<Triple<Float,Int,Float>> {it.second}.thenBy {it.third})?.first
    }
    return Triple(result,guide(true),guide(false))
}
