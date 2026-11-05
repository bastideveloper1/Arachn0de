package com.r0ybt.arachn0de.annotations

import android.app.Application
import android.graphics.*
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageAnnotationsTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private fun image():Bitmap=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.WHITE)}
    private fun marks()=listOf(
        ImageMark(tool=MarkTool.PEN,points=listOf(MarkPoint(.1f,.1f),MarkPoint(.5f,.5f))),
        ImageMark(tool=MarkTool.RECT,points=listOf(MarkPoint(.1f,.5f),MarkPoint(.5f,.8f))),
        ImageMark(tool=MarkTool.ELLIPSE,points=listOf(MarkPoint(.6f,.4f),MarkPoint(.9f,.7f))),
        ImageMark(tool=MarkTool.LINE,points=listOf(MarkPoint(.1f,.9f),MarkPoint(.8f,.9f))),
        ImageMark(tool=MarkTool.ARROW,points=listOf(MarkPoint(.5f,.2f),MarkPoint(.8f,.3f))),
        ImageMark(tool=MarkTool.MARKER,points=listOf(MarkPoint(.5f,.5f)),text="Primera observación\nSegunda línea"),
        ImageMark(tool=MarkTool.MARKER,points=listOf(MarkPoint(.8f,.8f)),text="Otra observación")
    )
    @Test fun allToolsRenderAndEditableCopyReopensWithoutChangingSource() {
        val source=image();val original=markedPng(source,emptyList());val content=marks()
        val public=markedPng(source,content);val editable=EditableImagePng.write(public,original,content)
        val read=EditableImagePng.read(editable)!!
        assertArrayEquals(original,read.original);assertEquals(content,read.marks)
        assertArrayEquals(com.r0ybt.arachn0de.photos.PrivatePhoto.clean(public).bytes,EditableImagePng.publicBytes(editable));assertNull(EditableImagePng.read(public))
        assertArrayEquals(original,markedPng(source,emptyList()))
        val decoded=BitmapFactory.decodeByteArray(public,0,public.size)
        assertEquals(400,decoded.width);assertEquals(300,decoded.height)
        assertNotEquals(Color.WHITE,decoded.getPixel(100,75));decoded.recycle();source.recycle()
    }
    @Test fun movingDeletingAndUndoRedoKeepMarkerTextsAndNumberingTogether() {
        val before=marks();val history=MarkHistory(before)
        val marker=before.last();val moved=marker.moved(.1f,-.2f).copy(text="Texto editado\ncompleto")
        val after=before.dropLast(2)+moved
        history.commit(after)
        assertEquals(listOf("Texto editado\ncompleto"),history.current.filter {it.tool==MarkTool.MARKER}.map {it.text})
        assertEquals(before,history.undo());assertEquals(after,history.redo())
        history.undo();history.commit(emptyList());assertFalse(history.canRedo)
        assertEquals(1f,marker.moved(10f,10f).points.single().x,0f)
    }
    @Test fun viewportInverseKeepsNormalizedMarksAlignedAtEveryZoomAndPan() {
        for(zoom in listOf(1f,2f,5f)) for(w in listOf(360f,800f)) {
            val viewport=annotationViewport(w,600f,400,300,zoom,71f,-42f)
            for(p in listOf(MarkPoint(0f,0f),MarkPoint(.37f,.82f),MarkPoint(1f,1f))) {
                val restored=viewport.point(viewport.left+p.x*viewport.width,viewport.top+p.y*viewport.height)
                assertEquals(p.x,restored.x,.00001f);assertEquals(p.y,restored.y,.00001f)
            }
        }
    }
    @Test fun observationPaginationRetainsEveryCharacterAndProducesShareablePngs() {
        val text=(1..250).joinToString("\n") {"Línea $it: observación completa y multilínea."}
        val document=ObservationDocument("Fotografía",listOf(Observation(text=text),Observation(text="Final")))
        val pages=observationPages(document,800);assertTrue(pages.size>2)
        assertEquals(text,pages.flatMap {it.slices}.filter {it.number==1&&it.content==text}.joinToString("") {it.text})
        pages.forEachIndexed {i,page->val output=ByteArrayOutputStream();observationPng(document,page,i,pages.size,output);val bitmap=BitmapFactory.decodeByteArray(output.toByteArray(),0,output.size());assertEquals(1240,bitmap.width);assertTrue(bitmap.height<=800);bitmap.recycle()}
        val share=observationShareIntent(listOf(Uri.parse("content://test/a"),Uri.parse("content://test/b")))
        assertEquals(android.content.Intent.ACTION_SEND_MULTIPLE,share.action);assertEquals(2,share.clipData!!.itemCount)
        assertTrue(share.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION !=0)
    }
    @Test fun corruptMetadataNestedSourcesAndInvalidCoordinatesAreRejected() {
        val image=image();val original=markedPng(image,emptyList());val editable=EditableImagePng.write(markedPng(image,marks()),original,marks())
        val corrupt=editable.clone();val at=corrupt.toString(Charsets.ISO_8859_1).indexOf("arNd");corrupt[at+10]=(corrupt[at+10].toInt() xor 1).toByte()
        assertTrue(runCatching {EditableImagePng.read(corrupt)}.isFailure)
        assertTrue(runCatching {EditableImagePng.write(original,editable,marks())}.isFailure)
        assertTrue(runCatching {ImageMarkCodec.decode(ImageMarkCodec.encode(listOf(marks().first().copy(points=listOf(MarkPoint(2f,0f))))))}.isFailure)
        image.recycle()
    }
    private fun <T> vault(block:suspend (VaultContext,Arachn0deDatabase,AttachmentStore,AttachmentRepository)->T):T=runBlocking {
        val id=UUID.randomUUID();val root=File(context.filesDir,"annotation-test-$id").apply {mkdirs()}
        val session=SecureFiles.Session(id,root,VaultCrypto.randomKey());SecureFiles.register(session)
        val vault=VaultContext(context,session);val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build();vault.database=db
        val store=AttachmentStore(vault,BackupFixture::syncDirectory);val repository=AttachmentRepository(db,store)
        try {block(vault,db,store,repository)} finally {db.close();vault.clearMemory();SecureFiles.revoke(session);root.deleteRecursively()}
    }
    @Test fun flattenedReplacementIsAtomicSharedReservedAndRestorableAfterCleanupFailure()=vault {vault,db,store,repo->
        val bitmap=image();val original=markedPng(bitmap,emptyList());val flat=markedPng(bitmap,marks());bitmap.recycle()
        db.projectDao().insert(ProjectEntity("p","Núcleo","",0,1,1))
        db.nodeDao().insert(NodeEntity("n","p",null,"Nota","",false,0,1,1))
        val source=repo.importGuestBytes(original,"draft","foto.png")
        repo.associateNode("n",source.id,AttachmentReferences.token(source.id,"Leyenda"))
        repo.associateProject("p",source.id,AttachmentReferences.token(source.id))
        repo.retainGallery("other-editor",setOf(source.id))
        val replacement=repo.saveAnnotated(source.id,flat,null,replace=true)
        assertEquals(setOf(replacement.id),AttachmentReferences.ids(db.nodeDao().getById("n")!!.description))
        assertEquals("Leyenda",AttachmentReferences.label(AttachmentReferences.matches(db.nodeDao().getById("n")!!.description).single()))
        assertEquals(2,db.attachmentDao().references(replacement.id));assertEquals(0,db.attachmentDao().references(source.id))
        assertArrayEquals(original,SecureFiles.read(store.file(source.storageName)))
        assertFalse(store.file(replacement.storageName).readBytes().contentEquals(flat))
        assertArrayEquals(flat,SecureFiles.read(store.file(replacement.storageName)))
        val failingStore=object:AttachmentStore(vault,BackupFixture::syncDirectory) {override fun delete(name:String)=false}
        val failing=AttachmentRepository(db,failingStore)
        failing.discardDraft("other-editor")
        assertEquals("DELETE_PENDING",db.attachmentDao().get(source.id)!!.lifecycleState)
        AttachmentRepository(db,store).cleanup()
        assertNull(db.attachmentDao().get(source.id));assertFalse(store.file(source.storageName).exists())
        val backup=BackupRepository(db,vault,BackupAvatarFiles(vault,BackupFixture::syncDirectory))
        val archive=backup.create("replacement".toCharArray());val candidate=backup.inspect(SecureFiles.input(archive),"replacement".toCharArray())
        repo.detachNode("n",replacement.id);repo.detachProject("p",replacement.id)
        assertFalse(store.file(replacement.storageName).exists())
        backup.restore(candidate);backup.discard(candidate);backup.discardPending(archive.name)
        val restored=repo.availableFiles().getValue(replacement.id)
        assertArrayEquals(flat,SecureFiles.read(store.file(restored.storageName)))
        assertEquals(2,db.attachmentDao().references(restored.id))
    }
    @Test fun failedReplacementAndCancelledOuterDraftPreserveOriginalAndReleaseOnlyNewFiles()=vault {vault,db,store,repo->
        val bitmap=image();val original=markedPng(bitmap,emptyList());val flat=markedPng(bitmap,marks());bitmap.recycle()
        db.projectDao().insert(ProjectEntity("p","Núcleo","",0,1,1))
        val source=repo.importGuestBytes(original,"draft","foto.png")
        repo.associateProject("p",source.id,AttachmentReferences.token(source.id,"Original"))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_replacement BEFORE UPDATE ON projects BEGIN SELECT RAISE(ABORT,'injected'); END")
        assertTrue(runCatching {repo.saveAnnotated(source.id,flat,null,replace=true)}.isFailure)
        assertEquals(listOf(source),db.attachmentDao().files())
        assertArrayEquals(original,SecureFiles.read(store.file(source.storageName)))
        assertTrue(runCatching {repo.saveAnnotated(source.id,byteArrayOf(1,2,3),"editor",replace=true)}.isFailure)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_replacement")
        val replacement=repo.saveAnnotated(source.id,flat,"editor",replace=true)
        assertEquals(setOf(source.id),AttachmentReferences.ids(db.projectDao().getById("p")!!.description))
        repo.discardDraft("editor")
        assertNull(db.attachmentDao().get(replacement.id));assertFalse(store.file(replacement.storageName).exists())
        assertArrayEquals(original,SecureFiles.read(store.file(source.storageName)))
        assertEquals(1,db.attachmentDao().references(source.id))
    }

    @Test fun encryptedCopySurvivesBackupRestoreSourceRemovalAndReediting()=vault {vault,db,store,repo->
        val source=image();val original=markedPng(source,emptyList());val marked=EditableImagePng.write(markedPng(source,marks()),original,marks());source.recycle()
        val input=File(context.cacheDir,"original-${UUID.randomUUID()}.png").apply {writeBytes(original)}
        try {
            db.projectDao().insert(ProjectEntity("p","Núcleo","",0,1,1))
            db.nodeDao().insert(NodeEntity("n","p",null,"Nota","",false,0,1,1))
            val row=repo.import(Uri.fromFile(input),"draft")
            repo.associateNode("n",row.id,AttachmentReferences.token(row.id));repo.associateProject("p",row.id,AttachmentReferences.token(row.id))
            val copy=repo.saveAnnotated(row.id,marked,null)
            assertEquals(2,db.attachmentDao().references(copy.id));assertEquals(2,db.attachmentDao().references(row.id))
            assertArrayEquals(original,SecureFiles.read(store.file(row.storageName)))
            assertFalse(store.file(copy.storageName).readBytes().contentEquals(marked))
            assertArrayEquals(com.r0ybt.arachn0de.photos.PublicImages.prepare(markedPng(decodeAnnotationSource(original),marks())),com.r0ybt.arachn0de.audio.publicAttachmentBytes(repo,copy))
            val backup=BackupRepository(db,vault,BackupAvatarFiles(vault,BackupFixture::syncDirectory))
            val archive=backup.create("annotations".toCharArray());val candidate=backup.inspect(SecureFiles.input(archive),"annotations".toCharArray())
            repo.detachNode("n",copy.id);repo.detachProject("p",copy.id);assertNull(db.attachmentDao().get(copy.id))
            backup.restore(candidate);backup.discard(candidate);backup.discardPending(archive.name)
            val restored=repo.availableFiles().getValue(copy.id);assertNotEquals(copy.storageName,restored.storageName)
            val content=EditableImagePng.read(SecureFiles.read(repo.localFile(restored)))!!
            assertArrayEquals(original,content.original);assertEquals(marks().map {it.tool},content.marks.map {it.tool});assertEquals("Primera observación\nSegunda línea",content.marks[5].text)
            repo.detachNode("n",row.id);repo.detachProject("p",row.id)
            assertNull(db.attachmentDao().get(row.id));assertNotNull(EditableImagePng.read(SecureFiles.read(repo.localFile(restored))))
        } finally {input.delete()}
    }
    @Test fun pendingCopyIsRecoverableAndCancellationDeletesOnlyItsOwnUnreferencedFiles()=vault {_,db,store,repo->
        val image=image();val original=markedPng(image,emptyList());val editable=EditableImagePng.write(markedPng(image,marks()),original,marks());image.recycle()
        val input=File(context.cacheDir,"pending-${UUID.randomUUID()}.png").apply {writeBytes(original)}
        try {
            val source=repo.import(Uri.fromFile(input),"pending")
            assertTrue(runCatching {repo.saveAnnotated(source.id,editable,"other-editor")}.isFailure)
            val copy=repo.saveAnnotated(source.id,editable,"pending")
            repo.cleanup();assertEquals(setOf(source.id,copy.id),repo.pendingImports("pending").map {it.id}.toSet())
            assertTrue(store.file(source.storageName).isFile);assertTrue(store.file(copy.storageName).isFile)
            repo.discardDraft("pending")
            assertTrue(db.attachmentDao().files().isEmpty());assertFalse(store.file(copy.storageName).exists());assertFalse(store.file(source.storageName).exists())
        } finally {input.delete()}
    }
    @Test fun failedCommitLeavesOriginalUntouchedAndNoOrphanDerivative()=vault {vault,db,store,repo->
        val image=image();val original=markedPng(image,emptyList());val editable=EditableImagePng.write(markedPng(image,marks()),original,marks());image.recycle()
        val input=File(context.cacheDir,"rollback-${UUID.randomUUID()}.png").apply {writeBytes(original)}
        try {
            val source=repo.import(Uri.fromFile(input),"pending")
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_annotation BEFORE INSERT ON attachment_files WHEN NEW.originalName LIKE '%anotada%' BEGIN SELECT RAISE(ABORT,'injected'); END")
            assertTrue(runCatching {repo.saveAnnotated(source.id,editable,"pending")}.isFailure)
            assertEquals(listOf(source),db.attachmentDao().files());assertEquals(listOf(source),repo.pendingImports("pending"))
            assertArrayEquals(original,SecureFiles.read(store.file(source.storageName)))
            assertEquals(listOf(source.storageName),File(vault.filesDir,"attachments").listFiles()!!.filter {it.isFile}.map {it.name})
        } finally {input.delete()}
    }
    @Test fun reeditingCreatesIndependentCopyAndFailedDeletionRetriesAfterRestart()=vault {vault,db,store,repo->
        val image=image();val original=markedPng(image,emptyList());val firstMarks=marks();val editable=EditableImagePng.write(markedPng(image,firstMarks),original,firstMarks)
        val input=File(context.cacheDir,"release-${UUID.randomUUID()}.png").apply {writeBytes(original)}
        try {
            db.projectDao().insert(ProjectEntity("p","Núcleo","",0,1,1))
            val source=repo.import(Uri.fromFile(input),"pending");repo.associateProject("p",source.id,AttachmentReferences.token(source.id))
            val first=repo.saveAnnotated(source.id,editable,null)
            val changed=firstMarks.dropLast(1)
            val second=repo.saveAnnotated(first.id,EditableImagePng.write(markedPng(image,changed),original,changed),null)
            assertEquals(firstMarks,EditableImagePng.read(SecureFiles.read(store.file(first.storageName)))!!.marks)
            val failing=AttachmentRepository(db,object:AttachmentStore(vault,BackupFixture::syncDirectory) {override fun delete(name:String)=false})
            failing.detachProject("p",first.id)
            assertEquals("DELETE_PENDING",db.attachmentDao().get(first.id)!!.lifecycleState)
            AttachmentRepository(db,AttachmentStore(vault,BackupFixture::syncDirectory)).cleanup()
            assertNull(db.attachmentDao().get(first.id));assertFalse(store.file(first.storageName).exists())
            assertArrayEquals(original,SecureFiles.read(store.file(source.storageName)))
            assertEquals(changed,EditableImagePng.read(SecureFiles.read(store.file(second.storageName)))!!.marks)
        } finally {image.recycle();input.delete()}
    }
    @Test fun safExportIncludesFullObservationsPreservesEarlierFilesAndCleansPartialOutputs()=runBlocking<Unit> {
        val provider=ExportProvider()
        provider.attachInfo(context,android.content.pm.ProviderInfo().apply {authority="annotation.test.documents";exported=true;grantUriPermissions=true})
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("annotation.test.documents",provider)
        val tree=android.provider.DocumentsContract.buildTreeDocumentUri("annotation.test.documents","root")
        val image=image()
        try {
            val earlier=File(provider.root,"earlier.png").apply {writeText("Conservar")}
            val observed=marks().map {if(it.tool==MarkTool.MARKER) it.copy(text=(1..100).joinToString("\n") {n->"Observación larga $n"}) else it}
            val first=exportImageAnnotations(context,tree,image,observed,"Imagen",true)
            assertTrue(first.size>2)
            first.forEach {uri->val bytes=context.contentResolver.openInputStream(uri)!!.use {it.readBytes()};assertNull(EditableImagePng.read(bytes));BitmapFactory.decodeByteArray(bytes,0,bytes.size).recycle()}
            val second=exportImageAnnotations(context,tree,image,observed,"Imagen",false)
            assertEquals(1,second.size);assertTrue(first.intersect(second.toSet()).isEmpty());assertEquals("Conservar",earlier.readText())
            val names=provider.root.list()!!.toSet();provider.failAt=provider.created+2
            assertTrue(runCatching {exportImageAnnotations(context,tree,image,observed,"Imagen",true)}.isFailure)
            assertEquals(names,provider.root.list()!!.toSet())
        } finally {image.recycle();provider.root.deleteRecursively()}
    }
    private inner class ExportProvider:android.content.ContentProvider() {
        val root=File(this@ImageAnnotationsTest.context.cacheDir,"annotation-export-${UUID.randomUUID()}").apply {mkdirs()}
        var created=0;var failAt=Int.MAX_VALUE
        override fun onCreate()=true
        override fun call(method:String,arg:String?,extras:android.os.Bundle?):android.os.Bundle? = when(method) {
            "android:createDocument"->{
                created++;check(created!=failAt) {"Injected export failure"}
                val id=UUID.randomUUID().toString();File(root,id).createNewFile()
                android.os.Bundle().apply {putParcelable("uri",android.provider.DocumentsContract.buildDocumentUri("annotation.test.documents",id))}
            }
            "android:deleteDocument"->{val uri=extras!!.getParcelable<Uri>("uri")!!;check(File(root,android.provider.DocumentsContract.getDocumentId(uri)).delete());android.os.Bundle()}
            else->super.call(method,arg,extras)
        }
        override fun openFile(uri:Uri,mode:String)=android.os.ParcelFileDescriptor.open(File(root,android.provider.DocumentsContract.getDocumentId(uri)),android.os.ParcelFileDescriptor.parseMode(mode))
        override fun getType(uri:Uri)="image/png"
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):android.database.Cursor?=null
        override fun insert(uri:Uri,values:android.content.ContentValues?):Uri?=null
        override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
        override fun update(uri:Uri,values:android.content.ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
    }
}
