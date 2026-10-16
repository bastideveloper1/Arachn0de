package com.r0ybt.arachn0de.sharing

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import com.r0ybt.arachn0de.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=SharingTestApplication::class,sdk=[28])
class SharedActivityTest {
    private lateinit var controller:ActivityController<MainActivity>
    private val app get()=controller.get().application as Arachn0deApplication
    @Before fun start() {controller=Robolectric.buildActivity(MainActivity::class.java).setup()}
    private fun deliver(intent:Intent) {controller.newIntent(intent);shadowOf(Looper.getMainLooper()).idle()}
    private fun await(block:()->Boolean) {
        val until=System.nanoTime()+20_000_000_000L
        while(!block()) {
            check(System.nanoTime()<until) {"La recepción no terminó en 20 segundos"}
            shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10)
        }
    }
    private fun disposeContent() {
        val root=controller.get().findViewById<android.view.ViewGroup>(android.R.id.content)
        for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
        root.removeAllViews()
    }
    @After fun close() {
        val application=app
        disposeContent()
        controller.pause().stop().destroy()
        val closing=CoroutineScope(Dispatchers.Default).async {application.security.lock()}
        await {closing.isCompleted};runBlocking {closing.await()}
    }
    @Test fun receivedShareAndEditedJournalSurviveActivityRecreationWithoutRedelivery() {
        val queue=app.security.current.value!!.sharedCaptures
        val incoming=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,"Compartido").putExtra(Intent.EXTRA_TEXT,"https://example.com")
        deliver(incoming)
        await {runBlocking {queue.observe().first().size==1}}
        val row=runBlocking {queue.observe().first().single()}
        runBlocking {queue.update(row.id,"Editado","Texto conservado","project:destino")}
        val state=Bundle();controller.saveInstanceState(state)
        assertTrue(state.getBoolean("share_consumed"))
        assertFalse(state.toString().contains("https://example.com"))
        // Android recreation receives the original Intent plus the saved instance state.
        val configuration=android.content.res.Configuration(controller.get().resources.configuration)
        configuration.orientation=android.content.res.Configuration.ORIENTATION_LANDSCAPE
        controller.configurationChange(configuration)
        shadowOf(Looper.getMainLooper()).idle()
        val recovered=runBlocking {queue.observe().first().single()}
        assertEquals(row.id,recovered.id);assertEquals("Editado",recovered.title)
        assertEquals("Texto conservado",recovered.description);assertEquals("project:destino",recovered.destination)
    }
    @Test fun lockedShareIsRejectedWithoutContentInMessagesOrPendingStorage() {
        val session=app.security.current.value!!
        val result=CoroutineScope(Dispatchers.Default).async {app.security.lock()}
        await {result.isCompleted};runBlocking {result.await()}
        deliver(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"PRIVATE-SHARE-NEVER-DISPLAY"))
        assertNull(app.security.current.value)
        assertEquals("Desbloquea Arachn0de y vuelve a compartir el contenido.",org.robolectric.shadows.ShadowToast.getTextOfLatestToast())
        assertTrue(runCatching {runBlocking {session.sharedCaptures.observe().first()}}.isFailure)
    }
}
