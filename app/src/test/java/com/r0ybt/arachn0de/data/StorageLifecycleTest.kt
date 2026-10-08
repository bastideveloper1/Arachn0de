package com.r0ybt.arachn0de.data

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException
import java.util.UUID

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class StorageLifecycleTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: Arachn0deDatabase
    private lateinit var store: AvatarStore
    private lateinit var people: PersonRepository
    private lateinit var icons: TechnologyIconStore
    private lateinit var technologies: TechnologyRepository
    @Before fun setup() {
        context.deleteDatabase("arachn0de.db")
        listOf("avatars", "technology-icons", "attachments", "image-lifecycle", "backup-restore-journal.json", "backup-restore-journal.part", "technology-icon-journal.json", "technology-icon-journal.part", "backup-attachment-restore-journal.json").forEach { File(context.filesDir, it).deleteRecursively() }
        File(context.cacheDir, "backups").deleteRecursively()
        db = Arachn0deDatabase.create(context)
        store = AvatarStore(context, syncDirectory = BackupFixture::syncDirectory); people = PersonRepository(db, store)
        icons = TechnologyIconStore(context, BackupFixture::syncDirectory); technologies = TechnologyRepository(db, icons)
        PrivateImageCache.clear()
    }
    @After fun close() { technologies.close(); db.close(); PrivateImageCache.clear() }
    @Test fun cleanupRejectsLinkedAttachmentDirectoryAndPreservesExternalFiles() {
        val outside = File(context.cacheDir, "external-attachments").apply { mkdirs() }
        val image = File(outside, "${UUID.randomUUID()}.png").apply { writeBytes(BackupFixture.png()) }
        val link = File(context.filesDir, "attachments")
        java.nio.file.Files.createSymbolicLink(link.toPath(), outside.toPath())
        try {
            val attachments = AttachmentStore(context, BackupFixture::syncDirectory)
            assertTrue(runCatching { attachments.recover(emptyList()) }.isFailure)
            assertTrue(image.exists())
        } finally { java.nio.file.Files.delete(link.toPath()); outside.deleteRecursively() }
    }
    private fun source(): Uri {
        val input = File(context.cacheDir, "storage-source.png")
        input.writeBytes(BackupFixture.png()); return Uri.fromFile(input)
    }
    private fun avatar(name: String) = File(context.filesDir, "avatars/$name")
    @Test fun sharedAvatarSurvivesReplacementAndDeletesOnlyAfterLastReference() = runBlocking {
        val old = people.importAvatar(source()); people.save("a", "A", old); people.save("b", "B", old)
        val replacement = people.importAvatar(source()); people.save("a", "A", replacement, false)
        assertTrue(avatar(old).exists()); assertTrue(avatar(replacement).exists())
        people.delete("b"); assertFalse(avatar(old).exists()); assertTrue(avatar(replacement).exists())
        people.delete("a"); assertFalse(avatar(replacement).exists())
    }
    @Test fun failedAvatarDatabaseUpdatePreservesPreviousAndRetainsEditableImportUntilCancel() = runBlocking {
        val old = people.importAvatar(source()); people.save("a", "Before", old)
        val replacement = people.importAvatar(source())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_avatar_update BEFORE UPDATE ON persons BEGIN SELECT RAISE(ABORT, 'injected'); END")
        assertTrue(runCatching { people.save("a", "After", replacement, false) }.isFailure)
        assertEquals(old, db.personDao().get("a")!!.avatarFile); assertTrue(avatar(old).exists()); assertTrue(avatar(replacement).exists())
        people.discardAvatar(replacement); assertFalse(avatar(replacement).exists())
    }
    @Test fun twoPendingEditorsProtectImageUntilBothReservationsAreReleased() = runBlocking {
        val name = people.importAvatar(source())
        people.retainAvatar(name, "editor-a"); people.retainAvatar(name, "editor-b")
        people.discardAvatar(name, "editor-a"); assertTrue(avatar(name).exists())
        people.cleanup(); assertTrue(avatar(name).exists())
        people.discardAvatar(name, "editor-b"); assertFalse(avatar(name).exists())
    }
    @Test fun committedDeletionAndFileFailureAreRecoveredAfterReopeningDatabase() = runBlocking {
        val name = people.importAvatar(source()); people.save("a", "A", name)
        var failing = true
        val failingStore = AvatarStore(context, syncDirectory = { directory ->
            if (failing && directory.name == "image-lifecycle") throw IOException("Injected durable queue failure")
            BackupFixture.syncDirectory(directory)
        })
        val failingPeople = PersonRepository(db, failingStore)
        assertTrue(failingPeople.delete("a")); assertNull(db.personDao().get("a")); assertTrue(avatar(name).exists())
        assertTrue(File(context.filesDir, "backup-restore-journal.json").exists())
        failing = false; technologies.close(); db.close()
        db = Arachn0deDatabase.create(context); people = PersonRepository(db, AvatarStore(context, syncDirectory = BackupFixture::syncDirectory))
        technologies = TechnologyRepository(db, TechnologyIconStore(context, BackupFixture::syncDirectory))
        people.cleanup(); assertFalse(avatar(name).exists()); assertFalse(File(context.filesDir, "backup-restore-journal.json").exists())
    }
    @Test fun orphanSweepUsesGraceAndPreservesReferencesLeasesJournalsAndUnknownFiles() = runBlocking {
        val root = File(context.filesDir, "avatars").apply { mkdirs() }
        val orphan = "${UUID.randomUUID()}.png"
        val shared = "${UUID.randomUUID()}.png"
        val reserved = "${UUID.randomUUID()}.png"
        val staged = "${UUID.randomUUID()}.png"
        listOf(orphan, shared, reserved, staged).forEach { File(root, it).apply { writeBytes(BackupFixture.png()); setLastModified(1) } }
        val unknown = File(root, "do-not-touch.txt").apply { writeText("user") }
        val nested = File(root, "unknown-folder").apply { mkdirs() }; File(nested, orphan).writeBytes(BackupFixture.png())
        var now = ImageFileLifecycle.ORPHAN_GRACE_MS * 3
        val lifecycle = ImageFileLifecycle(context, "avatars", BackupFixture::syncDirectory) { now }
        lifecycle.reserve(reserved)
        lifecycle.cleanup(setOf(shared), setOf(staged), store::delete)
        assertTrue(File(root, orphan).exists())
        now += ImageFileLifecycle.ORPHAN_GRACE_MS + 1
        lifecycle.cleanup(setOf(shared), setOf(staged), store::delete)
        assertFalse(File(root, orphan).exists()); listOf(shared, reserved, staged).forEach { assertTrue(File(root, it).exists()) }
        assertTrue(unknown.exists()); assertTrue(File(nested, orphan).exists())
    }
    @Test fun pendingIconAndAvatarSurviveReopenAndCancelledDraftRemovesOnlyUnreferencedFiles() = runBlocking {
        val icon = technologies.importIcon(source()); val image = people.importAvatar(source())
        technologies.close(); db.close()
        db = Arachn0deDatabase.create(context); technologies = TechnologyRepository(db, TechnologyIconStore(context, BackupFixture::syncDirectory)); people = PersonRepository(db, AvatarStore(context, syncDirectory = BackupFixture::syncDirectory))
        technologies.cleanup(); people.cleanup(); assertTrue(icons.file(icon).exists()); assertTrue(avatar(image).exists())
        technologies.discardIcon(icon); people.discardAvatar(image)
        assertFalse(icons.file(icon).exists()); assertFalse(avatar(image).exists())
    }
    @Test fun cancelledAndInvalidImportsLeaveNoPublishedOrTemporaryImages() {
        assertTrue(runCatching { store.import(source()) { throw CancellationException("Cancelled") } }.exceptionOrNull() is CancellationException)
        val invalid = File(context.cacheDir, "invalid-storage-image").apply { writeText("invalid") }
        assertTrue(runCatching { icons.import(Uri.fromFile(invalid)) }.isFailure)
        assertTrue(File(context.filesDir, "avatars").listFiles().orEmpty().isEmpty())
        assertTrue(File(context.filesDir, "technology-icons").listFiles().orEmpty().isEmpty())
        val leftover = File(File(context.filesDir, "avatars").apply { mkdirs() }, "import-abandoned.tmp").apply { writeText("interrupted") }
        store.cleanup(emptySet(), emptySet()); assertFalse(leftover.exists())
    }
    @Test fun cancellingAfterPublicationReleasesReservationAndDeletesOnlyNewFile() {
        val input = source(); var checks = 0
        assertTrue(runCatching { store.import(input) { if (++checks == 3) throw CancellationException("After publish") } }.isFailure)
        assertTrue(File(context.filesDir, "avatars").listFiles().orEmpty().isEmpty())
    }
    @Test fun privateThumbnailCacheReusesSharedImagesAndRemainsBoundedFor600Entries() {
        val root = File(context.filesDir, "avatars").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val first = File(root, "${UUID.randomUUID()}.png"); first.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val decoded = PrivateImageCache.read(first)!!
        assertSame(decoded, PrivateImageCache.read(first)); assertTrue(decoded.width <= 96 && decoded.height <= 96)
        val bytes = first.readBytes()
        repeat(600) { index ->
            val file = File(root, "${UUID.randomUUID()}.png").apply { writeBytes(bytes) }
            assertNotNull(PrivateImageCache.read(file)); assertTrue("cache $index", PrivateImageCache.sizeBytes() <= PrivateImageCache.MAX_BYTES)
        }
        PrivateImageCache.invalidate(first); assertFalse(decoded.isRecycled)
        PrivateImageCache.clear(); assertEquals(0, PrivateImageCache.sizeBytes()); bitmap.recycle()
    }
    @Test fun cleanupProtectsOldPendingBackupAndActiveInspectedCandidate() = runBlocking {
        val backup = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory))
        val pending = backup.create().apply { setLastModified(1) }
        val marker = File(context.cacheDir, "backups/${pending.name}.keep").apply { setLastModified(1) }
        val candidate = backup.inspect(pending.inputStream()); candidate.inspectionDirectory!!.setLastModified(1)
        val abandoned = File(context.cacheDir, "backups/${UUID.randomUUID()}.arachnode").apply { writeBytes(pending.readBytes()); setLastModified(1) }
        val unknown = File(context.cacheDir, "backups/other-cache.dat").apply { writeText("retain"); setLastModified(1) }
        backup.recover(); assertTrue(pending.exists()); assertTrue(marker.exists()); assertTrue(candidate.inspectionDirectory.exists())
        assertFalse(abandoned.exists()); assertTrue(unknown.exists())
        backup.discard(candidate); assertFalse(candidate.inspectionDirectory.exists())
        backup.discardPending(pending.name); assertFalse(pending.exists()); assertFalse(marker.exists())
    }
}
