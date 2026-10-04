package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[24,28])
class SprintMigrationTest {
    @Test fun v15PreservesEveryRowAndStartsInNormalMode()=runBlocking {
        val context=RuntimeEnvironment.getApplication();context.deleteDatabase("arachn0de.db")
        val helper=androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object:androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(15) {
                override fun onCreate(db:androidx.sqlite.db.SupportSQLiteDatabase) {}
                override fun onUpgrade(db:androidx.sqlite.db.SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
            }).build())
        val sql=helper.writableDatabase
        val entities=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/15.json")!!.bufferedReader().use { it.readText() }).getJSONObject("database").getJSONArray("entities")
        for(i in 0 until entities.length()) {
            val row=entities.getJSONObject(i);val name=row.getString("tableName")
            sql.execSQL(row.getString("createSql").replace("\${TABLE_NAME}",name))
            val indexes=row.optJSONArray("indices") ?: org.json.JSONArray()
            for(j in 0 until indexes.length()) sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",name))
        }
        NodeInvariants.install(sql);ExplicitLayerInvariants.install(sql);ObligationInvariants.install(sql)
        sql.execSQL("INSERT INTO projects VALUES ('p','P','',0,1,2)")
        sql.execSQL("INSERT INTO nodes VALUES ('layer','p',NULL,'Layer','',0,2,10,20,100,200,'LAYER',NULL,NULL,'HIGH',NULL)")
        sql.execSQL("INSERT INTO nodes VALUES ('task','p','layer','Task','',1,4,11,21,110,210,'ACTION',15000,'CLP','HIGH','group')")
        sql.execSQL("INSERT INTO nodes VALUES ('note','p','layer','Note','',0,5,12,22,NULL,NULL,'NOTE',NULL,NULL,'NONE',NULL)")
        sql.execSQL("INSERT INTO node_events VALUES ('event','task','COMPLETED',21)")
        helper.close()
        val db=Arachn0deDatabase.create(context)
        try {
            assertEquals(16,db.openHelper.readableDatabase.version)
            val rows=db.nodeDao().getProjectNodes("p");assertTrue(rows.all { !it.sprintMode && it.workState==null })
            assertEquals(NodeEntity("task","p","layer","Task","",true,4,11,21,110,210,"ACTION",15000,"CLP","HIGH","group"),db.nodeDao().getById("task"))
            assertEquals("LAYER",db.nodeDao().getById("layer")!!.purpose);assertEquals("NOTE",db.nodeDao().getById("note")!!.purpose)
            assertEquals(NodeEventEntity("event","task","COMPLETED",21),db.nodeEventDao().all().single())
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { db.close() }
    }
}
