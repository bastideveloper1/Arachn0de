package com.r0ybt.arachn0de.photos

import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/** RIFF allow-list; encoded frames, blending, disposal, durations and loop count are preserved. */
internal object PublicWebp {
    private data class Chunk(val type:String,val at:Int,val size:Int)
    data class Info(val width:Int,val height:Int,val frames:Int,val orientation:Int,val icc:Boolean,val animated:Boolean)
    class Plan internal constructor(val info:Info,private val write:()->ByteArray) {fun bytes()=write()}
    fun matches(b:ByteArray)=b.size>=12 && String(b,0,4,Charsets.US_ASCII)=="RIFF" && String(b,8,4,Charsets.US_ASCII)=="WEBP"
    fun inspect(b:ByteArray,validatePixels:Boolean=true,displayOrientation:Int?=null):Plan {
        require(matches(b) && b.size<=PrivatePhoto.LIMIT)
        fun byte(p:Int):Int {require(p in b.indices);return b[p].toInt() and 255}
        fun u24(p:Int)=byte(p) or (byte(p+1) shl 8) or (byte(p+2) shl 16)
        fun u32(p:Int)=u24(p).toLong() or (byte(p+3).toLong() shl 24)
        require(u32(4)==b.size-8L) {"Longitud RIFF incorrecta."}
        fun chunks(start:Int,end:Int):List<Chunk> {
            val result=mutableListOf<Chunk>();var at=start
            while(at<end) {require(end-at>=8);val n=u32(at+4);require(n<=end-at-8L)
                val type=String(b,at,4,Charsets.US_ASCII);require(type.all {it.code in 32..126})
                if(n%2==1L)require(at+8+n<end && byte((at+8+n).toInt())==0)
                result+=Chunk(type,at,n.toInt());at+=(8+n+n%2).toInt()
            };require(at==end);return result
        }
        fun dimensions(c:Chunk):Pair<Int,Int> {
            val p=c.at+8
            return when(c.type) {
                "VP8L"->{require(c.size>=5 && byte(p)==0x2f);val bits=u32(p+1);require(bits ushr 29==0L);((bits and 0x3fff)+1).toInt() to (((bits ushr 14) and 0x3fff)+1).toInt()}
                "VP8 "->{require(c.size>=10 && byte(p) and 1==0 && byte(p+3)==0x9d && byte(p+4)==1 && byte(p+5)==0x2a)
                    require((u24(p) ushr 5)<c.size-3);((byte(p+6) or (byte(p+7) shl 8)) and 0x3fff) to ((byte(p+8) or (byte(p+9) shl 8)) and 0x3fff)}
                else->error("Faltan píxeles WebP.")
            }.also {require(it.first>0 && it.second>0)}
        }
        fun pixels(cs:List<Chunk>,w:Int,h:Int):List<Chunk> {
            val data=cs.filter {it.type in setOf("ALPH","VP8 ","VP8L")}
            require(data.count {it.type=="VP8 " || it.type=="VP8L"}==1 && data.count {it.type=="ALPH"}<=1)
            val image=data.last();require(image.type!="ALPH" && dimensions(image)==(w to h))
            if(data.any {it.type=="ALPH"}) {require(data.size==2 && data.first().type=="ALPH" && image.type=="VP8 ");val alpha=data.first();require(alpha.size>0 && byte(alpha.at+8) and 0xc3<=1 && (byte(alpha.at+8) ushr 4) and 3<=1)}
            // Native bounded decode of each independent frame validates compressed pixel payloads.
            val raw=ByteArrayOutputStream()
            if(data.first().type=="ALPH") {val x=ByteArray(10);x[0]=16;for(i in 0..2) {x[4+i]=((w-1) ushr (8*i)).toByte();x[7+i]=((h-1) ushr (8*i)).toByte()};writeChunk(raw,"VP8X",x)}
            data.forEach {raw.write(b,it.at,it.size+8+(it.size and 1))}
            val frame=riff(raw.toByteArray());var sample=1;while(maxOf(w,h)/sample>512)sample*=2
            if(validatePixels)requireNotNull(BitmapFactory.decodeByteArray(frame,0,frame.size,BitmapFactory.Options().apply {inSampleSize=sample})) {"Fotograma WebP corrupto."}.recycle()
            return data
        }
        val cs=chunks(12,b.size);require(cs.isNotEmpty())
        val x=cs.singleOrNull {it.type=="VP8X"};require(cs.count {it.type=="VP8X"}<=1 && (x==null || cs.first()==x))
        val flags=x?.let {require(it.size==10 && byte(it.at+8) and 0xc1==0 && u24(it.at+9)==0);byte(it.at+8)} ?: 0
        val animated=flags and 2!=0
        val size=if(x!=null)(u24(x.at+12)+1) to (u24(x.at+15)+1) else dimensions(cs.first())
        val w=size.first;val h=size.second;require(w in 1..32000 && h in 1..32000)
        for(type in listOf("ICCP","EXIF","XMP ","ANIM"))require(cs.count {it.type==type}<=1)
        require((flags and 32!=0)==cs.any {it.type=="ICCP"})
        require((flags and 8!=0)==cs.any {it.type=="EXIF"})
        require((flags and 4!=0)==cs.any {it.type=="XMP "})
        val sourceOrientation=if(cs.any {it.type=="EXIF"}) PrivatePhoto.sourceOrientation(b) else 1
        val orientation=displayOrientation ?: sourceOrientation
        val frameChunks=cs.filter {it.type=="ANMF"};val framePixels=mutableMapOf<Int,List<Chunk>>()
        if(animated) {
            val anim=requireNotNull(cs.singleOrNull {it.type=="ANIM"});require(anim.size==6 && frameChunks.size in 1..1000 && cs.indexOf(anim)<cs.indexOf(frameChunks.first()))
            require(cs.none {it.type in setOf("VP8 ","VP8L","ALPH")})
            frameChunks.forEach {c->val p=c.at+8;require(c.size>=16 && byte(p+15) and 0xfc==0)
                val fw=u24(p+6)+1;val fh=u24(p+9)+1;require(u24(p).toLong()*2+fw<=w && u24(p+3).toLong()*2+fh<=h)
                framePixels[c.at]=pixels(chunks(p+16,p+c.size),fw,fh)
            }
        } else {require(frameChunks.isEmpty() && cs.none {it.type=="ANIM"});framePixels[-1]=pixels(cs,w,h)}
        val info=Info(w,h,if(animated)frameChunks.size else 1,orientation,flags and 32!=0,animated)
        return Plan(info) {
            val out=ByteArrayOutputStream()
            if(x==null && orientation!=1) {val payload=ByteArray(10);payload[0]=8;for(i in 0..2) {payload[4+i]=((w-1) ushr (8*i)).toByte();payload[7+i]=((h-1) ushr (8*i)).toByte()};writeChunk(out,"VP8X",payload)}
            for(c in cs)when(c.type) {
                "VP8X"->{val payload=b.copyOfRange(c.at+8,c.at+18);payload[0]=((flags and 0x12) or if(orientation!=1)8 else 0).toByte();writeChunk(out,"VP8X",payload)}
                "ANIM"->out.write(b,c.at,c.size+8+(c.size and 1))
                "ANMF"->{val frame=ByteArrayOutputStream();frame.write(b,c.at+8,16);framePixels.getValue(c.at).forEach {frame.write(b,it.at,it.size+8+(it.size and 1))};writeChunk(out,"ANMF",frame.toByteArray())}
                "VP8 ","VP8L","ALPH"->if(!animated)out.write(b,c.at,c.size+8+(c.size and 1))
                "EXIF"->Unit
            }
            if(orientation!=1)writeChunk(out,"EXIF",PublicPng.orientationTiff(orientation))
            riff(out.toByteArray())
        }
    }
    private fun little(out:ByteArrayOutputStream,n:Int) {for(i in 0..3)out.write(n ushr (i*8) and 255)}
    private fun writeChunk(out:ByteArrayOutputStream,type:String,b:ByteArray) {out.write(type.toByteArray());little(out,b.size);out.write(b);if(b.size and 1!=0)out.write(0)}
    private fun riff(payload:ByteArray)=ByteArrayOutputStream().apply {write("RIFF".toByteArray());little(this,payload.size+4);write("WEBP".toByteArray());write(payload)}.toByteArray()
}
