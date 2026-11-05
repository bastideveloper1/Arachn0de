package com.r0ybt.arachn0de.backup.legacy

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.backup.legacy.game.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.security.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class LegacyGameBackupTest {
    private fun fixture(name:String)=javaClass.classLoader!!.getResourceAsStream("legacy-game/$name.json")!!.bufferedReader().use {it.readText()}
    private val names=listOf("v1","v2","v3","v3-board","v4")
    private class Store:AutoCloseable {
        val base=ApplicationProvider.getApplicationContext<Application>()
        val root=File(base.filesDir,"legacy-game-${UUID.randomUUID()}").apply {mkdirs()}
        val owner=SecureFiles.Session(UUID.randomUUID(),root,VaultCrypto.randomKey())
        val context=VaultContext(base,owner)
        val db=Room.inMemoryDatabaseBuilder(base,Arachn0deDatabase::class.java).build()
        val backup:BackupRepository
        init {SecureFiles.register(owner);SecureFiles.activeProfile=owner.id;context.database=db;backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))}
        override fun close() {context.clearMemory();db.close();SecureFiles.revoke(owner);root.deleteRecursively()}
    }
    @Test fun historicalVersionsAndGeneratedBoardKeepTheirValidationAndNormalization() {
        names.forEach {name->
            val raw=fixture(name)
            assertTrue(name,RaceCodec.validPayload(raw))
            val decoded=BackupJson.decode(BackupJson.encode(BackupFixture.empty().copy(gameSession=raw)))
            assertEquals(raw,decoded.gameSession)
            if(name!="v4") {
                val session=requireNotNull(GameSessionCodec.decode(raw))
                assertEquals(session,GameSessionCodec.decode(GameSessionCodec.encode(session)))
            }
        }
        val pending=requireNotNull(RaceCodec.decode(fixture("v4")))
        assertEquals(3,pending.steps);assertEquals(2,pending.actor.inventory.single().readyTurn)
        assertEquals((1..7).toSet(),pending.eyes.single().zone)
        assertEquals(41,GameSessionCodec.decode(fixture("v3-board"))!!.board!!.tiles.size)
    }
    @Test fun corruptPayloadsStillFailBeforeRestore()=runBlocking<Unit> {
        Store().use {store->
            val original=fixture("v4")
            store.backup.restore(BackupFixture.empty().copy(gameSession=original))
            store.db.projectDao().insert(ProjectEntity("p","Preserved","",0,1,2))
            val row=store.db.gameStateDao().get()
            for(raw in listOf("corrupt",original+"garbage",original.replace("\"version\":4","\"version\":4,\"version\":4"),
                JSONObject(original).put("laps",2.5).toString(),JSONObject(original).put("current",99).toString(),
                JSONObject(original).also {it.getJSONObject("track").getJSONArray("tiles").getJSONObject(0).put("progress",1000)}.toString(),
                "x".repeat(GameSessionCodec.MAX_BYTES+1))) {
                assertFalse(RaceCodec.validPayload(raw))
                try {store.backup.restore(BackupFixture.empty().copy(gameSession=raw));fail("Accepted corrupt save")} catch(_:IllegalArgumentException) {}
                assertEquals(row,store.db.gameStateDao().get());assertEquals("Preserved",store.db.projectDao().getById("p")!!.name)
            }
            val badBoard=JSONObject(fixture("v3-board")).also {it.getJSONObject("board").getJSONArray("tiles").getJSONObject(0).put("next",org.json.JSONArray(listOf("missing")))}.toString()
            assertFalse(RaceCodec.validPayload(badBoard))
        }
    }
    @Test fun encryptedArchivesRestoreEveryHistoricalPayloadIntoAnEmptyStore()=runBlocking<Unit> {
        for(name in names) {
            val raw=fixture(name)
            Store().use {source->
                source.backup.restore(BackupFixture.empty().copy(gameSession=raw))
                source.db.projectDao().insert(ProjectEntity("p","Preserved","",0,1,2))
                val password="synthetic-backup-password".toCharArray()
                val file=source.backup.create(password)
                try {
                    val archive=SecureFiles.read(file)
                    Store().use {target->
                        val candidate=target.backup.inspect(archive.inputStream(),password)
                        try {target.backup.restore(candidate)} finally {target.backup.discard(candidate)}
                        assertEquals(raw,target.db.gameStateDao().get()!!.payload)
                        assertEquals("Preserved",target.db.projectDao().getById("p")!!.name)
                        assertEquals(raw,target.backup.snapshot().gameSession)
                    }
                } finally {source.backup.discardPending(file.name)}
            }
        }
    }
    @Test fun sqlFailureRollsBackHistoricalSaveAndProductivityTogether()=runBlocking<Unit> {
        Store().use {store->
            store.backup.restore(BackupFixture.empty().copy(gameSession=fixture("v4")))
            store.db.projectDao().insert(ProjectEntity("p","Preserved","",0,1,2))
            val row=store.db.gameStateDao().get()
            store.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_legacy_restore BEFORE UPDATE ON game_state BEGIN SELECT RAISE(ABORT, 'injected'); END")
            try {store.backup.restore(BackupFixture.empty().copy(gameSession=fixture("v3")));fail()} catch(_:android.database.sqlite.SQLiteException) {}
            assertEquals(row,store.db.gameStateDao().get());assertEquals("Preserved",store.db.projectDao().getById("p")!!.name)
        }
    }
    @Test fun backupsBeforeGameSupportKeepTheExistingEmptyMarkerBehavior()=runBlocking<Unit> {
        Store().use {store->
            store.backup.restore(BackupFixture.empty().copy(gameSession=fixture("v4")))
            val old=JSONObject(String(BackupJson.encode(BackupFixture.empty()))).put("dataVersion",14).apply {
                remove("gameSession");remove("metroPreferences");remove("metroJourneys");BackupFixture.removeSprintFields(this)
            }
            store.backup.restore(BackupJson.decode(old.toString().toByteArray()))
            assertEquals("",store.db.gameStateDao().get()!!.payload)
            assertNull(store.backup.snapshot().gameSession)
        }
    }
    @Test fun legacyPreferenceMigrationIsIdempotentAndPreservesOtherData() {
        val base=ApplicationProvider.getApplicationContext<Application>()
        val helper=androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(base).name(null)
                .callback(object:androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(22) {
                    override fun onCreate(db:androidx.sqlite.db.SupportSQLiteDatabase) {db.execSQL("CREATE TABLE sentinel (value TEXT NOT NULL)");db.execSQL("INSERT INTO sentinel VALUES ('preserved')")}
                    override fun onUpgrade(db:androidx.sqlite.db.SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
                }).build())
        try {
            val db=helper.writableDatabase
            GameMigration22To23(fixture("v1")).migrate(db)
            fun payload()=db.query("SELECT payload FROM game_state").use {it.moveToFirst();it.getString(0)}
            val expected=GameSessionCodec.encode(requireNotNull(GameSessionCodec.decode(fixture("v1"))))
            assertEquals(expected,payload())
            importLegacyGame(db,"corrupt");assertEquals(expected,payload())
            db.query("SELECT value FROM sentinel").use {assertTrue(it.moveToFirst());assertEquals("preserved",it.getString(0))}
        } finally {helper.close()}
    }
}
