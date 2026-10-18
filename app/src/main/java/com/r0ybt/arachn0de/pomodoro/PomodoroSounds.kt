package com.r0ybt.arachn0de.pomodoro

import android.content.Context
import android.content.SharedPreferences
import android.media.*
import android.net.Uri
import android.util.Base64
import com.r0ybt.arachn0de.R
import com.r0ybt.arachn0de.security.EncryptedPreferences
import org.json.JSONObject
import java.security.MessageDigest

/** Small original clips live in the encrypted snapshot itself, never as device paths or loose files. */
internal object PomodoroSoundCodec {
    const val NAME="pomodoro_sounds"
    const val MAX_BYTES=512*1024
    val keys=setOf("ticking","completion")
    fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
    fun pack(bytes:ByteArray):String {
        checkAudio(bytes)
        return JSONObject().put("version",1).put("sha256",hash(bytes)).put("data",Base64.encodeToString(bytes,Base64.NO_WRAP)).toString()
    }
    fun unpack(text:String):ByteArray {
        require(text.length<=MAX_BYTES*4/3+1024)
        val j=JSONObject(text)
        require(j.keys().asSequence().toSet()==setOf("version","sha256","data"))
        require(j.get("version")==1 && j.get("data") is String && j.get("sha256") is String)
        val bytes=Base64.decode(j.getString("data"),Base64.NO_WRAP)
        require(Base64.encodeToString(bytes,Base64.NO_WRAP)==j.getString("data"))
        require(hash(bytes)==j.getString("sha256"));checkAudio(bytes);return bytes
    }
    private fun checkAudio(bytes:ByteArray) {
        require(bytes.size in 16..MAX_BYTES) {"El sonido debe ocupar como máximo 512 KiB."}
        val head=String(bytes,0,minOf(12,bytes.size),Charsets.ISO_8859_1)
        require((head.startsWith("RIFF") && head.substring(8)=="WAVE") || head.startsWith("OggS") || head.startsWith("ID3") ||
            (bytes[0].toInt() and 255==255 && bytes[1].toInt() and 224==224)) {"Selecciona un audio WAV, OGG o MP3."}
    }
    fun validatePayload(payload:String) {
        val values=EncryptedPreferences.decode(payload)
        require(values.keys.all {it in keys})
        values.values.forEach {require(it is String);unpack(it)}
    }
}

internal class PomodoroBytes(private val bytes:ByteArray):MediaDataSource() {
    override fun getSize()=bytes.size.toLong()
    override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int {
        if(position<0 || position>=bytes.size) return -1
        val count=minOf(size,bytes.size-position.toInt());bytes.copyInto(buffer,offset,position.toInt(),position.toInt()+count);return count
    }
    override fun close() {}
}

/** One application-owned loop. Screens never create, restart or release a player. */
internal class PomodoroSounds(private val context:Context) {
    private var preferences:SharedPreferences?=null
    private var loop:MediaPlayer?=null
    private var notice:MediaPlayer?=null
    private var loopKey:String?=null
    @Volatile var foreground=false
    @Volatile var recording=false
    private var settings=PomodoroState()
    private val listener=SharedPreferences.OnSharedPreferenceChangeListener {_,_->update(settings)}
    @Synchronized fun attach(value:SharedPreferences?) {
        if(preferences===value) return
        preferences?.unregisterOnSharedPreferenceChangeListener(listener)
        stop();preferences=value;value?.registerOnSharedPreferenceChangeListener(listener)
    }
    fun selected(key:String)=runCatching {preferences?.contains(key)==true}.getOrDefault(false)
    fun import(key:String,uri:Uri) {
        require(key in PomodoroSoundCodec.keys)
        val bytes=context.contentResolver.openInputStream(uri)!!.use {input->
            val output=java.io.ByteArrayOutputStream();val block=ByteArray(8192)
            while(true) {val n=input.read(block);if(n<0) break;require(output.size()+n<=PomodoroSoundCodec.MAX_BYTES) {"El sonido debe ocupar como máximo 512 KiB."};output.write(block,0,n)};output.toByteArray()
        }
        val encoded=PomodoroSoundCodec.pack(bytes)
        // Ask Android's decoder before committing; invalid imports preserve the previous selection.
        val probe=MediaPlayer()
        try {probe.setDataSource(PomodoroBytes(bytes));probe.prepare();require(probe.duration in 1..30_000) {"El sonido debe durar entre 1 ms y 30 segundos."}}
        finally {probe.release()}
        val store=requireNotNull(preferences) {"Desbloquea el almacén para elegir sonidos."}
        check(store.edit().putString(key,encoded).commit()) {"No se pudo guardar el sonido."}
    }
    fun reset(key:String) {require(key in PomodoroSoundCodec.keys);check(requireNotNull(preferences).edit().remove(key).commit())}
    private fun audible():Boolean {
        val audio=context.getSystemService(AudioManager::class.java)
        val notifications=context.getSystemService(android.app.NotificationManager::class.java)
        return audio.ringerMode==AudioManager.RINGER_MODE_NORMAL && notifications.currentInterruptionFilter in setOf(
            android.app.NotificationManager.INTERRUPTION_FILTER_ALL,android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
    }
    private fun player(key:String,repeat:Boolean):MediaPlayer {
        val player=MediaPlayer()
        try {
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            val custom=preferences?.getString(key,null)
            if(custom!=null) player.setDataSource(PomodoroBytes(PomodoroSoundCodec.unpack(custom)))
            else context.resources.openRawResourceFd(if(key=="ticking") R.raw.pomodoro_clock else R.raw.pomodoro_bell).use {player.setDataSource(it.fileDescriptor,it.startOffset,it.length)}
            player.isLooping=repeat;player.setVolume(1f,1f);player.prepare();return player
        } catch(e:Exception) {player.release();throw e}
    }
    @Synchronized fun update(state:PomodoroState) {
        settings=state
        if(recording || !state.config.sound) {notice?.release();notice=null}
        val play=foreground && !recording && preferences!=null && state.config.ticking &&
            state.session?.status==PomodoroStatus.RUNNING && state.session.phase==PomodoroPhase.FOCUS && !state.session.manualEnd && audible()
        val key=if(play) runCatching {preferences?.getString("ticking",null) ?: "builtin"}.getOrDefault("builtin") else null
        if(key==loopKey) return
        loop?.release();loop=null;loopKey=null
        if(key!=null) runCatching {player("ticking",true).also {loop=it;it.start();loopKey=key}}.onFailure {android.util.Log.w("Pomodoro","No se pudo reproducir el tic-tac",it)}
    }
    @Synchronized fun completed() {
        val capturing=(context.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication)?.security?.current?.value?.audio?.state?.value?.recording==true
        if(recording || capturing || !settings.config.sound || !audible()) return
        notice?.release();notice=null
        runCatching {player("completion",false).also {p->notice=p;p.setOnCompletionListener {synchronized(this) {if(notice===it) notice=null;it.release()}};p.start()}}
            .onFailure {android.util.Log.w("Pomodoro","No se pudo reproducir el aviso",it)}
    }
    @Synchronized fun stop() {loop?.release();notice?.release();loop=null;notice=null;loopKey=null}
}
