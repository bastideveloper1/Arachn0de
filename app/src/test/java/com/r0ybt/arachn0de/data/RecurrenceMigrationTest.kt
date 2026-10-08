package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24,28])
class RecurrenceMigrationTest {
    @Test fun exportedV8MigratesPreservingEveryFieldAndAddsRecurrenceTables() = runBlocking {
        val context = RuntimeEnvironment.getApplication(); context.deleteDatabase("arachn0de.db")
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db")
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected")
                }).build())
        val sql = helper.writableDatabase
        val schema = JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/8.json")!!.bufferedReader().use { it.readText() }).getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
            sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
            val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
            for (j in 0 until indices.length()) sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
        }
        sql.execSQL("INSERT INTO projects VALUES ('old','Project','Description',8,100,150)")
        sql.execSQL("INSERT INTO nodes VALUES ('root','old',NULL,'Layer','Content',0,7,100,150,10,20,'ACTION',NULL,NULL)")
        sql.execSQL("INSERT INTO nodes VALUES ('leaf','old','root','Task','Details',1,9,110,160,11,21,'ACTION',15000,'CLP')")
        sql.execSQL("INSERT INTO nodes VALUES ('note','old','root','Note','Text',0,10,111,161,12,22,'NOTE',NULL,NULL)")
        sql.execSQL("INSERT INTO persons VALUES ('p','Person','avatar.png')")
        sql.execSQL("INSERT INTO node_person VALUES ('leaf','p')")
        NodeInvariants.install(sql); NodePurposeInvariants.install(sql); ObligationInvariants.install(sql)
        helper.close()
        val db = Arachn0deDatabase.create(context)
        try {
            assertEquals(20, db.openHelper.readableDatabase.version)
            assertEquals(NodeEntity("leaf","old","root","Task","Details",true,9,110,160,11,21, amountMinor = 15000, currencyCode = "CLP"),db.nodeDao().getById("leaf"))
            assertEquals(NodeEntity("root","old",null,"Layer","Content",false,7,100,150,10,20,purpose="LAYER"),db.nodeDao().getById("root"))
            assertEquals(NodeEntity("note","old","root","Note","Text",false,10,111,161,12,22,"NOTE"),db.nodeDao().getById("note"))
            assertEquals(8,db.projectDao().getById("old")!!.position)
            assertEquals("avatar.png",db.personDao().get("p")!!.avatarFile)
            assertEquals("p",PersonRepository(db,AvatarStore(context)).observeAssignments("old").first().getValue("leaf").single().id)
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name LIKE 'nodes_%'").use { it.moveToFirst(); assertEquals(17,it.getInt(0)) }
            assertTrue(db.recurrenceDao().rules().isEmpty())
            assertTrue(db.recurrenceDao().occurrences().isEmpty())
            assertTrue(db.recurrenceDao().assignments().isEmpty())
            val nodes = NodeRepository(db)
            val bill = nodes.createNode("old",null,"Bill",obligation=com.r0ybt.arachn0de.domain.model.Obligation(50000,"CLP"))
            assertEquals(50000L,db.nodeDao().getById(bill.id)!!.amountMinor)
        } finally { db.close(); context.deleteDatabase("arachn0de.db") }
    }
}
