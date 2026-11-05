package com.r0ybt.arachn0de.diagrams

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.*
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.*
import java.io.FilterOutputStream
import java.io.OutputStream
import kotlin.math.ceil

internal enum class DiagramFormat(val label:String,val mime:String,val extension:String) {
    PNG("Imagen PNG","image/png","png"),GIF("GIF animado","image/gif","gif"),MP4("Video MP4","video/mp4","mp4")
}
internal enum class DiagramExportProfile { SHARE,HIGH,CUSTOM }
internal data class DiagramExportOptions(val format:DiagramFormat=DiagramFormat.PNG,val width:Int=640,val seconds:Int=8,val loop:Boolean=true,val profile:DiagramExportProfile=DiagramExportProfile.CUSTOM) {
    val height get()=width*DIAGRAM_H/DIAGRAM_W // Historical callers; production uses dimensions(document).
    val fps get()=if(format==DiagramFormat.GIF)10 else 20
    fun dimensions(d:DiagramDocument):Pair<Int,Int> {
        val limit=if(format==DiagramFormat.GIF)640 else 1920
        var w:Double;var h:Double
        if(profile==DiagramExportProfile.HIGH&&format!=DiagramFormat.GIF) {
            w=(if(d.page.format==DiagramPageFormat.FREE)d.width else d.page.format.exportW).toDouble()
            h=(if(d.page.format==DiagramPageFormat.FREE)d.height else d.page.format.exportH).toDouble()
        } else {
            val side=when(profile) {DiagramExportProfile.SHARE->if(format==DiagramFormat.GIF)320 else 960;DiagramExportProfile.HIGH->640;else->width}
            if(d.width>=d.height) {w=side.toDouble();h=w*d.height/d.width} else {h=side.toDouble();w=h*d.width/d.height}
        }
        val scale=minOf(1.0,limit/maxOf(w,h),kotlin.math.sqrt(2_100_000.0/(w*h)));w*=scale;h*=scale
        return (w.toInt()/2*2).coerceAtLeast(2) to (h.toInt()/2*2).coerceAtLeast(2)
    }
    fun duration(d:DiagramDocument):Float {
        if(format==DiagramFormat.PNG)return 0f
        val full=if(d.playback.mode==DiagramAnimationMode.SEQUENTIAL)diagramTimeline(d).let {if(d.playback.loop)it.cycleSeconds else it.onceSeconds()}
        else if(d.playback.mode==DiagramAnimationMode.OFF)0f else d.edges.filter {it.flow.enabled&&(!it.flow.loop||!d.playback.loop)}.maxOfOrNull {flowEnd(it.flow)} ?: 0f
        return maxOf(seconds.toFloat(),full).also {require(it<=60f) {"El recorrido supera 60 s. Reduce el recorrido o aumenta su velocidad."}}
    }
    fun validateDocument(d:DiagramDocument) {
        validate();DiagramCodec.validate(d)
        require(d.nodes.none {it.shape==DiagramShape.IMAGE&&it.text.isNotBlank()&&(it.y+it.h)*d.height+6+diagramTextLayout(it,d).height>d.height}) {"Una etiqueta de imagen queda fuera del documento. Amplía el lienzo o mueve la imagen antes de exportar."}
        if(format!=DiagramFormat.PNG&&d.playback.mode==DiagramAnimationMode.SEQUENTIAL)require(diagramRoute(d).choices.isEmpty()) {"Elige el recorrido en Animación global antes de exportar."}
        duration(d)
    }
    fun validate() {require(width in 2..1920&&seconds in 1..60)}
}
internal fun flowEnd(f:DiagramFlow)=((if(f.direction==FlowDirection.BOUNCE)2f else 1f)+(f.count-1f)/f.count)*4/f.speed
internal const val DIAGRAM_EXPORT_LIMIT=96L*1024*1024
internal class BoundedDiagramOutput(output:OutputStream,private val limit:Long=DIAGRAM_EXPORT_LIMIT):FilterOutputStream(output) {
    var count=0L;private set
    override fun write(b:Int) {check(count<limit) {"La exportación supera el límite de 96 MiB."};out.write(b);count++}
    override fun write(b:ByteArray,off:Int,len:Int) {check(count+len<=limit) {"La exportación supera el límite de 96 MiB."};out.write(b,off,len);count+=len}
}
/** Single reusable frame. Video explicitly composites alpha over white. */
internal fun renderDiagramFrame(renderer:DiagramRenderer,d:DiagramDocument,bitmap:Bitmap,time:Float?,opaque:Boolean=false) {
    val canvas=Canvas(bitmap);canvas.save();canvas.scale(bitmap.width.toFloat()/d.width,bitmap.height.toFloat()/d.height)
    renderer.draw(canvas,d,time);canvas.restore()
    if(opaque) {val p=android.graphics.Paint().apply {color=Color.WHITE;xfermode=android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OVER)};canvas.drawRect(0f,0f,bitmap.width.toFloat(),bitmap.height.toFloat(),p)}
}
/** GIF89a writer: exact per-frame palettes when possible, RGB332 fallback, binary alpha, full frames and bounded LZW blocks.
 * Frequent clear codes keep the code width at 9 bits. No dictionary or frame retention.
 * Deliberately trades compression for small, auditable, dependency-free memory usage. */
