package com.r0ybt.arachn0de.backup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.ConversionFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.ProjectNestingRepository
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
class ProjectConversionBackupTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: Arachn0deDatabase
    private lateinit var backup: BackupRepository
    @Before fun setup() = runBlocking<Unit> {
        context.deleteDatabase("arachn0de.db"); File(context.filesDir, "image-lifecycle").deleteRecursively()
        db = Arachn0deDatabase.create(context); backup = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory))
        backup.restore(ConversionFixture.complete())
    }
    @After fun close() { db.close() }
    private suspend fun logical() = String(BackupJson.encode(backup.snapshot().copy(createdAt = 0)))
    private fun reinstall() {
        db.close(); context.deleteDatabase("arachn0de.db")
        listOf("project-photos", "avatars", "technology-icons", "image-lifecycle").forEach { File(context.filesDir, it).deleteRecursively() }
        db = Arachn0deDatabase.create(context); backup = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory))
    }
    @Test fun originalAndConvertedArchivesRestoreLiveTreeHiddenPhotoAndPermanentProjectIdentity() = runBlocking<Unit> {
        val original = backup.create().readBytes()
        val originalCandidate = backup.inspect(original.inputStream()); assertTrue(originalCandidate.imageFiles.isNotEmpty()); backup.discard(originalCandidate)
        val layer = ProjectNestingRepository(db).move("p", "q", null)
        db.nodeDao().updateContent(layer, "Six months later", "Live text", 1000)
        db.nodeDao().insert(NodeEntity("new-current", "q", layer, "Added", "Live", false, 3, 4, 5))
        val archive = backup.create().readBytes(); reinstall()
        val candidate = backup.inspect(archive.inputStream()); backup.restore(candidate); backup.discard(candidate)
        assertNull(db.projectDao().getById("p")); assertEquals(layer, db.conversionDao().roots().single().nodeId)
        val photo = db.projectPhotoDao().forNodes(listOf(layer)).single()
        assertEquals(2.4f, photo.photoZoom); assertArrayEquals(BackupFixture.png(), File(context.filesDir, "project-photos/${photo.file}").readBytes())
        val project = ProjectNestingRepository(db).promote(layer)
        assertEquals("p", project); assertEquals("Six months later", db.projectDao().getById(project)!!.name)
        assertEquals(7, db.projectDao().getById(project)!!.position); assertEquals(project, db.nodeDao().getById("new-current")!!.projectId)
        assertEquals("tech", db.technologyDao().projects().single { it.projectId == project }.technologyId)
        assertEquals(photo.copy(projectId = project, nodeId = null), db.projectPhotoDao().forProject(project))
        backup.snapshot().validate()
    }
    @Test fun promotedSprintLayerBackupRetainsDormantPeopleTagsEventsAndRootWorkStates() = runBlocking<Unit> {
        val project = ProjectNestingRepository(db).promote("root")
        val archive = backup.create().readBytes(); reinstall()
        val candidate = backup.inspect(archive.inputStream()); backup.restore(candidate); backup.discard(candidate)
        assertEquals("DOING", db.conversionDao().workStates().single().workState)
        val layer = ProjectNestingRepository(db).move(project, "q", null)
        assertTrue(db.nodeDao().getById(layer)!!.sprintMode); assertEquals("DOING", db.nodeDao().getById("work")!!.workState)
        assertEquals("r", db.personDao().assignmentsForNodes(listOf(layer)).single().personId)
        assertEquals("tag", db.tagDao().tagsForNodes(listOf(layer)).single().tagId)
        assertEquals("root-event", db.nodeEventDao().forNode(layer).single().id); backup.snapshot().validate()
    }
    @Test fun genuineV13RestoresPhotosWithNoConversionMetadataOrSortOverrides() = runBlocking<Unit> {
        val root = JSONObject(String(BackupJson.encode(ConversionFixture.complete()))).apply {
            put("dataVersion", 13); BackupFixture.removeSprintFields(this); remove("metroPreferences"); remove("metroJourneys"); remove("gameSession")
            listOf("conversionRoots", "conversionPeople", "conversionTags", "conversionEvents", "conversionWorkStates", "nodeSortPreferences", "imageFiles").forEach { remove(it) }
        }
        val old = BackupJson.decode(root.toString().toByteArray())
        backup.restore(old)
        assertTrue(db.conversionDao().roots().isEmpty()); assertTrue(db.nodeSortPreferenceDao().all().isEmpty())
        assertNotNull(db.projectPhotoDao().forProject("p")); backup.snapshot().validate()
    }
    @Test fun brokenOwnersLinksFramesManifestsAndMissingImageBytesFailBeforeMutation() = runBlocking<Unit> {
        ProjectNestingRepository(db).promote("root")
        val before = logical(); val data = backup.snapshot(); val root = data.conversionRoots.single()
        listOf(data.copy(conversionRoots = listOf(root.copy(projectId = "missing"))),
            data.copy(conversionPeople = listOf(ConversionPersonEntity(root.id, "missing"))),
            data.copy(conversionWorkStates = listOf(ConversionWorkStateEntity("bill", root.id, "DOING"))),
            data.copy(conversionEvents = data.conversionEvents + ConversionEventEntity("bill-event", root.id, "CREATED", 1)),
            data.copy(nodeSortPreferences = listOf(NodeSortPreferenceEntity("missing:project-root", "MANUAL")))).forEach {
            assertTrue(runCatching { backup.restore(it) }.isFailure); assertEquals(before, logical())
        }
        val candidate = backup.inspect(backup.create().inputStream())
        assertTrue(runCatching { backup.restore(candidate.copy(imageContents = emptyMap())) }.isFailure); assertEquals(before, logical())
        val malformed = candidate.copy(imageFiles = candidate.imageFiles.map { it.copy(name = "../outside.png") })
        assertTrue(runCatching { malformed.validate() }.isFailure); backup.discard(candidate)
    }
    @Test fun corruptOrTruncatedStreamAndConversionInsertFailurePreservePreviousDatabaseAndFiles() = runBlocking<Unit> {
        ProjectNestingRepository(db).move("p", "q", null)
        val before = logical(); val file = backup.create(); val bytes = file.readBytes()
        val corrupted = bytes.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertTrue(runCatching { backup.inspect(corrupted.inputStream()) }.isFailure)
        assertTrue(runCatching { backup.inspect(bytes.copyOf(bytes.size - 1).inputStream()) }.isFailure)
        val candidate = backup.inspect(bytes.inputStream())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_conversion BEFORE INSERT ON conversion_roots BEGIN SELECT RAISE(ABORT, 'injected'); END")
        assertTrue(runCatching { backup.restore(candidate) }.isFailure); assertEquals(before, logical())
        assertEquals(1, File(context.filesDir, "project-photos").listFiles().orEmpty().size)
        backup.recover(); backup.discard(candidate)
    }
    @Test fun hundredsOfImagesBeyondEightMiBExportInspectAndRestoreWithBoundedManifestStreaming() = runBlocking<Unit> {
        backup.restore(BackupFixture.empty())
        val image = android.graphics.Bitmap.createBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888)
        val random = java.util.Random(42)
        image.setPixels(IntArray(128 * 128) { random.nextInt() or 0xff000000.toInt() }, 0, 128, 0, 0, 128, 128)
        val bytes = java.io.ByteArrayOutputStream().also { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray(); image.recycle()
        assertTrue(bytes.size * 600L > BackupLimits.TOTAL_AVATAR_BYTES)
        repeat(600) { index ->
            val name = java.util.UUID.nameUUIDFromBytes("image-$index".toByteArray()).toString() + ".png"
            val directory = if (index < 100) "avatars" else "technology-icons"
            File(context.filesDir, "$directory/$name").apply { parentFile!!.mkdirs(); writeBytes(bytes) }
            if (index < 100) db.personDao().insert(PersonEntity("person-$index", "Person $index", name))
            else db.technologyDao().insert(TechnologyEntity("tech-$index", "Tool $index", name))
        }
        val file = backup.create(); val candidate = backup.inspect(file.inputStream())
        assertEquals(600, candidate.imageFiles.size); assertEquals(600, candidate.imageContents.size)
        assertTrue(candidate.avatars.isEmpty()); assertTrue(candidate.technologyIcons.isEmpty())
        assertTrue(candidate.imageFiles.sumOf { it.byteSize } > 8L * 1024 * 1024)
        backup.restore(candidate)
        assertEquals(100, db.backupDao().persons().size); assertEquals(500, db.technologyDao().catalog().size)
        val sample = db.technologyDao().catalog().first().iconFile!!
        assertArrayEquals(bytes, File(context.filesDir, "technology-icons/$sample").readBytes()); backup.discard(candidate)
    }
}
