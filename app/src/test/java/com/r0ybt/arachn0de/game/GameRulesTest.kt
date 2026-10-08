package com.r0ybt.arachn0de.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class GameRulesTest {
    private val map = FirstGameMap.value
    private fun session(count: Int = 2) = GameRules.newGame((1..count).map { "Jugador $it" }, RatCharacter.entries.take(count), map)
    private fun move(s: GameSession, roll: Int): GameSession {
        var result = GameRules.finishRoll(GameRules.roll(GameRules.beginTurn(s), roll))
        while (result.phase == TurnPhase.MOVING) result = GameRules.step(result, map)
        return result
    }
    @Test fun d6IsWithinRangeAndCanProduceEveryFace() {
        val random = Random(42)
        val results = (1..1000).map { GameRules.rollD6(random) }
        assertTrue(results.all { it in 1..6 })
        assertEquals((1..6).toSet(), results.toSet())
    }
    @Test fun movementConsumesOneConnectionPerStepAndRotatesTurn() {
        var s = GameRules.finishRoll(GameRules.roll(GameRules.beginTurn(session()), 4))
        repeat(4) { step ->
            s = GameRules.step(s, map)
            assertEquals("tile-${step + 1}", s.players[0].tileId)
            assertEquals(if (step == 3) TurnPhase.HANDOFF else TurnPhase.MOVING, s.phase)
        }
        assertEquals(1, s.currentIndex)
        assertEquals(1, s.round)
        s = move(s, 2)
        assertEquals(0, s.currentIndex)
        assertEquals(2, s.round)
        assertEquals("tile-2", s.players[1].tileId)
    }
    @Test fun sharedTilePreservesAllFourQuadrants() {
        var s = session(4)
        assertTrue(s.players.all { it.tileId == map.start })
        repeat(4) { s = move(s, 1) }
        assertTrue(s.players.all { it.tileId == "tile-1" })
        assertEquals(TokenQuadrant.entries, s.players.map { it.quadrant })
        assertEquals(listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1), s.players.map { it.quadrant.row to it.quadrant.column })
        assertEquals(2, s.round)
    }
    @Test fun reachingGoalWithExcessStepsWinsAndStopsTurnRotation() {
        val initial = session().let { it.copy(players = it.players.map { p -> p.copy(tileId = "tile-25") }) }
        val s = move(initial, 6)
        assertEquals(map.goal, s.currentPlayer.tileId)
        assertEquals(s.players[0].id, s.winnerId)
        assertEquals(TurnPhase.WON, s.phase)
        assertEquals(0, s.remainingSteps)
        assertEquals(0, s.currentIndex)
    }
    @Test fun mapSupportsMultipleDestinationsIndependentOfGridPosition() {
        val fork = map.copy(tiles = map.tiles + (map.start to GameTile(map.start, listOf("tile-1", "tile-20"))))
        assertEquals(2, fork.tiles.getValue(map.start).next.size)
        assertEquals("tile-1", fork.next(map.start))
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateCharactersAreRejected() {
        GameRules.newGame(listOf("A", "B"), listOf(RatCharacter.MAGE, RatCharacter.MAGE), map)
    }
    @Test(expected = IllegalArgumentException::class) fun rollRequiresBeginningTurn() { GameRules.roll(session(), 3) }
    @Test(expected = IllegalArgumentException::class) fun invalidDieIsRejected() { GameRules.roll(GameRules.beginTurn(session()), 7) }
}