internal class DiagramGif(private val output:OutputStream,val width:Int,val height:Int,loop:Boolean):AutoCloseable {
    private fun byte(v:Int)=output.write(v and 255)
    private fun short(v:Int) {byte(v);byte(v ushr 8)}
    init {
        require(width>0&&height>0)
        output.write("GIF89a".toByteArray(Charsets.US_ASCII));short(width);short(height);byte(0xf7);byte(0);byte(0)
        repeat(256) {i->byte(((i shr 5) and 7)*255/7);byte(((i shr 2) and 7)*255/7);byte(if(i==1)0 else (i and 3)*255/3)}
        if(loop) {byte(0x21);byte(0xff);byte(11);output.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII));byte(3);byte(1);short(0);byte(0)}
    }
    fun frame(bitmap:Bitmap,delayCentiseconds:Int=10) {
        require(bitmap.width==width&&bitmap.height==height)
        byte(0x21);byte(0xf9);byte(4);byte(9);short(delayCentiseconds);byte(0);byte(0)
        // Index 0 is transparent; opaque black must have its own black entry.
        val exact=linkedMapOf<Int,Int>();val scan=IntArray(width)
        scan@for(y in 0 until height) {
            bitmap.getPixels(scan,0,width,0,y,width,1)
            for(c in scan)if(Color.alpha(c)>=128) {
                val rgb=c or Color.BLACK
                if(rgb !in exact) {if(exact.size==255) {exact.clear();break@scan};exact[rgb]=exact.size+1}
            }
        }
        val local=exact.isNotEmpty()
        byte(0x2c);short(0);short(0);short(width);short(height);byte(if(local)0x87 else 0)
        if(local) {
            byte(0);byte(0);byte(0)
            exact.keys.forEach {c->byte(Color.red(c));byte(Color.green(c));byte(Color.blue(c))}
            repeat(255-exact.size) {byte(0);byte(0);byte(0)}
        }
        byte(8)
        val block=ByteArray(255);var length=0;var bits=0;var bitCount=0
        fun encodedByte(v:Int) {block[length++]=v.toByte();if(length==255) {byte(length);output.write(block,0,length);length=0}}
        fun code(v:Int) {bits=bits or (v shl bitCount);bitCount+=9;while(bitCount>=8) {encodedByte(bits and 255);bits=bits ushr 8;bitCount-=8}}
        code(256);var literals=0;val row=IntArray(width)
        repeat(height) {y->bitmap.getPixels(row,0,width,0,y,width,1);row.forEach {c->
            if(literals==240) {code(256);literals=0}
            val index=if(Color.alpha(c)<128)0 else if(local)exact.getValue(c or Color.BLACK) else {
                val quantized=((Color.red(c)*7+127)/255 shl 5) or ((Color.green(c)*7+127)/255 shl 2) or ((Color.blue(c)*3+127)/255)
                when(quantized) {0->1;1->if(Color.blue(c)<85)1 else 2;else->quantized}
            }
            code(index);literals++
        }}
        code(257);if(bitCount>0)encodedByte(bits and 255)
        if(length>0) {byte(length);output.write(block,0,length)};byte(0)
    }
    override fun close() {byte(0x3b)}
}

