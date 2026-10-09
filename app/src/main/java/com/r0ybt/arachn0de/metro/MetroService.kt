package com.r0ybt.arachn0de.metro

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch

/** User-initiated offline timer, not location/navigation hardware or background data sync. */
internal class MetroTrackingService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var observer: Job?=null
    override fun onBind(intent: Intent?) : IBinder? = null
    override fun onCreate() { super.onCreate(); if(Build.VERSION.SDK_INT>=26) {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Seguimiento Metro Beta",NotificationManager.IMPORTANCE_LOW))
    } }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        val placeholder=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_directions).setContentTitle("Metro · estimación offline Beta").setContentText("Recuperando seguimiento…").setOngoing(true).build()
        if(Build.VERSION.SDK_INT>=34) startForeground(NOTIFICATION,placeholder,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(NOTIFICATION,placeholder)
        val owner=(application as Arachn0deApplication).security.current.value
        if(owner==null) { stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY }
        val repository=owner.metro
        val action=intent?.action
        if((action==PAUSE || action==RESUME) && intent.getStringExtra("store")==owner.access.id.toString()) scope.launch {
            try {
                val row=repository.snapshot().journeys.firstOrNull { it.row.id==intent.getStringExtra("journey") }
                if(row!=null && row.data.active?.id==intent.getStringExtra("session")) {
                    repository.tracking(row.row.id,intent.getLongExtra("revision",-1)) { s -> if(action==PAUSE) MetroTracking.pause(s,metroTime(this@MetroTrackingService)) else MetroTracking.resume(s,metroTime(this@MetroTrackingService)) }
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { getSystemService(NotificationManager::class.java).notify(NOTIFICATION,NotificationCompat.Builder(this@MetroTrackingService,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_directions).setContentTitle("Metro: acción no aplicada").setContentText("Abre el seguimiento para reintentar.").setContentIntent(open()).setOngoing(true).build()) }
        }
        if(observer==null) observer=scope.launch {
            repository.observe().catch {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                getSystemService(NotificationManager::class.java).notify(844,NotificationCompat.Builder(this@MetroTrackingService,CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_directions).setContentTitle("Metro: abre la sesión para recuperarla")
                    .setContentText("No se pudo leer el seguimiento guardado.").setContentIntent(open()).setAutoCancel(true).build())
            }.collectLatest { snapshot ->
                val active=snapshot.journeys.firstOrNull { it.data.active!=null }
                if(active==null) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return@collectLatest }
                while(isActive) {
                    owner.access.checkOpen()
                    val session=active.data.active!!; val position=MetroTracking.position(session,metroTime(this@MetroTrackingService))
                    val station=MetroPresentation.positionText(session.route,position,snapshot.preferences.network)
                    val status=when { position.uncertain -> "Requiere confirmar posición"; session.pausedAt!=null -> "Estimación pausada"; position.waiting!=null -> "Parada o combinación pendiente"; else -> "Posición estimada" }
                    val command=if(session.pausedAt==null) PAUSE else RESUME
                    val pending=PendingIntent.getService(this@MetroTrackingService,command.hashCode(),Intent(this@MetroTrackingService,MetroTrackingService::class.java).setAction(command)
                        .putExtra("store",owner.access.id.toString()).putExtra("journey",active.row.id).putExtra("session",session.id).putExtra("revision",active.row.revision),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val notification=NotificationCompat.Builder(this@MetroTrackingService,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_directions)
                        .setContentTitle("Metro · $status").setContentText(station).setContentIntent(open()).setOngoing(true).setOnlyAlertOnce(true)
                        .addAction(android.R.drawable.ic_media_pause,if(command==PAUSE) "Metro detenido" else "Reanudar",pending)
                        .addAction(android.R.drawable.ic_menu_view,"Abrir seguimiento",open()).build()
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION,notification)
                    delay(30_000)
                }
            }
        }
        return START_STICKY
    }
    private fun open()=PendingIntent.getActivity(this,843,Intent(this,MainActivity::class.java).putExtra("metro",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        private const val CHANNEL="metro_tracking"
        private const val NOTIFICATION=843
        private const val PAUSE="com.r0ybt.arachn0de.metro.PAUSE"
        private const val RESUME="com.r0ybt.arachn0de.metro.RESUME"
        fun start(context: Context) = ContextCompat.startForegroundService(context,Intent(context,MetroTrackingService::class.java))
    }
}
internal object MetroNavigation {
    data class Request(val nodeId: String?,val token: Long=System.nanoTime())
    val requests=kotlinx.coroutines.flow.MutableStateFlow<Request?>(null)
    fun open(nodeId: String?=null) { requests.value=Request(nodeId) }
}
