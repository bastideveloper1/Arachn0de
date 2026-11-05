package com.r0ybt.arachn0de.bank

import android.content.Context
import android.content.ContextWrapper
import androidx.sqlite.db.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.domain.model.Obligation
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Requires a device: exercises the real SQLCipher engine, never a plaintext substitute. */
@RunWith(AndroidJUnit4::class)
class NativeBankDirectMigrationTest {
    @Test fun encrypted54MigratesPreservesHistoryAndRestoresDirectWithoutAccount()=runBlocking<Unit> {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val root=File(app.cacheDir,"bank-native-${UUID.randomUUID()}").apply {mkdirs()}
        val context=object:ContextWrapper(app) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
            override fun getDatabasePath(name:String)=File(root,name)
        }
        val key=ByteArray(32).also {java.security.SecureRandom().nextBytes(it)}
        System.loadLibrary("sqlcipher")
        val helper=net.zetetic.database.sqlcipher.SupportOpenHelperFactory(key.copyOf(),null,false).create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object:SupportSQLiteOpenHelper.Callback(54) {
                override fun onConfigure(db:SupportSQLiteDatabase) {db.setForeignKeyConstraintsEnabled(true)}
                override fun onCreate(db:SupportSQLiteDatabase) {
                    val bytes=InstrumentationRegistry.getInstrumentation().context.assets.open("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/54.json").use {it.readBytes()}
                    val entities=JSONObject(String(bytes)).getJSONObject("database").getJSONArray("entities")
                    repeat(entities.length()) {i->val e=entities.getJSONObject(i);val t=e.getString("tableName")
                        db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",t))
                        val indexes=e.optJSONArray("indices") ?: org.json.JSONArray()
                        repeat(indexes.length()) {j->db.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",t))}
                    }
                }
                override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("unexpected")
            }).build())
        try {
            val sql=helper.writableDatabase
            sql.execSQL("INSERT INTO projects(id,name,description,position,createdAt,updatedAt) VALUES('bank','Banco','',0,1,1),('tasks','Tarjetas','',1,1,1)")
            sql.execSQL("INSERT INTO bank_roots VALUES('bank')")
            sql.execSQL("INSERT INTO persons(id,name) VALUES('p','Persona')")
            sql.execSQL("INSERT INTO nodes(id,projectId,title,description,isCompleted,position,createdAt,updatedAt,purpose,priority,sprintMode) VALUES('account','bank','Cuenta','',0,0,1,1,'LAYER','NONE',0)")
            sql.execSQL("INSERT INTO bank_accounts VALUES('account','bank','p')")
            sql.execSQL("INSERT INTO bank_operations VALUES('old','bank','account','DIRECT',2000,20000,'Histórico',NULL,NULL,'p','p',NULL,1,1),('correction','bank','account','DIRECT',3000,20000,'Corrección',NULL,NULL,'p','p','old',0,2)")
            sql.execSQL("INSERT INTO nodes(id,projectId,title,description,isCompleted,position,createdAt,updatedAt,purpose,priority,sprintMode,amountMinor,currencyCode) VALUES('debt','tasks','Deuda','',1,0,1,1,'ACTION','NONE',0,10000,'CLP')")
            sql.execSQL("INSERT INTO bank_allocations VALUES('a','old','debt',2000,'p','Persona','p','Persona','Deuda',NULL,NULL,1,2),('b','correction','debt',3000,'p','Persona','p','Persona','Deuda',NULL,NULL,2,2)")
            fun rows(db:SupportSQLiteDatabase,t:String)=db.query("SELECT * FROM $t ORDER BY id").use {c->buildList {
                while(c.moveToNext())add((0 until c.columnCount).map {if(c.isNull(it))null else c.getString(it)})
            }}
            val tables=listOf("projects","nodes","bank_roots","bank_accounts","bank_operations","bank_allocations")
            val before=tables.associateWith {rows(sql,it)};helper.close()
            val room=Arachn0deDatabase.create(context,key)
            try {
                val migrated=room.openHelper.writableDatabase
                assertEquals(55,migrated.version);assertEquals(before,tables.associateWith {rows(migrated,it)})
                migrated.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
                val repo=BankRepository(room)
                val node=NodeRepository(room).createNode("tasks",null,"Sin responsable",obligation=Obligation(1000,"CLP"))
                val preview=repo.preview(BankRequest(rootId="bank",contextAccountId=null,kind="DIRECT",amount=500,day=20000,concept=""))
                repo.confirmPayment(preview,mapOf(node.id to 500L));repo.confirmPayment(preview,mapOf(node.id to 500L))
                val saved=repo.state();val backup=BackupRepository(room,context)
                backup.restore(BackupJson.decode(BackupJson.encode(backup.snapshot())))
                assertEquals(saved,repo.state())
            } finally {room.close()}
        } finally {helper.close();key.fill(0);root.deleteRecursively()}
    }
}
