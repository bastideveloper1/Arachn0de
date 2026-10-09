package com.r0ybt.arachn0de.data

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.domain.model.AvatarFraming
import kotlinx.coroutines.flow.first
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
class AvatarFramingTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: Arachn0deDatabase
    private lateinit var people: PersonRepository
    private lateinit var store: AvatarStore
    @Before fun setup() {
        context.deleteDatabase("arachn0de.db")
        listOf("avatars", "image-lifecycle", "backup-restore-journal.json", "backup-restore-journal.part").forEach { File(context.filesDir, it).deleteRecursively() }
        db = Arachn0deDatabase.create(context); store = AvatarStore(context, syncDirectory = BackupFixture::syncDirectory); people = PersonRepository(db, store)
    }
    @After fun close() { db.close() }
    private fun file(name: String) = File(context.filesDir, "avatars/$name")
    private suspend fun photo(): String {
        val source = File(context.cacheDir, "framing.png").apply { writeBytes(BackupFixture.png()) }
        return people.importAvatar(Uri.fromFile(source))
    }
    @Test fun savedFramingReopensExactlyWithoutRecompressionOrNewPhotograph() = runBlocking {
        val name = photo(); val bytes = file(name).readBytes()
        people.save("a", "A", name, zoom = 2.5f, x = -.75f, y = .375f)
        db.close(); db = Arachn0deDatabase.create(context); people = PersonRepository(db, store)
        val person = people.observePeople().first().single()
        assertEquals(2.5f, person.avatarZoom); assertEquals(-.75f, person.avatarX); assertEquals(.375f, person.avatarY)
        people.save("a", "A", name, false, zoom = 1f, x = 0f, y = 0f)
        assertArrayEquals(bytes, file(name).readBytes()); assertEquals(name, db.personDao().get("a")!!.avatarFile)
    }
    @Test fun rollbackAndCancelledReplacementPreserveOriginalFramingAndBytes() = runBlocking {
        val old = photo(); people.save("a", "A", old, zoom = 2f, x = .4f, y = -.3f)
        val before = db.personDao().get("a")!!; val bytes = file(old).readBytes()
        val draft = photo(); people.retainAvatar(draft, "a")
        people.discardAvatar(draft, "a")
        assertEquals(before, db.personDao().get("a")); assertArrayEquals(bytes, file(old).readBytes()); assertFalse(file(draft).exists())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_framing BEFORE UPDATE ON persons BEGIN SELECT RAISE(ABORT, 'injected'); END")
        assertTrue(runCatching { people.save("a", "A", old, false, zoom = 3f) }.isFailure)
        assertEquals(before, db.personDao().get("a")); assertTrue(file(old).exists())
    }
    @Test fun replacementUsesExistingLastReferenceCleanup() = runBlocking {
        val old = photo(); people.save("a", "A", old); people.save("b", "B", old)
        val next = photo(); people.save("a", "A", next, false, zoom = 3f, x = -.2f)
        assertTrue(file(old).exists()); people.delete("b"); assertFalse(file(old).exists()); assertTrue(file(next).exists())
    }
    @Test fun backupRestoresCustomFramingAndBytesAfterDeletingOriginalPrivateFiles() = runBlocking {
        val old = photo(); people.save("a", "A", old, zoom = 2.25f, x = -.2f, y = .8f)
        val bytes = file(old).readBytes()
        val repo = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory))
        val archive = repo.create(); val inspected = archive.inputStream().use { repo.inspect(it) }
        people.delete("a"); assertFalse(file(old).exists())
        repo.restore(inspected); repo.discard(inspected)
        val row = db.personDao().get("a")!!
        assertEquals(2.25f, row.avatarZoom); assertEquals(-.2f, row.avatarX); assertEquals(.8f, row.avatarY)
        assertArrayEquals(bytes, file(row.avatarFile!!).readBytes()); assertNotEquals(old, row.avatarFile)
        assertEquals(17, JSONObject(String(BackupJson.encode(repo.snapshot()))).getInt("dataVersion"))
    }
    @Test fun oldV11BackupDefaultsToCenteredCoverAndInvalidFramingIsRejected() {
        val root = JSONObject(String(BackupJson.encode(BackupFixture.complete())))
        BackupFixture.removeSprintFields(root); root.remove("metroPreferences"); root.remove("metroJourneys"); root.put("dataVersion", 11); root.remove("gameSession"); root.remove("conversionRoots"); root.remove("conversionPeople"); root.remove("conversionTags"); root.remove("conversionEvents"); root.remove("conversionWorkStates"); root.remove("nodeSortPreferences"); root.remove("imageFiles"); root.remove("projectPhotos"); root.remove("projectPhotoImages")
        val rows = root.getJSONArray("persons")
        for (i in 0 until rows.length()) rows.getJSONObject(i).apply { remove("avatarZoom"); remove("avatarX"); remove("avatarY") }
        val old = BackupJson.decode(root.toString().toByteArray())
        assertTrue(old.persons.all { it.avatarZoom == 1f && it.avatarX == 0f && it.avatarY == 0f })
        val data = old.copy(persons = old.persons.map { it.copy(avatarZoom = Float.NaN) })
        assertTrue(runCatching { data.validate() }.isFailure)
        val encoded = JSONObject(String(BackupJson.encode(old)))
        encoded.getJSONArray("persons").getJSONObject(0).put("avatarZoom", "2")
        assertTrue(runCatching { BackupJson.decode(encoded.toString().toByteArray()) }.isFailure)
    }
    @Test fun hundredPeoplePersistSharedFramingInAssignmentsAndStayWithinThumbnailCache() = runBlocking {
        val data = BackupFixture.complete()
        data.projects.forEach { db.projectDao().insert(it) }; data.validate().forEach { db.nodeDao().insert(it) }
        repeat(100) { index ->
            val name = photo(); people.save("person-$index", "Persona $index", name, zoom = 1f + index / 25f, x = .5f, y = -.5f)
            db.personDao().assign(listOf(NodePersonEntity("task", "person-$index")))
            store.readThumbnail(name); assertTrue(PrivateImageCache.sizeBytes() <= PrivateImageCache.MAX_BYTES)
        }
        assertEquals(100, people.observePeople().first().size)
        val assigned = people.observeAssignments("q").first().getValue("task")
        assertEquals(100, assigned.size)
        assertTrue(assigned.all { it.avatarX == .5f && it.avatarY == -.5f })
        assertEquals(assigned.toSet(), people.observeAllAssignments().first().getValue("task").toSet())
        val repo = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory))
        assertEquals(100, repo.snapshot().persons.size)
    }
    @Test fun gestureStartsWithoutJumpAndClampsToCoveredFrameAtEveryScale() {
        val original = AvatarFraming(2f, .25f, -.5f)
        val stationary = original.transform(400f, 200f, 240f, 0f, 0f, 1f)
        assertEquals(original.zoom, stationary.zoom); assertEquals(original.x, stationary.x, .00001f); assertEquals(original.y, stationary.y, .00001f)
        val dragged = original.transform(400f, 200f, 240f, 7f, -9f, 1f)
        val before = original.geometry(400f, 200f, 240f); val after = dragged.geometry(400f, 200f, 240f)
        assertEquals(before.left + 7f, after.left, .0001f); assertEquals(before.top - 9f, after.top, .0001f)
        val edge = dragged.transform(400f, 200f, 240f, 10000f, -10000f, 99f); edge.validate()
        val g = edge.geometry(400f, 200f, 240f)
        assertTrue(g.left <= 0f && g.top <= 0f && g.left + g.width >= 240f && g.top + g.height >= 240f)
    }
    @Test fun pinchKeepsImagePointUnderCentroidAndGeometryIsDisplayIndependent() {
        val old = AvatarFraming(2f, .1f, .1f); val before = old.geometry(300f, 400f, 240f)
        val next = old.transform(300f, 400f, 240f, 0f, 0f, 1.1f, 80f, 100f); val after = next.geometry(300f, 400f, 240f)
        assertEquals((80f - before.left) / before.width, (80f - after.left) / after.width, .00001f)
        assertEquals((100f - before.top) / before.height, (100f - after.top) / after.height, .00001f)
        val small = next.geometry(300f, 400f, 28f)
        assertEquals(after.left / 240f, small.left / 28f, .00001f); assertEquals(after.top / 240f, small.top / 28f, .00001f)
    }
}
