package com.r0ybt.arachn0de.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class GameExpansionTest {
    private val map = GameMapGenerator.generate(72)
    private fun ready(m: GameMap = map) = GameRules.beginTurn(GameRules.newGame(listOf("A", "B"), RatCharacter.entries.take(2), m))
    private fun at(s: GameSession, tile: String, history: List<String> = listOf(tile)) = s.copy(players = s.players.mapIndexed { i, p ->
        if (i == s.currentIndex) p.copy(tileId = tile, routeHistory = history) else p
    })
    private fun finish(s: GameSession, m: GameMap = map): GameSession {
        var state = s
        while (state.phase in setOf(TurnPhase.MOVING, TurnPhase.ANIMATING, TurnPhase.CHOOSING_ROUTE, TurnPhase.RESOLVING)) {
            state = when (state.phase) {
                TurnPhase.MOVING -> GameExpansion.planStep(state, m)
                TurnPhase.CHOOSING_ROUTE -> GameExpansion.planStep(state, m, m.next(state.currentPlayer.tileId))
                TurnPhase.ANIMATING -> GameExpansion.arrive(state, m)
                else -> GameExpansion.resolveLanding(state, m)
            }
        }
        return state
    }
    @Test fun generatedMapsAreReproducibleConnectedLongerAndPhysicalAcrossFiveHundredSeeds() {
        repeat(500) { seed ->
            val m = GameMapGenerator.generate(seed.toLong()); assertEquals(m, GameMapGenerator.generate(seed.toLong()))
            validateGeneratedMap(m)
            assertEquals(43, m.mainRoute.size); assertTrue(m.mainRoute.size.toDouble() / FirstGameMap.value.tiles.size in 1.5..1.75)
            assertTrue(m.tiles.values.count { it.next.size == 2 } in 3..4)
            assertEquals(6, m.tiles.values.count { it.terrain == Terrain.SWAMP })
            assertTrue(m.slots.any { it.type == SlotType.CAVE_WALL })
            val distances = mutableMapOf(m.start to 0)
            val queue = ArrayDeque<String>().apply { add(m.start) }
            while (queue.isNotEmpty()) {
                val tile = queue.removeFirst()
                m.tiles.getValue(tile).next.forEach { next ->
                    if (next !in distances) { distances[next] = distances.getValue(tile) + 1; queue.add(next) }
                }
            }
            assertTrue("Even shortcuts must lengthen the original game", distances.getValue(m.goal) >= 32)
        }
        assertNotEquals(GameMapGenerator.generate(1), GameMapGenerator.generate(2))
    }
    @Test fun swampsAvoidStartGoalAndCriticalForksAndCaveWallsNeverOccupyPaths() {
        val incoming = map.tiles.values.flatMap { it.next }.groupingBy { it }.eachCount()
        map.tiles.values.filter { it.terrain == Terrain.SWAMP }.forEach { assertNotEquals(map.start, it.id); assertNotEquals(map.goal, it.id); assertEquals(1, it.next.size); assertEquals(1, incoming[it.id]) }
        map.slots.filter { it.type == SlotType.CAVE_WALL }.forEach { wall ->
            assertNull(wall.tileId); assertTrue(wall.rotation in listOf(0, 90, 180, 270))
            assertTrue(map.slots.any { floor -> floor.tileId?.let { map.tiles.getValue(it).terrain == Terrain.CAVE } == true && kotlin.math.abs(floor.row - wall.row) + kotlin.math.abs(floor.column - wall.column) == 1 })
        }
    }
    @Test fun everyShortcutHasEntryExitAndAdvancesThroughRealAdjacentTiles() {
        map.tiles.values.filter { it.next.size == 2 }.forEach { fork ->
            var id = fork.next.last(); val route = mutableListOf(fork.id)
            while (id !in map.mainRoute) { route.add(id); id = map.next(id)!! }; route.add(id)
            assertTrue(route.size >= 5); assertTrue(map.mainRoute.indexOf(id) > map.mainRoute.indexOf(fork.id))
            assertTrue(route.size - 1 < map.mainRoute.indexOf(id) - map.mainRoute.indexOf(fork.id))
            assertTrue(route.drop(1).dropLast(1).all { map.tiles.getValue(it).terrain in setOf(Terrain.CAVE, Terrain.DRY_GRASS) })
        }
    }
    @Test fun visualAcknowledgementPrecedesEffectsAndTurnRotation() {
        val start = ready(FirstGameMap.value).copy(objects = listOf(BoardObject("spikes", "player-2", "tile-1", ObjectType.SPIKES)))
        val moving = GameExpansion.finishRoll(GameRules.roll(start, 1))
        val animated = GameExpansion.planStep(moving, FirstGameMap.value)
        assertEquals(TurnPhase.ANIMATING, animated.phase); assertEquals(0, animated.currentIndex); assertEquals(100, animated.currentPlayer.health)
        assertEquals("tile-0", animated.motion!!.from); assertEquals("tile-1", animated.motion.to)
        val arrived = GameExpansion.arrive(animated, FirstGameMap.value)
        assertEquals(TurnPhase.RESOLVING, arrived.phase); assertNull(arrived.resultDeadline)
        val resolved = GameExpansion.resolveLanding(arrived, FirstGameMap.value)
        assertEquals(85, resolved.currentPlayer.health); assertEquals(TurnPhase.RESULT, resolved.phase); assertEquals(0, resolved.currentIndex)
        val presented = GameExpansion.presentResult(resolved, 1000)
        assertEquals(11000L, presented.resultDeadline)
        assertEquals(1, GameExpansion.continueTurn(presented, FirstGameMap.value).currentIndex)
    }
    @Test fun pendingRouteAndAnimationRejectEarlyTurnAdvanceAndDuplicateAcknowledgement() {
        val fork = map.tiles.values.first { it.next.size > 1 }
        val choice = GameExpansion.planStep(GameExpansion.finishRoll(GameRules.roll(at(ready(), fork.id), 2)), map)
        assertEquals(TurnPhase.CHOOSING_ROUTE, choice.phase)
        assertThrows(IllegalArgumentException::class.java) { GameExpansion.continueTurn(choice, map) }
        val animated = GameExpansion.planStep(choice, map, fork.next.last())
        assertThrows(IllegalArgumentException::class.java) { GameExpansion.continueTurn(animated, map) }
        val arrived = GameExpansion.arrive(animated, map)
        assertThrows(IllegalArgumentException::class.java) { GameExpansion.arrive(arrived, map) }
    }
    @Test fun swampEscapeExactlyMatchesEachFaceAndConsumesTheWholeTurn() {
        val swamp = map.tiles.values.first { it.terrain == Terrain.SWAMP }.id
        (1..6).forEach { die ->
            val s = at(ready(), swamp).let { it.copy(players = it.players.map { p -> if (p.id == it.currentPlayer.id) p.copy(trapped = true) else p }) }
            val result = GameExpansion.finishRoll(GameRules.roll(s, die))
            assertEquals(die <= 3, result.currentPlayer.trapped); assertEquals(swamp, result.currentPlayer.tileId)
            assertEquals(TurnPhase.RESULT, result.phase); assertEquals(0, result.remainingSteps)
        }
    }
    @Test fun landingTriggersSwampWhilePassingDoesNot() {
        val swamp = map.mainRoute.first { map.tiles.getValue(it).terrain == Terrain.SWAMP }
        val before = map.mainRoute[map.mainRoute.indexOf(swamp) - 1]
        val land = finish(GameExpansion.finishRoll(GameRules.roll(at(ready(), before), 1)))
        assertTrue(land.currentPlayer.trapped)
        val next = map.next(swamp)!!
        val pass = finish(GameExpansion.finishRoll(GameRules.roll(at(ready(), before), 2)))
        assertEquals(next, pass.currentPlayer.tileId); assertFalse(pass.currentPlayer.trapped)
    }
    @Test fun exactlyOneSpiderExistsInCaveAndDiscoveryIsIndividual() {
        repeat(50) { val m = GameMapGenerator.generate(it.toLong()); val s = ready(m)
            assertNotNull(s.spider); assertEquals(Terrain.CAVE, m.tiles.getValue(s.spider!!.tileId).terrain)
            val far = m.mainRoute.last()
            val split = at(s, s.spider.tileId).copy(players = at(s, s.spider.tileId).players.mapIndexed { i, p -> if (i == 1) p.copy(tileId = far) else p })
            assertNotNull(GameBots.observe(split, m, "player-1").spider)
            if (s.spider.tileId !in GameVision.visibleTiles(split, m, "player-2")) assertNull(GameBots.observe(split, m, "player-2").spider)
        }
    }
    private fun fighting(): GameSession {
        val spider = ready().spider!!
        val incoming = map.tiles.values.first { spider.tileId in it.next }.id
        return GameExpansion.resolveLanding(at(ready(), spider.tileId, listOf(incoming, spider.tileId)).copy(phase = TurnPhase.RESOLVING), map)
    }
    @Test fun spiderWinsTiesAndLossesRespectAllThirtySixDicePairs() {
        (1..6).forEach { a -> (1..6).forEach { b ->
            val s = GameExpansion.combat(fighting(), map, a, b)
            assertEquals(a > b, s.spider!!.defeated)
            assertEquals(a == b, s.currentPlayer.engagedSpider)
            assertEquals(if (a < b) TurnPhase.ANIMATING else TurnPhase.RESULT, s.phase)
        } }
    }
    @Test fun losingRetreatUsesRecordedBranchAndBypassesLandingEffects() {
        val cave = map.tiles.values.first { it.terrain == Terrain.CAVE }
        val previous = map.tiles.values.first { cave.id in it.next }
        val before = map.tiles.values.first { previous.id in it.next }
        val s = at(ready(), cave.id, listOf(before.id, previous.id, cave.id)).copy(spider = SpiderState(cave.id), phase = TurnPhase.COMBAT,
            objects = listOf(BoardObject("trap", "player-2", before.id, ObjectType.SPIKES)))
        val back = finish(GameExpansion.combat(s, map, 1, 6))
        assertEquals(before.id, back.currentPlayer.tileId); assertEquals(listOf(before.id), back.currentPlayer.routeHistory)
        assertEquals(100, back.currentPlayer.health); assertEquals(1, back.objects.size); assertEquals(TurnPhase.RESULT, back.phase)
    }
    @Test fun defeatedSpiderNeverReturnsAndEndsOtherPendingEncounters() {
        val s = fighting().let { it.copy(players = it.players.map { p -> p.copy(tileId = it.spider!!.tileId, engagedSpider = true) }) }
        val won = GameExpansion.combat(s, map, 6, 1)
        assertTrue(won.spider!!.defeated); assertTrue(won.players.none { it.engagedSpider })
    }
    @Test fun sharedTilesDoNotBlockWalkingAndEnemiesOnlyAttackOnFinalTile() {
        val m = FirstGameMap.value
        val s = ready(m).let { it.copy(players = it.players.mapIndexed { i, p -> if (i == 1) p.copy(tileId = "tile-1") else p }) }
        val pass = finish(GameExpansion.finishRoll(GameRules.roll(s, 2)), m)
        assertEquals(100, pass.players[1].health)
        val land = finish(GameExpansion.finishRoll(GameRules.roll(s, 1)), m)
        assertEquals(75, land.players[1].health); assertEquals(land.players[0].tileId, land.players[1].tileId)
    }
    @Test fun eyeCostsNoMovementHasThreeUsesAndLastsTwoOwnTurns() {
        val m = FirstGameMap.value; val s = at(ready(m), "tile-10")
        val eye = GameExpansion.explore(s, m)
        assertEquals(2, eye.currentPlayer.eyeUses); assertEquals(2, eye.currentPlayer.eyeTurns); assertEquals(s.lastRoll, eye.lastRoll)
        assertTrue("tile-6" in GameVision.visibleTiles(eye, m, "player-1"))
        val result = finish(GameExpansion.finishRoll(GameRules.roll(eye, 1)), m)
        val next = GameExpansion.continueTurn(GameExpansion.presentResult(result, 0), m)
        assertEquals(1, next.players[0].eyeTurns)
        val again = next.copy(currentIndex = 0, phase = TurnPhase.READY)
        val expired = GameExpansion.continueTurn(GameExpansion.presentResult(finish(GameExpansion.finishRoll(GameRules.roll(again, 1)), m), 0), m)
        assertEquals(0, expired.players[0].eyeTurns); assertTrue("tile-6" in expired.players[0].explored)
        assertFalse("tile-6" in GameVision.visibleTiles(expired, m, "player-1"))
        assertThrows(IllegalArgumentException::class.java) { GameExpansion.explore(eye, m) }
        assertThrows(IllegalArgumentException::class.java) { GameExpansion.explore(s.copy(players = s.players.map { it.copy(eyeUses = 0) }), m) }
    }
    @Test fun fogSeparatesVisibleRememberedAndNeverExploredForEachPlayer() {
        val m = FirstGameMap.value
        val s = GameExpansion.refreshVision(at(ready(m), "tile-10"), m)
        val moved = GameExpansion.refreshVision(at(s, "tile-20"), m)
        val fog = GameFog.cells(moved, m, "player-1")
        val slot = m.slots.first { it.tileId == "tile-10" }
        assertEquals(FogLevel.EXPLORED, fog[slot.row to slot.column])
        assertNotEquals(moved.players[0].explored, moved.players[1].explored)
        assertTrue(fog.values.containsAll(FogLevel.entries))
    }
    @Test fun eyeExpiryKeepsTerrainButHidesEnemiesAndObjects() {
        val m = FirstGameMap.value
        val s = at(ready(m), "tile-10").let { it.copy(players = it.players.mapIndexed { i, p ->
            if (i == 1) p.copy(tileId = "tile-6") else p
        }, objects = listOf(BoardObject("hidden", "player-2", "tile-6", ObjectType.SPIKES))) }
        assertEquals(1, GameVision.visiblePlayers(s, m, "player-1").size)
        val eye = GameExpansion.explore(s, m)
        assertEquals(2, GameVision.visiblePlayers(eye, m, "player-1").size)
        assertEquals(1, GameVision.visibleObjects(eye, m, "player-1").size)
        val expired = eye.copy(players = eye.players.map { it.copy(eyeTurns = 0) })
        assertTrue("tile-6" in expired.players[0].explored)
        assertEquals(1, GameVision.visiblePlayers(expired, m, "player-1").size)
        assertTrue(GameVision.visibleObjects(expired, m, "player-1").isEmpty())
    }
    @Test fun botProjectionAndDecisionsAreUnaffectedByHiddenEnemiesOrGeneratorSeed() {
        val m = FirstGameMap.value
        val base = ready(m).copy(players = ready(m).players.mapIndexed { i, p -> if (i == 0) p.copy(control = PlayerControl.HARD) else p.copy(tileId = "tile-20") })
        val hidden = base.copy(players = base.players.mapIndexed { i, p -> if (i == 1) p.copy(tileId = "tile-21", health = 1, explored = setOf("tile-22")) else p },
            objects = listOf(BoardObject("secret", "player-2", "tile-19", ObjectType.ICE_BARRIER)))
        assertEquals(GameBots.observe(base, m), GameBots.observe(hidden, m))
        val generated = ready().copy(players = ready().players.map { it.copy(control = PlayerControl.HARD) })
        assertNull(GameBots.observe(generated, map).map.seed); assertEquals("observed-board", GameBots.observe(generated, map).map.id)
    }
    @Test fun difficultiesChangeRoutePlanningAndResourceDecisionsRatherThanDice() {
        val original = FirstGameMap.value
        val fork = original.copy(tiles = original.tiles + (original.start to GameTile(original.start, listOf("tile-1", "tile-4"))))
        val base = ready(fork)
        fun view(control: PlayerControl) = GameBots.observe(base.copy(players = base.players.mapIndexed { i, p ->
            if (i == 0) p.copy(control = control) else p
        }), fork)
        val choices = fork.tiles.getValue(fork.start).next
        assertEquals(choices.toSet(), (0..50).map { GameBots.route(view(PlayerControl.EASY), choices, Random(it)) }.toSet())
        assertEquals("tile-4", GameBots.route(view(PlayerControl.MEDIUM), choices))
        assertEquals("tile-4", GameBots.route(view(PlayerControl.HARD), choices))
        val monk = GameRules.beginTurn(GameRules.newGame(listOf("A", "B"), listOf(RatCharacter.MONK, RatCharacter.MAGE), original))
            .let { it.copy(players = it.players.mapIndexed { i, p -> if (i == 0) p.copy(health = 70) else p.copy(tileId = "tile-20") }) }
        fun healing(control: PlayerControl) = GameBots.observe(monk.copy(players = monk.players.mapIndexed { i, p -> if (i == 0) p.copy(control = control) else p }), original)
        assertNull(GameBots.placement(healing(PlayerControl.MEDIUM)))
        assertEquals(original.start, GameBots.placement(healing(PlayerControl.HARD)))
        assertEquals(GameDefinitions.characters.getValue(RatCharacter.MONK).abilityUses, healing(PlayerControl.HARD).player.abilityCharges)
    }
    @Test fun allDifficultiesFinishOfflineMatchesUsingTheSameRulesAndFairDice() {
        PlayerControl.entries.filter { it != PlayerControl.HUMAN }.forEach { difficulty -> repeat(8) { seed ->
            val m = GameMapGenerator.generate(seed.toLong()); val random = Random(seed + 100)
            var s = GameRules.newGame(listOf("A", "B"), RatCharacter.entries.take(2), m, controls = listOf(difficulty, difficulty))
            var actions = 0
            while (s.phase != TurnPhase.WON && actions++ < 3000) {
                val view = GameBots.observe(s, m)
                s = when (s.phase) {
                    TurnPhase.HANDOFF -> GameRules.beginTurn(s)
                    TurnPhase.DAMAGE -> GameRules.acknowledgeDamage(s)
                    TurnPhase.REST -> GameExpansion.rest(s)
                    TurnPhase.READY -> when { GameBots.placement(view, random) != null -> GameRules.startPlacement(s)
                        GameBots.useEye(view, random) -> GameExpansion.explore(s, m)
                        else -> GameRules.roll(s, GameRules.rollD6(random)) }
                    TurnPhase.PLACING -> if (s.selectedPlacementTile != null) GameRules.confirmPlacement(s, m)
                        else GameRules.selectPlacement(s, m, GameBots.placement(view, random) ?: view.legalPlacements.first())
                    TurnPhase.ROLLING -> GameExpansion.finishRoll(s)
                    TurnPhase.MOVING -> GameExpansion.planStep(s, m)
                    TurnPhase.CHOOSING_ROUTE -> GameExpansion.planStep(s, m, GameBots.route(view, m.tiles.getValue(s.currentPlayer.tileId).next, random))
                    TurnPhase.ANIMATING -> GameExpansion.arrive(s, m)
                    TurnPhase.RESOLVING -> GameExpansion.resolveLanding(s, m)
                    TurnPhase.COMBAT -> GameExpansion.combat(s, m, GameRules.rollD6(random), GameRules.rollD6(random))
                    TurnPhase.RESULT -> GameExpansion.continueTurn(GameExpansion.presentResult(s, actions.toLong()), m)
                    else -> error("Unexpected bot phase")
                }
            }
            assertEquals("$difficulty seed=$seed actions=$actions", TurnPhase.WON, s.phase)
        } }
    }
}
