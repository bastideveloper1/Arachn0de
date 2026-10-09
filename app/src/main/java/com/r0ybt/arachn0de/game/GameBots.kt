package com.r0ybt.arachn0de.game

import kotlin.random.Random

/** Only this projection crosses into bot policy. No seed, hidden entity, or opponent history. */
data class BotObservation(val player: GamePlayer, val map: GameMap, val visible: Set<String>,
    val players: List<GamePlayer>, val objects: List<BoardObject>, val spider: SpiderState?,
    val legalPlacements: Set<String>, val remainingSteps: Int = 0)

object GameBots {
    fun observe(s: GameSession, map: GameMap, playerId: String = s.currentPlayer.id): BotObservation {
        val visible = GameVision.visibleTiles(s, map, playerId)
        return BotObservation(s.players.first { it.id == playerId }, map.copy(id = "observed-board", seed = null), visible,
            GameVision.visiblePlayers(s, map, playerId).map { it.copy(routeHistory = emptyList(), explored = emptySet(), inventory = emptyList(), statuses = emptyList(), eyeUses = 0, eyeTurns = 0) },
            GameVision.visibleObjects(s, map, playerId), s.spider?.takeIf { !it.defeated && it.tileId in visible },
            if (s.currentPlayer.id == playerId) GameRules.legalPlacements(s, map) else emptySet(), if (s.currentPlayer.id == playerId) s.remainingSteps else 0)
    }
    fun route(view: BotObservation, choices: List<String>, random: Random = Random.Default): String {
        require(choices.isNotEmpty() && choices.all { it in view.map.tiles.getValue(view.player.tileId).next })
        if (view.player.control == PlayerControl.EASY) return choices.random(random)
        fun risk(tile: String, immediate: Boolean): Double {
            val known = tile in view.player.explored || tile in view.visible
            val swamp = known && view.map.tiles.getValue(tile).terrain == Terrain.SWAMP
            val traps = view.objects.filter { it.tileId == tile && it.ownerPlayerId != view.player.id }
            val enemy = view.players.any { it.id != view.player.id && it.tileId == tile }
            if (view.player.control == PlayerControl.MEDIUM) return (if (swamp) 3.0 else 0.0) + (if (traps.isNotEmpty()) 2.0 else 0.0) +
                (if (view.spider?.tileId == tile) 1.0 else 0.0)
            // Same fair d6: mean stride 3.5, 50% swamp escape,
            // 15/36 spider losses and 6/36 ties. No hidden entities influence cost.
            val landingWeight = if (immediate && view.remainingSteps == 1) 1.0 else 1.0 / 3.5
            return (if (swamp) 2.0 * 3.5 * landingWeight else 0.0) +
                traps.sumOf { GameDefinitions.objects.getValue(it.type).damage /
                    view.player.health.coerceAtLeast(1).toDouble() * 6 + if (it.type == ObjectType.ICE_BARRIER) 1.0 else 0.0 } +
                (if (view.spider?.tileId == tile) (15.0 / 36 * 2 / 3.5 + 6.0 / 36) else 0.0) +
                (if (enemy && view.player.health < 35) 1.5 else 0.0) + (if (!known) .05 else 0.0)
        }
        fun cost(origin: String): Double {
            val distances = mutableMapOf(origin to risk(origin, true)); val settled = hashSetOf<String>()
            while (true) {
                val entry = distances.filterKeys { it !in settled }.minByOrNull { it.value } ?: return Double.POSITIVE_INFINITY
                if (entry.key == view.map.goal) return entry.value
                settled.add(entry.key)
                view.map.tiles.getValue(entry.key).next.forEach { next ->
                    val distance = entry.value + 1.0 / 3.5 + risk(next, false)
                    if (distance < (distances[next] ?: Double.POSITIVE_INFINITY)) distances[next] = distance
                }
            }
        }
        return choices.minWith(compareBy<String> { cost(it) }.thenBy { it })
    }
    fun useEye(view: BotObservation, random: Random = Random.Default): Boolean {
        if (view.player.eyeUses == 0 || view.player.eyeTurns > 0) return false
        return when (view.player.control) {
            PlayerControl.EASY -> random.nextInt(8) == 0
            PlayerControl.MEDIUM -> view.map.tiles.getValue(view.player.tileId).next.size > 1
            PlayerControl.HARD -> GameVision.distances(view.map, view.player.tileId, GameExpansion.EYE_RADIUS).keys.count { it !in view.player.explored } >= 2
            PlayerControl.HUMAN -> false
        }
    }
    fun placement(view: BotObservation, random: Random = Random.Default): String? {
        if (view.objects.any { it.ownerPlayerId == view.player.id } || view.legalPlacements.isEmpty() || view.player.abilityCharges == 0) return null
        if (view.player.control == PlayerControl.EASY) return if (random.nextInt(6) == 0) view.legalPlacements.toList().random(random) else null
        val ability = GameDefinitions.characters.getValue(view.player.character).ability
        val wounded = view.player.health <= if (view.player.control == PlayerControl.HARD) 80 else 60
        return when (ability) {
            ObjectType.TOTEM -> view.player.tileId.takeIf { wounded && it in view.legalPlacements }
            ObjectType.BANNER -> view.player.tileId.takeIf { it in view.legalPlacements && view.players.any { p -> p.id != view.player.id } }
            ObjectType.ZOMBIE -> view.legalPlacements.maxByOrNull { GameVision.distances(view.map, it, 2).keys.count { tile -> tile !in view.player.explored } }
            else -> view.players.filter { it.id != view.player.id }.flatMap { view.map.tiles.getValue(it.tileId).next }
                .firstOrNull { it in view.legalPlacements }
        }
    }
}
