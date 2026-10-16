package com.r0ybt.arachn0de.ui.theme

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
class AppearanceFinalTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    @Test fun minimalistMigratesOnlyThemeAndNewSelectionsPersist() {
        val storage=context.getSharedPreferences("appearance-final-test",0)
        storage.edit().clear().putString("theme","Minimalist").putString("text_size","Large").putBoolean("other",true).commit()
        val settings=AppearancePreferences(storage)
        assertEquals(AppearanceTheme.Arachn0de,settings.settings.theme)
        assertEquals("Arachn0de",storage.getString("theme",null));assertEquals(AppearanceTextSize.Large,settings.settings.textSize);assertTrue(storage.getBoolean("other",false))
        assertFalse(AppearanceTheme.entries.any {it.name=="Minimalist"})
        for(theme in listOf(AppearanceTheme.NightBlue,AppearanceTheme.DayGrey)) {
            settings.setTheme(theme);assertEquals(theme,AppearancePreferences(storage).settings.theme)
            UnlockAppearanceCache.update(context,theme);assertEquals(theme,UnlockAppearanceCache.read(context))
            assertEquals(mapOf("theme" to theme.name),context.getSharedPreferences(UnlockAppearanceCache.FILE,0).all)
        }
        storage.edit().putString("theme","Minimalist").commit();settings.reload();assertEquals("Arachn0de",storage.getString("theme",null))
    }
    @Test fun palettesKeepApprovedColorsContrastAndNoWhiteDaySurfaces() {
        val night=AppearanceTheme.NightBlue.palette;val day=AppearanceTheme.DayGrey.palette
        assertEquals(Color(0xFF1B2634),night.background);assertEquals(Color(0xFF2B3B4F),night.surface);assertEquals(Color(0xFF8EC5F4),night.primary)
        assertEquals(Color(0xFFD9DDE2),day.background);assertEquals(Color(0xFFEDF0F2),day.surface);assertEquals(Color(0xFF515963),day.primary)
        fun contrast(a:Color,b:Color):Float {val x=a.luminance();val y=b.luminance();return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)}
        for(p in listOf(night,day)) {
            listOf(p.background,p.surface,p.controls).forEach {surface->assertTrue(contrast(p.text,surface)>=4.5f);assertTrue(contrast(p.muted,surface)>=4.5f)}
            assertTrue(contrast(p.onSolidPrimary,p.solidPrimary)>=4.5f)
        }
        val scheme=day.materialColors()
        listOf(scheme.background,scheme.surface,scheme.surfaceContainer,scheme.surfaceContainerLowest,scheme.surfaceContainerHighest,scheme.surfaceContainerHigh,scheme.surfaceContainerLow,scheme.surfaceDim,scheme.surfaceBright,scheme.primaryContainer,scheme.secondaryContainer,scheme.tertiaryContainer,scheme.inverseSurface).forEach {assertNotEquals(Color.White,it)}
        assertEquals(day.text,day.semantic(SemanticColors.Error));assertEquals(day.primary,day.semantic(SemanticColors.ImageLink))
    }
    @Test fun privateThemesRestoreFromRealEncryptedBackupAndRejectMalformedValues()=runBlocking<Unit> {
        val id=UUID.randomUUID();val root=File(context.filesDir,"appearance-vault-$id").apply {mkdirs()}
        val access=SecureFiles.Session(id,root,VaultCrypto.randomKey());SecureFiles.register(access)
        val vault=VaultContext(context,access)
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build();vault.database=db
        try {
            val storage=vault.getSharedPreferences(AppearancePreferences.FILE,0)
            storage.edit().putString("theme","Minimalist").putString("text_size","Large").putBoolean("other",true).commit()
            val settings=AppearancePreferences(storage);settings.setTheme(AppearanceTheme.DayGrey)
            val backup=BackupRepository(db,vault,BackupAvatarFiles(vault,BackupFixture::syncDirectory))
            val original=db.privatePreferenceDao().all()
            val archive=backup.create("appearance-backup-fixture".toCharArray())
            val candidate=backup.inspect(SecureFiles.input(archive),"appearance-backup-fixture".toCharArray())
            settings.setTheme(AppearanceTheme.NightBlue);backup.restore(candidate);backup.discard(candidate);backup.discardPending(archive.name)
            settings.reload();assertEquals(AppearanceTheme.DayGrey,settings.settings.theme);assertEquals(AppearanceTextSize.Large,settings.settings.textSize);assertTrue(storage.getBoolean("other",false))
            assertEquals(original,db.privatePreferenceDao().all())
            val before=backup.snapshot()
            val bad=before.copy(privatePreferences=listOf(PrivatePreferenceEntity(AppearancePreferences.FILE,EncryptedPreferences.encode(mapOf("theme" to false)))))
            assertTrue(runCatching {backup.restore(bad)}.isFailure);assertEquals(original,db.privatePreferenceDao().all())
            val historical=before.copy(privatePreferences=listOf(PrivatePreferenceEntity(AppearancePreferences.FILE,EncryptedPreferences.encode(mapOf("theme" to "Minimalist","text_size" to "Large")))))
            backup.restore(historical);settings.reload();assertEquals(AppearanceTheme.Arachn0de,settings.settings.theme);assertEquals("Arachn0de",storage.getString("theme",null))
        } finally {db.close();vault.clearMemory();SecureFiles.revoke(access);root.deleteRecursively()}
    }
}
