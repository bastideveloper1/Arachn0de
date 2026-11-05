package com.r0ybt.arachn0de.diagrams

import android.app.Application
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagramExportTest {
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var provider:ExportDocuments
    private lateinit var vault:VaultContext
    private lateinit var session:SecureFiles.Session
    private lateinit var root:File
    private val tree=DocumentsContract.buildTreeDocumentUri("diagram-export-test","folder")
    private fun journal(sync:(File)->Unit={})=DiagramExportJournal(app,vault,sync,persist={})
    @Before fun setup() {
        root=File(app.filesDir,"export-test-${UUID.randomUUID()}").apply {mkdirs()}
        session=SecureFiles.Session(UUID.randomUUID(),root,VaultCrypto.randomKey());SecureFiles.register(session);vault=VaultContext(app,session)
        provider=ExportDocuments(File(app.cacheDir,"provider-${UUID.randomUUID()}").apply {mkdirs()})
        provider.attachInfo(app,ProviderInfo().apply {authority="diagram-export-test";exported=true;grantUriPermissions=true;readPermission="android.permission.MANAGE_DOCUMENTS";writePermission=readPermission})
        ShadowContentResolver.registerProviderInternal("diagram-export-test",provider)
    }
    @After fun teardown() {restart();SecureFiles.revoke(session);root.deleteRecursively();provider.root.deleteRecursively()}
    private fun restart() {val active=DiagramExportJournal::class.java.getDeclaredField("active").apply {isAccessible=true}.get(null) as MutableSet<*>;active.clear()}
    @Test fun journalIsEncryptedRollbackOnlyDeletesItsOwnFileAndFailedDeleteRetriesWithoutAge()=runBlocking<Unit> {
        val unrelated=provider.createDocument("folder","image/png","existing.png")
        val j=journal();val ticket=j.begin(tree,DiagramFormat.GIF)
        val marker=File(vault.filesDir,"diagram-export-journal/${ticket.id}.json")
        assertFalse(marker.readBytes().toString(Charsets.ISO_8859_1).contains(tree.toString()))
        assertTrue(SecureFiles.text(marker).contains(ticket.name))
        assertTrue(j.recover().isEmpty());assertTrue(provider.entries.containsKey(DocumentsContract.getDocumentId(ticket.uri!!)))
        provider.refuseDelete=true
        assertTrue(runCatching {j.rollback(ticket)}.isFailure);assertTrue(marker.exists())
        provider.refuseDelete=false;restart();assertTrue(j.recover().isEmpty())
        assertFalse(marker.exists());assertEquals(setOf(unrelated),provider.entries.keys)
    }
    @Test fun recoveryFindsUniquePreparedNameAfterCrashBetweenCreateAndUriRecording()=runBlocking<Unit> {
        val j=journal();val ticket=j.begin(tree,DiagramFormat.PNG)
        val marker=File(vault.filesDir,"diagram-export-journal/${ticket.id}.json")
        val json=org.json.JSONObject(SecureFiles.text(marker)).put("uri",org.json.JSONObject.NULL)
        SecureFiles.write(marker,json.toString().toByteArray());restart()
        assertTrue(j.recover().isEmpty());assertTrue(provider.entries.isEmpty())
    }
    @Test fun completedOutputSurvivesRecoveryAndUnknownPrivateFilesAreUntouched()=runBlocking<Unit> {
        val j=journal();val t=j.begin(tree,DiagramFormat.PNG)
        val marker=File(vault.filesDir,"diagram-export-journal/${t.id}.json")
        val completed=org.json.JSONObject(SecureFiles.text(marker)).put("complete",true)
        SecureFiles.write(marker,completed.toString().toByteArray())
        val unknown=File(marker.parentFile,"unknown.bin").apply {writeText("keep")}
        restart();assertTrue(j.recover().isEmpty());assertFalse(marker.exists())
        assertEquals(1,provider.entries.size);assertTrue(unknown.exists())
    }
    @Test fun actualGifIsWrittenCancellationAndDiskErrorsDeletePartialOutputs()=runBlocking<Unit> {
        val j=journal();val d=diagramTemplate(3)
        val uri=exportDiagram(app,tree,d,emptyMap(),DiagramExportOptions(DiagramFormat.GIF,320,4),j) {}
        val complete=provider.entries.getValue(DocumentsContract.getDocumentId(uri)).file
        assertTrue(complete.length()>0)
        javax.imageio.ImageIO.createImageInputStream(complete).use {input->
            val reader=javax.imageio.ImageIO.getImageReadersByFormatName("gif").next();reader.input=input
            try {assertEquals(41,reader.getNumImages(true));val a=reader.read(0);val b=reader.read(10);assertTrue((0 until 200).any {y->(0 until 320).any {x->a.getRGB(x,y)!=b.getRGB(x,y)}})} finally {reader.dispose()}
        }
        val previous=provider.entries.keys.toSet()
        assertTrue(runCatching {exportDiagram(app,tree,d,emptyMap(),DiagramExportOptions(DiagramFormat.GIF,320,4),j) {if(it>.05f)throw CancellationException("cancel test")}}.isFailure)
        assertEquals(previous,provider.entries.keys)
        provider.failWrite=true
        assertTrue(runCatching {exportDiagram(app,tree,d,emptyMap(),DiagramExportOptions(),j) {}}.isFailure)
        assertEquals(previous,provider.entries.keys)
        assertTrue(j.recover().isEmpty());assertEquals(previous,provider.entries.keys)
    }
    @Test @Config(sdk=[24]) fun unsupportedVideoDoesNotLeaveAnEmptyFileOrMutateTheDocument()=runBlocking<Unit> {
        val d=diagramTemplate(3);val before=DiagramCodec.encode(d)
        val error=runCatching {exportDiagram(app,tree,d,emptyMap(),DiagramExportOptions(DiagramFormat.MP4),journal()) {}}.exceptionOrNull()
        assertTrue("$error",error is IllegalArgumentException);assertTrue(provider.entries.isEmpty());assertEquals(before,DiagramCodec.encode(d))
    }



    @Test fun documentReportWritesWordAndRecoversOnlyItsOwnPartialFiles()=runBlocking<Unit> {
        val unrelated=provider.createDocument("folder","application/pdf","existing-report.pdf")
        val j=DiagramExportJournal(app,vault,{},persist={},report=true)
        val data=com.r0ybt.arachn0de.document.ReportContent(listOf(com.r0ybt.arachn0de.document.ReportSection("Núcleo",0,"",emptyList(),listOf(com.r0ybt.arachn0de.document.ReportPart.Text("Texto original")))),emptyMap())
        val options=com.r0ybt.arachn0de.document.ReportOptions("Informe",format=com.r0ybt.arachn0de.document.ReportFormat.DOCX)
        val plan=com.r0ybt.arachn0de.document.ReportLayout.measure(data,options)
        val uri=com.r0ybt.arachn0de.document.exportReport(app,vault,tree,data,options,plan,j)
        val output=provider.entries.getValue(DocumentsContract.getDocumentId(uri))
        assertEquals(options.format.mime,output.mime);assertTrue(output.name.endsWith(".docx"))
        java.util.zip.ZipFile(output.file).use {zip->assertNotNull(zip.getEntry("word/document.xml"))}
        val before=provider.entries.keys.toSet()
        provider.failWrite=true
        assertTrue(runCatching {com.r0ybt.arachn0de.document.exportReport(app,vault,tree,data,options,plan,j)}.isFailure)
        provider.failWrite=false;assertEquals(before,provider.entries.keys)
        provider.cancelWrite=true
        assertTrue(runCatching {com.r0ybt.arachn0de.document.exportReport(app,vault,tree,data,options,plan,j)}.exceptionOrNull() is CancellationException)
        provider.cancelWrite=false;assertEquals(before,provider.entries.keys)
        val pending=j.beginReport(tree,"application/pdf","pdf");provider.refuseDelete=true
        assertTrue(runCatching {j.rollback(pending)}.isFailure)
        assertTrue(File(vault.filesDir,"document-export-journal/${pending.id}.json").exists())
        provider.refuseDelete=false;restart();assertTrue(j.recover().isEmpty());assertEquals(before,provider.entries.keys);assertTrue(unrelated in provider.entries)
    }

    private class ExportDocuments(val root:File):DocumentsProvider() {
        data class Entry(val name:String,val mime:String,val file:File)
        val entries=linkedMapOf<String,Entry>();var refuseDelete=false;var failWrite=false;var cancelWrite=false
        override fun onCreate()=true
        override fun isChildDocument(parentDocumentId:String,documentId:String)=parentDocumentId=="folder"
        override fun queryRoots(projection:Array<out String>?):Cursor=MatrixCursor(arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID))
        private fun cursor(projection:Array<out String>?,ids:List<String>):Cursor {
            val cols=projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            return MatrixCursor(cols).apply {ids.forEach {id->val e=entries[id];addRow(cols.map<String,Any?> {when(it) {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID->id
                DocumentsContract.Document.COLUMN_DISPLAY_NAME->e?.name ?: "folder"
                DocumentsContract.Document.COLUMN_MIME_TYPE->e?.mime ?: DocumentsContract.Document.MIME_TYPE_DIR
                DocumentsContract.Document.COLUMN_FLAGS->DocumentsContract.Document.FLAG_SUPPORTS_DELETE or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                DocumentsContract.Document.COLUMN_SIZE->e?.file?.length() ?: 0L
                else->null
            }}.toTypedArray())}}
        }
        override fun queryDocument(id:String,projection:Array<out String>?)=cursor(projection,if(id=="folder"||id in entries)listOf(id) else emptyList())
        override fun queryChildDocuments(id:String,projection:Array<out String>?,sortOrder:String?)=cursor(projection,entries.keys.toList())
        override fun createDocument(parentDocumentId:String,mimeType:String,displayName:String):String {
            val id=UUID.randomUUID().toString();entries[id]=Entry(displayName,mimeType,File(root,id).apply {createNewFile()});return id
        }
        override fun deleteDocument(documentId:String) {check(!refuseDelete) {"injected provider refuses delete"};entries.remove(documentId)?.file?.delete()}
        override fun openDocument(documentId:String,mode:String,signal:CancellationSignal?):ParcelFileDescriptor {
            if(cancelWrite)throw CancellationException("injected cancellation")
            if(failWrite)throw java.io.FileNotFoundException("injected ENOSPC")
            return ParcelFileDescriptor.open(entries.getValue(documentId).file,ParcelFileDescriptor.parseMode(mode))
        }
    }
}
