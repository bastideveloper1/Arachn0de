package com.r0ybt.arachn0de.photos

import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.CRC32

/** Photos only: allow-listed structural data, never copy opaque metadata blocks. */
internal object PrivatePhoto {
    const val LIMIT=20*1024*1024
    data class Clean(val bytes:ByteArray,val mime:String,val width:Int,val height:Int)
    fun read(input:InputStream):ByteArray {
        val out=ByteArrayOutputStream();val buffer=ByteArray(32768)
        while(true) {val n=input.read(buffer);if(n<0)break;require(out.size().toLong()+n<=LIMIT) {"La fotografía supera 20 MiB."};out.write(buffer,0,n)}
        return out.toByteArray()
    }
    fun clean(source:ByteArray):Clean {
        require(source.size in 1..LIMIT)
        val jpeg=source.size>2 && source[0]==(-1).toByte() && source[1]==(-40).toByte()
        val header=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(source,0,source.size,header)
        val srgb=android.os.Build.VERSION.SDK_INT>=26 && header.outColorSpace?.isSrgb==true
        val bytes=try {if(jpeg) jpeg(source,srgb) else png(source,srgb)} catch(e:ConversionRequired) {convert(source)}
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth in 1..32000 && bounds.outHeight in 1..32000 && bounds.outWidth.toLong()*bounds.outHeight<=100_000_000) {"Dimensiones de fotografía no admitidas."}
        var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1024)sample*=2
        requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inSampleSize=sample})){"Fotografía dañada."}.recycle()
        // The allow-listed writers above produce only JPEG or PNG (conversion produces PNG).
        val mime=if(bytes[0]==(-1).toByte() && bytes[1]==(-40).toByte()) "image/jpeg" else "image/png"
        return Clean(bytes,mime,bounds.outWidth,bounds.outHeight)
    }
    internal fun sourceOrientation(b:ByteArray):Int {
        fun u16(at:Int,little:Boolean):Int {
            require(at>=0 && at+2<=b.size)
            return if(little) (b[at].toInt() and 255) or ((b[at+1].toInt() and 255) shl 8)
            else ((b[at].toInt() and 255) shl 8) or (b[at+1].toInt() and 255)
        }
        fun u32(at:Int,little:Boolean):Int {val a=u16(at,little);val c=u16(at+2,little);return if(little) a or (c shl 16) else (a shl 16) or c}
        fun tiff(start:Int,n:Int):Int {
            require(n>=8 && start>=0 && n<=b.size-start)
            val little=b[start]==73.toByte()
            require((little && b[start+1]==73.toByte()) || b[start]==77.toByte() && b[start+1]==77.toByte())
            require(u16(start+2,little)==42)
            val offset=u32(start+4,little);require(offset>=8 && offset<=n-2)
            val directory=start+offset;val count=u16(directory,little)
            require(count<=(start+n-directory-2)/12)
            repeat(count) {index->val at=directory+2+index*12
                if(u16(at,little)==0x112) {
                    require(u16(at+2,little)==3 && u32(at+4,little)==1)
                    return u16(at+8,little).let {if(it==0) 1 else it.also {o->require(o in 1..8)}}
                }
            }
            return 1
        }
        if(b.size>8 && b[0]==(-119).toByte()) {
            var i=8
            while(i+12<=b.size) {
                val n=u32(i,false);require(n>=0 && n<=b.size-i-12)
                if(String(b,i+4,4,Charsets.US_ASCII)=="eXIf") return tiff(i+8,n)
                i+=n+12
            }
        } else if(b.size>=12 && String(b,0,4,Charsets.US_ASCII)=="RIFF" && String(b,8,4,Charsets.US_ASCII)=="WEBP") {
            var i=12
            while(i+8<=b.size) {
                val n=u32(i+4,true);require(n>=0 && n<=b.size-i-8)
                if(String(b,i,4,Charsets.US_ASCII)=="EXIF") {
                    val prefix=n>=6 && b.copyOfRange(i+8,i+14).contentEquals(byteArrayOf(69,120,105,102,0,0))
                    return tiff(i+8+if(prefix)6 else 0,n-if(prefix)6 else 0)
                }
                i+=8+n+(n and 1)
            }
        } else if(b.size>2 && b[0]==(-1).toByte() && b[1]==(-40).toByte()) {
            var i=2
            while(i+4<=b.size) {
                require(b[i++]==(-1).toByte());while(i<b.size && b[i]==(-1).toByte())i++
                require(i<b.size);val marker=b[i++].toInt() and 255
                if(marker==0xDA || marker==0xD9)break
                val n=u16(i,false);require(n>=2 && n<=b.size-i)
                if(marker==0xE1 && n>=8 && b.copyOfRange(i+2,i+8).contentEquals(byteArrayOf(69,120,105,102,0,0)))return tiff(i+8,n-8)
                i+=n
            }
        }
        return 1
    }
    private class ConversionRequired:RuntimeException()
    /** ICC images require a color conversion; PNG compression is lossless, with no resize. */
    private fun convert(source:ByteArray):ByteArray {
        require(android.os.Build.VERSION.SDK_INT>=26) {"Este Android no admite la conversión de color segura de esta imagen."}
        val b=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(source,0,source.size,b)
        val pixels=b.outWidth.toLong()*b.outHeight
        require(pixels in 1..16_000_000 && pixels*12<Runtime.getRuntime().maxMemory()/2) {"La conversión privada de esta imagen excede el límite de memoria. Usa JPEG/PNG sin perfil ICC."}
        val options=BitmapFactory.Options().apply {inPreferredColorSpace=android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)}
        val bitmap=requireNotNull(BitmapFactory.decodeByteArray(source,0,source.size,options))
        var oriented=bitmap
        try {
            val orientation=sourceOrientation(source)
            oriented=orient(bitmap,orientation)
            val out=object:ByteArrayOutputStream() {
                override fun write(b:ByteArray,off:Int,len:Int) {require(count.toLong()+len<=LIMIT) {"La fotografía limpia supera 20 MiB."};super.write(b,off,len)}
            }
            require(oriented.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out))
            return png(out.toByteArray(),true)
        } finally {if(oriented!==bitmap)oriented.recycle();bitmap.recycle()}
    }
    fun orient(bitmap:android.graphics.Bitmap,orientation:Int):android.graphics.Bitmap {
        val m=android.graphics.Matrix()
        when(orientation) {
            2->m.setScale(-1f,1f);3->m.setRotate(180f);4->m.setScale(1f,-1f)
            5->{m.setRotate(90f);m.postScale(-1f,1f)};6->m.setRotate(90f)
            7->{m.setRotate(-90f);m.postScale(-1f,1f)};8->m.setRotate(-90f)
        }
        return if(m.isIdentity) bitmap else android.graphics.Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,m,true)
    }
    /** Structural validation for the public policy. Color conversion belongs to its destination. */
    internal fun publicJpegStructure(source:ByteArray):ByteArray=jpeg(source,true)
    private fun jpeg(b:ByteArray,srgb:Boolean):ByteArray {
        val orientation=sourceOrientation(b)
        val out=ByteArrayOutputStream();out.write(b,0,2)
        // The only EXIF retained is a freshly constructed orientation tag: no source directory, thumbnail or identifier.
        if(orientation in 2..8) {
            val exif=byteArrayOf(69,120,105,102,0,0,77,77,0,42,0,0,0,8,0,1,1,18,0,3,0,0,0,1,0,orientation.toByte(),0,0,0,0,0,0)
            out.write(255);out.write(225);out.write(0);out.write(exif.size+2);out.write(exif)
        }
        var i=2;var scan=false;var ended=false
        while(i<b.size) {
            if(scan) {
                val start=i
                while(i<b.size) {
                    if(b[i].toInt() and 255!=255) {i++;continue}
                    var j=i+1;while(j<b.size && b[j].toInt() and 255==255)j++
                    require(j<b.size)
                    val marker=b[j].toInt() and 255
                    if(marker==0 || marker in 0xD0..0xD7) {i=j+1;continue}
                    break
                }
                out.write(b,start,i-start);scan=false
            }
            require(i<b.size && b[i++].toInt() and 255==255) {"JPEG inválido."}
            while(i<b.size && b[i].toInt() and 255==255)i++
            require(i<b.size);val marker=b[i++].toInt() and 255
            if(marker==0xD9) {out.write(255);out.write(marker);ended=true;break}
            require(marker in setOf(0xC0,0xC1,0xC2,0xC3,0xC4,0xC5,0xC6,0xC7,0xC9,0xCA,0xCB,0xCC,0xCD,0xCE,0xCF,0xDA,0xDB,0xDC,0xDD) || marker in 0xE0..0xEF || marker==0xFE) {"JPEG no admitido."}
            require(i+2<=b.size);val length=((b[i].toInt() and 255) shl 8)+(b[i+1].toInt() and 255)
            require(length>=2 && length<=b.size-i)
            val metadata=marker in 0xE0..0xEF || marker==0xFE
            // Adobe color-transform segment is structural, fixed-length, and has no variable strings.
            val adobe=marker==0xEE && length==14 && b.copyOfRange(i+2,i+7).contentEquals("Adobe".toByteArray())
            if(!srgb && marker==0xE2 && length>=14 && b.copyOfRange(i+2,i+14).contentEquals("ICC_PROFILE\u0000".toByteArray())) throw ConversionRequired()
            if(adobe) {
                val transform=b[i+13].toInt() and 255;require(transform in 0..2)
                out.write(byteArrayOf(-1,-18,0,14,65,100,111,98,101,0,100,0,0,0,0,transform.toByte()))
            } else if(!metadata) {out.write(255);out.write(marker);out.write(b,i,length)}
            i+=length;scan=marker==0xDA
        }
        require(ended) {"JPEG incompleto."}
        return out.toByteArray()
    }
    private fun png(b:ByteArray,srgb:Boolean=false):ByteArray {
        val signature=byteArrayOf(-119,80,78,71,13,10,26,10)
        require(b.size>=8 && b.copyOfRange(0,8).contentEquals(signature)) {"Solo se admiten fotografías JPEG o PNG."}
        val out=ByteArrayOutputStream();out.write(signature)
        var i=8;var ended=false;var first=true
        fun int(at:Int)=((b[at].toInt() and 255) shl 24) or ((b[at+1].toInt() and 255) shl 16) or ((b[at+2].toInt() and 255) shl 8) or (b[at+3].toInt() and 255)
        while(i<b.size) {
            require(b.size-i>=12);val n=int(i);require(n>=0 && n<=b.size-i-12)
            val type=String(b,i+4,4,Charsets.US_ASCII)
            require(!first || type=="IHDR" && n==13);first=false
            val crc=CRC32().apply {update(b,i+4,n+4)}.value
            require(int(i+8+n).toLong() and 0xffffffffL==crc) {"PNG dañado."}
            require(type!="acTL") {"Selecciona una fotografía estática."}
            if(type=="iCCP" && !srgb || type=="eXIf" && sourceOrientation(b)!=1) throw ConversionRequired()
            val allowed=type in setOf("IHDR","PLTE","IDAT","tRNS","IEND","sRGB","gAMA","cHRM")
            require(allowed || type[0].isLowerCase()) {"Bloque PNG desconocido."}
            if(type=="sRGB")require(n==1 && b[i+8].toInt() in 0..3)
            if(type=="gAMA")require(n==4)
            if(type=="cHRM")require(n==32)
            if(allowed)out.write(b,i,n+12)
            i+=n+12
            if(type=="IEND") {require(n==0);ended=true;break}
        }
        require(ended) {"PNG incompleto."}
        return out.toByteArray()
    }
}
