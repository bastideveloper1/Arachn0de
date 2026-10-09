package com.r0ybt.arachn0de.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class GameCorrectionTest {
    private val map = FirstGameMap.value
    private fun ready(character: RatCharacter = RatCharacter.KNIGHT): GameSession {
        val other = RatCharacter.entries.first { it != character }
        val s = GameRules.beginTurn(GameRules.newGame(listOf("A", "B"), listOf(character, other), map))
        return s.copy(players = s.players.mapIndexed { i, p -> p.copy(tileId = if (i == 0) "tile-4" else "tile-24") })
    }
    private fun move(s: GameSession, result: Int): GameSession {
        var moving = GameRules.finishRoll(GameRules.roll(s, result))
        while (moving.phase == TurnPhase.MOVING) moving = GameRules.step(moving, map)
        return moving
    }
    @Test fun battleMaskRemovesExteriorWithoutChangingForegroundColorsOrInteriorBlack() {
        val black = 0xFF000000.toInt()
        val art = 0xFF704010.toInt()
        val pixels = IntArray(25) { black }
        listOf(6, 7, 8, 11, 13, 16, 17, 18).forEach { pixels[it] = art }
        maskExteriorBlack(pixels, 5, 5)
        assertEquals(0, pixels[0] ushr 24)
        assertEquals(art, pixels[6])
        assertEquals(black, pixels[12])
        assertEquals(0, pixels[24] ushr 24)
    }
    @Test fun perimeterPreservesSavedIdsAndEveryConnectionIsAnAdjacentEdge() {
        assertEquals((0..26).map { "tile-$it" }.toSet(), map.tiles.keys)
        val positions = map.slots.filter { it.tileId != null }.associateBy { it.tileId!! }
        positions.values.forEach { assertTrue(it.row == 0 || it.row == map.rows - 1 || it.column == 0 || it.column == map.columns - 1) }
        map.tiles.values.forEach { tile -> tile.next.forEach { next ->
            val a = positions.getValue(tile.id); val b = positions.getValue(next)
            assertEquals(1, abs(a.row - b.row) + abs(a.column - b.column))
        } }
        val result = move(ready(), 3)
        assertEquals("tile-7", result.players[0].tileId)
    }
    @Test fun everyCharacterHasDirectionalVisionThroughCornersAndAtBothEnds() {
        RatCharacter.entries.forEach { character ->
            val forward = if (character == RatCharacter.HUNTRESS) 4 else 3
            val s = ready(character)
            assertEquals((2..4 + forward).map { "tile-$it" }.toSet(), GameVision.visibleTiles(s, map, "player-1"))
            val start = s.copy(players = s.players.map { it.copy(tileId = map.start) })
            assertEquals((0..forward).map { "tile-$it" }.toSet(), GameVision.visibleTiles(start, map, "player-1"))
            val goal = s.copy(players = s.players.map { it.copy(tileId = map.goal) })
            assertEquals((24..26).map { "tile-$it" }.toSet(), GameVision.visibleTiles(goal, map, "player-1"))
        }
    }
    @Test fun remoteZombieUnionIsTwoEachDirectionOnlyForActiveOwner() {
        val zombie = BoardObject("z", "player-1", "tile-20", ObjectType.ZOMBIE)
        val s = ready(RatCharacter.NECROMANCER).copy(objects = listOf(zombie))
        assertEquals(((2..7) + (18..22)).map { "tile-$it" }.toSet(), GameVision.visibleTiles(s, map, "player-1"))
        listOf(zombie.copy(active = false), zombie.copy(ownerPlayerId = "player-2"), zombie.copy(type = ObjectType.BANNER)).forEach {
            assertEquals((2..7).map { "tile-$it" }.toSet(), GameVision.visibleTiles(s.copy(objects = listOf(it)), map, "player-1"))
        }
        assertFalse("tile-20" in GameVision.visibleTiles(ready().copy(objects = listOf(zombie)), map, "player-1"))
    }
    @Test fun offensiveObjectsIgnorePassingAndResolveOnlyFinalLanding() {
        listOf(ObjectType.SPIKES, ObjectType.BEAR_TRAP, ObjectType.ZOMBIE).forEach { type ->
            val obj = BoardObject("o", "player-2", "tile-5", type)
            val s = ready().copy(objects = listOf(obj))
            val passing = move(s, 2)
            assertEquals(100, passing.players[0].health)
            assertEquals(0, passing.players[0].skippedTurns)
            assertTrue(passing.pendingDamage.isEmpty())
            assertEquals(listOf(obj), passing.objects)
            val landed = move(s, 1)
            assertEquals(100 - GameDefinitions.objects.getValue(type).damage, landed.players[0].health)
            assertEquals(if (type == ObjectType.BEAR_TRAP) 1 else 0, landed.players[0].skippedTurns)
            assertEquals(if (type == ObjectType.ZOMBIE) 1 else 0, landed.objects.size)
            assertEquals(type, landed.pendingDamage.single().source)
        }
    }
    @Test fun blockedPathLosesRemainingStepsAndResolvesEffectiveFinalTile() {
        val s = ready().copy(objects = listOf(BoardObject("ice", "player-2", "tile-6", ObjectType.ICE_BARRIER),
            BoardObject("spikes", "player-2", "tile-5", ObjectType.SPIKES)))
        val result = move(s, 6)
        assertEquals("tile-5", result.players[0].tileId)
        assertEquals(85, result.players[0].health)
        assertEquals(0, result.remainingSteps)
        assertTrue(result.objects.isEmpty())
        assertEquals(TurnPhase.HANDOFF, result.phase)
    }
    @Test fun enemyDynamicsStayHiddenUntilWithinDirectionalProjection() {
        val s = ready().copy(objects = listOf(BoardObject("trap", "player-2", "tile-8", ObjectType.BEAR_TRAP),
            BoardObject("own", "player-1", "tile-24", ObjectType.BANNER)))
        assertEquals(listOf("player-1"), GameVision.visiblePlayers(s, map, "player-1").map { it.id })
        assertTrue(GameVision.visibleObjects(s, map, "player-1").isEmpty())
        assertFalse("tile-24" in GameVision.visibleTiles(s, map, "player-1"))
    }
}
