package com.r0ybt.arachn0de.pomodoro

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.MainActivity
import com.r0ybt.arachn0de.R
import kotlinx.coroutines.*

internal object PomodoroAlarms {
    private const val REQUEST=917
    private fun pending(context:Context)=PendingIntent.getBroadcast(context,REQUEST,Intent(context,PomodoroReceiver::class.java).setAction("com.r0ybt.arachn0de.POMODORO"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(context:Context,state:PomodoroState,now:PomodoroTime) {
        val manager=context.getSystemService(AlarmManager::class.java)
        val intent=pending(context)
        manager.cancel(intent)
        val s=state.session
        if(s==null || !state.visible) {clearNotice(context);return}
        ongoing(context,s,now)
        if(s.status==PomodoroStatus.RUNNING && !s.manualEnd) manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,now.elapsed+s.remainingAt(now).coerceAtLeast(1),intent)
    }
    private fun ongoing(context:Context,s:PomodoroSession,now:PomodoroTime) {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
        val manager=context.getSystemService(NotificationManager::class.java)
        val channel="pomodoro_progress"
        if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel(channel,"Pomodoro · temporizador",NotificationManager.IMPORTANCE_LOW).apply {setSound(null,null)})
        val open=PendingIntent.getActivity(context,REQUEST,Intent(context,MainActivity::class.java).putExtra("pomodoro",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val rest=s.remainingAt(now)
        val running=s.status==PomodoroStatus.RUNNING && !s.manualEnd
        val notification=NotificationCompat.Builder(context,channel).setSmallIcon(R.drawable.lucide_apple)
            .setContentTitle("Pomodoro · ${s.phase.label}")
            .setContentText("${s.status.label} · ${s.number}/${s.cycle}"+if(running) "" else " · ${pomodoroTimeText(rest)}")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setWhen(System.currentTimeMillis()+rest).setUsesChronometer(running).setChronometerCountDown(true)
            .setShowWhen(running).build()
        runCatching {manager.notify(REQUEST,notification)}
    }
    fun clearNotice(context:Context) {context.getSystemService(NotificationManager::class.java).cancel(REQUEST)}
    fun notify(context:Context,finished:PomodoroPhase,next:PomodoroPhase) {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
        val manager=context.getSystemService(NotificationManager::class.java)
        if(!manager.areNotificationsEnabled()) return
        // Sound has a single media owner; the notification must not play Android's default too.
        val channel="pomodoro_transitions_media_v1"
        if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel(channel,"Pomodoro · fases",NotificationManager.IMPORTANCE_DEFAULT).apply {setSound(null,null)})
        val open=PendingIntent.getActivity(context,REQUEST,Intent(context,MainActivity::class.java).putExtra("pomodoro",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,channel).setSmallIcon(R.drawable.lucide_apple)
            .setContentTitle("Pomodoro · ${finished.label} finalizada")
            .setContentText("${next.label} lista para iniciar").setContentIntent(open).setAutoCancel(true)
            .setSilent(true).build()
        runCatching {manager.notify(REQUEST,notification)}
    }
}

/** Inexact one-shot wakeup; neither an always-running service nor an exact-alarm permission. */
class PomodoroReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action !in setOf("com.r0ybt.arachn0de.POMODORO",Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                val timer=(context.applicationContext as Arachn0deApplication).pomodoro
                timer.refresh()
                PomodoroAlarms.schedule(context,timer.state.value,pomodoroClock(context))
            } finally {pending.finish()}
        }
    }
}
internal object PomodoroNavigation {val requested=kotlinx.coroutines.flow.MutableStateFlow(false)}
