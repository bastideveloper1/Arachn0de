package com.r0ybt.arachn0de.export

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.photos.PublicImages
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PublicExportStagingTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private val root get()=File(context.cacheDir,"public-exports-v1")
    @Test fun encryptedCopiesHaveIndependentReservationsAndReplacementNeverChangesSource() {
        val bytes="SYNTHETIC PRIVATE SOURCE".repeat(100).toByteArray();val before=bytes.copyOf()
        val a=PublicExportStaging(context,sync=BackupFixture::syncDirectory)
        val b=PublicExportStaging(context,sync=BackupFixture::syncDirectory)
        val one=a.put(bytes);val two=a.put("replacement".toByteArray());val other=b.put(bytes)
        root.walkTopDown().filter {it.extension=="enc"}.forEach {assertFalse(it.readBytes().toString(Charsets.ISO_8859_1).contains("SYNTHETIC PRIVATE SOURCE"))}
        PublicExportStaging.recover(context,BackupFixture::syncDirectory)
        assertArrayEquals(bytes,a.input(one).use {it.readBytes()});assertEquals("replacement",a.input(two).use {it.readBytes().toString(Charsets.UTF_8)})
        a.close();assertArrayEquals(bytes,b.input(other).use {it.readBytes()});assertArrayEquals(before,bytes)
        b.close();assertTrue(root.listFiles().orEmpty().isEmpty())
    }
    @Test fun deletionFailureRetainsDurableCleanupAndRestartRecoveryOnlyRemovesOwnedDerivatives() {
        val stage=PublicExportStaging(context,delete={false},sync=BackupFixture::syncDirectory);stage.put("synthetic".toByteArray());stage.close()
        assertTrue(root.listFiles().orEmpty().any {it.extension=="lease"})
        val unknown=File(root,"user-file-do-not-remove").apply {writeText("preserve")}
        // A well-formed inactive lease represents a lost ephemeral key after a process exit.
        root.listFiles().orEmpty().filter {it.extension=="lease"}.forEach {it.writeText("public-export-v1\n${UUID.randomUUID()}")}
        PublicExportStaging.recover(context,BackupFixture::syncDirectory)
        assertEquals("preserve",unknown.readText());assertEquals(listOf(unknown.name),root.listFiles()!!.map {it.name});unknown.delete()
    }
    @Test fun imagePackageFailureAndCancellationReleaseStagingAndKeepPrivateBytes()=runBlocking<Unit> {
        val raw=File("src/androidTest/assets/public-images/animated.png").readBytes();val id=UUID.randomUUID().toString();val name="$id.png"
        val source=File(context.cacheDir,name).apply {writeBytes(raw)}
        val row=AttachmentFileEntity(id,name,"synthetic.png","image/png",raw.size.toLong(),48,32,imageHash(raw),1)
        val export=ElementExport(BackupData("synthetic",1,emptyList(),emptyList(),emptyList(),emptyList(),emptyMap(),attachmentFiles=listOf(row),attachmentContents=mapOf(name to source)),emptyList(),null,null,false)
        try {
            val failure=object:OutputStream() {override fun write(b:Int) {throw IOException("synthetic failure")}}
            assertTrue(runCatching {export.write(failure,true,context,BackupFixture::syncDirectory)}.isFailure)
            assertTrue(root.listFiles().orEmpty().isEmpty());assertArrayEquals(raw,source.readBytes())
            var operation:Job?=null
            val cancelled=object:OutputStream() {override fun write(b:Int) {operation!!.cancel()};override fun write(b:ByteArray,o:Int,n:Int) {operation!!.cancel()}}
            val job=launch(start=CoroutineStart.LAZY) {export.write(cancelled,true,context,BackupFixture::syncDirectory)};operation=job;job.start();job.join()
            assertTrue(job.isCancelled);assertTrue(root.listFiles().orEmpty().isEmpty());assertArrayEquals(raw,source.readBytes())
        } finally {source.delete()}
    }
}
