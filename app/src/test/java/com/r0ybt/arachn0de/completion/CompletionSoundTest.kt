package com.r0ybt.arachn0de.completion

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.security.EncryptedPreferences
import com.r0ybt.arachn0de.ui.theme.*
import com.r0ybt.arachn0de.backup.legacy.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
class CompletionSoundTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private fun wav(ms:Int=65):ByteArray {
        val size=ms*16
        return ByteBuffer.allocate(44+size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray());putInt(36+size);put("WAVEfmt ".toByteArray());putInt(16)
            putShort(1);putShort(1);putInt(8000);putInt(16000);putShort(2);putShort(16)
            put("data".toByteArray());putInt(size);put(ByteArray(size))
        }.array()
    }
    private class Audio:CompletionAudio {
        var clip:String?=null;var plays=0
        override fun select(clip:String?) {this.clip=clip}
        override fun play() {plays++}
        override fun stop() {}
        override fun close() {}
    }

    @Test fun defaultMuteAndDuplicateEventsKeepOfficialFeedback() {
        val audio=Audio();val feedback=CompletionFeedback(audio)
        feedback.completed("first",AppearanceSettings());feedback.completed("first",AppearanceSettings())
        assertEquals(1,audio.plays);assertNull(audio.clip)
        feedback.completed("muted",AppearanceSettings(completionSoundEnabled=false));assertEquals(1,audio.plays)
        feedback.completed("next",AppearanceSettings(theme=AppearanceTheme.GreenBash));assertEquals(2,audio.plays);assertNull(audio.clip)
    }

    @Test fun statusTransitionsOnlyQueueActualCompletionsAndRollbackCannotPlay()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build()
        try {
            val projects=ProjectRepository(db.projectDao(),db)
            val project=projects.createProject("Sonidos");projects.setVerificationEnabled(project.id,true)
            val nodes=NodeRepository(db);val task=nodes.createNode(project.id,null,"Tarea")
            val events=mutableListOf<Pair<String,String>>()
            nodes.onCompletionCandidate={node,event->events.add(node to event)}
            nodes.cycleTaskStatus(task.id);assertEquals(TaskStatus.VERIFY,nodes.getNode(task.id)!!.taskStatus);assertTrue(events.isEmpty())
            nodes.cycleTaskStatus(task.id);assertEquals(1,events.size)
            assertTrue(nodes.completionStillCommitted(events[0].first,events[0].second))
            nodes.setCompleted(task.id,true);assertEquals(1,events.size)
            nodes.setTaskStatus(task.id,TaskStatus.PENDING);assertEquals(1,events.size)
            assertFalse(nodes.completionStillCommitted(events[0].first,events[0].second))
            assertTrue(runCatching {db.withTransaction {nodes.setCompleted(task.id,true);error("rollback")}}.isFailure)
            assertEquals(2,events.size)
            assertFalse(nodes.completionStillCommitted(events.last().first,events.last().second))
            assertFalse(nodes.getNode(task.id)!!.isCompleted)
        } finally {db.close()}
    }
    @Test fun legacySkinsStayIdenticalAndAudioSurvivesSkinAndBackupRoundTrip()=runBlocking<Unit> {
        val legacy=CustomTheme(name="Anterior")
        val oldRaw=CustomThemeCodec.encode(legacy)
        assertEquals(1,JSONObject(oldRaw).getInt("schema"));assertNull(CustomThemeCodec.decode(oldRaw).completionSound)
        val sound=CompletionSoundCodec.encode(wav())
        val theme=legacy.copy(completionSound=sound)
        val exported=CustomThemeCodec.encode(theme)
        assertEquals(4,JSONObject(exported).getInt("schema"))
        assertArrayEquals(wav(),CompletionSoundCodec.decode(CustomThemeCodec.decode(exported).completionSound!!))
        val pref=PrivatePreferenceEntity(AppearancePreferences.FILE,EncryptedPreferences.encode(mapOf(
            AppearancePreferences.COMPLETION_SOUND to false,CustomThemeCodec.ACTIVE to theme.id,
            CustomThemeCodec.LIBRARY to CustomThemeCodec.library(listOf(theme)))))
        val data=BackupFixture.empty().copy(privatePreferences=listOf(pref))
        val encoded=BackupJson.encode(data);assertEquals(46,JSONObject(String(encoded)).getInt("dataVersion"))
        assertEquals(listOf(pref),BackupJson.decode(encoded).privatePreferences)
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build()
        try {
            val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
            backup.restore(BackupJson.decode(encoded))
            assertEquals(pref,db.privatePreferenceDao().get(pref.name))
            val bad=pref.copy(payload=EncryptedPreferences.encode(mapOf(AppearancePreferences.COMPLETION_SOUND to "false")))
            assertTrue(runCatching {backup.restore(data.copy(privatePreferences=listOf(bad)))}.isFailure)
            assertEquals(pref,db.privatePreferenceDao().get(pref.name))
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_sound BEFORE INSERT ON private_preferences BEGIN SELECT RAISE(ABORT,'injected'); END")
            assertTrue(runCatching {backup.restore(BackupFixture.empty().copy(privatePreferences=listOf(pref.copy(payload=EncryptedPreferences.encode(mapOf(AppearancePreferences.COMPLETION_SOUND to true))))))}.isFailure)
            assertEquals(pref,db.privatePreferenceDao().get(pref.name))
        } finally {db.close()}
        assertEquals(oldRaw,CustomThemeCodec.encode(theme.copy(completionSound=null)))
    }
    @Test fun invalidOversizedCorruptAndLongSoundsAreRejectedWithoutReplacingTheTheme() {
        val raw=CompletionSoundCodec.encode(wav(3000))
        assertArrayEquals(wav(3000),CompletionSoundCodec.decode(raw))
        assertTrue(runCatching {CompletionSoundCodec.encode(wav(3001))}.isFailure)
        assertTrue(runCatching {CompletionSoundCodec.encode(ByteArray(512*1024+1))}.isFailure)
        val corrupt=JSONObject(raw).put("sha256","0".repeat(64)).toString()
        assertTrue(runCatching {CompletionSoundCodec.decode(corrupt)}.isFailure)
        assertTrue(runCatching {CustomThemeCodec.encode(CustomTheme(completionSound=corrupt))}.isFailure)
    }
}
