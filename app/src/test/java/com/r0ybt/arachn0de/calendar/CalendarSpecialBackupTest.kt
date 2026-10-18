package com.r0ybt.arachn0de.calendar

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
class CalendarSpecialBackupTest {
    @Test fun switchesPersistAndEncryptedBackupRestoresThemWhileRejectingInvalidDataBeforeReplace()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val id=UUID.randomUUID();val root=File(context.filesDir,"calendar-special-$id").apply {mkdirs()}
        val access=SecureFiles.Session(id,root,VaultCrypto.randomKey());SecureFiles.register(access)
        val vault=VaultContext(context,access)
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build();vault.database=db
        val preferences=CalendarSpecialPreferences(vault.getSharedPreferences(CalendarSpecialPreferences.NAME,0))
        preferences.observe()
        try {
            assertEquals(CalendarSpecialVisibility(),preferences.settings)
            val selected=CalendarSpecialVisibility(false,true,true)
            assertTrue(preferences.set(selected))
            assertEquals(selected,CalendarSpecialPreferences(vault.getSharedPreferences(CalendarSpecialPreferences.NAME,0)).settings)
            val backup=BackupRepository(db,vault,BackupAvatarFiles(vault,BackupFixture::syncDirectory))
            val archive=backup.create("calendar-special-fixture".toCharArray())
            val candidate=backup.inspect(SecureFiles.input(archive),"calendar-special-fixture".toCharArray())
            preferences.set(CalendarSpecialVisibility())
            backup.restore(candidate);backup.discard(candidate);backup.discardPending(archive.name)
            assertEquals(selected,preferences.settings)
            val before=backup.snapshot()
            val invalid=before.copy(privatePreferences=listOf(PrivatePreferenceEntity(CalendarSpecialPreferences.NAME,EncryptedPreferences.encode(mapOf("holidays" to "true")))))
            assertTrue(runCatching {backup.restore(invalid)}.isFailure)
            assertEquals(before.privatePreferences,backup.snapshot().privatePreferences);assertEquals(selected,preferences.settings)
            backup.restore(before.copy(privatePreferences=emptyList()))
            assertEquals(CalendarSpecialVisibility(),preferences.settings)
        } finally {preferences.unobserve();db.close();vault.clearMemory();SecureFiles.revoke(access);root.deleteRecursively()}
    }
    @Test fun birthdayVisibilityRestoresEncryptedAndOldKeysDefaultToEnabled()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val id=UUID.randomUUID();val root=File(context.filesDir,"calendar-birthdays-$id").apply {mkdirs()}
        val access=SecureFiles.Session(id,root,VaultCrypto.randomKey());SecureFiles.register(access)
        val vault=VaultContext(context,access)
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build();vault.database=db
        val storage=vault.getSharedPreferences(CalendarSpecialPreferences.NAME,0)
        val preferences=CalendarSpecialPreferences(storage);preferences.observe()
        try {
            assertTrue(preferences.settings.birthdays)
            preferences.set(preferences.settings.copy(birthdays=false))
            val backup=BackupRepository(db,vault,BackupAvatarFiles(vault,BackupFixture::syncDirectory))
            val archive=backup.create("birthday-visibility-fixture".toCharArray())
            val candidate=backup.inspect(SecureFiles.input(archive),"birthday-visibility-fixture".toCharArray())
            preferences.set(preferences.settings.copy(birthdays=true))
            backup.restore(candidate);backup.discard(candidate);backup.discardPending(archive.name)
            assertFalse(preferences.settings.birthdays)
            val before=backup.snapshot()
            val invalid=before.copy(privatePreferences=listOf(PrivatePreferenceEntity(CalendarSpecialPreferences.NAME,EncryptedPreferences.encode(mapOf("birthdays" to 1)))))
            assertTrue(runCatching {backup.restore(invalid)}.isFailure)
            assertEquals(before.privatePreferences,backup.snapshot().privatePreferences)
            val old=before.copy(privatePreferences=listOf(PrivatePreferenceEntity(CalendarSpecialPreferences.NAME,EncryptedPreferences.encode(mapOf("holidays" to false,"saints" to true,"commemorations" to true)))))
            backup.restore(old)
            assertTrue(preferences.settings.birthdays);assertFalse(preferences.settings.holidays)
        } finally {preferences.unobserve();db.close();vault.clearMemory();SecureFiles.revoke(access);root.deleteRecursively()}
    }

}
