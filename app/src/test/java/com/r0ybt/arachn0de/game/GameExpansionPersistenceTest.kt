package com.r0ybt.arachn0de.game

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GameExpansionPersistenceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: Arachn0deDatabase
    private lateinit var repository: GameStateRepository
    private lateinit var backup: BackupRepository
    private val map = GameMapGenerator.generate(91)
    private fun initial() = GameRules.newGame(listOf("A", "Bot"), RatCharacter.entries.take(2), map, controls = listOf(PlayerControl.HUMAN, PlayerControl.HARD))
    @Before fun setup() { context.deleteDatabase("arachn0de.db"); context.getSharedPreferences("experimental_game", 0).edit().clear().commit(); open() }
    private fun open() { db = Arachn0deDatabase.create(context); repository = GameStateRepository(db); backup = BackupRepository(db, context, BackupAvatarFiles(context, BackupFixture::syncDirectory)) }
    @After fun close() { db.close() }
    private fun roundTrip(s: GameSession): GameSession = requireNotNull(GameSessionCodec.decode(GameSessionCodec.encode(s))).also { assertEquals(s, it) }
    @Test fun roundTripsMapSeedBotsFogEyeTrapAnimationCombatAndTimer() = runBlocking<Unit> {
        var s = GameExpansion.explore(GameRules.beginTurn(initial()), map)
        s = roundTrip(GameRules.roll(s, 2)); s = roundTrip(GameExpansion.finishRoll(s))
        s = roundTrip(GameExpansion.planStep(s, map)); assertNotNull(s.motion)
        val arrived = roundTrip(GameExpansion.arrive(s, map)); assertEquals(TurnPhase.MOVING, arrived.phase)
        val tile = s.spider!!.tileId
        val before = map.tiles.values.first { tile in it.next }.id
        s = s.copy(players = s.players.mapIndexed { i, p -> if (i == 0) p.copy(tileId = tile, routeHistory = listOf(before, tile), engagedSpider = true, trapped = false) else p },
            phase = TurnPhase.COMBAT, motion = null, remainingSteps = 0)
        s = roundTrip(GameExpansion.combat(roundTrip(s), map, 3, 3))
        s = roundTrip(GameExpansion.presentResult(s, 1234)); assertEquals(11234L, s.resultDeadline)
        repository.start(s); db.close(); open(); assertEquals(s, repository.load().session)
        val swamp = map.tiles.values.first { it.terrain == Terrain.SWAMP }.id
        val trapped = s.copy(players = s.players.map { it.copy(tileId = swamp, routeHistory = listOf(swamp), trapped = true, engagedSpider = false) }); roundTrip(trapped)
    }
    @Test fun compareAndSetMakesTimerContinueDoubleTapAndStaleAnimationExactlyOnce() = runBlocking<Unit> {
        val s = GameExpansion.presentResult(initial().copy(phase = TurnPhase.RESULT, result = "Terminado"), 0)
        repository.start(s); val version = repository.load().revision
        val attempts = coroutineScope { List(6) { async(Dispatchers.Default) { repository.act(version) { GameExpansion.continueTurn(it, map) } } }.awaitAll() }
        assertEquals(1, attempts.count { it }); assertEquals(1, repository.load().session!!.currentIndex)
        assertFalse(repository.act(version) { GameExpansion.continueTurn(it, map) })
    }
    @Test fun cancelledCommandRollsBackPersistedState() = runBlocking<Unit> {
        repository.start(initial()); val before = repository.load()
        try { repository.act(before.revision) { throw CancellationException("simulated cancellation") }; fail() } catch (_: CancellationException) {}
        assertEquals(before, repository.load())
    }
    @Test fun backupReinstallRestoresFullGameAndHistoricalV14ClearsItWithoutResurrectingLegacyPreferences() = runBlocking<Unit> {
        val game = GameExpansion.planStep(GameExpansion.finishRoll(GameRules.roll(GameExpansion.explore(GameRules.beginTurn(initial()), map), 2)), map)
        repository.start(game); val archive = backup.create().readBytes()
        db.close(); context.deleteDatabase("arachn0de.db"); open()
        val candidate = backup.inspect(archive.inputStream()); backup.restore(candidate); backup.discard(candidate)
        assertEquals(game, repository.load().session)
        val historical = JSONObject(String(BackupJson.encode(BackupFixture.empty()))).put("dataVersion", 14).apply { remove("metroPreferences"); remove("metroJourneys"); remove("gameSession") }
        backup.restore(BackupJson.decode(historical.toString().toByteArray()))
        context.getSharedPreferences("experimental_game", 0).edit().putString("session", GameSessionCodec.encode(game)).commit()
        db.close(); open(); assertNull(repository.load().session)
    }
    @Test fun corruptGameAndRestoreSqlFailureKeepPreviousGameAndProductivityData() = runBlocking<Unit> {
        repository.start(initial()); db.projectDao().insert(ProjectEntity("p", "Protected", "Previous", 0, 1, 2)); val before = repository.load()
        val invalid = BackupFixture.empty().copy(gameSession = "corrupt")
        try { backup.restore(invalid); fail() } catch (_: IllegalArgumentException) {}
        assertEquals(before, repository.load())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_game_restore BEFORE UPDATE ON game_state BEGIN SELECT RAISE(ABORT, 'injected'); END")
        val alternate = GameSessionCodec.encode(initial().copy(round = 8))
        try { backup.restore(BackupFixture.empty().copy(gameSession = alternate)); fail() } catch (_: android.database.sqlite.SQLiteException) {}
        assertEquals(before, repository.load()); assertEquals("Protected", db.projectDao().getById("p")!!.name)
    }
    @Test fun malformedDuplicatesOversizedNumbersGraphsAndVisionAreRejectedBeforeUse() {
        val raw = GameSessionCodec.encode(initial())
        assertNull(GameSessionCodec.decode(raw.replace("\"version\":3", "\"version\":3,\"version\":3")))
        assertNull(GameSessionCodec.decode(raw + "garbage"))
        assertNull(GameSessionCodec.decode(JSONObject(raw).apply { getJSONArray("players").getJSONObject(0).put("vision", Int.MAX_VALUE) }.toString()))
        assertNull(GameSessionCodec.decode(JSONObject(raw).apply { getJSONArray("players").getJSONObject(0).put("eyeUses", -1) }.toString()))
        assertNull(GameSessionCodec.decode(JSONObject(raw).apply { getJSONArray("players").getJSONObject(0).put("trapped", true) }.toString()))
        assertNull(GameSessionCodec.decode(JSONObject(raw).apply { getJSONArray("players").getJSONObject(0).put("engaged", true) }.toString()))
        assertNull(GameSessionCodec.decode(JSONObject(raw).apply { getJSONObject("board").getJSONArray("tiles").getJSONObject(0).put("next", org.json.JSONArray(listOf("missing"))) }.toString()))
        assertNull(GameSessionCodec.decode("x".repeat(GameSessionCodec.MAX_BYTES + 1)))
    }
    @Test fun genuineV2KeepsOriginalMapPositionsRollAndAbilitiesAndAddsIndependentEye() {
        val oldMap = FirstGameMap.value
        val old = GameRules.roll(GameRules.beginTurn(GameRules.newGame(listOf("A", "B"), RatCharacter.entries.take(2), oldMap)), 5)
        val root = JSONObject(GameSessionCodec.encode(old)).put("version", 2)
        listOf("board", "spider", "motion", "retreat", "result", "deadline", "lapReset", "combat").forEach(root::remove)
        repeat(2) { i -> listOf("control", "trapped", "engaged", "history", "explored", "eyeUses", "eyeTurns").forEach(root.getJSONArray("players").getJSONObject(i)::remove) }
        val restored = GameSessionCodec.decode(root.toString())!!
        assertEquals(oldMap.id, restored.mapId); assertNull(restored.board); assertEquals(5, restored.lastRoll)
        assertEquals(TurnPhase.ROLLING, restored.phase); assertEquals(3, restored.currentPlayer.eyeUses)
        assertEquals(old.currentPlayer.abilityCharges, restored.currentPlayer.abilityCharges)
    }
}
