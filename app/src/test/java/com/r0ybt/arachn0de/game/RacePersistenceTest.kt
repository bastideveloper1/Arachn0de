package com.r0ybt.arachn0de.game

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class RacePersistenceTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: Arachn0deDatabase
    private lateinit var repo: RaceRepository
    private lateinit var backup: BackupRepository
    private fun initial()=RaceRules.newGame(Racer.entries.toList(),setOf(0,2),3,44)
    @Before fun setup() { context.deleteDatabase("arachn0de.db"); context.getSharedPreferences("experimental_game",0).edit().clear().commit(); open() }
    private fun open() { db=Arachn0deDatabase.create(context); repo=RaceRepository(db); backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory)) }
    @After fun close() { db.close() }
    private fun roundtrip(s: RaceSession): RaceSession = requireNotNull(RaceCodec.decode(RaceCodec.encode(s))).also { assertEquals(s,it) }
    @Test fun everyTransitionRoundTripsIncludingBotsBranchesPrizesTimersAndWinner() {
        var s=initial().copy(players=initial().players.map { it.copy(human=false) })
        var count=0; val phases=mutableSetOf<RacePhase>(); val effects=mutableSetOf<RaceItem>()
        while(s.phase!=RacePhase.WON && count++<20000) { phases.add(s.phase); s.effect?.let(effects::add); s=roundtrip(RaceBots.act(s)) }
        assertEquals(RacePhase.WON,s.phase); assertTrue(RacePhase.PRIZE in phases); assertTrue(RacePhase.BRANCH in phases); assertTrue(RacePhase.SKIPPED in phases)
        assertTrue(effects.containsAll(listOf(RaceItem.BARRIER,RaceItem.CLOAK,RaceItem.DYNAMITE,RaceItem.EYE)))
        roundtrip(s)
    }
    @Test fun closeAndReopenPreservesPendingMovementPrizeAndFutureAvailability() = runBlocking<Unit> {
        var s=RaceRules.begin(initial()).let { it.copy(players=it.players.map { p->if(p.id==0)p.copy(tile=32,history=listOf(32)) else p },phase=RacePhase.MOVING,steps=3) }
        s=RaceRules.step(s); assertEquals(RacePhase.PRIZE,s.phase); repo.start(s)
        db.close(); open(); assertEquals(s,repo.observe().first().session)
        val saved=repo.observe().first(); repo.act(saved.revision) { RaceRules.prize(it) }
        assertEquals(2,repo.observe().first().session!!.steps); assertEquals(2,repo.observe().first().session!!.actor.inventory.single().readyTurn)
    }
    @Test fun concurrentAndCancelledCommandsCannotOverwritePreviousState() = runBlocking<Unit> {
        repo.start(initial()); val before=repo.observe().first()
        val attempts=coroutineScope { List(5) { async(Dispatchers.Default) { repo.act(before.revision,RaceRules::begin) } }.awaitAll() }
        assertEquals(1,attempts.count { it }); assertFalse(repo.act(before.revision,RaceRules::begin))
        val saved=repo.observe().first()
        try { repo.act(saved.revision) { throw CancellationException("test") }; fail() } catch(_: CancellationException) {}
        assertEquals(saved,repo.observe().first())
        try { repo.act(saved.revision) { it.copy(current=99) }; fail() } catch(_: IllegalArgumentException) {}
        assertEquals(saved,repo.observe().first())
    }
    @Test fun archiveRestoresIntoEmptyInstallationWithProductivityAndInvalidatesStaleCallbacks() = runBlocking<Unit> {
        var s=RaceRules.begin(initial()); s=s.copy(players=s.players.map { if(it.id==0)it.copy(inventory=listOf(HeldRaceItem(RaceItem.EYE,1))) else it })
        s=RaceRules.use(s,0,cells=listOf(4)); s=RaceRules.roll(s); repo.start(s)
        db.projectDao().insert(ProjectEntity("p","Protected","Preserved",0,1,2))
        val archive=backup.create().readBytes(); db.close(); context.deleteDatabase("arachn0de.db"); open()
        val candidate=backup.inspect(archive.inputStream()); backup.restore(candidate); backup.discard(candidate)
        assertEquals(s,repo.observe().first().session); assertEquals("Protected",db.projectDao().getById("p")!!.name)
        val before=repo.observe().first(); backup.restore(BackupFixture.empty().copy(gameSession=RaceCodec.encode(s)))
        assertFalse(repo.act(before.revision) { it.copy(message="stale") }); assertEquals(s,repo.observe().first().session)
    }
    @Test fun invalidSnapshotAndSqlFailureRollbackGameAndProductivityTogether() = runBlocking<Unit> {
        repo.start(initial()); db.projectDao().insert(ProjectEntity("p","Protected","",0,1,2)); val before=repo.observe().first()
        try { backup.restore(BackupFixture.empty().copy(gameSession="corrupt")); fail() } catch(_: IllegalArgumentException) {}
        assertEquals(before,repo.observe().first()); assertEquals("Protected",db.projectDao().getById("p")!!.name)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_race_restore BEFORE UPDATE ON game_state BEGIN SELECT RAISE(ABORT, 'injected'); END")
        try { backup.restore(BackupFixture.empty().copy(gameSession=RaceCodec.encode(initial().copy(round=2)))); fail() } catch(_: android.database.sqlite.SQLiteException) {}
        assertEquals(before,repo.observe().first()); assertEquals("Protected",db.projectDao().getById("p")!!.name)
    }
    @Test fun legacyGamesRemainExportableRestorableAndAreNeverAutomaticallyReplaced() = runBlocking<Unit> {
        val old=GameRules.newGame(listOf("A","B"),RatCharacter.entries.take(2),FirstGameMap.value)
        val raw=GameSessionCodec.encode(old); db.gameStateDao().save(GameStateEntity(payload=raw,revision=6))
        assertTrue(repo.observe().first().legacy); assertNull(repo.observe().first().session)
        val archive=backup.create().readBytes(); val candidate=backup.inspect(archive.inputStream()); backup.restore(candidate); backup.discard(candidate)
        assertEquals(old,GameSessionCodec.decode(db.gameStateDao().get()!!.payload)); assertTrue(repo.observe().first().legacy)
        val v2=JSONObject(raw).put("version",2).toString(); assertTrue(RaceCodec.validPayload(v2))
        val historical=JSONObject(String(BackupJson.encode(BackupFixture.empty()))).put("dataVersion",14).apply { remove("gameSession"); remove("metroPreferences"); remove("metroJourneys"); BackupFixture.removeSprintFields(this) }
        backup.restore(BackupJson.decode(historical.toString().toByteArray())); assertFalse(repo.observe().first().legacy); assertNull(repo.observe().first().session)
    }
    @Test fun strictIntegrityRejectsDuplicatesBadReferencesTypesBranchesTimersAndPhase() {
        val raw=RaceCodec.encode(initial()); assertNotNull(RaceCodec.decode(raw))
        assertNull(RaceCodec.decode(raw.replace("\"version\":4","\"version\":4,\"version\":4")))
        assertNull(RaceCodec.decode(raw+"garbage")); assertNull(RaceCodec.decode("x".repeat(GameSessionCodec.MAX_BYTES+1)))
        fun altered(action: (JSONObject)->Unit) = JSONObject(raw).also(action).toString()
        assertNull(RaceCodec.decode(altered { it.put("laps",2.5) }))
        assertNull(RaceCodec.decode(altered { it.put("current","0") }))
        assertNull(RaceCodec.decode(altered { it.getJSONArray("players").getJSONObject(1).put("character","RABBIT") }))
        assertNull(RaceCodec.decode(altered { it.getJSONObject("track").getJSONArray("tiles").getJSONObject(0).put("next",org.json.JSONArray(listOf(99))) }))
        assertNull(RaceCodec.decode(altered { it.getJSONObject("track").getJSONArray("tiles").getJSONObject(0).put("progress",1000) }))
        assertNull(RaceCodec.decode(altered { it.put("phase","PRIZE") }))
        assertNull(RaceCodec.decode(altered { it.getJSONArray("players").getJSONObject(0).put("collected",org.json.JSONArray(listOf(999))) }))
    }
}
