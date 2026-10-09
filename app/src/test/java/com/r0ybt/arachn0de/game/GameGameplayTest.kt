package com.r0ybt.arachn0de.game

import org.junit.Assert.*
import org.junit.Test

class GameGameplayTest {
    private val map = FirstGameMap.value
    private fun ready(character: RatCharacter = RatCharacter.KNIGHT, count: Int = 2, laps: Int = 1): GameSession {
        val characters = listOf(character) + RatCharacter.entries.filter { it != character }.take(count - 1)
        val game = GameRules.newGame((1..count).map { "P$it" }, characters, map, laps)
        return GameRules.beginTurn(game).copy(players = game.players.mapIndexed { i, p -> p.copy(tileId = if (i == 0) "tile-10" else "tile-24") })
    }
    private fun move(s: GameSession, die: Int): GameSession {
        var state = GameRules.finishRoll(GameRules.roll(s, die))
        while (state.phase == TurnPhase.MOVING) state = GameRules.step(state, map)
        return state
    }
    private fun obj(type: ObjectType, tile: String, owner: String = "player-2", id: String = "test-object") = BoardObject(id, owner, tile, type)
    @Test fun diceCommitsOutcomeBeforeAnimationAndFinishesOnEveryChosenFace() {
        (1..6).forEach { result ->
            val rolling = GameRules.roll(ready(), result)
            assertEquals(TurnPhase.ROLLING, rolling.phase)
            assertEquals("tile-10", rolling.currentPlayer.tileId)
            assertEquals(result, rolling.lastRoll)
            assertEquals(result, DiceAnimation.faces(result).last())
            assertTrue(DiceAnimation.faces(result).all { it in 1..6 })
            assertTrue(DiceAnimation.delays.zipWithNext().all { (a, b) -> a < b })
            assertTrue(DiceAnimation.delays.sum() in 1200..1500)
            val finished = GameRules.finishRoll(rolling)
            assertEquals(result, finished.lastRoll)
            assertEquals(result, finished.remainingSteps)
        }
    }
    @Test(expected = IllegalArgumentException::class) fun cannotRollTwice() {
        GameRules.roll(GameRules.roll(ready(), 2), 5)
    }
    @Test fun damageClampsHpAndRestRecoversExactlyOnce() {
        var s = GameRules.damage(ready(), map, "player-2", "player-1", 120)
        assertEquals(0, s.currentPlayer.health)
        assertTrue(s.currentPlayer.exhausted)
        s = GameRules.beginTurn(s.copy(phase = TurnPhase.HANDOFF))
        assertEquals(TurnPhase.DAMAGE, s.phase)
        s = GameRules.acknowledgeDamage(s)
        assertEquals(TurnPhase.REST, s.phase)
        s = GameRules.rest(s)
        assertEquals(50, s.players[0].health)
        assertFalse(s.players[0].exhausted)
        assertEquals(1, s.currentIndex)
        assertEquals(TurnPhase.HANDOFF, s.phase)
        s = move(GameRules.beginTurn(s), 1)
        assertEquals(0, s.currentIndex)
        assertEquals(TurnPhase.READY, GameRules.beginTurn(s).phase)
    }
    @Test fun multiplePendingDamageEventsResolveInOrderOnlyOnVictimsTurnWithoutApplyingTwice() {
        var s = GameRules.damage(ready(), map, "player-2", "player-1", 20)
        s = GameRules.damage(s, map, "player-2", "player-1", 15, ObjectType.ZOMBIE)
        assertEquals(65, s.currentPlayer.health)
        val otherTurn = s.copy(currentIndex = 1, phase = TurnPhase.HANDOFF)
        assertEquals(TurnPhase.READY, GameRules.beginTurn(otherTurn).phase)
        s = GameRules.beginTurn(s.copy(phase = TurnPhase.HANDOFF))
        assertEquals(20, GameRules.currentDamage(s)!!.damage)
        s = GameRules.acknowledgeDamage(s)
        assertEquals(TurnPhase.DAMAGE, s.phase)
        assertEquals(15, GameRules.currentDamage(s)!!.damage)
        s = GameRules.acknowledgeDamage(s)
        assertEquals(TurnPhase.READY, s.phase)
        assertEquals(65, s.currentPlayer.health)
        assertTrue(s.pendingDamage.isEmpty())
    }
    @Test fun everyCharacterGetsConfiguredAbilityUsesAndPlacementConsumesOne() {
        RatCharacter.entries.forEach { c ->
            var s = ready(c)
            assertEquals(GameDefinitions.characters.getValue(c).abilityUses, s.currentPlayer.abilityCharges)
            s = GameRules.startPlacement(s)
            s = GameRules.selectPlacement(s, map, "tile-11")
            s = GameRules.confirmPlacement(s, map)
            assertEquals(GameDefinitions.characters.getValue(c).abilityUses - 1, s.currentPlayer.abilityCharges)
            assertEquals(GameDefinitions.characters.getValue(c).ability, s.objects.single().type)
            assertEquals("player-1", s.objects.single().ownerPlayerId)
            assertEquals(TurnPhase.READY, s.phase)
            assertNull(s.selectedPlacementTile)
        }
    }
    @Test fun placementIsLimitedToVisibleGraphRangeAndZeroUsesAndCancelDoesNotConsume() {
        val s = ready()
        assertTrue("tile-13" in GameRules.legalPlacements(s, map))
        assertFalse("tile-14" in GameRules.legalPlacements(s, map))
        val blind = s.copy(players = s.players.map { if (it.id == "player-1") it.copy(visionModifier = -5) else it })
        assertEquals(setOf("tile-10"), GameRules.legalPlacements(blind, map))
        val zero = s.copy(players = s.players.map { if (it.id == "player-1") it.copy(abilityCharges = 0) else it })
        assertTrue(GameRules.legalPlacements(zero, map).isEmpty())
        assertEquals(s.currentPlayer.abilityCharges, GameRules.cancelPlacement(GameRules.startPlacement(s)).currentPlayer.abilityCharges)
    }
    @Test(expected = IllegalArgumentException::class) fun zeroUsesCannotEnterPlacement() {
        val s = ready().let { it.copy(players = it.players.map { p -> p.copy(abilityCharges = 0) }) }
        GameRules.startPlacement(s)
    }
    @Test(expected = IllegalArgumentException::class) fun outOfRangePlacementIsRejected() { GameRules.selectPlacement(GameRules.startPlacement(ready()), map, "tile-20") }
    @Test fun barriersCannotBePlacedUnderPlayersAtStartGoalOrOnAnotherObject() {
        val s = ready(RatCharacter.MAGE).let { it.copy(players = it.players.mapIndexed { i, p -> if (i == 1) p.copy(tileId = "tile-11") else p }) }
        assertFalse("tile-10" in GameRules.legalPlacements(s, map))
        assertFalse("tile-11" in GameRules.legalPlacements(s, map))
        val nearStart = s.copy(players = s.players.map { it.copy(tileId = map.start) })
        assertFalse(map.start in GameRules.legalPlacements(nearStart, map))
        val nearGoal = s.copy(players = s.players.map { it.copy(tileId = "tile-25") })
        assertFalse(map.goal in GameRules.legalPlacements(nearGoal, map))
        val occupied = ready().copy(objects = listOf(obj(ObjectType.BANNER, "tile-11")))
        assertFalse("tile-11" in GameRules.legalPlacements(occupied, map))
    }
    @Test fun spikesTriggerOnlyOnLandingAndDisappear() {
        val s = move(ready().copy(objects = listOf(obj(ObjectType.SPIKES, "tile-13"))), 3)
        assertEquals(85, s.players[0].health)
        assertEquals("tile-13", s.players[0].tileId)
        assertTrue(s.objects.isEmpty())
        assertEquals(ObjectType.SPIKES, s.pendingDamage.single().source)
    }
    @Test fun ownersDoNotTriggerTheirOwnTraps() {
        val s = move(ready().copy(objects = listOf(obj(ObjectType.SPIKES, "tile-11", "player-1"))), 1)
        assertEquals(100, s.players[0].health)
        assertEquals(1, s.objects.size)
    }
    @Test fun bearTrapDealsTwentyAndSkipsExactlyOneFutureTurn() {
        var s = move(ready().copy(objects = listOf(obj(ObjectType.BEAR_TRAP, "tile-11"))), 1)
        assertEquals(80, s.players[0].health)
        assertEquals(1, s.players[0].skippedTurns)
        assertTrue(s.objects.isEmpty())
        s = GameRules.beginTurn(s.copy(currentIndex = 0, phase = TurnPhase.HANDOFF))
        s = GameRules.acknowledgeDamage(s)
        assertEquals(TurnPhase.REST, s.phase)
        s = GameRules.rest(s)
        assertEquals(80, s.players[0].health)
        assertEquals(0, s.players[0].skippedTurns)
        assertEquals(TurnPhase.READY, GameRules.beginTurn(s.copy(currentIndex = 0, phase = TurnPhase.HANDOFF)).phase)
    }
    @Test fun trapAndExhaustionDueTogetherUseOneRest() {
        var s = ready().let { it.copy(players = it.players.map { p -> if (p.id == "player-1") p.copy(health = 10) else p }, objects = listOf(obj(ObjectType.BEAR_TRAP, "tile-11"))) }
        s = move(s, 1)
        assertEquals("tile-11", s.players[0].tileId)
        s = GameRules.acknowledgeDamage(GameRules.beginTurn(s.copy(currentIndex = 0)))
        s = GameRules.rest(s)
        assertEquals(50, s.players[0].health)
        assertEquals(0, s.players[0].skippedTurns)
        assertFalse(s.players[0].exhausted)
    }
    @Test fun totemHealsTwentyAtFinalProximityAndCapsHpAndConsumes() {
        listOf(60 to 80, 95 to 100).forEach { (before, after) ->
            val s = ready(RatCharacter.MONK).let { it.copy(players = it.players.map { p -> if (p.id == "player-1") p.copy(health = before) else p },
                objects = listOf(obj(ObjectType.TOTEM, "tile-13", "player-1"))) }
            val moved = move(s, 1)
            assertEquals(after, moved.players[0].health)
            assertTrue(moved.objects.isEmpty())
        }
    }
    @Test fun totemDoesNotTriggerWhilePassingItsRadiusIfFinalPositionIsFarAway() {
        val s = ready(RatCharacter.MONK).let { it.copy(players = it.players.map { p -> if (p.id == "player-1") p.copy(health = 60) else p }, objects = listOf(obj(ObjectType.TOTEM, "tile-10", "player-1"))) }
        val moved = move(s, 4)
        assertEquals(60, moved.players[0].health)
        assertEquals(1, moved.objects.size)
    }
    @Test fun bannerHalvesDamageAtTwoGraphEdgesDoesNotStackAndRoundsUp() {
        val s = ready().copy(objects = listOf(obj(ObjectType.BANNER, "tile-8", "player-1"), obj(ObjectType.BANNER, "tile-9", "player-1", "second")))
        assertEquals(87, GameRules.damage(s, map, "player-2", "player-1", 25).currentPlayer.health)
        assertEquals(90, GameRules.damage(s, map, "player-2", "player-1", 20).currentPlayer.health)
        val far = s.copy(objects = listOf(obj(ObjectType.BANNER, "tile-7", "player-1")))
        assertEquals(75, GameRules.damage(far, map, "player-2", "player-1", 25).currentPlayer.health)
    }
    @Test fun barrierStopsBeforeEntryAndMeltsSoRaceCanContinue() {
        val s = move(ready().copy(objects = listOf(obj(ObjectType.ICE_BARRIER, "tile-12"))), 6)
        assertEquals("tile-11", s.players[0].tileId)
        assertTrue(s.objects.isEmpty())
        assertEquals(0, s.remainingSteps)
        assertEquals(TurnPhase.HANDOFF, s.phase)
    }
    @Test fun zombieDealsFifteenAndPersists() {
        val s = move(ready().copy(objects = listOf(obj(ObjectType.ZOMBIE, "tile-11"))), 1)
        assertEquals(85, s.players[0].health)
        assertEquals(1, s.objects.size)
    }
    @Test fun zombieProvidesRemoteVisionOnlyToNecromancerOwner() {
        val s = ready(RatCharacter.NECROMANCER).copy(objects = listOf(obj(ObjectType.ZOMBIE, "tile-23", "player-1")))
        assertTrue("tile-21" in GameVision.visibleTiles(s, map, "player-1"))
        assertFalse(map.goal in GameVision.visibleTiles(s, map, "player-1"))
        val otherOwner = s.copy(objects = listOf(obj(ObjectType.ZOMBIE, "tile-23", "player-2")))
        assertFalse("tile-23" in GameVision.visibleTiles(otherOwner, map, "player-1"))
        val nonNecromancer = ready().copy(objects = listOf(obj(ObjectType.ZOMBIE, "tile-23", "player-1")))
        assertFalse("tile-23" in GameVision.visibleTiles(nonNecromancer, map, "player-1"))
    }
    @Test fun fogShowsNearEnemiesHidesFarEnemiesAndOwnObjectsDoNotGrantVision() {
        val s = ready().copy(objects = listOf(obj(ObjectType.BANNER, "tile-24", "player-1"), obj(ObjectType.SPIKES, "tile-23")))
        assertEquals(listOf("player-1"), GameVision.visiblePlayers(s, map, "player-1").map { it.id })
        assertTrue(GameVision.visibleObjects(s, map, "player-1").isEmpty())
        val near = s.copy(players = s.players.map { if (it.id == "player-2") it.copy(tileId = "tile-13") else it })
        assertEquals(2, GameVision.visiblePlayers(near, map, "player-1").size)
        assertFalse("tile-24" in GameVision.visibleTiles(s, map, "player-1"))
    }
    @Test fun visionUsesGraphRatherThanCoordinatesAndClampsAtRouteEnds() {
        assertEquals((0..5).map { "tile-$it" }.toSet(), GameVision.distances(map, map.start, 5).keys)
        assertEquals((21..26).map { "tile-$it" }.toSet(), GameVision.distances(map, map.goal, 5).keys)
        val fork = map.copy(tiles = map.tiles + (map.start to GameTile(map.start, listOf("tile-1", "tile-20"))))
        assertTrue("tile-20" in GameVision.distances(fork, map.start, 1))
        assertEquals(1, GameVision.distances(fork, "tile-20", 1)[map.start])
    }
    @Test fun combatOccursOnlyAtFinalTileAndCanTargetMultipleOpponents() {
        val s = ready(count = 3).let { it.copy(players = it.players.mapIndexed { i, p -> if (i == 0) p else p.copy(tileId = "tile-11") }) }
        val passing = move(s, 2)
        assertTrue(passing.pendingDamage.isEmpty())
        val landed = move(s, 1)
        assertEquals(2, landed.pendingDamage.size)
        assertTrue(landed.players.drop(1).all { it.health == 75 })
        assertEquals(TurnPhase.DAMAGE, GameRules.beginTurn(landed).phase)
    }
    @Test fun characterAttackDamageIsCentralized() {
        val expected = listOf(25, 20, 20, 15, 15, 20)
        assertEquals(expected, RatCharacter.entries.map { GameDefinitions.characters.getValue(it).attackDamage })
        RatCharacter.entries.forEach { assertEquals(100, GameDefinitions.characters.getValue(it).maxHp) }
    }
    @Test fun everyLapCountResetsAtGoalUntilChosenNumberThenRanksWithoutEndingFirstOfFour() {
        (1..4).forEach { laps ->
            var s = ready(count = 4, laps = laps)
            repeat(laps) { index ->
                s = s.copy(currentIndex = 0, phase = TurnPhase.READY, players = s.players.map { if (it.id == "player-1") it.copy(tileId = "tile-25") else it })
                s = move(s, 6)
                assertEquals(index + 1, s.players[0].completedLaps)
                if (index + 1 < laps) { assertEquals(map.start, s.players[0].tileId); assertTrue(s.ranking.isEmpty()) }
            }
            assertEquals(listOf("player-1"), s.ranking)
            assertEquals(TurnPhase.HANDOFF, s.phase)
            assertEquals(1, s.currentIndex)
        }
    }
    @Test fun classificationKeepsArrivalOrderSkipsFinishersAndAutomaticallyRanksLast() {
        var s = ready(count = 4)
        val order = listOf(2, 0, 3)
        order.forEachIndexed { index, player ->
            s = s.copy(currentIndex = player, phase = TurnPhase.READY, players = s.players.mapIndexed { i, p -> if (i == player) p.copy(tileId = "tile-25") else p })
            s = move(s, 1)
            if (index < 2) { assertEquals(TurnPhase.HANDOFF, s.phase); assertTrue(s.currentPlayer.id !in s.ranking) }
        }
        assertEquals(listOf("player-3", "player-1", "player-4", "player-2"), s.ranking)
        assertEquals(TurnPhase.WON, s.phase)
        assertEquals("player-3", s.winnerId)
    }
}
