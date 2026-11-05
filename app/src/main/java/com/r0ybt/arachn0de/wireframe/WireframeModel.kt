package com.r0ybt.arachn0de.wireframe

import android.graphics.BitmapFactory
import android.util.Base64
import com.r0ybt.arachn0de.security.EncryptedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

internal enum class WireFormat(val label:String,val width:Int,val height:Int) { MOBILE("Móvil",1080,2340),DESKTOP("Escritorio",1920,1080) }
internal enum class WireKind(val label:String,val group:String) {
    RECT("Rectángulo","Formas"),SQUARE("Cuadrado","Formas"),ROUND_RECT("Rectángulo redondeado","Formas"),CIRCLE("Círculo","Formas"),LINE("Línea","Formas"),
    BUTTON("Botón rectangular","Botones"),ROUND_BUTTON("Botón redondeado","Botones"),CIRCLE_BUTTON("Botón circular","Botones"),ICON_BUTTON("Botón con icono","Botones"),TEXT_ICON_BUTTON("Botón texto e icono","Botones"),
    INPUT("Campo de texto","Campos"),MULTILINE("Campo multilínea","Campos"),SEARCH("Búsqueda","Campos"),CHECKBOX("Casilla","Campos"),SWITCH("Interruptor","Campos"),SELECT("Desplegable","Campos"),
    TOP_BAR("Barra superior","Barras"),BOTTOM_BAR("Barra inferior","Barras"),SIDE_MENU("Menú lateral","Barras"),NAV_BAR("Navegación","Barras"),TOOL_BAR("Herramientas","Barras"),CONTEXT_MENU("Menú contextual","Barras"),DIALOG("Diálogo","Barras"),
    CARD("Tarjeta","Contenedores"),PANEL("Panel","Contenedores"),SECTION("Sección","Contenedores"),SEPARATOR("Separador","Contenedores"),PLACEHOLDER("Marcador de imagen","Contenedores"),
    TEXT("Texto libre","Texto"),TITLE("Título","Texto"),SUBTITLE("Subtítulo","Texto"),LABEL("Etiqueta","Texto"),PARAGRAPH("Párrafo","Texto"),ICON("Icono","Medios"),IMAGE("Imagen","Medios");
    val textCapable get()=this !in setOf(RECT,SQUARE,ROUND_RECT,CIRCLE,LINE,SEPARATOR,ICON,IMAGE,PLACEHOLDER)
    val textOnly get()=this in setOf(TEXT,TITLE,SUBTITLE,LABEL,PARAGRAPH)
    val iconCapable get()=this in setOf(ICON,ICON_BUTTON,TEXT_ICON_BUTTON,SEARCH)
}
internal data class WireElement(val id:String=UUID.randomUUID().toString(),val kind:WireKind=WireKind.RECT,
    val x:Float=.1f,val y:Float=.1f,val w:Float=.4f,val h:Float=.12f,val text:String="",
    val fill:Long=0xffeeeeee,val stroke:Long=0xff555555,val ink:Long=0xff222222,val border:Float=2f,val radius:Float=16f,val opacity:Float=1f,
    val font:Float=36f,val family:String="sans-serif",val bold:Boolean=false,val italic:Boolean=false,val underline:Boolean=false,val align:String="CENTER",
    val icon:String="menu",val asset:String?=null,val checked:Boolean=false,val imageFit:String="FIT",val lockAspect:Boolean=false,val template:String?=null,
    val contentUnit:Float?=null,val contentW:Float?=null,val contentH:Float?=null,val icons:Map<Int,String> = emptyMap()) {
    fun bounded()=copy(x=x.coerceIn(0f,1f-w.coerceIn(.005f,1f)),y=y.coerceIn(0f,1f-h.coerceIn(.005f,1f)),w=w.coerceIn(.005f,1f),h=h.coerceIn(.005f,1f))
}
internal data class WireDocument(val id:String=UUID.randomUUID().toString(),val name:String="Nuevo prototipo",val format:WireFormat=WireFormat.MOBILE,
    val background:Long=0xffffffff,val elements:List<WireElement> = emptyList(),val assets:Map<String,String> = emptyMap(),
    val created:Long=System.currentTimeMillis(),val modified:Long=created,val annotations:List<WireAnnotation> = emptyList()) {
    fun prune()=copy(assets=assets.filterKeys {id->elements.any {it.asset==id}})
    fun insert(kind:WireKind):WireDocument {
        require(elements.size<128) {"Máximo 128 elementos por prototipo."}
        val size=when(kind) {WireKind.ICON,WireKind.CIRCLE,WireKind.CIRCLE_BUTTON,WireKind.SQUARE->160f to 160f;WireKind.LINE,WireKind.SEPARATOR->600f to 4f;WireKind.TITLE->700f to 120f;else->600f to 180f}
        val element=WireElement(kind=kind,w=(size.first/format.width).coerceAtMost(.8f),h=(size.second/format.height).coerceIn(.005f,.8f),text=if(kind.textCapable) kind.label else "",font=if(kind==WireKind.TITLE) 56f else 36f,
            fill=if(kind.textOnly || kind==WireKind.ICON) 0x00ffffff else 0xffeeeeee,lockAspect=kind in setOf(WireKind.CIRCLE,WireKind.CIRCLE_BUTTON,WireKind.SQUARE))
        return copy(elements=elements+element)
    }
}
internal class WireHistory {
    private val past=ArrayDeque<WireDocument>();private val future=ArrayDeque<WireDocument>()
    val canUndo get()=past.isNotEmpty();val canRedo get()=future.isNotEmpty()
    fun record(before:WireDocument,after:WireDocument):WireDocument {if(before!=after) {past.addLast(before);if(past.size>40) past.removeFirst();future.clear()};return after}
    fun undo(current:WireDocument):WireDocument {if(past.isEmpty()) return current;future.addLast(current);return past.removeLast()}
    fun redo(current:WireDocument):WireDocument {if(future.isEmpty()) return current;past.addLast(current);return future.removeLast()}
}