/** Converts a reusable ARGB frame to the codec's actual plane/pixel strides. */
internal fun writeDiagramYuv(bitmap:Bitmap,image:Image) {
    require(image.format==android.graphics.ImageFormat.YUV_420_888&&image.width>=bitmap.width&&image.height>=bitmap.height)
    writeDiagramYuvPlanes(bitmap,image.planes.map {DiagramYuvPlane(it.buffer.slice(),it.rowStride,it.pixelStride)})
}
internal data class DiagramYuvPlane(val buffer:java.nio.ByteBuffer,val rowStride:Int,val pixelStride:Int)
internal fun writeDiagramYuvPlanes(bitmap:Bitmap,planes:List<DiagramYuvPlane>) {
    require(planes.size==3)
    val row=IntArray(bitmap.width)
    fun put(index:Int,x:Int,y:Int,v:Int) {val plane=planes[index];plane.buffer.put(y*plane.rowStride+x*plane.pixelStride,v.coerceIn(0,255).toByte())}
    for(y in 0 until bitmap.height) {
        bitmap.getPixels(row,0,bitmap.width,0,y,bitmap.width,1)
        for(x in row.indices) {
            val c=row[x];val r=Color.red(c);val g=Color.green(c);val b=Color.blue(c)
            put(0,x,y,((66*r+129*g+25*b+128) shr 8)+16)
            if(y%2==0&&x%2==0) {put(1,x/2,y/2,((-38*r-74*g+112*b+128) shr 8)+128);put(2,x/2,y/2,((112*r-94*g-18*b+128) shr 8)+128)}
        }
    }
}

internal suspend fun exportDiagram(context:Context,tree:Uri,d:DiagramDocument,assets:Map<String,String>,options:DiagramExportOptions,journal:DiagramExportJournal=DiagramExportJournal(context,(context.applicationContext as com.r0ybt.arachn0de.Arachn0deApplication).privateContext),onProgress:suspend (Float)->Unit):Uri=withContext(Dispatchers.Default) {
    options.validateDocument(d);options.duration(d);val (outputWidth,outputHeight)=options.dimensions(d);DiagramCodec.validate(d);require(d.assets().all {it in assets}) {"La biblioteca de iconos aún no está disponible."}
    val resolver=context.contentResolver
    var ticket:DiagramExportJournal.Ticket?=null
    try {
        // Assign inside NonCancellable so cancellation cannot lose a newly created URI.
        withContext(NonCancellable) {ticket=journal.begin(tree,options.format)}
        val uri=requireNotNull(ticket?.uri)
        ensureActive()
        DiagramRenderer(assets.filterKeys {it in d.assets()}).use {renderer->
            when(options.format) {
                DiagramFormat.PNG->withContext(Dispatchers.IO) {BoundedDiagramOutput(requireNotNull(resolver.openOutputStream(uri,"wt"))).use {renderer.png(d,it,outputWidth,outputHeight)}}
                DiagramFormat.GIF-> {
                    val bitmap=Bitmap.createBitmap(outputWidth,outputHeight,Bitmap.Config.ARGB_8888)
                    try {BoundedDiagramOutput(requireNotNull(resolver.openOutputStream(uri,"wt"))).buffered(32768).use {out->
                        DiagramGif(out,outputWidth,outputHeight,options.loop).use {gif->
                            val frames=ceil(options.duration(d)*options.fps).toInt()+1
                            repeat(frames) {i->ensureActive();renderDiagramFrame(renderer,d,bitmap,i.toFloat()/options.fps);gif.frame(bitmap,100/options.fps);onProgress((i+1f)/frames)}
                        }
                    }} finally {bitmap.recycle()}
                }
                DiagramFormat.MP4-> {
                    require(android.os.Build.VERSION.SDK_INT>=26) {"La exportación MP4 requiere Android 8 o posterior."}
                    try {requireNotNull(resolver.openFileDescriptor(uri,"rw")).use {fd->encodeDiagramMp4(fd.fileDescriptor,renderer,d,options,onProgress)}}
                    catch(c:CancellationException) {throw c}
                    catch(e:Exception) {throw IllegalStateException("No se pudo codificar H.264 o la carpeta no admite escritura de video con acceso aleatorio. Utiliza GIF u otra carpeta. ${e.message}",e)}
                }
            }
        }
        ensureActive()
        // Publication is the durable completed marker; subsequent cancellation must not erase it.
        withContext(NonCancellable) {onProgress(1f);journal.complete(requireNotNull(ticket));ticket=null}
        uri
    } catch(t:Throwable) {
        ticket?.let {pending->
            try {journal.rollback(pending)} catch(cleanup:Throwable) {cleanup.addSuppressed(t);throw IllegalStateException("No se completó la exportación. Quedó un archivo parcial; se reintentará su limpieza al abrir Diagramas: ${pending.uri}",cleanup)}
        }
        throw t
    }
}

