package com.r0ybt.arachn0de.wireframe

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.runBlocking
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
class WireframeTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private fun drawing():WireDocument {
        val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.RED)
        val bytes=ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val asset=UUID.randomUUID().toString()
        return WireDocument(name="Pantalla").insert(WireTemplates.items.first {it.id=="title"}).let {doc->doc.copy(elements=doc.elements+WireElement(kind=WireKind.IMAGE,asset=asset),assets=mapOf(asset to WireCodec.image(bytes)))}
    }
    @Test fun staticComponentsRoundTripSelectionAndHistory() {
        WireKind.entries.filter {it!=WireKind.IMAGE}.forEach {kind->val d=WireDocument().insert(kind);WireCodec.validate(d);assertEquals(d,WireCodec.decode(WireCodec.encode(d)))}
        val original=drawing();assertEquals(original,WireCodec.decode(WireCodec.encode(original)))
        assertEquals(original.elements.last(),wireHit(original,.15f,.15f));assertNull(wireHit(original,.99f,.99f))
        val moved=original.copy(elements=original.elements.map {it.copy(x=10f,y=-1f).bounded()})
        WireCodec.validate(moved)
        val history=WireHistory();history.record(original,moved)
        assertEquals(original,history.undo(moved));assertEquals(moved,history.redo(original))
        val desktop=moved.copy(format=WireFormat.DESKTOP);assertEquals(moved.elements,desktop.elements)
        assertTrue(runCatching {WireCodec.validate(original.copy(elements=original.elements.map {it.copy(x=Float.NaN)}))}.isFailure)
        assertTrue(runCatching {WireCodec.decode(WireCodec.encode(original).replace("\"sha256\":\"","\"sha256\":\"broken"))}.isFailure)
        assertTrue(runCatching {WireCodec.validate(original.copy(assets=emptyMap()))}.isFailure)
    }
    @Test fun visualTemplatesPersistAndRejectUnknownIdentity() {
        assertEquals(8,WireTemplates.items.map {it.category}.distinct().size)
        assertEquals(WireTemplates.items.size,WireTemplates.items.map {it.id}.distinct().size)
        WireTemplates.items.filter {it.kind!=WireKind.IMAGE}.forEach {template->
            val d=WireDocument().insert(template)
            assertEquals(d,WireCodec.decode(WireCodec.encode(d)))
            WireframeRenderer(context,emptyMap()).use {r->val b=r.bitmap(d,270);val pixels=IntArray(b.width*b.height);b.getPixels(pixels,0,b.width,0,0,b.width,b.height);assertTrue("Plantilla vacía: ${template.id}",pixels.any {it!=Color.WHITE});b.recycle()}
            val inserted=d.insert(template)
            assertNotEquals(d.elements.single().x to d.elements.single().y,inserted.elements.last().x to inserted.elements.last().y)
            assertTrue(runCatching {WireCodec.decode(WireCodec.encode(d).replace("\"template\":\"${template.id}\"","\"template\":\"unknown\""))}.isFailure)
        }
        val legacy=WireDocument().insert(WireKind.INPUT)
        assertTrue(WireCodec.encode(legacy).contains("\"version\":1"))
        assertEquals(legacy,WireCodec.decode(WireCodec.encode(legacy)))
    }
    @Test fun alignmentSnapsOnlyNearRealCanvasCenters() {
        val near=WireElement(x=.295f,y=.39f,w=.4f,h=.2f)
        val snapped=wireAlign(near,12f/1080,12f/2340)
        assertEquals(.3f,snapped.x,.00001f)
        // Y is outside its independent tolerance and remains free.
        assertEquals(near.y,snapped.y,0f)
        val both=wireAlign(near.copy(y=.398f),12f/1080,12f/2340)
        assertEquals(.4f,both.y,.00001f)
        val far=near.copy(x=.2f,y=.2f)
        assertEquals(far,wireAlign(far,12f/1920,12f/1080))
    }
    @Test fun dragResizeBoundsAspectAndSingleHistoryStep() {
        val before=WireDocument().insert(WireTemplates.items.first {it.id=="fab"})
        val e=before.elements.single()
        val moved=wireTransform(e,.12f,.15f,false)
        assertEquals(e.x+.12f,moved.x,.00001f)
        val resized=wireTransform(moved,.2f,.2f,true)
        assertEquals(e.w/e.h,resized.w/resized.h,.0001f)
        val bounded=wireTransform(resized,5f,5f,true)
        assertTrue(bounded.x+bounded.w<=1.00001f && bounded.y+bounded.h<=1.00001f)
        val after=before.copy(elements=listOf(bounded));val history=WireHistory()
        history.record(before,after);assertEquals(before,history.undo(after));assertFalse(history.canUndo)
        assertEquals(after,history.redo(before))
    }
    @Test fun pngUsesCompleteDocumentResolutionAndNoEditorDecorations() {
        val doc=WireDocument(background=0xffeeeeee).copy(elements=listOf(WireElement(x=.8f,y=.8f,w=.1f,h=.1f,fill=0xffff0000,stroke=0xffff0000,border=0f)))
        WireframeRenderer(context,emptyMap()).use {renderer->
            val output=ByteArrayOutputStream();renderer.png(doc,false,output)
            val bitmap=BitmapFactory.decodeByteArray(output.toByteArray(),0,output.size())
            assertEquals(1080,bitmap.width);assertEquals(2340,bitmap.height)
            assertEquals(Color.RED,bitmap.getPixel(918,1989))
            assertEquals(0xffeeeeee.toInt(),bitmap.getPixel(5,5))
            bitmap.recycle()
            val signed=renderer.bitmap(doc,signature=true);assertEquals(2388,signed.height);signed.recycle()
            val desktop=renderer.bitmap(doc.copy(format=WireFormat.DESKTOP));assertEquals(1920,desktop.width);assertEquals(1080,desktop.height);desktop.recycle()
        }
    }
    @Test fun encryptedBackupAndAtomicAssetLifecycle()=runBlocking<Unit> {
        val id=UUID.randomUUID();val root=File(context.filesDir,"wireframe-$id").apply {mkdirs()}
        val session=SecureFiles.Session(id,root,VaultCrypto.randomKey());SecureFiles.register(session)
        val vault=VaultContext(context,session)
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build();vault.database=db
        try {
            val preferences=vault.getSharedPreferences(WireCodec.NAME,0);val repository=WireframeRepository(preferences)
            val original=drawing().let {d->d.copy(elements=WireTemplates.items.map {t->t.element(d.format).let {e->if(e.kind==WireKind.IMAGE) e.copy(asset=d.assets.keys.single()) else e}.bounded()})};val raw=repository.save(original,null)
            val replacementId=UUID.randomUUID().toString()
            val replacement=original.copy(elements=original.elements.map {if(it.kind==WireKind.IMAGE) it.copy(asset=replacementId) else it},assets=mapOf(replacementId to original.assets.values.single()))
            val failingPreferences=object:android.content.SharedPreferences by preferences {
                override fun edit():android.content.SharedPreferences.Editor = object:android.content.SharedPreferences.Editor by preferences.edit() {
                    override fun putString(key:String?,value:String?):android.content.SharedPreferences.Editor=this
                    override fun commit()=false
                }
            }
            assertTrue(runCatching {WireframeRepository(failingPreferences).save(replacement,raw)}.isFailure)
            assertEquals(original,repository.load(original.id).first)
            val backup=BackupRepository(db,vault,BackupAvatarFiles(vault,BackupFixture::syncDirectory))
            val archive=backup.create("wireframe-fixture".toCharArray())
            val candidate=backup.inspect(SecureFiles.input(archive),"wireframe-fixture".toCharArray())
            val copied=repository.duplicate(original.id)
            val shared=original.copy(elements=original.elements+original.elements.last().copy(id=UUID.randomUUID().toString()))
            var current=repository.save(shared,raw)
            val oneRemoved=shared.copy(elements=shared.elements.dropLast(1)).prune()
            assertEquals(original.assets,oneRemoved.assets)
            current=repository.save(oneRemoved,current)
            val noImages=oneRemoved.copy(elements=oneRemoved.elements.filter {it.kind!=WireKind.IMAGE}).prune()
            val clean=repository.save(noImages,current);assertTrue(repository.load(original.id).first.assets.isEmpty())
            assertEquals(original.assets,repository.load(copied.id).first.assets)
            // Stale writes, invalid replacements and failed edits preserve the committed state.
            assertTrue(runCatching {repository.save(original,raw)}.isFailure)
            assertTrue(runCatching {repository.save(original.copy(assets=emptyMap()),clean)}.isFailure)
            assertEquals(noImages,repository.load(original.id).first)
            repository.delete(original.id,clean);assertEquals(original.assets,repository.load(copied.id).first.assets)
            backup.restore(candidate);backup.discard(candidate);backup.discardPending(archive.name)
            assertEquals(original,repository.load(original.id).first)
            assertEquals(1,preferences.all.size)
            // A database failure after clearing rows rolls the entire preference namespace back.
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_wire_restore BEFORE INSERT ON private_preferences BEGIN SELECT RAISE(ABORT, 'Injected failure'); END")
            assertTrue(runCatching {backup.restore(backup.snapshot())}.isFailure)
            assertEquals(original,repository.load(original.id).first)
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_wire_restore")
            val reopened=WireframeRepository(vault.getSharedPreferences(WireCodec.NAME,0));assertEquals(original,reopened.load(original.id).first)
            val before=backup.snapshot()
            val bad=before.copy(privatePreferences=before.privatePreferences.map {if(it.name==WireCodec.NAME) it.copy(payload=EncryptedPreferences.encode(mapOf(original.id to "{}"))) else it})
            assertTrue(runCatching {backup.restore(bad)}.isFailure)
            assertEquals(before.privatePreferences,backup.snapshot().privatePreferences)
            // Historical backups without the namespace restore an empty library.
            backup.restore(before.copy(privatePreferences=emptyList()));assertTrue(preferences.all.isEmpty())
            // A cancelled editor's immutable draft has never written or reserved image files.
            drawing();assertTrue(preferences.all.isEmpty())
        } finally {db.close();vault.clearMemory();SecureFiles.revoke(session);root.deleteRecursively()}
    }
}
