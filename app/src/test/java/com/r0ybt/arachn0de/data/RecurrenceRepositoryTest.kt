package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
class RecurrenceRepositoryTest {
    private lateinit var db: Arachn0deDatabase
    private lateinit var repo: RecurrenceRepository
    private lateinit var nodes: NodeRepository
    private var now = 0L
    private fun at(date: String) { now = RecurrenceSchedule.timestamp(RecurrenceSchedule.parse(date), "UTC", 720) }
    private fun rule(id: String = "rule", frequency: String = "MONTHLY", start: String = "2026-10-10", end: String? = null, interval: Int = 1) =
        RecurrenceRuleEntity(id, "project", null, "Teléfono", "Plan", 15000, "CLP", RecurrenceSchedule.parse(start), frequency, interval,
            end?.let(RecurrenceSchedule::parse), 0, "ACTIVE", "UTC", 0, null)
    @Before fun setup() = runBlocking {
        val context = RuntimeEnvironment.getApplication(); context.deleteDatabase("arachn0de.db")
        db = Arachn0deDatabase.create(context); nodes = NodeRepository(db) { now }; repo = nodes.recurrence
        db.projectDao().insert(ProjectEntity("project", "Proyecto", "", 0, 1, 1))
        at("2026-10-10")
    }
    @After fun cleanup() { db.close(); RuntimeEnvironment.getApplication().deleteDatabase("arachn0de.db") }
    @Test fun noFutureNodesAndRepeatedRunsAreIdempotent() = runBlocking {
        repo.create(rule()); repeat(3) { repo.materializeDue() }
        assertEquals(1, db.recurrenceDao().occurrences().size)
        assertEquals(1, nodes.getProjectNodes("project").size)
        assertEquals(1L, repo.get("rule")!!.nextIndex)
        assertEquals("ACTIVE", repo.get("rule")!!.status)
        at("2026-11-09"); repo.materializeDue(); assertEquals(1, nodes.getProjectNodes("project").size)
    }
    @Test fun closedAppRecoversAllActiveMonths() = runBlocking {
        repo.create(rule()); repo.materializeDue(); at("2027-02-10"); repo.materializeDue()
        assertEquals(listOf("2026-10-10", "2026-11-10", "2026-12-10", "2027-01-10", "2027-02-10"), db.recurrenceDao().occurrences().map { RecurrenceSchedule.format(it.day) })
    }
    @Test fun pauseThenResumeJanuarySkipsNovemberAndDecember() = runBlocking {
        repo.create(rule()); repo.materializeDue(); repo.setStatus("rule", RecurrenceStatus.PAUSED)
        at("2026-12-20"); repo.materializeDue(); assertEquals(1, db.recurrenceDao().occurrences().size)
        at("2027-01-01"); repo.setStatus("rule", RecurrenceStatus.ACTIVE)
        assertEquals(1, db.recurrenceDao().occurrences().size)
        assertEquals(3L, repo.get("rule")!!.nextIndex)
        at("2027-01-10"); repo.materializeDue()
        assertEquals(listOf("2026-10-10", "2027-01-10"), db.recurrenceDao().occurrences().map { RecurrenceSchedule.format(it.day) })
    }
    @Test fun resumeOnScheduledDayIncludesTodayWithoutReplayingDeletedReceipt() = runBlocking {
        repo.create(rule()); repo.materializeDue(); repo.setStatus("rule", RecurrenceStatus.PAUSED)
        nodes.deleteNode(db.recurrenceDao().occurrences().single().nodeId)
        repo.setStatus("rule", RecurrenceStatus.ACTIVE); repo.materializeDue()
        assertTrue(nodes.getProjectNodes("project").isEmpty())
        repo.setStatus("rule", RecurrenceStatus.PAUSED); at("2027-01-10"); repo.setStatus("rule", RecurrenceStatus.ACTIVE)
        assertEquals(1, nodes.getProjectNodes("project").size)
    }
    @Test fun finishIsPermanentAndRetainsHistory() = runBlocking {
        repo.create(rule()); repo.materializeDue(); val before = nodes.getProjectNodes("project")
        repo.setStatus("rule", RecurrenceStatus.FINISHED); at("2030-10-10"); repo.materializeDue()
        assertEquals(before, nodes.getProjectNodes("project")); assertEquals("FINISHED", repo.get("rule")!!.status)
        assertTrue(runCatching { repo.setStatus("rule", RecurrenceStatus.ACTIVE) }.isFailure)
        assertTrue(runCatching { repo.setStatus("rule", RecurrenceStatus.PAUSED) }.isFailure)
    }
    @Test fun inclusiveEndFinishesAndExpiredPausedRuleCannotResume() = runBlocking {
        repo.create(rule(end = "2026-11-10")); at("2027-01-10"); repo.materializeDue()
        assertEquals(2, db.recurrenceDao().occurrences().size); assertEquals("FINISHED", repo.get("rule")!!.status)
        at("2026-10-10"); repo.create(rule("paused", end = "2026-12-10")); repo.setStatus("paused", RecurrenceStatus.PAUSED)
        at("2027-03-10"); repo.setStatus("paused", RecurrenceStatus.ACTIVE)
        assertEquals("FINISHED", repo.get("paused")!!.status)
    }
    @Test fun octoberSnapshotRemainsOldNovemberUsesEditedTemplateAndPeopleAndDestination() = runBlocking {
        db.personDao().insert(PersonEntity("roy", "Roy", null)); db.personDao().insert(PersonEntity("ana", "Ana", null))
        val layer = nodes.createNode("project", null, "Capa")
        repo.create(rule(), setOf("roy")); repo.materializeDue()
        val october = nodes.getProjectNodes("project").single { it.obligation != null }
        repo.editTemplate("rule", "project", layer.id, "Nuevo plan", "Nuevo", Obligation(17000, "CLP"), setOf("ana"))
        at("2026-11-10"); repo.materializeDue()
        val november = nodes.getProjectNodes("project").single { it.id != october.id && it.obligation != null }
        assertEquals(october, nodes.getNode(october.id)); assertEquals(15000L, october.obligation!!.amountMinor)
        assertEquals(17000L, november.obligation!!.amountMinor); assertEquals("Nuevo plan", november.title)
        assertEquals("Nuevo", november.description); assertEquals(layer.id, november.parentId)
        assertEquals(listOf("roy"), db.personDao().assignmentIds(october.id)); assertEquals(listOf("ana"), db.personDao().assignmentIds(november.id))
        assertFalse(november.isCompleted)
    }
    @Test fun editsAndPauseFirstSettleMissedActiveDebtUsingOldTemplate() = runBlocking {
        repo.create(rule()); at("2026-12-10")
        repo.editTemplate("rule", "project", null, "Cambio", "", Obligation(17000, "CLP"), emptySet())
        assertTrue(nodes.getProjectNodes("project").all { it.obligation!!.amountMinor == 15000L })
        at("2027-01-10"); repo.setStatus("rule", RecurrenceStatus.PAUSED)
        assertEquals(4, nodes.getProjectNodes("project").size)
    }
    @Test fun completingOrDeletingOccurrenceDoesNotChangeRuleOrRecreateDeletedNodeAcrossReopen() = runBlocking {
        repo.create(rule()); repo.materializeDue(); val node = nodes.getProjectNodes("project").single()
        nodes.setCompleted(node.id, true); assertEquals("ACTIVE", repo.get("rule")!!.status)
        nodes.deleteNode(node.id); db.close(); db = Arachn0deDatabase.create(RuntimeEnvironment.getApplication())
        nodes = NodeRepository(db) { now }; repo = nodes.recurrence
        repo.materializeDue(); assertTrue(nodes.getProjectNodes("project").isEmpty()); assertEquals(1, db.recurrenceDao().occurrences().size)
        at("2026-11-10"); repo.materializeDue(); assertEquals(1, nodes.getProjectNodes("project").size)
    }
    @Test fun generalTasksAndIntervalsMaterializeNormalNodesWithDates() = runBlocking {
        repo.create(rule(frequency = "WEEKLY", interval = 2).copy(amountMinor = null, currencyCode = null, startOffsetMillis = 3600000))
        at("2026-11-07"); repo.materializeDue()
        val all = nodes.getProjectNodes("project"); assertEquals(3, all.size)
        assertTrue(all.all { it.isCompletable && !it.isCompleted && it.obligation == null && it.dueAt!! - it.startAt!! == 3600000L })
    }
    @Test fun dueTimeIsRespectedAndFutureRuleHasNoOccurrence() = runBlocking {
        repo.create(rule().copy(dueMinute = 800)); repo.materializeDue(); assertTrue(nodes.getProjectNodes("project").isEmpty())
        now += 2 * 3600000; repo.materializeDue(); assertEquals(1, nodes.getProjectNodes("project").size)
        repo.create(rule("future", start = "2028-01-01")); repo.materializeDue(); assertEquals(1, db.recurrenceDao().occurrences().size)
    }
    @Test fun catchupIsChunkedWithoutArtificialLifetimeLimit() = runBlocking {
        repo.create(rule(frequency = "DAILY", start = "2025-01-01"))
        assertTrue(repo.materializeBatch()); assertEquals(256, db.recurrenceDao().occurrences().size)
        repo.materializeDue(); val count = db.recurrenceDao().occurrences().size
        assertTrue(count > 500); assertEquals("ACTIVE", repo.get("rule")!!.status)
        repo.materializeDue(); assertEquals(count, db.recurrenceDao().occurrences().size)
    }
    @Test fun concurrentRunsSerializeWithoutDuplicates() = runBlocking {
        repo.create(rule()); at("2027-01-10")
        coroutineScope { repeat(4) { launch(Dispatchers.Default) { repo.materializeDue() } } }
        assertEquals(4, db.recurrenceDao().occurrences().size); assertEquals(4, nodes.getProjectNodes("project").size)
    }
    @Test fun failureRollsBackNodeReceiptAndCursor() = runBlocking {
        repo.create(rule())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_receipt BEFORE INSERT ON recurrence_occurrences BEGIN SELECT RAISE(ABORT,'fail'); END")
        assertTrue(runCatching { repo.materializeDue() }.isFailure)
        assertTrue(nodes.getProjectNodes("project").isEmpty()); assertEquals(0L, repo.get("rule")!!.nextIndex)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_receipt"); repo.materializeDue(); assertEquals(1, nodes.getProjectNodes("project").size)
    }
    @Test fun deletedDestinationPausesRatherThanLosingRuleOrHistory() = runBlocking {
        val layer = nodes.createNode("project", null, "Capa"); repo.create(rule().copy(parentId = layer.id)); repo.materializeDue()
        nodes.deleteNode(layer.id); at("2026-11-10"); repo.materializeDue()
        assertEquals("PAUSED", repo.get("rule")!!.status); assertEquals(1, db.recurrenceDao().occurrences().size)
        repo.editTemplate("rule", "project", null, "Reparado", "", null, emptySet()); repo.setStatus("rule", RecurrenceStatus.ACTIVE)
        assertEquals(1, nodes.getProjectNodes("project").size)
    }
    @Test fun corruptIntervalsAndUnknownStatesAreRejectedBeforeWrite() = runBlocking {
        for (bad in listOf(rule().copy(interval = 0), rule().copy(status = "UNKNOWN"), rule().copy(nextIndex = Long.MAX_VALUE), rule().copy(endDay = 0)))
            assertTrue(runCatching { repo.create(bad) }.isFailure)
        assertTrue(db.recurrenceDao().rules().isEmpty())
    }
    @Test fun financialAndCalendarConsumeMaterializedNodesWithoutEngineCoupling() = runBlocking {
        db.personDao().insert(PersonEntity("roy", "Roy", null))
        repo.create(rule(), setOf("roy")); at("2026-12-10"); repo.materializeDue()
        val tree = nodes.observeAllState().first()
        val calendar = CalendarSnapshot(tree, java.util.TimeZone.getTimeZone("UTC"))
        assertEquals(3, calendar.tasksByDay.size)
        assertEquals(tree.nodes.map { it.id }.toSet(), calendar.tasksByDay.values.flatten().map { it.id }.toSet())
        val people = PersonRepository(db, AvatarStore(RuntimeEnvironment.getApplication())).observeAllAssignments().first()
        val finance = FinancialSnapshot(tree, people, FinancialSelection(FinancialPeriod.ALL, "roy"), CalendarMonth(2026, 12), java.util.TimeZone.getTimeZone("UTC"))
        assertEquals(3, finance.summary.count)
        assertEquals(FinancialState.PENDING, finance.summary.state)
    }
    @Test fun creationRetryHasStableIdentity() = runBlocking {
        repo.create(rule()); repo.materializeDue(); repo.create(rule()); repo.materializeDue()
        assertEquals(1, db.recurrenceDao().rules().size); assertEquals(1, nodes.getProjectNodes("project").size)
    }
}
