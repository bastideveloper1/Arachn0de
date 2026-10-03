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
class PriorityBackupTest {
    private lateinit var db:Arachn0deDatabase; private lateinit var nodes:NodeRepository; private lateinit var backup:BackupRepository
    private var now=RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2026-10-10"),"UTC",720)
    @Before fun setup()=runBlocking { val c=RuntimeEnvironment.getApplication(); c.deleteDatabase("arachn0de.db"); db=Arachn0deDatabase.create(c); nodes=NodeRepository(db) { now }; backup=BackupRepository(db,c,BackupAvatarFiles(c,syncDirectory=BackupFixture::syncDirectory)); db.projectDao().insert(ProjectEntity("p","P","",0,1,1)) }
    @After fun close() { db.close(); RuntimeEnvironment.getApplication().deleteDatabase("arachn0de.db") }
    private suspend fun populate() {
        val t=nodes.tags.create("Tag"); val n=nodes.createNode("p",null,"Bill",obligation=Obligation(15000,"CLP"),tagIds=setOf(t.id),priority=Priority.HIGH)
        nodes.setCompleted(n.id,true)
        val rule=RecurrenceRuleEntity("r","p",null,"Plan","",null,null,RecurrenceSchedule.parse("2026-10-10"),"MONTHLY",1,null,0,"ACTIVE","UTC",0,null,priority="MEDIUM")
        nodes.recurrence.create(rule,tagIds=setOf(t.id)); nodes.recurrence.materializeDue()
    }
    @Test fun v5PreservesPriorityTagsEventsRulesAndFutureMaterialization()=runBlocking {
        populate(); val before=backup.snapshot(); val data=BackupJson.decode(BackupJson.encode(before)); backup.restore(data); nodes.recurrence.materializeDue()
        val after=backup.snapshot(); assertEquals(before.nodes,after.nodes); assertEquals(before.recurrenceRules,after.recurrenceRules); assertEquals(before.nodeEvents,after.nodeEvents); assertEquals(before.nodeTags,after.nodeTags)
        now=RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2026-11-10"),"UTC",720); nodes.recurrence.materializeDue()
        assertEquals(2,nodes.getProjectNodes("p").count { it.priority==Priority.MEDIUM }); assertEquals(4,db.nodeEventDao().all().size)
    }
    @Test fun everyLegacyVersionDefaultsNodesAndTemplatesToNoneWithoutFabrication()=runBlocking {
        populate(); val before=backup.snapshot()
        for (version in 1..4) {
            val json=JSONObject(BackupJson.encode(before).toString(Charsets.UTF_8)).apply {
                put("dataVersion",version)
                for (key in listOf("nodes","recurrenceRules")) for (i in 0 until getJSONArray(key).length()) getJSONArray(key).getJSONObject(i).apply { remove("priority"); remove("creationGroupId") }
                if (version<4) remove("nodeEvents")
                if (version<3) { remove("tags"); remove("nodeTags"); remove("recurrenceTags") }
                if (version<2) { remove("recurrenceRules"); remove("recurrenceOccurrences"); remove("recurrenceAssignments") }
            }
            val old=BackupJson.decode(json.toString().toByteArray()); backup.restore(old)
            assertTrue(backup.snapshot().nodes.all { it.priority=="NONE" }); assertTrue(backup.snapshot().recurrenceRules.all { it.priority=="NONE" })
            if (version==4) assertEquals(before.nodeEvents,backup.snapshot().nodeEvents) else assertTrue(backup.snapshot().nodeEvents.isEmpty())
        }
    }
    @Test fun invalidPriorityRejectedBeforeRestore()=runBlocking {
        populate(); val before=backup.snapshot()
        for (bad in listOf(before.copy(nodes=before.nodes.map { it.copy(priority="URGENT") }),before.copy(recurrenceRules=before.recurrenceRules.map { it.copy(priority="OTHER") }))) {
            assertTrue(runCatching { backup.restore(bad) }.isFailure); assertEquals(before.nodes,backup.snapshot().nodes); assertEquals(before.nodeEvents,backup.snapshot().nodeEvents)
        }
    }
}
