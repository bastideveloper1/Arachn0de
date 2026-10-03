package com.r0ybt.arachn0de.backup

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.defaults.*
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
class CreationDefaultsBackupTest {
    private lateinit var db:Arachn0deDatabase;private lateinit var nodes:NodeRepository;private lateinit var backup:BackupRepository
    @Before fun setup()=runBlocking {
        val c=RuntimeEnvironment.getApplication();c.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(c);nodes=NodeRepository(db)
        backup=BackupRepository(db,c,BackupAvatarFiles(c,syncDirectory=BackupFixture::syncDirectory));db.projectDao().insert(ProjectEntity("p","Project","",0,1,1))
        val layer=nodes.createNode("p",null,"Layer",creationId="layer");nodes.createNode("p",layer.id,"Task",creationId="task",priority=Priority.HIGH)
        db.personDao().insert(PersonEntity("person","Ana",null));val tag=nodes.tags.create("Tag")
        nodes.creationDefaults.save(DefaultsScope.Global,CreationDefaults(tags=DefaultValue.Own(setOf(tag.id)),priority=DefaultValue.Own(Priority.MEDIUM)))
        nodes.creationDefaults.save(DefaultsScope.Project("p"),CreationDefaults(people=DefaultValue.Own(setOf("person")),currency=DefaultValue.Own("USD")))
        nodes.creationDefaults.save(DefaultsScope.Layer("p",layer.id),CreationDefaults(obligation=DefaultValue.Own(false),start=DefaultValue.Own(DefaultDate()),due=DefaultValue.Own(DefaultDate(DefaultDateKind.DAY_OF_MONTH,31)),dueTime=DefaultValue.Own(DefaultTime.Minute(1320))))
    }
    @After fun close() { db.close();RuntimeEnvironment.getApplication().deleteDatabase("arachn0de.db") }
    @Test fun v7RoundTripRestoresAllScopesAndEffectiveConfiguration()=runBlocking {
        val before=backup.snapshot();val effective=nodes.creationDefaults.resolve("p","layer")
        val bytes=BackupJson.encode(before);assertEquals(7,JSONObject(bytes.toString(Charsets.UTF_8)).getInt("dataVersion"))
        backup.restore(BackupJson.decode(bytes));val after=backup.snapshot()
        assertEquals(before.copy(createdAt=after.createdAt),after);assertEquals(effective,nodes.creationDefaults.resolve("p","layer"))
    }
    @Test fun everyOlderFormatRestoresWithoutDefaultsAndClearsExistingGlobal()=runBlocking {
        val before=backup.snapshot()
        for(version in 1..6) {
            nodes.creationDefaults.save(DefaultsScope.Global,CreationDefaults(currency=DefaultValue.Own("EUR")))
            val json=JSONObject(BackupJson.encode(before).toString(Charsets.UTF_8)).apply {
                put("dataVersion",version);remove("creationDefaults");remove("defaultsTags");remove("defaultsPeople")
                if(version<6) for(i in 0 until getJSONArray("nodes").length()) getJSONArray("nodes").getJSONObject(i).remove("creationGroupId")
                if(version<5) for(key in listOf("nodes","recurrenceRules")) for(i in 0 until getJSONArray(key).length()) getJSONArray(key).getJSONObject(i).remove("priority")
                if(version<4) remove("nodeEvents")
                if(version<3) { remove("tags");remove("nodeTags");remove("recurrenceTags") }
                if(version<2) { remove("recurrenceRules");remove("recurrenceOccurrences");remove("recurrenceAssignments") }
            }
            backup.restore(BackupJson.decode(json.toString().toByteArray()))
            assertTrue(db.creationDefaultsDao().all().isEmpty());assertEquals(EffectiveCreationDefaults(),nodes.creationDefaults.resolve("p","layer"))
        }
    }
    @Test fun invalidConfigurationIdentityValuesAndReferencesPreservePreviousDatabase()=runBlocking {
        val before=backup.snapshot()
        val global=before.creationDefaults.single { it.id=="G" }
        val bads=listOf(
            before.copy(creationDefaults=before.creationDefaults+global),
            before.copy(creationDefaults=before.creationDefaults.map { if(it.id=="G") it.copy(currency="XYZ") else it }),
            before.copy(creationDefaults=before.creationDefaults.map { if(it.id=="G") it.copy(id="other") else it }),
            before.copy(creationDefaults=before.creationDefaults.map { if(it.id=="N:layer") it.copy(dueNumber=32) else it }),
            before.copy(defaultsTags=listOf(CreationDefaultsTagEntity("G","missing"))),
            before.copy(defaultsPeople=listOf(CreationDefaultsPersonEntity("P:p","missing"))),
            before.copy(creationDefaults=before.creationDefaults.map { if(it.id=="G") it.copy(tagsOverride=false) else it }),
            before.copy(creationDefaults=before.creationDefaults.map { if(it.id=="N:layer") it.copy(projectId="missing") else it })
        )
        for(bad in bads) { assertTrue(runCatching { backup.restore(bad) }.isFailure);val after=backup.snapshot();assertEquals(before.copy(createdAt=after.createdAt),after) }
    }
    @Test fun defaultsInsertFailureRollsBackTheEntireRestore()=runBlocking {
        val before=backup.snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_defaults BEFORE INSERT ON creation_defaults WHEN NEW.id='N:layer' BEGIN SELECT RAISE(ABORT,'injected'); END")
        try {
            assertTrue(runCatching { backup.restore(before) }.isFailure)
            val after=backup.snapshot();assertEquals(before.copy(createdAt=after.createdAt),after)
        } finally { db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_defaults") }
    }
    @Test fun malformedJsonRulesAndHoursAreRejectedByDecoder()=runBlocking {
        val before=backup.snapshot()
        for((key,value) in listOf("dueMinute" to 1440,"dueRule" to "RECURRENT","dueNumber" to 0)) {
            val json=JSONObject(BackupJson.encode(before).toString(Charsets.UTF_8));val rows=json.getJSONArray("creationDefaults")
            for(i in 0 until rows.length()) if(rows.getJSONObject(i).getString("id")=="N:layer") rows.getJSONObject(i).put(key,value)
            assertTrue(runCatching { BackupJson.decode(json.toString().toByteArray()) }.isFailure)
        }
    }
}
