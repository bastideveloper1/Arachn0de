package com.r0ybt.arachn0de.data

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
class ProjectPhotoRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: Arachn0deDatabase
    private lateinit var store: ProjectPhotoStore
    private lateinit var photos: ProjectPhotoRepository
    private lateinit var projects: ProjectRepository
    private lateinit var project: Project
    private fun file(name: String) = File(context.filesDir, "project-photos/$name")
    @Before fun setup() = runBlocking {
        context.deleteDatabase("arachn0de.db")
        listOf("project-photos", "project-photo-journal.json", "project-photo-journal.part", "image-lifecycle/project-photos").forEach { File(context.filesDir, it).deleteRecursively() }
        db = Arachn0deDatabase.create(context); store = ProjectPhotoStore(context, BackupFixture::syncDirectory)
        photos = ProjectPhotoRepository(db, store); projects = ProjectRepository(db.projectDao(), db)
        project = projects.createProject("Photo project")
    }
    @After fun close() { db.close() }
    private suspend fun import(owner: String = "editor"): String {
        val source = File(context.cacheDir, "project-source.png").apply { writeBytes(BackupFixture.png()) }
        return photos.import(Uri.fromFile(source), owner)
    }
    @Test fun saveReopenAndContentEditsPreservePrivatePhotoAndFraming() = runBlocking {
        assertNull(projects.getProject(project.id)!!.photo)
        val name = import(); val framing = AvatarFraming(2.5f, -.3f, .7f)
        assertTrue(photos.save(project.id, name, framing, "editor"))
        val saved = projects.observeProjects().first().single().photo!!
        assertEquals(name, saved.file); assertEquals(framing, saved.framing)
        projects.updateProject(project.id, "Renamed", "Changed")
        db.close(); db = Arachn0deDatabase.create(context); projects = ProjectRepository(db.projectDao(), db)
        assertEquals(saved, projects.getProject(project.id)!!.photo)
        assertTrue(file(name).exists())
    }
    @Test fun replacementRemovalAndSharedReferencesDeleteOnlyTheLastUnreservedFile() = runBlocking {
        val old = import(); photos.save(project.id, old, AvatarFraming(), "editor")
        val other = projects.createProject("Shared"); photos.save(other.id, old, AvatarFraming(3f, 0f, 0f), "other")
        val new = import(); photos.save(project.id, new, AvatarFraming(), "editor")
        assertTrue(file(old).exists()); assertEquals(old, projects.getProject(other.id)!!.photo!!.file)
        photos.retain(old, "recoverable-editor")
        photos.save(other.id, null, AvatarFraming(), "other")
        photos.cleanup(); assertTrue(file(old).exists())
        photos.discard(setOf(old), "recoverable-editor"); assertFalse(file(old).exists())
        photos.save(project.id, null, AvatarFraming(), "editor")
        assertFalse(file(new).exists()); assertNull(projects.getProject(project.id)!!.photo)
    }
    @Test fun cancelDropsImportedFilesAndReleasesOnlyItsOwnEditorReservation() = runBlocking {
        val name = import(); photos.retain(name, "second")
        photos.discard(setOf(name), "editor"); assertTrue(file(name).exists())
        photos.discard(setOf(name), "second"); assertFalse(file(name).exists())
        assertNull(projects.getProject(project.id)!!.photo)
    }
    @Test fun failedSaveKeepsPreviousPhotoAndImportedDraftForRetry() = runBlocking {
        val old = import(); photos.save(project.id, old, AvatarFraming(), "editor")
        val next = import("retry")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER photo_fail BEFORE UPDATE ON project_photos BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertTrue(runCatching { photos.save(project.id, next, AvatarFraming(2f, 0f, 0f), "retry") }.isFailure)
        assertEquals(old, projects.getProject(project.id)!!.photo!!.file)
        photos.cleanup(); assertTrue(file(old).exists()); assertTrue(file(next).exists())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER photo_fail")
        assertTrue(photos.save(project.id, next, AvatarFraming(2f, 0f, 0f), "retry"))
        assertFalse(file(old).exists()); assertTrue(file(next).exists())
    }
    @Test fun deletingOwnerLeavesDurableCleanupAndRestartRespectsReservations() = runBlocking {
        val name = import(); photos.save(project.id, name, AvatarFraming(), "editor")
        photos.retain(name, "pending-editor")
        projects.deleteProject(project.id)
        assertEquals(1, db.projectPhotoDao().all().size)
        db.close(); db = Arachn0deDatabase.create(context); photos = ProjectPhotoRepository(db, store)
        photos.cleanup(); assertTrue(file(name).exists()); assertTrue(db.projectPhotoDao().all().isEmpty())
        photos.discard(setOf(name), "pending-editor"); assertFalse(file(name).exists())
    }
    @Test fun journalAndPostCommitDeletionFailuresAreRetriedWithoutLosingConfirmedData() = runBlocking {
        val old = import(); photos.save(project.id, old, AvatarFraming(), "editor")
        val next = import("next")
        var fail = false
        val fragileStore = ProjectPhotoStore(context) { directory ->
            if (fail && directory.name == "project-photos") throw java.io.IOException("injected fsync failure")
            BackupFixture.syncDirectory(directory)
        }
        fail = true
        val fragile = ProjectPhotoRepository(db, fragileStore)
        assertTrue(fragile.save(project.id, next, AvatarFraming(), "next"))
        assertEquals(next, projects.getProject(project.id)!!.photo!!.file)
        fail = false; fragile.cleanup(); assertFalse(file(old).exists()); assertTrue(file(next).exists())
        val orphan = import("interrupted"); photos.discard(setOf(orphan), "interrupted")
        store.durable.record(setOf(next, orphan)); photos.cleanup()
        assertTrue(file(next).exists()); assertFalse(store.durable.hasJournal())
    }
    @Test fun existingMovePreservesHiddenPhotoAndDeletingLayerCleansIt() = runBlocking {
        val name = import(); photos.save(project.id, name, AvatarFraming(2f, .5f, 0f), "editor")
        val target = projects.createProject("Target")
        val layer = projects.moveInside(project.id, target.id, null)
        val hidden = db.projectPhotoDao().forNodes(listOf(layer)).single()
        assertNull(hidden.projectId); assertEquals(name, hidden.file); assertTrue(file(name).exists())
        assertNull(projects.getProject(target.id)!!.photo)
        NodeRepository(db).deleteNode(layer); photos.cleanup(); assertFalse(file(name).exists())
    }
    @Test fun hundredsOfSharedProjectIdentitiesUseBoundedPrivateCache() = runBlocking {
        val name = import(); photos.save(project.id, name, AvatarFraming(), "editor")
        repeat(300) { index -> val p = projects.createProject("Project $index"); photos.save(p.id, name, AvatarFraming(), "editor") }
        repeat(300) { store.readThumbnail(name) }
        assertEquals(301, projects.observeProjects().first().count { it.photo?.file == name })
        assertTrue(PrivateImageCache.sizeBytes() <= PrivateImageCache.MAX_BYTES)
    }
    @Test fun normalizationKeepsFullWideImageAndChangingFrameDoesNotRewritePixels() = runBlocking {
        val bitmap = android.graphics.Bitmap.createBitmap(1024, 256, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        val source = File(context.cacheDir, "wide-photo.png")
        source.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val name = photos.import(Uri.fromFile(source), "wide")
        val decoded = android.graphics.BitmapFactory.decodeFile(file(name).path)
        assertEquals(512, decoded.width); assertEquals(128, decoded.height); decoded.recycle()
        val bytes = file(name).readBytes()
        photos.save(project.id, name, AvatarFraming(4f, .8f, -.2f), "wide")
        photos.save(project.id, name, AvatarFraming(1f, 0f, 0f), "wide")
        assertArrayEquals(bytes, file(name).readBytes())
    }

}
