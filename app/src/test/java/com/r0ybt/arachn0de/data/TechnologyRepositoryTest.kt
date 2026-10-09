package com.r0ybt.arachn0de.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.TechnologyRepository
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
class TechnologyRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: Arachn0deDatabase
    private lateinit var repository: TechnologyRepository
    @Before fun setup() = runBlocking {
        context.deleteDatabase("arachn0de.db")
        listOf("technology-icons", "technology-icon-journal.json", "technology-icon-journal.part").forEach { File(context.filesDir, it).deleteRecursively() }
        db = Arachn0deDatabase.create(context)
        repository = TechnologyRepository(db, TechnologyIconStore(context, BackupFixture::syncDirectory))
        db.projectDao().insert(ProjectEntity("p", "Project", "", 0, 1, 2))
        db.nodeDao().insert(NodeEntity("layer", "p", null, "Layer", "", false, 0, 1, 2, purpose = "LAYER"))
        db.nodeDao().insert(NodeEntity("sub", "p", "layer", "Sub", "", false, 0, 1, 2, purpose = "LAYER"))
        db.nodeDao().insert(NodeEntity("task", "p", "sub", "Task", "", false, 0, 1, 2))
        db.personDao().insert(PersonEntity("person", "Person", null)); db.personDao().assign(listOf(NodePersonEntity("task", "person")))
    }
    @After fun close() { repository.close(); db.close() }
    private suspend fun icon(color: Int = android.graphics.Color.MAGENTA): String {
        val input = File(context.cacheDir, "technology-source.png")
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        return repository.importIcon(Uri.fromFile(input))
    }
    private fun file(name: String) = File(context.filesDir, "technology-icons/$name")
    @Test fun manualAssignmentOrderSurvivesReopenRemovalAndDoesNotReorderCatalogOrPeople() = runBlocking {
        repository.save("a","Alpha",null,true);repository.save("b","Beta",null,true);repository.save("c","Gamma",null,true)
        assertTrue(repository.assign("task",false,linkedSetOf("c","a","b")))
        assertEquals(listOf("c","a","b"),repository.state.first {it.loaded && it.forOwner("task",false).size==3}.forOwner("task",false).map {it.id})
        repository.close();db.close();db=Arachn0deDatabase.create(context);repository=TechnologyRepository(db,TechnologyIconStore(context,BackupFixture::syncDirectory))
        assertEquals(listOf("c","a","b"),db.technologyDao().nodes().map {it.technologyId})
        repository.assign("task",false,linkedSetOf("c","b"))
        assertEquals(listOf("c","b"),db.technologyDao().nodes().map {it.technologyId});assertEquals(listOf("a","b","c"),db.technologyDao().observeCatalog().first().map {it.id})
        assertEquals(listOf("person"),db.personDao().assignmentIds("task"))
    }
    @Test fun createEditAndDeleteKeepOwnersAndPeopleWhileCascadingTechnologyLinks() = runBlocking {
        val name = icon()
        assertTrue(repository.save("k", " Kotlin personalizada ", name, true))
        assertEquals("Kotlin personalizada", db.technologyDao().get("k")!!.name)
        repository.assign("p", true, setOf("k"))
        listOf("layer", "sub", "task").forEach { assertTrue(repository.assign(it, false, setOf("k"))) }
        assertTrue(repository.save("k", "Kotlin actualizada", name, false))
        assertEquals("Kotlin actualizada", repository.state.first { it.loaded && it.catalog.single().name == "Kotlin actualizada" }.forOwner("task", false).single().name)
        assertTrue(repository.delete("k"))
        assertTrue(db.technologyDao().nodes().isEmpty()); assertTrue(db.technologyDao().projects().isEmpty())
        assertEquals(3, db.backupDao().nodes().size); assertEquals(1, db.backupDao().projects().size)
        assertEquals(listOf(NodePersonEntity("task", "person")), db.backupDao().assignments())
        assertFalse(file(name).exists())
    }
    @Test fun multipleReferencesShareOneIconAndReplacementUpdatesEveryAppearance() = runBlocking {
        val old = icon()
        repository.save("a", "Python", old, true); repository.save("b", "Docker", null, true)
        repository.assign("p", true, setOf("a", "b"))
        listOf("layer", "sub", "task").forEach { repository.assign(it, false, setOf("a", "b")) }
        assertEquals(1, File(context.filesDir, "technology-icons").listFiles()!!.size)
        val replacement = icon(android.graphics.Color.BLUE)
        assertTrue(repository.save("a", "Python tool", replacement, false))
        val state = repository.state.first { it.loaded && it.nodeIds.size == 3 && it.catalog.any { row -> row.id == "a" && row.iconFile == replacement } }
        listOf("layer", "sub", "task").forEach { assertEquals(replacement, state.forOwner(it, false).single { row -> row.id == "a" }.iconFile) }
        assertEquals(replacement, state.forOwner("p", true).single { it.id == "a" }.iconFile)
        assertFalse(file(old).exists()); assertEquals(1, File(context.filesDir, "technology-icons").listFiles()!!.size)
    }
    @Test fun reopeningDatabasePreservesCatalogIconAndAllAssociations() = runBlocking {
        val name = icon(); repository.save("a", "Herramienta libre ñ", name, true)
        repository.assign("p", true, setOf("a")); repository.assign("sub", false, setOf("a")); repository.assign("task", false, setOf("a"))
        val bytes = file(name).readBytes()
        repository.close(); db.close()
        db = Arachn0deDatabase.create(context); repository = TechnologyRepository(db, TechnologyIconStore(context, BackupFixture::syncDirectory))
        assertEquals(TechnologyEntity("a", "Herramienta libre ñ", name), db.technologyDao().catalog().single())
        assertEquals(2, db.technologyDao().nodes().size); assertEquals(1, db.technologyDao().projects().size)
        assertArrayEquals(bytes, file(name).readBytes())
    }
    @Test fun invalidAssignmentsDoNotClearExistingReferences() = runBlocking {
        repository.save("a", "Custom", null, true); repository.assign("task", false, setOf("a"))
        assertFalse(repository.assign("task", false, setOf("missing"))); assertFalse(repository.assign("missing", false, setOf("a")))
        assertEquals(listOf(NodeTechnologyEntity("task", "a")), db.technologyDao().nodes())
        assertTrue(repository.assign("task", false, emptySet())); assertTrue(db.technologyDao().nodes().isEmpty())
    }
    @Test fun failedReplacementRollsBackAndKeepsOldIcon() = runBlocking {
        val old = icon(); repository.save("a", "Before", old, true)
        val replacement = icon(android.graphics.Color.BLUE)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_technology_update BEFORE UPDATE ON technologies BEGIN SELECT RAISE(ABORT, 'injected'); END")
        assertTrue(runCatching { repository.save("a", "After", replacement, false) }.isFailure)
        assertEquals(TechnologyEntity("a", "Before", old), db.technologyDao().get("a"))
        assertTrue(file(old).exists()); assertTrue(file(replacement).exists())
        repository.discardIcon(replacement); assertFalse(file(replacement).exists())
    }
    @Test fun iconsAreBoundedNormalizedPrivateAndDiscardDoesNotRemoveSharedIcons() = runBlocking {
        val name = icon(); val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file(name).path, bounds)
        assertTrue(bounds.outWidth <= 512 && bounds.outHeight <= 512); assertEquals("image/png", bounds.outMimeType)
        repository.save("a", "A", name, true); repository.save("b", "B", name, true)
        repository.discardIcon(name); repository.delete("a"); assertTrue(file(name).exists())
        repository.delete("b"); assertFalse(file(name).exists())
        val draft = icon(); repository.discardIcon(draft); assertFalse(file(draft).exists())
    }
    @Test fun technologyAssignedAfterCreationPreventsUndoFromDeletingNewWork() = runBlocking {
        val nodes = com.r0ybt.arachn0de.data.repository.NodeRepository(db)
        val token = nodes.captureCreation(listOf("task"))
        repository.save("a", "Tool", null, true); repository.assign("task", false, setOf("a"))
        assertFalse(nodes.undoCreation(token)); assertNotNull(db.nodeDao().getById("task"))
        assertEquals(listOf(NodeTechnologyEntity("task", "a")), db.technologyDao().nodes())
    }
    @Test fun staleEditorAndMissingIconCannotCreateBrokenRows() = runBlocking {
        assertFalse(repository.save("missing", "Edit", null, false))
        assertTrue(runCatching { repository.save("a", "", null, true) }.isFailure)
        assertTrue(runCatching { repository.save("a", "Valid", "00000000-0000-0000-0000-000000000001.png", true) }.isFailure)
        assertTrue(db.technologyDao().catalog().isEmpty())
    }
}
