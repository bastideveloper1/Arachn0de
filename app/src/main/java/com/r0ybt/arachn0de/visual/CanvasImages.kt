package com.r0ybt.arachn0de.visual
import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.*
import android.util.Base64
    fun canvasImage(bytes:ByteArray):String {
        require(bytes.size in 8..1024*1024) {"La imagen normalizada supera 1 MiB."}
        com.r0ybt.arachn0de.backup.validateAvatar(bytes)
        require(bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))) {"Se requiere una imagen PNG normalizada."}
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth in 1..1024 && bounds.outHeight in 1..1024) {"La vista previa PNG debe medir entre 1 y 1024 píxeles por lado."}
        val decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: error("Imagen ilegible")
        try {require(decoded.width in 1..1024 && decoded.height in 1..1024) {"La imagen PNG decodificada supera 1024 píxeles por lado."}} finally {decoded.recycle()}
        return Base64.encodeToString(bytes,Base64.NO_WRAP)
    }
/** Import directly into an encrypted document, with bounded decode and no private temporary file. */
internal suspend fun importCanvasImage(context:Context,uri:Uri,maxDimension:Int=1024,maxBytes:Int=1024*1024):String = withContext(Dispatchers.IO) {
    require(maxDimension in 64..1024&&maxBytes in 8192..1024*1024)
    val bytes=context.contentResolver.openInputStream(uri)!!.use {input->
        val out=java.io.ByteArrayOutputStream();val block=ByteArray(8192)
        while(true) {val n=input.read(block);if(n<0) break;require(out.size()+n<=20*1024*1024) {"Máximo 20 MiB por imagen de origen."};out.write(block,0,n)};out.toByteArray()
    }
    val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
    BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
    require(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outWidth.toLong()*bounds.outHeight<=100_000_000L) {"Imagen no válida o demasiado grande."}
    var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxDimension) sample*=2
    val bitmap=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inSampleSize=sample}))
    var current=bitmap
    try {
        while(true) {
            ensureActive()
            val output=java.io.ByteArrayOutputStream();check(current.compress(Bitmap.CompressFormat.PNG,100,output))
            if(output.size()<=maxBytes)return@withContext canvasImage(output.toByteArray())
            // Existing callers keep their exact former rejection; theme backgrounds can request a smaller copy.
            require(maxBytes<1024*1024&&maxOf(current.width,current.height)>32) {"La imagen normalizada supera 1 MiB."}
            val next=Bitmap.createScaledBitmap(current,(current.width*.8f).toInt().coerceAtLeast(1),(current.height*.8f).toInt().coerceAtLeast(1),true)
            if(current!==bitmap)current.recycle();current=next
        }
        @Suppress("UNREACHABLE_CODE") error("Imagen no disponible")
    } finally {if(current!==bitmap)current.recycle();bitmap.recycle()}
}
