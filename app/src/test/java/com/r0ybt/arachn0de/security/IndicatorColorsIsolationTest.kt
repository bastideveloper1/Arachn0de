package com.r0ybt.arachn0de.security

import com.r0ybt.arachn0de.ui.theme.*

import android.content.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(application=android.app.Application::class,sdk=[28])
class IndicatorColorsIsolationTest {
    @Test fun colorsRemainEncryptedPerStoreAndRestoreWithHistoricalDefaults()=runBlocking<Unit> {
        val original=ApplicationProvider.getApplicationContext<Context>()
        val root=File(original.filesDir,"share-isolation-${UUID.randomUUID()}").apply {mkdirs()}
        val base=object:ContextWrapper(original) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
        }
        val id=UUID.randomUUID();val home=File(base.filesDir,"security-v1/stores/$id").apply {mkdirs()}
        val key=VaultCrypto.randomKey();val access=SecureFiles.Session(id,home,key);SecureFiles.register(access)
        try {
            File(home,"key.envelope").writeBytes(VaultCrypto.wrap(id,"principal-share".toCharArray(),key))
            SecureFiles.write(File(home,"identity.enc"),"primary".toByteArray())
            SecureFiles.output(File(home,"recovery.arachnode.enc")).use {BackupContainer.writeBackup(BackupFixture.empty(),it)}
            File(base.filesDir,"security-v1/index").writeText(JSONArray(listOf(id.toString())).toString())
        } finally {SecureFiles.revoke(access);key.fill(0)}
        fun manager()=VaultManager(base,BackupFixture::syncDirectory) {ctx,_->Room.databaseBuilder(base,Arachn0deDatabase::class.java,ctx.getDatabasePath("arachn0de.db").absolutePath).build()}
        var vault=manager()
        try {
            vault.unlock("principal-share".toCharArray());val main=vault.current.value!!
            val prefs=AppearancePreferences(main.context.getSharedPreferences(AppearancePreferences.FILE,0))
            prefs.setIndicatorColor("notes",IndicatorColor.BLUE);prefs.setIndicatorColor("people",IndicatorColor.RED)
            val backup=BackupRepository(main.database,main.context,BackupAvatarFiles(main.context,syncDirectory=BackupFixture::syncDirectory))
            val snapshot=BackupJson.decode(BackupJson.encode(backup.snapshot()))
            prefs.resetIndicatorColors();backup.restore(snapshot);prefs.reload()
            assertEquals(IndicatorColor.BLUE,prefs.settings.indicatorColors["notes"])
            val row=main.database.privatePreferenceDao().get(AppearancePreferences.FILE)!!
            val bad=snapshot.copy(privatePreferences=snapshot.privatePreferences.filter {it.name!=row.name}+row.copy(payload=EncryptedPreferences.encode(mapOf("indicator_color_notes" to "INVALID"))))
            assertTrue(runCatching {backup.restore(bad)}.isFailure)
            assertEquals(row,main.database.privatePreferenceDao().get(row.name))
            vault.createSecondary("principal-share".toCharArray(),"secondary-share".toCharArray())
            vault.lock();assertTrue(runCatching {main.context.getSharedPreferences(AppearancePreferences.FILE,0)}.isFailure)
            vault.unlock("secondary-share".toCharArray())
            val decoy=AppearancePreferences(vault.current.value!!.context.getSharedPreferences(AppearancePreferences.FILE,0))
            assertTrue(decoy.settings.indicatorColors.isEmpty());decoy.setIndicatorColor("notes",IndicatorColor.GREEN)
            vault.lock();vault=manager();vault.unlock("principal-share".toCharArray())
            val recovered=vault.current.value!!
            val reopened=AppearancePreferences(recovered.context.getSharedPreferences(AppearancePreferences.FILE,0))
            assertEquals(IndicatorColor.BLUE,reopened.settings.indicatorColors["notes"])
            assertEquals(IndicatorColor.RED,reopened.settings.indicatorColors["people"])
            val restored=BackupRepository(recovered.database,recovered.context,BackupAvatarFiles(recovered.context,syncDirectory=BackupFixture::syncDirectory))
            restored.restore(BackupJson.decode(BackupJson.encode(BackupFixture.empty())))
            reopened.reload();assertTrue(reopened.settings.indicatorColors.isEmpty())
            vault.lock();vault.unlock("secondary-share".toCharArray())
            assertEquals(IndicatorColor.GREEN,AppearancePreferences(vault.current.value!!.context.getSharedPreferences(AppearancePreferences.FILE,0)).settings.indicatorColors["notes"])
        } finally {vault.lock()}
    }
}
