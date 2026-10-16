package com.r0ybt.arachn0de.sharing

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.MainActivity
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application=SharingTestApplication::class,sdk=[28])
class SharedExternalEntryTest {
    private val app get()=ApplicationProvider.getApplicationContext<SharingTestApplication>()
    private var controller:ActivityController<MainActivity>?=null
    private fun await(block:()->Boolean) {
        val until=System.nanoTime()+20_000_000_000L
        while(!block()) {
            check(System.nanoTime()<until) {"La entrada externa no terminó en 20 segundos"}
            shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10)
        }
    }
    @After fun close() {
        controller?.let {c->
            val root=c.get().findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews();c.pause().stop().destroy()
        }
        val manager=app.security
        val closing=CoroutineScope(Dispatchers.Default).async {manager.lock()}
        await {closing.isCompleted};runBlocking {closing.await()}
    }
    @Test fun androidResolvesSupportedSharesToExportedMainActivityOnly() {
        assertTrue(SHARE_ENABLED)
        for(mime in listOf("text/plain","image/png","image/jpeg")) {
            val intent=Intent(Intent.ACTION_SEND).setType(mime).setPackage(app.packageName)
            val matches=app.packageManager.queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY)
            assertEquals(listOf(MainActivity::class.java.name),matches.map {it.activityInfo.name})
            assertTrue(matches.single().activityInfo.exported)
        }
        for(intent in listOf(Intent(Intent.ACTION_SEND).setType("application/pdf"),Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/png"))) {
            assertTrue(app.packageManager.queryIntentActivities(intent.setPackage(app.packageName),PackageManager.MATCH_DEFAULT_ONLY).isEmpty())
        }
    }
    @Test fun onCreateReceivesTextLinkAndNewIntentReceivesPngAndJpeg() {
        val session=app.security.current.value!!;val queue=session.sharedCaptures
        // ShadowLinux cannot fsync directories; use the existing host-fsync fixture.
        org.robolectric.util.ReflectionHelpers.setField(session.attachments,"store",
            com.r0ybt.arachn0de.data.local.AttachmentStore(session.context,com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory))
        controller=Robolectric.buildActivity(MainActivity::class.java,Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"https://example.com/desde-otra-app")).setup()
        await {runBlocking {queue.observe().first().size==1}}
        assertEquals("https://example.com/desde-otra-app",runBlocking {queue.observe().first().single().description})
        for((index,format) in listOf(Bitmap.CompressFormat.PNG,Bitmap.CompressFormat.JPEG).withIndex()) {
            val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
            val bytes=ByteArrayOutputStream().also {bitmap.compress(format,90,it);bitmap.recycle()}.toByteArray()
            val uri=Uri.parse("content://external-sharing/image-$index")
            shadowOf(app.contentResolver).registerInputStream(uri,ByteArrayInputStream(bytes))
            val intent=Intent(Intent.ACTION_SEND).setType(if(index==0) "image/png" else "image/jpeg").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            controller!!.newIntent(intent)
            await {runBlocking {queue.observe().first().size==index+2}}
            val row=runBlocking {queue.observe().first().last()}
            assertTrue(row.image)
            assertEquals(1,runBlocking {session.attachments.pendingImports(row.id).size})
        }
    }
}
