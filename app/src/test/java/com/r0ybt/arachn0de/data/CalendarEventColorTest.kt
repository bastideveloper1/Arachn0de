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
class CalendarEventColorTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private fun event()=CalendarEventEntity(UUID.randomUUID().toString(),"Color","2026-12-31",null,"", "BLUE")
    @Test fun colorsRoundTripRollbackInvalidAndHistoricalEventsDefaultToGrey()=runBlocking<Unit> {
        context.deleteDatabase("arachn0de.db");val db=Arachn0deDatabase.create(context)
        try {
            val row=event();val repo=CalendarEventRepository(db);repo.save(row,emptySet(),true)
            val backup=BackupRepository(db,context,BackupAvatarFiles(context,syncDirectory=BackupFixture::syncDirectory))
            val data=backup.snapshot();val bytes=BackupJson.encode(data)
            assertEquals(38,JSONObject(String(bytes)).getInt("dataVersion"))
            repo.delete(row.id);backup.restore(BackupJson.decode(bytes))
            assertEquals(row,db.calendarEventDao().all().single())
            val bad=JSONObject(String(bytes));bad.getJSONArray("calendarEvents").getJSONObject(0).put("color","INVALID")
            assertTrue(runCatching {BackupJson.decode(bad.toString().toByteArray())}.isFailure)
            assertTrue(runCatching {backup.restore(data.copy(calendarEvents=listOf(row.copy(color="INVALID"))))}.isFailure)
            assertEquals(row,db.calendarEventDao().all().single())
            val old=JSONObject(String(bytes));old.put("dataVersion",37);old.getJSONArray("calendarEvents").getJSONObject(0).remove("color")
            backup.restore(BackupJson.decode(old.toString().toByteArray()))
            assertEquals("LIGHT_GREY",db.calendarEventDao().all().single().color)
            assertEquals(37,JSONObject(String(BackupJson.encode(backup.snapshot()))).getInt("dataVersion"))
        } finally {db.close()}
    }
    @Test fun genuineRoom45AddsDefaultColorWithoutLosingEventsOrResponsibilities()=runBlocking<Unit> {
        context.deleteDatabase("arachn0de.db")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object:SupportSQLiteOpenHelper.Callback(45) {
            override fun onCreate(db:SupportSQLiteDatabase) {}
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
        }).build())
        val sql=helper.writableDatabase
        val schema=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/45.json")!!.bufferedReader().use {it.readText()}).getJSONObject("database").getJSONArray("entities")
        for(i in 0 until schema.length()) {val entity=schema.getJSONObject(i);val table=entity.getString("tableName")
            sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
            val indices=entity.optJSONArray("indices") ?: org.json.JSONArray();for(j in 0 until indices.length()) sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
        }
        sql.execSQL("INSERT INTO projects(id,name,description,position,createdAt,updatedAt) VALUES('p','Núcleo','',0,1,1)")
        sql.execSQL("INSERT INTO persons(id,name,avatarFile,avatarZoom,avatarX,avatarY) VALUES('roy','Roy',NULL,1,0,0)")
        sql.execSQL("INSERT INTO nodes(id,projectId,title,description,isCompleted,position,createdAt,updatedAt,purpose) VALUES('t','p','Tarea','',0,0,1,1,'ACTION')")
        sql.execSQL("INSERT INTO node_person VALUES('t','roy')")
        sql.execSQL("INSERT INTO calendar_events VALUES('12345678-1234-1234-1234-123456789abc','Consulta','2026-12-31',600,'Conservar')")
        sql.execSQL("INSERT INTO calendar_event_people VALUES('12345678-1234-1234-1234-123456789abc','roy')");helper.close()
        val db=Arachn0deDatabase.create(context)
        try {
            assertEquals("Tarea",db.nodeDao().getById("t")!!.title)
            assertEquals(listOf("roy"),db.personDao().assignmentIds("t"));assertEquals("LIGHT_GREY",db.calendarEventDao().all().single().color)
            assertEquals("Conservar",db.calendarEventDao().all().single().description)
            assertEquals("roy",db.calendarEventDao().people().single().personId)
            CalendarEventRepository(db).save(event(),setOf("roy"),true)
            assertEquals(47,db.openHelper.writableDatabase.version)
        } finally {db.close()}
    }
}
