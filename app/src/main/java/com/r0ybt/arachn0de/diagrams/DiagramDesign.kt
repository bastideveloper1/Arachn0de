package com.r0ybt.arachn0de.diagrams

import org.json.*

internal const val DIAGRAM_COAL=0xff0d131b.toInt()
internal enum class DiagramPageFormat(val label:String,val width:Int,val height:Int,val exportW:Int,val exportH:Int) {
    FREE("Libre",1600,1000,1600,1000),SQUARE("1:1",1000,1000,1080,1080),STORY("9:16",900,1600,1080,1920),PORTRAIT("4:5",1000,1250,1080,1350),WIDE("16:9",1600,900,1920,1080)
}
internal data class DiagramPage(val format:DiagramPageFormat=DiagramPageFormat.FREE,val width:Int=1600,val height:Int=1000)
internal enum class DiagramResizeContent { CONTAINER,SCALE }
internal data class DiagramGradient(val colors:List<Int>,val angle:Float=90f)
internal enum class DiagramParticleStyle { NORMAL,LUMINOUS,PULSE }
internal enum class DiagramAnimationMode { OFF,SEGMENT,SEQUENTIAL,SIMULTANEOUS }
internal data class DiagramPlayback(val mode:DiagramAnimationMode=DiagramAnimationMode.SIMULTANEOUS,val route:List<String> = emptyList(),val loop:Boolean=true)
internal object DiagramDesignCodec {
    fun obj(vararg pairs:Pair<String,Any?>)=JSONObject().apply {pairs.forEach {(k,v)->put(k,v ?: JSONObject.NULL)}}
    fun int(j:JSONObject,k:String,default:Int):Int {if(!j.has(k))return default;val value=j.get(k) as? Number ?: error("Entero inválido: $k");val n=value.toDouble();require(n.isFinite()&&n==value.toInt().toDouble());return value.toInt()}
    fun float(j:JSONObject,k:String,default:Float):Float {if(!j.has(k))return default;return (j.get(k) as? Number ?: error("Número inválido: $k")).toFloat().also {require(it.isFinite())}}
    fun bool(j:JSONObject,k:String,default:Boolean):Boolean=if(!j.has(k))default else j.get(k) as? Boolean ?: error("Booleano inválido: $k")
    inline fun <reified T:Enum<T>> enum(j:JSONObject,k:String,default:T):T=if(!j.has(k))default else enumValueOf(j.get(k) as? String ?: error("Tipo inválido: $k"))
    fun colors(raw:String,unique:Boolean=true):List<Int> {val a=JSONArray(raw);require(a.length()<=32);return (0 until a.length()).map {int(obj("v" to a.get(it)),"v",0)}.also {require(!unique||it.distinct().size==it.size)}}
    fun gradient(value:DiagramGradient?)=value?.let {obj("colors" to JSONArray(it.colors),"angle" to it.angle)}
    fun gradient(j:JSONObject,k:String):DiagramGradient? {
        if(!j.has(k)||j.isNull(k))return null
        val o=j.getJSONObject(k);val colors=colors(o.getJSONArray("colors").toString(),false);require(colors.size in 2..8)
        return DiagramGradient(colors,float(o,"angle",90f)).also {require(it.angle in 0f..360f)}
    }
    fun page(p:DiagramPage)=obj("format" to p.format.name,"width" to p.width,"height" to p.height)
    fun page(j:JSONObject):DiagramPage {if(!j.has("page"))return DiagramPage();val p=j.getJSONObject("page");return DiagramPage(enum(p,"format",DiagramPageFormat.FREE),int(p,"width",1600),int(p,"height",1000))}
    fun playback(p:DiagramPlayback)=obj("mode" to p.mode.name,"route" to JSONArray(p.route),"loop" to p.loop)
    fun playback(j:JSONObject):DiagramPlayback {
        if(!j.has("playback"))return DiagramPlayback()
        val p=j.getJSONObject("playback");val a=p.getJSONArray("route");require(a.length()<=192)
        return DiagramPlayback(enum(p,"mode",DiagramAnimationMode.SIMULTANEOUS),(0 until a.length()).map {a.get(it) as? String ?: error("Recorrido inválido")},bool(p,"loop",true))
    }
}
