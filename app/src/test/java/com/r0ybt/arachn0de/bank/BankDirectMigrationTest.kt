package com.r0ybt.arachn0de.bank

import android.app.Application
import androidx.sqlite.db.*
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class BankDirectMigrationTest {
    @Test fun real54SchemaPreservesAllRowsIncludingSelfCorrectionsAndHistoricalStates() {
        val app=ApplicationProvider.getApplicationContext<Application>();app.deleteDatabase("arachn0de.db")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(app).name("arachn0de.db")
            .callback(object:SupportSQLiteOpenHelper.Callback(54) {
                override fun onCreate(db:SupportSQLiteDatabase) {}
                override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("unexpected")
                override fun onConfigure(db:SupportSQLiteDatabase) {db.setForeignKeyConstraintsEnabled(true)}
            }).build())
        val sql=helper.writableDatabase
        val entities=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/54.json")!!.bufferedReader().use {it.readText()})
            .getJSONObject("database").getJSONArray("entities")
        val tables=mutableListOf<String>()
        repeat(entities.length()) {i->val e=entities.getJSONObject(i);val t=e.getString("tableName");tables.add(t)
            sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",t))
            val indexes=e.optJSONArray("indices") ?: org.json.JSONArray();repeat(indexes.length()) {j->sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",t))}
        }
        sql.execSQL("INSERT INTO projects(id,name,description,position,createdAt,updatedAt) VALUES('bank','Banco','',0,1,1),('tasks','Tareas','',1,1,1)")
        sql.execSQL("INSERT INTO bank_roots VALUES('bank')")
        sql.execSQL("INSERT INTO persons(id,name) VALUES('p','Persona')")
        sql.execSQL("INSERT INTO nodes(id,projectId,parentId,title,description,isCompleted,position,createdAt,updatedAt,purpose,priority,sprintMode) VALUES('account','bank',NULL,'Cuenta','',0,0,1,1,'LAYER','NONE',0)")
        sql.execSQL("INSERT INTO bank_accounts VALUES('account','bank','p')")
        sql.execSQL("INSERT INTO nodes(id,projectId,title,description,isCompleted,position,createdAt,updatedAt,purpose,priority,sprintMode,amountMinor,currencyCode) VALUES('debt','tasks','Deuda','',1,0,1,1,'ACTION','NONE',0,10000,'CLP')")
        sql.execSQL("INSERT INTO bank_operations VALUES('old','bank','account','DIRECT',2000,20000,'Histórico',NULL,NULL,'p','p',NULL,1,1)")
        sql.execSQL("INSERT INTO bank_operations VALUES('correction','bank','account','DIRECT',3000,20000,'Corrección',NULL,NULL,'p','p','old',0,2)")
        sql.execSQL("INSERT INTO bank_allocations VALUES('a','old','debt',2000,'p','Persona','p','Persona','Deuda',NULL,NULL,1,2),('b','correction','debt',3000,'p','Persona','p','Persona','Deuda',NULL,NULL,2,2)")
        sql.execSQL("CREATE TRIGGER preserved_financial_trigger AFTER UPDATE OF concept ON bank_operations BEGIN SELECT 1; END")
        fun snapshot(db:SupportSQLiteDatabase)=tables.associateWith {t->db.query("SELECT * FROM `$t` ORDER BY rowid").use {c->buildList {
            while(c.moveToNext())add((0 until c.columnCount).map {if(c.isNull(it))null else c.getString(it)})
        }}}
        val before=snapshot(sql);helper.close()
        val failing=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(app).name("arachn0de.db")
            .callback(object:SupportSQLiteOpenHelper.Callback(55) {
                override fun onCreate(db:SupportSQLiteDatabase)=error("unexpected")
                override fun onConfigure(db:SupportSQLiteDatabase) {db.setForeignKeyConstraintsEnabled(true)}
                override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int) {
                    BankMigration54To55.migrate(db)
                    error("Simulated failure before migration commit")
                }
            }).build())
        assertTrue(runCatching {failing.writableDatabase}.isFailure);failing.close()
        assertEquals(54,helper.writableDatabase.version);assertEquals(before,snapshot(helper.writableDatabase));helper.close()
        val room=Arachn0deDatabase.create(app)
        try {
            val migrated=room.openHelper.writableDatabase
            assertEquals(55,migrated.version);assertEquals(before,snapshot(migrated))
            migrated.query("SELECT name FROM sqlite_master WHERE type='trigger' AND name='preserved_financial_trigger'").use {assertTrue(it.moveToFirst())}
            migrated.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
            migrated.execSQL("INSERT INTO bank_operations VALUES('new','bank',NULL,'DIRECT',100,20001,'Sin cuenta',NULL,NULL,NULL,NULL,NULL,0,3)")
            assertTrue(runCatching {migrated.execSQL("INSERT INTO bank_operations VALUES('invalid','bank',NULL,'PAYMENT',100,20001,'Inválida',NULL,NULL,NULL,NULL,NULL,0,3)")}.isFailure)
        } finally {room.close()}
    }
}
