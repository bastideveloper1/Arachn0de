package com.r0ybt.arachn0de.backup

import android.content.Context
import android.graphics.Bitmap
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.security.MessageDigest
import java.util.UUID

@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class AttachmentBackupTest {
    private lateinit var context: Context
    private lateinit var db: Arachn0deDatabase
    private lateinit var repo: BackupRepository
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("arachn0de.db")
        listOf("attachments", "avatars", "backup-restore-journal.json", "backup-attachment-restore-journal.json").forEach { File(context.filesDir, it).deleteRecursively() }
        File(context.cacheDir, "backups").deleteRecursively()
        db = Arachn0deDatabase.create(context)
        repo = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory))
    }
    @After fun cleanup() { db.close(); context.deleteDatabase("arachn0de.db") }
    private fun attachment(jpeg: Boolean = false): Pair<AttachmentFileEntity, ByteArray> {
        val bytes = if (!jpeg) BackupFixture.png() else {
            val image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.JPEG, 90, it); image.recycle() }.toByteArray()
        }
        val id = UUID.randomUUID().toString()
        val extension = if (jpeg) "jpg" else "png"
        return AttachmentFileEntity(id, "$id.$extension", "Foto original ñ.$extension", if (jpeg) "image/jpeg" else "image/png", bytes.size.toLong(), 32, 32,
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, 42) to bytes
    }
    private suspend fun seed(count: Int): BackupData {
        val parts = (0 until count).map { attachment(it % 2 == 1) }
        val text = parts.joinToString("\n") { AttachmentReferences.token(it.first.id, "Descripción") }
        val data = BackupFixture.empty().copy(
            projects = listOf(ProjectEntity("p", "Proyecto", text, 0, 1, 2)),
            nodes = listOf(NodeEntity("layer", "p", null, "Capa", "", false, 0, 1, 2, purpose = "LAYER"), NodeEntity("task", "p", "layer", "Tarea", text, false, 0, 1, 2)),
            attachmentFiles = parts.map { it.first },
            nodeAttachments = parts.map { NodeAttachmentEntity("task", it.first.id) },
            projectAttachments = parts.map { ProjectAttachmentEntity("p", it.first.id) },
        )
        parts.forEach { (row, bytes) -> File(File(context.filesDir, "attachments").apply { mkdirs() }, row.storageName).writeBytes(bytes) }
        db.withTransaction {
            data.projects.forEach { db.projectDao().insert(it) }
            data.nodes.forEach { db.nodeDao().insert(it) }
            data.attachmentFiles.forEach { db.attachmentDao().insert(it) }
            db.attachmentDao().attachNode(data.nodeAttachments); db.attachmentDao().attachProject(data.projectAttachments)
        }
        return repo.snapshot()
    }
    private suspend fun roundTrip(count: Int) {
        val before = seed(count)
        val archive = repo.create()
        val candidate = repo.inspect(archive.inputStream())
        assertEquals(before.attachmentFiles, candidate.attachmentFiles)
        assertEquals(before.nodeAttachments, candidate.nodeAttachments)
        repo.restore(candidate)
        val restored = repo.snapshot()
        assertEquals(before.projects, restored.projects); assertEquals(before.nodes, restored.nodes)
        assertEquals(before.nodeAttachments, restored.nodeAttachments); assertEquals(before.projectAttachments, restored.projectAttachments)
        before.attachmentFiles.forEach { old ->
            val new = restored.attachmentFiles.single { it.id == old.id }
            assertNotEquals(old.storageName, new.storageName)
            assertEquals(old.copy(storageName = new.storageName), new)
            assertArrayEquals(candidate.attachmentContents.getValue(old.storageName).readBytes(), restored.attachmentContents.getValue(new.storageName).readBytes())
            assertFalse(File(context.filesDir, "attachments/${old.storageName}").exists())
        }
        assertEquals(count, File(context.filesDir, "attachments").listFiles().orEmpty().count { it.isFile })
        repo.discard(candidate)
        assertFalse(candidate.inspectionDirectory!!.exists())
    }
    @Test fun backupWithoutAttachments() = runBlocking { roundTrip(0) }
    @Test fun backupWithOneImageAndSharedOwners() = runBlocking { roundTrip(1) }
    @Test fun backupWithMultipleOriginalPngAndJpegFiles() = runBlocking { roundTrip(4) }
    @Test fun oldV9BackupRestoresOverCurrentAttachments() = runBlocking {
        seed(2)
        val json = JSONObject(BackupJson.encode(BackupFixture.empty()).toString(Charsets.UTF_8)).apply {
            put("dataVersion", 9); for (personIndex in 0 until getJSONArray("persons").length()) { getJSONArray("persons").getJSONObject(personIndex).apply { remove("avatarZoom"); remove("avatarX"); remove("avatarY") } }; remove("technologies"); remove("nodeTechnologies"); remove("projectTechnologies"); remove("technologyIcons"); remove("attachmentFiles"); remove("nodeAttachments"); remove("projectAttachments")
        }.toString().toByteArray()
        val archive = ByteArrayOutputStream().also { BackupContainer.write(json, it) }.toByteArray()
        val data = repo.inspect(archive.inputStream())
        repo.restore(data)
        assertTrue(db.attachmentDao().files().isEmpty()); assertTrue(repo.snapshot().projects.isEmpty())
        assertTrue(File(context.filesDir, "attachments").listFiles().orEmpty().isEmpty())
    }
    @Test fun corruptTruncatedAndTrailingAttachmentBytesAreRejectedWithoutChanges() = runBlocking {
        val before = seed(2)
        val archive = repo.create().readBytes()
        listOf(archive.copyOf(archive.size - 1), archive + byteArrayOf(0), archive.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }).forEach {
            assertTrue(runCatching { repo.inspect(it.inputStream()) }.isFailure)
            assertEquals(before.attachmentFiles, db.attachmentDao().files())
            assertEquals(before.nodes, db.backupDao().nodes())
            assertTrue(File(context.cacheDir, "backups").listFiles().orEmpty().none { file -> file.name.startsWith("inspect-") })
        }
    }
    @Test fun missingAndModifiedAttachmentAbortExportWithoutSuccessArtifact() = runBlocking {
        val before = seed(1)
        val file = before.attachmentContents.values.single()
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })
        assertTrue(runCatching { repo.create() }.exceptionOrNull() is AttachmentBackupException)
        file.delete()
        assertTrue(runCatching { repo.create() }.exceptionOrNull() is AttachmentBackupException)
        assertEquals(before.attachmentFiles, db.attachmentDao().files())
        assertTrue(File(context.cacheDir, "backups").listFiles().orEmpty().isEmpty())
    }
    @Test fun databaseFailureAfterDeletionPreservesOldRowsAndFilesAndCleansOnlyNewFiles() = runBlocking {
        val before = seed(2)
        val candidate = repo.inspect(repo.create().inputStream())
        val oldBytes = before.attachmentContents.mapValues { it.value.readBytes() }
        val unrelated = File(context.filesDir, "attachments/${UUID.randomUUID()}.png").apply { writeBytes(BackupFixture.png()) }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore BEFORE INSERT ON attachment_files BEGIN SELECT RAISE(ABORT, 'failure'); END")
        assertTrue(runCatching { repo.restore(candidate) }.isFailure)
        assertEquals(before.attachmentFiles, db.attachmentDao().files()); assertEquals(before.nodes, db.backupDao().nodes())
        assertEquals(before.nodeAttachments, db.attachmentDao().nodeAttachments()); assertEquals(before.projectAttachments, db.attachmentDao().projectAttachments())
        oldBytes.forEach { (name, bytes) -> assertArrayEquals(bytes, File(context.filesDir, "attachments/$name").readBytes()) }
        assertTrue(unrelated.exists())
        assertEquals(oldBytes.size + 1, File(context.filesDir, "attachments").listFiles()!!.size)
        assertFalse(File(context.filesDir, "backup-attachment-restore-journal.json").exists())
    }
    @Test fun stagingDurabilityFailurePreservesExistingData() = runBlocking {
        val before = seed(1)
        val candidate = repo.inspect(repo.create().inputStream())
        val failing = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory), BackupAttachmentFiles(context) { directory ->
            if (directory.name == "attachments") throw IOException("Injected fsync failure")
            BackupFixture.syncDirectory(directory)
        })
        assertTrue(runCatching { failing.restore(candidate) }.isFailure)
        assertEquals(before.attachmentFiles, db.attachmentDao().files())
        assertTrue(before.attachmentContents.values.single().exists())
        assertEquals(1, File(context.filesDir, "attachments").listFiles()!!.size)
        repo.recover()
        assertFalse(File(context.filesDir, "backup-attachment-restore-journal.json").exists())
    }
    @Test fun inspectedFileModifiedBeforeRestoreFailsBeforeChangingExistingRows() = runBlocking {
        val before = seed(1)
        val candidate = repo.inspect(repo.create().inputStream())
        candidate.attachmentContents.values.single().writeBytes(byteArrayOf(1))
        assertTrue(runCatching { repo.restore(candidate) }.exceptionOrNull() is AttachmentBackupException)
        assertEquals(before.attachmentFiles, db.attachmentDao().files())
        assertEquals(before.nodes, db.backupDao().nodes())
        assertEquals(1, File(context.filesDir, "attachments").listFiles()!!.size)
        assertFalse(File(context.filesDir, "backup-attachment-restore-journal.json").exists())
    }
    @Test fun cancelledStagingRollsBackAndRemovesOnlyNewFiles() = runBlocking {
        val before = seed(1)
        val candidate = repo.inspect(repo.create().inputStream())
        var cancelled = false
        val cancelling = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory), BackupAttachmentFiles(context) { directory ->
            if (directory.name == "attachments" && !cancelled) {
                cancelled = true
                throw kotlinx.coroutines.CancellationException("Injected cancellation")
            }
            BackupFixture.syncDirectory(directory)
        })
        assertTrue(runCatching { cancelling.restore(candidate) }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertEquals(before.attachmentFiles, db.attachmentDao().files())
        assertTrue(before.attachmentContents.values.single().exists())
        assertEquals(1, File(context.filesDir, "attachments").listFiles()!!.size)
        assertFalse(File(context.filesDir, "backup-attachment-restore-journal.json").exists())
    }
    @Test fun crashRecoveryUsesCommittedStorageReferencesAndPreservesUnrelatedFiles() = runBlocking {
        val before = seed(1)
        val store = BackupAttachmentFiles(context, BackupFixture::syncDirectory)
        val orphan = "${UUID.randomUUID()}.png"
        val unrelated = "${UUID.randomUUID()}.png"
        store.file(orphan).writeBytes(BackupFixture.png()); store.file(unrelated).writeBytes(BackupFixture.png())
        store.record(setOf(before.attachmentFiles.single().storageName, orphan))
        repo.recover()
        assertFalse(store.file(orphan).exists()); assertTrue(store.file(unrelated).exists())
        assertTrue(before.attachmentContents.values.single().exists())
    }
    @Test fun brokenAssociationsAndUnsafePathsAreRejectedBeforeRestore() = runBlocking {
        val before = seed(1)
        listOf(before.copy(nodeAttachments = emptyList()), before.copy(attachmentFiles = before.attachmentFiles.map { it.copy(storageName = "../outside.png") }), before.copy(projectAttachments = listOf(ProjectAttachmentEntity("absent", before.attachmentFiles.single().id)))).forEach {
            assertTrue(runCatching { repo.restore(it) }.isFailure)
            assertEquals(before.attachmentFiles, db.attachmentDao().files())
        }
    }
}
