package com.r0ybt.arachn0de.wireframe

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WireframeScreensTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    @Test fun completeCatalogIsNativeEditableAndCompatible() {
        assertEquals(25,WireScreens.items.size);assertEquals(25,WireScreens.items.map {it.id}.distinct().size)
        assertEquals(listOf(5,5,5,5,5),WireScreens.items.groupBy {it.group}.values.map {it.size})
        assertEquals(80,WireTemplates.items.size);assertEquals(8,WireTemplates.items.map {it.category}.distinct().size)
        assertEquals(20,WireScreens.items.count {it.format==WireFormat.MOBILE})
        val structures=mutableSetOf<List<List<Any>>>()
        WireScreens.items.forEach {screen->
            val blank=WireDocument(format=screen.format);val doc=blank.insert(screen)
            assertTrue(screen.id,doc.elements.size in 6..128);WireCodec.validate(doc)
            assertEquals(doc,WireCodec.decode(WireCodec.encode(doc)))
            assertTrue(doc.elements.all {e->WireTemplates.items.any {it.id==e.template && it.kind==e.kind}})
            assertTrue(structures.add(doc.elements.map {listOf(it.template!!,it.x,it.y,it.w,it.h)}))
            val history=WireHistory();history.record(blank,doc)
            assertEquals(blank,history.undo(doc));assertFalse(history.canUndo);assertEquals(doc,history.redo(blank))
            val first=doc.elements.first {it.editableText}
            val edited=doc.copy(elements=doc.elements.map {if(it.id==first.id) it.copy(text="Mi proyecto",ink=0xff116611,x=it.x+.001f,w=it.w-.002f) else it})
            assertEquals(doc.elements.size,edited.elements.size);assertNotEquals(doc,edited)
            assertEquals(edited,WireCodec.decode(WireCodec.encode(edited)))
            val removed=edited.copy(elements=edited.elements.filterNot {it.id==first.id})
            assertEquals(doc.elements.size-1,removed.elements.size);WireCodec.validate(removed)
            assertTrue(doc.elements.map {it.id}.intersect(screen.elements().map {it.id}.toSet()).isEmpty())
        }
    }
    @Test fun appendAndRejectedInsertionsPreserveExistingContent() {
        val screen=WireScreens.items.first();val original=WireDocument().insert(WireTemplates.items.first {it.id=="title"})
        val next=original.insert(screen)
        assertEquals(original.elements,next.elements.take(original.elements.size));assertEquals(original.assets,next.assets)
        val history=WireHistory();history.record(original,next);assertEquals(original,history.undo(next));assertEquals(next,history.redo(original))
        assertTrue(runCatching {original.insert(WireScreens.items.last())}.isFailure)
        val full=original.copy(elements=List(125) {original.elements.single().copy(id=UUID.randomUUID().toString())})
        assertTrue(runCatching {full.insert(screen)}.isFailure);assertEquals(125,full.elements.size)
    }
    @Test fun nativeRenderingAndContactSheetUseRealElements() {
        val sheet=Bitmap.createBitmap(1200,1500,Bitmap.Config.ARGB_8888);val canvas=Canvas(sheet);canvas.drawColor(Color.LTGRAY)
        WireScreens.items.forEachIndexed {index,screen->
            val doc=screen.preview()
            WireframeRenderer(context,emptyMap()).use {renderer->
                val full=renderer.bitmap(doc)
                assertEquals(screen.format.width.toInt(),full.width);assertEquals(screen.format.height.toInt(),full.height)
                val pixels=IntArray(full.width*full.height);full.getPixels(pixels,0,full.width,0,0,full.width,full.height)
                assertTrue(screen.id,pixels.count {it!=Color.WHITE}>pixels.size/100)
                val x=(index%5)*240f;val y=(index/5)*300f
                val scale=minOf(220f/full.width,260f/full.height)
                canvas.save();canvas.translate(x+(240-full.width*scale)/2,y+28);canvas.scale(scale,scale);canvas.drawBitmap(full,0f,0f,null);canvas.restore()
                canvas.drawText(screen.id,x+10,y+20,android.graphics.Paint().apply {color=Color.BLACK;textSize=16f})
                full.recycle()
                if(screen.id in setOf("login","register","dashboard","shop","info","desktop")) {
                    val png=java.io.ByteArrayOutputStream();renderer.png(doc,false,png)
                    val decoded=android.graphics.BitmapFactory.decodeByteArray(png.toByteArray(),0,png.size())
                    assertEquals(screen.format.width.toInt(),decoded.width);assertEquals(screen.format.height.toInt(),decoded.height);decoded.recycle()
                }
            }
        }
        FileOutputStream("/tmp/wire-screen-contact.png").use {sheet.compress(Bitmap.CompressFormat.PNG,100,it)};sheet.recycle()
    }
    @Test fun allScreensReopenAndRestoreFromEncryptedBackup()=runBlocking<Unit> {
        val id=UUID.randomUUID();val root=File(context.filesDir,"wire-screens-$id").apply {mkdirs()}
        val session=SecureFiles.Session(id,root,VaultCrypto.randomKey());SecureFiles.register(session)
        val vault=VaultContext(context,session)
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build();vault.database=db
        try {
            val prefs=vault.getSharedPreferences(WireCodec.NAME,0);val repo=WireframeRepository(prefs)
            val docs=WireScreens.items.map {screen->screen.preview().also {repo.save(it,null)}}
            val reopened=WireframeRepository(vault.getSharedPreferences(WireCodec.NAME,0))
            docs.forEach {assertEquals(it,reopened.load(it.id).first)}
            val backup=BackupRepository(db,vault,BackupAvatarFiles(vault,BackupFixture::syncDirectory))
            val archive=backup.create("screen-fixture".toCharArray());val candidate=backup.inspect(SecureFiles.input(archive),"screen-fixture".toCharArray())
            docs.forEach {repo.delete(it.id,WireCodec.encode(it))};assertTrue(prefs.all.isEmpty())
            backup.restore(candidate);backup.discard(candidate);backup.discardPending(archive.name)
            assertEquals(25,prefs.all.size);docs.forEach {assertEquals(it,reopened.load(it.id).first)}
            val before=backup.snapshot()
            val bad=before.copy(privatePreferences=before.privatePreferences.map {if(it.name==WireCodec.NAME) it.copy(payload=EncryptedPreferences.encode(mapOf(docs.first().id to "{}"))) else it})
            assertTrue(runCatching {backup.restore(bad)}.isFailure)
            assertEquals(before.privatePreferences,backup.snapshot().privatePreferences)
        } finally {db.close();vault.clearMemory();SecureFiles.revoke(session);root.deleteRecursively()}
    }
}
