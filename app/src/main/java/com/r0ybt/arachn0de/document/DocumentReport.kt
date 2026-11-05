package com.r0ybt.arachn0de.document

import android.graphics.*
import com.r0ybt.arachn0de.domain.model.DescriptionParser
import java.time.LocalDate

internal enum class ReportFormat(val label:String,val extension:String,val mime:String) {
    PDF("PDF","pdf","application/pdf"), DOCX("Word","docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document")
}
internal enum class ReportImageLayout(val label:String) { AUTO("Automática"),LEFT("Izquierda"),RIGHT("Derecha"),FULL("Ancho completo") }
internal data class ReportOptions(val title:String,val subtitle:String="",val date:String=LocalDate.now().toString(),val index:Boolean=true,val cover:Boolean=false,val format:ReportFormat=ReportFormat.PDF,val images:ReportImageLayout=ReportImageLayout.AUTO) {
    fun validate() {require(title.isNotBlank()&&title.length<=500);require(subtitle.length<=1000);LocalDate.parse(date)}
}
internal sealed interface ReportPart {
    data class Text(val text:String,val formats:Set<DescriptionParser.Format> = emptySet()):ReportPart
    data class Image(val id:String,val label:String):ReportPart
}
internal data class ReportImage(val bytes:ByteArray,val width:Int,val height:Int)
internal data class ReportSection(val title:String,val depth:Int,val number:String,val metadata:List<String>,val parts:List<ReportPart>)
internal data class ReportContent(val sections:List<ReportSection>,val images:Map<String,ReportImage>) {
    fun release() {images.values.forEach {it.bytes.fill(0)}}
}
internal fun reportParts(description:String,imageIds:Set<String>,audioIds:Set<String>):List<ReportPart> = DescriptionParser.parse(description).mapNotNull {p->
    when {p.audio||p.imageId in audioIds->null
        p.imageId!=null->{require(p.imageId in imageIds) {"Falta una imagen del contenido. No se exportará un informe incompleto."};ReportPart.Image(p.imageId,p.label ?: "")}
        p.text.isNotEmpty()->ReportPart.Text(p.text,p.formats)
        else->null}
}
/** Normalize a copy, including EXIF orientation, without changing the encrypted original. */
internal fun reportImage(bytes:ByteArray):ReportImage {
    val image=com.r0ybt.arachn0de.photos.PublicImages.render(bytes)
    try {
        val output=java.io.ByteArrayOutputStream();check(image.compress(Bitmap.CompressFormat.PNG,100,output))
        return ReportImage(output.toByteArray(),image.width,image.height)
    } finally {image.recycle()}
}
