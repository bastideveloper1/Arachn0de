package com.r0ybt.arachn0de.annotations

import android.graphics.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.UUID
import java.util.zip.CRC32
import kotlin.math.*

internal data class MarkPoint(val x:Float,val y:Float)
internal enum class MarkTool(val label:String) { PEN("Trazo"),RECT("Rectángulo"),ELLIPSE("Elipse"),LINE("Línea"),ARROW("Flecha"),MARKER("Marcador") }
internal data class ImageMark(val id:String=UUID.randomUUID().toString(),val tool:MarkTool,val points:List<MarkPoint>,val color:Int=0xffe53935.toInt(),val width:Float=.006f,val text:String="") {
    fun moved(dx:Float,dy:Float):ImageMark {
        val x=dx.coerceIn(-points.minOf {it.x},1-points.maxOf {it.x});val y=dy.coerceIn(-points.minOf {it.y},1-points.maxOf {it.y})
        return copy(points=points.map {MarkPoint(it.x+x,it.y+y)})
    }
}
internal object ImageMarkCodec {
    fun encode(marks:List<ImageMark>):String = JSONArray().also {array->marks.forEach {m->array.put(JSONObject().put("id",m.id).put("tool",m.tool.name).put("color",m.color).put("width",m.width).put("text",m.text).put("points",JSONArray().also {p->m.points.forEach {p.put(JSONArray(listOf(it.x,it.y)))}}))}}.toString()
    fun decode(text:String):List<ImageMark> {
        require(text.toByteArray().size<=1024*1024)
        val array=JSONArray(text);require(array.length()<=256)
        val result=(0 until array.length()).map {i->val m=array.getJSONObject(i);val points=m.getJSONArray("points");require(points.length() in 1..4096)
            ImageMark(m.getString("id").also {require(UUID.fromString(it).toString()==it)},MarkTool.valueOf(m.getString("tool")),(0 until points.length()).map {n->val p=points.getJSONArray(n);MarkPoint(p.getDouble(0).toFloat(),p.getDouble(1).toFloat()).also {require(it.x.isFinite()&&it.y.isFinite()&&it.x in 0f..1f&&it.y in 0f..1f)}},m.getInt("color"),m.getDouble("width").toFloat().also {require(it.isFinite()&&it in .001f.. .04f)},m.getString("text").also {require(it.length<=20000)})
        }
        require(result.map {it.id}.distinct().size==result.size)
        require(result.sumOf {it.points.size}<=16000)
        return result
    }
}
/** A private, ancillary PNG chunk. No device paths or external IDs: copies are self contained.
 * Ordinary exports deliberately omit this chunk (and therefore omit the unmarked original).
 */
