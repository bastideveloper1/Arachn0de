package com.r0ybt.arachn0de.game

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GameSessionCodecTest {
    private val map = FirstGameMap.value
    private fun initial() = GameRules.newGame(listOf("Camila", "José"), listOf(RatCharacter.NECROMANCER, RatCharacter.KNIGHT), map)
    @Test fun restoresPendingMovementWithoutRepeatingConsumedSteps() {
        val moved = GameRules.step(GameRules.finishRoll(GameRules.roll(GameRules.beginTurn(initial()), 4)), map)
        var restored = GameSessionCodec.decode(GameSessionCodec.encode(moved), map)!!
        assertEquals(moved, restored)
        while (restored.phase == TurnPhase.MOVING) restored = GameRules.step(restored, map)
        assertEquals("tile-4", restored.players[0].tileId)
        assertEquals(1, restored.currentIndex)
        assertEquals(TurnPhase.HANDOFF, restored.phase)
    }
    @Test fun preservesReadyHandoffAndWinnerSnapshots() {
        val s = initial()
        assertEquals(s, GameSessionCodec.decode(GameSessionCodec.encode(s), map))
        val ready = GameRules.beginTurn(s)
        assertEquals(ready, GameSessionCodec.decode(GameSessionCodec.encode(ready), map))
        val nearGoal = ready.copy(players = ready.players.map { it.copy(tileId = "tile-25") })
        val won = GameRules.step(GameRules.finishRoll(GameRules.roll(nearGoal, 6)), map)
        assertEquals(won, GameSessionCodec.decode(GameSessionCodec.encode(won), map))
    }
    @Test fun corruptOrUnknownSnapshotsAreSafelyRejected() {
        assertNull(GameSessionCodec.decode("broken", map))
        assertNull(GameSessionCodec.decode(GameSessionCodec.encode(initial()).replace("meadow-v1", "unknown"), map))
        assertNull(GameSessionCodec.decode(GameSessionCodec.encode(initial()).replace("TOP_RIGHT", "TOP_LEFT"), map))
    }
}
