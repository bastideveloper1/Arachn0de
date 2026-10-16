package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.*
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class RecurrenceDeletionMigrationTest {
    @Test fun room43PreservesRulesReceiptsAndTasksWithVisibleDefault()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>();context.deleteDatabase("arachn0de.db")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object:SupportSQLiteOpenHelper.Callback(43) {
            override fun onCreate(db:SupportSQLiteDatabase) {}
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
        }).build())
        val sql=helper.writableDatabase
        val schema=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/43.json")!!.bufferedReader().use {it.readText()}).getJSONObject("database").getJSONArray("entities")
        for(i in 0 until schema.length()) {
            val entity=schema.getJSONObject(i);val table=entity.getString("tableName")
            sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
            val indexes=entity.optJSONArray("indices") ?: org.json.JSONArray()
            for(j in 0 until indexes.length()) sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
        }
        sql.execSQL("INSERT INTO projects (id,name,description,position,createdAt,updatedAt) VALUES ('p','Proyecto','',0,1,1)")
        sql.execSQL("INSERT INTO recurrence_rules (id,projectId,parentId,title,description,amountMinor,currencyCode,startDay,frequency,interval,endDay,nextIndex,status,zoneId,dueMinute,startOffsetMillis,priority) VALUES ('rule','p',NULL,'Plan','Historia',15000,'CLP',20000,'MONTHLY',1,NULL,1,'FINISHED','UTC',0,NULL,'HIGH')")
        sql.execSQL("INSERT INTO nodes (id,projectId,title,description,isCompleted,position,createdAt,updatedAt,purpose,amountMinor,currencyCode) VALUES ('receipt','p','Pago','Histórico',1,0,1,1,'ACTION',15000,'CLP')")
        sql.execSQL("INSERT INTO recurrence_occurrences VALUES ('rule',20000,'receipt')")
        helper.close()
        val db=Arachn0deDatabase.create(context)
        try {
            val rule=db.recurrenceDao().get("rule")!!
            assertFalse(rule.isDeleted);assertEquals("FINISHED",rule.status);assertEquals("Historia",rule.description)
            assertEquals(15000L,rule.amountMinor);assertEquals("HIGH",rule.priority);assertEquals(1L,rule.nextIndex)
            assertEquals(listOf(RecurrenceOccurrenceEntity("rule",20000,"receipt")),db.recurrenceDao().occurrences())
            assertTrue(db.nodeDao().getById("receipt")!!.isCompleted)
            assertEquals(15000L,db.nodeDao().getById("receipt")!!.amountMinor)
            assertEquals("Proyecto",db.projectDao().getById("p")!!.name)
        } finally {db.close()}
    }
}
