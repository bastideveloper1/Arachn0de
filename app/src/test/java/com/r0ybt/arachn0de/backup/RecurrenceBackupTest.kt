package com.r0ybt.arachn0de.backup

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
class RecurrenceBackupTest {
    private lateinit var db: Arachn0deDatabase
    private lateinit var backup: BackupRepository
    private lateinit var nodes: NodeRepository
    private var now = RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2026-10-10"), "UTC", 720)
    private fun rule(id: String) = RecurrenceRuleEntity(id, "p", null, "Plan", "", 15000, "CLP", RecurrenceSchedule.parse("2026-10-10"), "MONTHLY", 1, null, 0, "ACTIVE", "UTC", 0, null)
    @Before fun setup() = runBlocking {
        val context = RuntimeEnvironment.getApplication(); context.deleteDatabase("arachn0de.db")
        db = Arachn0deDatabase.create(context); nodes = NodeRepository(db) { now }
        backup = BackupRepository(db, context, BackupAvatarFiles(context, syncDirectory = BackupFixture::syncDirectory))
        db.projectDao().insert(ProjectEntity("p", "Proyecto", "", 0, 1, 1))
        db.personDao().insert(PersonEntity("roy", "Roy", null))
    }
    @After fun cleanup() { db.close(); RuntimeEnvironment.getApplication().deleteDatabase("arachn0de.db") }
    @Test fun roundTripPreservesAllStatesTemplatePeopleHistoryAndDeletionReceiptsAndDoesNotDuplicate() = runBlocking {
        val repo = nodes.recurrence
        for (id in listOf("active", "paused", "finished")) repo.create(rule(id), setOf("roy"))
        repo.materializeDue(); repo.setStatus("paused", RecurrenceStatus.PAUSED); repo.setStatus("finished", RecurrenceStatus.FINISHED)
        val deleted = db.recurrenceDao().occurrences().single { it.ruleId == "active" }.nodeId
        nodes.deleteNode(deleted)
        val original = backup.snapshot()
        val restored = backup.inspect(BackupFixture.archive(original).inputStream())
        assertEquals(original.recurrenceRules, restored.recurrenceRules)
        assertEquals(original.recurrenceOccurrences, restored.recurrenceOccurrences)
        assertEquals(original.recurrenceAssignments, restored.recurrenceAssignments)
        backup.restore(restored); repo.materializeDue()
        assertEquals(original.nodes, backup.snapshot().nodes)
        assertNull(nodes.getNode(deleted))
        now = RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2027-01-10"), "UTC", 720)
        repo.materializeDue()
        assertEquals(6, db.recurrenceDao().occurrences().size) // three October receipts + ACTIVE Nov/Dec/Jan
        assertEquals(original.recurrenceRules.filter { it.status != "ACTIVE" }, db.recurrenceDao().rules().filter { it.status != "ACTIVE" })
    }
    @Test fun genuineV1PayloadAndEnvelopeRestoreReplacingRecurrencesWithEmptyRules() = runBlocking {
        val legacy = BackupFixture.empty().copy(projects = listOf(ProjectEntity("old", "Antiguo", "", 8, 1, 2)))
        val json = JSONObject(BackupJson.encode(legacy).toString(Charsets.UTF_8)).apply {
            for (i in 0 until getJSONArray("nodes").length()) getJSONArray("nodes").getJSONObject(i).apply { remove("priority"); remove("creationGroupId") }
            for (i in 0 until getJSONArray("recurrenceRules").length()) getJSONArray("recurrenceRules").getJSONObject(i).apply { remove("priority"); remove("creationGroupId") }
            put("dataVersion",1); BackupFixture.removeSprintFields(this); remove("metroPreferences"); remove("metroJourneys"); remove("gameSession"); remove("conversionRoots"); remove("conversionPeople"); remove("conversionTags"); remove("conversionEvents"); remove("conversionWorkStates"); remove("nodeSortPreferences"); remove("imageFiles"); remove("projectPhotos"); remove("projectPhotoImages"); for (personIndex in 0 until getJSONArray("persons").length()) { getJSONArray("persons").getJSONObject(personIndex).apply { remove("avatarZoom"); remove("avatarX"); remove("avatarY") } }; remove("technologies"); remove("nodeTechnologies"); remove("projectTechnologies"); remove("technologyIcons"); remove("attachmentFiles"); remove("nodeAttachments"); remove("projectAttachments"); for(i in 0 until getJSONArray("nodes").length()) { getJSONArray("nodes").getJSONObject(i).remove("sprintMode"); getJSONArray("nodes").getJSONObject(i).remove("workState") }; for(i in 0 until getJSONArray("nodes").length()) { val row=getJSONArray("nodes").getJSONObject(i);if(row.getString("purpose")=="LAYER") row.put("purpose","ACTION") }; remove("creationDefaults"); remove("defaultsTags"); remove("defaultsPeople"); remove("nodeEvents"); remove("tags"); remove("nodeTags"); remove("recurrenceTags"); remove("recurrenceRules"); remove("recurrenceOccurrences"); remove("recurrenceAssignments")
        }.toString().toByteArray()
        val bytes = java.io.ByteArrayOutputStream().also { BackupContainer.write(json, it) }.toByteArray()
        nodes.recurrence.create(rule("current")); nodes.recurrence.materializeDue()
        val data = backup.inspect(bytes.inputStream()); backup.restore(data)
        assertTrue(db.recurrenceDao().rules().isEmpty()); assertTrue(db.recurrenceDao().occurrences().isEmpty())
        assertEquals(legacy.projects, backup.snapshot().projects)
    }
    @Test fun corruptRecurrenceBackupRejectedWithoutChangingDatabase() = runBlocking {
        nodes.recurrence.create(rule("rule")); nodes.recurrence.materializeDue()
        val data = backup.snapshot()
        for (bad in listOf(
            data.copy(recurrenceRules = data.recurrenceRules.map { it.copy(interval = 0) }),
            data.copy(recurrenceOccurrences = data.recurrenceOccurrences + data.recurrenceOccurrences),
            data.copy(recurrenceRules = emptyList()),
            data.copy(recurrenceAssignments = listOf(RecurrencePersonEntity("rule", "missing"))),
            data.copy(recurrenceOccurrences = data.recurrenceOccurrences.map { it.copy(nodeId = "bad-id") }),
            data.copy(recurrenceRules = data.recurrenceRules.map { it.copy(nextIndex = 0) }),
        )) assertTrue(runCatching { backup.restore(bad) }.isFailure)
        assertEquals(data.nodes, backup.snapshot().nodes); assertEquals(data.recurrenceRules, backup.snapshot().recurrenceRules)
    }
    @Test fun restoreFailureRollsBackRecurrenceAndOldNodesTogether() = runBlocking {
        nodes.recurrence.create(rule("old")); nodes.recurrence.materializeDue(); val original = backup.snapshot()
        val replacement = original.copy(recurrenceRules = original.recurrenceRules.map { it.copy(title = "FAIL") })
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore_rule BEFORE INSERT ON recurrence_rules WHEN NEW.title='FAIL' BEGIN SELECT RAISE(ABORT,'fail'); END")
        assertTrue(runCatching { backup.restore(replacement) }.isFailure)
        assertEquals(original.nodes, backup.snapshot().nodes); assertEquals(original.recurrenceRules, backup.snapshot().recurrenceRules)
        assertEquals(original.recurrenceOccurrences, backup.snapshot().recurrenceOccurrences)
    }
    @Test fun restoreActiveCursorRecoversClosedMonthsExactlyOnce() = runBlocking {
        nodes.recurrence.create(rule("rule")); nodes.recurrence.materializeDue(); val archived = BackupFixture.archive(backup.snapshot())
        now = RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2027-01-10"), "UTC", 720)
        backup.restore(backup.inspect(archived.inputStream())); repeat(3) { nodes.recurrence.materializeDue() }
        assertEquals(4, db.recurrenceDao().occurrences().size); assertEquals(4, nodes.getProjectNodes("p").size)
    }
}
