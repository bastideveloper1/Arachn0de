package com.r0ybt.arachn0de.backup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ProjectPhotoBackupTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: Arachn0deDatabase
    private lateinit var repo: BackupRepository
    private val name = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.png"
    private fun file(n: String) = File(context.filesDir, "project-photos/$n")
    @Before fun setup() {
        context.deleteDatabase("arachn0de.db")
        listOf("project-photos", "avatars", "project-photo-journal.json", "image-lifecycle/project-photos").forEach { File(context.filesDir, it).deleteRecursively() }
        db = Arachn0deDatabase.create(context)
        repo = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory))
    }
    @After fun close() { db.close() }
    private suspend fun seed(): BackupData {
        val data = BackupFixture.complete().copy(projectPhotos = listOf(
            ProjectPhotoEntity("photo-p", "p", null, name, 2f, -.4f, .6f),
            ProjectPhotoEntity("photo-layer", null, "inner", name, 3f, .2f, -.7f)),
            projectPhotoImages = mapOf(name to BackupFixture.png()))
        data.validate()
        data.projects.forEach { db.projectDao().insert(it) }
        data.validate().forEach { db.nodeDao().insert(it) }
        data.projectPhotos.forEach { db.projectPhotoDao().save(it) }
        file(name).apply { parentFile!!.mkdirs(); writeBytes(BackupFixture.png()) }
        return repo.snapshot()
    }
    @Test fun portableBackupRebuildsSharedPhotoBytesFramesAndHiddenLayerAfterDeletingOriginals() = runBlocking {
        val before = seed(); val archive = repo.create().readBytes()
        db.close(); context.deleteDatabase("arachn0de.db"); File(context.filesDir, "project-photos").deleteRecursively()
        db = Arachn0deDatabase.create(context); repo = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory))
        val candidate = repo.inspect(archive.inputStream()); repo.restore(candidate); repo.discard(candidate)
        val after = repo.snapshot()
        assertEquals(1, after.projectPhotoImages.size)
        before.projectPhotos.forEach { original ->
            val restored = after.projectPhotos.single { it.id == original.id }
            assertEquals(original.copy(file = restored.file), restored)
            assertNotEquals(name, restored.file)
            assertArrayEquals(before.projectPhotoImages.getValue(name), file(restored.file).readBytes())
        }
        assertEquals(1, after.projectPhotos.map { it.file }.distinct().size)
    }
    @Test fun genuineV12HasNoPhotosAndRestoresHistoricalDefaults() = runBlocking {
        seed()
        val root = JSONObject(String(BackupJson.encode(BackupFixture.empty()))).apply {
            put("dataVersion", 12); remove("privatePreferences");remove("storeId");remove("storeKind"); BackupFixture.removeSprintFields(this); remove("metroPreferences"); remove("metroJourneys"); remove("gameSession"); remove("conversionRoots"); remove("conversionPeople"); remove("conversionTags"); remove("conversionEvents"); remove("conversionWorkStates"); remove("nodeSortPreferences"); remove("imageFiles"); remove("projectPhotos"); remove("projectPhotoImages")
        }
        val decoded = BackupJson.decode(root.toString().toByteArray())
        assertTrue(decoded.projectPhotos.isEmpty()); repo.restore(decoded)
        assertTrue(db.projectPhotoDao().all().isEmpty()); assertFalse(file(name).exists())
    }
    @Test fun invalidBytesPathsOwnersOrFramesFailBeforeChangingConfirmedData() = runBlocking {
        val before = seed(); val photo = before.projectPhotos.first()
        val variants = listOf(before.copy(projectPhotoImages = emptyMap()),
            before.copy(projectPhotoImages = mapOf(name to byteArrayOf(1, 2))),
            before.copy(projectPhotos = listOf(photo.copy(file = "/device/photo.png"))),
            before.copy(projectPhotos = listOf(photo.copy(projectId = "missing"))),
            before.copy(projectPhotos = listOf(photo.copy(nodeId = "inner"))),
            before.copy(projectPhotos = listOf(photo.copy(photoZoom = Float.NaN))),
            before.copy(projectPhotos = listOf(photo.copy(photoX = 2f))))
        variants.forEach { assertTrue(runCatching { repo.restore(it) }.isFailure) }
        assertEquals(before.projectPhotos, repo.snapshot().projectPhotos); assertTrue(file(name).exists())
    }
    @Test fun rollbackAfterDeletingTablesKeepsPreviousPhotoAndCleansOnlyStagedCopies() = runBlocking {
        val before = seed()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_photo_restore BEFORE INSERT ON project_photos BEGIN SELECT RAISE(ABORT, 'injected'); END")
        assertTrue(runCatching { repo.restore(before) }.isFailure)
        assertEquals(before.projectPhotos, repo.snapshot().projectPhotos)
        assertEquals(listOf(name), File(context.filesDir, "project-photos").listFiles()!!.map { it.name })
    }
    @Test fun stagingDurabilityFailureAndJournalRecoveryPreserveOriginalAndUnrelatedFiles() = runBlocking {
        val before = seed()
        val failing = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory),
            projectPhotos = BackupAvatarFiles(context, "project-photos", "project-photo-journal") { directory ->
                if (directory.name == "project-photos" && directory.listFiles().orEmpty().size > 1) throw java.io.IOException("injected")
                BackupFixture.syncDirectory(directory)
            })
        assertTrue(runCatching { failing.restore(before) }.isFailure)
        repo.recover(); assertEquals(before.projectPhotos, repo.snapshot().projectPhotos); assertTrue(file(name).exists())
        val orphan = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.png"
        val unrelated = "cccccccc-cccc-cccc-cccc-cccccccccccc.png"
        val files = BackupAvatarFiles(context, "project-photos", "project-photo-journal", BackupFixture::syncDirectory)
        files.write(orphan, BackupFixture.png()); files.write(unrelated, BackupFixture.png()); files.record(setOf(name, orphan))
        repo.recover(); assertTrue(file(name).exists()); assertFalse(file(orphan).exists()); assertTrue(file(unrelated).exists())
    }
    @Test fun missingConfirmedPhotoPreventsSuccessfulExport() = runBlocking {
        seed(); file(name).delete(); assertTrue(runCatching { repo.create() }.isFailure)
        assertEquals(2, db.projectPhotoDao().confirmed().size)
    }
}
