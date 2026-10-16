package com.r0ybt.arachn0de.metro

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[24,28],application=Application::class)
class MetroLearningMigrationTest {
    @Test fun genuine38MigratesPreservingEveryExistingTableAndHistoricalMetroPayload()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>()
        context.deleteDatabase("arachn0de.db")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object:SupportSQLiteOpenHelper.Callback(38) {
            override fun onCreate(db:SupportSQLiteDatabase) {}
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
        }).build())
        val sql=helper.writableDatabase
        val entities=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/38.json")!!.bufferedReader().use {it.readText()}).getJSONObject("database").getJSONArray("entities")
        val tables=(0 until entities.length()).map {entities.getJSONObject(it).getString("tableName")}
        repeat(entities.length()) {i->val row=entities.getJSONObject(i);val table=row.getString("tableName");sql.execSQL(row.getString("createSql").replace("\${TABLE_NAME}",table));val indexes=row.optJSONArray("indices") ?: JSONArray();repeat(indexes.length()) {j->sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))}}
        val net=MetroNetwork.decode(context.assets.open("metro/santiago-beta1.json").bufferedReader().use {it.readText()})
        val route=MetroPlanner.plan(net,listOf("los-heroes","tobalaba"),false,MetroRestrictions())!!
        val payload=MetroCodec.journey(MetroJourneyData(route,listOf(MetroTracking.start(route,MetroTime(100_000,1000,1)))))
        sql.execSQL("INSERT INTO metro_preferences VALUES (1,?)",arrayOf(MetroCodec.preferences(MetroPreferences(context.assets.open("metro/santiago-beta1.json").bufferedReader().use {it.readText()}))))
        sql.execSQL("INSERT INTO metro_journeys VALUES ('trip',NULL,NULL,1,?,7)",arrayOf(payload))
        fun snapshot(db:SupportSQLiteDatabase)=tables.associateWith {table->db.query("SELECT * FROM `$table` ORDER BY rowid").use {c->buildList {while(c.moveToNext()) add((0 until c.columnCount).map {if(c.isNull(it)) null else c.getString(it)})}}}
        val before=snapshot(sql);helper.close()
        repeat(2) {
            val db=Arachn0deDatabase.create(context)
            try {
                assertEquals(39,db.openHelper.writableDatabase.version)
                assertEquals(before,snapshot(db.openHelper.writableDatabase))
                assertTrue(db.metroLearningDao().events().isEmpty());assertNull(db.metroLearningDao().clock())
                db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
            } finally {db.close()}
        }
    }
}
