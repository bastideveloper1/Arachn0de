package com.r0ybt.arachn0de.annotations

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.DocumentsContract
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class Observation(val title:String="",val text:String="")
internal data class ObservationDocument(val name:String,val observations:List<Observation>)
internal data class ObservationSlice(val number:Int,val content:String,val layout:StaticLayout,val firstLine:Int,val lastLine:Int,val y:Int) {
    val text get()=content.substring(layout.getLineStart(firstLine),layout.getLineEnd(lastLine-1))
    val height get()=layout.getLineBottom(lastLine-1)-layout.getLineTop(firstLine)
}
internal data class ObservationPage(val slices:List<ObservationSlice>,val height:Int)
/** Pagination uses measured line boundaries. No truncation, ellipsis or maximum line count. */
internal fun observationPages(document:ObservationDocument,maxHeight:Int=2048):List<ObservationPage> {
    require(maxHeight in 512..4096)
    val pages=mutableListOf<ObservationPage>();var slices=mutableListOf<ObservationSlice>();var y=160
    fun flush() {if(slices.isNotEmpty()) {pages+=ObservationPage(slices.toList(),(y+70).coerceIn(320,maxHeight));slices=mutableListOf();y=160}}
    fun block(number:Int,content:String,heading:Boolean) {
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.DKGRAY;textSize=if(heading) 38f else 32f;typeface=if(heading) Typeface.DEFAULT_BOLD else Typeface.DEFAULT}
        val layout=StaticLayout.Builder.obtain(content,0,content.length,paint,1112).setIncludePad(false).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(8f,1f).build()
        if(heading && y>160 && y+layout.height+170>maxHeight) flush()
        var line=0
        while(line<layout.lineCount) {
            var end=line
            while(end<layout.lineCount && y+layout.getLineBottom(end)-layout.getLineTop(line)+70<=maxHeight) end++
            if(end==line) {flush();continue}
            val slice=ObservationSlice(number,content,layout,line,end,y);slices+=slice;y+=slice.height;line=end
            if(line<layout.lineCount) flush()
        }
        y+=24
    }
    document.observations.forEachIndexed {index,a->
        block(index+1,"${index+1}. ${a.title.ifBlank {"Observación"}}",true)
        if(a.text.isNotEmpty()) block(index+1,a.text,false)
        y+=12
    }
    flush();return pages
}
internal fun observationPng(document:ObservationDocument,page:ObservationPage,index:Int,total:Int,output:OutputStream) {
    val bitmap=Bitmap.createBitmap(1240,page.height,Bitmap.Config.ARGB_8888)
    try {
        val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.DKGRAY;textSize=30f;typeface=Typeface.DEFAULT_BOLD}
        // The document name is metadata; observations themselves are always laid out in full below.
        canvas.drawText("Observaciones",64f,58f,paint)
        paint.textSize=24f;paint.typeface=Typeface.DEFAULT
        val label=android.text.TextUtils.ellipsize(document.name,TextPaint(paint),1112f,android.text.TextUtils.TruncateAt.END).toString()
        canvas.drawText(label,64f,100f,paint)
        paint.textSize=20f
        val first=page.slices.first().number;val last=page.slices.last().number
        canvas.drawText(if(first==last) "Observación $first" else "Observaciones $first–$last",64f,134f,paint)
        page.slices.forEach {slice->canvas.save();canvas.clipRect(64, slice.y,1176,slice.y+slice.height);canvas.translate(64f,(slice.y-slice.layout.getLineTop(slice.firstLine)).toFloat());slice.layout.draw(canvas);canvas.restore()}
        canvas.drawText("Arachn0de · ${index+1} / $total",64f,page.height-24f,paint)
        check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output)) {"No se pudo exportar la hoja de observaciones."}
    } finally {bitmap.recycle()}
}
internal fun observationShareIntent(uris:List<Uri>):Intent {
    require(uris.isNotEmpty())
    return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type="image/png";putParcelableArrayListExtra(Intent.EXTRA_STREAM,ArrayList(uris))
        clipData=ClipData.newRawUri("Imágenes anotadas",uris.first()).apply {uris.drop(1).forEach {addItem(ClipData.Item(it))}}
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
