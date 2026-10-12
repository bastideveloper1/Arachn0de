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

/** Real store sessions and encrypted archives; native SQLCipher replaced only in JVM tests. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class BacklogVaultTest {
    @Test fun protectedBackupRestoresBacklogAndPrimaryAndDecoyStaySeparateAcrossReopening()=runBlocking<Unit> {
        val original=ApplicationProvider.getApplicationContext<Context>()
        val root=File(original.filesDir,"planning-test-${UUID.randomUUID()}").apply {mkdirs()}
        val base=object:ContextWrapper(original) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
        }
        seed(base)
        val vault=VaultManager(base,BackupFixture::syncDirectory) {ctx,_->Room.databaseBuilder(base,Arachn0deDatabase::class.java,ctx.getDatabasePath("arachn0de.db").absolutePath).build()}
        try {
            vault.unlock("planning-main-fixture".toCharArray());val main=vault.current.value!!
            val project=main.projects.createProject("Main",creationId="same-project")
            val id=main.nodes.management.configure(project.id,null,true,ManagementMethodology.HYBRID)!!.id
            val repo=main.nodes.management.planning
            repo.saveCharter(ProjectCharterEntity(id,purpose="Private purpose"))
            val goal=repo.createObjective(id,"Private goal");val criterion=repo.createCriterion(goal.id,"Private criterion")
            repo.validateCriterion(criterion.id,true)
            val epic=main.nodes.createNode(project.id,null,"Private epic",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
            val story=main.nodes.createNode(project.id,null,"Private story",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
            val task=main.nodes.createNode(project.id,null,"Private task",creationId="same-task")
            val backlog=main.nodes.management.backlog
            backlog.setRole(id,epic.id,LayerRole.EPIC);backlog.setRole(id,story.id,LayerRole.STORY)
            backlog.linkObjective(id,goal.id,epic.id);backlog.linkEpic(id,epic.id,story.id);backlog.linkTask(id,story.id,task.id)
            val originalRecords=backlog.records()
            val backup=BackupRepository(main.database,main.context,BackupAvatarFiles(main.context,BackupFixture::syncDirectory))
            val before=backup.snapshot();val archive=backup.create("planning-backup-fixture".toCharArray())
            val portable=SecureFiles.read(archive);backup.discardPending(archive.name)
            assertTrue(EncryptedBackup.isEncrypted(portable.copyOfRange(0,8)))
            repo.deleteObjective(goal.id)
            val inspected=backup.inspect(portable.inputStream(),"planning-backup-fixture".toCharArray())
            try {backup.restore(inspected)} finally {backup.discard(inspected)}
            assertEquals(before.projectCharters,main.database.planningDao().charters())
            assertEquals(before.managementObjectives,main.database.planningDao().objectives())
            assertEquals(before.acceptanceCriteria,main.database.planningDao().criteria());assertEquals(originalRecords,backlog.records())
            vault.createSecondary("planning-main-fixture".toCharArray(),"planning-decoy-fixture".toCharArray())
            vault.lock();vault.unlock("planning-decoy-fixture".toCharArray());val decoy=vault.current.value!!
            assertTrue(decoy.database.planningDao().objectives().isEmpty());assertEquals(BacklogRecords(),decoy.nodes.management.backlog.records())
            val dp=decoy.projects.createProject("Decoy",creationId="same-project")
            val did=decoy.nodes.management.configure(dp.id,null,true,ManagementMethodology.AGILE)!!.id
            decoy.nodes.management.planning.createObjective(did,"Decoy goal")
            val dt=decoy.nodes.createNode(dp.id,null,"Decoy task",creationId="same-task")
            decoy.nodes.management.backlog.addTask(did,dt.id)
            val decoyBackup=BackupRepository(decoy.database,decoy.context,BackupAvatarFiles(decoy.context,BackupFixture::syncDirectory))
            assertTrue(runCatching {decoyBackup.inspect(portable.inputStream(),"planning-backup-fixture".toCharArray())}.isFailure)
            assertEquals("Decoy goal",decoy.database.planningDao().objectives().single().title)
            assertTrue(decoy.database.planningDao().criteria().isEmpty())
            vault.lock();vault.unlock("planning-main-fixture".toCharArray())
            assertEquals(originalRecords,vault.current.value!!.nodes.management.backlog.records())
            assertEquals(before.managementObjectives,vault.current.value!!.database.planningDao().objectives())
            assertEquals(before.acceptanceCriteria,vault.current.value!!.database.planningDao().criteria())
            vault.lock();vault.unlock("planning-decoy-fixture".toCharArray())
            assertEquals("Decoy goal",vault.current.value!!.database.planningDao().objectives().single().title)
            assertEquals(listOf(BacklogEntryEntity(dt.id,did,0)),vault.current.value!!.database.backlogDao().entries())
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
