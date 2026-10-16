package com.r0ybt.arachn0de.metro

import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.MetroLearningEventEntity
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28])
class MetroAdaptiveServiceTest {
    private val app get()=RuntimeEnvironment.getApplication() as Arachn0deApplication
    @After fun close() {app.database.close()}
    @Test fun notificationDisplaysTheSameFrozenAdaptedRemainingTimeAsTheScreenEngine()=runBlocking<Unit> {
        val repo=app.metroRepository;val p=repo.snapshot().preferences
        val route=MetroPlanner.plan(p.planningNetwork,listOf("los-heroes","republica"),false,p.restrictions)!!
        val id=repo.savePlan(route)
        repeat(30) {i->repeat(2) {j->
            val step=route.steps.single()
            val e=MetroCapture(route,if(j==0) step.from else step.to,j,step.line,step.direction,step.service,MetroCaptureKind.DOORS,
                MetroTime(1_000_000+i*1_000_000L+j*60_000,100_000+j*60_000L,1),"00000000-0000-0000-0000-000000000001",false,0,true,null,0,120_000)
            app.database.metroLearningDao().insert(MetroLearningEventEntity("train-$i-$j","training-$i",j.toLong(),MetroLearningCodec.encode(e)))
        }}
        repo.begin(id,repo.snapshot().journeys.single().row.revision,metroTime(app))
        val s=repo.snapshot().journeys.single().data.active!!;assertEquals(60_000L,s.route.totalMillis)
        val controller=Robolectric.buildService(MetroTrackingService::class.java).create();val service=controller.get()
        try {
            service.onStartCommand(Intent(app,MetroTrackingService::class.java),0,1)
            val manager=app.getSystemService(NotificationManager::class.java)
            val limit=System.nanoTime()+10_000_000_000
            var notification:android.app.Notification?=null
            while(System.nanoTime()<limit) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                notification=Shadows.shadowOf(manager).getNotification(843)
                if(notification?.extras?.getCharSequence("android.text")?.contains("≈ 1 min hasta República")==true) break
                Thread.sleep(20)
            }
            val text=notification!!.extras.getCharSequence("android.text").toString()
            assertTrue(text,text.contains(MetroEstimates.remainingText(s,metroTime(app),p.network)))
            assertTrue(repo.learningEvents().all {it.sessionId!=s.id})
            val cancelTime=metroTime(app)
            repo.tracking(id,repo.snapshot().journeys.single().row.revision) {MetroTracking.cancel(it,cancelTime)}
            val stopLimit=System.nanoTime()+10_000_000_000
            while(System.nanoTime()<stopLimit && !Shadows.shadowOf(service).isStoppedBySelf) {
                Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(20)
            }
            assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
            assertNull(Shadows.shadowOf(manager).getNotification(843))
            assertTrue(repo.snapshot().journeys.single().data.sessions.last().cancelled)
            assertTrue(repo.learningEvents().all {it.sessionId!=s.id})
        } finally {controller.destroy()}
    }
}
