package com.r0ybt.arachn0de.completion

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.r0ybt.arachn0de.ui.theme.AppearancePreferences
import kotlinx.coroutines.*

/** One authenticated-store owner; only confirmed completion candidates call play(). */
internal class CompletionSounds(context:Context,private val scope:CoroutineScope):AutoCloseable {
    private val storage=context.getSharedPreferences(AppearancePreferences.FILE,0)
    @Volatile private var foreground=false
    @Volatile private var closed=false
    @Volatile private var enabled=false
    private val access=(context as? com.r0ybt.arachn0de.security.VaultContext)?.session
    private val player=CompletionPlayer(access?.caches) {foreground && !closed && enabled}
    private val feedback=CompletionFeedback(player)
    private val appearance=scope.async {access?.let {AppearancePreferences(storage,it)} ?: AppearancePreferences(storage)}
    private val listener=SharedPreferences.OnSharedPreferenceChangeListener {_,key->
        if(key==AppearancePreferences.COMPLETION_SOUND)enabled=storage.getBoolean(AppearancePreferences.COMPLETION_SOUND,true)
        if(key==AppearancePreferences.COMPLETION_SOUND)scope.launch {safeRefresh()}
    }
    init {storage.registerOnSharedPreferenceChangeListener(listener);scope.launch {safeRefresh()}}
    private suspend fun safeRefresh() {
        try {refresh()} catch(c:CancellationException) {throw c}
        catch(_:Exception) {enabled=false;android.util.Log.w("CompletionSound","No se pudo cargar la configuración de audio.")}
    }
    private suspend fun refresh() {
        val prefs=appearance.await();prefs.reload()
        val settings=prefs.settings
        enabled=settings.completionSoundEnabled
        withContext(Dispatchers.Main) {
            if(!closed) {
                if(!settings.completionSoundEnabled)player.stop()
                player.select(if(settings.completionSoundEnabled)null else null)
            }
        }
    }
    fun foreground(value:Boolean) {
        foreground=value
        if(!value)Handler(Looper.getMainLooper()).post {if(!foreground || closed)player.stop()}
    }
    suspend fun play(eventId:String) {
        if(closed || !foreground)return
        val prefs=appearance.await();prefs.reload()
        val settings=prefs.settings
        enabled=settings.completionSoundEnabled
        withContext(Dispatchers.Main) {
            if(!closed && foreground && enabled) {
                feedback.completed(eventId,settings)
            }
        }
    }
    override fun close() {
        closed=true;foreground=false;storage.unregisterOnSharedPreferenceChangeListener(listener)
        Handler(Looper.getMainLooper()).post {player.close()}
    }
}
