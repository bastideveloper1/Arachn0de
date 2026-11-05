package com.r0ybt.arachn0de.diagrams

import android.util.Base64
import org.json.*
import java.util.UUID
import java.security.MessageDigest
import com.r0ybt.arachn0de.security.EncryptedPreferences

internal fun diagramId()=UUID.randomUUID().toString()
internal enum class DiagramShape(val label:String) { RECT("Rectángulo"),ROUND("Redondeado"),ELLIPSE("Elipse"),DIAMOND("Rombo"),TEXT("Texto"),IMAGE("Imagen / icono"),TABLE("Tabla ERD") }
internal enum class DiagramRoute(val label:String) { STRAIGHT("Recta"),ORTHOGONAL("Ortogonal"),CURVE("Curva") }
internal enum class DiagramAnchor { TOP,BOTTOM,LEFT,RIGHT }
internal enum class DiagramStroke { SOLID,DASHED,DOTTED }
internal enum class DiagramBackgroundFit { FIT,FILL }
internal enum class FlowObject(val label:String) { DOT("Punto"),SPHERE("Esfera"),ICON("Archivo"),IMAGE("Imagen propia") }
internal enum class FlowDirection(val label:String) { FORWARD("Origen → destino"),REVERSE("Destino → origen"),BOUNCE("Ida y vuelta") }
/** Scale is dp per logical pixel; center is normalized. Saved with the document/backup. */
internal data class DiagramCamera(val cx:Float,val cy:Float,val scale:Float)
internal data class DPoint(val x:Float,val y:Float)
internal data class DiagramField(val id:String=diagramId(),val name:String="",val type:String="",val primaryKey:Boolean=false,val foreignKey:Boolean=false)
internal fun diagramTableNode()=DiagramNode(shape=DiagramShape.TABLE,text="Tabla",w=.30f,h=.22f,fields=listOf(DiagramField(name="id",primaryKey=true),DiagramField(name="nombre")))
internal data class DiagramNode(val id:String=diagramId(),val shape:DiagramShape=DiagramShape.ROUND,val text:String="Elemento",val x:Float=.35f,val y:Float=.35f,val w:Float=.18f,val h:Float=.065f,val fill:Int=0xff1d2b3b.toInt(),val ink:Int=0xffdceaf8.toInt(),val stroke:Float=1.5f,val asset:String?=null,val border:Int=0xff6984a4.toInt(),val radius:Float=12f,val font:Float=28f,val lockAspect:Boolean=shape==DiagramShape.IMAGE,val centeredResize:Boolean=false,val resizeContent:DiagramResizeContent=DiagramResizeContent.CONTAINER,val gradient:DiagramGradient?=null,val fields:List<DiagramField> = emptyList())
internal data class DiagramFlow(val enabled:Boolean=false,val objectType:FlowObject=FlowObject.DOT,val asset:String?=null,val size:Float=18f,val direction:FlowDirection=FlowDirection.FORWARD,val speed:Float=1f,val loop:Boolean=true,val count:Int=1,val particleStyle:DiagramParticleStyle=DiagramParticleStyle.NORMAL,val color:Int?=0xff43e8ff.toInt(),val haloColor:Int?=null,val glow:Float=.6f,val imageHalo:Boolean=false)
internal data class DiagramEdge(val id:String=diagramId(),val from:String,val to:String,val route:DiagramRoute=DiagramRoute.STRAIGHT,val points:List<DPoint> = emptyList(),val startArrow:Boolean=false,val endArrow:Boolean=true,val color:Int=0xff6984a4.toInt(),val width:Float=2f,val label:String="",val flow:DiagramFlow=DiagramFlow(),val stroke:DiagramStroke=DiagramStroke.SOLID,val fromAnchor:DiagramAnchor?=null,val toAnchor:DiagramAnchor?=null)
internal data class DiagramDocument(val id:String=diagramId(),val name:String="Diagrama",val nodes:List<DiagramNode> = emptyList(),val edges:List<DiagramEdge> = emptyList(),val background:Int=DIAGRAM_COAL,val camera:DiagramCamera?=null,val backgroundAsset:String?=null,val backgroundFit:DiagramBackgroundFit=DiagramBackgroundFit.FIT,val page:DiagramPage=DiagramPage(),val playback:DiagramPlayback=DiagramPlayback(),val backgroundGradient:DiagramGradient?=null) {
    val width get()=page.width
    val height get()=page.height
    fun removeNode(id:String):DiagramDocument {val kept=edges.filterNot {it.from==id||it.to==id};return copy(nodes=nodes.filterNot {it.id==id},edges=kept,playback=playback.copy(route=if(kept.size==edges.size)playback.route else emptyList()))}
    fun assets()=nodes.mapNotNull {it.asset}.toSet()+edges.mapNotNull {it.flow.asset}+listOfNotNull(backgroundAsset)
}
internal data class DiagramLibrary(val documents:List<DiagramDocument> = emptyList(),val assets:Map<String,String> = emptyMap(),val colors:List<Int> = emptyList())
internal object DiagramCodec {
    const val NAME="diagram_library"
    const val LIMIT=3*1024*1024
    private fun uuid(s:String) {require(UUID.fromString(s).toString()==s)}
    fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
    fun validate(d:DiagramDocument) {
        require(d.width in 320..4096&&d.height in 320..4096&&d.width.toLong()*d.height<=8_000_000)
        if(d.page.format!=DiagramPageFormat.FREE)require(kotlin.math.abs(d.width.toFloat()/d.height-d.page.format.width.toFloat()/d.page.format.height)<.001f)
        fun gradient(g:DiagramGradient?) {g?.let {require(it.colors.size in 2..8&&it.angle.isFinite()&&it.angle in 0f..360f)}}
        gradient(d.backgroundGradient)
        require(d.playback.route.size<=192&&d.playback.route.distinct().size==d.playback.route.size)
        require(d.playback.route.all {id->d.edges.any {it.id==id}})
        d.playback.route.zipWithNext().forEach {(a,b)->require(flowTarget(d.edges.first {it.id==a})==flowSource(d.edges.first {it.id==b})) {"El recorrido debe enlazar nodos del grafo."}}
        require(d.assets().size<=12) {"Máximo 12 recursos distintos por diagrama."}
        d.camera?.let {require(it.cx.isFinite()&&it.cy.isFinite()&&it.scale.isFinite()&&it.cx in -1f..2f&&it.cy in -1f..2f&&it.scale in .01f..8f)}
        uuid(d.id);require(d.name.isNotBlank()&&d.name.length<=100&&d.nodes.size<=128&&d.edges.size<=192)
        val ids=d.nodes.map {it.id};require(ids.distinct().size==ids.size)
        d.nodes.forEach {n->
            require(n.fields.size<=32&&n.fields.map {it.id}.distinct().size==n.fields.size)
            require(n.shape==DiagramShape.TABLE||n.fields.isEmpty())
            if(n.shape==DiagramShape.TABLE)require(n.text.length<=100)
            n.fields.forEach {field->uuid(field.id);require(field.name.length<=100&&field.type.length<=64&&'\n' !in field.name&&'\n' !in field.type)}
            require(n.radius.isFinite()&&n.radius in 0f..200f&&n.font.isFinite()&&n.font in 12f..192f);gradient(n.gradient);uuid(n.id);require(n.text.length<=4000&&listOf(n.x,n.y,n.w,n.h,n.stroke).all {it.isFinite()});require(n.x>=0&&n.y>=0&&n.w in .001f..1f&&n.h in .001f..1f&&n.w*d.width>=7.99f&&n.h*d.height>=7.99f&&n.x+n.w<=1.00001f&&n.y+n.h<=1.00001f&&n.stroke in 0f..20f);require(n.shape!=DiagramShape.IMAGE||n.asset!=null)}
        require(d.edges.map {it.id}.distinct().size==d.edges.size)
        d.edges.forEach {e->uuid(e.id);require(e.from in ids&&e.to in ids&&e.from!=e.to&&e.label.length<=1000&&e.width.isFinite()&&e.width in 1f..20f&&e.points.size<=8);e.points.forEach {require(it.x.isFinite()&&it.y.isFinite()&&it.x in 0f..1f&&it.y in 0f..1f)};val f=e.flow;require(f.glow.isFinite()&&f.glow in 0f..1f);require(f.count in 1..8&&f.size.isFinite()&&f.size in 6f..100f&&f.speed in setOf(.5f,1f,2f));require(f.objectType!=FlowObject.IMAGE||f.asset!=null)}
        require(d.edges.filter {it.flow.enabled}.sumOf {it.flow.count}<=64) {"Máximo 64 partículas activas por diagrama."}
    }
    private fun obj(vararg pairs:Pair<String,Any?>)=JSONObject().apply {pairs.forEach {(k,v)->put(k,v ?: JSONObject.NULL)}}
    fun encode(d:DiagramDocument):String {validate(d);return obj("version" to if(d.nodes.any {it.shape==DiagramShape.TABLE})3 else if(d.edges.any {it.fromAnchor!=null||it.toAnchor!=null})2 else 1,"id" to d.id,"name" to d.name,"background" to d.background,"page" to DiagramDesignCodec.page(d.page),"playback" to DiagramDesignCodec.playback(d.playback),"backgroundGradient" to DiagramDesignCodec.gradient(d.backgroundGradient),"backgroundAsset" to d.backgroundAsset,"backgroundFit" to d.backgroundFit.name,"camera" to d.camera?.let {obj("cx" to it.cx,"cy" to it.cy,"scale" to it.scale)},
        "nodes" to JSONArray(d.nodes.map {n->obj("id" to n.id,"shape" to n.shape.name,"text" to n.text,"x" to n.x,"y" to n.y,"w" to n.w,"h" to n.h,"fill" to n.fill,"ink" to n.ink,"stroke" to n.stroke,"asset" to n.asset,"borderColor" to n.border,"radius" to n.radius,"font" to n.font,"lockAspect" to n.lockAspect,"centeredResize" to n.centeredResize,"resizeContent" to n.resizeContent.name,"gradient" to DiagramDesignCodec.gradient(n.gradient)).apply {if(n.shape==DiagramShape.TABLE)put("fields",JSONArray(n.fields.map {field->obj("id" to field.id,"name" to field.name,"type" to field.type,"pk" to field.primaryKey,"fk" to field.foreignKey)}))}}),
        "edges" to JSONArray(d.edges.map {e->obj("id" to e.id,"from" to e.from,"to" to e.to,"route" to e.route.name,"strokeStyle" to e.stroke.name,"points" to JSONArray(e.points.map {JSONArray(listOf(it.x,it.y))}),"start" to e.startArrow,"end" to e.endArrow,"color" to e.color,"width" to e.width,"label" to e.label,"flow" to obj("enabled" to e.flow.enabled,"object" to e.flow.objectType.name,"asset" to e.flow.asset,"size" to e.flow.size,"direction" to e.flow.direction.name,"speed" to e.flow.speed,"loop" to e.flow.loop,"count" to e.flow.count,"particleStyle" to e.flow.particleStyle.name,"particleColor" to e.flow.color,"haloColor" to e.flow.haloColor,"glow" to e.flow.glow,"imageHalo" to e.flow.imageHalo)).apply {e.fromAnchor?.let {put("fromAnchor",it.name)};e.toAnchor?.let {put("toAnchor",it.name)}}})).toString().also {require(it.toByteArray().size<=512*1024) {"El documento supera 512 KiB."}}}
    private fun JSONObject.s(k:String)=get(k) as? String ?: error("Texto inválido: $k")
    private fun JSONObject.b(k:String)=get(k) as? Boolean ?: error("Booleano inválido: $k")
    private fun JSONObject.i(k:String):Int {val n=get(k) as? Number ?: error("Entero inválido");val v=n.toDouble();require(v.isFinite()&&v==n.toLong().toDouble()&&v in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble());return n.toInt()}
    private fun JSONObject.f(k:String)=(get(k) as? Number ?: error("Número inválido")).toFloat().also {require(it.isFinite())}
    private fun JSONObject.nullString(k:String):String? {require(has(k));return if(isNull(k)) null else s(k)}
    private fun fields(n:JSONObject,version:Int):List<DiagramField> {
        if(n.s("shape")!=DiagramShape.TABLE.name) {require(!n.has("fields"));return emptyList()}
        require(version>=3);val fields=n.getJSONArray("fields");require(fields.length()<=32)
        return (0 until fields.length()).map {i->val f=fields.getJSONObject(i);DiagramField(f.s("id"),f.s("name"),f.s("type"),f.b("pk"),f.b("fk"))}
    }
    fun decode(raw:String):DiagramDocument {
        require(raw.toByteArray().size<=512*1024);val j=JSONObject(raw);require(j.i("version") in 1..3)
        val ns=j.getJSONArray("nodes");val es=j.getJSONArray("edges");require(ns.length()<=128&&es.length()<=192)
        return DiagramDocument(j.s("id"),j.s("name"),(0 until ns.length()).map {i->val n=ns.getJSONObject(i);DiagramNode(n.s("id"),DiagramShape.valueOf(n.s("shape")),n.s("text"),n.f("x"),n.f("y"),n.f("w"),n.f("h"),n.i("fill"),n.i("ink"),n.f("stroke"),n.nullString("asset"),DiagramDesignCodec.int(n,"borderColor",n.i("ink")),DiagramDesignCodec.float(n,"radius",20f),DiagramDesignCodec.float(n,"font",48f),DiagramDesignCodec.bool(n,"lockAspect",DiagramShape.valueOf(n.s("shape"))==DiagramShape.IMAGE),DiagramDesignCodec.bool(n,"centeredResize",false),DiagramDesignCodec.enum(n,"resizeContent",DiagramResizeContent.CONTAINER),DiagramDesignCodec.gradient(n,"gradient"),fields(n,j.i("version")))},(0 until es.length()).map {i->val e=es.getJSONObject(i);val f=e.getJSONObject("flow");val ps=e.getJSONArray("points");require(ps.length()<=8);DiagramEdge(e.s("id"),e.s("from"),e.s("to"),DiagramRoute.valueOf(e.s("route")),(0 until ps.length()).map {val p=ps.getJSONArray(it);DPoint(p.getDouble(0).toFloat(),p.getDouble(1).toFloat())},e.b("start"),e.b("end"),e.i("color"),e.f("width"),e.s("label"),DiagramFlow(f.b("enabled"),FlowObject.valueOf(f.s("object")),f.nullString("asset"),f.f("size"),FlowDirection.valueOf(f.s("direction")),f.f("speed"),f.b("loop"),f.i("count"),DiagramDesignCodec.enum(f,"particleStyle",DiagramParticleStyle.NORMAL),if(!f.has("particleColor")||f.isNull("particleColor"))null else f.i("particleColor"),if(!f.has("haloColor")||f.isNull("haloColor"))null else f.i("haloColor"),DiagramDesignCodec.float(f,"glow",.6f),DiagramDesignCodec.bool(f,"imageHalo",false)),if(e.has("strokeStyle"))DiagramStroke.valueOf(e.s("strokeStyle")) else DiagramStroke.SOLID,if(!e.has("fromAnchor")||e.isNull("fromAnchor"))null else DiagramAnchor.valueOf(e.s("fromAnchor")),if(!e.has("toAnchor")||e.isNull("toAnchor"))null else DiagramAnchor.valueOf(e.s("toAnchor")))},j.i("background"),if(!j.has("camera")||j.isNull("camera"))null else j.getJSONObject("camera").let {DiagramCamera(it.f("cx"),it.f("cy"),it.f("scale"))},if(j.has("backgroundAsset"))j.nullString("backgroundAsset") else null,if(j.has("backgroundFit"))DiagramBackgroundFit.valueOf(j.s("backgroundFit")) else DiagramBackgroundFit.FIT,DiagramDesignCodec.page(j),DiagramDesignCodec.playback(j),DiagramDesignCodec.gradient(j,"backgroundGradient")).also(::validate)
    }
    fun library(values:Map<String,*>):DiagramLibrary {
        require(values.size<=81)
        val docs=mutableListOf<DiagramDocument>();val assets=mutableMapOf<String,String>();var colors=emptyList<Int>()
        values.forEach {(key,v)->val raw=v as? String ?: error("Dato de Diagramas inválido.");when {
            key=="favorite_colors"->colors=DiagramDesignCodec.colors(raw)
            key.startsWith("doc_")->docs+=decode(raw).also {require(key=="doc_${it.id}")}
            key.startsWith("asset_")-> {require(raw.length<=1_400_000);val bytes=Base64.decode(raw,Base64.NO_WRAP);require(key=="asset_${hash(bytes)}");require(com.r0ybt.arachn0de.visual.canvasImage(bytes)==raw);assets[key.removePrefix("asset_")]=raw}
            else->error("Clave de Diagramas desconocida.")
        }}
        require(docs.size<=32&&assets.size<=48);docs.forEach {require(it.assets().all {id->id in assets}) {"Recurso de Diagramas ausente."}}
        return DiagramLibrary(docs,assets,colors)
    }
    fun validatePayload(payload:String) {require(payload.toByteArray().size<=LIMIT) {"La biblioteca Diagramas supera 3 MiB."};library(EncryptedPreferences.decode(payload))}
}
internal class DiagramHistory(initial:DiagramDocument) {
    private val delegate=com.r0ybt.arachn0de.visual.EditHistory(initial,DiagramCodec::validate)
    val current get()=delegate.current
    val canUndo get()=delegate.canUndo
    val canRedo get()=delegate.canRedo
    fun commit(d:DiagramDocument)=delegate.commit(d)
    fun undo()=delegate.undo()
    fun redo()=delegate.redo()
}
internal fun diagramTemplate(index:Int,portrait:Boolean=false):DiagramDocument {
    if(index==0)return DiagramDocument()
    val names=listOf("", "Flujo básico","Arquitectura de software","Red de dispositivos","Mapa conceptual","Explicación libre")
    val labels=when(index) {1->listOf("Inicio","¿Condición?","Resultado");2->listOf("Cliente","API","Base de datos");3->listOf("PC","Router","Servidor");4->listOf("Idea","Concepto A","Concepto B");else->listOf("Idea","Explicación")}
    val nodes=labels.mapIndexed {i,label->DiagramNode(text=label,x=if(portrait).41f else .18f+i*.22f,y=if(portrait).12f+i*.10f else .35f,w=.18f,h=.065f,shape=if(index==1&&i==1)DiagramShape.DIAMOND else DiagramShape.ROUND)}
    return DiagramDocument(name=names[index],nodes=nodes,edges=nodes.zipWithNext {a,b->DiagramEdge(from=a.id,to=b.id,color=0xff6984a4.toInt(),width=2f,flow=DiagramFlow(enabled=index==3,objectType=FlowObject.SPHERE,size=10f,particleStyle=DiagramParticleStyle.LUMINOUS))},camera=DiagramCamera(.5f,if(portrait).35f else .42f,.5f))
}
