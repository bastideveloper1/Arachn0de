package com.r0ybt.arachn0de.audio

import android.content.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.security.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows

/** Actual encrypted file roots; SQLite is substituted only for SQLCipher's native JVM backend. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class AudioVaultTest {
    private fun tone()=javaClass.classLoader!!.getResourceAsStream("audio/tone.aac")!!.use {it.readBytes()}
    @Test fun encryptedAudioArchiveMarkersAndLockStayIsolatedFromDecoy()=runBlocking<Unit> {
        val original=ApplicationProvider.getApplicationContext<Context>();val root=File(original.filesDir,"audio-vault-${UUID.randomUUID()}").apply {mkdirs()}
        val base=object:ContextWrapper(original) {override fun getFilesDir()=root;override fun getCacheDir()=File(root,"cache").apply {mkdirs()};override fun getApplicationContext():Context=this}
        seed(base)
        val vault=VaultManager(base,sync=BackupFixture::syncDirectory,openEncrypted={ctx,_->Room.databaseBuilder(base,Arachn0deDatabase::class.java,ctx.getDatabasePath("arachn0de.db").absolutePath).build()})
        try {
            vault.unlock("audio-primary-fixture".toCharArray());val main=vault.current.value!!
            val project=main.projects.createProject("Contexto")
            val node=main.nodes.createNode(project.id,null,"Audio privado","Texto")
            val drafts=AudioDraftStore(main.context,main.database,BackupFixture::syncDirectory)
            val id=drafts.begin(node.id);drafts.append(id,0,tone())
            val partial=File(main.context.filesDir,"audio-drafts/$id/0.aac")
            assertFalse(partial.readBytes().contentEquals(tone()))
            val row=drafts.finish(id)!!;main.database.attachmentDao().playback(id,128);main.database.attachmentDao().bookmark(id,64)
            val file=AttachmentStore(main.context,BackupFixture::syncDirectory).file(row.storageName)
            assertFalse(file.readBytes().contentEquals(tone()));assertArrayEquals(tone(),SecureFiles.read(file))
            val backup=BackupRepository(main.database,main.context,BackupAvatarFiles(main.context,BackupFixture::syncDirectory))
            val archive=backup.create("audio-backup-fixture".toCharArray());val bytes=SecureFiles.read(archive);backup.discardPending(archive.name)
            com.r0ybt.arachn0de.data.repository.AttachmentRepository(main.database,AttachmentStore(main.context,BackupFixture::syncDirectory)).detachNode(node.id,id)
            val inspected=backup.inspect(bytes.inputStream(),"audio-backup-fixture".toCharArray());backup.restore(inspected);backup.discard(inspected)
            val restored=main.database.attachmentDao().get(id)!!;val restoredFile=AttachmentStore(main.context,BackupFixture::syncDirectory).file(restored.storageName)
            assertArrayEquals(tone(),SecureFiles.read(restoredFile));assertEquals(128L,restored.playbackMs);assertEquals(64L,restored.bookmarkMs)
            main.audio.state.value=AudioState(fileId=id,owner=node.id,title="Audio privado",positionMs=192,durationMs=row.durationMs)
            vault.createSecondary("audio-primary-fixture".toCharArray(),"audio-decoy-fixture".toCharArray())
            vault.lock();assertNull(vault.current.value);assertFalse(main.audio.state.value.visible)
            assertTrue(runCatching {SecureFiles.read(restoredFile)}.isFailure)
            vault.unlock("audio-decoy-fixture".toCharArray());val decoy=vault.current.value!!
            assertTrue(decoy.database.attachmentDao().files().isEmpty());assertFalse(decoy.audio.state.value.visible)
            assertTrue(runCatching {SecureFiles.read(restoredFile)}.isFailure)
            vault.lock();vault.unlock("audio-primary-fixture".toCharArray())
            assertEquals(192L,vault.current.value!!.database.attachmentDao().get(id)!!.playbackMs)
            assertFalse(vault.current.value!!.audio.state.value.visible)
        } finally {vault.lock();root.deleteRecursively()}
    }
    @Test fun onlyLiveAudioSuspendsAutomaticLockManualLockStillClosesSession()=runBlocking<Unit> {
        val original=ApplicationProvider.getApplicationContext<Context>();val root=File(original.filesDir,"audio-lock-${UUID.randomUUID()}").apply {mkdirs()}
        val base=object:ContextWrapper(original) {override fun getFilesDir()=root;override fun getCacheDir()=File(root,"cache").apply {mkdirs()};override fun getApplicationContext():Context=this}
        seed(base);val vault=VaultManager(base,sync=BackupFixture::syncDirectory,openEncrypted={ctx,_->Room.databaseBuilder(base,Arachn0deDatabase::class.java,ctx.getDatabasePath("arachn0de.db").absolutePath).build()})
        try {
            vault.unlock("audio-primary-fixture".toCharArray());val session=vault.current.value!!
            session.context.getSharedPreferences(ScreenCapturePreference.NAME,0).edit().putLong(AutoLockPreference.KEY,0L).commit()
            session.audio.state.value=AudioState(recording=true,paused=true)
            vault.background();delay(40);assertSame(session,vault.current.value)
            vault.foreground();assertSame(session,vault.current.value)
            vault.lock();assertNull(vault.current.value);assertFalse(session.audio.state.value.visible)
            vault.unlock("audio-primary-fixture".toCharArray());val reopened=vault.current.value!!
            assertFalse(reopened.audio.state.value.visible)
            vault.background()
            withTimeout(5000) {while(vault.current.value!=null) {Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();delay(10)}}
            assertNull(vault.current.value)
        } finally {vault.lock();root.deleteRecursively()}
    }
    private suspend fun seed(base:Context) {
        val id=UUID.randomUUID();val root=File(base.filesDir,"security-v1/stores/$id").apply {mkdirs()}
        val key=VaultCrypto.randomKey();val access=SecureFiles.Session(id,root,key);SecureFiles.register(access)
        try {
            File(root,"key.envelope").writeBytes(VaultCrypto.wrap(id,"audio-primary-fixture".toCharArray(),key))
            SecureFiles.write(File(root,"identity.enc"),"primary".toByteArray())
            SecureFiles.output(File(root,"recovery.arachnode.enc")).use {BackupContainer.writeBackup(BackupFixture.empty(),it)}
            File(base.filesDir,"security-v1/index").writeText(JSONArray(listOf(id.toString())).toString())
        } finally {SecureFiles.revoke(access);key.fill(0)}
    }
}
