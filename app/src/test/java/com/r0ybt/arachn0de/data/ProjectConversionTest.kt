package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import kotlinx.coroutines.runBlocking
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
class ProjectConversionTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: Arachn0deDatabase
    private lateinit var backup: BackupRepository
    private lateinit var conversion: ProjectNestingRepository
    @Before fun setup() = runBlocking<Unit> {
        context.deleteDatabase("arachn0de.db"); File(context.filesDir, "image-lifecycle").deleteRecursively()
        db = Arachn0deDatabase.create(context); backup = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory))
        conversion = ProjectNestingRepository(db); backup.restore(ConversionFixture.complete())
    }
    @After fun close() { db.close() }
    private suspend fun logical() = String(BackupJson.encode(backup.snapshot().copy(createdAt = 0)))
    private suspend fun valid() { backup.snapshot().validate(); db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) } }
    @Test fun layerPromotionPreservesLiveDescendantsAndDormantRootPropertiesRelationsAndSprint() = runBlocking {
        val before = db.nodeDao().getProjectNodes("p").associateBy { it.id }
        val project = conversion.promote("root")
        assertEquals("root", project); assertEquals(before.getValue("root").title, db.projectDao().getById(project)!!.name)
        assertNull(db.nodeDao().getById("root")); assertEquals("p", db.nodeDao().getById("outside")!!.projectId)
        val root = db.conversionDao().project(project)!!
        assertEquals(100L, root.startAt); assertEquals(200L, root.dueAt); assertEquals("HIGH", root.priority); assertTrue(root.sprintMode)
        assertEquals(listOf(ConversionPersonEntity(root.id, "r")), db.conversionDao().people())
        assertEquals(listOf(ConversionTagEntity(root.id, "tag")), db.conversionDao().tags())
        assertEquals("root-event", db.conversionDao().events().single().id)
        assertEquals("DOING", db.conversionDao().workStates().single().workState)
        assertNull(db.nodeDao().getById("work")!!.workState); assertEquals("VALIDATED", db.nodeDao().getById("bill")!!.workState)
        assertEquals(before.getValue("bill").copy(projectId = project), db.nodeDao().getById("bill"))
        assertEquals("root-tech", db.technologyDao().projects().single { it.projectId == project }.technologyId)
        assertEquals("P:root", db.creationDefaultsDao().forProject(project).single { it.projectId == project }.id)
        assertEquals(project, db.recurrenceDao().get("rule")!!.projectId); assertNull(db.recurrenceDao().get("rule")!!.parentId)
        assertEquals("PRIORITY", db.nodeSortPreferenceDao().all().single { it.context == "$project:project-root" }.mode)
        assertEquals(9, db.backupDao().nodes().size + db.conversionDao().roots().size)
        valid()
    }
    @Test fun projectDemotionPreservesIdentityAllAssociationsPhotoAndRelativeOrder() = runBlocking {
        val before = db.nodeDao().getProjectNodes("p").associateBy { it.id }
        val photo = db.projectPhotoDao().forProject("p")!!
        val id = conversion.move("p", "q", null)
        assertEquals("p", id); assertNull(db.projectDao().getById("p"))
        before.values.forEach { old -> assertEquals(old.copy(projectId = "q", parentId = old.parentId ?: id), db.nodeDao().getById(old.id)) }
        assertEquals(photo.copy(projectId = null, nodeId = id), db.projectPhotoDao().forNodes(listOf(id)).single())
        assertEquals("tech", db.technologyDao().forNodes(listOf(id)).single().technologyId)
        ProjectPhotoRepository(db, ProjectPhotoStore(context, BackupFixture::syncDirectory)).cleanup()
        assertTrue(File(context.filesDir, "project-photos/${photo.file}").exists()); valid()
    }
    @Test fun repeatedRoundTripsAfterEditsUseCurrentTreeAndRecoverOriginalPhotoAndProjectPosition() = runBlocking {
        val original = db.projectDao().getById("p")!!; val photo = db.projectPhotoDao().forProject("p")!!
        repeat(3) { cycle ->
            val layer = conversion.move("p", "q", null)
            db.nodeDao().updateContent(layer, "Updated $cycle", "Months later $cycle", 10_000L + cycle)
            db.nodeDao().insert(NodeEntity("added-$cycle", "q", layer, "New $cycle", "Current", false, cycle, 3, 4))
            val project = conversion.promote(layer)
            assertEquals("p", project); assertEquals(original.position, db.projectDao().getById(project)!!.position)
            assertEquals("Updated $cycle", db.projectDao().getById(project)!!.name)
            assertEquals(photo, db.projectPhotoDao().forProject(project))
            assertEquals((0..cycle).map { "added-$it" }.toSet(), db.nodeDao().getProjectNodes(project).filter { it.id.startsWith("added-") }.map { it.id }.toSet())
            valid()
        }
    }
    @Test fun nativeLayerRoundTripRestoresRootRelationsAndSprintWithoutUndoAndRespectsEdits() = runBlocking {
        val id = conversion.promote("root")
        db.projectDao().update(id, "Live title", "Live description", 999)
        db.nodeDao().setCompleted("work", true, 999)
        val layer = conversion.move(id, "q", null)
        val row = db.nodeDao().getById(layer)!!
        assertEquals("root", layer); assertEquals("Live title", row.title); assertEquals(100L, row.startAt); assertTrue(row.sprintMode)
        assertEquals("DONE", db.nodeDao().getById("work")!!.workState)
        assertEquals(listOf(NodePersonEntity(layer, "r")), db.personDao().assignmentsForNodes(listOf(layer)))
        assertEquals(listOf(NodeTagEntity(layer, "tag")), db.tagDao().tagsForNodes(listOf(layer)))
        assertEquals("root-event", db.nodeEventDao().forNode(layer).single().id)
        assertTrue(db.conversionDao().people().isEmpty()); assertTrue(db.conversionDao().workStates().isEmpty()); valid()
    }
    @Test fun selfDestinationsForeignLayersAndNonLayersFailWithoutMutatingAnything() = runBlocking {
        val before = logical()
        listOf<suspend () -> Unit>({ conversion.move("p", "p", "root") }, { conversion.move("p", "q", "root") },
            { conversion.move("p", "q", "task") }, { conversion.promote("bill") }, { conversion.promote("missing") }).forEach {
            assertTrue(runCatching { it() }.isFailure); assertEquals(before, logical())
        }
    }
    @Test fun failureBeforeCommitRollsBackBothDirectionsIncludingFilesAndAllRelations() = runBlocking {
        val before = logical(); val failing = ProjectNestingRepository(db) { throw java.io.IOException("injected") }
        assertTrue(runCatching { failing.move("p", "q", null) }.isFailure); assertEquals(before, logical())
        assertTrue(runCatching { failing.promote("root") }.isFailure); assertEquals(before, logical()); valid()
    }
    @Test fun identifierCollisionsNeverReplaceAnotherRootAndArePermanentlyRecorded() = runBlocking {
        db.projectDao().insert(ProjectEntity("root", "Occupied", "Keep", 2, 3))
        val project = conversion.promote("root")
        assertNotEquals("root", project); assertEquals("Keep", db.projectDao().getById("root")!!.description)
        assertEquals("root", db.conversionDao().project(project)!!.nodeIdentity)
        assertEquals("root", conversion.move(project, "q", null)); assertEquals(project, conversion.promote("root")); valid()
    }
    @Test fun emptyRootsAndDeepHierarchyConvertWithoutRecursiveDeletionOrOrphans() = runBlocking {
        db.projectDao().insert(ProjectEntity("empty", "Empty", "", 0, 1))
        val emptyLayer = conversion.move("empty", "q", null); assertEquals("empty", conversion.promote(emptyLayer))
        db.withTransaction { repeat(1500) { i -> db.nodeDao().insert(NodeEntity("deep-$i", "p", if (i == 0) "root" else "deep-${i-1}", "Depth $i", "", false, i, 1, 2, purpose = "LAYER")) } }
        val project = conversion.promote("root")
        assertEquals(1500, db.nodeDao().getProjectNodes(project).count { it.id.startsWith("deep-") })
        val layer = conversion.move(project, "q", null)
        assertEquals("deep-1498", db.nodeDao().getById("deep-1499")!!.parentId); assertEquals("root", layer); valid()
    }
    @Test fun finalDeletionReleasesHiddenPhotoOnlyAfterLastReferenceAndEditorReservation() = runBlocking {
        val photo = db.projectPhotoDao().forProject("p")!!
        val store = ProjectPhotoStore(context, BackupFixture::syncDirectory); val photos = ProjectPhotoRepository(db, store)
        val layer = conversion.move("p", "q", null); photos.retain(photo.file, "editor")
        NodeRepository(db).deleteNode(layer); photos.cleanup()
        assertTrue(File(context.filesDir, "project-photos/${photo.file}").exists()); assertTrue(db.conversionDao().roots().isEmpty())
        photos.discard(setOf(photo.file), "editor"); assertFalse(File(context.filesDir, "project-photos/${photo.file}").exists()); valid()
    }
    @Test fun nativeEmptyLayerAndHistoricalLongTitleRemainIntact() = runBlocking {
        val title = "Historical layer ".repeat(8)
        db.nodeDao().insert(NodeEntity("empty-native", "p", null, title, "Keep", false, 20, 1, 2, 3, 4, purpose = "LAYER", priority = "LOW"))
        val project = conversion.promote("empty-native")
        assertEquals(title, db.projectDao().getById(project)!!.name)
        assertTrue(db.nodeDao().getProjectNodes(project).isEmpty())
        val layer = conversion.move(project, "q", null)
        assertEquals(title, db.nodeDao().getById(layer)!!.title); assertEquals(3L, db.nodeDao().getById(layer)!!.startAt); valid()
    }
    @Test fun sharedPhotoSurvivesConvertedRootDeletionUntilItsLastConfirmedOwnerIsRemoved() = runBlocking {
        val photo = db.projectPhotoDao().forProject("p")!!
        db.projectPhotoDao().save(photo.copy(id = "shared-photo", projectId = "q"))
        val layer = conversion.move("p", "q", null)
        NodeRepository(db).deleteNode(layer)
        val photos = ProjectPhotoRepository(db, ProjectPhotoStore(context, BackupFixture::syncDirectory))
        photos.cleanup(); assertTrue(File(context.filesDir, "project-photos/${photo.file}").exists())
        assertTrue(photos.save("q", null, com.r0ybt.arachn0de.domain.model.AvatarFraming(), "editor"))
        assertFalse(File(context.filesDir, "project-photos/${photo.file}").exists()); valid()
    }
    @Test fun sqlFailureAfterDetachingAndReinsertingSomeChildrenRollsBackBothDirections() = runBlocking {
        val before = logical()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_child BEFORE INSERT ON nodes WHEN NEW.id = 'bill' AND NEW.projectId != 'p' BEGIN SELECT RAISE(ABORT, 'injected child failure'); END")
        assertTrue(runCatching { conversion.move("p", "q", null) }.isFailure); assertEquals(before, logical())
        assertTrue(runCatching { conversion.promote("root") }.isFailure); assertEquals(before, logical()); valid()
    }
    @Test fun inheritedCreationValuesAndSortModesRemainEffectiveAcrossRootChanges() = runBlocking {
        val defaults = CreationDefaultsRepository(db)
        defaults.save(com.r0ybt.arachn0de.domain.defaults.DefaultsScope.Project("p"),
            com.r0ybt.arachn0de.domain.defaults.CreationDefaults(currency = com.r0ybt.arachn0de.domain.defaults.DefaultValue.Own("EUR")))
        val before = defaults.resolve("p", "root")
        val innerBefore = defaults.resolve("p", "inner")
        val project = conversion.promote("root")
        assertEquals(before, defaults.resolve(project, null)); assertEquals(innerBefore, defaults.resolve(project, "inner"))
        val layer = conversion.move(project, "q", null)
        assertEquals(before, defaults.resolve("q", layer)); assertEquals(innerBefore, defaults.resolve("q", "inner"))
        assertEquals("PRIORITY", db.nodeSortPreferenceDao().all().single { it.context == "q:$layer" }.mode)
        assertEquals("CREATED_NEWEST", db.nodeSortPreferenceDao().all().single { it.context == "q:inner" }.mode); valid()
    }
    @Test fun movingTaskWhileRootIsProjectDropsObsoleteDormantSprintContext() = runBlocking {
        val project = conversion.promote("root")
        assertEquals(1, db.conversionDao().workStates().size)
        assertTrue(NodeRepository(db).moveNode("work", "inner"))
        assertTrue(db.conversionDao().workStates().isEmpty())
        val current = db.nodeDao().getById("work")!!.workState
        conversion.move(project, "q", null)
        assertEquals("inner", db.nodeDao().getById("work")!!.parentId)
        assertEquals(current, db.nodeDao().getById("work")!!.workState); valid()
    }

}
