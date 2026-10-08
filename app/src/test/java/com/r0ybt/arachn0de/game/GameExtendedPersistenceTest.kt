package com.r0ybt.arachn0de.game

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GameExtendedPersistenceTest {
    private val map = FirstGameMap.value
    private fun initial() = GameRules.newGame(listOf("Ana", "Luis", "Paz"), listOf(RatCharacter.MONK, RatCharacter.NECROMANCER, RatCharacter.ROGUE), map, 3)
    private fun roundTrip(s: GameSession): GameSession {
        val restored = GameSessionCodec.decode(GameSessionCodec.encode(s), map)
        assertEquals(s, restored)
        return requireNotNull(restored)
    }
    @Test fun preservesObjectsOwnershipHpChargesStatusesLapsRankingAndPlacement() {
        var s = GameRules.beginTurn(initial())
        s = s.copy(players = s.players.mapIndexed { i, p -> p.copy(abilityCharges = p.abilityCharges - 1,
            completedLaps = if (i == 0) 1 else 0, coins = 2, inventory = listOf("future-item"), statuses = listOf("future-state"), visionModifier = -1) },
            objects = listOf(BoardObject("object-40", "player-1", "tile-1", ObjectType.TOTEM), BoardObject("object-41", "player-2", "tile-23", ObjectType.ZOMBIE)), nextEventId = 42)
        s = GameRules.selectPlacement(GameRules.startPlacement(s), map, "tile-2")
        val restored = roundTrip(s)
        val placed = GameRules.confirmPlacement(restored, map)
        assertEquals("object-42", placed.objects.last().id)
        assertEquals(0, placed.currentPlayer.abilityCharges)
    }
    @Test fun restoresDamageQueueAndRestWithoutDoubleDamage() {
        var s = initial().copy(players = initial().players.map { if (it.id == "player-1") it.copy(skippedTurns = 1) else it })
        s = GameRules.damage(s, map, "player-2", "player-1", 40, ObjectType.ZOMBIE)
        s = GameRules.damage(s, map, "player-3", "player-1", 100, ObjectType.SPIKES)
        s = roundTrip(GameRules.beginTurn(s))
        s = roundTrip(GameRules.acknowledgeDamage(s))
        s = roundTrip(GameRules.acknowledgeDamage(s))
        assertEquals(TurnPhase.REST, s.phase)
        assertEquals(0, s.currentPlayer.health)
        s = roundTrip(GameRules.rest(s))
        assertEquals(50, s.players[0].health)
        assertEquals(0, s.players[0].skippedTurns)
        assertTrue(s.pendingDamage.isEmpty())
    }
    @Test fun rollingRestoresSameOutcomeThenContinuesMovement() {
        var s = roundTrip(GameRules.roll(GameRules.beginTurn(initial()), 4))
        assertEquals(TurnPhase.ROLLING, s.phase)
        assertEquals(4, s.lastRoll)
        s = roundTrip(GameRules.step(GameRules.finishRoll(s), map))
        assertEquals("tile-1", s.currentPlayer.tileId)
        assertEquals(3, s.remainingSteps)
        while (s.phase == TurnPhase.MOVING) s = GameRules.step(roundTrip(s), map)
        assertEquals("tile-4", s.players[0].tileId)
    }
    @Test fun preservesIncompleteAndCompleteClassification() {
        val s = initial().copy(players = initial().players.map { if (it.id == "player-1") it.copy(completedLaps = 3, tileId = map.goal) else it },
            ranking = listOf("player-1"), winnerId = "player-1", currentIndex = 1)
        roundTrip(s)
        roundTrip(s.copy(phase = TurnPhase.WON, ranking = listOf("player-1", "player-3", "player-2")))
    }
    private fun legacy(phase: TurnPhase = TurnPhase.MOVING): JSONObject {
        val original = GameRules.newGame(listOf("A", "B", "C"), RatCharacter.entries.take(3), map)
        val s = if (phase == TurnPhase.MOVING) GameRules.step(GameRules.finishRoll(GameRules.roll(GameRules.beginTurn(original), 4)), map) else original
        val o = JSONObject(GameSessionCodec.encode(s)).put("version", 1)
        listOf("laps", "objects", "damage", "ranking", "placement", "nextEvent").forEach(o::remove)
        val players = o.getJSONArray("players")
        repeat(players.length()) { i ->
            val p = players.getJSONObject(i)
            p.put("health", 10).put("maxHealth", 10).put("charges", 0)
            listOf("exhausted", "skipped", "completedLaps", "vision").forEach(p::remove)
        }
        return o
    }
    @Test fun upgradesV1InFlightMovementAndPreservesIdentityQuadrantsAndSteps() {
        val restored = GameSessionCodec.decode(legacy().toString(), map)!!
        assertEquals(TurnPhase.MOVING, restored.phase)
        assertEquals("tile-1", restored.currentPlayer.tileId)
        assertEquals(3, restored.remainingSteps)
        assertEquals(100, restored.currentPlayer.health)
        assertEquals(2, restored.currentPlayer.abilityCharges)
        assertEquals(TokenQuadrant.entries.take(3), restored.players.map { it.quadrant })
        roundTrip(restored)
    }
    @Test fun upgradesLegacyWinnerAndLetsOthersContinueWithoutInventingTheirPlacements() {
        val o = legacy(TurnPhase.HANDOFF).put("phase", "WON").put("winner", "player-1")
        o.getJSONArray("players").getJSONObject(0).put("tile", map.goal)
        val restored = GameSessionCodec.decode(o.toString(), map)!!
        assertEquals(listOf("player-1"), restored.ranking)
        assertEquals(TurnPhase.HANDOFF, restored.phase)
        assertEquals("player-2", restored.currentPlayer.id)
        roundTrip(restored)
    }
    @Test fun rejectsUnknownOwnersDuplicateRankingInvalidHealthAndCorruptPhases() {
        val normal = GameSessionCodec.encode(initial())
        val badHp = JSONObject(normal).apply { getJSONArray("players").getJSONObject(0).put("health", -1) }
        assertNull(GameSessionCodec.decode(badHp.toString(), map))
        assertNull(GameSessionCodec.decode(JSONObject(normal).put("phase", "ROLLING").toString(), map))
        assertNull(GameSessionCodec.decode(JSONObject(normal).put("phase", "DAMAGE").toString(), map))
        val withObject = initial().copy(objects = listOf(BoardObject("object-1", "unknown", map.start, ObjectType.TOTEM)))
        assertNull(GameSessionCodec.decode(GameSessionCodec.encode(withObject), map))
        val ranked = initial().copy(ranking = listOf("player-1", "player-1"), winnerId = "player-1", currentIndex = 1)
        assertNull(GameSessionCodec.decode(GameSessionCodec.encode(ranked), map))
    }
}
