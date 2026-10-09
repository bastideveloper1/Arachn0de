package com.r0ybt.arachn0de.templates

import android.app.Application
import androidx.sqlite.db.*
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[24,28],application=Application::class)
class SavedTemplatesMigrationTest {
    @Test fun genuineV24PreservesEveryColumnAndAssignsPreviousCatalogOrder()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>();context.deleteDatabase("arachn0de.db")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object:SupportSQLiteOpenHelper.Callback(24) {
            override fun onCreate(db:SupportSQLiteDatabase) {}
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("unexpected")
        }).build())
        val sql=helper.writableDatabase
        val entities=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/24.json")!!.bufferedReader().use {it.readText()}).getJSONObject("database").getJSONArray("entities")
        val columns=mutableMapOf<String,List<String>>()
        repeat(entities.length()) {i->val row=entities.getJSONObject(i);val table=row.getString("tableName");sql.execSQL(row.getString("createSql").replace("\${TABLE_NAME}",table));val indexes=row.optJSONArray("indices") ?: org.json.JSONArray();repeat(indexes.length()) {j->sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))};columns[table]=sql.query("SELECT * FROM `$table`").use {it.columnNames.toList()}}
        sql.execSQL("INSERT INTO projects VALUES ('p','Project','Original',0,1,2)")
        sql.execSQL("INSERT INTO nodes VALUES ('n','p',NULL,'Task','Original',0,0,1,2,NULL,NULL,'ACTION',NULL,NULL,'NONE',NULL,0,NULL)")
        sql.execSQL("INSERT INTO technologies VALUES ('a','Zulu',NULL),('b','alpha',NULL),('c','ALPHA',NULL)")
        sql.execSQL("INSERT INTO node_technologies VALUES ('n','a'),('n','b'),('n','c')")
        sql.execSQL("INSERT INTO project_technologies VALUES ('p','a'),('p','b')")
        sql.execSQL("INSERT INTO persons VALUES ('person','Name',NULL,1,0,0)")
        sql.execSQL("INSERT INTO node_person VALUES ('n','person')")
        sql.execSQL("INSERT INTO game_state VALUES (1,'original',12)")
        fun snapshot(db:SupportSQLiteDatabase)=columns.mapValues {(table,fields)->db.query("SELECT ${fields.joinToString {"`$it`"}} FROM `$table` ORDER BY rowid").use {c->buildList {while(c.moveToNext()) add((0 until c.columnCount).map {if(c.isNull(it)) null else c.getString(it)})}}}
        val before=snapshot(sql);helper.close();val db=Arachn0deDatabase.create(context)
        try {assertEquals(27,db.openHelper.writableDatabase.version);assertEquals(before,snapshot(db.openHelper.writableDatabase));assertEquals(listOf("b","c","a"),db.technologyDao().nodes().map {it.technologyId});assertEquals(listOf(0,1,2),db.technologyDao().nodes().map {it.position});assertEquals(listOf("b","a"),db.technologyDao().projects().map {it.technologyId});assertTrue(db.savedTemplateDao().all().isEmpty())} finally {db.close()}
    }
}
