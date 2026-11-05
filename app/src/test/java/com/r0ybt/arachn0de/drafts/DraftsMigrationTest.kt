package com.r0ybt.arachn0de.drafts

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.*
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.NodeRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class DraftsMigrationTest {
    @Test fun room48MigrationPreservesEveryTableAndAllCascadeAndSetNullReferences()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>();context.deleteDatabase("arachn0de.db")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object:SupportSQLiteOpenHelper.Callback(48) {
            override fun onConfigure(db:SupportSQLiteDatabase) {db.setForeignKeyConstraintsEnabled(true)}
            override fun onCreate(db:SupportSQLiteDatabase) {}
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
        }).build())
        val sql=helper.writableDatabase
        val schema=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/48.json")!!.bufferedReader().use {it.readText()}).getJSONObject("database").getJSONArray("entities")
        val tables=mutableListOf<String>()
        for(i in 0 until schema.length()) {
            val entity=schema.getJSONObject(i);val table=entity.getString("tableName");tables+=table
            sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
            val indexes=entity.optJSONArray("indices") ?: org.json.JSONArray()
            for(j in 0 until indexes.length()) sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
        }
        NodeInvariants.install(sql);ExplicitLayerInvariants.install(sql);SprintInvariants.install(sql)
        sql.execSQL("INSERT INTO projects(id,name,description,position,createdAt,updatedAt,isPinned) VALUES ('p','Original','Conservar',4,1,2,1)")
        sql.execSQL("INSERT INTO nodes(id,projectId,parentId,title,description,isCompleted,position,createdAt,updatedAt,purpose) VALUES ('layer','p',NULL,'Capa','',0,0,1,2,'LAYER')")
        sql.execSQL("INSERT INTO nodes(id,projectId,parentId,title,description,isCompleted,position,createdAt,updatedAt,purpose) VALUES ('task','p','layer','Tarea','Contenido',1,3,11,22,'ACTION')")
        sql.execSQL("INSERT INTO persons(id,name,avatarFile,avatarZoom,avatarX,avatarY) VALUES ('person','Ana',NULL,1,0,0)")
        sql.execSQL("INSERT INTO node_person VALUES ('task','person')")
        sql.execSQL("INSERT INTO node_events VALUES ('event','task','CREATED',11)")
        sql.execSQL("INSERT INTO tags VALUES ('tag','Etiqueta','etiqueta')")
        sql.execSQL("INSERT INTO node_tag VALUES ('task','tag')")
        // These dependants disappear via CASCADE or lose their node via SET NULL on DROP.
        sql.execSQL("INSERT INTO project_photos(id,projectId,nodeId,file,photoZoom,photoX,photoY) VALUES ('photo',NULL,'layer','original.png',1,0,0)")
        sql.execSQL("INSERT INTO private_preferences VALUES ('retained','payload')")
        sql.execSQL("INSERT INTO nodes(id,projectId,parentId,title,description,isCompleted,position,createdAt,updatedAt,purpose,sprintMode) VALUES ('sprint','p',NULL,'Sprint','',0,1,1,2,'LAYER',1)")
        sql.execSQL("INSERT INTO nodes(id,projectId,parentId,title,description,isCompleted,position,createdAt,updatedAt,purpose,workState) VALUES ('sprint-task','p','sprint','Compromiso','',1,1,1,2,'ACTION','DONE')")
        sql.execSQL("INSERT INTO conversion_roots VALUES ('context','p',NULL,'p','identity',4,0,NULL,NULL,'NONE',NULL,0)")
        sql.execSQL("INSERT INTO management_contexts VALUES ('context',1,'AGILE')")
        sql.execSQL("INSERT INTO iteration_plans VALUES ('plan','context','sprint','Objetivo',NULL,NULL,'Notas','CLOSED')")
        sql.execSQL("INSERT INTO iteration_cycles VALUES ('cycle','plan',1,1,2)")
        sql.execSQL("INSERT INTO iteration_commitments VALUES ('cycle','sprint-task',1,1)")
        sql.execSQL("INSERT INTO iteration_closures VALUES ('cycle','sprint-task',1,'DONE',1)")
        val originalColumns=tables.associateWith {table->sql.query("PRAGMA table_info(`$table`)").use {c->buildList {while(c.moveToNext())add(c.getString(1))}}}
        fun rows(db:SupportSQLiteDatabase,table:String):List<List<String?>> = db.query("SELECT "+originalColumns.getValue(table).joinToString(",") {"`$it`"}+" FROM `$table` ORDER BY 1,2").use {c->buildList {
            while(c.moveToNext()) add((0 until c.columnCount).filter {c.getColumnName(it) !in setOf("verificationEnabled","awaitingVerification")}.map {if(c.isNull(it)) null else c.getString(it)})
        }}
        val before=tables.associateWith {rows(sql,it)}
        // Simulate interruption after the rebuild but before Room commits its migration.
        sql.beginTransaction()
        try {DraftsMigration48To49.migrate(sql)} finally {sql.endTransaction()}
        tables.forEach {assertEquals("rollback:$it",before[it],rows(sql,it))}
        sql.query("PRAGMA table_info(nodes)").use {c->while(c.moveToNext()) if(c.getString(1)=="projectId") assertEquals(1,c.getInt(3))}
        helper.close()
        val db=Arachn0deDatabase.create(context)
        try {
            assertEquals(54, db.openHelper.writableDatabase.version)
            tables.forEach {assertEquals(it,before[it],rows(db.openHelper.writableDatabase,it))}
            val draft=NodeRepository(db).createNode(null,null,"Nueva nota",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.NOTE)
            assertNull(db.nodeDao().getById(draft.id)!!.projectId)
            assertTrue(runCatching {db.openHelper.writableDatabase.execSQL("UPDATE nodes SET parentId='task' WHERE id='layer'")}.isFailure)
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
        } finally {db.close();context.deleteDatabase("arachn0de.db")}
    }
}
