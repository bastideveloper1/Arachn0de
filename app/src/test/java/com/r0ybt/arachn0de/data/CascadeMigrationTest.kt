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
class CascadeMigrationTest {
    @Test fun genuineRoom36PreservesEveryHistoricalTableAndAddsOnlyEmptyRelations()=runBlocking {
        val app=RuntimeEnvironment.getApplication();app.deleteDatabase("arachn0de.db")
        val schema=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/36.json")!!.bufferedReader().use {it.readText()}).getJSONObject("database")
        assertEquals(36,schema.getInt("version"))
        val helper=androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(app).name("arachn0de.db").callback(object:androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(36) {
            override fun onCreate(db:androidx.sqlite.db.SupportSQLiteDatabase) {}
            override fun onUpgrade(db:androidx.sqlite.db.SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
        }).build())
        val sql=helper.writableDatabase;val entities=schema.getJSONArray("entities")
        val tables=(0 until entities.length()).map {entities.getJSONObject(it).getString("tableName")}
        for(i in 0 until entities.length()) {
            val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
            sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
            val indices=entity.optJSONArray("indices") ?: org.json.JSONArray()
            for(j in 0 until indices.length()) sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
        }
        sql.execSQL("INSERT INTO projects VALUES ('p','Real historical project','Description',0,1,2)")
        sql.execSQL("INSERT INTO nodes VALUES ('s','p',NULL,'Sprint','',0,0,1,2,NULL,NULL,'LAYER',NULL,NULL,'NONE',NULL,1,NULL)")
        sql.execSQL("INSERT INTO nodes VALUES ('story','p',NULL,'Story','Content',0,1,1,2,NULL,NULL,'LAYER',NULL,NULL,'NONE',NULL,0,NULL)")
        sql.execSQL("INSERT INTO nodes VALUES ('t','p','story','Task','',0,0,1,2,NULL,NULL,'ACTION',NULL,NULL,'NONE',NULL,0,'DOING')")
        sql.execSQL("INSERT INTO persons VALUES ('person','Roy',NULL,3,0.4,-0.2)")
        sql.execSQL("INSERT INTO conversion_roots VALUES ('stable','p',NULL,'p','p',0,0,NULL,NULL,'NONE',NULL,0)")
        sql.execSQL("INSERT INTO management_contexts VALUES ('stable',1,'HYBRID')")
        sql.execSQL("INSERT INTO management_roles VALUES ('story','stable','STORY')")
        sql.execSQL("INSERT INTO sprint_links VALUES ('t','s')")
        sql.execSQL("INSERT INTO project_charters VALUES ('stable','Historic purpose','Historic scope','','','')")
        sql.execSQL("INSERT INTO management_objectives VALUES ('goal','stable','Goal','Original',0)")
        sql.execSQL("INSERT INTO acceptance_criteria VALUES ('criterion','goal','Checked',0,1,123)")
        fun snapshot(db:androidx.sqlite.db.SupportSQLiteDatabase)=tables.associateWith {table->db.query("SELECT * FROM '$table' ORDER BY rowid").use {c->buildList {while(c.moveToNext()) add((0 until c.columnCount).map {if(c.isNull(it)) null else c.getString(it)})}}}
        val before=snapshot(sql);helper.close()
        val db=Arachn0deDatabase.create(app)
        try {
            assertEquals(37,db.openHelper.writableDatabase.version);assertEquals(before,snapshot(db.openHelper.readableDatabase))
            assertTrue(db.managementLayerDao().all().isEmpty());assertTrue(db.cascadeRelationDao().stories().isEmpty())
            com.r0ybt.arachn0de.data.repository.ManagementCascadeRepository(db).ensure("stable")
            assertEquals("DOING",db.nodeDao().getById("t")!!.workState)
            assertEquals("story",db.nodeDao().getById("t")!!.parentId)
            assertEquals(listOf(SprintLinkEntity("t","s")),db.sprintLinkDao().all())
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
        } finally {db.close()}
    }
}