internal object EditableImagePng {
    private val type="arNd".toByteArray(Charsets.US_ASCII)
    private val signature=byteArrayOf(-119,80,78,71,13,10,26,10)
    data class Content(val original:ByteArray,val marks:List<ImageMark>)
    fun read(bytes:ByteArray):Content? = read(bytes,true)
    private fun read(bytes:ByteArray,allowEditable:Boolean):Content? {
        if(bytes.size<8 || !bytes.copyOfRange(0,8).contentEquals(signature)) return null
        val input=DataInputStream(ByteArrayInputStream(bytes,8,bytes.size-8));var found:Content?=null
        while(input.available()>0) {
            val start=bytes.size-input.available()
            val size=input.readInt();require(size>=0&&size<=input.available()-8)
            val kind=ByteArray(4).also {input.readFully(it)}
            if(kind.contentEquals(type)) {
                require(allowEditable) {"No se permiten originales anidados."}
                require(found==null && size>=8)
                val payload=DataInputStream(ByteArrayInputStream(bytes,start+8,size))
                require(payload.readInt()==1)
                val length=payload.readInt();require(length in 1..20*1024*1024 && length<=payload.available())
                val source=ByteArray(length).also {payload.readFully(it)}
                require(read(source,false)==null)
                val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeByteArray(source,0,source.size,bounds)
                require(bounds.outMimeType in setOf("image/png","image/jpeg")&&bounds.outWidth in 1..32000&&bounds.outHeight in 1..32000)
                var sample=1;while(bounds.outWidth/sample>512 || bounds.outHeight/sample>512) sample*=2
                requireNotNull(BitmapFactory.decodeByteArray(source,0,source.size,BitmapFactory.Options().apply {inSampleSize=sample})).recycle()
                found=Content(source,ImageMarkCodec.decode(payload.readBytes().toString(Charsets.UTF_8)))
            }
            // Skip directly over image data: don't allocate an extra 20 MiB for IDAT or arNd.
            require(input.skipBytes(size)==size)
            val crc=input.readInt()
            if(kind.contentEquals(type)) require(CRC32().apply {update(bytes,start+4,size+4)}.value.toInt()==crc)
            if(kind.contentEquals("IEND".toByteArray())) {if(found!=null) require(input.available()==0);return found}
        }
        require(found==null) {"PNG incompleto."};return null
    }
    /** Keep the historical public-copy entry point on the shared metadata/editing policy. */
    fun publicBytes(bytes:ByteArray):ByteArray = com.r0ybt.arachn0de.photos.PublicImages.prepare(bytes)
    fun write(png:ByteArray,original:ByteArray,marks:List<ImageMark>):ByteArray {
        require(read(png)==null&&read(original)==null)
        val encoded=ImageMarkCodec.encode(marks);ImageMarkCodec.decode(encoded)
        val text=encoded.toByteArray()
        val payloadSize=8L+original.size+text.size
        require(png.size+payloadSize+12<=20L*1024*1024) {"La copia editable supera 20 MiB; exporta un PNG o reduce la imagen original."}
        require(png.size>=12&&png.copyOfRange(png.size-8,png.size-4).contentEquals("IEND".toByteArray()))
        val result=ByteArrayOutputStream((png.size+payloadSize+12).toInt());result.write(png,0,png.size-12)
        val header=ByteArrayOutputStream().also {DataOutputStream(it).apply {writeInt(1);writeInt(original.size)}}.toByteArray()
        val crc=CRC32().apply {update(type);update(header);update(original);update(text)}
        DataOutputStream(result).apply {writeInt(payloadSize.toInt());write(type);write(header);write(original);write(text);writeInt(crc.value.toInt())}
        result.write(png,png.size-12,12)
        return result.toByteArray().also {require(it.size<=20*1024*1024) {"La copia editable supera 20 MiB; exporta un PNG o reduce la imagen original."}}
    }
}
internal fun drawImageMarks(canvas:Canvas,width:Int,height:Int,marks:List<ImageMark>,selected:String?=null) {
    val unit=min(width,height).toFloat();val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    var number=0
    marks.forEach {m->
        val p=m.points.map {PointF(it.x*width,it.y*height)};val a=p.first();val b=p.last()
        paint.color=m.color;paint.strokeWidth=m.width*unit;paint.style=Paint.Style.STROKE
        val rect=RectF(min(a.x,b.x),min(a.y,b.y),max(a.x,b.x),max(a.y,b.y))
        when(m.tool) {
            MarkTool.PEN-> {val path=Path();path.moveTo(a.x,a.y);p.drop(1).forEach {path.lineTo(it.x,it.y)};if(p.size==1) canvas.drawPoint(a.x,a.y,paint) else canvas.drawPath(path,paint)}
            MarkTool.RECT->canvas.drawRect(rect,paint)
            MarkTool.ELLIPSE->canvas.drawOval(rect,paint)
            MarkTool.LINE,MarkTool.ARROW-> {canvas.drawLine(a.x,a.y,b.x,b.y,paint);if(m.tool==MarkTool.ARROW) {val angle=atan2(b.y-a.y,b.x-a.x);val length=max(unit*.025f,paint.strokeWidth*4);for(sign in listOf(-1,1)) {val t=angle+sign*.55f;canvas.drawLine(b.x,b.y,b.x-cos(t)*length,b.y-sin(t)*length,paint)}}}
            MarkTool.MARKER-> {
                number++
                val r=unit*.028f
                val edge=r+unit*.004f
                val x=a.x.coerceIn(edge,width-edge)
                val y=a.y.coerceIn(edge,height-edge)
                paint.style=Paint.Style.FILL;paint.color=Color.WHITE
                canvas.drawCircle(x,y,edge,paint)
                paint.color=0xff163d88.toInt();canvas.drawCircle(x,y,r,paint)
                paint.color=Color.WHITE;paint.textSize=r*(if(number>=100) .9f else 1.25f)
                paint.typeface=Typeface.DEFAULT_BOLD;paint.textAlign=Paint.Align.CENTER
                canvas.drawText("$number",x,y-(paint.ascent()+paint.descent())/2,paint)
            }
        }
        if(m.id==selected) {paint.style=Paint.Style.STROKE;paint.strokeWidth=max(unit*.002f,1f);paint.color=Color.CYAN;canvas.drawRect(RectF(p.minOf {it.x}-8,p.minOf {it.y}-8,p.maxOf {it.x}+8,p.maxOf {it.y}+8),paint)}
    }
}
internal fun markedPng(source:Bitmap,marks:List<ImageMark>):ByteArray {
    val bitmap=source.copy(Bitmap.Config.ARGB_8888,true)
    try {drawImageMarks(Canvas(bitmap),bitmap.width,bitmap.height,marks);return ByteArrayOutputStream().also {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}.toByteArray()} finally {bitmap.recycle()}
}
/** One entry per completed gesture. Consecutive edits of the same observation coalesce. */
internal class MarkHistory(initial:List<ImageMark>) {
    private val delegate=com.r0ybt.arachn0de.visual.EditHistory(initial) {next->ImageMarkCodec.decode(ImageMarkCodec.encode(next));Unit}
    val current get()=delegate.current
    val canUndo get()=delegate.canUndo
    val canRedo get()=delegate.canRedo
    fun commit(next:List<ImageMark>,key:String?=null)=delegate.commit(next,key)
    fun undo()=delegate.undo()
    fun redo()=delegate.redo()
}
