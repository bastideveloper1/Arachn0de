package com.r0ybt.arachn0de.audio

import org.junit.Assert.*
import org.junit.Test

class RecordingLevelsTest {
    private fun pcm(vararg samples:Int)=ByteArray(samples.size*2).also {bytes->
        samples.forEachIndexed {index,value->
            bytes[index*2]=value.toByte();bytes[index*2+1]=(value shr 8).toByte()
        }
    }

    @Test fun silenceAndQuietInputRemainSmall() {
        assertEquals(listOf(0f,0f,0f,0f),pcmRecordingLevels(ByteArray(32),32))
        assertEquals(listOf(0f,0f,0f,0f),pcmRecordingLevels(pcm(1,-1,16,-16),8))
        assertEquals(listOf(0f,0f,0f,0f),pcmRecordingLevels(ByteArray(0),0))
    }

    @Test fun windowsFollowActualAmplitudeAndDecodeSignedPcm() {
        val bytes=pcm(0,0,1024,-1024,4096,-4096,32767,-32768)
        val levels=pcmRecordingLevels(bytes,bytes.size)
        assertEquals(0f,levels[0],0f)
        assertTrue(levels[1]>levels[0])
        assertTrue(levels[2]>levels[1])
        assertEquals(1f,levels[3],0f)
        assertArrayEquals(bytes,pcm(0,0,1024,-1024,4096,-4096,32767,-32768))
        assertEquals(pcmRecordingLevels(pcm(4096,4096,4096,4096),8),
            pcmRecordingLevels(pcm(-4096,-4096,-4096,-4096),8))
    }

    @Test fun ignoresUnusedBufferTail() {
        val bytes=pcm(0,0,0,0,32767,32767,32767,32767)
        assertEquals(listOf(0f,0f,0f,0f),pcmRecordingLevels(bytes,8))
    }
}
