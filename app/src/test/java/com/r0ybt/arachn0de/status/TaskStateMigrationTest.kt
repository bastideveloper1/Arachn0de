package com.r0ybt.arachn0de.status

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.*
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.r0ybt.arachn0de.data.local.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class TaskStateMigrationTest {
    @Test fun room50PreservesExistingRowsAndVerificationWithInheritedDefaults() {
        val context=ApplicationProvider.getApplicationContext<Application>();context.deleteDatabase("arachn0de.db")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object:SupportSQLiteOpenHelper.Callback(50) {
            override fun onConfigure(db:SupportSQLiteDatabase) {db.setForeignKeyConstraintsEnabled(true)}
            override fun onCreate(db:SupportSQLiteDatabase) {}
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
        }).build())
        val sql=helper.writableDatabase
        val schema=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/50.json")!!.bufferedReader().use {it.readText()}).getJSONObject("database").getJSONArray("entities")
        val columns=mutableMapOf<String,List<String>>()
        for(i in 0 until schema.length()) {
            val entity=schema.getJSONObject(i);val table=entity.getString("tableName")
            sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
            val indexes=entity.optJSONArray("indices") ?: org.json.JSONArray()
            for(j in 0 until indexes.length()) sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
            val fields=entity.getJSONArray("fields");columns[table]=(0 until fields.length()).map {fields.getJSONObject(it).getString("columnName")}
        }
        sql.execSQL("INSERT INTO projects(id,name,description,position,createdAt,updatedAt,isPinned) VALUES ('p','Original','Conservar',4,1,2,1)")
        sql.execSQL("INSERT INTO nodes(id,projectId,title,description,isCompleted,position,createdAt,updatedAt,purpose,sprintMode) VALUES ('layer','p','Capa','',0,0,1,2,'LAYER',1)")
        sql.execSQL("INSERT INTO nodes(id,projectId,parentId,title,description,isCompleted,position,createdAt,updatedAt,purpose,workState) VALUES ('task','p','layer','Tarea','Contenido',1,3,11,22,'ACTION','VALIDATED')")
        sql.execSQL("INSERT INTO nodes(id,projectId,title,description,isCompleted,position,createdAt,updatedAt,purpose) VALUES ('draft',NULL,'Borrador','',0,0,1,2,'NOTE')")
        sql.execSQL("INSERT INTO persons(id,name,avatarFile,avatarZoom,avatarX,avatarY) VALUES ('person','Ana',NULL,1,0,0)")
        sql.execSQL("INSERT INTO node_person VALUES ('task','person')")
        sql.execSQL("INSERT INTO node_events VALUES ('event','task','COMPLETED',22)")
        fun rows(db:SupportSQLiteDatabase,table:String)=db.query("SELECT ${columns.getValue(table).joinToString(",") {"`$it`"}} FROM `$table` ORDER BY 1,2").use {c->buildList {while(c.moveToNext()) add((0 until c.columnCount).map {if(c.isNull(it)) null else c.getString(it)})}}
        sql.execSQL("UPDATE projects SET verificationEnabled=1 WHERE id='p'")
        sql.execSQL("INSERT INTO nodes(id,projectId,title,description,isCompleted,position,createdAt,updatedAt,purpose,awaitingVerification) VALUES ('verify','p','Verificar','Conservar',0,8,3,4,'ACTION',1)")
        val before=columns.keys.associateWith {rows(sql,it)}
        helper.close()
        val db=Arachn0deDatabase.create(context)
        try {
            assertEquals(51,db.openHelper.writableDatabase.version)
            columns.keys.forEach {assertEquals(it,before[it],rows(db.openHelper.writableDatabase,it))}
            db.openHelper.writableDatabase.query("SELECT verificationEnabled FROM projects").use {it.moveToFirst();assertEquals(1,it.getInt(0))}
            db.openHelper.writableDatabase.query("SELECT awaitingVerification,taskStateCount FROM nodes WHERE id='verify'").use {assertTrue(it.moveToFirst());assertEquals(1,it.getInt(0));assertTrue(it.isNull(1))}
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
        } finally {db.close()}
    }
}