@android.annotation.TargetApi(26)
private suspend fun encodeDiagramMp4(fd:java.io.FileDescriptor,renderer:DiagramRenderer,d:DiagramDocument,o:DiagramExportOptions,onProgress:suspend (Float)->Unit) {
    val (outputWidth,outputHeight)=o.dimensions(d)
    val encoder=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    var muxer:MediaMuxer?=null;var started=false;var codecStarted=false
    var bitmap:Bitmap?=null
    try {
        val frameBitmap=Bitmap.createBitmap(outputWidth,outputHeight,Bitmap.Config.ARGB_8888);bitmap=frameBitmap
        require(encoder.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)) {"El codificador H.264 no admite imágenes YUV compatibles. Utiliza GIF."}
        require(encoder.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities?.isSizeSupported(outputWidth,outputHeight)==true) {"El dispositivo no codifica estas dimensiones. Elige Compartir o una resolución menor."}
        val format=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,outputWidth,outputHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE,if(outputWidth<=640)1_200_000 else 2_400_000)
            setInteger(MediaFormat.KEY_FRAME_RATE,o.fps);setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)
            setInteger(MediaFormat.KEY_COLOR_RANGE,MediaFormat.COLOR_RANGE_LIMITED)
            setInteger(MediaFormat.KEY_COLOR_STANDARD,MediaFormat.COLOR_STANDARD_BT601_NTSC)
            setInteger(MediaFormat.KEY_COLOR_TRANSFER,MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
        }
        encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);encoder.start();codecStarted=true
        val mux=MediaMuxer(fd,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);muxer=mux
        val info=MediaCodec.BufferInfo();var track=-1;var frame=0;var eos=false;var done=false;var bytes=0L
        val frames=ceil(o.duration(d)*o.fps).toInt()+1
        var lastProgress=System.nanoTime()
        while(!done) {
            currentCoroutineContext().ensureActive()
            check(System.nanoTime()-lastProgress<15_000_000_000L) {"El codificador H.264 dejó de responder."}
            if(!eos) {
                val index=encoder.dequeueInputBuffer(10_000)
                if(index>=0) {
                    if(frame==frames) {encoder.queueInputBuffer(index,0,0,frame*1_000_000L/o.fps,MediaCodec.BUFFER_FLAG_END_OF_STREAM);eos=true}
                    else {
                        renderDiagramFrame(renderer,d,frameBitmap,frame.toFloat()/o.fps,true)
                        val image=requireNotNull(encoder.getInputImage(index)) {"El codificador no expone planos YUV. Utiliza GIF."}
                        try {writeDiagramYuv(frameBitmap,image)} finally {image.close()}
                        encoder.queueInputBuffer(index,0,outputWidth*outputHeight*3/2,frame*1_000_000L/o.fps,0)
                        frame++;onProgress(frame.toFloat()/frames)
                    };lastProgress=System.nanoTime()
                }
            }
            when(val index=encoder.dequeueOutputBuffer(info,10_000)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED-> {check(!started);track=mux.addTrack(encoder.outputFormat);mux.start();started=true;lastProgress=System.nanoTime()}
                else->if(index>=0) {
                    try {
                        if(info.size>0&&info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0) {
                            check(started);bytes+=info.size;check(bytes<=DIAGRAM_EXPORT_LIMIT) {"El video supera 96 MiB."}
                            val buffer=requireNotNull(encoder.getOutputBuffer(index));buffer.position(info.offset);buffer.limit(info.offset+info.size);mux.writeSampleData(track,buffer,info)
                        }
                        done=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0;lastProgress=System.nanoTime()
                    } finally {encoder.releaseOutputBuffer(index,false)}
                }
            }
        }
        check(started&&bytes>0) {"El codificador no produjo video."}
        mux.stop();started=false
    } finally {
        bitmap?.recycle()
        if(codecStarted)runCatching {encoder.stop()};runCatching {encoder.release()}
        // stop() on the success path is mandatory; release alone must never report success.
        if(started)runCatching {muxer?.stop()};runCatching {muxer?.release()}
    }
}
