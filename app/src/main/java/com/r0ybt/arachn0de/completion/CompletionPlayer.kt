package com.r0ybt.arachn0de.completion

import android.media.*
import com.r0ybt.arachn0de.pomodoro.PomodoroBytes

/** Main-thread owned, preloaded and disposed with its editor or authenticated session. */
internal class CompletionPlayer(scope:com.r0ybt.arachn0de.security.PrivateCacheScope?=null,private val allowed:()->Boolean={true}):CompletionAudio {
    private val checks=com.r0ybt.arachn0de.security.PrivateCache<String,ByteArray>(scope,4)
    private var tone:ToneGenerator?=runCatching {ToneGenerator(AudioManager.STREAM_MUSIC,30)}.getOrNull()
    private var player:MediaPlayer?=null
    private var source:String?=null
    private var ready=false
    private var pending=false
    override fun select(clip:String?) {
        if(source==clip && (clip==null || player!=null))return
        stop();player?.release();player=null;source=clip;ready=false
        if(clip==null)return
        try {
            val bytes=CompletionSoundCodec.decode(clip,checks)
            val next=MediaPlayer();player=next
            next.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            next.setDataSource(PomodoroBytes(bytes))
            next.setOnPreparedListener {p->if(player===p) {
                if(p.duration !in 1..3000) {p.release();player=null;ready=false;if(pending)tin();pending=false}
                else {ready=true;if(pending) {pending=false;if(allowed())p.start()}}
            }}
            next.setOnErrorListener {p,_,_->if(player===p) {p.release();player=null;ready=false;if(pending)tin();pending=false};true}
            next.prepareAsync()
        } catch(_:Exception) {player?.release();player=null;ready=false;android.util.Log.w("CompletionSound","No se pudo preparar el audio; se utilizará el sonido predeterminado.")}
    }
    override fun play() {
        if(!allowed()) {stop();return}
        if(source==null || player==null) {tin();return}
        if(ready)runCatching {player?.seekTo(0);player?.start()} else pending=true
    }
    private fun tin() {if(!allowed())return;runCatching {if(tone==null)tone=ToneGenerator(AudioManager.STREAM_MUSIC,30);tone?.startTone(ToneGenerator.TONE_PROP_BEEP,65)}}
    override fun stop() {pending=false;runCatching {player?.pause()};tone?.stopTone()}
    override fun close() {checks.dispose();stop();player?.release();player=null;tone?.release();tone=null;source=null}
}
