package com.r0ybt.arachn0de.security

import android.app.Application
import androidx.sqlite.db.*
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class SecurityMigrationTest {
    @Test fun genuineV25RetainsAllHistoricalTablesAndAddsOnlyPrivatePreferences() {
        val context=ApplicationProvider.getApplicationContext<Application>();context.deleteDatabase("arachn0de.db")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object:SupportSQLiteOpenHelper.Callback(25) {
            override fun onCreate(db:SupportSQLiteDatabase) {}
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("unexpected")
        }).build())
        val sql=helper.writableDatabase
        val entities=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/25.json")!!.bufferedReader().use {it.readText()}).getJSONObject("database").getJSONArray("entities")
        val columns=mutableMapOf<String,List<String>>()
        repeat(entities.length()) {i->val row=entities.getJSONObject(i);val table=row.getString("tableName");sql.execSQL(row.getString("createSql").replace("\${TABLE_NAME}",table));val indexes=row.optJSONArray("indices") ?: org.json.JSONArray();repeat(indexes.length()) {j->sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))};columns[table]=sql.query("SELECT * FROM `$table`").use {it.columnNames.toList()}}
        sql.execSQL("INSERT INTO projects VALUES ('p','Principal','Conservar',0,1,2)")
        sql.execSQL("INSERT INTO nodes VALUES ('n','p',NULL,'Task','Conservar',0,0,1,2,NULL,NULL,'ACTION',NULL,NULL,'NONE',NULL,0,NULL)")
        sql.execSQL("INSERT INTO persons VALUES ('person','Name',NULL,1,0,0)")
        sql.execSQL("INSERT INTO node_person VALUES ('n','person')")
        sql.execSQL("INSERT INTO technologies VALUES ('t','Tech',NULL)")
        sql.execSQL("INSERT INTO node_technologies VALUES ('n','t',0)")
        sql.execSQL("INSERT INTO game_state VALUES (1,'original',12)")
        fun snapshot(db:SupportSQLiteDatabase)=columns.mapValues {(table,fields)->db.query("SELECT ${fields.joinToString {"`$it`"}} FROM `$table` ORDER BY rowid").use {c->buildList {while(c.moveToNext()) add((0 until c.columnCount).map {if(c.isNull(it)) null else c.getString(it)})}}}
        val before=snapshot(sql);helper.close();val db=Arachn0deDatabase.create(context)
        try {
            val migrated=db.openHelper.writableDatabase
            assertEquals(26,migrated.version);assertEquals(before,snapshot(migrated))
            migrated.query("SELECT * FROM private_preferences").use {assertFalse(it.moveToFirst())}
            migrated.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
        } finally {db.close()}
    }
}
