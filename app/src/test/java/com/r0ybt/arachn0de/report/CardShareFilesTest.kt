package com.r0ybt.arachn0de.report

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
class CardShareFilesTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private val colors=CardShareColors(Color.BLACK,Color.WHITE,Color.MAGENTA)
    private fun data()=CardShareData("Tarjeta de prueba","Información completa",listOf(CardSharePicture("Imagen panorámica") {Bitmap.createBitmap(640,160,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.GREEN)}}))
    @Test fun manyImagesIncludingHistoricalAnnotationsFitWithoutCroppingOrRetainedBitmaps() {
        val base=Bitmap.createBitmap(300,300,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.GREEN)}
        val original=java.io.ByteArrayOutputStream().also {base.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray()
        val marks=listOf(com.r0ybt.arachn0de.annotations.ImageMark(tool=com.r0ybt.arachn0de.annotations.MarkTool.RECT,
            points=listOf(com.r0ybt.arachn0de.annotations.MarkPoint(.2f,.2f),com.r0ybt.arachn0de.annotations.MarkPoint(.8f,.8f))))
        val annotated=com.r0ybt.arachn0de.annotations.EditableImagePng.write(com.r0ybt.arachn0de.annotations.markedPng(base,marks),original,marks)
        base.recycle()
        val loaded=mutableListOf<Bitmap>()
        val data=CardShareData("Muchas imágenes","Descripción",(0..9).map {i->CardSharePicture("Imagen $i") {
            assertTrue(loaded.all {it.isRecycled})
            BitmapFactory.decodeByteArray(if(i==9)annotated else original,0,if(i==9)annotated.size else original.size).also {loaded+=it}
        }})
        val rendered=CardShareRenderer.render(data,colors)
        try {assertTrue(rendered.height<=CardShareRenderer.MAX_HEIGHT);assertEquals(20,loaded.size);assertTrue(loaded.all {it.isRecycled})}
        finally {rendered.recycle()}
    }

    @Test fun portraitReferenceDecodeKeepsReadableWidthAndDoesNotChangeOriginal() {
        val file=File(context.cacheDir,"portrait-reference-test.png")
        val original=Bitmap.createBitmap(400,3000,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.GREEN)}
        try {
            file.outputStream().use {original.compress(Bitmap.CompressFormat.PNG,100,it)}
            val bytes=file.readBytes()
            val decoded=requireNotNull(decodeCardReference(file))
            try {assertEquals(400,decoded.width);assertEquals(3000,decoded.height)} finally {decoded.recycle()}
            assertArrayEquals(bytes,file.readBytes())
        } finally {original.recycle();file.delete()}
    }
    @Test fun referenceSheetKeepsTallAttachmentsFullWidthWithoutCropping() {
        val sheet=CardShareData("Ver si existe la posibilidad de exportar una tarea en imagen",
            "La imagen adjunta es la referencia de la tarea. Debe poder verse completa y leerse sin convertirse en una miniatura.",
            listOf(CardSharePicture("captura.png · PNG · 1,2 MB") {
                Bitmap.createBitmap(400,1000,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.GREEN)}
            }),project="Arachn0de",status="Pendiente",fields=listOf(
                CardShareField("Creada","27 oct 2026\n10:07"),CardShareField("Asignada","—"),
                CardShareField("Vencimiento","—"),CardShareField("Etiqueta","consulta")))
        val bitmap=CardShareRenderer.render(sheet,CardShareColors(0xff101416.toInt(),0xffeef0f2.toInt(),Color.CYAN))
        try {
            var top=-1;var bottom=-1
            for(y in 0 until bitmap.height)if(bitmap.getPixel(540,y)==Color.GREEN) {if(top<0)top=y;bottom=y}
            assertTrue(top>0);assertTrue(kotlin.math.abs(2440-(bottom-top+1))<=4)
            assertEquals(Color.GREEN,bitmap.getPixel(55,top+100))
            assertEquals(Color.GREEN,bitmap.getPixel(1024,top+100))
            assertTrue(bitmap.height>bottom+40)
            File(System.getProperty("java.io.tmpdir"),"arachnode-card-reference-preview.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))
            }
        } finally {bitmap.recycle()}
    }
    @Test fun compactPicturesKeepBoundedDimensionsAndOriginalProportions() {
        val bitmap=CardShareRenderer.render(CardShareData("Tarjeta","Información",listOf(
            CardSharePicture("Persona",compact=true) {Bitmap.createBitmap(800,1600,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.GREEN)}},
            CardSharePicture("Tecnología",compact=true) {Bitmap.createBitmap(2000,500,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.BLUE)}}
        )),colors)
        try {
            fun bounds(color:Int):Pair<Int,Int> {
                var left=bitmap.width;var top=bitmap.height;var right=-1;var bottom=-1
                for(y in 0 until bitmap.height) for(x in 0 until bitmap.width) {
                    if(bitmap.getPixel(x,y)==color) {left=minOf(left,x);right=maxOf(right,x);top=minOf(top,y);bottom=maxOf(bottom,y)}
                }
                assertTrue(right>=left)
                return (right-left+1) to (bottom-top+1)
            }
            assertEquals(80 to 160,bounds(Color.GREEN))
            assertEquals(160 to 40,bounds(Color.BLUE))
            assertTrue(bitmap.height<1000)
        } finally {bitmap.recycle()}
    }
    @Test fun saveUsesGeneratedPngAndReportsWriteFailure()=runBlocking<Unit> {
        val files=CardShareFiles(context,sync=com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory)
        val file=files.generate({data()},colors)
        val destination=Uri.parse("content://test-documents/card.png")
        val output=java.io.ByteArrayOutputStream()
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerOutputStream(destination,output)
        files.save(file,destination)
        assertArrayEquals(file.readBytes(),output.toByteArray())
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerOutputStream(destination,object:java.io.OutputStream() {
            override fun write(value:Int) {throw IOException("Injected write failure")}
        })
        try {files.save(file,destination);fail("Expected write failure")} catch(_:IOException) {}
        assertTrue(file.exists())
        try {files.save(file,Uri.parse("file:///tmp/card.png"));fail("Expected content URI")} catch(_:IllegalArgumentException) {}
        files.discard(file);assertFalse(file.exists())
    }
    @Test fun pngAndShareAreCompleteReadOnlyAndNarrow()=runBlocking<Unit> {
        val files=CardShareFiles(context,sync=com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory)
        val file=files.generate({data()},colors)
        val bitmap=BitmapFactory.decodeFile(file.path)!!
        assertEquals(1080,bitmap.width);assertTrue(bitmap.height>200);bitmap.recycle()
        val intent=files.shareIntent(file);val uri=intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals(Intent.ACTION_SEND,intent.action);assertEquals("image/png",intent.type)
        assertEquals("content",uri.scheme);assertEquals("${context.packageName}.reports",uri.authority)
        assertEquals(uri,intent.clipData!!.getItemAt(0).uri)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,intent.flags)
        assertEquals("image/png",context.contentResolver.getType(uri))
        // Real nonblocking reliable pipes are exercised by CardShareSessionTest.
        // Robolectric models pipes as files and does not implement Os.poll.
        assertThrows(java.io.FileNotFoundException::class.java) {context.contentResolver.openFileDescriptor(uri,"w")}
        val outside=File(context.cacheDir,file.name).apply {writeText("original")}
        assertThrows(IllegalArgumentException::class.java) {files.shareIntent(outside)}
        files.discard(file);assertFalse(file.exists());assertEquals("original",outside.readText());outside.delete()
    }
    @Test fun failureCancellationAndOversizeNeverPublishPartialImages()=runBlocking<Unit> {
        val directory=File(context.cacheDir,CardShareFiles.DIRECTORY).apply {mkdirs()}
        val before=directory.listFiles()!!.map {it.name}.toSet()
        try {CardShareFiles(context,sync={throw IOException("Injected sync failure")}).generate({data()},colors);fail()} catch(_:IOException) {}
        try {CardShareFiles(context,sync=com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory).generate({throw kotlinx.coroutines.CancellationException("Cancelled")},colors);fail()} catch(_:kotlinx.coroutines.CancellationException) {}
        try {CardShareFiles(context,sync=com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory).generate({CardShareData("Título","línea\n".repeat(1000))},colors);fail()} catch(_:IllegalArgumentException) {}
        assertEquals(before,directory.listFiles()!!.map {it.name}.toSet())
    }
    @Test fun cleanupPreservesRecentSharesAndUnknownFilesAndRetriesPendingDeletion()=runBlocking<Unit> {
        val files=CardShareFiles(context,sync=com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory);val file=files.generate({data()},colors)
        val directory=file.parentFile!!;val original=File(directory,"original-photo.png").apply {writeText("untouched")}
        files.cleanup();assertTrue(file.exists())
        val staging=File(directory,file.name+".part").apply {writeText("partial")}
        val pending=File(directory,file.name+".delete").apply {writeText("retry")}
        files.cleanup();assertFalse(file.exists());assertFalse(staging.exists());assertFalse(pending.exists())
        assertEquals("untouched",original.readText());original.delete()
    }
    @Test fun sourceImagesRemainByteForByteAfterSharingReplacementAndDiscard()=runBlocking<Unit> {
        val original=File(context.filesDir,"synthetic-original.png")
        val bitmap=Bitmap.createBitmap(80,320,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.BLUE)}
        original.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
        val bytes=original.readBytes()
        val files=CardShareFiles(context,sync=com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory)
        val source=CardShareData("Retrato","Nota",listOf(CardSharePicture("Original") {BitmapFactory.decodeFile(original.path)}))
        val first=files.generate({source},colors);val second=files.generate({source},colors)
        files.discard(first);assertTrue(second.exists());assertArrayEquals(bytes,original.readBytes())
        files.discard(second);assertArrayEquals(bytes,original.readBytes());original.delete()
    }
    @Test fun encryptedShareNeverCachesPlaintextAndIsRevokedWithItsSession()=runBlocking<Unit> {
        val id=java.util.UUID.randomUUID()
        val root=File(context.filesDir,"security-v1/stores/$id").apply {mkdirs()}
        val access=com.r0ybt.arachn0de.security.SecureFiles.Session(id,root,com.r0ybt.arachn0de.security.VaultCrypto.randomKey())
        com.r0ybt.arachn0de.security.SecureFiles.register(access)
        com.r0ybt.arachn0de.security.SecureFiles.activeProfile=id
        try {
            val storage=com.r0ybt.arachn0de.security.VaultContext(context,access)
            val files=CardShareFiles(storage,sync=com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory);val file=files.generate({data()},colors)
            val plain=com.r0ybt.arachn0de.security.SecureFiles.read(file)
            assertArrayEquals(byteArrayOf(-119,80,78,71,13,10,26,10),plain.copyOf(8))
            assertFalse(file.readBytes().contentEquals(plain))
            val uri=files.shareIntent(file).getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals(listOf("v1",id.toString(),file.name),uri.pathSegments)
            assertEquals("image/png",context.contentResolver.getType(uri))
            com.r0ybt.arachn0de.security.SecureFiles.activeProfile=java.util.UUID.randomUUID()
            assertTrue(runCatching {context.contentResolver.getType(uri)}.isFailure)
            assertTrue(runCatching {files.shareIntent(file)}.isFailure)
        } finally {
            com.r0ybt.arachn0de.security.SecureFiles.activeProfile=null
            com.r0ybt.arachn0de.security.SecureFiles.revoke(access);root.deleteRecursively()
        }
    }
    @Test fun cacheLimitKeepsRecentRecipientFiles()=runBlocking<Unit> {
        val directory=File(context.cacheDir,CardShareFiles.DIRECTORY).apply {mkdirs()}
        val held=(1..CardShareFiles.MAX_FILES).map {File(directory,"arachn0de-card-${java.util.UUID.randomUUID()}.png").apply {writeText("recipient")}}
        try {CardShareFiles(context,sync=com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory).generate({data()},colors);fail()} catch(_:IllegalStateException) {}
        held.forEach {assertEquals("recipient",it.readText());it.delete()}
    }
}
