package com.r0ybt.arachn0de.security

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class ManualLockModeTest {
    private fun base():Pair<Context,File> {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val root=File(app.filesDir,"unlock-data-${UUID.randomUUID()}").apply {mkdirs()}
        return object:ContextWrapper(app) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
        } to root
    }
    private fun manager(base:Context)=VaultManager(base,BackupFixture::syncDirectory) {ctx,_->Room.databaseBuilder(base,Arachn0deDatabase::class.java,ctx.getDatabasePath("arachn0de.db").absolutePath).build()}
    private suspend fun seed(base:Context) {
        val id=UUID.randomUUID();val root=File(base.filesDir,"security-v1/stores/$id").apply {mkdirs()}
        val key=VaultCrypto.randomKey();val access=SecureFiles.Session(id,root,key);SecureFiles.register(access)
        try {
            File(root,"key.envelope").writeBytes(VaultCrypto.wrap(id,"primary-fixture".toCharArray(),key))
            SecureFiles.write(File(root,"identity.enc"),"primary".toByteArray())
            SecureFiles.output(File(root,"recovery.arachnode.enc")).use {BackupContainer.writeBackup(BackupFixture.empty(),it)}
            File(base.filesDir,"security-v1/index").writeText(JSONArray(listOf(id.toString())).toString())
        } finally {SecureFiles.revoke(access);key.fill(0)}
    }
    @Test fun manualSessionSurvivesBackgroundButLockAndNewManagerRequireAuthenticationInBothStores()=runBlocking<Unit> {
        val (base,root)=base();seed(base);var vault=manager(base)
        try {
            vault.unlock("primary-fixture".toCharArray())
            val primary=vault.current.value!!
            assertEquals(30000L,AutoLockPreference.read(primary.context.getSharedPreferences(ScreenCapturePreference.NAME,0)))
            val project=primary.projects.createProject("Principal privado")
            vault.createSecondary("primary-fixture".toCharArray(),"secondary-fixture".toCharArray())
            vault.lock()
            for((password,isPrimary) in listOf("primary-fixture" to true,"secondary-fixture" to false)) {
                vault.unlock(password.toCharArray());val session=vault.current.value!!
                val prefs=session.context.getSharedPreferences(ScreenCapturePreference.NAME,0)
                assertEquals(30000L,AutoLockPreference.read(prefs))
                assertTrue(prefs.edit().putLong(AutoLockPreference.KEY,AutoLockPreference.MANUAL).commit())
                repeat(2) {
                    vault.background();assertTrue(vault.backgrounded.value)
                    org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofHours(12))
                    vault.foreground();assertSame(session,vault.current.value);assertFalse(vault.backgrounded.value)
                }
                assertEquals(isPrimary,session.primary)
                assertEquals(if(isPrimary) listOf(project.id) else emptyList<String>(),session.initialProjects!!.map {it.id})
                vault.savedUi=mapOf("private" to listOf("draft"))
                vault.lock();assertNull(vault.current.value);assertNull(vault.savedUi);assertNull(session.initialProjects)
                assertFalse(session.database.isOpen);assertTrue(runCatching {session.projects.observeProjects().first()}.isFailure)
                vault=manager(base);assertNull(vault.current.value);assertTrue(vault.configured)
                vault.unlock(password.toCharArray());val reopened=vault.current.value!!
                assertEquals(isPrimary,reopened.primary)
                assertEquals(AutoLockPreference.MANUAL,AutoLockPreference.read(reopened.context.getSharedPreferences(ScreenCapturePreference.NAME,0)))
                assertNotNull(reopened.initialProjects)
                vault.lock()
            }
            vault.unlock("primary-fixture".toCharArray());val automatic=vault.current.value!!
            assertTrue(automatic.context.getSharedPreferences(ScreenCapturePreference.NAME,0).edit().putLong(AutoLockPreference.KEY,30000L).commit())
            vault.background();org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(31));vault.foreground()
            assertNull(vault.current.value);assertFalse(automatic.database.isOpen)
            vault.unlock("primary-fixture".toCharArray())
            vault.current.value!!.context.getSharedPreferences(ScreenCapturePreference.NAME,0).edit().putLong(AutoLockPreference.KEY,0L).commit()
            vault.background();withTimeout(5000) {while(vault.current.value!=null) delay(10)}
        } finally {vault.lock();root.deleteRecursively()}
    }
    @Test fun manualPreferenceRoundTripsEncryptedBackupAndCorruptionKeepsPreviousData()=runBlocking<Unit> {
        val (base,root)=base();seed(base);val vault=manager(base)
        try {
            vault.unlock("primary-fixture".toCharArray());val session=vault.current.value!!
            val preferences=session.context.getSharedPreferences(ScreenCapturePreference.NAME,0)
            assertTrue(preferences.edit().putLong(AutoLockPreference.KEY,AutoLockPreference.MANUAL).putBoolean("other",true).commit())
            val backup=BackupRepository(session.database,session.context,BackupAvatarFiles(session.context,BackupFixture::syncDirectory))
            val original=session.database.privatePreferenceDao().all()
            val archive=backup.create("manual-backup-fixture".toCharArray())
            val inspected=backup.inspect(SecureFiles.input(archive),"manual-backup-fixture".toCharArray())
            preferences.edit().putLong(AutoLockPreference.KEY,60000L).commit()
            backup.restore(inspected);backup.discard(inspected);backup.discardPending(archive.name)
            assertEquals(AutoLockPreference.MANUAL,AutoLockPreference.read(preferences));assertTrue(preferences.getBoolean("other",false))
            assertEquals(original,session.database.privatePreferenceDao().all())
            val before=backup.snapshot()
            for(value in listOf<Any>(true,-2L,"manual")) {
                val bad=before.copy(privatePreferences=listOf(com.r0ybt.arachn0de.data.local.PrivatePreferenceEntity(ScreenCapturePreference.NAME,EncryptedPreferences.encode(mapOf(AutoLockPreference.KEY to value)))))
                assertTrue(runCatching {backup.restore(bad)}.isFailure)
                assertEquals(original,session.database.privatePreferenceDao().all())
            }
            backup.restore(before.copy(privatePreferences=emptyList()))
            assertEquals(30000L,AutoLockPreference.read(preferences))
            assertSame(session,vault.current.value)
        } finally {vault.lock();root.deleteRecursively()}
    }
}
