package com.r0ybt.arachn0de.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.r0ybt.arachn0de.media.ContentGif
import java.io.ByteArrayOutputStream

/** One public policy: a full file preserves animation; a document explicitly requests rendered pixels.
 * No destination ever receives a private original as an error fallback. */
internal object PublicImages {
    data class Prepared(val bytes:ByteArray,val mime:String,val width:Int,val height:Int,val frames:Int=1,val animated:Boolean=false,val orientation:Int=1)
    fun prepare(source:ByteArray):ByteArray=file(source).bytes
    fun file(source:ByteArray,displayOrientation:Int?=null):Prepared=guard {
        require(source.size in 1..PrivatePhoto.LIMIT && (displayOrientation==null || displayOrientation in 1..8))
        when {
            PublicPng.matches(source)->{
                val plan=PublicPng.inspect(source,displayOrientation);val i=plan.info
                if(i.icc && !isSrgb(source)) {
                    require(!i.animated) {"No se puede convertir de forma segura el perfil ICC de esta animación."}
                    converted(source,"image/png",i.orientation)
                } else Prepared(plan.bytes(),"image/png",i.width,i.height,i.frames,i.animated,i.orientation)
            }
            PublicWebp.matches(source)->{
                val plan=PublicWebp.inspect(source,displayOrientation=displayOrientation);val i=plan.info
                if(i.icc && !isSrgb(source)) {
                    require(!i.animated) {"No se puede convertir de forma segura el perfil ICC de esta animación."}
                    converted(source,"image/png",i.orientation)
                } else Prepared(plan.bytes(),"image/webp",i.width,i.height,i.frames,i.animated,i.orientation)
            }
            isGif(source)->{
                val info=ContentGif.inspect(source)
                Prepared(ContentGif.publicBytes(source),"image/gif",info.width,info.height,info.frames,info.frames>1)
            }
            isJpeg(source)->{
                val structure=PrivatePhoto.publicJpegStructure(source)
                val bounds=bounds(source);val orientation=PrivatePhoto.sourceOrientation(source)
                if(jpegIcc(source) && !isSrgb(source)) {
                    structure.fill(0);converted(source,"image/jpeg",orientation)
                } else {
                    sampled(source,512,orientation=1,color=false).recycle()
                    Prepared(structure,"image/jpeg",bounds.outWidth,bounds.outHeight,orientation=orientation)
                }
            }
            else->error("Formato de imagen no admitido para publicación.")
        }
    }
    /** Decode original visible pixels once at the requested document size. The encoder sees only
     * this bitmap, never source metadata or editable chunks. ICC does not make a JPEG round-trip. */
    fun render(source:ByteArray,maxSide:Int=1600):Bitmap=guard {
        require(source.size in 1..PrivatePhoto.LIMIT && maxSide in 1..2048)
        val orientation:Int;val icc:Boolean
        when {
            PublicPng.matches(source)->{val i=PublicPng.inspect(source).info;orientation=i.orientation;icc=i.icc}
            PublicWebp.matches(source)->{val i=PublicWebp.inspect(source,false).info;orientation=i.orientation;icc=i.icc}
            isGif(source)->{ContentGif.publicBytes(source).fill(0);orientation=1;icc=false}
            isJpeg(source)->{PrivatePhoto.publicJpegStructure(source).fill(0);orientation=PrivatePhoto.sourceOrientation(source);icc=jpegIcc(source)}
            else->error("Formato de imagen no admitido para publicación.")
        }
        require(!icc || android.os.Build.VERSION.SDK_INT>=26 || isSrgb(source)) {"Este Android no admite la conversión de color segura de esta imagen."}
        sampled(source,maxSide,orientation,true)
    }
    private fun bounds(b:ByteArray)=BitmapFactory.Options().apply {
        inJustDecodeBounds=true;BitmapFactory.decodeByteArray(b,0,b.size,this)
        require(outWidth in 1..32000 && outHeight in 1..32000) {"Dimensiones no admitidas."}
    }
    private fun isSrgb(b:ByteArray)=android.os.Build.VERSION.SDK_INT>=26 && bounds(b).outColorSpace?.isSrgb==true
    private fun sampled(b:ByteArray,maxSide:Int,orientation:Int,color:Boolean):Bitmap {
        val bounds=bounds(b);var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxSide)sample*=2
        val options=BitmapFactory.Options().apply {
            inSampleSize=sample
            if(color && android.os.Build.VERSION.SDK_INT>=26)inPreferredColorSpace=android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
        }
        val decoded=requireNotNull(BitmapFactory.decodeByteArray(b,0,b.size,options)) {"Imagen corrupta."}
        return try {PrivatePhoto.orient(decoded,orientation).also {if(it!==decoded)decoded.recycle()}} catch(t:Throwable) {decoded.recycle();throw t}
    }
    private fun converted(b:ByteArray,mime:String,orientation:Int):Prepared {
        require(android.os.Build.VERSION.SDK_INT>=26) {"Este Android no admite la conversión de color segura de esta imagen."}
        val bounds=bounds(b);val pixels=bounds.outWidth.toLong()*bounds.outHeight
        require(pixels*8+40L*1024*1024<=Runtime.getRuntime().maxMemory()/2) {"La conversión a tamaño completo excede el presupuesto de memoria. Usa la representación de documento."}
        val options=BitmapFactory.Options().apply {inPreferredColorSpace=android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)}
        val decoded=requireNotNull(BitmapFactory.decodeByteArray(b,0,b.size,options));var image=decoded
        try {
            image=PrivatePhoto.orient(decoded,orientation)
            val output=object:ByteArrayOutputStream() {
                override fun write(b:ByteArray,off:Int,len:Int) {require(count.toLong()+len<=PrivatePhoto.LIMIT);super.write(b,off,len)}
            }
            check(image.compress(if(mime=="image/jpeg")Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG,95,output))
            val encoded=output.toByteArray()
            val bytes=try {if(mime=="image/jpeg")PrivatePhoto.publicJpegStructure(encoded) else PublicPng.inspect(encoded).bytes()} finally {encoded.fill(0)}
            return Prepared(bytes,mime,image.width,image.height)
        } finally {if(image!==decoded)image.recycle();decoded.recycle()}
    }
    private fun isJpeg(b:ByteArray)=b.size>=2 && b[0]==(-1).toByte() && b[1]==(-40).toByte()
    private fun isGif(b:ByteArray)=b.size>=6 && String(b,0,3,Charsets.US_ASCII)=="GIF"
    private fun jpegIcc(b:ByteArray):Boolean {
        var at=2
        while(at+4<=b.size) {
            require(b[at++]==(-1).toByte());while(at<b.size && b[at]==(-1).toByte())at++
            val marker=b[at++].toInt() and 255;if(marker==0xda || marker==0xd9)return false
            val n=((b[at].toInt() and 255) shl 8)+(b[at+1].toInt() and 255);require(n>=2 && n<=b.size-at)
            if(marker==0xe2 && n>=14 && String(b,at+2,12,Charsets.US_ASCII)=="ICC_PROFILE\u0000")return true
            at+=n
        };return false
    }
    private inline fun <T> guard(work:()->T):T=try {work()} catch(f:OutOfMemoryError) {throw IllegalArgumentException("No hay memoria suficiente para preparar la copia pública. El original se conserva.",f)}
}
