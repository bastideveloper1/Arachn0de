package com.r0ybt.arachn0de.report

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
class ObligationReportFilesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun file(): File {
        val dir = File(context.cacheDir, ObligationReportFiles.CACHE_DIRECTORY).apply { mkdirs() }
        return File(dir, ObligationReportFiles.filename(ReportFixture.now, ReportFixture.zone.id))
            .apply { writeBytes(byteArrayOf(-119,80,78,71,13,10,26,10)) }
    }
    @Test fun filenameIsNeutralUniqueAndShareGrantsOnlyReadToNarrowContentUri() {
        val files = ObligationReportFiles(context)
        val file = file()
        try {
            assertTrue(file.name.matches(Regex("arachn0de-obligations-2026-10-03-120000-000-[a-f0-9]{8}\\.png")))
            assertNotEquals(file.name, ObligationReportFiles.filename(ReportFixture.now, ReportFixture.zone.id))
            val intent = files.shareIntent(file)
            assertEquals(Intent.ACTION_SEND, intent.action); assertEquals("image/png", intent.type)
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals("content", uri.scheme)
            assertEquals("${context.packageName}.reports", uri.authority)
            assertTrue(uri.path!!.startsWith("/obligation_reports/"))
            assertEquals("image/png", context.contentResolver.getType(uri))
            context.contentResolver.openInputStream(uri)!!.use { assertArrayEquals(file.readBytes(), it.readBytes()) }
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertThrows(IllegalArgumentException::class.java) { files.shareIntent(File(context.cacheDir, "outside.png").apply { writeText("outside") }) }
            assertThrows(IllegalArgumentException::class.java) { files.cachedFile("../outside.png") }
        } finally { file.delete() }
    }
    @Test fun documentContractUsesCreateDocumentPngAndSuggestedNeutralName() {
        val name = ObligationReportFiles.filename(ReportFixture.now, ReportFixture.zone.id)
        val intent = androidx.activity.result.contract.ActivityResultContracts.CreateDocument(ObligationReportFiles.MIME)
            .createIntent(context, name)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("image/png", intent.type)
        assertEquals(name, intent.getStringExtra(Intent.EXTRA_TITLE))
    }
    @Test fun cachePublicationNeverExposesPartialOrEmptyFiles() {
        val target = File(File(context.cacheDir, ObligationReportFiles.CACHE_DIRECTORY).apply { mkdirs() }, "atomic.png")
        assertThrows(IOException::class.java) {
            ObligationReportFiles.publish(target) { it.write(1); throw IOException("Injected write failure") }
        }
        assertFalse(target.exists()); assertFalse(File(target.parentFile, "${target.name}.part").exists())
        assertThrows(IOException::class.java) { ObligationReportFiles.publish(target) {} }
        assertFalse(target.exists())
        ObligationReportFiles.publish(target) { it.write(byteArrayOf(1, 2, 3)) }
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
        target.delete()
    }
    @Test fun saveCopiesBytesAndPropagatesWriteFailureWithoutSuccess() = runBlocking {
        val file = file()
        val files = ObligationReportFiles(context)
        val uri = Uri.parse("content://test-documents/report.png")
        val bytes = java.io.ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(uri, bytes)
        files.save(file, uri)
        assertArrayEquals(file.readBytes(), bytes.toByteArray())
        shadowOf(context.contentResolver).registerOutputStream(uri, object : OutputStream() {
            override fun write(value: Int) { throw IOException("Injected document write failure") }
        })
        try {
            files.save(file, uri)
            fail("Expected write failure")
        } catch (_: IOException) {}
        assertTrue(file.exists())
        try { files.save(file, Uri.parse("file:///tmp/report.png")); fail("Expected URI rejection") }
        catch (_: IllegalArgumentException) {}
        file.delete()
        Unit
    }
}
