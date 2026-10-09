package com.r0ybt.arachn0de.metro

import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import com.r0ybt.arachn0de.Arachn0deApplication
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28])
class MetroServiceTest {
    private val app get()=RuntimeEnvironment.getApplication() as Arachn0deApplication
    @After fun close() {app.database.close()}
    private fun await(test: ()->Boolean) {val limit=System.nanoTime()+10_000_000_000;while(System.nanoTime()<limit) {Shadows.shadowOf(Looper.getMainLooper()).idle();if(test())return;Thread.sleep(20)};assertTrue(test())}
    @Test fun persistentNotificationPausesResumesOpensAndStopsAfterArrival()=runBlocking<Unit> {
        val repo=app.metroRepository;val net=repo.snapshot().preferences.network
        val route=MetroPlanner.plan(net,listOf("los-heroes","tobalaba"),false,MetroRestrictions())!!
        val id=repo.savePlan(route);repo.begin(id,repo.snapshot().journeys.single().row.revision,metroTime(app))
        val controller=Robolectric.buildService(MetroTrackingService::class.java).create();val service=controller.get()
        try {
            service.onStartCommand(Intent(app,MetroTrackingService::class.java),0,1)
            val manager=app.getSystemService(NotificationManager::class.java)
            await {Shadows.shadowOf(manager).getNotification(843)?.actions?.size==2}
            var notification=Shadows.shadowOf(manager).getNotification(843)
            assertEquals("Metro detenido",notification.actions[0].title)
            val pause=Shadows.shadowOf(notification.actions[0].actionIntent).savedIntent
            assertEquals(MetroTrackingService::class.java.name,pause.component!!.className)
            service.onStartCommand(pause,0,2)
            await {runBlocking {repo.snapshot().journeys.single().data.active!!.pausedAt!=null}}
            await {Shadows.shadowOf(manager).getNotification(843)?.actions?.firstOrNull()?.title=="Reanudar"}
            notification=Shadows.shadowOf(manager).getNotification(843)
            val open=Shadows.shadowOf(notification.actions[1].actionIntent).savedIntent;assertTrue(open.getBooleanExtra("metro",false))
            val resume=Shadows.shadowOf(notification.actions[0].actionIntent).savedIntent;service.onStartCommand(resume,0,3)
            await {runBlocking {repo.snapshot().journeys.single().data.active!!.pausedAt==null}}
            val j=repo.snapshot().journeys.single();repo.tracking(id,j.row.revision) {MetroTracking.finish(it,metroTime(app))}
            await {Shadows.shadowOf(service).isStoppedBySelf}
            assertNull(Shadows.shadowOf(manager).getNotification(843))
        } finally {controller.destroy()}
    }
    @Test fun stickyRecoveryReadsRoomRatherThanStartingANewSession()=runBlocking<Unit> {
        val repo=app.metroRepository;val net=repo.snapshot().preferences.network
        val id=repo.savePlan(MetroPlanner.plan(net,listOf("los-heroes","tobalaba"),false,MetroRestrictions())!!)
        repo.begin(id,repo.snapshot().journeys.single().row.revision,metroTime(app));val session=repo.snapshot().journeys.single().data.active!!.id
        val first=Robolectric.buildService(MetroTrackingService::class.java).create();first.get().onStartCommand(null,0,1);first.destroy()
        val second=Robolectric.buildService(MetroTrackingService::class.java).create()
        try {second.get().onStartCommand(null,0,2);await {Shadows.shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(843)?.actions?.size==2};assertEquals(session,repo.snapshot().journeys.single().data.active!!.id)} finally {second.destroy()}
    }
}
