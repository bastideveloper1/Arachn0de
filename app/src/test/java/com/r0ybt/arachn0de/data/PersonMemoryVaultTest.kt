package com.r0ybt.arachn0de.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.security.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Originals and gallery metadata stay within their encrypted vault and portable archive. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class PersonMemoryVaultTest {
    @Test fun pinnedEntriesAndHistoricalMetadataRoundTripEncryptedArchiveAndRemainIsolated()=runBlocking<Unit> {
        val original=ApplicationProvider.getApplicationContext<Context>()
        val root=File(original.filesDir,"memory-test-${UUID.randomUUID()}").apply {mkdirs()}
        val base=object:ContextWrapper(original) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
        }
        seed(base)
        val vault=VaultManager(base,BackupFixture::syncDirectory) {ctx,_->Room.databaseBuilder(base,Arachn0deDatabase::class.java,ctx.getDatabasePath("arachn0de.db").absolutePath).build()}
        try {
            vault.unlock("planning-main-fixture".toCharArray());val main=vault.current.value!!
            val people=PersonRepository(main.database,AvatarStore(main.context,syncDirectory=BackupFixture::syncDirectory))
            val store=AttachmentStore(main.context,BackupFixture::syncDirectory)
            val attachments=AttachmentRepository(main.database,store)
            val gallery=PersonMemoryRepository(main.database,main.context,attachments,people)
            people.save("person","Ana",null)
            val source=File(main.context.cacheDir,"source.png").apply {writeBytes(BackupFixture.png())}
            val file=attachments.importGallery(android.net.Uri.fromFile(source),"draft")
            val id=gallery.create("person",listOf(file.id),"draft")
            val entry=main.database.personMemoryDao().memories().single().copy(date="1980-04-12",description="Recuerdo privado",isPinned=true)
            val image=main.database.personMemoryDao().images().single().copy(description="Fotografía privada")
            gallery.save(entry,listOf(image),"edit");gallery.pin(id,true)
            assertArrayEquals(source.readBytes(),SecureFiles.read(store.file(file.storageName)))
            assertFalse(source.readBytes().contentEquals(store.file(file.storageName).readBytes()))
            val backup=BackupRepository(main.database,main.context,BackupAvatarFiles(main.context,BackupFixture::syncDirectory))
            val archive=backup.create("memory-backup-fixture".toCharArray())
            val portable=SecureFiles.read(archive);backup.discardPending(archive.name)
            assertTrue(EncryptedBackup.isEncrypted(portable.copyOfRange(0,8)))
            gallery.deleteEntry(id)
            val inspected=backup.inspect(portable.inputStream(),"memory-backup-fixture".toCharArray())
            try {backup.restore(inspected)} finally {backup.discard(inspected)}
            assertEquals(listOf(entry),main.database.personMemoryDao().memories())
            assertEquals(listOf(image),main.database.personMemoryDao().images())
            val restored=main.database.attachmentDao().files().single()
            assertArrayEquals(source.readBytes(),SecureFiles.read(store.file(restored.storageName)))
            vault.createSecondary("planning-main-fixture".toCharArray(),"memory-secondary-fixture".toCharArray())
            vault.lock();vault.unlock("memory-secondary-fixture".toCharArray())
            val secondary=vault.current.value!!
            assertTrue(secondary.database.personMemoryDao().memories().isEmpty())
            assertTrue(secondary.database.personMemoryDao().images().isEmpty())
            assertTrue(secondary.database.attachmentDao().files().isEmpty())
            vault.lock();vault.unlock("planning-main-fixture".toCharArray())
            assertEquals(listOf(entry),vault.current.value!!.database.personMemoryDao().memories())
            assertEquals(listOf(image),vault.current.value!!.database.personMemoryDao().images())
        } finally {vault.lock();root.deleteRecursively()}
    }
    private suspend fun seed(base:Context) {
        val id=UUID.randomUUID();val root=File(base.filesDir,"security-v1/stores/$id").apply {mkdirs()}
        val key=VaultCrypto.randomKey();val session=SecureFiles.Session(id,root,key);SecureFiles.register(session)
        try {
            File(root,"key.envelope").writeBytes(VaultCrypto.wrap(id,"planning-main-fixture".toCharArray(),key))
            SecureFiles.write(File(root,"identity.enc"),"primary".toByteArray())
            SecureFiles.output(File(root,"recovery.arachnode.enc")).use {BackupContainer.writeBackup(BackupFixture.empty(),it)}
            File(base.filesDir,"security-v1/index").writeText(JSONArray(listOf(id.toString())).toString())
        } finally {SecureFiles.revoke(session);key.fill(0)}
    }
}
