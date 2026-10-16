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

/** Real vault/session and backup encryption; JVM SQLite replaces native SQLCipher in this test. */
@RunWith(RobolectricTestRunner::class) @Config(application=android.app.Application::class,sdk=[28])
class PersonPersonalDataVaultTest {
    @Test fun personalDataStaysInItsVaultAndRoundTripsEncryptedBackup()=runBlocking<Unit> {
        val original=ApplicationProvider.getApplicationContext<Context>()
        val root=File(original.filesDir,"personal-${UUID.randomUUID()}").apply {mkdirs()}
        val base=object:ContextWrapper(original) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
        }
        val id=UUID.randomUUID();val folder=File(base.filesDir,"security-v1/stores/$id").apply {mkdirs()}
        val key=VaultCrypto.randomKey();val session=SecureFiles.Session(id,folder,key);SecureFiles.register(session)
        try {
            File(folder,"key.envelope").writeBytes(VaultCrypto.wrap(id,"personal-main-fixture".toCharArray(),key))
            SecureFiles.write(File(folder,"identity.enc"),"primary".toByteArray())
            SecureFiles.output(File(folder,"recovery.arachnode.enc")).use {BackupContainer.writeBackup(BackupFixture.empty(),it)}
            File(base.filesDir,"security-v1/index").writeText(JSONArray(listOf(id.toString())).toString())
        } finally {SecureFiles.revoke(session);key.fill(0)}
        val vault=VaultManager(base,BackupFixture::syncDirectory) {ctx,_->Room.databaseBuilder(base,Arachn0deDatabase::class.java,ctx.getDatabasePath("arachn0de.db").absolutePath).build()}
        try {
            vault.unlock("personal-main-fixture".toCharArray());val main=vault.current.value!!
            val people=PersonRepository(main.database,AvatarStore(main.context,syncDirectory=BackupFixture::syncDirectory))
            val profile=PersonProfileEntity("a",alias="Luna",givenName="Nombre principal",familyName="Apellido",documentId="document-main-fixture")
            people.save("a","Luna",null,profile=profile)
            val backups=BackupRepository(main.database,main.context,BackupAvatarFiles(main.context,BackupFixture::syncDirectory))
            val archive=backups.create("personal-backup-fixture".toCharArray())
            val portable=SecureFiles.read(archive);backups.discardPending(archive.name)
            assertTrue(EncryptedBackup.isEncrypted(portable.copyOfRange(0,8)))
            assertFalse(String(portable,Charsets.ISO_8859_1).contains(profile.documentId))
            people.saveProfile(profile.copy(documentId="Modificado"))
            val inspected=backups.inspect(portable.inputStream(),"personal-backup-fixture".toCharArray())
            try {backups.restore(inspected)} finally {backups.discard(inspected)}
            assertEquals(profile,main.database.personProfileDao().profiles().single())
            vault.createSecondary("personal-main-fixture".toCharArray(),"personal-decoy-fixture".toCharArray())
            vault.lock();vault.unlock("personal-decoy-fixture".toCharArray());val decoy=vault.current.value!!
            assertTrue(decoy.database.personProfileDao().profiles().isEmpty())
            val secondary=PersonRepository(decoy.database,AvatarStore(decoy.context,syncDirectory=BackupFixture::syncDirectory))
            secondary.save("a","Sombra",null,profile=profile.copy(alias="Sombra",givenName="Nombre señuelo",documentId="document-decoy-fixture"))
            val decoyBackup=BackupRepository(decoy.database,decoy.context,BackupAvatarFiles(decoy.context,BackupFixture::syncDirectory))
            assertTrue(runCatching {decoyBackup.inspect(portable.inputStream(),"personal-backup-fixture".toCharArray())}.isFailure)
            assertEquals("document-decoy-fixture",decoy.database.personProfileDao().profiles().single().documentId)
            vault.lock();vault.unlock("personal-main-fixture".toCharArray())
            assertEquals(profile,vault.current.value!!.database.personProfileDao().profiles().single())
            assertEquals("Luna",vault.current.value!!.database.personDao().get("a")!!.name)
        } finally {vault.lock();root.deleteRecursively()}
    }
}
