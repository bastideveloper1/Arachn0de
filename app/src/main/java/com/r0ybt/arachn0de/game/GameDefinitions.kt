package com.r0ybt.arachn0de.game

/** Gameplay values live here; the renderer never chooses character-specific rules. */
enum class ObjectType { BANNER, ICE_BARRIER, SPIKES, TOTEM, BEAR_TRAP, ZOMBIE }
enum class EffectTrigger { PASSIVE_RANGE, ON_PATH_BLOCK, ON_LAND, ON_FINAL_PROXIMITY }
data class ObjectDefinition(val label: String, val trigger: EffectTrigger, val damage: Int = 0,
    val consumable: Boolean = false, val skipsTurn: Boolean = false, val radius: Int = 0,
    val healing: Int = 0, val damageReductionPercent: Int = 0, val remoteVision: Int = 0)
data class CharacterDefinition(val attackDamage: Int, val forwardVision: Int, val ability: ObjectType,
    val abilityUses: Int, val maxHp: Int = 100, val backwardVision: Int = 2)
object GameDefinitions {
    const val MAX_HP = 100
    const val RECOVERY_HP = 50
    const val PLACEMENT_RANGE = 3
    val characters = mapOf(
        RatCharacter.KNIGHT to CharacterDefinition(25, 3, ObjectType.BANNER, 2),
        RatCharacter.MAGE to CharacterDefinition(20, 3, ObjectType.ICE_BARRIER, 3),
        RatCharacter.ROGUE to CharacterDefinition(20, 3, ObjectType.SPIKES, 4),
        RatCharacter.MONK to CharacterDefinition(15, 3, ObjectType.TOTEM, 2),
        RatCharacter.HUNTRESS to CharacterDefinition(20, 4, ObjectType.BEAR_TRAP, 3),
        RatCharacter.NECROMANCER to CharacterDefinition(15, 3, ObjectType.ZOMBIE, 3),
    )
    val objects = mapOf(
        ObjectType.BANNER to ObjectDefinition("Estandarte", EffectTrigger.PASSIVE_RANGE, radius = 2, damageReductionPercent = 50),
        ObjectType.ICE_BARRIER to ObjectDefinition("Barrera", EffectTrigger.ON_PATH_BLOCK),
        ObjectType.SPIKES to ObjectDefinition("Espinas", EffectTrigger.ON_LAND, damage = 15, consumable = true),
        ObjectType.TOTEM to ObjectDefinition("Tótem", EffectTrigger.ON_FINAL_PROXIMITY, consumable = true, radius = 2, healing = 20),
        ObjectType.BEAR_TRAP to ObjectDefinition("Trampa", EffectTrigger.ON_LAND, damage = 20, consumable = true, skipsTurn = true),
        ObjectType.ZOMBIE to ObjectDefinition("Zombi", EffectTrigger.ON_LAND, damage = 15, remoteVision = 2),
    )
}
data class BoardObject(val id: String, val ownerPlayerId: String, val tileId: String, val type: ObjectType,
    val active: Boolean = true)
data class PendingDamage(val id: String, val attackerId: String, val victimId: String,
    val damage: Int, val source: ObjectType? = null, val hpBefore: Int, val hpAfter: Int)

/** Graph distances for proximity, plus independent directed visibility traversals. */
object GameVision {
    fun distances(map: GameMap, origin: String, radius: Int): Map<String, Int> {
        require(origin in map.tiles && radius >= 0)
        val neighbors = map.tiles.keys.associateWith { mutableSetOf<String>() }
        map.tiles.values.forEach { tile -> tile.next.forEach { next ->
            neighbors.getValue(tile.id).add(next); neighbors.getValue(next).add(tile.id)
        } }
        val result = mutableMapOf(origin to 0)
        val queue = ArrayDeque<String>().apply { add(origin) }
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst(); val distance = result.getValue(id)
            if (distance < radius) neighbors.getValue(id).forEach { next ->
                if (next !in result) { result[next] = distance + 1; queue.add(next) }
            }
        }
        return result
    }
    /** Traverse each direction independently; physical proximity never reveals another route segment. */
    fun directional(map: GameMap, origin: String, forward: Int, backward: Int): Set<String> {
        require(origin in map.tiles && forward >= 0 && backward >= 0)
        val result = mutableSetOf(origin)
        fun walk(radius: Int, next: (String) -> List<String>) {
            var frontier = setOf(origin)
            val seen = mutableSetOf(origin)
            repeat(radius) {
                frontier = frontier.flatMap(next).filter { seen.add(it) }.toSet()
                result.addAll(frontier)
            }
        }
        walk(forward) { map.tiles.getValue(it).next }
        val previous = map.tiles.keys.associateWith { id -> map.tiles.values.filter { id in it.next }.map { it.id } }
        walk(backward) { previous.getValue(it) }
        return result
    }
    fun visibleTiles(session: GameSession, map: GameMap, playerId: String): Set<String> {
        val player = session.players.first { it.id == playerId }
        val definition = GameDefinitions.characters.getValue(player.character)
        val result = directional(map, player.tileId,
            (definition.forwardVision + player.visionModifier).coerceAtLeast(0),
            (definition.backwardVision + player.visionModifier).coerceAtLeast(0)).toMutableSet()
        session.objects.filter { it.active && it.ownerPlayerId == playerId }.forEach { obj ->
            val range = GameDefinitions.objects.getValue(obj.type).remoteVision
            if (range > 0 && definition.ability == obj.type) result.addAll(directional(map, obj.tileId, range, range))
        }
        return result
    }
    fun visiblePlayers(session: GameSession, map: GameMap, playerId: String): List<GamePlayer> {
        val visible = visibleTiles(session, map, playerId)
        return session.players.filter { it.id !in session.ranking && (it.id == playerId || it.tileId in visible) }
    }
    fun visibleObjects(session: GameSession, map: GameMap, playerId: String): List<BoardObject> {
        val visible = visibleTiles(session, map, playerId)
        return session.objects.filter { it.active && (it.ownerPlayerId == playerId || it.tileId in visible) }
    }
}

internal fun ObjectType.description(): String = when (this) {
    ObjectType.BANNER -> "Reduce un 50 % el daño recibido mientras estés a 2 casillas o menos de tu estandarte."
    ObjectType.ICE_BARRIER -> "Bloquea el avance de un enemigo. Se detendrá antes de la barrera y esta se derretirá."
    ObjectType.SPIKES -> "Si un enemigo termina su movimiento sobre ellas, recibe 15 de daño. Desaparecen al activarse."
    ObjectType.TOTEM -> "Si terminas tu movimiento a 2 casillas o menos, recuperas 20 de vida. Desaparece al activarse."
    ObjectType.BEAR_TRAP -> "Si un enemigo termina su movimiento sobre ella, recibe 20 de daño y pierde su próximo turno."
    ObjectType.ZOMBIE -> "Si un enemigo termina su movimiento en su casilla, recibe 15 de daño. Además, el zombi te permite ver una pequeña zona a su alrededor."
}
