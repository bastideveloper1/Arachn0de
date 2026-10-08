package com.r0ybt.arachn0de.game

import kotlin.random.Random

object GameRules {
    fun newGame(names: List<String>, characters: List<RatCharacter>, map: GameMap, laps: Int = 1): GameSession {
        require(names.size in 2..4 && names.size == characters.size && laps in 1..4)
        require(characters.distinct().size == characters.size)
        require(names.all { it.trim().isNotEmpty() && it.trim().length <= 16 })
        return GameSession(map.id, names.indices.map { i ->
            val definition = GameDefinitions.characters.getValue(characters[i])
            GamePlayer("player-${i + 1}", names[i].trim(), characters[i], map.start, TokenQuadrant.entries[i],
                abilityCharges = definition.abilityUses)
        }, targetLaps = laps)
    }
    fun rollD6(random: Random = Random.Default): Int = random.nextInt(1, 7)
    private fun replace(s: GameSession, p: GamePlayer) = s.copy(players = s.players.map { if (it.id == p.id) p else it })
    fun beginTurn(s: GameSession): GameSession {
        require(s.phase == TurnPhase.HANDOFF)
        return if (s.pendingDamage.any { it.victimId == s.currentPlayer.id }) s.copy(phase = TurnPhase.DAMAGE) else afterDamage(s)
    }
    fun currentDamage(s: GameSession): PendingDamage? = s.pendingDamage.firstOrNull { it.victimId == s.currentPlayer.id }
    fun acknowledgeDamage(s: GameSession): GameSession {
        require(s.phase == TurnPhase.DAMAGE)
        val event = requireNotNull(currentDamage(s))
        val next = s.copy(pendingDamage = s.pendingDamage.filterNot { it.id == event.id })
        return if (currentDamage(next) != null) next else afterDamage(next)
    }
    private fun afterDamage(s: GameSession) = s.copy(phase = if (s.currentPlayer.exhausted || s.currentPlayer.skippedTurns > 0) TurnPhase.REST else TurnPhase.READY)
    fun rest(s: GameSession): GameSession {
        require(s.phase == TurnPhase.REST)
        val player = s.currentPlayer
        // A trap and exhaustion due on the same turn are both discharged by this one rest.
        return endTurn(replace(s, player.copy(health = if (player.exhausted) GameDefinitions.RECOVERY_HP else player.health,
            exhausted = false, skippedTurns = (player.skippedTurns - 1).coerceAtLeast(0))))
    }
    fun roll(s: GameSession, result: Int): GameSession {
        require(s.phase == TurnPhase.READY && result in 1..6)
        return s.copy(phase = TurnPhase.ROLLING, lastRoll = result, remainingSteps = result, lastPlayerId = s.currentPlayer.id)
    }
    fun finishRoll(s: GameSession): GameSession {
        require(s.phase == TurnPhase.ROLLING && s.lastRoll in 1..6)
        return s.copy(phase = TurnPhase.MOVING)
    }
    fun startPlacement(s: GameSession): GameSession {
        require(s.phase == TurnPhase.READY && s.currentPlayer.abilityCharges > 0)
        return s.copy(phase = TurnPhase.PLACING, selectedPlacementTile = null)
    }
    fun cancelPlacement(s: GameSession): GameSession {
        require(s.phase == TurnPhase.PLACING)
        return s.copy(phase = TurnPhase.READY, selectedPlacementTile = null)
    }
    fun legalPlacements(s: GameSession, map: GameMap): Set<String> {
        if (s.phase !in setOf(TurnPhase.READY, TurnPhase.PLACING) || s.currentPlayer.abilityCharges <= 0) return emptySet()
        val player = s.currentPlayer
        val ability = GameDefinitions.characters.getValue(player.character).ability
        val nearby = GameVision.distances(map, player.tileId, GameDefinitions.PLACEMENT_RANGE).keys
        val visible = GameVision.visibleTiles(s, map, player.id)
        return nearby.intersect(visible).filter { id ->
            // One object per tile prevents stacking ambiguities. Placement checks use full authoritative state.
            s.objects.none { it.active && it.tileId == id } && (ability != ObjectType.ICE_BARRIER ||
                (id != map.start && id != map.goal && s.players.none { it.id !in s.ranking && it.tileId == id }))
        }.toSet()
    }
    fun selectPlacement(s: GameSession, map: GameMap, tileId: String): GameSession {
        require(s.phase == TurnPhase.PLACING && tileId in legalPlacements(s, map))
        return s.copy(selectedPlacementTile = tileId)
    }
    fun confirmPlacement(s: GameSession, map: GameMap): GameSession {
        val tile = requireNotNull(s.selectedPlacementTile)
        require(s.phase == TurnPhase.PLACING && tile in legalPlacements(s, map))
        val player = s.currentPlayer
        val obj = BoardObject("object-${s.nextEventId}", player.id, tile, GameDefinitions.characters.getValue(player.character).ability)
        return replace(s, player.copy(abilityCharges = player.abilityCharges - 1)).copy(
            objects = s.objects + obj, phase = TurnPhase.READY, selectedPlacementTile = null, nextEventId = s.nextEventId + 1)
    }
    fun damage(s: GameSession, map: GameMap, attackerId: String, victimId: String, amount: Int, source: ObjectType? = null): GameSession {
        require(amount >= 0 && s.players.any { it.id == attackerId })
        val victim = s.players.first { it.id == victimId }
        val reduction = s.objects.filter { obj ->
            val definition = GameDefinitions.objects.getValue(obj.type)
            obj.active && obj.ownerPlayerId == victimId && definition.damageReductionPercent > 0 &&
                victim.tileId in GameVision.distances(map, obj.tileId, definition.radius)
        }.maxOfOrNull { GameDefinitions.objects.getValue(it.type).damageReductionPercent } ?: 0
        val reduced = ((amount.toLong() * (100 - reduction) + 99) / 100).toInt() // Round up; banners never stack.
        val health = (victim.health - reduced).coerceIn(0, GameDefinitions.MAX_HP)
        val event = PendingDamage("damage-${s.nextEventId}", attackerId, victimId, victim.health - health, source, victim.health, health)
        return replace(s, victim.copy(health = health, exhausted = victim.exhausted || health == 0)).copy(
            pendingDamage = s.pendingDamage + event, nextEventId = s.nextEventId + 1)
    }
    fun step(s: GameSession, map: GameMap): GameSession {
        require(s.mapId == map.id && s.phase == TurnPhase.MOVING && s.remainingSteps > 0)
        val destination = map.next(s.currentPlayer.tileId)
        if (destination == null) return finishMovement(s, map)
        val barrier = s.objects.firstOrNull { it.active && it.tileId == destination && it.ownerPlayerId != s.currentPlayer.id &&
            GameDefinitions.objects.getValue(it.type).trigger == EffectTrigger.ON_PATH_BLOCK }
        // A barrier blocks one attempted crossing then melts, so a linear route cannot become unwinnable.
        if (barrier != null) return finishMovement(s.copy(objects = s.objects.filterNot { it.id == barrier.id }), map)
        val moved = replace(s, s.currentPlayer.copy(tileId = destination)).copy(remainingSteps = s.remainingSteps - 1)
        return if (destination == map.goal || moved.remainingSteps == 0 || moved.currentPlayer.exhausted || map.next(destination) == null)
            finishMovement(moved, map) else moved
    }
    private fun finishMovement(s: GameSession, map: GameMap): GameSession {
        var result = s.copy(remainingSteps = 0)
        val playerId = s.currentPlayer.id
        val objects = result.objects.filter { it.active && it.tileId == result.currentPlayer.tileId && it.ownerPlayerId != result.currentPlayer.id }
        for (obj in objects) {
            val definition = GameDefinitions.objects.getValue(obj.type)
            if (definition.trigger != EffectTrigger.ON_LAND) continue
            result = damage(result, map, obj.ownerPlayerId, result.currentPlayer.id, definition.damage, obj.type)
            if (definition.skipsTurn) result = replace(result, result.currentPlayer.copy(skippedTurns = result.currentPlayer.skippedTurns + 1))
            if (definition.consumable) result = result.copy(objects = result.objects.filterNot { it.id == obj.id })
        }
        result.objects.filter { it.active && it.ownerPlayerId == playerId }.forEach { obj ->
            val definition = GameDefinitions.objects.getValue(obj.type)
            if (definition.trigger == EffectTrigger.ON_FINAL_PROXIMITY && result.currentPlayer.tileId in GameVision.distances(map, obj.tileId, definition.radius) && !result.currentPlayer.exhausted) {
                result = replace(result, result.currentPlayer.copy(health = (result.currentPlayer.health + definition.healing).coerceAtMost(GameDefinitions.MAX_HP)))
                    .copy(objects = result.objects.filterNot { it.id == obj.id })
            }
        }
        // Automatic attack against every unfinished opponent on the final tile; passing never attacks.
        if (!result.currentPlayer.exhausted) {
            val victims = result.players.filter { it.id != playerId && it.id !in result.ranking && it.tileId == result.currentPlayer.tileId }
            victims.forEach { victim -> result = damage(result, map, playerId, victim.id, GameDefinitions.characters.getValue(result.currentPlayer.character).attackDamage) }
        }
        if (result.currentPlayer.tileId == map.goal) {
            val player = result.currentPlayer.copy(completedLaps = result.currentPlayer.completedLaps + 1)
            result = replace(result, player)
            if (player.completedLaps >= result.targetLaps) {
                result = result.copy(ranking = result.ranking + player.id, winnerId = result.winnerId ?: player.id)
                val remaining = result.players.filter { it.id !in result.ranking }
                if (remaining.size == 1) result = result.copy(ranking = result.ranking + remaining.single().id)
                if (result.ranking.size == result.players.size) return result.copy(phase = TurnPhase.WON)
            } else result = replace(result, player.copy(tileId = map.start))
        }
        return endTurn(result)
    }
    private fun endTurn(s: GameSession): GameSession {
        var index = s.currentIndex
        var round = s.round
        do { index = (index + 1) % s.players.size; if (index == 0) round++ } while (s.players[index].id in s.ranking)
        return s.copy(currentIndex = index, round = round, phase = TurnPhase.HANDOFF, remainingSteps = 0, selectedPlacementTile = null)
    }
}

/** Presentation frames are ephemeral. The committed outcome is always the final frame. */
internal object DiceAnimation {
    val delays = listOf(45L, 55L, 65L, 80L, 100L, 125L, 155L, 185L, 220L, 270L)
    fun faces(result: Int): List<Int> {
        require(result in 1..6)
        val pattern = listOf(4, 1, 6, 2, 5, 3, 1, 5, 2)
        return pattern.map { ((it + result - 2) % 6) + 1 } + result
    }
}
