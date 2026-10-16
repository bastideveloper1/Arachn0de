package com.r0ybt.arachn0de.audio

import android.media.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Microphone PCM is encoded directly to AAC; only encrypted segments reach disk. */
internal class AudioCapture(private val store:AudioDraftStore,private val draft:String,private val progress:(Long)->Unit,private val interrupted:()->Unit={}) {
    val paused=AtomicBoolean(false)
    val stopped=AtomicBoolean(false)
    @Volatile var limitReached=false
        private set
    @Volatile private var microphone:AudioRecord?=null
    fun stop() {stopped.set(true);runCatching {microphone?.stop()}}
    suspend fun run()=withContext(Dispatchers.IO) {
        val minimum=AudioRecord.getMinBufferSize(AacFrames.RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
        require(minimum>0) {"Micrófono no disponible."}
        val record=AudioRecord(MediaRecorder.AudioSource.MIC,AacFrames.RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(minimum,8192))
        val codec=try {MediaCodec.createEncoderByType("audio/mp4a-latm")} catch(e:Exception) {record.release();throw e}
        val segment=WipingByteArrayOutputStream();var number=0;var total=0L;var samples=0L
        fun publish() {if(segment.size()>0) {val bytes=segment.toByteArray();try {store.append(draft,number,bytes);number++;total+=bytes.size;segment.erase()} finally {bytes.fill(0)}}}
        var eosQueued=false
        val callback=if(android.os.Build.VERSION.SDK_INT>=29) object:AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs:MutableList<AudioRecordingConfiguration>?) {
                if(configs.orEmpty().any {it.clientAudioSessionId==record.audioSessionId && it.isClientSilenced}) {paused.set(true);interrupted()}
            }
        } else null
        val info=MediaCodec.BufferInfo()
        fun drain(end:Boolean=false):Boolean {
            var finished=false
            while(true) {
                val out=codec.dequeueOutputBuffer(info,if(end) 10000 else 0)
                if(out<0) break
                if(info.size>0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0) {
                    val b=codec.getOutputBuffer(out)!!;b.position(info.offset);b.limit(info.offset+info.size)
                    val payload=ByteArray(info.size);b.get(payload);val packet=AacFrames.packet(payload);payload.fill(0)
                    if(total+segment.size()+packet.size>20L*1024*1024) {limitReached=true;stopped.set(true)} else segment.write(packet)
                    packet.fill(0)
                    if(segment.size()>=4096) publish()
                }
                finished=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0
                codec.releaseOutputBuffer(out,false)
                if(finished) break
            }
            return finished
        }
        try {
            require(record.state==AudioRecord.STATE_INITIALIZED) {"No se pudo abrir el micrófono."}
            microphone=record
            if(android.os.Build.VERSION.SDK_INT>=29 && callback!=null) record.registerAudioRecordingCallback(java.util.concurrent.Executor {it.run()},callback)
            val format=MediaFormat.createAudioFormat("audio/mp4a-latm",AacFrames.RATE,1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE,32000);setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,8192)
            }
            codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start();record.startRecording()
            var recording=true
            val pcm=ByteArray(2048)
            while(!stopped.get()) {
                currentCoroutineContext().ensureActive()
                if(paused.get()) {if(recording) {record.stop();recording=false;drain();publish()};delay(50);continue}
                if(!recording) {record.startRecording();recording=true}
                val index=codec.dequeueInputBuffer(10000)
                if(index<0) {drain();continue}
                val n=record.read(pcm,0,pcm.size,AudioRecord.READ_BLOCKING)
                if(n<=0) {if(stopped.get()) {codec.queueInputBuffer(index,0,0,samples*1000000/AacFrames.RATE,MediaCodec.BUFFER_FLAG_END_OF_STREAM);eosQueued=true;break};error("La captura se interrumpió; se conserva el audio parcial.")}
                if(paused.get()) {codec.queueInputBuffer(index,0,0,samples*1000000/AacFrames.RATE,0);continue}
                val buffer=codec.getInputBuffer(index)!!;buffer.clear();buffer.put(pcm,0,n)
                codec.queueInputBuffer(index,0,n,samples*1000000/AacFrames.RATE,0);samples+=n/2
                progress(samples*1000/AacFrames.RATE);drain();pcm.fill(0)
            }
            val end=if(eosQueued) -1 else codec.dequeueInputBuffer(10000)
            if(end>=0) codec.queueInputBuffer(end,0,0,samples*1000000/AacFrames.RATE,MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            for(i in 0..100) {if(drain(true)) break}
        } finally {
            runCatching {record.stop()};if(android.os.Build.VERSION.SDK_INT>=29 && callback!=null) runCatching {record.unregisterAudioRecordingCallback(callback)};record.release();microphone=null
            // Keep every complete encoder packet even when microphone/encoder interruption occurs.
            try {runCatching {drain()};publish()} finally {segment.erase();runCatching {codec.stop()};codec.release()}
        }
    }
}
