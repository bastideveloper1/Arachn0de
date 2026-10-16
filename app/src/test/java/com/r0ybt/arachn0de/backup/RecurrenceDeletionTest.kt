package com.r0ybt.arachn0de.backup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class RecurrenceDeletionTest {
    private lateinit var db:Arachn0deDatabase
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private val day=RecurrenceSchedule.parse("2026-10-10")
    private var now=RecurrenceSchedule.timestamp(day,"UTC",720)
    private fun rule(id:String)=RecurrenceRuleEntity(id,"p",null,"Plan $id","Descripción",15000,"CLP",day,"MONTHLY",1,null,0,"ACTIVE","UTC",0,null)
    private fun backup()=BackupRepository(db,context,BackupAvatarFiles(context,syncDirectory=BackupFixture::syncDirectory))
    @Before fun setup()=runBlocking {context.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(context);db.projectDao().insert(ProjectEntity("p","Proyecto","",0,1,1))}
    @After fun close() {db.close()}
    @Test fun deletionKeepsHistoryDoesNotGenerateOtherDebtAndSurvivesReopen()=runBlocking<Unit> {
        var repo=RecurrenceRepository(db) {now}
        repo.create(rule("finished"));repo.create(rule("active"));repo.materializeDue();repo.setStatus("finished",RecurrenceStatus.FINISHED)
        val before=backup().snapshot()
        now=RecurrenceSchedule.timestamp(RecurrenceSchedule.parse("2027-02-10"),"UTC",720)
        repeat(2) {repo.deleteFinished("finished")}
        assertEquals(before.nodes,backup().snapshot().nodes);assertEquals(before.recurrenceOccurrences,db.recurrenceDao().occurrences())
        assertEquals(listOf("active"),repo.rules.first().map {it.id})
        assertTrue(repo.get("finished")!!.isDeleted)
        assertTrue(runCatching {repo.deleteFinished("active")}.isFailure)
        db.close();db=Arachn0deDatabase.create(context);repo=RecurrenceRepository(db) {now}
        assertTrue(repo.get("finished")!!.isDeleted);assertEquals(listOf("active"),repo.rules.first().map {it.id})
        repo.materializeDue()
        assertEquals(before.recurrenceOccurrences.filter {it.ruleId=="finished"},db.recurrenceDao().occurrences().filter {it.ruleId=="finished"})
        assertTrue(runCatching {repo.setStatus("finished",RecurrenceStatus.ACTIVE)}.isFailure)
    }
    @Test fun backupRestoresHiddenRuleWithReceiptsAndOldBackupDefaultsVisibleAndRejectsCorruption()=runBlocking<Unit> {
        db.personDao().insert(PersonEntity("person","Ana",null))
        val repo=RecurrenceRepository(db) {now};repo.create(rule("finished"),setOf("person"));repo.materializeDue();repo.setStatus("finished",RecurrenceStatus.FINISHED)
        val old=backup().snapshot();val legacy=BackupJson.encode(old)
        assertTrue(JSONObject(legacy.toString(Charsets.UTF_8)).getInt("dataVersion")<36)
        assertFalse(BackupJson.decode(legacy).recurrenceRules.single().isDeleted)
        repo.deleteFinished("finished");val original=backup().snapshot();val encoded=BackupJson.encode(original)
        assertEquals(36,JSONObject(encoded.toString(Charsets.UTF_8)).getInt("dataVersion"))
        val restored=backup().inspect(BackupFixture.archive(original).inputStream());backup().restore(restored)
        assertEquals(original.recurrenceRules,backup().snapshot().recurrenceRules);assertEquals(original.recurrenceOccurrences,db.recurrenceDao().occurrences());assertEquals(original.nodes,backup().snapshot().nodes)
        assertTrue(repo.rules.first().isEmpty())
        assertEquals(original.recurrenceAssignments,backup().snapshot().recurrenceAssignments)
        assertEquals(original.assignments,backup().snapshot().assignments)
        val invalid=original.copy(recurrenceRules=original.recurrenceRules.map {it.copy(status="ACTIVE")})
        assertTrue(runCatching {backup().restore(invalid)}.isFailure)
        assertEquals(original.recurrenceRules,backup().snapshot().recurrenceRules)
        val malformed=JSONObject(encoded.toString(Charsets.UTF_8)).apply {getJSONArray("recurrenceRules").getJSONObject(0).put("isDeleted","true")}
        assertTrue(runCatching {BackupJson.decode(malformed.toString().toByteArray())}.isFailure)
        backup().restore(BackupJson.decode(legacy));assertFalse(repo.rules.first().single().isDeleted)
    }
}
