package com.r0ybt.arachn0de.data

import android.app.Application
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28], application = Application::class)
class TechnologyMigrationTest {
    @Test fun v18MigrationPreservesEveryOldTableAndValidatesNewForeignKeys() = runBlocking {
        val context = RuntimeEnvironment.getApplication(); context.deleteDatabase("arachn0de.db")
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db")
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(18) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected")
                }).build())
        val sql = helper.writableDatabase
        val entities = JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/18.json")!!.bufferedReader().use { it.readText() }).getJSONObject("database").getJSONArray("entities")
        val tables = (0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }
        for (i in 0 until entities.length()) {
            val row = entities.getJSONObject(i); val name = row.getString("tableName")
            sql.execSQL(row.getString("createSql").replace("\${TABLE_NAME}", name))
            val indexes = row.optJSONArray("indices") ?: org.json.JSONArray()
            for (j in 0 until indexes.length()) sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", name))
        }
        NodeInvariants.install(sql); ExplicitLayerInvariants.install(sql); SprintInvariants.install(sql); ObligationInvariants.install(sql); AttachmentInvariants.install(sql)
        sql.execSQL("INSERT INTO projects VALUES ('p','Project','Original',0,1,2)")
        sql.execSQL("INSERT INTO nodes VALUES ('n','p',NULL,'Task','Original',0,0,1,2,NULL,NULL,'ACTION',NULL,NULL,'NONE',NULL,0,NULL)")
        sql.execSQL("INSERT INTO persons VALUES ('person','Person',NULL)")
        sql.execSQL("INSERT INTO node_person VALUES ('n','person')")
        val attachmentId = "00000000-0000-0000-0000-000000000001"
        sql.execSQL("INSERT INTO attachment_files VALUES (?, ?, 'photo.png', 'image/png', 10, 1, 1, 'hash', 1, 'READY')", arrayOf(attachmentId, "$attachmentId.png"))
        sql.execSQL("INSERT INTO node_attachments VALUES ('n', ?)", arrayOf(attachmentId))
        fun snapshot(database: androidx.sqlite.db.SupportSQLiteDatabase) = tables.associateWith { table ->
            database.query("SELECT ${if (table == "persons") "id, name, avatarFile" else "*"} FROM `$table` ORDER BY rowid").use { cursor -> buildList {
                while (cursor.moveToNext()) add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) })
            } }
        }
        val before = snapshot(sql); helper.close()
        val db = Arachn0deDatabase.create(context)
        try {
            val migrated = db.openHelper.writableDatabase
            assertEquals(26, migrated.version); assertEquals(before, snapshot(migrated)); assertTrue(db.technologyDao().catalog().isEmpty())
            db.technologyDao().insert(TechnologyEntity("t", "Tool", null))
            db.technologyDao().assignNodes(listOf(NodeTechnologyEntity("n", "t"))); db.technologyDao().assignProjects(listOf(ProjectTechnologyEntity("p", "t")))
            assertTrue(runCatching { db.technologyDao().assignNodes(listOf(NodeTechnologyEntity("n", "absent"))) }.isFailure)
            db.technologyDao().delete("t")
            assertTrue(db.technologyDao().nodes().isEmpty()); assertTrue(db.technologyDao().projects().isEmpty())
            assertEquals(before, snapshot(migrated))
            migrated.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { db.close() }
    }
}
