package com.r0ybt.arachn0de.game

import org.json.JSONArray
import org.json.JSONObject

/** Versioned snapshot isolated from Room. V1 saves are upgraded without rerolling or losing positions. */
object GameSessionCodec {
    private fun strings(a: JSONArray) = (0 until a.length()).map { a.get(it) as? String ?: error("Cadena inválida") }
    private fun objects(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }
    private fun JSONObject.string(key: String) = get(key) as? String ?: error("Cadena inválida: $key")
    private fun JSONObject.longValue(key: String) = (get(key) as? Number)?.toLong() ?: error("Entero inválido: $key")
    private fun JSONObject.integer(key: String): Int = longValue(key).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
    private fun JSONObject.boolean(key: String) = get(key) as? Boolean ?: error("Booleano inválido: $key")
    private fun JSONObject.optionalInt(key: String, fallback: Int = 0) = if (has(key)) integer(key) else fallback
    private fun JSONObject.optionalLong(key: String, fallback: Long = 0) = if (has(key)) longValue(key) else fallback
    private fun JSONObject.optionalBoolean(key: String, fallback: Boolean = false) = if (has(key)) boolean(key) else fallback
    private fun JSONObject.nullableString(key: String) = if (!has(key) || isNull(key)) null else getString(key)
    fun encode(s: GameSession): String = JSONObject().apply {
        put("version", 3); put("map", s.mapId); put("current", s.currentIndex); put("round", s.round)
        put("phase", s.phase.name); put("roll", s.lastRoll); put("steps", s.remainingSteps)
        put("lastPlayer", s.lastPlayerId); put("winner", s.winnerId); put("laps", s.targetLaps)
        put("ranking", JSONArray(s.ranking)); put("placement", s.selectedPlacementTile); put("nextEvent", s.nextEventId)
        put("board", s.board?.let(::encodeMap)); put("spider", s.spider?.let { JSONObject().put("tile", it.tileId).put("defeated", it.defeated) })
        put("motion", s.motion?.let { JSONObject().put("player", it.playerId).put("from", it.from).put("to", it.to).put("kind", it.kind.name) })
        put("retreat", JSONArray(s.retreatPath)); put("result", s.result); put("deadline", s.resultDeadline); put("lapReset", s.lapReset)
        put("combat", s.combat?.let { JSONObject().put("player", it.playerDie).put("spider", it.spiderDie) })
        put("players", JSONArray(s.players.map { p -> JSONObject().apply {
            put("id", p.id); put("name", p.name); put("character", p.character.name); put("tile", p.tileId)
            put("quadrant", p.quadrant.name); put("health", p.health); put("maxHealth", p.maxHealth)
            put("coins", p.coins); put("charges", p.abilityCharges); put("inventory", JSONArray(p.inventory)); put("statuses", JSONArray(p.statuses))
            put("control", p.control.name); put("trapped", p.trapped); put("engaged", p.engagedSpider)
            put("history", JSONArray(p.routeHistory)); put("explored", JSONArray(p.explored.sorted())); put("eyeUses", p.eyeUses); put("eyeTurns", p.eyeTurns)
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
    fun decode(raw: String, fallbackMap: GameMap = FirstGameMap.value): GameSession? = runCatching {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        validateJson(raw)
        val o = JSONObject(raw); val version = o.integer("version")
        require(version in 1..3)
        val board = if (version >= 3 && !o.isNull("board") && o.has("board")) decodeMap(o.getJSONObject("board")) else null
        val map = board ?: fallbackMap
        require(o.string("map") == map.id)
        val players = objects(o.getJSONArray("players")).map { p ->
            val character = RatCharacter.valueOf(p.string("character"))
            if (version >= 3) require(p.integer("maxHealth") == 100)
            val health = if (version == 1) (p.integer("health") * 10).coerceIn(0, 100) else p.integer("health")
            GamePlayer(p.string("id"), p.string("name"), character, p.string("tile"),
                TokenQuadrant.valueOf(p.string("quadrant")), health, 100, p.integer("coins"),
                if (version == 1) GameDefinitions.characters.getValue(character).abilityUses else p.integer("charges"),
                strings(p.getJSONArray("inventory")), strings(p.getJSONArray("statuses")),
                if (version == 1) health == 0 else p.boolean("exhausted"), p.optionalInt("skipped"), p.optionalInt("completedLaps"), p.optionalInt("vision"),
                if (version >= 3) PlayerControl.valueOf(p.string("control")) else PlayerControl.HUMAN,
                p.optionalBoolean("trapped"), p.optionalBoolean("engaged"),
                if (version >= 3) strings(p.getJSONArray("history")) else listOf(p.string("tile")),
                if (version >= 3) strings(p.getJSONArray("explored")).toSet() else emptySet(),
                p.optionalInt("eyeUses", 3), p.optionalInt("eyeTurns"))
        }
        require(players.size in 2..4 && players.map { it.character }.distinct().size == players.size)
        require(players.map { it.id }.distinct().size == players.size)
        require(players.withIndex().all { (i, p) -> p.tileId in map.tiles && p.quadrant == TokenQuadrant.entries[i] && p.name.isNotBlank() && p.name.length <= 16 &&
            p.health in 0..100 && p.abilityCharges in 0..GameDefinitions.characters.getValue(p.character).abilityUses && p.skippedTurns in 0..100 && p.visionModifier in -32..32 && p.coins >= 0 && p.id.isNotBlank() && p.id.length <= 128 && p.inventory.size <= 32 && p.statuses.size <= 32 && p.completedLaps in 0..4 && (p.health != 0 || p.exhausted) })
        val boardObjects = objects(o.optJSONArray("objects") ?: JSONArray()).map { obj ->
            BoardObject(obj.string("id"), obj.string("owner"), obj.string("tile"), ObjectType.valueOf(obj.string("type")), obj.boolean("active"))
        }
        val damage = objects(o.optJSONArray("damage") ?: JSONArray()).map { d -> PendingDamage(d.string("id"), d.string("attacker"), d.string("victim"),
            d.integer("amount"), d.nullableString("source")?.let(ObjectType::valueOf), d.integer("before"), d.integer("after")) }
        var s = GameSession(map.id, players, o.integer("current"), o.integer("round"), TurnPhase.valueOf(o.string("phase")),
            if (o.has("roll")) o.integer("roll") else null, o.integer("steps"), o.nullableString("lastPlayer"), o.nullableString("winner"),
            o.optionalInt("laps", 1), boardObjects, damage, strings(o.optJSONArray("ranking") ?: JSONArray()), o.nullableString("placement"), o.optionalLong("nextEvent", 1))
        if (version >= 3) {
            val spider = o.optJSONObject("spider")?.let { SpiderState(it.string("tile"), it.boolean("defeated")) }
            val motion = o.optJSONObject("motion")?.let { GameMotion(it.string("player"), it.string("from"), it.string("to"), MotionKind.valueOf(it.string("kind"))) }
            val combat = o.optJSONObject("combat")?.let { SpiderCombat(it.integer("player"), it.integer("spider")) }
            s = s.copy(board = board, spider = spider, motion = motion, combat = combat,
                retreatPath = strings(o.getJSONArray("retreat")), result = o.nullableString("result"),
                resultDeadline = if (o.isNull("deadline") || !o.has("deadline")) null else o.longValue("deadline"), lapReset = o.boolean("lapReset"))
        }
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
        require((s.phase in setOf(TurnPhase.ROLLING, TurnPhase.MOVING, TurnPhase.CHOOSING_ROUTE) || s.phase == TurnPhase.ANIMATING && s.motion?.kind == MotionKind.FORWARD && s.remainingSteps > 0) == (s.remainingSteps > 0))
        require(s.remainingSteps == 0 || s.lastRoll != null && s.remainingSteps <= s.lastRoll)
        require(s.lastPlayerId == null || s.lastPlayerId in ids)
        require(s.ranking.distinct().size == s.ranking.size && s.ranking.all { it in ids })
        require(s.winnerId == s.ranking.firstOrNull())
        require((s.phase == TurnPhase.WON || s.phase == TurnPhase.RESULT && s.ranking.size == players.size) == (s.ranking.size == players.size))
        require(s.phase in setOf(TurnPhase.WON, TurnPhase.RESULT) || s.currentPlayer.id !in s.ranking)
        require(s.selectedPlacementTile == null || s.phase == TurnPhase.PLACING && s.selectedPlacementTile in map.tiles)
        require(boardObjects.all { it.ownerPlayerId in ids && it.tileId in map.tiles && GameDefinitions.characters.getValue(players.first { p -> p.id == it.ownerPlayerId }.character).ability == it.type })
        require(boardObjects.map { it.id }.distinct().size == boardObjects.size && boardObjects.filter { it.active }.map { it.tileId }.distinct().size == boardObjects.count { it.active })
        require(damage.map { it.id }.distinct().size == damage.size)
        require(damage.all { it.attackerId in ids && it.victimId in ids && it.damage in 0..100 && it.hpBefore in 0..100 && it.hpAfter in 0..it.hpBefore && it.hpBefore - it.hpAfter == it.damage })
        require(s.phase != TurnPhase.DAMAGE || GameRules.currentDamage(s) != null)
        require(players.all { p -> p.eyeUses in 0..3 && p.eyeTurns in 0..2 && p.routeHistory.size <= 4096 && p.routeHistory.all { it in map.tiles } && p.explored.all { it in map.tiles } })
        players.forEach { p -> require(p.routeHistory.zipWithNext().all { (a, b) -> b in map.tiles.getValue(a).next }) }
        require(s.spider == null || s.spider.tileId in map.tiles && map.tiles.getValue(s.spider.tileId).terrain == Terrain.CAVE)
        require(board == null || s.spider != null)
        require((s.phase == TurnPhase.ANIMATING) == (s.motion != null))
        s.motion?.let { require(it.playerId == s.currentPlayer.id && it.to == s.currentPlayer.tileId && it.from in map.tiles && it.to in map.tiles)
            require(if (it.kind == MotionKind.FORWARD) it.to in map.tiles.getValue(it.from).next else it.from in map.tiles.getValue(it.to).next) }
        require(s.retreatPath.size <= 1 && s.retreatPath.all { it in map.tiles })
        require(s.retreatPath.isEmpty() || s.motion?.kind == MotionKind.RETREAT)
        require(s.retreatPath.isEmpty() || s.currentPlayer.tileId in map.tiles.getValue(s.retreatPath.single()).next)
        require(s.resultDeadline == null || s.phase == TurnPhase.RESULT && s.resultDeadline >= 0)
        require(s.result == null || s.result.length <= 512)
        require(s.combat == null || s.combat.playerDie in 1..6 && s.combat.spiderDie in 1..6)
        require(s.phase != TurnPhase.COMBAT || s.spider?.let { !it.defeated && it.tileId == s.currentPlayer.tileId } == true)
        require(s.players.none { it.engagedSpider } || s.spider?.defeated == false)
        require(players.all { !it.trapped || map.tiles.getValue(it.tileId).terrain == Terrain.SWAMP })
        require(players.all { !it.engagedSpider || it.tileId == s.spider?.tileId })
        if (version < 3) GameExpansion.refreshVision(s, map) else s
    }.getOrNull()

    const val MAX_BYTES = 256 * 1024
    private fun encodeMap(m: GameMap) = JSONObject().apply {
        put("id", m.id); put("columns", m.columns); put("rows", m.rows); put("start", m.start); put("goal", m.goal); put("seed", m.seed)
        put("main", JSONArray(m.mainRoute))
        put("slots", JSONArray(m.slots.map { JSONObject().put("row", it.row).put("column", it.column).put("type", it.type.name).put("tile", it.tileId).put("rotation", it.rotation) }))
        put("tiles", JSONArray(m.tiles.values.map { JSONObject().put("id", it.id).put("next", JSONArray(it.next)).put("terrain", it.terrain.name) }))
    }
    private fun decodeMap(o: JSONObject): GameMap {
        val columns = o.integer("columns"); val rows = o.integer("rows")
        require(columns in 1..32 && rows in 1..64 && columns * rows <= 2048)
        val tiles = objects(o.getJSONArray("tiles")).map { GameTile(it.string("id"), strings(it.getJSONArray("next")), Terrain.valueOf(it.string("terrain"))) }
        require(tiles.size <= 1024 && tiles.map { it.id }.distinct().size == tiles.size && tiles.all { it.id.length in 1..128 && it.next.size <= 4 })
        return GameMap(o.string("id"), columns, rows, objects(o.getJSONArray("slots")).map {
            MapSlot(it.integer("row"), it.integer("column"), SlotType.valueOf(it.string("type")), it.nullableString("tile"), it.integer("rotation"))
        }, tiles.associateBy { it.id }, o.string("start"), o.string("goal"), o.longValue("seed"), strings(o.getJSONArray("main"))).also(::validateGeneratedMap)
    }
    /** Bounded strict parser before JSONObject: reject duplicate keys, fractions, and excessive nesting. */
    private fun validateJson(raw: String) {
        android.util.JsonReader(java.io.StringReader(raw)).use { reader ->
            fun value(depth: Int) {
                require(depth <= 10)
                when (reader.peek()) {
                    android.util.JsonToken.BEGIN_OBJECT -> { reader.beginObject(); val keys = hashSetOf<String>()
                        while (reader.hasNext()) { require(keys.size < 40 && keys.add(reader.nextName())); value(depth + 1) }; reader.endObject() }
                    android.util.JsonToken.BEGIN_ARRAY -> { reader.beginArray(); var size = 0
                        while (reader.hasNext()) { require(++size <= 4096); value(depth + 1) }; reader.endArray() }
                    android.util.JsonToken.NUMBER -> { require(reader.nextString().toLongOrNull() != null) }
                    android.util.JsonToken.STRING -> { require(reader.nextString().length <= 4096) }
                    android.util.JsonToken.BOOLEAN -> reader.nextBoolean()
                    android.util.JsonToken.NULL -> reader.nextNull()
                    else -> error("JSON de partida inválido")
                }
            }
            value(0); require(reader.peek() == android.util.JsonToken.END_DOCUMENT)
        }
    }
}
