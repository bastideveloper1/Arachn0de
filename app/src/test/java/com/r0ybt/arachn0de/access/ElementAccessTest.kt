package com.r0ybt.arachn0de.access

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
class ElementAccessTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:AccessRepository
    @Before fun setup() {context.deleteDatabase("arachn0de.db");open()}
    private fun open() {db=Arachn0deDatabase.create(context);repo=AccessRepository(db)}
    @After fun close() {db.close()}
    @Test fun accentCaseMultilineDescriptionAndHierarchyUseOriginalIdentities()=runBlocking<Unit> {
        val project=ProjectRepository(db.projectDao(),db).createProject("Árbol")
        val nodes=NodeRepository(db);val layer=nodes.createNode(project.id,null,"Área",purpose=NodePurpose.LAYER)
        val task=nodes.createNode(project.id,layer.id,"REVISIÓN\nTÉCNICA","Información útil")
        val index=ElementSearch.index(listOf(project),nodes.getProjectNodes(project.id))
        val result=ElementSearch.find(index,"revision tecnica").single()
        assertEquals(task.id,result.nodeId);assertEquals("Árbol › Área",result.path)
        assertEquals(task.id,ElementSearch.find(index,"informacion UTIL").single().nodeId)
        nodes.updateNode(task.id,"Nuevo título","Texto cambiado")
        assertTrue(ElementSearch.find(ElementSearch.index(listOf(project),nodes.getProjectNodes(project.id)),"revision").isEmpty())
    }
    @Test fun favoritesSurviveReopenMoveAndDeletionWithoutChangingOrder()=runBlocking<Unit> {
        val project=ProjectRepository(db.projectDao(),db).createProject("P")
        val nodes=NodeRepository(db);val layer=nodes.createNode(project.id,null,"Capa",purpose=NodePurpose.LAYER)
        val task=nodes.createNode(project.id,null,"Tarea")
        repo.toggle("node:${task.id}");repo.toggle("project:${project.id}");repo.opened("node:${task.id}")
        db.close();open();assertEquals(2,repo.snapshot().favorites.size)
        nodesSafeMove(task.id,project.id,layer.id)
        val index=ElementSearch.index(listOf(project),NodeRepository(db).getProjectNodes(project.id))
        assertEquals("P › Capa",index.single {it.nodeId==task.id}.path)
        NodeRepository(db).deleteNode(task.id)
        assertEquals(setOf("project:${project.id}"),repo.snapshot().favorites)
        assertTrue(repo.snapshot().recent.isEmpty())
    }
    private suspend fun nodesSafeMove(id:String,project:String,parent:String) {NodeRepository(db).moveNode(id,parent)}
    @Test fun rapidCaptureRetriesOneIdentityThenAllowsNormalEditingAndBacksUpSettings()=runBlocking<Unit> {
        val project=ProjectRepository(db.projectDao(),db).createProject("P")
        val first=repo.capture("project:${project.id}","Rápida","Descripción","capture")
        val second=repo.capture("project:${project.id}","Rápida","Descripción","capture")
        assertEquals(first.id,second.id);assertEquals(1,db.backupDao().nodes().size)
        assertTrue(runCatching {repo.capture("project:${project.id}"," ","","empty")}.isFailure)
        repo.toggle("node:capture");repo.opened("node:capture")
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val data=backup.snapshot()
        repo.toggle("node:capture");backup.restore(data)
        assertEquals(setOf("node:capture"),repo.snapshot().favorites)
        assertEquals("project:${project.id}",repo.snapshot().destination)
        NodeRepository(db).updateNode(first.id,"Editada","Completa")
        assertEquals("Editada",db.nodeDao().getById(first.id)!!.title)
        assertEquals(1,db.backupDao().nodes().size)
    }
    @Test fun malformedAccessBackupIsRejectedBeforeReplacingExistingData()=runBlocking<Unit> {
        val project=ProjectRepository(db.projectDao(),db).createProject("Conservar")
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val data=backup.snapshot().copy(privatePreferences=listOf(PrivatePreferenceEntity(AccessCodec.NAME,com.r0ybt.arachn0de.security.EncryptedPreferences.encode(mapOf("version" to 99)))))
        assertTrue(runCatching {backup.restore(data)}.isFailure)
        assertNotNull(db.projectDao().getById(project.id))
    }
}
