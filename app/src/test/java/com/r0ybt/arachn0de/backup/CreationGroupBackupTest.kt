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
class CreationGroupBackupTest {
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
    @Test fun v6RestoresDistinctGroupsIndividualsEventsAndIndependentRecurrence()=runBlocking {
        populate()
        val spec=GeneratedNodeSpec("Cuota", "", NodePurpose.ACTION, null, null, Priority.LOW)
        nodes.createBatch("p",null,"first",List(12) { spec.copy(title="Cuota $it") })
        nodes.createBatch("p",null,"second",listOf(spec))
        val before=backup.snapshot()
        val encoded=BackupJson.encode(before)
        assertEquals(17, JSONObject(encoded.toString(Charsets.UTF_8)).getInt("dataVersion"))
        backup.restore(BackupJson.decode(encoded))
        val after=backup.snapshot()
        assertEquals(before.copy(createdAt=after.createdAt),after)
        assertEquals(12,after.nodes.count { it.creationGroupId=="first" })
        assertEquals(1,after.nodes.count { it.creationGroupId=="second" })
        assertEquals(2,after.nodes.count { it.creationGroupId==null })
    }
    @Test fun v1ThroughV5RestoreWithoutGroups()=runBlocking {
        populate()
        val spec=GeneratedNodeSpec("Cuota", "", NodePurpose.ACTION, null, null, Priority.NONE)
        nodes.createBatch("p",null,"group",listOf(spec))
        val before=backup.snapshot()
        for(version in 1..5) {
            val json=JSONObject(BackupJson.encode(before).toString(Charsets.UTF_8)).apply {
                put("dataVersion",version); BackupFixture.removeSprintFields(this); remove("metroPreferences"); remove("metroJourneys"); remove("gameSession"); remove("conversionRoots"); remove("conversionPeople"); remove("conversionTags"); remove("conversionEvents"); remove("conversionWorkStates"); remove("nodeSortPreferences"); remove("imageFiles"); remove("projectPhotos"); remove("projectPhotoImages"); for (personIndex in 0 until getJSONArray("persons").length()) { getJSONArray("persons").getJSONObject(personIndex).apply { remove("avatarZoom"); remove("avatarX"); remove("avatarY") } }; remove("technologies"); remove("nodeTechnologies"); remove("projectTechnologies"); remove("technologyIcons"); remove("attachmentFiles"); remove("nodeAttachments"); remove("projectAttachments"); for(i in 0 until getJSONArray("nodes").length()) { getJSONArray("nodes").getJSONObject(i).remove("sprintMode"); getJSONArray("nodes").getJSONObject(i).remove("workState") }; for(i in 0 until getJSONArray("nodes").length()) { val row=getJSONArray("nodes").getJSONObject(i);if(row.getString("purpose")=="LAYER") row.put("purpose","ACTION") }; remove("creationDefaults"); remove("defaultsTags"); remove("defaultsPeople")
                for(i in 0 until getJSONArray("nodes").length()) getJSONArray("nodes").getJSONObject(i).remove("creationGroupId")
                if(version<5) for(key in listOf("nodes","recurrenceRules")) for(i in 0 until getJSONArray(key).length()) getJSONArray(key).getJSONObject(i).remove("priority")
                if(version<4) remove("nodeEvents")
                if(version<3) { remove("tags");remove("nodeTags");remove("recurrenceTags") }
                if(version<2) { remove("recurrenceRules");remove("recurrenceOccurrences");remove("recurrenceAssignments") }
            }
            backup.restore(BackupJson.decode(json.toString().toByteArray()))
            val after=backup.snapshot()
            assertTrue(after.nodes.all { it.creationGroupId==null })
            assertEquals(before.nodes.map { it.id }.toSet(),after.nodes.map { it.id }.toSet())
            if(version>=4) assertEquals(before.nodeEvents,after.nodeEvents)
            if(version>=2) assertEquals(before.recurrenceOccurrences,after.recurrenceOccurrences)
        }
    }
}
