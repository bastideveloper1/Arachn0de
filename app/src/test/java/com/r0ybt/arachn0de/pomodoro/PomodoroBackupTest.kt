package com.r0ybt.arachn0de.pomodoro

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class)
class PomodoroBackupTest {
    @Test fun encryptedRoundTripValidatesBeforeReplaceAndHistoricalAbsenceResetsRuntime()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences(PomodoroController.CACHE,0).edit().clear().commit()
        val id=UUID.randomUUID();val root=File(context.filesDir,"pomodoro-$id").apply {mkdirs()}
        val access=SecureFiles.Session(id,root,VaultCrypto.randomKey());SecureFiles.register(access)
        val vault=VaultContext(context,access)
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build();vault.database=db
        val now=PomodoroTime(1_800_000_000_000,100_000,7)
        val controller=PomodoroController(context) {now}
        try {
            val storage=vault.getSharedPreferences(PomodoroCodec.NAME,0)
            val sounds=vault.getSharedPreferences(PomodoroSoundCodec.NAME,0)
            val tick=context.resources.openRawResource(com.r0ybt.arachn0de.R.raw.pomodoro_clock).use {it.readBytes()}
            val bell=context.resources.openRawResource(com.r0ybt.arachn0de.R.raw.pomodoro_bell).use {it.readBytes()}
            sounds.edit().putString("ticking",PomodoroSoundCodec.pack(tick)).putString("completion",PomodoroSoundCodec.pack(bell)).commit()
            controller.attach(storage,sounds);controller.configure(PomodoroConfig(10,2,8,3,true,false,PomodoroAppearance.NIGHT_BLUE));controller.start();controller.pause();controller.adjust(5);controller.adjust(5)
            val saved=controller.state.value
            val backup=BackupRepository(db,vault,BackupAvatarFiles(vault,BackupFixture::syncDirectory))
            val archive=backup.create("pomodoro-backup-fixture".toCharArray())
            val candidate=backup.inspect(SecureFiles.input(archive),"pomodoro-backup-fixture".toCharArray())
            controller.finish();controller.configure(PomodoroConfig())
            sounds.edit().clear().commit()
            backup.restore(candidate);backup.discard(candidate);backup.discardPending(archive.name)
            assertArrayEquals(tick,PomodoroSoundCodec.unpack(sounds.getString("ticking",null)!!))
            assertArrayEquals(bell,PomodoroSoundCodec.unpack(sounds.getString("completion",null)!!))
            assertEquals(saved,controller.state.value)
            assertEquals(saved,PomodoroCodec.decode(storage.getString(PomodoroCodec.KEY,null)!!))
            // A later cache from a discarded runtime must never replace the restored private checkpoint.
            val stale=PomodoroController(context) {now};stale.finish();stale.configure(PomodoroConfig(90,10,30,8))
            val reopened=PomodoroController(context) {now};reopened.attach(storage)
            assertEquals(saved,reopened.state.value);reopened.attach(null)
            val before=backup.snapshot()
            val bad=before.copy(privatePreferences=listOf(PrivatePreferenceEntity(PomodoroCodec.NAME,EncryptedPreferences.encode(mapOf(PomodoroCodec.KEY to org.json.JSONObject(PomodoroCodec.encode(saved)).apply {getJSONObject("config").put("appearance","INVALID")}.toString())))))
            assertTrue(runCatching {backup.restore(bad)}.isFailure)
            assertEquals(saved,controller.state.value)
            assertEquals(before.privatePreferences,backup.snapshot().privatePreferences)
            val badSound=before.copy(privatePreferences=before.privatePreferences.map {
                if(it.name==PomodoroSoundCodec.NAME) it.copy(payload=EncryptedPreferences.encode(mapOf("ticking" to org.json.JSONObject(PomodoroSoundCodec.pack(tick)).put("sha256","invalid").toString()))) else it
            })
            assertTrue(runCatching {backup.restore(badSound)}.isFailure)
            assertEquals(before.privatePreferences,backup.snapshot().privatePreferences)
            controller.sounds.reset("ticking");assertFalse(controller.sounds.selected("ticking"));assertTrue(controller.sounds.selected("completion"))
            sounds.edit().putString("ticking",PomodoroSoundCodec.pack(bell)).commit()
            assertArrayEquals(bell,PomodoroSoundCodec.unpack(sounds.getString("ticking",null)!!))
            backup.restore(before.copy(privatePreferences=emptyList()))
            assertFalse(controller.sounds.selected("ticking"));assertFalse(controller.sounds.selected("completion"))
            assertEquals(PomodoroState(),controller.state.value)
            assertEquals(PomodoroState(),PomodoroController(context) {now}.state.value)
        } finally {controller.attach(null);db.close();vault.clearMemory();SecureFiles.revoke(access);root.deleteRecursively()}
    }
}
