package com.r0ybt.arachn0de.sharing

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
class SharedCaptureIsolationTest {
    @Test fun pendingIsBoundToAuthenticatedStoreAcrossLockAndProcessReopen()=runBlocking<Unit> {
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
            val request=UUID.randomUUID().toString();main.sharedCaptures.receive(request,SharedContent("Privada","https://private.example",null))
            vault.createSecondary("principal-share".toCharArray(),"secondary-share".toCharArray())
            vault.lock();assertTrue(runCatching {main.sharedCaptures.observe().first()}.isFailure)
            vault.unlock("secondary-share".toCharArray());assertFalse(vault.current.value!!.primary)
            assertTrue(vault.current.value!!.sharedCaptures.observe().first().isEmpty())
            val decoy=UUID.randomUUID().toString();vault.current.value!!.sharedCaptures.receive(decoy,SharedContent("Señuelo","Distinto",null))
            vault.lock();vault=manager();vault.unlock("principal-share".toCharArray())
            val recovered=vault.current.value!!;recovered.sharedCaptures.recover()
            assertEquals(listOf(request),recovered.sharedCaptures.observe().first().map {it.id})
            recovered.sharedCaptures.discard(request)
            vault.lock();vault.unlock("secondary-share".toCharArray());assertEquals(listOf(decoy),vault.current.value!!.sharedCaptures.observe().first().map {it.id})
        } finally {vault.lock()}
    }
}
