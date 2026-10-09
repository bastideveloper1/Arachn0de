package com.r0ybt.arachn0de.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
class PrivatePreferencesBackupTest {
    @Test fun v18RestoresSettingsInSameTransactionAndV17ImportsDefaults()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build()
        try {
            val settings=PrivatePreferenceEntity("appearance_preferences",EncryptedPreferences.encode(mapOf("theme" to "HeavyGold","text_size" to "Large")))
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