internal object WireCodec {
    const val NAME="wireframe_library"
    const val MAX_DOCUMENT=3*1024*1024
    // Keep the single SQLCipher preference row below Android's usual 2 MiB CursorWindow.
    const val MAX_LIBRARY=1536*1024
    val icons=setOf("menu","settings","search","trash","plus","image","user","check","star","arrow_left")
    private fun uuid(s:String) {require(UUID.fromString(s).toString()==s)}
    fun image(bytes:ByteArray):String = com.r0ybt.arachn0de.visual.canvasImage(bytes)
    fun validate(d:WireDocument) {
        uuid(d.id);require(d.name.isNotBlank() && d.name.length<=100 && d.created>=0 && d.modified>=d.created)
        require(d.background in 0..0xffffffffL && d.elements.size<=128 && d.assets.size<=16)
        require(d.elements.map {it.id}.distinct().size==d.elements.size)
        require(d.annotations.size<=128 && d.annotations.map {it.id}.distinct().size==d.annotations.size)
        d.annotations.forEach {a->
            uuid(a.id);val range=if(a.elementId==null) 0f..1f else -1f..1f;require(a.x.isFinite() && a.y.isFinite() && a.x in range && a.y in range)
            require(a.title.length<=200 && a.text.length<=16000)
            require(a.elementId==null || d.elements.any {it.id==a.elementId})
        }
        d.elements.forEach {e->
            require(listOf(e.contentUnit,e.contentW,e.contentH).all {it==null} || listOf(e.contentUnit,e.contentW,e.contentH).all {it!=null && it.isFinite() && it>0f && it<=64f})
            require(e.icons.all {(slot,icon)->slot in wireIconDefaults(e).indices && icon in WireIcons})
            uuid(e.id);require(e.template==null || WireTemplates.items.any {it.id==e.template && it.kind==e.kind})
            require(listOf(e.x,e.y,e.w,e.h,e.border,e.radius,e.opacity,e.font).all {it.isFinite()})
            require(e.x>=0 && e.y>=0 && e.w in .005f..1f && e.h in .005f..1f && e.x+e.w<=1.00001f && e.y+e.h<=1.00001f)
            require(e.border in 0f..40f && e.radius in 0f..300f && e.opacity in 0f..1f && e.font in 8f..240f && e.text.length<=4000)
            require(listOf(e.fill,e.stroke,e.ink).all {it in 0..0xffffffffL})
            require(e.align in setOf("LEFT","CENTER","RIGHT") && e.family in setOf("sans-serif","serif","monospace") && e.icon in icons && e.imageFit in setOf("FIT","CROP"))
            require(e.asset==null || e.kind==WireKind.IMAGE && e.asset in d.assets)
            require(e.kind!=WireKind.IMAGE || e.asset!=null)
        }
        require(d.assets.keys==d.elements.mapNotNull {it.asset}.toSet())
        d.assets.forEach {(id,value)->uuid(id);require(value.length<=1_400_000);val bytes=Base64.decode(value,Base64.NO_WRAP);require(image(bytes)==value)}
    }
    private fun obj(vararg fields:Pair<String,Any?>)=JSONObject().apply {fields.forEach {(k,v)->put(k,v ?: JSONObject.NULL)}}
    private fun enhanced(d:WireDocument)=d.annotations.isNotEmpty() || d.elements.any {it.contentUnit!=null || it.icons.isNotEmpty()}
    fun encode(d:WireDocument):String = obj("version" to (if(enhanced(d)) 3 else if(d.elements.any {it.template!=null}) 2 else 1),"id" to d.id,"name" to d.name,"format" to d.format.name,"background" to d.background,"created" to d.created,"modified" to d.modified,
        "elements" to JSONArray(d.elements.map {e->obj("id" to e.id,"kind" to e.kind.name,"x" to e.x,"y" to e.y,"w" to e.w,"h" to e.h,"text" to e.text,"fill" to e.fill,"stroke" to e.stroke,"ink" to e.ink,"border" to e.border,"radius" to e.radius,"opacity" to e.opacity,"font" to e.font,"family" to e.family,"bold" to e.bold,"italic" to e.italic,"underline" to e.underline,"align" to e.align,"icon" to e.icon,"asset" to e.asset,"checked" to e.checked,"imageFit" to e.imageFit,"lockAspect" to e.lockAspect).apply {if(enhanced(d) || d.elements.any {it.template!=null}) put("template",e.template ?: JSONObject.NULL)
            if(enhanced(d)) {put("contentUnit",e.contentUnit ?: JSONObject.NULL);put("contentW",e.contentW ?: JSONObject.NULL);put("contentH",e.contentH ?: JSONObject.NULL);put("icons",JSONObject().apply {e.icons.forEach {(slot,icon)->put(slot.toString(),icon)}})}}}),
        "assets" to JSONObject().apply {d.assets.forEach {(id,data)->put(id,obj("data" to data,"sha256" to sha(Base64.decode(data,Base64.NO_WRAP))))}}).apply {
            if(enhanced(d)) put("annotations",JSONArray(d.annotations.map {a->obj("id" to a.id,"x" to a.x,"y" to a.y,"title" to a.title,"text" to a.text,"elementId" to a.elementId)}))
        }.toString().also {require(it.length<=MAX_DOCUMENT) {"El prototipo supera 3 MiB."}}
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
    private fun JSONObject.fields(vararg names:String) {require(keys().asSequence().toSet()==names.toSet())}
    private fun JSONObject.str(k:String)=get(k).also {require(it is String)} as String
    private fun JSONObject.num(k:String)=get(k).also {require(it is Number)} as Number
    private fun JSONObject.long(k:String):Long {val v=get(k);require(v is Int || v is Long);return (v as Number).toLong()}
    private fun JSONObject.flag(k:String)=get(k).also {require(it is Boolean)} as Boolean
    fun decode(text:String):WireDocument {
        require(text.length<=MAX_DOCUMENT);val j=JSONObject(text)
        val version=j.long("version");require(version in 1L..3L)
        j.fields(*(arrayOf("version","id","name","format","background","created","modified","elements","assets")+if(version==3L) arrayOf("annotations") else emptyArray()))
        val items=j.getJSONArray("elements");require(items.length()<=128)
        val assets=j.getJSONObject("assets");require(assets.length()<=16)
        val d=WireDocument(j.str("id"),j.str("name"),WireFormat.valueOf(j.str("format")),j.long("background"),(0 until items.length()).map {i->
            val e=items.getJSONObject(i)
            val elementFields=arrayOf("id","kind","x","y","w","h","text","fill","stroke","ink","border","radius","opacity","font","family","bold","italic","underline","align","icon","asset","checked","imageFit","lockAspect")
            e.fields(*(elementFields+if(version==3L) arrayOf("template","contentUnit","contentW","contentH","icons") else if(version==2L) arrayOf("template") else emptyArray()))
            WireElement(e.str("id"),WireKind.valueOf(e.str("kind")),e.num("x").toFloat(),e.num("y").toFloat(),e.num("w").toFloat(),e.num("h").toFloat(),e.str("text"),e.long("fill"),e.long("stroke"),e.long("ink"),e.num("border").toFloat(),e.num("radius").toFloat(),e.num("opacity").toFloat(),e.num("font").toFloat(),e.str("family"),e.flag("bold"),e.flag("italic"),e.flag("underline"),e.str("align"),e.str("icon"),if(e.isNull("asset")) null else e.str("asset"),e.flag("checked"),e.str("imageFit"),e.flag("lockAspect"),if(version>=2L && !e.isNull("template")) e.str("template") else null,
                if(version==3L && !e.isNull("contentUnit")) e.num("contentUnit").toFloat() else null,
                if(version==3L && !e.isNull("contentW")) e.num("contentW").toFloat() else null,
                if(version==3L && !e.isNull("contentH")) e.num("contentH").toFloat() else null,
                if(version==3L) e.getJSONObject("icons").let {icons->require(icons.length()<=8);icons.keys().asSequence().associate {key->val slot=key.toInt();require(slot.toString()==key);slot to icons.str(key)}} else emptyMap())
        },assets.keys().asSequence().associateWith {id->val a=assets.getJSONObject(id);a.fields("data","sha256");val data=a.str("data");require(data.length<=1_400_000);require(sha(Base64.decode(data,Base64.NO_WRAP))==a.str("sha256"));data},j.long("created"),j.long("modified"),if(version==3L) j.getJSONArray("annotations").let {rows->
            require(rows.length()<=128);(0 until rows.length()).map {index->val a=rows.getJSONObject(index);a.fields("id","x","y","title","text","elementId");WireAnnotation(a.str("id"),a.num("x").toFloat(),a.num("y").toFloat(),a.str("title"),a.str("text"),if(a.isNull("elementId")) null else a.str("elementId"))}
        } else emptyList())
        validate(d);return d
    }
    fun validatePayload(payload:String) {
        require(payload.length<=MAX_LIBRARY && payload.toByteArray(Charsets.UTF_8).size<=MAX_LIBRARY) {"La biblioteca Wireframe supera 1,5 MiB. El dibujo permanece editable; exporta el PNG o libera dibujos de la biblioteca."}
        val rows=EncryptedPreferences.decode(payload);require(rows.size<=32 && rows.values.sumOf {(it as? String)?.length?.toLong() ?: Long.MAX_VALUE/64}<=MAX_LIBRARY)
        rows.forEach {(id,value)->require(value is String);require(decode(value).id==id)}
    }
}
