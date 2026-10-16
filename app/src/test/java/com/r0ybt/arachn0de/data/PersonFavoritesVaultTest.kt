package com.r0ybt.arachn0de.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/** Actual vault sessions and encrypted backup; native SQLCipher replaced by SQLite in JVM. */
@RunWith(RobolectricTestRunner::class) @Config(application=android.app.Application::class,sdk=[28])
class PersonFavoritesVaultTest {
    @Test fun favoritesRestoreFromEncryptedBackupAndRemainSeparateAcrossVaultReopening()=runBlocking<Unit> {
        val original=ApplicationProvider.getApplicationContext<Context>()
        val root=File(original.filesDir,"favorites-${UUID.randomUUID()}").apply {mkdirs()}
        val base=object:ContextWrapper(original) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
        }
        val id=UUID.randomUUID();val folder=File(base.filesDir,"security-v1/stores/$id").apply {mkdirs()}
        val key=VaultCrypto.randomKey();val session=SecureFiles.Session(id,folder,key);SecureFiles.register(session)
        try {
            File(folder,"key.envelope").writeBytes(VaultCrypto.wrap(id,"favorites-main-fixture".toCharArray(),key))
            SecureFiles.write(File(folder,"identity.enc"),"primary".toByteArray())
            SecureFiles.output(File(folder,"recovery.arachnode.enc")).use {BackupContainer.writeBackup(BackupFixture.empty(),it)}
            File(base.filesDir,"security-v1/index").writeText(JSONArray(listOf(id.toString())).toString())
        } finally {SecureFiles.revoke(session);key.fill(0)}
        val vault=VaultManager(base,BackupFixture::syncDirectory) {ctx,_->Room.databaseBuilder(base,Arachn0deDatabase::class.java,ctx.getDatabasePath("arachn0de.db").absolutePath).build()}
        try {
            vault.unlock("favorites-main-fixture".toCharArray());val main=vault.current.value!!
            val people=PersonRepository(main.database,AvatarStore(main.context,syncDirectory=BackupFixture::syncDirectory))
            people.save("a","Ana",null);people.save("b","Bruno",null);people.identifySelf("a");people.toggleFavorite("b")
            val backups=BackupRepository(main.database,main.context,BackupAvatarFiles(main.context,BackupFixture::syncDirectory))
            val archive=backups.create("favorites-backup-fixture".toCharArray());val portable=SecureFiles.read(archive);backups.discardPending(archive.name)
            assertTrue(EncryptedBackup.isEncrypted(portable.copyOfRange(0,8)))
            people.toggleFavorite("b")
            val inspected=backups.inspect(portable.inputStream(),"favorites-backup-fixture".toCharArray())
            try {backups.restore(inspected)} finally {backups.discard(inspected)}
            assertEquals(setOf("b"),people.favoritePeople())
            vault.createSecondary("favorites-main-fixture".toCharArray(),"favorites-decoy-fixture".toCharArray())
            vault.lock();vault.unlock("favorites-decoy-fixture".toCharArray());val decoy=vault.current.value!!
            val secondary=PersonRepository(decoy.database,AvatarStore(decoy.context,syncDirectory=BackupFixture::syncDirectory))
            secondary.save("a","Otra Ana",null);secondary.save("b","Otro Bruno",null)
            assertTrue(secondary.favoritePeople().isEmpty());secondary.toggleFavorite("a")
            val decoyBackups=BackupRepository(decoy.database,decoy.context,BackupAvatarFiles(decoy.context,BackupFixture::syncDirectory))
            assertTrue(runCatching {decoyBackups.inspect(portable.inputStream(),"favorites-backup-fixture".toCharArray())}.isFailure)
            assertEquals(setOf("a"),secondary.favoritePeople())
            vault.lock();vault.unlock("favorites-main-fixture".toCharArray());val reopened=vault.current.value!!
            assertEquals(setOf("b"),PersonRepository(reopened.database,AvatarStore(reopened.context)).favoritePeople())
            assertEquals("a",reopened.database.cascadeRelationDao().identities().single().personId)
        } finally {vault.lock();root.deleteRecursively()}
    }
}
