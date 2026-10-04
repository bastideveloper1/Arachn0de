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

@RunWith(RobolectricTestRunner::class) @Config(sdk=[24,28])
class NodeEventBackupTest {
    private lateinit var db: Arachn0deDatabase
    private lateinit var nodes: NodeRepository
    private lateinit var backup: BackupRepository
    private var now = RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2026-10-10"),"UTC",720)
    @Before fun setup() = runBlocking {
        val context = RuntimeEnvironment.getApplication(); context.deleteDatabase("arachn0de.db")
        db=Arachn0deDatabase.create(context); nodes=NodeRepository(db) { now }
        backup=BackupRepository(db,context,BackupAvatarFiles(context,syncDirectory=BackupFixture::syncDirectory))
        db.projectDao().insert(ProjectEntity("p","P","",0,1,1))
    }
    @After fun close() { db.close(); RuntimeEnvironment.getApplication().deleteDatabase("arachn0de.db") }
    @Test fun v4RestoresExactEventsIncludingEqualTimestampsAndMultipleNodesWithoutExtraCreated() = runBlocking {
        val bill=nodes.createNode("p",null,"Bill",obligation=Obligation(15000,"CLP")); val note=nodes.createNode("p",null,"Note",purpose=NodePurpose.NOTE)
        val task=nodes.createNode("p",null,"Task"); now += 1234
        nodes.setCompleted(bill.id,true); nodes.setCompleted(bill.id,false); nodes.setCompleted(bill.id,true); nodes.setCompleted(task.id,true)
        val before=backup.snapshot(); val history=db.nodeEventDao().forNode(bill.id)
        val encoded=BackupJson.encode(before); assertEquals(8,JSONObject(encoded.toString(Charsets.UTF_8)).getInt("dataVersion"))
        val decoded=backup.inspect(BackupFixture.archive(before).inputStream()); assertEquals(before.nodeEvents,decoded.nodeEvents)
        nodes.deleteNode(bill.id); backup.restore(decoded)
        assertEquals(before.nodeEvents,backup.snapshot().nodeEvents); assertEquals(history,db.nodeEventDao().forNode(bill.id))
        assertEquals(1,db.nodeEventDao().forNode(note.id).size); assertEquals(before.nodes,backup.snapshot().nodes)
        backup.restore(decoded); assertEquals(before.nodeEvents,backup.snapshot().nodeEvents)
    }
    @Test fun v1V2V3RestoreCompletedSnapshotWithEmptyHistoryAndNoFabricatedEvents() = runBlocking {
        val node=nodes.createNode("p",null,"Task"); nodes.setCompleted(node.id,true)
        val before=backup.snapshot()
        for (version in 1..3) {
            val json=JSONObject(BackupJson.encode(before).toString(Charsets.UTF_8)).apply {
                for (i in 0 until getJSONArray("nodes").length()) getJSONArray("nodes").getJSONObject(i).apply { remove("priority"); remove("creationGroupId") }
                for (i in 0 until getJSONArray("recurrenceRules").length()) getJSONArray("recurrenceRules").getJSONObject(i).apply { remove("priority"); remove("creationGroupId") }
                put("dataVersion",version); for(i in 0 until getJSONArray("nodes").length()) { val row=getJSONArray("nodes").getJSONObject(i);if(row.getString("purpose")=="LAYER") row.put("purpose","ACTION") }; remove("creationDefaults"); remove("defaultsTags"); remove("defaultsPeople"); remove("nodeEvents")
                if (version < 3) { remove("tags"); remove("nodeTags"); remove("recurrenceTags") }
                if (version < 2) { remove("recurrenceRules"); remove("recurrenceOccurrences"); remove("recurrenceAssignments") }
            }
            val old=BackupJson.decode(json.toString().toByteArray()); assertTrue(old.nodeEvents.isEmpty())
            backup.restore(old); assertTrue(db.nodeEventDao().all().isEmpty()); assertTrue(nodes.getNode(node.id)!!.isCompleted)
        }
    }
    @Test fun restoreAndRecurrencePreserveReceiptAndHistoryExactlyThenNewOccurrenceGetsCreated() = runBlocking {
        val rule=RecurrenceRuleEntity("r","p",null,"Phone","",15000,"CLP",RecurrenceSchedule.parse("2026-10-10"),"MONTHLY",1,null,0,"ACTIVE","UTC",0,null)
        nodes.recurrence.create(rule); nodes.recurrence.materializeDue(); val october=nodes.getProjectNodes("p").single()
        nodes.setCompleted(october.id,true); val before=backup.snapshot()
        backup.restore(BackupJson.decode(BackupJson.encode(before))); nodes.recurrence.materializeDue(); nodes.recurrence.materializeDue()
        val after=backup.snapshot(); assertEquals(before.nodeEvents,after.nodeEvents); assertEquals(before.nodes,after.nodes); assertEquals(before.recurrenceOccurrences,after.recurrenceOccurrences)
        now=RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2026-11-10"),"UTC",720); nodes.recurrence.materializeDue()
        assertEquals(3,db.nodeEventDao().all().size); assertEquals(2,db.recurrenceDao().occurrences().size)
        assertEquals(before.nodeEvents.filter { it.nodeId == october.id }.asReversed(),db.nodeEventDao().forNode(october.id))
    }
    @Test fun invalidTypesIdsReferencesAndNonIntegerTimestampRejectedWithoutChangingDatabase() = runBlocking {
        nodes.createNode("p",null,"Task"); val before=backup.snapshot(); val event=before.nodeEvents.single()
        for (bad in listOf(before.copy(nodeEvents=listOf(event,event)),before.copy(nodeEvents=listOf(event.copy(type="PAID"))),
            before.copy(nodeEvents=listOf(event.copy(nodeId="absent"))),before.copy(nodeEvents=listOf(event.copy(id=""))))) {
            assertTrue(runCatching { backup.restore(bad) }.isFailure); assertEquals(before.nodeEvents,backup.snapshot().nodeEvents)
        }
        for (timestamp in listOf<Any>("123",1.5)) {
            val json=JSONObject(BackupJson.encode(before).toString(Charsets.UTF_8))
            json.getJSONArray("nodeEvents").getJSONObject(0).put("occurredAt",timestamp)
            assertTrue(runCatching { BackupJson.decode(json.toString().toByteArray()) }.isFailure)
        }
    }
    @Test fun failedEventRestoreRollsBackPreviousSnapshot() = runBlocking {
        nodes.createNode("p",null,"Task"); val before=backup.snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore_event BEFORE INSERT ON node_events BEGIN SELECT RAISE(ABORT,'injected'); END")
        assertTrue(runCatching { backup.restore(before) }.isFailure)
        assertEquals(before.nodes,backup.snapshot().nodes); assertEquals(before.nodeEvents,backup.snapshot().nodeEvents)
    }
}
