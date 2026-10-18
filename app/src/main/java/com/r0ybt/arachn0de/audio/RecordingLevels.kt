package com.r0ybt.arachn0de.audio

import kotlin.math.log10
import kotlin.math.sqrt

/** Four consecutive time windows of signed, little-endian PCM16, not frequency bands. */
internal fun pcmRecordingLevels(pcm:ByteArray,count:Int):List<Float> {
    require(count in 0..pcm.size && count%2==0)
    val samples=count/2
    return List(4) {bar->
        val start=samples*bar/4
        val end=samples*(bar+1)/4
        var energy=0.0
        for(sample in start until end) {
            val at=sample*2
            val value=((pcm[at].toInt() and 0xff) or (pcm[at+1].toInt() shl 8))/32768.0
            energy+=value*value
        }
        val rms=if(end>start) sqrt(energy/(end-start)) else 0.0
        // Quiet input stays small; speech gets a useful range without amplifying silence.
        if(rms<=0.0) 0f else ((20*log10(rms)+54)/48).toFloat().coerceIn(0f,1f)
    }
}
