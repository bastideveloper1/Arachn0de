package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.*
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.backup.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.*

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class CalendarEventsTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private fun event(date:String="2026-12-31",minute:Int?=null)=CalendarEventEntity(UUID.randomUUID().toString(),"Cita",date,minute,"Descripción privada")
    @Test fun independentCrudRelationsAndPersonDeletionKeepTasksUntouched()=runBlocking<Unit> {
        context.deleteDatabase("arachn0de.db");val db=Arachn0deDatabase.create(context)
        try {
            val repo=CalendarEventRepository(db);val row=event()
            db.personDao().insert(PersonEntity("roy","Roy",null))
            repo.save(row,setOf("roy"),true)
            assertTrue(db.backupDao().nodes().isEmpty());assertTrue(db.backupDao().projects().isEmpty())
            assertEquals(listOf(row),repo.observe().first().matching("roy"))
            assertTrue(repo.observe().first().matching("absent").isEmpty())
            val updated=row.copy(title="Reunión",minute=600)
            repo.save(updated,emptySet(),false)
            assertEquals(updated,db.calendarEventDao().all().single());assertTrue(db.calendarEventDao().people().isEmpty())
            repo.save(updated,setOf("roy"),false);db.personDao().delete("roy")
            assertEquals(updated,db.calendarEventDao().all().single());assertTrue(db.calendarEventDao().people().isEmpty())
            assertTrue(repo.delete(row.id));assertTrue(db.calendarEventDao().all().isEmpty())
            assertTrue(runCatching {repo.save(row,emptySet(),false)}.isFailure)
        } finally {db.close()}
    }
    @Test fun civilDatesValidateLeapDaysDstGapsAndKeepWallTimeAcrossZones() {
        assertTrue(runCatching {CalendarEventDates.day("2027-02-29")}.isFailure)
        assertTrue(runCatching {CalendarEventDates.day("2026-13-01")}.isFailure)
        assertEquals("2028-02-29",CalendarEventDates.format(CalendarEventDates.day("2028-02-29")))
        assertTrue(runCatching {CalendarEventDates.validateLocal("2026-03-08",150,TimeZone.getTimeZone("America/New_York"))}.isFailure)
        CalendarEventDates.validateLocal("2026-03-08",null,TimeZone.getTimeZone("America/New_York"))
        val row=event("2026-11-01",90)
        for(zone in listOf("UTC","America/New_York","Asia/Tokyo")) CalendarEventDates.validateLocal(row.date,row.minute,TimeZone.getTimeZone(zone))
        assertEquals("2026-11-01",row.date);assertEquals(90,row.minute)
    }
    @Test fun backupRestoresEventsAndPeopleRejectsCorruptionAndReadsHistoricalFormat()=runBlocking<Unit> {
        context.deleteDatabase("arachn0de.db");val db=Arachn0deDatabase.create(context)
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,syncDirectory=BackupFixture::syncDirectory))
        try {
            db.personDao().insert(PersonEntity("roy","Roy",null))
            val row=event(minute=780);val other=event("2027-01-01")
            val repo=CalendarEventRepository(db);repo.save(row,setOf("roy"),true);repo.save(other,emptySet(),true)
            val before=backup.snapshot();val bytes=BackupJson.encode(before)
            assertEquals(37,JSONObject(String(bytes)).getInt("dataVersion"))
            val decoded=BackupJson.decode(bytes)
            assertEquals(before.calendarEvents,decoded.calendarEvents);assertEquals(before.calendarEventPeople,decoded.calendarEventPeople)
            repo.delete(row.id);repo.delete(other.id);backup.restore(decoded)
            assertEquals(before.calendarEvents,db.calendarEventDao().all());assertEquals(before.calendarEventPeople,db.calendarEventDao().people())
            val invalid=decoded.copy(calendarEventPeople=listOf(CalendarEventPersonEntity(row.id,"missing")))
            assertTrue(runCatching {backup.restore(invalid)}.isFailure)
            assertEquals(before.calendarEvents,db.calendarEventDao().all())
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER event_restore_failure BEFORE INSERT ON calendar_events BEGIN SELECT RAISE(ABORT,'injected'); END")
            assertTrue(runCatching {backup.restore(decoded)}.isFailure)
            assertEquals(before.calendarEvents,db.calendarEventDao().all());assertEquals(before.calendarEventPeople,db.calendarEventDao().people())
            assertEquals("Roy",db.personDao().get("roy")!!.name)
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER event_restore_failure")
            val json=JSONObject(String(bytes));json.getJSONArray("calendarEvents").getJSONObject(0).put("minute",1440)
            assertTrue(runCatching {BackupJson.decode(json.toString().toByteArray())}.isFailure)
            val old=BackupJson.decode(BackupJson.encode(BackupFixture.empty()))
            assertTrue(old.calendarEvents.isEmpty());backup.restore(old)
            assertTrue(db.calendarEventDao().all().isEmpty());assertTrue(db.calendarEventDao().people().isEmpty())
        } finally {db.close()}
    }
    @Test fun malformedEventsAndDstGapCannotReplaceValidData()=runBlocking<Unit> {
        context.deleteDatabase("arachn0de.db");val db=Arachn0deDatabase.create(context)
        try {
            val repo=CalendarEventRepository(db);val row=event()
            repo.save(row,emptySet(),true)
            assertTrue(runCatching {repo.save(row.copy(date="2026-03-08",minute=150),emptySet(),false,TimeZone.getTimeZone("America/New_York"))}.isFailure)
            assertEquals(listOf(row),db.calendarEventDao().all())
            val base=BackupFixture.empty().copy(calendarEvents=listOf(row))
            val invalid=listOf(base.copy(calendarEvents=listOf(row,row)),base.copy(calendarEvents=listOf(row.copy(title=" "))),
                base.copy(calendarEvents=listOf(row.copy(date="2027-02-29"))),base.copy(calendarEvents=listOf(row.copy(minute=-1))),
                base.copy(calendarEventPeople=listOf(CalendarEventPersonEntity(row.id,"missing"))),base.copy(calendarEvents=listOf(row.copy(id="invalid"))))
            invalid.forEach {assertTrue(runCatching {it.validate()}.isFailure)}
            val json=JSONObject(String(BackupJson.encode(base)));json.put("unapprovedField",true)
            assertTrue(runCatching {BackupJson.decode(json.toString().toByteArray())}.isFailure)
            assertEquals(listOf(row),db.calendarEventDao().all())
        } finally {db.close()}
    }
    @Test fun genuineRoom44MigrationPreservesCardsAndPeople()=runBlocking<Unit> {
        context.deleteDatabase("arachn0de.db")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object:SupportSQLiteOpenHelper.Callback(44) {
            override fun onCreate(db:SupportSQLiteDatabase) {}
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
        }).build())
        val sql=helper.writableDatabase
        val schema=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/44.json")!!.bufferedReader().use {it.readText()}).getJSONObject("database").getJSONArray("entities")
        for(i in 0 until schema.length()) {val entity=schema.getJSONObject(i);val table=entity.getString("tableName")
            sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
            val indices=entity.optJSONArray("indices") ?: org.json.JSONArray();for(j in 0 until indices.length()) sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
        }
        sql.execSQL("INSERT INTO projects(id,name,description,position,createdAt,updatedAt) VALUES('p','Núcleo','',0,1,1)")
        sql.execSQL("INSERT INTO persons(id,name,avatarFile,avatarZoom,avatarX,avatarY) VALUES('roy','Roy',NULL,1,0,0)")
        sql.execSQL("INSERT INTO nodes(id,projectId,title,description,isCompleted,position,createdAt,updatedAt,purpose) VALUES('t','p','Tarea','',0,0,1,1,'ACTION')")
        sql.execSQL("INSERT INTO node_person VALUES('t','roy')");helper.close()
        val db=Arachn0deDatabase.create(context)
        try {
            assertEquals("Tarea",db.nodeDao().getById("t")!!.title)
            assertEquals(listOf("roy"),db.personDao().assignmentIds("t"));assertTrue(db.calendarEventDao().all().isEmpty())
            CalendarEventRepository(db).save(event(),setOf("roy"),true)
            assertEquals(47,db.openHelper.writableDatabase.version)
        } finally {db.close()}
    }
}
