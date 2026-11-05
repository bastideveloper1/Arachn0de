package com.r0ybt.arachn0de.audio

import android.media.MediaPlayer
import android.os.Looper
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.AttachmentFileEntity
import com.r0ybt.arachn0de.security.SecureFiles
import com.r0ybt.arachn0de.ui.NavigationWorkApplication
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class) @Config(application=NavigationWorkApplication::class,sdk=[28])
class AudioIdleTest {
    private val app get()=RuntimeEnvironment.getApplication() as NavigationWorkApplication
    private fun withUiLoop(block:suspend CoroutineScope.()->Unit)=runBlocking<Unit> {
        val work=async(Dispatchers.IO) {block()}
        withTimeout(30_000) {while(!work.isCompleted) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(10));delay(10)
        };work.await()}
    }
    private class Player:MediaPlayer() {
        @Volatile var position=0
        lateinit var reads:AtomicInteger
            private set
        init {reads=AtomicInteger()}
        @Volatile var released=false
        // MediaPlayer calls this during its constructor, before subclass fields are initialized.
        override fun getCurrentPosition():Int {if(::reads.isInitialized) reads.incrementAndGet();return position}
        override fun start() {}
        override fun pause() {}
        override fun seekTo(msec:Int) {position=msec}
        override fun release() {released=true;super.release()}
    }
    @Test fun pausedPlayerDoesNotPollAndPlaybackPauseSeekCloseAndLockSavePosition()=withUiLoop {
        val session=app.security.current.value!!;val audio=session.audio
        val row=AttachmentFileEntity("audio-test","audio-test.aac","Audio","audio/aac",100,0,0,"0".repeat(64),0,durationMs=10_000)
        session.database.attachmentDao().insert(row)
        withTimeout(5000) {while(!audio.canStartRecording) delay(10)}
        val media=withContext(Dispatchers.Main) {Player()}
        try {
            withContext(Dispatchers.Main) {
                AudioController::class.java.getDeclaredField("player").apply {isAccessible=true}.set(audio,media)
                audio.state.value=AudioState(fileId=row.id,paused=true,durationMs=row.durationMs)
                AudioController::class.java.getDeclaredMethod("startPlaybackUpdates",MediaPlayer::class.java).apply {isAccessible=true}.invoke(audio,media)
            }
            delay(650);assertEquals(0,media.reads.get())
            media.position=1200;withContext(Dispatchers.Main) {audio.toggle()}
            withTimeout(5000) {while(session.database.attachmentDao().get(row.id)!!.playbackMs!=1200L) delay(20)}
            assertTrue(media.reads.get()>=2)
            media.position=1350;withContext(Dispatchers.Main) {audio.pause()}
            assertEquals(1350L,session.database.attachmentDao().get(row.id)!!.playbackMs)
            val pausedReads=media.reads.get();delay(650);assertEquals(pausedReads,media.reads.get())
            withContext(Dispatchers.Main) {audio.seek(500);audio.toggle()}
            assertEquals(500L,session.database.attachmentDao().get(row.id)!!.playbackMs)
            media.position=1700;withContext(Dispatchers.Main) {audio.closePlayback(switching=true)}
            assertEquals(1700L,session.database.attachmentDao().get(row.id)!!.playbackMs);assertTrue(media.released)
            val replacement=withContext(Dispatchers.Main) {Player().apply {position=1900}}
            withContext(Dispatchers.Main) {
                AudioController::class.java.getDeclaredField("player").apply {isAccessible=true}.set(audio,replacement)
                AudioController::class.java.getDeclaredMethod("startPlaybackUpdates",MediaPlayer::class.java).apply {isAccessible=true}.invoke(audio,replacement)
            }
            app.security.lock()
            assertNull(app.security.current.value);assertTrue(audio.isClosed);assertFalse(audio.state.value.visible)
            assertTrue(replacement.released)
            val afterLock=replacement.reads.get();delay(650);assertEquals(afterLock,replacement.reads.get())
            assertTrue(runCatching {session.access.checkOpen()}.isFailure)
        } finally {if(app.security.current.value!=null) app.security.lock()}
    }

    @Test fun identicalPositionsDoNotUpdateRowsAndFinalizedEncryptedAudioRemainsReadable()=withUiLoop {
        val session=app.security.current.value!!
        try {
            val project=session.projects.createProject("Audio")
            val node=session.nodes.createNode(project.id,null,"Grabación")
            val drafts=AudioDraftStore(session.context,session.database,BackupFixture::syncDirectory)
            val tone=javaClass.classLoader!!.getResourceAsStream("audio/tone.aac")!!.use {it.readBytes()}
            val id=drafts.begin(node.id);drafts.append(id,0,tone);val row=drafts.finish(id)!!
            val db=session.database.openHelper.writableDatabase
            db.execSQL("CREATE TABLE position_writes (n INTEGER NOT NULL)");db.execSQL("INSERT INTO position_writes VALUES (0)")
            db.execSQL("CREATE TRIGGER count_position_writes AFTER UPDATE OF playbackMs ON attachment_files BEGIN UPDATE position_writes SET n=n+1; END")
            val dao=session.database.attachmentDao()
            dao.playback(id,0);dao.playback(id,64);dao.playback(id,64);dao.playback(id,128);dao.playback(id,128)
            db.query("SELECT n FROM position_writes").use {it.moveToFirst();assertEquals(2,it.getInt(0))}
            assertEquals(128L,dao.get(id)!!.playbackMs)
            assertArrayEquals(tone,SecureFiles.read(session.attachments.localFile(row)))
        } finally {app.security.lock()}
    }
}
