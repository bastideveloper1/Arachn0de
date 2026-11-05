package com.r0ybt.arachn0de.report

import android.app.Application
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.diagrams.DiagramExportJournal
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardTreeImagesTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var session:VaultSession
    private lateinit var root:File
    private lateinit var stageRoot:File
    private lateinit var provider:ImagesDocuments
    private val tree=DocumentsContract.buildTreeDocumentUri("card-tree-test","folder")
    private val colors=CardShareColors(Color.BLACK,Color.WHITE,Color.MAGENTA)
    private fun journal()=DiagramExportJournal(app,session.context,BackupFixture::syncDirectory,persist={},cards=true)
    @Before fun before()=runBlocking<Unit> {
        root=File(app.filesDir,"card-tree-${UUID.randomUUID()}").apply {mkdirs()}
        val access=SecureFiles.Session(UUID.randomUUID(),root,VaultCrypto.randomKey());SecureFiles.register(access);SecureFiles.activeProfile=access.id
        val context=VaultContext(app,access)
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).allowMainThreadQueries().build();context.database=db
        session=VaultSession(access,context,db,true);stageRoot=File(context.cacheDir,"public-exports-v1")
        provider=ImagesDocuments(File(app.cacheDir,"public-destination-${UUID.randomUUID()}").apply {mkdirs()})
        provider.attachInfo(app,ProviderInfo().apply {authority="card-tree-test";exported=true;grantUriPermissions=true;readPermission="android.permission.MANAGE_DOCUMENTS";writePermission=readPermission})
        ShadowContentResolver.registerProviderInternal("card-tree-test",provider)
        db.projectDao().insert(ProjectEntity("p","Núcleo","Descripción del Núcleo",0,1,1))
        db.projectDao().insert(ProjectEntity("foreign","No exportar","",1,1,1))
        fun row(id:String,parent:String?,position:Int,purpose:String="ACTION",complete:Boolean=false,project:String="p")=NodeEntity(id,project,parent,"Título repetido","Texto completo $id",complete,position,1,1,purpose=purpose)
        listOf(row("layer",null,0,"LAYER"),row("task","layer",0),row("note","layer",1,"NOTE"),row("inner","layer",2,"LAYER"),row("nested","inner",0),row("done","layer",-1,complete=true),row("sibling",null,1),row("empty",null,2,"LAYER"),row("other",null,0,project="foreign")).forEach {db.nodeDao().insert(it)}
    }
    @After fun after() {
        // No audio is active. A paused Robolectric main looper cannot join media jobs.
        session.close();root.deleteRecursively();provider.root.deleteRecursively()
        val active=DiagramExportJournal::class.java.getDeclaredField("active").apply {isAccessible=true}.get(null) as MutableSet<*>;active.clear()
    }
    private fun exporter(j:DiagramExportJournal=journal())=CardTreeImages(app,session.context,j,BackupFixture::syncDirectory)
    private fun checkOpen()=session.access.checkOpen()
    private suspend fun source(item:CardImageItem)=if(item.project)CardShareSource.projectSnapshot(session,item.id) else CardShareSource.snapshot(session,item.id)
    private fun image(color:Int):ByteArray {
        val bitmap=Bitmap.createBitmap(160,80,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color)
        for(y in 0 until 12)for(x in 0 until 12)bitmap.setPixel(x,y,Color.WHITE)
        return try {ByteArrayOutputStream().also {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}.toByteArray()}finally {bitmap.recycle()}
    }
    private suspend fun attach(owner:String,color:Int,project:Boolean=false):AttachmentFileEntity {
        val bytes=image(color);val id=UUID.randomUUID().toString()
        val row=AttachmentRepository.fileOperations.withLock {
            val store=AttachmentStore(session.context,BackupFixture::syncDirectory);val file=store.importBytes(bytes,"imagen-$id.png","fixture")
            session.database.attachmentDao().insert(file)
            if(project)session.database.attachmentDao().attachProject(listOf(ProjectAttachmentEntity(owner,file.id))) else session.database.attachmentDao().attachNode(listOf(NodeAttachmentEntity(owner,file.id)))
            store.release(file.id);file
        }
        return row
    }
    @Test fun pendingCopyIncludesVerificationNotesAndNestedElementsButNeverCompletedTasks()=runBlocking<Unit> {
        session.database.nodeDao().markAwaitingVerification("task",2L)
        val before=session.database.backupDao().nodes()
        assertEquals(listOf("p","layer","task","note","inner","nested","sibling","empty"),CardImageTree.capture(session,"p",null).map {it.id})
        assertEquals(listOf("layer","task","note","inner","nested"),CardImageTree.capture(session,"p","layer").map {it.id})
        for(id in listOf("empty","task","note"))assertTrue(runCatching {CardImageTree.capture(session,"p",id)}.isFailure)
        assertEquals("Por verificar",CardShareSource.snapshot(session,"task",onlyPending=true).status)
        assertEquals("Pendiente",CardShareSource.snapshot(session,"nested",onlyPending=true).status)
        assertTrue(runCatching {CardShareSource.snapshot(session,"done",onlyPending=true)}.isFailure)
        assertEquals(before,session.database.backupDao().nodes())
    }
    @Test fun coreAndLayerWriteSeparateRealPngsExactlyLikeIndividualWithAllThreeImages()=runBlocking<Unit> {
        val rows=listOf(attach("task",Color.RED),attach("task",Color.GREEN),attach("task",Color.BLUE))
        val node=session.database.nodeDao().getById("task")!!
        session.database.nodeDao().updateContent(node.id,node.title,"Descripción completa\n"+rows.joinToString("\n") {AttachmentReferences.token(it.id)},node.updatedAt)
        val coreImage=attach("p",Color.CYAN,true)
        val project=session.database.projectDao().getById("p")!!
        session.database.projectDao().update(project.id,project.name,project.description+"\n"+AttachmentReferences.token(coreImage.id),project.updatedAt)
        val originals=(rows+coreImage).associate {it.id to session.attachments.localFile(it).readBytes()}
        val before=session.database.backupDao().nodes()
        val individual=CardShareFiles(session.context,sync=BackupFixture::syncDirectory)
        val png=individual.generate({CardShareSource.snapshot(session,"task")},colors)
        val expected=BitmapFactory.decodeByteArray(SecureFiles.read(png),0,SecureFiles.read(png).size)!!
        individual.discard(png)
        val items=CardImageTree.capture(session,"p",null)
        assertEquals(items.size,exporter().save(tree,items,colors,::checkOpen,::source))
        assertEquals(items.size,provider.entries.size)
        val files=provider.entries.values.toList();assertEquals(files.map {it.name}.sorted(),files.map {it.name})
        val task=files[items.indexOfFirst {it.id=="task"}]
        val actual=BitmapFactory.decodeFile(task.file.path)!!
        try {
            assertTrue(expected.sameAs(actual))
            val pixels=IntArray(actual.width*actual.height);actual.getPixels(pixels,0,actual.width,0,0,actual.width,actual.height)
            for(color in listOf(Color.RED,Color.GREEN,Color.BLUE))assertTrue("Real full-size image missing",pixels.count {it==color}>100_000)
        }finally {actual.recycle();expected.recycle()}
        files.forEach {entry->assertEquals("image/png",entry.mime);BitmapFactory.decodeFile(entry.file.path)!!.recycle()}
        (rows+coreImage).forEach {assertArrayEquals(originals.getValue(it.id),session.attachments.localFile(it).readBytes())}
        assertEquals(before,session.database.backupDao().nodes());assertTrue(stageRoot.listFiles().orEmpty().isEmpty())
        val previous=provider.entries.keys.toSet()
        val branch=CardImageTree.capture(session,"p","layer");exporter().save(tree,branch,colors,::checkOpen,::source)
        assertEquals(items.size+branch.size,provider.entries.size);assertTrue(provider.entries.keys.containsAll(previous))
        assertEquals(provider.entries.size,provider.entries.values.map {it.name}.distinct().size)
    }
    @Test fun twentyTasksProduceTwentyOneFilesWithoutFillingTheShareCache()=runBlocking<Unit> {
        val items=(0..20).map {CardImageItem("synthetic-$it","Repetido")}
        assertEquals(21,exporter().save(tree,items,colors,::checkOpen) {CardShareData(it.title,"Contenido completo")})
        assertEquals(21,provider.entries.size);assertEquals(21,provider.entries.values.map {it.name}.distinct().size)
        assertTrue(stageRoot.listFiles().orEmpty().isEmpty());assertFalse(File(session.context.cacheDir,CardShareFiles.DIRECTORY).exists())
    }
    @Test fun failureAndCancellationDeleteOnlyCurrentPartialAndPreserveCompletedPngs()=runBlocking<Unit> {
        val existing=provider.createDocument("folder","image/png","user-original.png")
        val items=listOf(CardImageItem("1","uno"),CardImageItem("2","dos"))
        provider.failAt=2;val export=exporter()
        assertTrue(runCatching {export.save(tree,items,colors,::checkOpen) {CardShareData(it.title,"")}}.isFailure)
        assertEquals(1,export.saved);assertFalse(export.cleanupPending);assertEquals(2,provider.entries.size);assertTrue(existing in provider.entries)
        assertTrue(stageRoot.listFiles().orEmpty().isEmpty())
        provider.failAt=0;provider.cancelAt=provider.opens+1
        val cancelled=runCatching {exporter().save(tree,items,colors,::checkOpen) {CardShareData(it.title,"")}}.exceptionOrNull()
        assertTrue(cancelled is CancellationException);assertEquals(2,provider.entries.size)
        assertTrue(stageRoot.listFiles().orEmpty().isEmpty())
    }
    @Test fun retryResumesAfterSixCompletedPngsIncludingAnnotatedImagesWithoutDuplicates()=runBlocking<Unit> {
        val export=exporter()
        val items=(1..8).map {CardImageItem("item-$it","Tarjeta $it")}
        val base=image(Color.GREEN)
        val bitmap=BitmapFactory.decodeByteArray(base,0,base.size)!!
        val marks=listOf(com.r0ybt.arachn0de.annotations.ImageMark(tool=com.r0ybt.arachn0de.annotations.MarkTool.RECT,
            points=listOf(com.r0ybt.arachn0de.annotations.MarkPoint(.1f,.1f),com.r0ybt.arachn0de.annotations.MarkPoint(.8f,.8f))))
        val annotated=com.r0ybt.arachn0de.annotations.EditableImagePng.write(com.r0ybt.arachn0de.annotations.markedPng(bitmap,marks),base,marks);bitmap.recycle()
        val source:suspend (CardImageItem)->CardShareData={item->CardShareData(item.title,"",if(item.id=="item-7") (1..10).map {CardSharePicture("Anotada $it") {BitmapFactory.decodeByteArray(annotated,0,annotated.size)}} else emptyList())}
        provider.failAt=7
        assertTrue(runCatching {export.save(tree,items,colors,::checkOpen,source)}.isFailure)
        assertEquals(6,export.saved);assertEquals(6,provider.entries.size)
        val completed=provider.entries.keys.toSet()
        provider.failAt=0
        assertEquals(8,export.save(tree,items,colors,::checkOpen,source))
        assertEquals(8,provider.entries.size);assertTrue(provider.entries.keys.containsAll(completed))
        provider.entries.values.forEach {val png=BitmapFactory.decodeFile(it.file.path)!!;assertTrue(png.height<=CardShareRenderer.MAX_HEIGHT);png.recycle()}
    }

    @Test fun deniedDeletionLeavesEncryptedJournalAndRecoveryPreservesFinishedAndUnknownFiles()=runBlocking<Unit> {
        val j=journal();provider.failAt=1;provider.refuseDelete=true;val export=exporter(j)
        assertTrue(runCatching {export.save(tree,listOf(CardImageItem("1","uno")),colors,::checkOpen) {CardShareData("uno","")}}.isFailure)
        assertTrue(export.cleanupPending);assertEquals(1,provider.entries.size);assertTrue(stageRoot.listFiles().orEmpty().isEmpty())
        assertTrue(runCatching {export.save(tree,listOf(CardImageItem("1","uno")),colors,::checkOpen) {CardShareData("uno","")}}.isFailure)
        assertTrue(export.cleanupPending);assertEquals(1,provider.entries.size)
        val marker=File(session.context.filesDir,"card-image-export-journal").listFiles()!!.single {it.extension=="json"}
        assertFalse(marker.readBytes().toString(Charsets.ISO_8859_1).contains(tree.toString()))
        // Simulate loss between SAF create and URI recording; recover by the exact unique name.
        val json=JSONObject(SecureFiles.text(marker)).put("uri",JSONObject.NULL);SecureFiles.write(marker,json.toString().toByteArray())
        val unknown=provider.createDocument("folder","image/png","user-owned.png")
        provider.refuseDelete=false;provider.failAt=0
        val active=DiagramExportJournal::class.java.getDeclaredField("active").apply {isAccessible=true}.get(null) as MutableSet<*>;active.clear()
        assertTrue(j.recover().isEmpty());assertEquals(setOf(unknown),provider.entries.keys);assertFalse(marker.exists())
    }
    @Test fun createFailureAfterRemoteCommitKeepsRecoverableNameAndWarnsAboutPartial()=runBlocking<Unit> {
        val j=journal();val export=exporter(j);provider.throwAfterCreate=true;provider.refuseDelete=true
        assertTrue(runCatching {export.save(tree,listOf(CardImageItem("1","uno")),colors,::checkOpen) {CardShareData("uno","")}}.isFailure)
        assertTrue(export.cleanupPending);assertEquals(1,provider.entries.size)
        assertTrue(stageRoot.listFiles().orEmpty().isEmpty())
        provider.throwAfterCreate=false;provider.refuseDelete=false
        assertTrue(j.recover().isEmpty());assertTrue(provider.entries.isEmpty())
    }
    @Test fun lockingDuringDestinationOpenRevokesKeysAndDoesNotKeepTheFileMutex()=runBlocking<Unit> {
        val export=exporter();provider.onOpen={session.access.beginClosing()}
        assertTrue(runCatching {export.save(tree,listOf(CardImageItem("1","uno")),colors,::checkOpen) {CardShareData("uno","")}}.isFailure)
        assertTrue(runCatching {session.access.withKey {it.size}}.isFailure)
        assertTrue(stageRoot.listFiles().orEmpty().isEmpty());assertTrue(provider.entries.isEmpty())
    }

    private class ImagesDocuments(val root:File):DocumentsProvider() {
        data class Entry(val name:String,val mime:String,val file:File)
        val entries=linkedMapOf<String,Entry>();var opens=0;var failAt=0;var cancelAt=0;var refuseDelete=false;var onOpen:(()->Unit)?=null;var throwAfterCreate=false
        private fun unlocked() {check(AttachmentRepository.fileOperations.tryLock()) {"SAF called while holding source-file mutex"};AttachmentRepository.fileOperations.unlock()}
        override fun onCreate()=true
        override fun isChildDocument(parentDocumentId:String,documentId:String)=parentDocumentId=="folder"
        override fun queryRoots(projection:Array<out String>?):Cursor=MatrixCursor(arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID))
        private fun cursor(projection:Array<out String>?,ids:List<String>):Cursor {
            unlocked();val cols=projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            return MatrixCursor(cols).apply {ids.forEach {id->val e=entries[id];addRow(cols.map<String,Any?> {when(it) {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID->id
                DocumentsContract.Document.COLUMN_DISPLAY_NAME->e?.name ?: "folder"
                DocumentsContract.Document.COLUMN_MIME_TYPE->e?.mime ?: DocumentsContract.Document.MIME_TYPE_DIR
                DocumentsContract.Document.COLUMN_FLAGS->DocumentsContract.Document.FLAG_SUPPORTS_DELETE or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                else->null
            }}.toTypedArray())}}
        }
        override fun queryDocument(id:String,projection:Array<out String>?)=cursor(projection,if(id=="folder"||id in entries)listOf(id) else emptyList())
        override fun queryChildDocuments(id:String,projection:Array<out String>?,sortOrder:String?)=cursor(projection,entries.keys.toList())
        override fun createDocument(parentDocumentId:String,mimeType:String,displayName:String):String {
            unlocked();check(entries.values.none {it.name==displayName}) {"Would overwrite an existing name"}
            val id=UUID.randomUUID().toString();entries[id]=Entry(displayName,mimeType,File(root,id).apply {createNewFile()})
            if(throwAfterCreate)throw java.io.FileNotFoundException("synthetic create committed before failure")
            return id
        }
        override fun deleteDocument(documentId:String) {unlocked();check(!refuseDelete);entries.remove(documentId)?.file?.delete()}
        override fun openDocument(documentId:String,mode:String,signal:CancellationSignal?):ParcelFileDescriptor {
            unlocked();opens++;onOpen?.invoke()
            if(opens==cancelAt)throw CancellationException("synthetic cancel")
            if(opens==failAt)throw java.io.FileNotFoundException("synthetic full destination")
            return ParcelFileDescriptor.open(entries.getValue(documentId).file,ParcelFileDescriptor.parseMode(mode))
        }
    }
}
