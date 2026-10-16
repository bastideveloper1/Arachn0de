package com.r0ybt.arachn0de.audio

import java.io.InputStream

/** ADTS AAC-LC mono, 16 kHz. Samples, not wall time, define duration across pauses. */
internal object AacFrames {
    const val RATE=16000
    fun packet(payload:ByteArray):ByteArray {
        val n=payload.size+7;require(n in 8..8191)
        return byteArrayOf(0xff.toByte(),0xf1.toByte(),0x60,((1 shl 6) or (n shr 11)).toByte(),(n shr 3).toByte(),(((n and 7) shl 5) or 31).toByte(),0xfc.toByte())+payload
    }
    fun duration(input:InputStream):Long {
        var frames=0L
        while(true) {
            val first=input.read();if(first<0) break
            val h=ByteArray(7);h[0]=first.toByte();var at=1
            while(at<7) {val n=input.read(h,at,7-at);require(n>0) {"Audio incompleto."};at+=n}
            require(first==255 && h[1].toInt() and 0xff==241 && h[2].toInt() and 0xff==96 && h[3].toInt() and 0xc0==64 && h[6].toInt() and 3==0) {"Audio AAC inválido."}
            val size=((h[3].toInt() and 3) shl 11) or ((h[4].toInt() and 255) shl 3) or ((h[5].toInt() and 224) shr 5)
            require(size>7);var left=size-7;val bytes=ByteArray(8192)
            while(left>0) {val n=input.read(bytes,0,minOf(left,bytes.size));require(n>0) {"Audio incompleto."};left-=n}
            frames++
        }
        require(frames>0) {"No se capturó sonido."}
        return frames*1024*1000/RATE
    }
}
