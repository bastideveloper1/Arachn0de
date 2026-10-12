package com.r0ybt.arachn0de.metro

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

/** Real store sessions and encrypted archives; native SQLCipher replaced only in JVM tests. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class MetroIndependenceVaultTest {
    @Test fun activeJourneysAndEncryptedBackupsRemainIsolatedBetweenStores()=runBlocking<Unit> {
        val original=ApplicationProvider.getApplicationContext<Context>()
        val root=File(original.filesDir,"metro-vault-${UUID.randomUUID()}").apply {mkdirs()}
        val base=object:ContextWrapper(original) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
        }
        seed(base)
        val vault=VaultManager(base,BackupFixture::syncDirectory) {ctx,_->Room.databaseBuilder(base,Arachn0deDatabase::class.java,ctx.getDatabasePath("arachn0de.db").absolutePath).build()}
        try {
            vault.unlock("metro-main-fixture".toCharArray());val main=vault.current.value!!
            val repo=MetroRepository(main.database,main.context)
            val route=MetroPlanner.plan(repo.snapshot().preferences.network,listOf("los-heroes","tobalaba"),false,MetroRestrictions())!!
            val p=main.projects.createProject("Main");val task=main.nodes.createNode(p.id,null,"Main task")
            val id=repo.savePlan(route);assertTrue(repo.begin(id,repo.snapshot().journeys.single().row.revision,MetroTime(100000,1000,1)));repo.link(id,repo.snapshot().journeys.single().row.revision,task.id)
            val before=repo.snapshot()
            val backup=BackupRepository(main.database,main.context,BackupAvatarFiles(main.context,BackupFixture::syncDirectory))
            val archive=backup.create("metro-backup-fixture".toCharArray())
            val bytes=SecureFiles.read(archive);backup.discardPending(archive.name)
            vault.createSecondary("metro-main-fixture".toCharArray(),"metro-decoy-fixture".toCharArray())
            vault.lock();vault.unlock("metro-decoy-fixture".toCharArray());val decoy=vault.current.value!!
            val other=MetroRepository(decoy.database,decoy.context);assertTrue(other.snapshot().journeys.isEmpty())
            val copy=other.savePlan(route)
            val otherBefore=other.snapshot()
            val otherBackup=BackupRepository(decoy.database,decoy.context,BackupAvatarFiles(decoy.context,BackupFixture::syncDirectory))
            assertTrue(runCatching {otherBackup.inspect(bytes.inputStream(),"metro-backup-fixture".toCharArray())}.isFailure)
            assertEquals(otherBefore,other.snapshot());assertNotEquals(id,copy)
            vault.lock();vault.unlock("metro-main-fixture".toCharArray());val reopened=vault.current.value!!
            val restoredRepo=MetroRepository(reopened.database,reopened.context)
            assertEquals(before,restoredRepo.snapshot())
            val restoredBackup=BackupRepository(reopened.database,reopened.context,BackupAvatarFiles(reopened.context,BackupFixture::syncDirectory))
            val candidate=restoredBackup.inspect(bytes.inputStream(),"metro-backup-fixture".toCharArray())
            try {restoredBackup.restore(candidate)} finally {restoredBackup.discard(candidate)}
            val restored=restoredRepo.snapshot().journeys.single()
            assertEquals(id,restored.row.id);assertEquals(task.id,restored.row.nodeId)
            assertEquals(before.journeys.single().data.active!!.id,restored.data.active!!.id)
            assertTrue(restored.data.active!!.uncertain)
            vault.lock();vault.unlock("metro-decoy-fixture".toCharArray())
            val last=vault.current.value!!;assertEquals(otherBefore,MetroRepository(last.database,last.context).snapshot())
        } finally {vault.lock();root.deleteRecursively()}
    }
    private suspend fun seed(base:Context) {
        val id=UUID.randomUUID();val root=File(base.filesDir,"security-v1/stores/$id").apply {mkdirs()}
        val key=VaultCrypto.randomKey();val session=SecureFiles.Session(id,root,key);SecureFiles.register(session)
        try {
            File(root,"key.envelope").writeBytes(VaultCrypto.wrap(id,"metro-main-fixture".toCharArray(),key))
            SecureFiles.write(File(root,"identity.enc"),"primary".toByteArray())
            SecureFiles.output(File(root,"recovery.arachnode.enc")).use {BackupContainer.writeBackup(BackupFixture.empty(),it)}
            File(base.filesDir,"security-v1/index").writeText(JSONArray(listOf(id.toString())).toString())
        } finally {SecureFiles.revoke(session);key.fill(0)}
    }
}
