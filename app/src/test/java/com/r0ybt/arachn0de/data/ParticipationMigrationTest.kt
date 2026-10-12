package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class) @Config(sdk=[24,28])
class ParticipationMigrationTest {
    @Test fun v31IsPreservedAndRoomValidatesAdditiveSchema()=runBlocking {
        val context=RuntimeEnvironment.getApplication();context.deleteDatabase("arachn0de.db")
        val helper=androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object:androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(31) {
            override fun onCreate(db:androidx.sqlite.db.SupportSQLiteDatabase) {}
            override fun onUpgrade(db:androidx.sqlite.db.SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
        }).build())
        val sql=helper.writableDatabase
        val entities=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/31.json")!!.bufferedReader().use {it.readText()}).getJSONObject("database").getJSONArray("entities")
        val tables=(0 until entities.length()).map {entities.getJSONObject(it).getString("tableName")}
        for(i in 0 until entities.length()) {
            val entity=entities.getJSONObject(i);val name=entity.getString("tableName")
            sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",name))
            val indexes=entity.optJSONArray("indices") ?: org.json.JSONArray()
            for(j in 0 until indexes.length()) sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",name))
        }
        sql.execSQL("INSERT INTO projects VALUES ('p','Project','Description',0,1,2)")
        sql.execSQL("INSERT INTO nodes VALUES ('s','p',NULL,'Sprint','',0,0,1,2,NULL,NULL,'LAYER',NULL,NULL,'NONE',NULL,1,NULL)")
        sql.execSQL("INSERT INTO nodes VALUES ('t','p','s','Task','Full text',1,0,1,2,NULL,NULL,'ACTION',NULL,NULL,'NONE',NULL,0,'VALIDATED')")
        sql.execSQL("INSERT INTO persons VALUES ('person','Roy',NULL,1,0,0)")
        sql.execSQL("INSERT INTO node_person VALUES ('t','person')")
        fun snapshot(db:androidx.sqlite.db.SupportSQLiteDatabase)=tables.associateWith {table->db.query("SELECT * FROM `$table` ORDER BY rowid").use {c->buildList {while(c.moveToNext()) add((0 until c.columnCount).map {if(c.isNull(it)) null else c.getString(it)})}}}
        sql.execSQL("INSERT INTO conversion_roots VALUES ('stable','p',NULL,'p','p',0,0,NULL,NULL,'NONE',NULL,0)")
        sql.execSQL("INSERT INTO management_contexts VALUES ('stable',0,'HYBRID')")
        sql.execSQL("INSERT INTO project_charters VALUES ('stable','Partial purpose','','','','')")
        sql.execSQL("INSERT INTO management_objectives VALUES ('goal','stable','Goal','Full description',3)")
        sql.execSQL("INSERT INTO acceptance_criteria VALUES ('criterion','goal','Criterion',7,1,123456789)")
        sql.execSQL("INSERT INTO management_roles VALUES ('s','stable','STORY')")
        sql.execSQL("INSERT INTO backlog_entries VALUES ('t','stable',0)")
        val before=snapshot(sql);helper.close()
        val db=Arachn0deDatabase.create(context)
        try {
            assertEquals(37,db.openHelper.writableDatabase.version)
            assertEquals(before,snapshot(db.openHelper.readableDatabase))
            assertEquals(listOf(ManagementContextEntity("stable",false,"HYBRID")),db.managementContextDao().all())
            assertTrue(db.participationDao().participants().isEmpty());assertTrue(db.participationDao().roles().isEmpty());assertTrue(db.participationDao().objectives().isEmpty());assertTrue(db.participationDao().nodes().isEmpty())
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
        } finally {db.close()}
    }
}
