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
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real vault sessions; only the native SQLCipher factory is substituted in JVM tests. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=android.app.Application::class)
class KanbanVaultTest {
    @Test fun projectionsAndPhaseWritesRemainIsolatedAfterRelocking()=runBlocking<Unit> {
        val original=ApplicationProvider.getApplicationContext<Context>()
        val root=File(original.filesDir,"kanban-test-${UUID.randomUUID()}").apply {mkdirs()}
        val base=object:ContextWrapper(original) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
        }
        seed(base)
        val vault=VaultManager(base,BackupFixture::syncDirectory) {ctx,_->Room.databaseBuilder(base,Arachn0deDatabase::class.java,ctx.getDatabasePath("arachn0de.db").absolutePath).build()}
        suspend fun fixture(session:VaultSession,title:String):String {
            val p=session.projects.createProject(title,creationId="same-project")
            val context=session.nodes.management.configure(p.id,null,true,ManagementMethodology.AGILE)!!.id
            val s=session.nodes.createNode(p.id,null,"Sprint",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER,creationId="same-sprint")
            session.nodes.setSprintMode(s.id,true)
            val task=session.nodes.createNode(p.id,null,title,creationId="same-task")
            session.nodes.management.backlog.addTask(context,task.id);session.nodes.sprintLinks.link(s.id,setOf(task.id))
            session.database.personDao().insert(PersonEntity("same-self",title,null))
            session.database.cascadeRelationDao().save(LocalIdentityEntity(personId="same-self"))
            return context
        }
        try {
            vault.unlock("planning-main-fixture".toCharArray());val primary=fixture(vault.current.value!!,"Primary only")
            vault.current.value!!.nodes.management.kanban.phase(primary,"same-task",com.r0ybt.arachn0de.domain.model.WorkState.DOING)
            vault.createSecondary("planning-main-fixture".toCharArray(),"kanban-decoy-fixture".toCharArray())
            vault.lock();vault.unlock("kanban-decoy-fixture".toCharArray());val secondary=fixture(vault.current.value!!,"Secondary only")
            val decoy=vault.current.value!!.nodes.management.kanban
            assertTrue(kanbanTasks(decoy.observe(secondary).first(),primary,KanbanScope.PROJECT).isEmpty())
            assertTrue(runCatching {decoy.phase(primary,"same-task",com.r0ybt.arachn0de.domain.model.WorkState.VALIDATED)}.isFailure)
            decoy.phase(secondary,"same-task",com.r0ybt.arachn0de.domain.model.WorkState.PLANNED)
            vault.lock();vault.unlock("planning-main-fixture".toCharArray())
            assertEquals("Primary only",vault.current.value!!.database.personDao().get("same-self")!!.name)
            assertEquals("same-self",vault.current.value!!.database.cascadeRelationDao().identities().single().personId)
            val main=vault.current.value!!.nodes.management.kanban.observe(primary).first()
            assertEquals(listOf("Primary only"),kanbanTasks(main,primary,KanbanScope.SPRINT,"same-sprint").map {it.title})
            assertEquals("DOING",main.work.nodes.single {it.id=="same-task"}.workState)
            vault.lock();vault.unlock("kanban-decoy-fixture".toCharArray())
            assertEquals("Secondary only",vault.current.value!!.database.personDao().get("same-self")!!.name)
            assertEquals("same-self",vault.current.value!!.database.cascadeRelationDao().identities().single().personId)
            val other=vault.current.value!!.nodes.management.kanban.observe(secondary).first()
            assertEquals(listOf("Secondary only"),kanbanTasks(other,secondary,KanbanScope.BACKLOG).map {it.title})
            assertEquals("PLANNED",other.work.nodes.single {it.id=="same-task"}.workState)
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
