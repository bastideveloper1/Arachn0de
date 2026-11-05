package com.r0ybt.arachn0de.export

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.BackupData
import com.r0ybt.arachn0de.data.local.AttachmentFileEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class PublicExportMemoryTest {
    @Test(timeout=120000) fun largePackageRetainsLessThanOneThirdOfAggregatePayloadBeforeFirstOutput()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val root=File(context.cacheDir,"public-memory-${UUID.randomUUID()}").apply {mkdirs()}
        try {
            val tone=javaClass.getResourceAsStream("/audio/tone.aac")!!.use {it.readBytes()}
            val files=linkedMapOf<String,File>()
            val rows=(0 until 8).map {
                val id=UUID.randomUUID().toString();val name="$id.aac";val file=File(root,name);val hash=MessageDigest.getInstance("SHA-256")
                file.outputStream().use {out->repeat(6*1024*1024/tone.size+1) {out.write(tone);hash.update(tone)}};files[name]=file
                AttachmentFileEntity(id,name,"synthetic.aac","audio/aac",file.length(),0,0,hash.digest().joinToString("") {"%02x".format(it)},1)
            }
            val data=BackupData("synthetic",1,emptyList(),emptyList(),emptyList(),emptyList(),emptyMap(),attachmentFiles=rows,attachmentContents=files)
            val export=ElementExport(data,emptyList(),null,null,false)
            fun heap():Long {System.gc();System.runFinalization();System.gc();return Runtime.getRuntime().let {it.totalMemory()-it.freeMemory()}}
            val baseline=heap();var retained=-1L;var observed=false;val start=System.nanoTime()
            val archive=File(root,"public.zip")
            archive.outputStream().use {sink->
                val probe=object:FilterOutputStream(sink) {
                    fun observe() {if(!observed) {observed=true;retained=heap()-baseline}}
                    override fun write(b:Int) {observe();out.write(b)}
                    override fun write(b:ByteArray,off:Int,len:Int) {observe();out.write(b,off,len)}
                }
                export.write(probe,true,androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>(),com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory)
            }
            val report="{\"payloadBytes\":${rows.sumOf {it.byteSize}},\"retainedBytesAtFirstWrite\":$retained,\"elapsedMs\":${(System.nanoTime()-start)/1_000_000}}"
            File("/tmp/arachnode-a1-memory.json").writeText(report)
            ZipInputStream(archive.inputStream()).use {zip->var count=0;while(true) {val entry=zip.nextEntry ?: break
                if(entry.name.endsWith(".aac")) {val digest=MessageDigest.getInstance("SHA-256");val block=ByteArray(32768);var size=0L
                    while(true) {val n=zip.read(block);if(n<0)break;digest.update(block,0,n);size+=n}
                    assertEquals(rows[count].byteSize,size);assertEquals(rows[count].sha256,digest.digest().joinToString("") {"%02x".format(it)});count++
                }};assertEquals(rows.size,count)}
            assertTrue("Aggregate retention regression: $report",retained<16L*1024*1024)
        } finally {root.deleteRecursively()}
    }
    @Test(timeout=120000) fun imageCopiesAreStagedOneAtATimeWithExactPublicManifestHashes()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val root=File(context.cacheDir,"public-image-memory-${UUID.randomUUID()}").apply {mkdirs()}
        try {
            val template=File(root,"template.png")
            template.outputStream().use {out->
                out.write(byteArrayOf(-119,80,78,71,13,10,26,10))
                val header=ByteArrayOutputStream();DataOutputStream(header).apply {writeInt(1024);writeInt(512);write(byteArrayOf(8,2,0,0,0))}
                writeChunk(out,"IHDR",header.toByteArray())
                val compressed=ByteArrayOutputStream();java.util.zip.DeflaterOutputStream(compressed).use {z->val random=java.util.Random(42);val row=ByteArray(3073);repeat(512) {random.nextBytes(row);row[0]=0;z.write(row)}}
                writeChunk(out,"IDAT",compressed.toByteArray())
                writeChunk(out,"IEND",byteArrayOf())
            }
            val hash=MessageDigest.getInstance("SHA-256").digest(template.readBytes()).joinToString("") {"%02x".format(it)}
            val files=linkedMapOf<String,File>();val rows=(0 until 24).map {
                val id=UUID.randomUUID().toString();val name="$id.png";val file=File(root,name);template.copyTo(file);files[name]=file
                AttachmentFileEntity(id,name,"synthetic.png","image/png",file.length(),1024,512,hash,1)
            }
            val export=ElementExport(BackupData("synthetic",1,emptyList(),emptyList(),emptyList(),emptyList(),emptyMap(),attachmentFiles=rows,attachmentContents=files),emptyList(),null,null,false)
            fun heap():Long {System.gc();System.runFinalization();System.gc();return Runtime.getRuntime().let {it.totalMemory()-it.freeMemory()}}
            val baseline=heap();var retained=-1L;var observed=false;val start=System.nanoTime();val archive=File(root,"public.zip")
            archive.outputStream().use {sink->export.write(object:FilterOutputStream(sink) {
                fun observe() {if(!observed) {observed=true;retained=heap()-baseline}}
                override fun write(b:Int) {observe();out.write(b)}
                override fun write(b:ByteArray,o:Int,n:Int) {observe();out.write(b,o,n)}
            },true,context,com.r0ybt.arachn0de.backup.BackupFixture::syncDirectory)}
            var count=0;ZipInputStream(archive.inputStream()).use {zip->
                val first=zip.nextEntry!!;assertEquals("element.json",first.name)
                val records=org.json.JSONObject(String(zip.readBytes())).getJSONObject("records").getJSONArray("attachmentFiles")
                while(true) {val entry=zip.nextEntry ?: break;assertTrue(entry.name.endsWith(".png"));val digest=MessageDigest.getInstance("SHA-256");val block=ByteArray(32768);var size=0L
                    while(true) {val n=zip.read(block);if(n<0)break;digest.update(block,0,n);size+=n}
                    val record=records.getJSONObject(count++);assertEquals(record.getLong("byteSize"),size);assertEquals(record.getString("sha256"),digest.digest().joinToString("") {"%02x".format(it)})
                }
            };assertEquals(24,count)
            File("/tmp/arachnode-a1-image-memory.json").writeText("{\"payloadBytes\":${rows.sumOf {it.byteSize}},\"retainedBytesAtFirstWrite\":$retained,\"elapsedMs\":${(System.nanoTime()-start)/1_000_000}}")
            assertTrue("Image aggregate retained $retained",retained<8L*1024*1024)
            assertTrue(File(context.cacheDir,"public-exports-v1").listFiles().orEmpty().isEmpty())
        } finally {root.deleteRecursively()}
    }
    private fun writeChunk(out:OutputStream,type:String,payload:ByteArray) {
        val chunk=ByteArrayOutputStream();com.r0ybt.arachn0de.photos.PublicPng.writeChunk(chunk,type,payload);chunk.writeTo(out)
    }

}
