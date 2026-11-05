package com.r0ybt.arachn0de.templates

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StructuralTemplatesTest {
    private lateinit var db:Arachn0deDatabase
    private lateinit var context:VaultContext
    private lateinit var session:SecureFiles.Session
    private lateinit var repo:SavedTemplateRepository
    private lateinit var attachments:AttachmentRepository
    private lateinit var root:File
    @Before fun setup() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val id=UUID.randomUUID();root=File(app.filesDir,"structural-$id").apply {mkdirs()}
        session=SecureFiles.Session(id,root,VaultCrypto.randomKey());SecureFiles.register(session)
        context=VaultContext(app,session)
        db=Room.inMemoryDatabaseBuilder(app,Arachn0deDatabase::class.java).build();context.database=db
        repo=SavedTemplateRepository(db);attachments=AttachmentRepository(db,AttachmentStore(context,BackupFixture::syncDirectory))
    }
    @After fun close() {db.close();context.clearMemory();SecureFiles.revoke(session);root.deleteRecursively()}
    private suspend fun source():String {
        db.projectDao().insert(ProjectEntity("source","Origen","Contenido",0,1,1,verificationEnabled=true))
        db.nodeDao().insert(NodeEntity("layer","source",null,"Capa","Texto",false,0,1,1,purpose="LAYER",taskStateCount=3))
        db.nodeDao().insert(NodeEntity("nested","source","layer","Subcapa","",false,0,1,1,purpose="LAYER"))
        db.nodeDao().insert(NodeEntity("task","source","nested","Tarea","Contenido reutilizable",true,0,1,1,startAt=100,dueAt=200,effort=4,priority="HIGH"))
        db.nodeDao().insert(NodeEntity("note","source","layer","Nota","Texto de nota",false,1,1,1,purpose="NOTE"))
        val tag=TagRepository(db).create("Trabajo");TagRepository(db).assignNode("task",setOf(tag.id))
        db.conversionDao().save(ConversionRootEntity("source-root","source",null,"source","source-node",0,0,null,null,"HIGH",null,false))
        db.conversionDao().tags(listOf(ConversionTagEntity("source-root",tag.id)))
        val file=attachments.importGuestBytes(BackupFixture.png(),"source-editor","imagen.png")
        attachments.saveNodeDraft("source-editor",listOf("task"),AttachmentReferences.token(file.id),emptySet()) {
            db.nodeDao().updateContent("task","Tarea",AttachmentReferences.token(file.id),2)
        }
        attachments.associateNode("note",file.id,"Texto de nota\n"+AttachmentReferences.token(file.id))
        val photos=ProjectPhotoStore(context,BackupFixture::syncDirectory);val name="${UUID.randomUUID()}.png"
        photos.durable.write(name,BackupFixture.png());photos.durable.finishStaging()
        db.projectPhotoDao().save(ProjectPhotoEntity(UUID.randomUUID().toString(),"source",null,name,1.2f,0f,0f))
        db.creationDefaultsDao().save(CreationDefaultsEntity("N:layer","source","layer",purpose="NOTE",peopleOverride=true))
        return file.id
    }
    private suspend fun insert(row:SavedTemplateEntity,project:String?=null,parent:String?=null)=
        repo.insertStructure(context,attachments,row,project,parent,BackupFixture::syncDirectory)

    @Test fun historicalForeignCurrencyTemplateIsReadableButRejectedBeforeImportingResources()=runBlocking<Unit> {
        source()
        db.openHelper.writableDatabase.execSQL("UPDATE nodes SET amountMinor=1050,currencyCode='USD' WHERE id='task'")
        val row=repo.captureStructure(context,attachments,"source",null)
        repo.save(row,true)
        val before=db.backupDao().nodes()
        val files=root.walkTopDown().filter {it.isFile}.associate {it.relativeTo(root).path to it.readBytes().toList()}
        val failure=runCatching {insert(row)}.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(PaymentCurrencyPolicy.MESSAGE,failure?.message)
        assertEquals(before,db.backupDao().nodes())
        assertEquals(row,db.savedTemplateDao().get(row.id))
        assertEquals(files,root.walkTopDown().filter {it.isFile}.associate {it.relativeTo(root).path to it.readBytes().toList()})
    }

    @Test fun completeTreeCreatesIndependentInstancesAndResourcesWithoutOperationalData()=runBlocking<Unit> {
        val file=source();val before=db.nodeDao().getProjectNodes("source")
        val row=repo.captureStructure(context,attachments,"source",null);repo.save(row,true)
        val first=insert(row);val second=insert(row)
        val a=db.nodeDao().getProjectNodes(first);val b=db.nodeDao().getProjectNodes(second)
        assertEquals(before,db.nodeDao().getProjectNodes("source"));assertEquals(4,a.size);assertEquals(4,b.size)
        assertTrue(a.none {it.id in before.map {n->n.id} || it.id in b.map {n->n.id}})
        assertEquals(2,a.count {it.purpose=="LAYER"});assertEquals(1,a.count {it.purpose=="NOTE"})
        assertTrue(a.all {!it.isCompleted && !it.awaitingVerification && it.workState==null && !it.sprintMode && it.startAt==null && it.dueAt==null})
        val task=a.single {it.title=="Tarea"};assertEquals(4,task.effort);assertEquals("HIGH",task.priority)
        assertEquals("Subcapa",a.single {it.id==task.parentId}.title)
        assertFalse(AttachmentReferences.ids(task.description).contains(file))
        val copied=attachments.filesForNode(task.id).single();val other=attachments.filesForNode(b.single {it.title=="Tarea"}.id).single()
        assertNotEquals(copied.id,other.id);assertNotEquals(copied.storageName,other.storageName)
        assertArrayEquals(BackupFixture.png(),SecureFiles.read(attachments.localFile(copied)))
        assertFalse(attachments.localFile(copied).readBytes().contentEquals(BackupFixture.png()))
        assertEquals(copied.id,attachments.filesForNode(a.single {it.title=="Nota"}.id).single().id)
        attachments.detachNode(task.id,copied.id)
        assertArrayEquals(BackupFixture.png(),SecureFiles.read(attachments.localFile(copied)))
        assertEquals(1,db.tagDao().nodeIds(task.id).size)
        assertEquals(1,db.conversionDao().tags().count {it.rootId==db.conversionDao().project(first)!!.id})
        assertEquals("NOTE",db.creationDefaultsDao().forProject(first).single {it.nodeId!=null}.purpose)
        assertNotEquals(db.projectPhotoDao().forProject("source")!!.file,db.projectPhotoDao().forProject(first)!!.file)
        val replacement=repo.captureStructure(context,attachments,"source","note",false)
        repo.save(replacement.copy(id=row.id,name=row.name),false)
        assertArrayEquals(BackupFixture.png(),SecureFiles.read(attachments.localFile(copied)))
        repo.delete(row.id);db.projectDao().delete("source");attachments.cleanup()
        assertArrayEquals(BackupFixture.png(),SecureFiles.read(attachments.localFile(copied)))
        assertEquals(4,db.nodeDao().getProjectNodes(second).size)
    }
    @Test fun allTypesCompatibleDestinationsLegacyAndFailureRollbackKeepPreviousData()=runBlocking<Unit> {
        source()
        for(id in listOf("layer","task","note")) {
            val row=repo.captureStructure(context,attachments,"source",id,false)
            val inserted=insert(row,"source","layer")
            assertEquals(db.nodeDao().getById(id)!!.purpose,db.nodeDao().getById(inserted)!!.purpose)
            assertEquals("layer",db.nodeDao().getById(inserted)!!.parentId)
        }
        val legacy=SavedTemplateEntity("legacy","Antigua",SavedTemplateCodec.encode(SavedTaskConfiguration("Antigua")))
        SavedTemplateCodec.validate(legacy);assertEquals("Antigua",db.nodeDao().getById(insert(legacy,"source"))!!.title)
        val row=repo.captureStructure(context,attachments,"source",null)
        val projects=db.projectDao().getAll();val files=db.attachmentDao().files().map {it.id}.toSet()
        val photoFiles=File(context.filesDir,"project-photos").listFiles().orEmpty().filter {it.extension=="png"}.map {it.name}.toSet()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER template_failure BEFORE INSERT ON nodes BEGIN SELECT RAISE(ABORT,'injected'); END")
        assertTrue(runCatching {insert(row)}.isFailure)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER template_failure")
        attachments.cleanup()
        assertEquals(projects,db.projectDao().getAll());assertEquals(files,db.attachmentDao().files().map {it.id}.toSet())
        assertEquals(photoFiles,File(context.filesDir,"project-photos").listFiles().orEmpty().filter {it.extension=="png"}.map {it.name}.toSet())
        assertTrue(runCatching {insert(row,"source","task")}.isFailure)
        val corrupt=JSONObject(row.payload);corrupt.getJSONArray("elements").getJSONObject(1).put("parent",999)
        assertTrue(runCatching {SavedTemplateCodec.validate(row.copy(payload=corrupt.toString()))}.isFailure)
    }
    @Test fun cancellationAfterPhotoStagingRollsBackAndReleasesOnlyNewFiles()=runBlocking<Unit> {
        source();val row=repo.captureStructure(context,attachments,"source",null)
        val beforeProjects=db.projectDao().getAll();val beforeFiles=db.attachmentDao().files().map {it.id}.toSet()
        val photos=File(context.filesDir,"project-photos").listFiles().orEmpty().filter {it.extension=="png"}.map {it.name}.toSet()
        var interrupt=true
        val result=runCatching {repo.insertStructure(context,attachments,row,null,null) {dir->
            if(dir.name=="project-photos" && interrupt) {interrupt=false;throw kotlinx.coroutines.CancellationException("injected")}
            BackupFixture.syncDirectory(dir)
        }}
        assertTrue(result.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertEquals(beforeProjects,db.projectDao().getAll());assertEquals(beforeFiles,db.attachmentDao().files().map {it.id}.toSet())
        assertEquals(photos,File(context.filesDir,"project-photos").listFiles().orEmpty().filter {it.extension=="png"}.map {it.name}.toSet())
        assertTrue(File(context.filesDir,"template-insertions").listFiles().orEmpty().isEmpty())
    }
    @Test fun draftTasksAndNotesCanBeSavedAndInsertedWithoutAcceptingLayers()=runBlocking<Unit> {
        source()
        for(kind in listOf("ACTION","NOTE")) {
            val original=NodeEntity("draft-$kind",null,null,"Borrador $kind","Texto",false,0,1,1,purpose=kind)
            db.nodeDao().insert(original)
            val row=repo.captureStructure(context,attachments,null,original.id)
            val copy=requireNotNull(db.nodeDao().getById(insert(row)))
            assertNull(copy.projectId);assertNull(copy.parentId);assertEquals(kind,copy.purpose)
            assertEquals(original,db.nodeDao().getById(original.id))
            assertEquals("source",db.nodeDao().getById(insert(row,"source","layer"))!!.projectId)
        }
        val layer=repo.captureStructure(context,attachments,"source","layer")
        assertTrue(runCatching {insert(layer)}.isFailure)
        val task=repo.captureStructure(context,attachments,"source","task",false)
        assertTrue(runCatching {insert(task,"source","note")}.isFailure)
    }
    @Test fun encryptedBackupRestoresOldAndNewTemplatesAndInterruptedImportsAreRecoverable()=runBlocking<Unit> {
        source();val row=repo.captureStructure(context,attachments,"source",null);repo.save(row,true)
        repo.save(SavedTemplateEntity("old","Anterior",SavedTemplateCodec.encode(SavedTaskConfiguration("Anterior"))),true)
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val archive=backup.create("structural-fixture".toCharArray())
        val candidate=backup.inspect(SecureFiles.input(archive),"structural-fixture".toCharArray())
        repo.delete(row.id);backup.restore(candidate);backup.discard(candidate);backup.discardPending(archive.name)
        assertEquals(row.payload,db.savedTemplateDao().get(row.id)!!.payload)
        assertEquals(4,db.nodeDao().getProjectNodes(insert(db.savedTemplateDao().get(row.id)!!)).size)
        val id=UUID.randomUUID().toString();val dir=File(context.filesDir,"template-insertions").apply {mkdirs()}
        com.r0ybt.arachn0de.backup.durableWrite(File(dir,"$id.pending"),id.toByteArray())
        val imported=attachments.importGuestBytes(BackupFixture.png(),"template:$id","interrumpida.png")
        TemplateInsertionRecovery.recover(context,attachments,BackupFixture::syncDirectory)
        assertNull(db.attachmentDao().get(imported.id));assertFalse(File(dir,"$id.pending").exists())
    }
}
