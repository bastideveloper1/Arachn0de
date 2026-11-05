package com.r0ybt.arachn0de.photos

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.export.*
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*
import java.util.zip.ZipInputStream

/** Run only in a fresh disposable AVD. Never unlocks an existing store. */
@RunWith(AndroidJUnit4::class)
class PublicAnimationInstrumentedTest {
    private fun asset(name:String)=InstrumentationRegistry.getInstrumentation().context.assets.open("public-images/$name").use {it.readBytes()}
    private fun noPrivate(bytes:ByteArray) {assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("A1_PRIVATE"));assertNull(com.r0ybt.arachn0de.annotations.EditableImagePng.read(bytes))}
    @Test fun apngProviderZipAndEncryptedBackupRoundTrip()=runBlocking<Unit> {
        val app=ApplicationProvider.getApplicationContext<Arachn0deApplication>()
        require(!File(app.filesDir,"security-v1/index").exists()) {"This test requires a fresh disposable AVD."}
        val artifacts=File(app.cacheDir,"a1-public").apply {mkdirs()}
        app.security.setup("A1-only-synthetic-2026".toCharArray())
        try {
            val s=app.security.current.value!!;val vault=s.context;val db=s.database
            for(name in listOf("static.webp","animated.webp","oriented.webp","oriented.png")) {
                val raw=asset(name);val bytes=PublicImages.file(raw).bytes
                noPrivate(bytes)
                val originalPixels=PublicImages.render(raw)
                val publicPixels=PublicImages.render(bytes)
                try {assertEquals(originalPixels.width,publicPixels.width);assertEquals(originalPixels.height,publicPixels.height)}
                finally {originalPixels.recycle();publicPixels.recycle()}
                assertArrayEquals(asset(name),raw);File(artifacts,name).writeBytes(bytes)
            }
            db.projectDao().insert(ProjectEntity("p","Synthetic","",0,1,1))
            db.nodeDao().insert(NodeEntity("n","p",null,"Synthetic","",false,0,1,1))
            val source=File(artifacts,"private-source.png").apply {writeBytes(asset("animated.png"))}
            val raw=source.readBytes();val row=s.attachments.import(Uri.fromFile(source),"synthetic-apng");s.attachments.associateNode("n",row.id);source.delete()
            val file=s.attachments.localFile(row);val ciphertext=file.readBytes();assertFalse(raw.contentEquals(ciphertext))
            val uri=Uri.parse("content://${app.packageName}.attachments/${s.access.id}/${row.id}")
            assertEquals("image/png",app.contentResolver.getType(uri))
            val shared=app.contentResolver.openInputStream(uri)!!.use {it.readBytes()};noPrivate(shared)
            assertArrayEquals(PublicImages.prepare(raw),shared);File(artifacts,"shared.png").writeBytes(shared)
            val data=BackupData("synthetic",1,emptyList(),listOf(db.nodeDao().getById("n")!!),emptyList(),emptyList(),emptyMap(),attachmentFiles=listOf(row),nodeAttachments=listOf(NodeAttachmentEntity("n",row.id)),attachmentContents=mapOf(row.storageName to file))
            for(json in listOf(true,false)) {
                val out=ByteArrayOutputStream();ElementExport(data,listOf("n" to 0),"p","n",false).write(out,json,vault)
                val entries=linkedMapOf<String,ByteArray>();ZipInputStream(out.toByteArray().inputStream()).use {zip->while(true) {val e=zip.nextEntry ?: break;entries[e.name]=zip.readBytes()}}
                val public=entries.getValue("files/attachments/${row.id}.png");assertArrayEquals(shared,public)
                if(json) {val record=JSONObject(String(entries.getValue("element.json"))).getJSONObject("records").getJSONArray("attachmentFiles").getJSONObject(0);assertEquals(imageHash(public),record.getString("sha256"));assertEquals(public.size.toLong(),record.getLong("byteSize"))}
            }
            assertArrayEquals(ciphertext,file.readBytes());assertArrayEquals(raw,SecureFiles.read(file))
            val archive=s.backups.create("A1-backup-synthetic".toCharArray())
            val candidate=s.backups.inspect(SecureFiles.input(archive),"A1-backup-synthetic".toCharArray())
            try {
                assertArrayEquals(raw,SecureFiles.read(candidate.attachmentContents.getValue(row.storageName)))
                s.backups.restore(candidate)
                val restoredRow=db.attachmentDao().files().single();assertArrayEquals(raw,SecureFiles.read(s.attachments.localFile(restoredRow)))
            } finally {s.backups.discard(candidate);s.backups.discardPending(archive.name)}
            assertTrue(File(vault.cacheDir,"public-exports-v1").listFiles().orEmpty().isEmpty())
        } finally {app.security.lock()}
    }
    @Test fun linearIccConvertsMidGrayToSrgbWithoutChangingDimensions() {
        val raw=asset("linear.png");val result=PublicImages.file(raw)
        val pixels=android.graphics.BitmapFactory.decodeByteArray(result.bytes,0,result.bytes.size)
        try {assertEquals(188.0,android.graphics.Color.red(pixels.getPixel(10,10)).toDouble(),2.0);assertEquals(48,pixels.width);assertEquals(32,pixels.height)} finally {pixels.recycle()}
        val rendered=PublicImages.render(raw)
        try {assertEquals(188.0,android.graphics.Color.red(rendered.getPixel(10,10)).toDouble(),2.0)} finally {rendered.recycle()}
    }
    @Test fun androidDecoderAcceptsSanitizedAnimationsAndCorruptionFailsClosed() {
        val app=ApplicationProvider.getApplicationContext<android.content.Context>();val artifacts=File(app.cacheDir,"a1-public").apply {mkdirs()}
        for(name in listOf("animated.webp","animated.png","animated-default.png","ordinary.gif","text.gif","icc.png","icc.jpg")) {
            val raw=asset(name);val result=PublicImages.file(raw);noPrivate(result.bytes)
            val bitmap=PublicImages.render(raw,64);assertTrue(bitmap.width>0);bitmap.recycle()
            if(name.endsWith("webp") || name.endsWith("gif")) {
                val source=android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(result.bytes))
                assertTrue(android.graphics.ImageDecoder.decodeDrawable(source) is android.graphics.drawable.AnimatedImageDrawable)
            }
            assertTrue(runCatching {PublicImages.file(raw.copyOf(raw.size/2))}.isFailure)
            File(artifacts,name).writeBytes(result.bytes);assertArrayEquals(asset(name),raw)
        }
    }
}
