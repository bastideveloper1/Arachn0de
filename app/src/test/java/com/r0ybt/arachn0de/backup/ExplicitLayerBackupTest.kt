package com.r0ybt.arachn0de.backup

import com.r0ybt.arachn0de.data.local.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[24,28])
class ExplicitLayerBackupTest {
    private fun data()=BackupFixture.empty().copy(projects=listOf(ProjectEntity("p","Project","",0,1,1)),nodes=listOf(
        NodeEntity("root","p",null,"Layer","",false,0,1,1,purpose="LAYER"),
        NodeEntity("task","p","root","Task","",true,0,1,1),NodeEntity("note","p","root","Note","",false,1,1,1,purpose="NOTE")))
    @Test fun v8KeepsEmptyLayerAndRejectsInvalidHierarchy() {
        val empty=data().copy(nodes=listOf(data().nodes.first()))
        val bytes=BackupJson.encode(empty);assertEquals(12,JSONObject(String(bytes)).getInt("dataVersion"));assertEquals(empty,BackupJson.decode(bytes))
        assertTrue(runCatching { data().copy(nodes=data().nodes.map { if(it.id=="root") it.copy(purpose="ACTION") else it }).validate() }.isFailure)
        assertTrue(runCatching { empty.copy(nodes=listOf(empty.nodes.first().copy(isCompleted=true))).validate() }.isFailure)
    }
    @Test fun v1ThroughV7InferContainersButKeepTaskCompletionAndNotes() {
        for(version in 1..7) {
            val json=JSONObject(String(BackupJson.encode(data()))).apply {
                put("dataVersion",version); for (personIndex in 0 until getJSONArray("persons").length()) { getJSONArray("persons").getJSONObject(personIndex).apply { remove("avatarZoom"); remove("avatarX"); remove("avatarY") } }; remove("technologies"); remove("nodeTechnologies"); remove("projectTechnologies"); remove("technologyIcons"); remove("attachmentFiles"); remove("nodeAttachments"); remove("projectAttachments"); for(i in 0 until getJSONArray("nodes").length()) { getJSONArray("nodes").getJSONObject(i).remove("sprintMode"); getJSONArray("nodes").getJSONObject(i).remove("workState") };getJSONArray("nodes").getJSONObject(0).put("purpose","ACTION")
                if(version<7) { remove("creationDefaults");remove("defaultsTags");remove("defaultsPeople") }
                if(version<6) for(i in 0 until getJSONArray("nodes").length()) getJSONArray("nodes").getJSONObject(i).remove("creationGroupId")
                if(version<5) for(i in 0 until getJSONArray("nodes").length()) getJSONArray("nodes").getJSONObject(i).remove("priority")
                if(version<4) remove("nodeEvents")
                if(version<3) { remove("tags");remove("nodeTags");remove("recurrenceTags") }
                if(version<2) { remove("recurrenceRules");remove("recurrenceOccurrences");remove("recurrenceAssignments") }
            }
            val restored=BackupJson.decode(json.toString().toByteArray())
            assertEquals(data(),restored)
        }
    }
    @Test fun legacyInvalidNoteParentIsRejectedRatherThanConverted() {
        val json=JSONObject(String(BackupJson.encode(data()))).apply { put("dataVersion",7); for (personIndex in 0 until getJSONArray("persons").length()) { getJSONArray("persons").getJSONObject(personIndex).apply { remove("avatarZoom"); remove("avatarX"); remove("avatarY") } }; remove("technologies"); remove("nodeTechnologies"); remove("projectTechnologies"); remove("technologyIcons"); remove("attachmentFiles"); remove("nodeAttachments"); remove("projectAttachments"); for(i in 0 until getJSONArray("nodes").length()) { getJSONArray("nodes").getJSONObject(i).remove("sprintMode"); getJSONArray("nodes").getJSONObject(i).remove("workState") };getJSONArray("nodes").getJSONObject(0).put("purpose","NOTE") }
        assertTrue(runCatching { BackupJson.decode(json.toString().toByteArray()) }.isFailure)
    }
}
