package com.r0ybt.arachn0de.ui.theme

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class BashPreferenceBackupTest {
    @Test fun encryptedPreferenceRestartsRestoresAndRejectsInvalidBackupWithoutDataLoss()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val id=UUID.randomUUID()
        val root=File(context.filesDir,"bash-vault-$id").apply {mkdirs()}
        val access=SecureFiles.Session(id,root,VaultCrypto.randomKey());SecureFiles.register(access)
        val vault=VaultContext(context,access)
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build();vault.database=db
        try {
            val storage=vault.getSharedPreferences(AppearancePreferences.FILE,0)
            val preferences=AppearancePreferences(storage)
            assertFalse(preferences.settings.bashTheme)
            preferences.setTheme(AppearanceTheme.GreenBash)
            assertTrue(AppearancePreferences(storage).settings.bashTheme)
            assertEquals(AppearanceTheme.GreenBash,AppearancePreferences(storage).settings.theme)
            val backup=BackupRepository(db,vault,BackupAvatarFiles(vault,BackupFixture::syncDirectory))
            val original=db.privatePreferenceDao().all()
            val archive=backup.create("bash-backup-fixture".toCharArray())
            val candidate=backup.inspect(SecureFiles.input(archive),"bash-backup-fixture".toCharArray())
            preferences.setTheme(AppearanceTheme.Dark)
            backup.restore(candidate);backup.discard(candidate);backup.discardPending(archive.name)
            preferences.reload()
            assertTrue(preferences.settings.bashTheme);assertEquals(original,db.privatePreferenceDao().all())
            val before=backup.snapshot()
            val bad=before.copy(privatePreferences=listOf(PrivatePreferenceEntity(AppearancePreferences.FILE,EncryptedPreferences.encode(mapOf("bash_theme" to "true")))))
            assertTrue(runCatching {backup.restore(bad)}.isFailure)
            assertEquals(original,db.privatePreferenceDao().all())
            preferences.reload();assertTrue(preferences.settings.bashTheme)
            val historical=before.copy(privatePreferences=listOf(PrivatePreferenceEntity(AppearancePreferences.FILE,EncryptedPreferences.encode(mapOf("theme" to "Dark")))))
            backup.restore(historical);preferences.reload()
            assertFalse(preferences.settings.bashTheme);assertEquals(AppearanceTheme.Dark,preferences.settings.theme)
            preferences.setTheme(AppearanceTheme.GreenBash);preferences.setTheme(AppearanceTheme.Dark)
            assertFalse(AppearancePreferences(storage).settings.bashTheme)
        } finally {db.close();vault.clearMemory();SecureFiles.revoke(access);root.deleteRecursively()}
    }
}
