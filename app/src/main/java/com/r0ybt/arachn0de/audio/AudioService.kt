package com.r0ybt.arachn0de.audio

import android.app.*
import android.content.*
import android.media.session.*
import android.os.*
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.MainActivity
import kotlinx.coroutines.*

/** Started exclusively by an authenticated, visible user action; never restarted after process loss. */
internal class AudioService:Service() {
    companion object {const val NOTIFICATION=845;private const val CHANNEL="private-audio"}
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var media:MediaSession?=null
    private var updates:Job?=null
    private var ownerId:String?=null
    private var audio:AudioController?=null
    private var awake:PowerManager.WakeLock?=null
    private val noisy=object:BroadcastReceiver() {override fun onReceive(context:Context,intent:Intent) {audio?.command("close")}}
    override fun onBind(intent:Intent?)=null
    override fun onCreate() {
        super.onCreate()
        awake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Arachn0de:Audio")
        if(Build.VERSION.SDK_INT>=26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Audio privado",NotificationManager.IMPORTANCE_LOW).apply {lockscreenVisibility=Notification.VISIBILITY_PRIVATE})
        if(Build.VERSION.SDK_INT>=33) registerReceiver(noisy,IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY),RECEIVER_NOT_EXPORTED) else @Suppress("DEPRECATION") registerReceiver(noisy,IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        val session=(application as Arachn0deApplication).security.current.value
        if(session==null) {stopSelf();return START_NOT_STICKY}
        if(intent?.getStringExtra("profile")!=session.access.id.toString()) {stopSelf();return START_NOT_STICKY}
        ownerId=session.access.id.toString()
        val controller=session.audio;if(controller.isClosed) {stopSelf();return START_NOT_STICKY};audio=controller
        val recording=intent?.getStringExtra("record")
        val playing=intent?.getStringExtra("play")
        if(recording!=null && (!controller.state.value.recording || controller.state.value.owner!=recording)) {stopSelf();return START_NOT_STICKY}
        if(recording!=null || playing!=null) {
            media?.release();media=if(recording!=null) null else MediaSession(this,"Arachn0deAudio").apply {
                setCallback(object:MediaSession.Callback() {
                    override fun onPlay() {if(controller.state.value.paused) controller.command("toggle")}
                    override fun onPause() {if(!controller.state.value.paused) controller.command("toggle")}
                    override fun onStop() {controller.command(if(controller.state.value.recording) "finish" else "close")}
                    override fun onSeekTo(pos:Long) {scope.launch {controller.seek(pos)}}
                });isActive=true
            }
            val notification=notification(controller.state.value)
            try {
            if(Build.VERSION.SDK_INT>=30 && recording!=null) startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else if(Build.VERSION.SDK_INT>=29 && playing!=null) startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(NOTIFICATION,notification)
            } catch(failure:RuntimeException) {controller.startFailed();stopSelf();return START_NOT_STICKY}
            if(recording!=null) controller.beginCapture(recording) else if(playing!=null) controller.beginPlayback(playing)
            updates?.cancel()
            updates=scope.launch {controller.state.collect {s->if(!s.visible) {stopSelf()} else {
                if(!s.paused && awake?.isHeld!=true) awake?.acquire() else if(s.paused && awake?.isHeld==true) awake?.release()
                media?.setMetadata(android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,"Audio de Arachn0de").putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,s.durationMs).build())
                media?.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO).setState(if(s.paused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING,s.positionMs,if(s.paused) 0f else 1f).build())
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION,notification(s))
            }}}
        } else if(controller.state.value.visible) intent?.action?.let(controller::command) else stopSelf()
        return START_NOT_STICKY
    }
    private fun notification(s:AudioState):Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun command(action:String)=PendingIntent.getService(this,(action+ownerId).hashCode(),Intent(this,AudioService::class.java).setAction(action).putExtra("profile",ownerId),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle(if(s.recording) "Grabación de Arachn0de" else "Audio de Arachn0de").setContentText(if(s.paused) "En pausa" else if(s.recording) "Grabando" else "Reproduciendo").setContentIntent(open).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(if(s.paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,if(s.paused) "Continuar" else "Pausar",command("toggle"))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,if(s.recording) "Finalizar" else "Cerrar",command(if(s.recording) "finish" else "close"))
        val result=builder.build()
        if(!s.recording) return Notification.Builder(this).let {native->
            if(Build.VERSION.SDK_INT>=26) native.setChannelId(CHANNEL)
            native.setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Audio de Arachn0de").setContentText(if(s.paused) "En pausa" else "Reproduciendo").setContentIntent(open).setVisibility(Notification.VISIBILITY_PRIVATE).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(Notification.Action.Builder(if(s.paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,if(s.paused) "Reproducir" else "Pausar",command("toggle")).build())
                .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Cerrar",command("close")).build())
                .setStyle(Notification.MediaStyle().setMediaSession(media?.sessionToken).setShowActionsInCompactView(0,1)).build()
        }
        return result
    }
    override fun onDestroy() {audio?.serviceDestroyed();if(awake?.isHeld==true) awake?.release();scope.cancel();media?.release();runCatching {unregisterReceiver(noisy)};stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()}
}
