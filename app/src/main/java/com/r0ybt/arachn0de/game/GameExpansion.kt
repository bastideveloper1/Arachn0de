package com.r0ybt.arachn0de.game

/** Durable presentation boundaries: no landing effect occurs until the renderer acknowledges arrival. */
object GameExpansion {
    const val STEP_MILLIS = 450
    const val RESULT_MILLIS = 10_000L
    const val EYE_RADIUS = 4
    private fun replace(s: GameSession, p: GamePlayer) = s.copy(players = s.players.map { if (it.id == p.id) p else it })
    fun refreshVision(s: GameSession, map: GameMap) = s.copy(players = s.players.map { p ->
        p.copy(explored = p.explored + GameVision.visibleTiles(s, map, p.id))
    })
    fun explore(s: GameSession, map: GameMap): GameSession {
        require(s.phase == TurnPhase.READY && s.currentPlayer.eyeUses > 0 && s.currentPlayer.eyeTurns == 0)
        return refreshVision(replace(s, s.currentPlayer.copy(eyeUses = s.currentPlayer.eyeUses - 1, eyeTurns = 2)), map)
    }
    fun finishRoll(s: GameSession): GameSession {
        require(s.phase == TurnPhase.ROLLING)
        if (s.currentPlayer.trapped) {
            val escaped = requireNotNull(s.lastRoll) >= 4
            return replace(s, s.currentPlayer.copy(trapped = !escaped)).copy(phase = TurnPhase.RESULT, remainingSteps = 0,
                result = if (escaped) "Escapó del pantano. Este turno se consume en liberarse." else "Sigue atrapado en el pantano.")
        }
        return GameRules.finishRoll(s)
    }
    fun planStep(s: GameSession, map: GameMap, choice: String? = null): GameSession {
        require(s.phase in setOf(TurnPhase.MOVING, TurnPhase.CHOOSING_ROUTE) && s.remainingSteps > 0)
        val options = map.tiles.getValue(s.currentPlayer.tileId).next
        if (options.isEmpty()) return s.copy(phase = TurnPhase.RESOLVING, remainingSteps = 0)
        if (options.size > 1 && choice == null) return s.copy(phase = TurnPhase.CHOOSING_ROUTE)
        val destination = choice ?: options.single()
        require(destination in options)
        val barrier = s.objects.firstOrNull { it.active && it.tileId == destination && it.ownerPlayerId != s.currentPlayer.id && it.type == ObjectType.ICE_BARRIER }
        if (barrier != null) return s.copy(objects = s.objects.filterNot { it.id == barrier.id }, phase = TurnPhase.RESOLVING, remainingSteps = 0)
        val player = s.currentPlayer
        val history = if (player.routeHistory.lastOrNull() == player.tileId) player.routeHistory else listOf(player.tileId)
        return refreshVision(replace(s, player.copy(tileId = destination, routeHistory = (history + destination).takeLast(4096)))
            .copy(phase = TurnPhase.ANIMATING, motion = GameMotion(player.id, player.tileId, destination), remainingSteps = s.remainingSteps - 1), map)
    }
    fun arrive(s: GameSession, map: GameMap): GameSession {
        require(s.phase == TurnPhase.ANIMATING && s.motion != null)
        val motion = s.motion
        if (motion.kind == MotionKind.RETREAT) {
            if (s.retreatPath.isNotEmpty()) {
                val destination = s.retreatPath.first()
                return refreshVision(replace(s, s.currentPlayer.copy(tileId = destination)).copy(
                    motion = GameMotion(s.currentPlayer.id, motion.to, destination, MotionKind.RETREAT), retreatPath = s.retreatPath.drop(1)), map)
            }
            return s.copy(phase = TurnPhase.RESULT, motion = null)
        }
        val finished = s.remainingSteps == 0 || s.currentPlayer.tileId == map.goal
        return s.copy(phase = if (finished) TurnPhase.RESOLVING else TurnPhase.MOVING, motion = null,
            remainingSteps = if (finished) 0 else s.remainingSteps)
    }
    fun resolveLanding(s: GameSession, map: GameMap): GameSession {
        require(s.phase == TurnPhase.RESOLVING && s.motion == null)
        var result = GameRules.finishMovement(s, map, deferTurn = true)
        if (map.tiles.getValue(result.currentPlayer.tileId).terrain == Terrain.SWAMP) {
            result = replace(result, result.currentPlayer.copy(trapped = true)).copy(result = "Quedó atrapado en el pantano.")
        }
        if (result.spider?.let { !it.defeated && it.tileId == result.currentPlayer.tileId } == true) {
            result = replace(result, result.currentPlayer.copy(engagedSpider = true)).copy(phase = TurnPhase.COMBAT, result = null)
        }
        return refreshVision(result, map)
    }
    fun combat(s: GameSession, map: GameMap, playerDie: Int, spiderDie: Int): GameSession {
        require(s.phase == TurnPhase.COMBAT && playerDie in 1..6 && spiderDie in 1..6)
        require(s.spider?.let { !it.defeated && it.tileId == s.currentPlayer.tileId } == true)
        var result = s.copy(combat = SpiderCombat(playerDie, spiderDie), remainingSteps = 0, resultDeadline = null)
        result = when {
            playerDie > spiderDie -> replace(result, result.currentPlayer.copy(engagedSpider = false)).copy(
                players = result.players.map { it.copy(engagedSpider = false) }, spider = result.spider!!.copy(defeated = true), phase = TurnPhase.RESULT, result = "La araña fue derrotada.")
            playerDie == spiderDie -> result.copy(phase = TurnPhase.RESULT, result = "Empate. El combate continúa en su próximo turno.")
            else -> {
                val player = result.currentPlayer
                val history = player.routeHistory.ifEmpty { listOf(player.tileId) }
                val path = history.dropLast(1).takeLast(2).asReversed()
                val shortened = history.dropLast(path.size)
                if (path.isEmpty()) replace(result, player.copy(engagedSpider = false)).copy(phase = TurnPhase.RESULT, result = "Perdió el combate.")
                else replace(result, player.copy(tileId = path.first(), engagedSpider = false, routeHistory = shortened)).copy(
                    phase = TurnPhase.ANIMATING, motion = GameMotion(player.id, player.tileId, path.first(), MotionKind.RETREAT),
                    retreatPath = path.drop(1), result = "Perdió el combate y retrocede dos casillas por su ruta.")
            }
        }
        // Retreat deliberately bypasses landing resolution, avoiding chains of encounters/traps.
        return refreshVision(result, map)
    }
    fun rest(s: GameSession): GameSession {
        require(s.phase == TurnPhase.REST)
        val p = s.currentPlayer
        return replace(s, p.copy(health = if (p.exhausted) GameDefinitions.RECOVERY_HP else p.health,
            exhausted = false, skippedTurns = (p.skippedTurns - 1).coerceAtLeast(0))).copy(phase = TurnPhase.RESULT, result = "Turno de descanso terminado.")
    }
    fun presentResult(s: GameSession, now: Long): GameSession {
        require(s.phase == TurnPhase.RESULT && s.motion == null && s.resultDeadline == null && now in 0..Long.MAX_VALUE - RESULT_MILLIS)
        return s.copy(resultDeadline = now + RESULT_MILLIS)
    }
    fun continueTurn(s: GameSession, map: GameMap): GameSession {
        require(s.phase == TurnPhase.RESULT && s.resultDeadline != null && s.motion == null)
        var next = replace(s, s.currentPlayer.copy(eyeTurns = (s.currentPlayer.eyeTurns - 1).coerceAtLeast(0),
            tileId = if (s.lapReset) map.start else s.currentPlayer.tileId,
            routeHistory = if (s.lapReset) listOf(map.start) else s.currentPlayer.routeHistory))
            .copy(result = null, combat = null, resultDeadline = null, lapReset = false)
        next = if (next.ranking.size == next.players.size) next.copy(phase = TurnPhase.WON) else GameRules.endTurn(next)
        return refreshVision(next, map)
    }
}
