package com.r0ybt.arachn0de.report

import android.graphics.*
import android.net.Uri
import android.os.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.r0ybt.arachn0de.MainActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.*
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/** Requires a fresh disposable AVD. Refuses stores not created by this fixture. */
@RunWith(AndroidJUnit4::class)
class CardShareSessionTest {
    private val app get()=ApplicationProvider.getApplicationContext<Arachn0deApplication>()
    private val password="seg05-only-synthetic-2026"
    private suspend fun open():VaultSession {
        val guard=File(app.filesDir,"seg05-owned")
        if(!app.security.configured) {app.security.setup(password.toCharArray());guard.writeText("synthetic")}
        else {require(guard.exists());if(app.security.current.value==null)app.security.unlock(password.toCharArray())}
        return app.security.current.value!!
    }
    @Before fun before()=runBlocking<Unit> {open().context.getSharedPreferences(ScreenCapturePreference.NAME,0).edit().remove(AutoLockPreference.KEY).commit()}
    @After fun after()=runBlocking<Unit> {app.security.lock()}
    private fun image():ByteArray {
        val b=Bitmap.createBitmap(1024,512,Bitmap.Config.ARGB_8888);val random=java.util.Random(5);val pixels=IntArray(1024*512) {0xff000000.toInt() or (random.nextInt() and 0xffffff)}
        b.setPixels(pixels,0,1024,0,0,1024,512)
        return try {ByteArrayOutputStream().also {check(b.compress(Bitmap.CompressFormat.PNG,100,it))}.toByteArray()}finally {b.recycle()}
    }
    private fun card(s:VaultSession,bytes:ByteArray):Pair<File,Uri> {
        val f=File(File(s.context.cacheDir,CardShareFiles.DIRECTORY).apply {mkdirs()},"arachn0de-card-${UUID.randomUUID()}.png");SecureFiles.write(f,bytes)
        return f to Uri.parse("content://${app.packageName}.reports/v1/${s.access.id}/${f.name}")
    }
    private fun <T> task(name:String,work:()->T):FutureTask<T> = FutureTask(Callable(work)).also {Thread(it,name).apply {isDaemon=true;start()}}
    private fun record(mode:String,lockMs:Long) {val root=File(app.cacheDir,"seg05-results").apply {mkdirs()};File(root,"events.jsonl").appendText(JSONObject().put("mode",mode).put("lockMs",lockMs).toString()+"\n")}
    @Test(timeout=90000) fun stalledAndSlowRecipientsCannotDelayRevocationOrOwnTheFileMutex()=runBlocking<Unit> {
        val bytes=image()
        for(mode in listOf("complete","unread","slow","partial","closed")) {
            val s=open();val (file,uri)=card(s,bytes);val encrypted=file.readBytes();val input=app.contentResolver.openInputStream(uri)!!
            assertEquals("image/png",app.contentResolver.getType(uri))
            assertTrue(runCatching {app.contentResolver.openFileDescriptor(uri,"w")!!.close()}.isFailure)
            val stop=AtomicBoolean();var reader:FutureTask<Unit>?=null
            try {
                if(mode=="complete")assertArrayEquals(bytes,input.readBytes())
                if(mode=="slow")reader=task("seg05-slow-reader") {try {while(!stop.get() && input.read(ByteArray(512))>=0)Thread.sleep(50)}catch(_:Exception) {}}
                if(mode=="partial") {input.read(ByteArray(512));input.close()}
                if(mode=="closed")input.close()
                delay(200)
                withTimeout(350) {AttachmentRepository.fileOperations.withLock {}}
                assertArrayEquals(encrypted,file.readBytes())
                val start=SystemClock.elapsedRealtime();val closed=task("seg05-lock") {runBlocking {app.security.lock()}}
                // Keep unread/slow readers OPEN until closure, revocation and worker exit are proved.
                closed.get(1500,TimeUnit.MILLISECONDS);val elapsed=SystemClock.elapsedRealtime()-start
                assertNull(app.security.current.value)
                assertTrue(runCatching {s.access.withKey {it.size}}.isFailure)
                assertTrue(runCatching {s.access.retain(AutoCloseable {})}.isFailure)
                assertTrue(runCatching {app.contentResolver.openFileDescriptor(uri,"r")!!.close()}.isFailure)
                withTimeout(1000) {while(Thread.getAllStackTraces().keys.any {it.isAlive && it.name=="private-card-share"})delay(10)}
                record(mode,elapsed)
            } finally {stop.set(true);runCatching {input.close()};reader?.let {runCatching {it.get(2,TimeUnit.SECONDS)}}}
        }
    }
    @Test(timeout=30000) fun keyAdmissionClosesBeforeWaitingForTheFileMutex()=runBlocking<Unit> {
        val s=open();val (_,uri)=card(s,image());val held=CountDownLatch(1);val release=CountDownLatch(1)
        val blocker=task("seg05-local-file-owner") {runBlocking {AttachmentRepository.fileOperations.withLock {held.countDown();check(release.await(8,TimeUnit.SECONDS))}}}
        assertTrue(held.await(2,TimeUnit.SECONDS));var closing:FutureTask<Unit>?=null;var request:FutureTask<Boolean>?=null
        try {
            closing=task("seg05-pending-lock") {runBlocking {app.security.lock()}}
            withTimeout(1500) {while(runCatching {s.access.checkOpen()}.isSuccess)delay(5)}
            assertTrue(runCatching {s.access.withKey {it.size}}.isFailure)
            assertTrue(runCatching {s.access.retain(AutoCloseable {})}.isFailure)
            request=task("seg05-new-private-request") {runCatching {app.contentResolver.openFileDescriptor(uri,"r")!!.close()}.isFailure}
            assertTrue(request.get(1000,TimeUnit.MILLISECONDS))
            assertFalse(closing.isDone)
        } finally {release.countDown();blocker.get(2,TimeUnit.SECONDS);closing?.get(2,TimeUnit.SECONDS);request?.get(2,TimeUnit.SECONDS)}
    }
    @Test(timeout=30000) fun detachedSnapshotSurvivesCacheDiscardAndManualUnlockedPolicyIsPreserved()=runBlocking<Unit> {
        val s=open();val bytes=image();val (file,uri)=card(s,bytes)
        val prefs=s.context.getSharedPreferences(ScreenCapturePreference.NAME,0);prefs.edit().putLong(AutoLockPreference.KEY,AutoLockPreference.MANUAL).commit()
        val input=app.contentResolver.openInputStream(uri)!!
        try {
            CardShareFiles(s.context).discard(file);assertFalse(file.exists())
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                delay(700)
                scenario.onActivity {assertTrue(it.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0)}
                val command=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 187")
                ParcelFileDescriptor.AutoCloseInputStream(command).use {it.readBytes()};delay(400)
                assertSame(s,app.security.current.value);s.access.withKey {assertEquals(32,it.size)}
                app.security.background();delay(250);app.security.foreground();assertSame(s,app.security.current.value)
                assertArrayEquals(bytes,input.readBytes());assertTrue(runCatching {app.contentResolver.openFileDescriptor(uri,"r")!!.close()}.isFailure)
            }
            assertEquals(AutoLockPreference.MANUAL,AutoLockPreference.read(prefs));s.access.checkOpen()
        } finally {input.close()}
    }
    @Test(timeout=30000) fun corruptedCardFailsBeforeAnyDeliveryAndNextValidShareWorks()=runBlocking<Unit> {
        val s=open();val bytes=image();val (file,uri)=card(s,bytes);val encrypted=file.readBytes()
        file.writeBytes(encrypted.copyOf().apply {this[lastIndex]=(last().toInt() xor 1).toByte()})
        repeat(5) {assertTrue(runCatching {app.contentResolver.openInputStream(uri)!!.use {it.readBytes()}}.isFailure)}
        file.writeBytes(encrypted);app.contentResolver.openInputStream(uri)!!.use {assertArrayEquals(bytes,it.readBytes())}
    }
    @Test(timeout=30000) fun stalledDeliveryExpiresWithoutLockingTheVault()=runBlocking<Unit> {
        val s=open();val (file,_)=card(s,image());val clock=java.util.concurrent.atomic.AtomicLong()
        val delivery=AttachmentRepository.fileOperations.withLock {CardShareDelivery.prepare(file,s.access,clock::get) {s.access.checkOpen();SecureFiles.requireActive(file)}}
        val fd=delivery.start()
        try {
            delay(250);clock.set(60001)
            withTimeout(1000) {while(Thread.getAllStackTraces().keys.any {it.isAlive && it.name=="private-card-share"})delay(10)}
            assertSame(s,app.security.current.value);s.access.withKey {assertEquals(32,it.size)}
        } finally {delivery.close();fd.close()}
    }

    @Test(timeout=30000) fun visibleActivityLocksWithItsRecipientStillOpen()=runBlocking<Unit> {
        val s=open();val (_,uri)=card(s,image())
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            delay(700);val input=app.contentResolver.openInputStream(uri)!!
            try {
                val closing=task("seg05-visible-lock") {runBlocking {app.security.lock()}}
                closing.get(1500,TimeUnit.MILLISECONDS);delay(500)
                assertNull(app.security.current.value);assertTrue(runCatching {s.access.withKey {it.size}}.isFailure)
                scenario.onActivity {assertTrue(it.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0)}
            } finally {input.close()}
        }
    }

}
