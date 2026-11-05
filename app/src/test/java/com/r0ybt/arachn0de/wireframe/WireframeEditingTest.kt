package com.r0ybt.arachn0de.wireframe

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WireframeEditingTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private fun toolbar()=WireTemplates.items.first {it.id=="toolbar"}.element(WireFormat.MOBILE).copy(x=.05f,y=.1f,w=.8f,h=.1f,fill=0xffffffff,ink=0xff000000)
    private fun enhanced():WireDocument {
        val e=wireResizeEdge(toolbar(),-.35f,0f,WireHandle.RIGHT,WireFormat.MOBILE).copy(icons=mapOf(0 to "star",2 to "user"))
        return WireDocument(name="Decisiones",elements=listOf(e),annotations=listOf(WireAnnotation(x=.2f,y=.3f,title="Menú principal",text="No incluir administración.",elementId=e.id),WireAnnotation(x=.8f,y=.8f,text="Texto independiente")))
    }
    @Test fun fourEdgesHaveIndependentBoundsFixedContentAndAspectConstraints() {
        val e=toolbar()
        val right=wireResizeEdge(e,-.35f,.8f,WireHandle.RIGHT,WireFormat.MOBILE)
        assertEquals(e.h,right.h,0f);assertEquals(e.y,right.y,0f);assertEquals(e.font,right.font,0f);assertEquals(e.w,right.contentW!!,0f)
        assertEquals(minOf(e.w*1080/400,e.h*2340/112).coerceIn(.35f,1.5f),right.contentUnit!!,0f)
        val bottom=wireResizeEdge(e,.8f,-.05f,WireHandle.BOTTOM,WireFormat.MOBILE)
        assertEquals(e.w,bottom.w,0f);assertEquals(.05f,bottom.h,.00001f)
        val left=wireResizeEdge(e,.15f,.8f,WireHandle.LEFT,WireFormat.MOBILE)
        assertEquals(e.x+e.w,left.x+left.w,.00001f);assertEquals(e.h,left.h,0f)
        val top=wireResizeEdge(e,.8f,.03f,WireHandle.TOP,WireFormat.MOBILE)
        assertEquals(e.y+e.h,top.y+top.h,.00001f);assertEquals(e.w,top.w,0f)
        listOf(right,bottom,left,top).forEach {WireCodec.validate(WireDocument(elements=listOf(it)))}
        val minimum=wireResizeEdge(e,-5f,0f,WireHandle.RIGHT,WireFormat.MOBILE);assertEquals(.035f,minimum.w,.00001f)
        val circle=e.copy(lockAspect=true,w=.2f,h=.1f)
        val resized=wireResizeEdge(circle,.1f,0f,WireHandle.RIGHT,WireFormat.MOBILE)
        assertEquals(circle.w/circle.h,resized.w/resized.h,.0001f)
        val corner=wireTransform(right,.1f,.02f,true);assertTrue(corner.contentUnit!!>right.contentUnit!!)
        assertEquals(WireHandle.RIGHT,wireHandleAt(e,Offset(e.x+e.w,e.y+e.h/2),1000f,2000f,12f))
        assertNull(wireHandleAt(e,Offset(e.x+e.w/2,e.y+e.h/2),1000f,2000f,12f))
        val near=right.copy(w=.448f-right.x)
        val aligned=wireResizeAlign(WireDocument(elements=listOf(near,e.copy(id=UUID.randomUUID().toString(),x=.45f))),near,WireHandle.RIGHT,.01f,.01f,true)
        assertEquals(.45f,aligned.element.x+aligned.element.w,.00001f);assertEquals(near.h,aligned.element.h,0f);assertEquals(near.contentUnit,aligned.element.contentUnit)
        assertEquals(near,wireResizeAlign(WireDocument(elements=listOf(near,e.copy(id=UUID.randomUUID().toString(),x=.45f))),near,WireHandle.RIGHT,.01f,.01f,false).element)
        val original=WireDocument(elements=listOf(e));val changed=original.copy(elements=listOf(right));val history=WireHistory()
        history.record(original,changed);assertEquals(original,history.undo(changed));assertFalse(history.canUndo);assertEquals(changed,history.redo(original))
    }
    @Test fun edgeCroppingPreservesActualIconPixelsAndTextFont() {
        val e=toolbar();val resized=wireResizeEdge(e,-.4f,0f,WireHandle.RIGHT,WireFormat.MOBILE)
        WireframeRenderer(context,emptyMap()).use {r->
            val before=r.bitmap(WireDocument(elements=listOf(e)));val after=r.bitmap(WireDocument(elements=listOf(resized)))
            // The visible portion is cropped from the original icon layout, not scaled or compressed.
            for(y in 250..440) for(x in 65..470) assertEquals("$x,$y",before.getPixel(x,y),after.getPixel(x,y))
            before.recycle();after.recycle()
        }
        val text=WireTemplates.items.first {it.id=="title"}.element(WireFormat.MOBILE)
        assertEquals(text.font,wireResizeEdge(text,-.2f,0f,WireHandle.RIGHT,WireFormat.MOBILE).font,0f)
    }
    @Test fun individualIconOverridesKeepGeometryAndRoundTripAllCompatibleComponents() {
        WireTemplates.items.filter {it.kind!=WireKind.IMAGE}.forEach {template->
            val element=template.element(WireFormat.MOBILE)
            wireIconDefaults(element).indices.forEach {slot->
                val custom=element.copy(icons=mapOf(slot to "star"));val doc=WireDocument(elements=listOf(custom))
                assertEquals(element.copy(icons=custom.icons),custom);assertEquals(doc,WireCodec.decode(WireCodec.encode(doc)))
                WireframeRenderer(context,emptyMap()).use {r->r.bitmap(doc,160).recycle()}
            }
        }
        val doc=enhanced();val changed=doc.copy(elements=doc.elements.map {it.copy(icons=it.icons+(1 to "check"))})
        assertEquals(mapOf(0 to "star",1 to "check",2 to "user"),changed.elements.single().icons)
        assertEquals(doc.elements.single().w,changed.elements.single().w,0f)
        assertTrue(runCatching {WireCodec.validate(doc.copy(elements=doc.elements.map {it.copy(icons=mapOf(99 to "star"))}))}.isFailure)
    }
    @Test fun annotationLinkMovementDeletionRenumberingAndHistoryPreserveContents() {
        val original=enhanced();val a=original.annotations.first();val p=a.position(original)
        val moved=original.copy(elements=original.elements.map {it.copy(x=it.x+.1f,y=it.y+.1f)})
        assertEquals(p.x+.1f,a.position(moved).x,.00001f);assertEquals(p.y+.1f,a.position(moved).y,.00001f)
        val outside=WireAnnotation(x=.9f,y=.7f);val attached=outside.copy(elementId=original.elements.single().id).at(original,outside.position(original))
        assertEquals(outside.position(original),attached.position(original))
        val dragged=a.at(moved,p+Offset(.12f,.12f));assertNotEquals(a,dragged)
        val edited=moved.copy(annotations=listOf(dragged.copy(title="Nuevo título",text="Detalle\nmultilínea"))+moved.annotations.drop(1))
        val deleted=edited.copy(elements=emptyList()).preserveAnnotations(edited)
        assertNull(deleted.annotations.first().elementId);assertEquals(edited.annotations.first().position(edited),deleted.annotations.first().position(deleted));WireCodec.validate(deleted)
        val history=WireHistory();history.record(edited,deleted);assertEquals(edited,history.undo(deleted));assertEquals(deleted,history.redo(edited))
        val withoutFirst=deleted.copy(annotations=deleted.annotations.drop(1))
        assertEquals("Texto independiente",withoutFirst.annotations.single().text)
        assertEquals(withoutFirst,WireCodec.decode(WireCodec.encode(withoutFirst)))
    }
    @Test fun versionThreeRejectsMalformedDataAndLeavesHistoricalDocumentsUnchanged() {
        val legacy=WireDocument().insert(WireKind.INPUT);val v2=WireDocument(elements=listOf(toolbar()));val v3=enhanced()
        listOf(legacy to 1,v2 to 2,v3 to 3).forEach {(doc,version)->
            val raw=WireCodec.encode(doc);assertEquals(version,JSONObject(raw).getInt("version"));assertEquals(doc,WireCodec.decode(raw));assertEquals(raw,WireCodec.encode(WireCodec.decode(raw)))
        }
        val malformed=JSONObject(WireCodec.encode(v3));malformed.getJSONArray("annotations").getJSONObject(0).put("elementId",UUID.randomUUID().toString())
        assertTrue(runCatching {WireCodec.decode(malformed.toString())}.isFailure)
        malformed.getJSONArray("annotations").getJSONObject(0).put("elementId",JSONObject.NULL).put("x",2)
        assertTrue(runCatching {WireCodec.decode(malformed.toString())}.isFailure)
        assertEquals(80,WireTemplates.items.size);assertEquals(25,WireScreens.items.size)
    }
    @Test fun cleanExportNeverShowsMarkersAndLongObservationSheetsLoseNoText() {
        val doc=enhanced()
        WireframeRenderer(context,emptyMap()).use {r->
            val clean=r.bitmap(doc);val noNotes=r.bitmap(doc.copy(annotations=emptyList()));assertTrue(clean.sameAs(noNotes))
            val marked=r.bitmap(doc,markers=true);assertFalse(clean.sameAs(marked));assertEquals(clean.width,marked.width);assertEquals(clean.height,marked.height)
            java.io.FileOutputStream("/tmp/wireframe-marcadores-demo.png").use {r.png(doc,false,it,markers=true)}
            clean.recycle();noNotes.recycle();marked.recycle()
        }
        val example=wireObservationPages(doc).single()
        java.io.FileOutputStream("/tmp/wireframe-observaciones-demo.png").use {wireObservationPng(doc,example,0,1,it)}
        val long=(1..200).joinToString("\n") {"Línea $it: decisiones de diseño con símbolos áéíóú y saltos completos."}
        val notes=doc.copy(annotations=listOf(WireAnnotation(title="Título íntegro",text=long),WireAnnotation(title="Segunda",text="ÚLTIMA OBSERVACIÓN")))
        val pages=wireObservationPages(notes,512);assertTrue(pages.size>2);assertTrue(pages.all {it.height<=512})
        val first=pages.flatMap {it.slices}.filter {it.number==1 && it.content==long}.joinToString("") {it.text};assertEquals(long,first)
        assertTrue(pages.last().slices.any {it.text.contains("ÚLTIMA OBSERVACIÓN")})
        pages.forEachIndexed {index,page->
            val output=ByteArrayOutputStream();wireObservationPng(notes,page,index,pages.size,output)
            val bytes=output.toByteArray();val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
            assertEquals(1240,bitmap.width);assertEquals(page.height,bitmap.height);bitmap.recycle()
        }
        val uris=listOf(android.net.Uri.parse("content://documents/design.png"),android.net.Uri.parse("content://documents/notes.png"))
        val share=wireShareIntent(uris);assertEquals(android.content.Intent.ACTION_SEND_MULTIPLE,share.action);assertEquals(2,share.clipData!!.itemCount);assertTrue(share.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
    }
    @Test fun enhancedAndHistoricalDrawingsRestoreTogetherAndInvalidBackupRollsBack()=runBlocking<Unit> {
        val id=UUID.randomUUID();val root=File(context.filesDir,"wire-edit-$id").apply {mkdirs()}
        val session=SecureFiles.Session(id,root,VaultCrypto.randomKey());SecureFiles.register(session);val vault=VaultContext(context,session)
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build();vault.database=db
        try {
            val prefs=vault.getSharedPreferences(WireCodec.NAME,0);val repo=WireframeRepository(prefs)
            val docs=listOf(enhanced(),WireDocument().insert(WireKind.INPUT),WireScreens.items.first().preview());docs.forEach {repo.save(it,null)}
            val backup=BackupRepository(db,vault,BackupAvatarFiles(vault,BackupFixture::syncDirectory))
            val archive=backup.create("wire-edit-test".toCharArray());val candidate=backup.inspect(SecureFiles.input(archive),"wire-edit-test".toCharArray())
            docs.forEach {repo.delete(it.id,WireCodec.encode(it))};assertTrue(prefs.all.isEmpty())
            backup.restore(candidate);backup.discard(candidate);backup.discardPending(archive.name)
            docs.forEach {assertEquals(it,repo.load(it.id).first)}
            val before=backup.snapshot();val invalid=JSONObject(WireCodec.encode(docs.first()));invalid.getJSONArray("annotations").getJSONObject(0).put("elementId",UUID.randomUUID().toString())
            val bad=before.copy(privatePreferences=before.privatePreferences.map {if(it.name==WireCodec.NAME) it.copy(payload=EncryptedPreferences.encode(mapOf(docs.first().id to invalid.toString()))) else it})
            assertTrue(runCatching {backup.restore(bad)}.isFailure);assertEquals(before.privatePreferences,backup.snapshot().privatePreferences)
        } finally {db.close();vault.clearMemory();SecureFiles.revoke(session);root.deleteRecursively()}
    }
    @Test fun safExportsCreateIndependentFilesAndRollbackOnlyTheirOwnPartialOutputs()=runBlocking<Unit> {
        val provider=ExportProvider(context)
        provider.attachInfo(context,android.content.pm.ProviderInfo().apply {authority="wire.test.documents";exported=true;grantUriPermissions=true})
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("wire.test.documents",provider)
        val tree=android.provider.DocumentsContract.buildTreeDocumentUri("wire.test.documents","root")
        try {
            val preserved=File(provider.root,"previous.png").apply {writeText("Archivo del usuario")}
            val first=wireExportObserved(context,enhanced(),false,tree)
            assertEquals(2,first.size);assertTrue(first.all {context.contentResolver.openInputStream(it)!!.use {input->BitmapFactory.decodeStream(input)?.let {bitmap->bitmap.recycle();true} ?: false}})
            val second=wireExportObserved(context,enhanced(),true,tree)
            assertTrue(first.intersect(second.toSet()).isEmpty());assertEquals("Archivo del usuario",preserved.readText())
            val names=provider.root.list()!!.toSet();provider.failCreate=provider.created+2
            assertTrue(runCatching {wireExportObserved(context,enhanced(),false,tree)}.isFailure)
            assertEquals(names,provider.root.list()!!.toSet())
            provider.failCreate=provider.created+2;provider.failDelete=true
            val failed=runCatching {wireExportObserved(context,enhanced(),false,tree)}.exceptionOrNull()
            assertTrue(failed?.message.orEmpty().contains("archivos parciales"))
            assertTrue(provider.root.list()!!.toSet().containsAll(names))
            assertEquals("Archivo del usuario",preserved.readText())
        } finally {provider.root.deleteRecursively()}
    }
    private class ExportProvider(context:android.content.Context):android.content.ContentProvider() {
        val root=File(context.cacheDir,"wire-export-test-${UUID.randomUUID()}").apply {mkdirs()}
        var created=0;var failCreate=Int.MAX_VALUE;var failDelete=false
        override fun onCreate()=true
        override fun call(method:String,arg:String?,extras:android.os.Bundle?):android.os.Bundle? {
            return when(method) {
                "android:createDocument"->{
                    created++;if(created==failCreate) throw IllegalStateException("Injected creation failure")
                    val id=UUID.randomUUID().toString();File(root,id).createNewFile()
                    android.os.Bundle().apply {putParcelable("uri",android.provider.DocumentsContract.buildDocumentUri("wire.test.documents",id))}
                }
                "android:deleteDocument"->{
                    if(failDelete) throw IllegalStateException("Injected cleanup failure")
                    val uri=extras!!.getParcelable<android.net.Uri>("uri")!!
                    check(File(root,android.provider.DocumentsContract.getDocumentId(uri)).delete());android.os.Bundle()
                }
                else->super.call(method,arg,extras)
            }
        }
        override fun openFile(uri:android.net.Uri,mode:String):android.os.ParcelFileDescriptor = android.os.ParcelFileDescriptor.open(File(root,android.provider.DocumentsContract.getDocumentId(uri)),android.os.ParcelFileDescriptor.parseMode(mode))
        override fun getType(uri:android.net.Uri)="image/png"
        override fun query(uri:android.net.Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):android.database.Cursor?=null
        override fun insert(uri:android.net.Uri,values:android.content.ContentValues?):android.net.Uri?=null
        override fun delete(uri:android.net.Uri,selection:String?,selectionArgs:Array<out String>?)=0
        override fun update(uri:android.net.Uri,values:android.content.ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
    }

}
