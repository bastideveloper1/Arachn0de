package com.r0ybt.arachn0de.game

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
class GameStateMigrationTest {
    @Test fun importsLegacyGameExactlyOnceAndPreservesAllExistingTables() = runBlocking<Unit> {
        val context = RuntimeEnvironment.getApplication(); context.deleteDatabase("arachn0de.db")
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db")
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(22) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected")
                }).build())
        val sql = helper.writableDatabase
        val entities = JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/22.json")!!.bufferedReader().use { it.readText() }).getJSONObject("database").getJSONArray("entities")
        val tables = (0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }
        repeat(entities.length()) { i -> val row = entities.getJSONObject(i); val name = row.getString("tableName")
            sql.execSQL(row.getString("createSql").replace("\${TABLE_NAME}", name))
            val indexes = row.optJSONArray("indices") ?: org.json.JSONArray()
            repeat(indexes.length()) { j -> sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", name)) }
        }
        sql.execSQL("INSERT INTO projects VALUES ('p','Project','Original',0,1,2)")
        sql.execSQL("INSERT INTO nodes VALUES ('n','p',NULL,'Layer','Original',0,0,1,2,NULL,NULL,'LAYER',NULL,NULL,'NONE',NULL,0,NULL)")
        sql.execSQL("INSERT INTO conversion_roots VALUES ('bridge',NULL,'n','former-project','n',0,0,NULL,NULL,'NONE',NULL,0)")
        sql.execSQL("INSERT INTO node_sort_preferences VALUES ('p:n','PRIORITY')")
        fun snapshot(database: androidx.sqlite.db.SupportSQLiteDatabase) = tables.associateWith { table ->
            database.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor -> buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) }) } }
        }
        val before = snapshot(sql); helper.close()
        val original = GameRules.roll(GameRules.beginTurn(GameRules.newGame(listOf("A", "B"), RatCharacter.entries.take(2), FirstGameMap.value)), 4)
        context.getSharedPreferences("experimental_game", 0).edit().putString("session", GameSessionCodec.encode(original)).commit()
        var db = Arachn0deDatabase.create(context)
        try {
            assertEquals(27, db.openHelper.writableDatabase.version); assertEquals(before, snapshot(db.openHelper.writableDatabase))
            assertEquals(original, GameStateRepository(db).load().session)
            db.close(); context.getSharedPreferences("experimental_game", 0).edit().putString("session", "corrupt").commit()
            db = Arachn0deDatabase.create(context); assertEquals(original, GameStateRepository(db).load().session)
        } finally { db.close() }
    }
}
