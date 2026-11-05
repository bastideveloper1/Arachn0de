package com.r0ybt.arachn0de.security

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.CalendarPresentationPreference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class CalendarPresentationBackupTest {
    @Test fun optionalPreferenceRoundTripsAndRejectsCorruptionBeforeReplacement()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build()
        try {
            val row=PrivatePreferenceEntity(CalendarPresentationPreference.NAME,EncryptedPreferences.encode(mapOf(CalendarPresentationPreference.KEY to true)))
            val repo=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
            val encoded=BackupFixture.empty().copy(privatePreferences=listOf(row))
            val decoded=BackupJson.decode(BackupJson.encode(encoded))
            repo.restore(decoded)
            assertEquals(row,db.privatePreferenceDao().get(row.name))
            val exported=repo.snapshot()
            assertTrue(exported.privatePreferences.contains(row))
            val bad=encoded.copy(privatePreferences=listOf(row.copy(payload=EncryptedPreferences.encode(mapOf(CalendarPresentationPreference.KEY to "true")))))
            assertTrue(runCatching {repo.restore(bad)}.isFailure)
            assertEquals(row,db.privatePreferenceDao().get(row.name))
            repo.restore(BackupFixture.empty())
            assertNull(db.privatePreferenceDao().get(row.name))
        } finally {db.close()}
    }
}
