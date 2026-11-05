package com.r0ybt.arachn0de.photos

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Inflater

/** PNG/APNG publication plan. Pixel streams and animation controls stay byte-exact. */
internal object PublicPng {
    private val signature=byteArrayOf(-119,80,78,71,13,10,26,10)
    private data class Chunk(val type:String,val at:Int,val size:Int)
    internal data class Info(val width:Int,val height:Int,val frames:Int,val orientation:Int,val icc:Boolean,val animated:Boolean)
    internal class Plan internal constructor(val info:Info,private val write:()->ByteArray) {fun bytes()=write()}
    fun matches(b:ByteArray)=b.size>=8 && b.copyOfRange(0,8).contentEquals(signature)
    fun inspect(b:ByteArray,displayOrientation:Int?=null):Plan {
        require(matches(b) && b.size<=PrivatePhoto.LIMIT)
        fun int(at:Int):Int {require(at>=0 && at+4<=b.size);return ((b[at].toInt() and 255) shl 24) or ((b[at+1].toInt() and 255) shl 16) or ((b[at+2].toInt() and 255) shl 8) or (b[at+3].toInt() and 255)}
        val chunks=mutableListOf<Chunk>();var at=8;var ended=false
        while(at<b.size) {
            require(b.size-at>=12);val n=int(at);require(n>=0 && n<=b.size-at-12)
            val type=String(b,at+4,4,Charsets.US_ASCII);require(type.all {it in 'A'..'Z' || it in 'a'..'z'})
            require(CRC32().apply {update(b,at+4,n+4)}.value==(int(at+8+n).toLong() and 0xffffffffL)) {"CRC PNG inválido."}
            chunks+=Chunk(type,at,n);at+=n+12
            if(type=="IEND") {require(n==0);ended=true;break}
        }
        require(ended && chunks.first().type=="IHDR" && chunks.first().size==13 && chunks.count {it.type=="IHDR"}==1)
        val w=int(16);val h=int(20);require(w in 1..32000 && h in 1..32000)
        val depth=b[24].toInt() and 255;val color=b[25].toInt() and 255
        require(when(color) {0->depth in listOf(1,2,4,8,16);2,4,6->depth in listOf(8,16);3->depth in listOf(1,2,4,8);else->false})
        require(b[26]==0.toByte() && b[27]==0.toByte() && b[28].toInt() in 0..1)
        val channels=when(color) {0,3->1;2->3;4->2;else->4};val interlace=b[28].toInt()
        var sequence=0L;var expectedFrames=0;var frames=0;var idat=false;var idatEnded=false;var palette=false;var paletteEntries=0
        var active=false;var frameW=w;var frameH=h;var defaultFrame=false;var activeIsDefault=false
        var compressed=mutableListOf<Pair<Int,Int>>();var decodedBudget=0L
        fun verifyStream(width:Int,height:Int,parts:List<Pair<Int,Int>>) {
            require(parts.isNotEmpty()) {"Faltan píxeles PNG/APNG."}
            val passes=if(interlace==0)listOf(intArrayOf(0,0,1,1)) else listOf(intArrayOf(0,0,8,8),intArrayOf(4,0,8,8),intArrayOf(0,4,4,8),intArrayOf(2,0,4,4),intArrayOf(0,2,2,4),intArrayOf(1,0,2,2),intArrayOf(0,1,1,2))
            val rows=passes.mapNotNull {p->
                val pw=if(width<=p[0])0 else (width-p[0]+p[2]-1)/p[2];val ph=if(height<=p[1])0 else (height-p[1]+p[3]-1)/p[3]
                if(pw==0 || ph==0)null else ((pw.toLong()*channels*depth+7)/8+1) to ph
            }
            val expected=rows.sumOf {it.first*it.second};decodedBudget+=expected
            require(decodedBudget<=512L*1024*1024) {"El flujo PNG/APNG supera el presupuesto de validación de 512 MiB."}
            val inflater=Inflater();val buffer=ByteArray(32768);var total=0L;var part=0;var pass=0;var row=0;var column=0L
            try {
                while(!inflater.finished()) {
                    if(inflater.needsInput()) {require(part<parts.size) {"PNG/APNG truncado."};val p=parts[part++];inflater.setInput(b,p.first,p.second)}
                    val n=inflater.inflate(buffer);require(n>0 || inflater.finished() || inflater.needsInput()) {"Deflate PNG inválido."}
                    require(total+n<=expected)
                    for(i in 0 until n) {
                        if(column==0L)require(buffer[i].toInt() and 255<=4) {"Filtro PNG inválido."}
                        column++
                        if(column==rows[pass].first) {column=0;row++;if(row==rows[pass].second) {pass++;row=0}}
                    }
                    total+=n
                }
                require(total==expected && inflater.remaining==0 && part==parts.size) {"Píxeles PNG/APNG incompletos o adicionales."}
            } finally {buffer.fill(0);inflater.end()}
        }
        fun finishFrame() {if(active) {verifyStream(frameW,frameH,compressed);compressed=mutableListOf();active=false}}
        val seen=hashSetOf<String>()
        for(c in chunks.drop(1)) {
            val p=c.at+8
            if(idat && c.type!="IDAT")idatEnded=true
            when(c.type) {
                "PLTE"->{require(!idat && !palette && c.size in 3..768 && c.size%3==0 && color !in listOf(0,4));palette=true;paletteEntries=c.size/3;if(color==3)require(c.size/3<=1 shl depth)}
                "tRNS"->{require(!idat && seen.add(c.type));require(when(color) {0->c.size==2;2->c.size==6;3->palette && c.size in 1..paletteEntries;else->false})}
                "acTL"->{require(!idat && seen.add(c.type) && c.size==8);expectedFrames=int(p);require(expectedFrames in 1..1000)}
                "fcTL"->{
                    require(expectedFrames>0 && c.size==26 && int(p).toLong()==sequence++)
                    finishFrame();frameW=int(p+4);frameH=int(p+8);val x=int(p+12);val y=int(p+16)
                    require(frameW>0 && frameH>0 && x>=0 && y>=0 && x.toLong()+frameW<=w && y.toLong()+frameH<=h)
                    require((b[p+24].toInt() and 255)<=2 && (b[p+25].toInt() and 255)<=1)
                    if(!idat) {require(frames==0 && x==0 && y==0 && frameW==w && frameH==h);defaultFrame=true}
                    frames++;active=true;activeIsDefault=!idat
                }
                "IDAT"->{require(!idatEnded && (color!=3 || palette));if(!idat) {idat=true;if(!defaultFrame) {active=true;activeIsDefault=true;frameW=w;frameH=h}}
                    require(activeIsDefault);if(c.size>0)compressed+=p to c.size}
                "fdAT"->{require(idat && active && !activeIsDefault && c.size>=4 && int(p).toLong()==sequence++);if(c.size>4)compressed+=(p+4) to (c.size-4)}
                "IEND"->{require(idat);finishFrame()}
                "iCCP","sRGB","gAMA","cHRM","eXIf"->{require(!idat && seen.add(c.type));when(c.type) {"sRGB"->require(c.size==1 && b[p].toInt() in 0..3);"gAMA"->require(c.size==4);"cHRM"->require(c.size==32)}}
                else->require(c.type[0].isLowerCase()) {"Bloque PNG crítico desconocido."}
            }
        }
        require((expectedFrames==0 && frames==0) || expectedFrames==frames)
        val orientation=displayOrientation ?: PrivatePhoto.sourceOrientation(b)
        val info=Info(w,h,if(expectedFrames==0)1 else frames,orientation,"iCCP" in seen,expectedFrames>0)
        return Plan(info) {
            val out=ByteArrayOutputStream();out.write(signature)
            for(c in chunks) when(c.type) {
                "IHDR"->{out.write(b,c.at,c.size+12);if(info.icc)writeChunk(out,"sRGB",byteArrayOf(0));if(orientation!=1)writeChunk(out,"eXIf",orientationTiff(orientation))}
                "PLTE","IDAT","tRNS","IEND","acTL","fcTL","fdAT"->out.write(b,c.at,c.size+12)
                "sRGB","gAMA","cHRM"->if(!info.icc)out.write(b,c.at,c.size+12)
                "eXIf"->Unit
            }
            out.toByteArray()
        }
    }
    internal fun orientationTiff(o:Int)=byteArrayOf(77,77,0,42,0,0,0,8,0,1,1,18,0,3,0,0,0,1,0,o.toByte(),0,0,0,0,0,0)
    internal fun writeChunk(out:ByteArrayOutputStream,type:String,payload:ByteArray) {val t=type.toByteArray();DataOutputStream(out).apply {writeInt(payload.size);write(t);write(payload);writeInt(CRC32().apply {update(t);update(payload)}.value.toInt())}}
}
