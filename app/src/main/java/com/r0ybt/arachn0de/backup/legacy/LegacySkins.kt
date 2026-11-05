package com.r0ybt.arachn0de.backup.legacy

import com.r0ybt.arachn0de.ui.theme.AppearanceTheme

import android.util.Base64
import org.json.*
import java.util.UUID

/** Retired skin payloads: backup validation and reference remapping only; never rendered. */
data class ThemeStop(val position:Float,val color:Int)
enum class ThemeGradientType { LINEAR,RADIAL,ANGULAR }
data class ThemeGradient(val type:ThemeGradientType=ThemeGradientType.LINEAR,val stops:List<ThemeStop>,val angle:Float=90f,val intensity:Float=1f)
data class ThemePaint(val color:Int?=null,val gradient:ThemeGradient?=null)
enum class ThemeHeaderMode { ORIGINAL,NAME,IMAGE }
data class ThemeHeader(val mode:ThemeHeaderMode=ThemeHeaderMode.ORIGINAL,val image:String?=null,val sha256:String?=null,val scale:Float=1f,val x:Float=0f,val y:Float=0f,val spacing:Float=4f,val crop:Boolean=false)
data class ThemeImage(val image:String,val sha256:String,val zoom:Float=1f,val x:Float=0f,val y:Float=0f,val original:ThemeOriginal?=null)
internal val themeImageTargets=linkedMapOf("drawer" to "Encabezado del menú", "core" to "Núcleos", "bank" to "Núcleos Banco", "layer" to "Capas", "task" to "Tareas", "note" to "Notas")
data class CustomTheme(val id:String=UUID.randomUUID().toString(),val name:String="Mi tema",val description:String="",val base:AppearanceTheme=AppearanceTheme.Arachn0de,val colors:Map<String,Int> = emptyMap(),val paints:Map<String,ThemePaint> = emptyMap(),val header:ThemeHeader=ThemeHeader(),val images:Map<String,ThemeImage> = emptyMap(),val swatch:String?=null,val completionSound:String?=null)
internal val themeColorLabels=linkedMapOf("background" to "Fondo principal","middle" to "Fondo secundario","end" to "Fondo final","surface" to "Superficies","raised" to "Superficie elevada","cards" to "Tarjetas","panel" to "Panel inferior","taskPanel" to "Panel de tareas","drawer" to "Menú lateral","header" to "Barra superior y encabezado","text" to "Texto principal","muted" to "Texto secundario","outline" to "Bordes y separadores","primary" to "Acento","secondary" to "Acento secundario","buttons" to "Botones","onButtons" to "Texto de botones","icons" to "Iconos","controls" to "Campos de texto","disabled" to "Controles deshabilitados","selection" to "Selección","accentSurface" to "Superficie seleccionada","priority" to "Prioridad","error" to "Error","warning" to "Advertencia","success" to "Éxito","scrim" to "Fondo modal")
internal val themePaintRoles=setOf("background","surface","cards","panel","taskPanel","drawer","header","buttons","accentSurface")
internal object CustomThemeCodec {
    const val LIBRARY="custom_themes";const val ACTIVE="custom_theme";const val FAVORITES="theme_favorites"
    const val MAX_BYTES=2*1024*1024
    const val MAX_LIBRARY_BYTES=4*1024*1024
    fun obj(vararg p:Pair<String,Any?>)=JSONObject().apply {p.forEach {(k,v)->put(k,v ?: JSONObject.NULL)}}
    /** Bound nesting before invoking the recursive platform JSON parser. */
    private fun structure(raw:String) {
        var depth=0;var quoted=false;var escaped=false;var closed=false
        raw.forEach {c->
            if(quoted) {
                if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false
            } else {
                require(!closed||c.isWhitespace()) {"Contenido después del tema."}
                when(c) {
                    '"'->quoted=true
                    '\''->error("El tema requiere JSON con comillas dobles.")
                    '{','['->{depth++;require(depth<=16) {"Tema demasiado anidado."}}
                    '}',']'->{depth--;require(depth>=0);if(depth==0)closed=true}
                }
            }
        }
        require(closed&&!quoted&&depth==0) {"JSON de tema incompleto."}
    }
    private fun fields(j:JSONObject,allowed:Set<String>) {require(j.keys().asSequence().all {it in allowed}) {"Campo de tema desconocido."}}
    private fun string(j:JSONObject,k:String)=j.get(k) as? String ?: error("Texto de tema inválido: $k")
    private fun number(j:JSONObject,k:String)=(j.get(k) as? Number ?: error("Número inválido: $k")).toDouble().also {require(it.isFinite())}
    fun integer(j:JSONObject,k:String)=number(j,k).also {require(it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()&&it==it.toInt().toDouble())}.toInt()
    private fun bool(j:JSONObject,k:String)=j.get(k) as? Boolean ?: error("Booleano inválido: $k")
    fun validate(t:CustomTheme) {
        t.completionSound?.let {com.r0ybt.arachn0de.completion.CompletionSoundCodec.decode(it)}
        require(UUID.fromString(t.id).toString()==t.id) {"Identificador de tema incompatible."}
        require(t.name.isNotBlank()&&t.name.length<=80&&t.description.length<=500) {"El tema necesita un nombre de hasta 80 caracteres y una descripción de hasta 500."}
        require(t.colors.keys.all {it in themeColorLabels}&&t.paints.keys.all {it in themePaintRoles})
        val opaque=themePaintRoles+setOf("middle","end","raised","controls","selection")
        t.colors.filterKeys {it in opaque}.values.forEach {require(it ushr 24==255) {"Esta superficie debe ser opaca."}}
        t.paints.forEach {(role,p)->require((p.color==null)!=(p.gradient==null));p.color?.let {require(it ushr 24==255)};p.gradient?.let {g->
            require(g.angle.isFinite()&&g.angle in 0f..360f&&g.intensity.isFinite()&&g.intensity in 0f..1f)
            require(g.stops.size in 2..8&&g.stops.first().position==0f&&g.stops.last().position==1f)
            require(g.stops.all {it.position.isFinite()&&it.position in 0f..1f&&it.color ushr 24==255}) {"El degradado de $role debe cubrir una superficie opaca."}
            require(g.stops.zipWithNext().all {(a,b)->a.position<b.position})
        }}
        require(t.images.keys.all {it in themeImageTargets}) {"Superficie de imagen desconocida."}
        require(t.swatch==null||t.swatch in setOf("RED","BLUE","GREEN","PINK","PURPLE","BROWN","GREY","GOLD","YELLOW","ORANGE")) {"Muestra de color desconocida."}
        t.images.forEach {(target,image)->
            try {
            com.r0ybt.arachn0de.domain.model.AvatarFraming(image.zoom,image.x,image.y).validate()
            validateImage(image.image,image.sha256)
            image.original?.let {o->require(com.r0ybt.arachn0de.backup.imageNameValid(THEME_ORIGINAL_DIRECTORY,o.name)&&java.util.UUID.fromString(o.name.substringBefore('.')).toString()==o.name.substringBefore('.')) {"Nombre interno del original incompatible."};require(o.sha256.matches(Regex("[a-f0-9]{64}"))&&o.byteSize in 1..THEME_ORIGINAL_LIMIT.toLong()&&o.width>0&&o.height>0&&o.width.toLong()*o.height<=100_000_000&&themeOriginalMime(o.mime)&&o.orientation in 1..8) {"Dimensiones, formato, orientación o huella del original incompatibles."}}
            } catch(e:IllegalArgumentException) {
                throw IllegalArgumentException("${themeImageTargets[target]}: ${e.message ?: "validación de imagen fallida"}",e)
            }
        }
        val h=t.header;require(listOf(h.scale,h.x,h.y,h.spacing).all {it.isFinite()}&&h.scale in .25f..3f&&h.x in -1f..1f&&h.y in -1f..1f&&h.spacing in 0f..24f) {"Escala, posición o separación del encabezado fuera de rango."}
        require((h.image==null)==(h.sha256==null)) {"El encabezado necesita imagen y huella SHA-256 juntas."};require(h.mode!=ThemeHeaderMode.IMAGE||h.image!=null) {"Falta la imagen del encabezado."}
        h.image?.let {validateImage(it,h.sha256)}
    }
    // Historical previews must still match their exact bytes and declared hash.
    private fun validateImage(image:String,sha:String?) {
        require(image.length<=1_400_000) {"La vista previa codificada supera el límite de imagen."}
        val bytes=Base64.decode(image,Base64.NO_WRAP)
        require(com.r0ybt.arachn0de.visual.canvasImage(bytes)==image) {"La vista previa PNG no está normalizada."}
        require(hash(bytes)==sha) {"La huella SHA-256 de la vista previa no coincide."}
    }
    fun hash(b:ByteArray)=java.security.MessageDigest.getInstance("SHA-256").digest(b).joinToString("") {"%02x".format(it)}
    fun encode(t:CustomTheme):String {
        validate(t);val h=t.header
        return obj("kind" to "arachn0de-theme","schema" to if(t.completionSound!=null)4 else if(t.images.values.any {it.original!=null})3 else if(t.images.isEmpty()&&t.swatch==null)1 else 2,"id" to t.id,"name" to t.name,"description" to t.description,"base" to t.base.name,
            "colors" to JSONObject(t.colors),"paints" to JSONObject().apply {t.paints.forEach {(k,p)->put(k,obj("color" to p.color,"gradient" to p.gradient?.let {g->obj("type" to g.type.name,"angle" to g.angle,"intensity" to g.intensity,"stops" to JSONArray(g.stops.map {obj("position" to it.position,"color" to it.color)}))}))}},
            "header" to obj("mode" to h.mode.name,"image" to h.image,"sha256" to h.sha256,"scale" to h.scale,"x" to h.x,"y" to h.y,"spacing" to h.spacing,"crop" to h.crop)).apply {
                if(t.images.isNotEmpty()||t.swatch!=null||t.completionSound!=null) {
                    put("swatch",t.swatch ?: JSONObject.NULL)
                    put("images",JSONObject().apply {t.images.forEach {(key,image)->put(key,obj("image" to image.image,"sha256" to image.sha256,"zoom" to image.zoom,"x" to image.x,"y" to image.y).apply {image.original?.let {o->put("original",obj("name" to o.name,"sha256" to o.sha256,"byteSize" to o.byteSize,"width" to o.width,"height" to o.height,"mime" to o.mime,"orientation" to o.orientation))}})}})
                }
                t.completionSound?.let {put("completionSound",it)}
            }.toString().also {if(it.toByteArray().size>MAX_BYTES) error("Tema histórico demasiado grande.")}
    }
    fun decode(raw:String):CustomTheme {
        require(raw.toByteArray().size<=MAX_BYTES);structure(raw);val j=JSONObject(raw)
        val schema=integer(j,"schema");require(schema in 1..4)
        fields(j,setOf("kind","schema","id","name","description","base","colors","paints","header")+(if(schema>=2)setOf("images","swatch") else emptySet())+(if(schema>=4)setOf("completionSound")else emptySet()));require(string(j,"kind")=="arachn0de-theme")
        val images=if(schema==1)emptyMap() else j.getJSONObject("images").let {resources->resources.keys().asSequence().associateWith {key->
            val r=resources.getJSONObject(key);fields(r,setOf("image","sha256","zoom","x","y")+if(schema>=3)setOf("original") else emptySet())
            ThemeImage(string(r,"image"),string(r,"sha256"),number(r,"zoom").toFloat(),number(r,"x").toFloat(),number(r,"y").toFloat(),if(!r.has("original"))null else r.getJSONObject("original").let {o->fields(o,setOf("name","sha256","byteSize","width","height","mime","orientation"));val size=number(o,"byteSize");require(size==size.toLong().toDouble());ThemeOriginal(string(o,"name"),string(o,"sha256"),size.toLong(),integer(o,"width"),integer(o,"height"),string(o,"mime"),integer(o,"orientation"))})
        }}
        val swatch=if(schema==1||j.isNull("swatch"))null else string(j,"swatch")
        val colors=j.getJSONObject("colors");val paints=j.getJSONObject("paints");val h=j.getJSONObject("header")
        fields(h,setOf("mode","image","sha256","scale","x","y","spacing","crop"))
        return CustomTheme(string(j,"id"),string(j,"name"),string(j,"description"),AppearanceTheme.valueOf(string(j,"base")),colors.keys().asSequence().associateWith {integer(colors,it)},paints.keys().asSequence().associateWith {k->val p=paints.getJSONObject(k);fields(p,setOf("color","gradient"));ThemePaint(if(p.isNull("color"))null else integer(p,"color"),if(p.isNull("gradient"))null else p.getJSONObject("gradient").let {g->fields(g,setOf("type","angle","intensity","stops"));val stops=g.getJSONArray("stops");require(stops.length() in 2..8);ThemeGradient(ThemeGradientType.valueOf(string(g,"type")),(0 until stops.length()).map {i->val s=stops.getJSONObject(i);fields(s,setOf("position","color"));ThemeStop(number(s,"position").toFloat(),integer(s,"color"))},number(g,"angle").toFloat(),number(g,"intensity").toFloat())})},
            ThemeHeader(ThemeHeaderMode.valueOf(string(h,"mode")),if(h.isNull("image"))null else string(h,"image"),if(h.isNull("sha256"))null else string(h,"sha256"),number(h,"scale").toFloat(),number(h,"x").toFloat(),number(h,"y").toFloat(),number(h,"spacing").toFloat(),bool(h,"crop")),images,swatch,if(schema>=4)string(j,"completionSound")else null).also {validate(it)}
    }
    fun library(raw:String?):List<CustomTheme> {
        if(raw==null)return emptyList();if(raw.toByteArray().size>MAX_LIBRARY_BYTES) error("Biblioteca histórica demasiado grande.")
        structure(raw);val a=JSONArray(raw);require(a.length()<=16) {"La biblioteca admite hasta 16 temas. Exporta o elimina uno antes de añadir otro."}
        return (0 until a.length()).map {decode(a.getJSONObject(it).toString())}.also {themes->require(themes.map {it.id}.distinct().size==themes.size) {"Hay identificadores de tema repetidos."};require(themes.map {it.name.trim().lowercase(java.util.Locale.ROOT)}.distinct().size==themes.size) {"Ya existe otro tema con ese nombre. Elige un nombre diferente."}}
    }
    fun library(themes:List<CustomTheme>):String=JSONArray(themes.map {JSONObject(encode(it))}).toString().also {library(it)}
    fun favorites(raw:String?):List<Int> {if(raw==null)return emptyList();require(raw.length<=1024);structure(raw);val a=JSONArray(raw);require(a.length()<=32);return (0 until a.length()).map {integer(obj("v" to a.get(it)),"v")}.also {require(it.distinct().size==it.size)}}
    fun validatePreferences(v:Map<String,*>) {
        require(LIBRARY !in v||v[LIBRARY] is String);require(ACTIVE !in v||v[ACTIVE] is String);require(FAVORITES !in v||v[FAVORITES] is String)
        val themes=library(v[LIBRARY] as? String);favorites(v[FAVORITES] as? String)
        (v[ACTIVE] as? String)?.let {id->require(themes.any {it.id==id}) {"El tema activo no existe."}}
    }
}
