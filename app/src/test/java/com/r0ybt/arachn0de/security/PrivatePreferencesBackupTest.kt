package com.r0ybt.arachn0de.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
class PrivatePreferencesBackupTest {
    @Test fun annotationStylesRestoreInEmptyStoreAndRejectInvalidOrFailedReplacement()=runBlocking<Unit>(kotlinx.coroutines.Dispatchers.IO) {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build()
        try {
            val name=com.r0ybt.arachn0de.annotations.AnnotationStylePreferences.NAME
            val row=PrivatePreferenceEntity(name,EncryptedPreferences.encode(mapOf("color" to 0xff2196f3.toInt(),"width" to .014f)))
            db.privatePreferenceDao().save(row)
            val repo=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
            val data=BackupJson.decode(BackupJson.encode(repo.snapshot()))
            assertEquals(listOf(row),data.privatePreferences)
            db.clearAllTables();repo.restore(data)
            assertEquals(row,db.privatePreferenceDao().get(name))
            val values=EncryptedPreferences.decode(requireNotNull(db.privatePreferenceDao().get(name)).payload)
            val prefs=context.getSharedPreferences("annotation-restored-${java.util.UUID.randomUUID()}",0)
            prefs.edit().putInt("color",values.getValue("color") as Int).putFloat("width",values.getValue("width") as Float).commit()
            assertEquals(com.r0ybt.arachn0de.annotations.AnnotationStyle(0xff2196f3.toInt(),.014f),com.r0ybt.arachn0de.annotations.AnnotationStylePreferences(prefs).read())
            for(invalid in listOf(mapOf("color" to 12,"width" to .014f),mapOf("width" to .5f),mapOf("width" to "wide"),mapOf("extra" to true))) {
                val bad=data.copy(privatePreferences=listOf(row.copy(payload=EncryptedPreferences.encode(invalid))))
                assertTrue(runCatching {repo.restore(bad)}.isFailure)
                assertEquals(row,db.privatePreferenceDao().get(name))
            }
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_annotation_style BEFORE INSERT ON private_preferences WHEN NEW.name='image_annotation_style' BEGIN SELECT RAISE(ABORT,'test failure'); END")
            try {assertTrue(runCatching {repo.restore(data)}.isFailure)}
            finally {db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_annotation_style")}
            assertEquals(row,db.privatePreferenceDao().get(name))
            val old=BackupJson.decode(BackupJson.encode(BackupFixture.empty()))
            assertTrue(old.privatePreferences.isEmpty())
            val empty=context.getSharedPreferences("annotation-old-${java.util.UUID.randomUUID()}",0)
            assertEquals(com.r0ybt.arachn0de.annotations.AnnotationStyle(),com.r0ybt.arachn0de.annotations.AnnotationStylePreferences(empty).read())
        } finally {db.close()}
    }
    @Test fun restorationKeepsReceivingSecurityAndNeverImportsHistoricalAuthorizations()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build()
        try {
            val local=PrivatePreferenceEntity(ScreenCapturePreference.NAME,EncryptedPreferences.encode(mapOf(
                ScreenCapturePreference.KEY to false,AutoLockPreference.KEY to 0L)))
            val unsafe=local.copy(payload=EncryptedPreferences.encode(mapOf(
                ScreenCapturePreference.KEY to true,AutoLockPreference.KEY to AutoLockPreference.MANUAL)))
            val ordinary=PrivatePreferenceEntity("example",EncryptedPreferences.encode(mapOf("text" to "restored")))
            val repo=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
            val data=BackupFixture.empty().copy(privatePreferences=listOf(unsafe,ordinary))
            // Historical unencrypted v18 remains readable and retains its full backup payload.
            val candidate=repo.inspect(BackupFixture.archive(data).inputStream())
            assertTrue(candidate.privatePreferences.contains(unsafe))
            db.privatePreferenceDao().save(local)
            repo.discard(candidate) // Cancellation does not change local settings or data.
            assertEquals(listOf(local),db.privatePreferenceDao().all())
            val decoded=BackupJson.decode(BackupJson.encode(data))
            repo.restore(decoded)
            assertEquals(local,db.privatePreferenceDao().get(local.name))
            assertEquals(ordinary,db.privatePreferenceDao().get(ordinary.name))
            // Backups without security settings also preserve the receiving policy.
            repo.restore(BackupFixture.empty())
            assertEquals(listOf(local),db.privatePreferenceDao().all())
            // Even a less restrictive local choice belongs to the receiver, not the backup.
            db.privatePreferenceDao().save(unsafe)
            repo.restore(data.copy(privatePreferences=listOf(local)))
            assertEquals(unsafe,db.privatePreferenceDao().get(local.name))
            // A new store has no imported authorization and uses existing safe defaults.
            db.privatePreferenceDao().clear();repo.restore(decoded)
            assertNull(db.privatePreferenceDao().get(local.name))
            assertEquals(ordinary,db.privatePreferenceDao().get(ordinary.name))
            assertFalse(context.getSharedPreferences("new-safe-default",0).getBoolean(ScreenCapturePreference.KEY,false))
            assertNotEquals(AutoLockPreference.MANUAL,AutoLockPreference.read(context.getSharedPreferences("new-safe-default",0)))
            db.privatePreferenceDao().save(local)
            val cancelled=kotlinx.coroutines.Job().apply {cancel()}
            try {kotlinx.coroutines.withContext(cancelled) {repo.restore(decoded)};fail("Expected cancellation")}
            catch(_:kotlinx.coroutines.CancellationException) {}
            assertEquals(local,db.privatePreferenceDao().get(local.name))
            var stagingJob:kotlinx.coroutines.Job?=null
            val cancellingRepo=BackupRepository(db,context,BackupAvatarFiles(context) {dir->
                BackupFixture.syncDirectory(dir);stagingJob?.cancel()
            })
            val staged=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.currentCoroutineContext()).launch(start=kotlinx.coroutines.CoroutineStart.LAZY) {cancellingRepo.restore(decoded)}
            stagingJob=staged;staged.start();staged.join()
            assertTrue(staged.isCancelled)
            assertEquals(local,db.privatePreferenceDao().get(local.name))
            assertEquals(ordinary,db.privatePreferenceDao().get(ordinary.name))
            val bad=data.copy(privatePreferences=listOf(unsafe.copy(payload=EncryptedPreferences.encode(mapOf(ScreenCapturePreference.KEY to "true")))))
            assertTrue(runCatching {repo.restore(bad)}.isFailure)
            assertEquals(local,db.privatePreferenceDao().get(local.name))
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_capture_prefs BEFORE INSERT ON private_preferences WHEN NEW.name='example' BEGIN SELECT RAISE(ABORT,'injected'); END")
            assertTrue(runCatching {repo.restore(decoded)}.isFailure)
            assertEquals(local,db.privatePreferenceDao().get(local.name))
            assertEquals(ordinary,db.privatePreferenceDao().get(ordinary.name))
        } finally {db.close()}
    }
    @Test fun v18RestoresSettingsInSameTransactionAndV17ImportsDefaults()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build()
        try {
            val settings=PrivatePreferenceEntity("appearance_preferences",EncryptedPreferences.encode(mapOf("theme" to "HeavyGold","text_size" to "Large","indicator_color_verify" to "BLUE","indicator_color_birthday" to "LILAC","indicator_color_effort_1" to "WHITE","indicator_color_effort_2" to "BLUE","indicator_color_effort_3" to "GREEN","indicator_color_effort_4" to "RED","indicator_color_effort_5" to "VIOLET")))
            val data=BackupFixture.empty().copy(privatePreferences=listOf(settings))
            val encoded=BackupJson.encode(data);assertEquals(18,JSONObject(String(encoded)).getInt("dataVersion"))
            assertEquals(listOf(settings),BackupJson.decode(encoded).privatePreferences)
            val repo=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory));repo.restore(data)
            assertEquals(listOf(settings),db.privatePreferenceDao().all())
            db.privatePreferenceDao().save(settings.copy(payload=EncryptedPreferences.encode(mapOf("theme" to "Minimalist"))))
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_prefs BEFORE INSERT ON private_preferences BEGIN SELECT RAISE(ABORT,'injected'); END")
            assertTrue(runCatching { repo.restore(data) }.isFailure)
            assertEquals("Minimalist",EncryptedPreferences.decode(db.privatePreferenceDao().get(settings.name)!!.payload)["theme"])
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_prefs")
            val legacy=BackupFixture.empty();assertEquals(17,JSONObject(String(BackupJson.encode(legacy))).getInt("dataVersion"))
            repo.restore(BackupJson.decode(BackupJson.encode(legacy)));assertTrue(db.privatePreferenceDao().all().isEmpty())
            assertTrue(runCatching { BackupJson.encode(data.copy(privatePreferences=listOf(settings,settings))) }.isFailure)
            assertTrue(runCatching { BackupJson.encode(data.copy(privatePreferences=listOf(settings.copy(name="../outside")))) }.isFailure)
        } finally { db.close() }
    }
}
