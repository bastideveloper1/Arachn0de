package com.r0ybt.arachn0de.metro

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[24,28],application=Application::class)
class MetroMigrationTest {
    @Test fun genuineV23MigratesWithoutChangingAnyExistingRowOrGame()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>();context.deleteDatabase("arachn0de.db")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object:SupportSQLiteOpenHelper.Callback(23) {
            override fun onCreate(db:SupportSQLiteDatabase) {}
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
        }).build())
        val sql=helper.writableDatabase
        val entities=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/23.json")!!.bufferedReader().use {it.readText()}).getJSONObject("database").getJSONArray("entities")
        val tables=(0 until entities.length()).map {entities.getJSONObject(it).getString("tableName")}
        repeat(entities.length()) {i->val row=entities.getJSONObject(i);val table=row.getString("tableName");sql.execSQL(row.getString("createSql").replace("\${TABLE_NAME}",table));val indexes=row.optJSONArray("indices") ?: org.json.JSONArray();repeat(indexes.length()) {j->sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))}}
        sql.execSQL("INSERT INTO projects VALUES ('p','Project','Original',0,1,2)")
        sql.execSQL("INSERT INTO nodes VALUES ('n','p',NULL,'Task','Original',0,0,1,2,NULL,NULL,'ACTION',NULL,NULL,'NONE',NULL,0,NULL)")
        sql.execSQL("INSERT INTO game_state VALUES (1,'original-game-payload',12)")
        fun snapshot(db:SupportSQLiteDatabase)=tables.associateWith {table->db.query("SELECT * FROM `$table` ORDER BY rowid").use {c->buildList {while(c.moveToNext()) add((0 until c.columnCount).map {if(c.isNull(it)) null else c.getString(it)})}}}
        val before=snapshot(sql);helper.close();val db=Arachn0deDatabase.create(context)
        try {assertEquals(26,db.openHelper.writableDatabase.version);assertEquals(before,snapshot(db.openHelper.writableDatabase));assertTrue(db.metroDao().journeys().isEmpty());assertNull(db.metroDao().preferences())} finally {db.close()}
    }
}
