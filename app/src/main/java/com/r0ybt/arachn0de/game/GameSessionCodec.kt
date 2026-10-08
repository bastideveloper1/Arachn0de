package com.r0ybt.arachn0de.game

import org.json.JSONArray
import org.json.JSONObject

/** Versioned snapshot isolated from Room. V1 saves are upgraded without rerolling or losing positions. */
internal object GameSessionCodec {
    private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
    private fun objects(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }
    private fun JSONObject.nullableString(key: String) = if (!has(key) || isNull(key)) null else getString(key)
    fun encode(s: GameSession): String = JSONObject().apply {
        put("version", 2); put("map", s.mapId); put("current", s.currentIndex); put("round", s.round)
        put("phase", s.phase.name); put("roll", s.lastRoll); put("steps", s.remainingSteps)
        put("lastPlayer", s.lastPlayerId); put("winner", s.winnerId); put("laps", s.targetLaps)
        put("ranking", JSONArray(s.ranking)); put("placement", s.selectedPlacementTile); put("nextEvent", s.nextEventId)
        put("players", JSONArray(s.players.map { p -> JSONObject().apply {
            put("id", p.id); put("name", p.name); put("character", p.character.name); put("tile", p.tileId)
            put("quadrant", p.quadrant.name); put("health", p.health); put("maxHealth", p.maxHealth)
            put("coins", p.coins); put("charges", p.abilityCharges); put("inventory", JSONArray(p.inventory)); put("statuses", JSONArray(p.statuses))
            put("exhausted", p.exhausted); put("skipped", p.skippedTurns); put("completedLaps", p.completedLaps); put("vision", p.visionModifier)
        } }))
        put("objects", JSONArray(s.objects.map { obj -> JSONObject().apply {
            put("id", obj.id); put("owner", obj.ownerPlayerId); put("tile", obj.tileId); put("type", obj.type.name); put("active", obj.active)
        } }))
        put("damage", JSONArray(s.pendingDamage.map { d -> JSONObject().apply {
            put("id", d.id); put("attacker", d.attackerId); put("victim", d.victimId); put("amount", d.damage)
            put("source", d.source?.name); put("before", d.hpBefore); put("after", d.hpAfter)
        } }))
    }.toString()
    fun decode(raw: String, map: GameMap): GameSession? = runCatching {
        val o = JSONObject(raw); val version = o.getInt("version")
        require(version in 1..2 && o.getString("map") == map.id)
        val players = objects(o.getJSONArray("players")).map { p ->
            val character = RatCharacter.valueOf(p.getString("character"))
            val health = if (version == 1) (p.getInt("health") * 10).coerceIn(0, 100) else p.getInt("health")
            GamePlayer(p.getString("id"), p.getString("name"), character, p.getString("tile"),
                TokenQuadrant.valueOf(p.getString("quadrant")), health, 100, p.getInt("coins"),
                if (version == 1) GameDefinitions.characters.getValue(character).abilityUses else p.getInt("charges"),
                strings(p.getJSONArray("inventory")), strings(p.getJSONArray("statuses")),
                if (version == 1) health == 0 else p.getBoolean("exhausted"), p.optInt("skipped"), p.optInt("completedLaps"), p.optInt("vision"))
        }
        require(players.size in 2..4 && players.map { it.character }.distinct().size == players.size)
        require(players.map { it.id }.distinct().size == players.size)
        require(players.withIndex().all { (i, p) -> p.tileId in map.tiles && p.quadrant == TokenQuadrant.entries[i] && p.name.isNotBlank() && p.name.length <= 16 &&
            p.health in 0..100 && p.abilityCharges in 0..GameDefinitions.characters.getValue(p.character).abilityUses && p.skippedTurns >= 0 && p.completedLaps in 0..4 && (p.health != 0 || p.exhausted) })
        val boardObjects = objects(o.optJSONArray("objects") ?: JSONArray()).map { obj ->
            BoardObject(obj.getString("id"), obj.getString("owner"), obj.getString("tile"), ObjectType.valueOf(obj.getString("type")), obj.getBoolean("active"))
        }
        val damage = objects(o.optJSONArray("damage") ?: JSONArray()).map { d -> PendingDamage(d.getString("id"), d.getString("attacker"), d.getString("victim"),
            d.getInt("amount"), d.nullableString("source")?.let(ObjectType::valueOf), d.getInt("before"), d.getInt("after")) }
        var s = GameSession(map.id, players, o.getInt("current"), o.getInt("round"), TurnPhase.valueOf(o.getString("phase")),
            if (o.has("roll")) o.getInt("roll") else null, o.getInt("steps"), o.nullableString("lastPlayer"), o.nullableString("winner"),
            o.optInt("laps", 1), boardObjects, damage, strings(o.optJSONArray("ranking") ?: JSONArray()), o.nullableString("placement"), o.optLong("nextEvent", 1))
        if (version == 1 && s.phase == TurnPhase.WON) {
            val winner = requireNotNull(s.winnerId)
            require(s.currentPlayer.id == winner && s.currentPlayer.tileId == map.goal)
            val ranked = listOf(winner)
            s = s.copy(players = s.players.map { if (it.id == winner) it.copy(completedLaps = 1) else it }, ranking = ranked)
            if (players.size == 2) s = s.copy(ranking = ranked + players.first { it.id != winner }.id)
            else s = s.copy(phase = TurnPhase.HANDOFF, currentIndex = players.indices.first { players[it].id != winner })
        }
        val ids = players.map { it.id }.toSet()
        require(s.currentIndex in players.indices && s.round > 0 && s.targetLaps in 1..4 && s.nextEventId > 0)
        require(s.lastRoll == null || s.lastRoll in 1..6)
        require(s.remainingSteps in 0..6)
        require((s.phase in setOf(TurnPhase.ROLLING, TurnPhase.MOVING)) == (s.remainingSteps > 0))
        require(s.remainingSteps == 0 || s.lastRoll != null && s.remainingSteps <= s.lastRoll)
        require(s.lastPlayerId == null || s.lastPlayerId in ids)
        require(s.ranking.distinct().size == s.ranking.size && s.ranking.all { it in ids })
        require(s.winnerId == s.ranking.firstOrNull())
        require((s.phase == TurnPhase.WON) == (s.ranking.size == players.size))
        require(s.phase == TurnPhase.WON || s.currentPlayer.id !in s.ranking)
        require(s.selectedPlacementTile == null || s.phase == TurnPhase.PLACING && s.selectedPlacementTile in map.tiles)
        require(boardObjects.all { it.ownerPlayerId in ids && it.tileId in map.tiles && GameDefinitions.characters.getValue(players.first { p -> p.id == it.ownerPlayerId }.character).ability == it.type })
        require(boardObjects.map { it.id }.distinct().size == boardObjects.size && boardObjects.filter { it.active }.map { it.tileId }.distinct().size == boardObjects.count { it.active })
        require(damage.map { it.id }.distinct().size == damage.size)
        require(damage.all { it.attackerId in ids && it.victimId in ids && it.damage in 0..100 && it.hpBefore in 0..100 && it.hpAfter in 0..it.hpBefore && it.hpBefore - it.hpAfter == it.damage })
        require(s.phase != TurnPhase.DAMAGE || GameRules.currentDamage(s) != null)
        s
    }.getOrNull()
}
