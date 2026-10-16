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
class InitialUnlockDataTest {
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
    @Test fun firstQueryFinishesBeforeSessionPublicationForPrimaryAndSecondary()=runBlocking<Unit> {
        val (base,root)=base();seed(base);val manager=manager(base)
        try {
            manager.unlock("primary-fixture".toCharArray())
            assertEquals(emptyList<com.r0ybt.arachn0de.domain.model.Project>(),manager.current.value!!.initialProjects)
            val original=manager.current.value!!.projects.createProject("Principal")
            manager.createSecondary("primary-fixture".toCharArray(),"secondary-fixture".toCharArray());manager.lock()
            for((password,expected) in listOf("primary-fixture" to listOf(original.id),"secondary-fixture" to emptyList())) {
                val entered=CompletableDeferred<VaultSession>();val release=CompletableDeferred<Unit>()
                manager.loadInitial={session->entered.complete(session);release.await();session.projects.observeProjects().first()}
                val pending=async {manager.unlock(password.toCharArray())}
                val preparing=withTimeout(15000) {entered.await()}
                assertNull(manager.current.value);assertNull(preparing.initialProjects);assertFalse(pending.isCompleted)
                release.complete(Unit);pending.await()
                assertSame(preparing,manager.current.value)
                assertEquals(expected,manager.current.value!!.initialProjects!!.map {it.id})
                assertEquals(password=="primary-fixture",manager.current.value!!.primary)
                manager.lock()
            }
        } finally {manager.lock();root.deleteRecursively()}
    }
    @Test fun wrongPasswordAndFailedOrCancelledQueryStayLockedAndCanRetry()=runBlocking<Unit> {
        val (base,root)=base();seed(base);val manager=manager(base)
        try {
            var calls=0
            manager.loadInitial={calls++;it.projects.observeProjects().first()}
            assertTrue(runCatching {manager.unlock("wrong-fixture".toCharArray())}.isFailure)
            assertEquals(0,calls);assertNull(manager.current.value)
            lateinit var failed:VaultSession
            manager.loadInitial={failed=it;throw java.io.IOException("Private query detail")}
            assertTrue(runCatching {manager.unlock("primary-fixture".toCharArray())}.isFailure)
            assertNull(manager.current.value);assertFalse(failed.database.isOpen)
            val entered=CompletableDeferred<VaultSession>()
            manager.loadInitial={entered.complete(it);awaitCancellation()}
            val pending=launch {manager.unlock("primary-fixture".toCharArray())}
            val cancelled=withTimeout(15000) {entered.await()};pending.cancelAndJoin()
            assertNull(manager.current.value);assertFalse(cancelled.database.isOpen)
            manager.loadInitial={it.projects.observeProjects().first()}
            manager.unlock("primary-fixture".toCharArray())
            assertNotNull(manager.current.value);assertTrue(manager.current.value!!.initialProjects!!.isEmpty())
        } finally {manager.lock();root.deleteRecursively()}
    }
}
