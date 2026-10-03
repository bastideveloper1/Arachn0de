package com.r0ybt.arachn0de.backup

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class) @Config(sdk = [24,28])
class TagsBackupTest {
    @Test fun v3RoundTripRestoresUnusedTagsAndExactRelationsAndOlderVersionsAreEmpty() = runBlocking {
        val context = RuntimeEnvironment.getApplication(); context.deleteDatabase("arachn0de.db")
        val db = Arachn0deDatabase.create(context)
        try {
            db.projectDao().insert(ProjectEntity("p","P","",0,1,1))
            val repo = NodeRepository(db); val a = repo.tags.create("A"); repo.tags.create("Unused")
            repo.createNode("p",null,"Task",tagIds=setOf(a.id))
            val backup = BackupRepository(db,context,BackupAvatarFiles(context,syncDirectory=BackupFixture::syncDirectory))
            val snapshot = backup.snapshot(); val data = BackupJson.decode(BackupJson.encode(snapshot))
            backup.restore(data); assertEquals(snapshot.tags,backup.snapshot().tags); assertEquals(snapshot.nodeTags,backup.snapshot().nodeTags)
            for (version in listOf(1,2)) {
                val json = JSONObject(BackupJson.encode(data).toString(Charsets.UTF_8)).apply {
                    for (i in 0 until getJSONArray("nodes").length()) getJSONArray("nodes").getJSONObject(i).apply { remove("priority"); remove("creationGroupId") }
                for (i in 0 until getJSONArray("recurrenceRules").length()) getJSONArray("recurrenceRules").getJSONObject(i).apply { remove("priority"); remove("creationGroupId") }
                put("dataVersion",version); remove("nodeEvents"); remove("tags"); remove("nodeTags"); remove("recurrenceTags")
                    if (version == 1) { remove("recurrenceRules"); remove("recurrenceOccurrences"); remove("recurrenceAssignments") }
                }
                val old = BackupJson.decode(json.toString().toByteArray()); assertTrue(old.tags.isEmpty())
                backup.restore(old); assertTrue(db.tagDao().tags().isEmpty())
            }
        } finally { db.close(); context.deleteDatabase("arachn0de.db") }
    }
    @Test fun invalidNamesDuplicatesAndReferencesRejectedBeforeRestore() {
        val tag = TagEntity("a","A","a")
        val base = BackupFixture.empty().copy(tags=listOf(tag))
        for (bad in listOf(base.copy(tags=listOf(tag,tag.copy(id="b"))), base.copy(tags=listOf(tag.copy(normalizedName="A"))),
            base.copy(nodeTags=listOf(NodeTagEntity("missing","a"))), base.copy(recurrenceTags=listOf(RecurrenceTagEntity("missing","a"))))) {
            assertTrue(runCatching { bad.validate() }.isFailure)
        }
    }
}
