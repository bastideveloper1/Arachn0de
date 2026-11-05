package com.r0ybt.arachn0de.ux

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoveContainerTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:NodeRepository
    @Before fun setup() {context.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(context);repo=NodeRepository(db)}
    @After fun close() {db.close()}
    private suspend fun core(id:String) {ProjectRepository(db.projectDao(),db).createProject(id,creationId=id)}
    private fun backup()=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
    @Test fun tasksMoveBetweenLayersCoresAndDraftsWithoutChangingIdentity()=runBlocking<Unit> {
        core("p");core("q")
        val a=repo.createNode("p",null,"A",purpose=NodePurpose.LAYER)
        val b=repo.createNode("p",null,"B",purpose=NodePurpose.LAYER)
        val c=repo.createNode("q",null,"C",purpose=NodePurpose.LAYER)
        val task=repo.createNode("p",a.id,"Pago","Contenido",obligation=Obligation(1200,"CLP"))
        val before=repo.getNode(task.id)!!
        assertTrue(repo.moveToContainer(setOf(task.id),"p",b.id));assertEquals(b.id,repo.getNode(task.id)!!.parentId)
        assertTrue(repo.moveToContainer(setOf(task.id),"q",c.id));val after=repo.getNode(task.id)!!
        assertEquals("q",after.projectId);assertEquals(c.id,after.parentId);assertEquals(before.description,after.description);assertEquals(before.obligation,after.obligation)
        val draft=repo.createNode(null,null,"Nota",purpose=NodePurpose.NOTE)
        assertTrue(repo.moveToContainer(setOf(draft.id),"q",c.id));assertEquals("q",repo.getNode(draft.id)!!.projectId)
    }
    @Test fun completeLayerRetainsAttachmentsAssignmentsDefaultsRecurrencesAndBackup()=runBlocking<Unit> {
        // Simulate an existing installation carrying the previous persistent trigger.
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER drafts_project_move")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER drafts_project_move BEFORE UPDATE OF projectId ON nodes WHEN NEW.projectId IS NOT OLD.projectId AND NEW.purpose='LAYER' BEGIN SELECT RAISE(ABORT,'old guard'); END")
        db.close();db=Arachn0deDatabase.create(context);repo=NodeRepository(db)
        core("p");core("q")
        val layer=repo.createNode("p",null,"Capa",purpose=NodePurpose.LAYER)
        val inner=repo.createNode("p",layer.id,"Interior",purpose=NodePurpose.LAYER)
        val task=repo.createNode("p",inner.id,"Tarea","Descripción",priority=Priority.HIGH)
        val note=repo.createNode("p",layer.id,"Nota",purpose=NodePurpose.NOTE)
        db.personDao().insert(PersonEntity("ana","Ana",null));db.personDao().assign(listOf(NodePersonEntity(task.id,"ana")))
        db.tagDao().insert(listOf(TagEntity("tag","Etiqueta","etiqueta")));db.tagDao().assignNodes(listOf(NodeTagEntity(task.id,"tag")))
        db.creationDefaultsDao().save(CreationDefaultsEntity("N:${layer.id}","p",layer.id,purpose="NOTE",tagsOverride=true))
        db.creationDefaultsDao().insertTags(listOf(CreationDefaultsTagEntity("N:${layer.id}","tag")))
        val audio=com.r0ybt.arachn0de.audio.AudioDraftStore(context,db,BackupFixture::syncDirectory)
        val recording=audio.begin(task.id)
        val bytes=javaClass.classLoader!!.getResourceAsStream("audio/tone.aac")!!.use {it.readBytes()}
        audio.append(recording,0,bytes);val file=requireNotNull(audio.finish(recording))
        val rule=RecurrenceRuleEntity("rule","p",inner.id,"Mensual","",100,"CLP",1,"MONTHLY",1,null,0,"ACTIVE","America/Santiago",0,null)
        db.recurrenceDao().insert(rule)
        val before=backup().snapshot()
        assertTrue(repo.moveToContainer(setOf(layer.id,task.id),"q",null))
        val after=backup().snapshot()
        val ids=setOf(layer.id,inner.id,task.id,note.id)
        assertEquals(ids,after.nodes.filter {it.projectId=="q"}.map {it.id}.toSet())
        assertEquals(inner.id,repo.getNode(task.id)!!.parentId)
        assertEquals(before.assignments,after.assignments);assertEquals(before.nodeTags,after.nodeTags)
        assertEquals(before.nodeAttachments,after.nodeAttachments);assertEquals(before.attachmentFiles,after.attachmentFiles)
        assertArrayEquals(before.attachmentContents.getValue(file.storageName).readBytes(),after.attachmentContents.getValue(file.storageName).readBytes())
        assertEquals("q",db.creationDefaultsDao().all().single().projectId)
        assertEquals(before.defaultsTags,after.defaultsTags)
        assertEquals("q",db.recurrenceDao().rules().single().projectId)
        val decoded=BackupJson.decode(BackupJson.encode(after));assertEquals(after.nodes.toSet(),decoded.nodes.toSet())
        backup().restore(after);assertEquals(ids,db.backupDao().nodes().filter {it.projectId=="q"}.map {it.id}.toSet())
        assertArrayEquals(bytes,backup().snapshot().attachmentContents.values.single().readBytes())
    }
    @Test fun paidTaskKeepsPartialPaymentHistoryAndBankAccountsCannotMove()=runBlocking<Unit> {
        core("p");core("q")
        db.personDao().insert(PersonEntity("yo","Yo",null))
        val bank=com.r0ybt.arachn0de.bank.BankRepository(db)
        bank.createRoot("Banco","","bank");bank.createAccount("bank","Cuenta","yo")
        val account=bank.state().accounts.single()
        val task=repo.createNode("p",null,"Pago",obligation=Obligation(1200,"CLP"),responsibleIds=setOf("yo"))
        val request=com.r0ybt.arachn0de.bank.BankRequest(rootId="bank",contextAccountId=account.id,kind="DIRECT",amount=400,day=20000,concept="",sourcePersonId="yo",personId="yo")
        bank.confirm(bank.preview(request))
        val before=bank.state()
        assertTrue(repo.moveToContainer(setOf(task.id),"q",null))
        val after=bank.state()
        assertEquals(before.operations,after.operations);assertEquals(before.allocations,after.allocations)
        assertEquals(800L,after.remaining(db.nodeDao().getById(task.id)!!))
        assertEquals(before.balance(account.id),after.balance(account.id))
        assertTrue(runCatching {repo.moveToContainer(setOf(account.id),"q",null)}.isFailure)
        assertTrue(runCatching {repo.moveToContainer(setOf(task.id),"bank",null)}.isFailure)
        backup().snapshot().validate()
    }
    @Test fun invalidDestinationsAndMidMoveFailureRollbackEntireTree()=runBlocking<Unit> {
        core("p");core("q")
        val layer=repo.createNode("p",null,"Capa",purpose=NodePurpose.LAYER)
        val child=repo.createNode("p",layer.id,"Hija",purpose=NodePurpose.LAYER)
        val leaf=repo.createNode("q",null,"Tarea")
        val before=db.backupDao().nodes()
        assertTrue(runCatching {repo.moveToContainer(setOf(layer.id),"p",child.id)}.isFailure)
        assertTrue(runCatching {repo.moveToContainer(setOf(layer.id),"q",leaf.id)}.isFailure)
        assertEquals(before,db.backupDao().nodes())
        assertTrue(runCatching {db.openHelper.writableDatabase.execSQL("UPDATE nodes SET projectId=NULL,parentId=NULL WHERE id=?",arrayOf(layer.id))}.isFailure)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_move_child BEFORE UPDATE OF projectId ON nodes WHEN OLD.id='${child.id}' BEGIN SELECT RAISE(ABORT,'injected'); END")
        assertTrue(runCatching {repo.moveToContainer(setOf(layer.id),"q",null)}.isFailure)
        assertEquals(before,db.backupDao().nodes())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_move_child")
        assertTrue(repo.moveToContainer(setOf(layer.id),"q",null))
    }
}
