package com.r0ybt.arachn0de.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
class BackupDocumentsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun contractsUseSafAndDoNotRequestBroadStoragePermissions() {
        val create = CreateBackupDocument().createIntent(context, "Arachn0de-Backup-2026-10-03.arachnode")
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, create.action)
        assertEquals("Arachn0de-Backup-2026-10-03.arachnode", create.getStringExtra(Intent.EXTRA_TITLE))
        val open = OpenBackupDocument().createIntent(context, arrayOf("*/*"))
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, open.action)
        assertTrue(open.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
        assertTrue(create.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
        assertEquals("*/*", open.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.single())
    }
    @Test fun completeFileCopiesExactlyAndReadValidatesBeforeReturning() = runBlocking {
        val file = File(context.cacheDir, "backup-test.arachnode").apply { writeBytes(BackupFixture.archive(BackupFixture.complete())) }
        val uri = Uri.parse("content://backup-test/document")
        val output = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(uri, output)
        val documents = BackupDocuments(context.contentResolver)
        documents.save(file, uri)
        assertArrayEquals(file.readBytes(), output.toByteArray())
        val db = Arachn0deDatabase.create(context)
        try {
            shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(output.toByteArray()))
            BackupFixture.assertData(BackupFixture.complete(), documents.read(BackupRepository(db, context), uri))
        } finally { db.close(); file.delete() }
    }
    @Test fun providerFailureAndInvalidLocalFileNeverReportSuccess() = runBlocking {
        val file = File(context.cacheDir, "backup-test.arachnode").apply { writeBytes(BackupFixture.archive(BackupFixture.empty())) }
        val uri = Uri.parse("content://backup-test/failure")
        val partial = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(uri, object : OutputStream() {
            override fun write(value: Int) {
                if (partial.size() >= 10) throw IOException("Injected failure")
                partial.write(value)
            }
        })
        val documents = BackupDocuments(context.contentResolver)
        assertTrue(runCatching { documents.save(file, uri) }.isFailure)
        assertTrue(runCatching { BackupContainer.read(ByteArrayInputStream(partial.toByteArray())) }.isFailure)
        file.writeBytes(byteArrayOf(1))
        assertTrue(runCatching { documents.save(file, uri) }.isFailure)
        assertTrue(runCatching { documents.save(file, Uri.parse("file:///outside")) }.isFailure)
        file.delete()
        Unit
    }
}
