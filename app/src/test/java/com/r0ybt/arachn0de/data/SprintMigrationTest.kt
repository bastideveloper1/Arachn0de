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
            assertEquals(17,db.openHelper.readableDatabase.version)
            val rows=db.nodeDao().getProjectNodes("p");assertTrue(rows.all { !it.sprintMode && it.workState==null })
            assertEquals(NodeEntity("task","p","layer","Task","",true,4,11,21,110,210,"ACTION",15000,"CLP","HIGH","group"),db.nodeDao().getById("task"))
            assertEquals("LAYER",db.nodeDao().getById("layer")!!.purpose);assertEquals("NOTE",db.nodeDao().getById("note")!!.purpose)
            assertEquals(NodeEventEntity("event","task","COMPLETED",21),db.nodeEventDao().all().single())
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { db.close() }
    }
    @Test fun v16ReplacesOnlySprintGuardsAndPreservesAllTables()=runBlocking {
        val context=RuntimeEnvironment.getApplication();context.deleteDatabase("arachn0de.db")
        val helper=androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object:androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(16) {
                override fun onCreate(db:androidx.sqlite.db.SupportSQLiteDatabase) {}
                override fun onUpgrade(db:androidx.sqlite.db.SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
            }).build())
        val sql=helper.writableDatabase
        val entities=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/16.json")!!.bufferedReader().use { it.readText() }).getJSONObject("database").getJSONArray("entities")
        val tables=(0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }
        for(i in 0 until entities.length()) {
            val row=entities.getJSONObject(i);val name=row.getString("tableName")
            sql.execSQL(row.getString("createSql").replace("\${TABLE_NAME}",name))
            val indexes=row.optJSONArray("indices") ?: org.json.JSONArray()
            for(j in 0 until indexes.length()) sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",name))
        }
        NodeInvariants.install(sql);ExplicitLayerInvariants.install(sql);ObligationInvariants.install(sql)
        SprintInvariants.install(sql,requireMatchingCompletion=true)
        sql.execSQL("INSERT INTO projects VALUES ('p','P','',0,1,2)")
        sql.execSQL("INSERT INTO nodes VALUES ('layer','p',NULL,'Sprint','',0,0,10,20,100,200,'LAYER',NULL,NULL,'HIGH',NULL,1,NULL)")
        for ((index,state) in com.r0ybt.arachn0de.domain.model.WorkState.entries.withIndex()) {
            sql.execSQL("INSERT INTO nodes VALUES (?, 'p','layer',?,'description',?, ?,11,21,110,210,'ACTION',15000,'CLP','HIGH','group',0,?)",arrayOf<Any>(state.name,state.label,if(state.completed) 1 else 0,index,state.name))
        }
        sql.execSQL("INSERT INTO node_events VALUES ('event','DONE','COMPLETED',21)")
        fun snapshot(database:androidx.sqlite.db.SupportSQLiteDatabase)=tables.associateWith { table ->
            database.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
                buildList { while(cursor.moveToNext()) add((0 until cursor.columnCount).map { if(cursor.isNull(it)) null else cursor.getString(it) }) }
            }
        }
        val before=snapshot(sql)
        assertTrue(runCatching { sql.execSQL("UPDATE nodes SET isCompleted=1 WHERE id='DOING'") }.isFailure)
        helper.close()
        val db=Arachn0deDatabase.create(context)
        try {
            val migrated=db.openHelper.writableDatabase
            assertEquals(17,migrated.version);assertEquals(before,snapshot(migrated))
            for(state in com.r0ybt.arachn0de.domain.model.WorkState.entries) {
                for(completed in listOf(0,1)) migrated.execSQL("UPDATE nodes SET isCompleted=? WHERE id=?",arrayOf<Any>(completed,state.name))
            }
            migrated.execSQL("UPDATE nodes SET workState='UNPLANNED' WHERE id='VALIDATED'")
            assertTrue(db.nodeDao().getById("VALIDATED")!!.isCompleted)
            assertTrue(runCatching { migrated.execSQL("UPDATE nodes SET workState='UNKNOWN' WHERE id='DOING'") }.isFailure)
            migrated.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { db.close() }
    }

}
