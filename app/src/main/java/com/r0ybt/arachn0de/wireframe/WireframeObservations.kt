package com.r0ybt.arachn0de.wireframe

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

internal fun wireDrawMarkers(canvas:Canvas,document:WireDocument) {
    val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    document.annotations.forEachIndexed {index,a->
        val point=a.position(document)
        val radius=28f
        val x=(point.x*document.format.width).coerceIn(radius,document.format.width-radius)
        val y=(point.y*document.format.height).coerceIn(radius,document.format.height-radius)
        paint.color=0xff315cbb.toInt();canvas.drawCircle(x,y,radius,paint)
        paint.color=Color.WHITE;paint.textAlign=Paint.Align.CENTER;paint.textSize=28f;paint.typeface=Typeface.DEFAULT_BOLD
        canvas.drawText("${index+1}",x,y-(paint.ascent()+paint.descent())/2,paint)
    }
}
internal typealias WireObservationSlice = com.r0ybt.arachn0de.annotations.ObservationSlice
internal typealias WireObservationPage = com.r0ybt.arachn0de.annotations.ObservationPage
private fun WireDocument.observationDocument() = com.r0ybt.arachn0de.annotations.ObservationDocument(name,annotations.map {com.r0ybt.arachn0de.annotations.Observation(it.title,it.text)})
internal fun wireObservationPages(document:WireDocument,maxHeight:Int=2048) = com.r0ybt.arachn0de.annotations.observationPages(document.observationDocument(),maxHeight)
internal fun wireObservationPng(document:WireDocument,page:WireObservationPage,index:Int,total:Int,output:OutputStream) = com.r0ybt.arachn0de.annotations.observationPng(document.observationDocument(),page,index,total,output)
/** User-owned SAF outputs: never private attachments or the source of a drawing. Create unique documents only. */
internal suspend fun wireExportObserved(context:Context,document:WireDocument,signature:Boolean,tree:Uri):List<Uri> = withContext(Dispatchers.IO) {
    require(document.annotations.isNotEmpty())
    val resolver=context.contentResolver
    val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
    val outputs=mutableListOf<Uri>()
    fun write(name:String,render:(OutputStream)->Unit) {
        val uri=requireNotNull(DocumentsContract.createDocument(resolver,parent,"image/png",name)) {"No se pudo crear $name."};outputs+=uri
        requireNotNull(resolver.openOutputStream(uri,"wt")) {"No se pudo abrir $name."}.use(render)
    }
    try {
        WireframeRenderer(context,document.assets).use {renderer->write("wireframe-marcadores.png") {renderer.png(document,signature,it,markers=true)}}
        val pages=wireObservationPages(document)
        pages.forEachIndexed {index,page->write("wireframe-observaciones-${index+1}.png") {wireObservationPng(document,page,index,pages.size,it)}}
        outputs.toList()
    } catch(e:Exception) {
        // Only documents newly created by this export are candidates for rollback; never scan a folder.
        val remaining=outputs.filter {uri->runCatching {DocumentsContract.deleteDocument(resolver,uri)}.getOrDefault(false).not()}
        if(remaining.isNotEmpty()) throw IllegalStateException("Exportación incompleta. Revisa la carpeta elegida: algunos archivos parciales no pudieron retirarse.",e)
        throw e
    }
}
internal fun wireShareIntent(uris:List<Uri>):Intent = com.r0ybt.arachn0de.annotations.observationShareIntent(uris)
