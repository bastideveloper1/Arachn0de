package com.r0ybt.arachn0de.completion

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import com.r0ybt.arachn0de.pomodoro.PomodoroBytes
import com.r0ybt.arachn0de.pomodoro.PomodoroSoundCodec
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Same authenticated, inline byte format as Pomodoro; no loose files or original URI retained. */
internal object CompletionSoundCodec {
    const val MAX_BYTES=512*1024
    fun import(context:Context,uri:Uri):String {
        val bytes=requireNotNull(context.contentResolver.openInputStream(uri)).use {input->
            val out=java.io.ByteArrayOutputStream();val block=ByteArray(8192)
            while(true) {val n=input.read(block);if(n<0)break;require(out.size()+n<=MAX_BYTES) {"El audio supera 512 KiB."};out.write(block,0,n)}
            out.toByteArray()
        }
        return encode(bytes)
    }
    fun encode(bytes:ByteArray):String=PomodoroSoundCodec.pack(bytes).also {decode(it)}
    fun decode(raw:String,checks:com.r0ybt.arachn0de.security.PrivateCache<String,ByteArray>?=null):ByteArray {
        checks?.get(raw)?.let {return it}
        val bytes=PomodoroSoundCodec.unpack(raw)
        require(bytes.size<=MAX_BYTES)
        val duration=pcmDuration(bytes) ?: MediaMetadataRetriever().let {reader->
            try {reader.setDataSource(PomodoroBytes(bytes));reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L}
            finally {reader.release()}
        }
        require(duration in 1..3000) {"El sonido debe ser un MP3, OGG o WAV válido de hasta 3 segundos."}
        checks?.put(raw,bytes)
        return bytes
    }
    /** Strict PCM WAV fast path also makes restore validation independent of media playback. */
    private fun pcmDuration(bytes:ByteArray):Long? {
        if(bytes.size<44 || String(bytes,0,4,Charsets.US_ASCII)!="RIFF" || String(bytes,8,4,Charsets.US_ASCII)!="WAVE")return null
        val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var p=12;var rate=0L;var block=0;var data=0L;var pcm=false
        while(p+8<=bytes.size) {
            val name=String(bytes,p,4,Charsets.US_ASCII)
            val length=b.getInt(p+4).toLong() and 0xffffffffL
            require(length<=bytes.size-p-8) {"WAV incompleto."}
            if(name=="fmt " && length>=16) {
                pcm=b.getShort(p+8).toInt()==1
                val channels=b.getShort(p+10).toInt();val sample=b.getInt(p+12)
                rate=b.getInt(p+16).toLong() and 0xffffffffL;block=b.getShort(p+20).toInt()
                val bits=b.getShort(p+22).toInt()
                if(pcm)require(channels in 1..2 && bits in listOf(8,16) && sample>0 && block==channels*bits/8 && rate==sample.toLong()*block) {"Formato PCM incompatible."}
            }
            if(name=="data")data+=length
            p+=(8+length+(length%2)).toInt()
        }
        if(!pcm)return null
        require(rate>0 && block>0 && data>0 && data%block==0L && data*1000<=rate*3000) {"El WAV debe durar hasta 3 segundos."}
        return ((data*1000+rate-1)/rate)
    }
}
