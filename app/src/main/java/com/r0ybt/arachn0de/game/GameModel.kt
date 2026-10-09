package com.r0ybt.arachn0de.game

import kotlin.random.Random

enum class SlotType { EMPTY, TILE, FOREST, CAVE_WALL }
enum class Terrain { MEADOW, SWAMP, DRY_GRASS, CAVE }
data class MapSlot(val row: Int, val column: Int, val type: SlotType, val tileId: String? = null, val rotation: Int = 0)
data class GameTile(val id: String, val next: List<String> = emptyList(), val terrain: Terrain = Terrain.MEADOW)
data class GameMap(val id: String, val columns: Int, val rows: Int, val slots: List<MapSlot>,
    val tiles: Map<String, GameTile>, val start: String, val goal: String, val seed: Long? = null,
    val mainRoute: List<String> = emptyList()) {
    init {
        require(columns > 0 && rows > 0)
        require(slots.size == columns * rows)
        require(slots.map { it.row to it.column }.distinct().size == slots.size)
        require(slots.all { it.row in 0 until rows && it.column in 0 until columns })
        require(slots.all { (it.type == SlotType.TILE) == (it.tileId != null) })
        require(slots.mapNotNull { it.tileId }.toSet() == tiles.keys)
        require(slots.mapNotNull { it.tileId }.distinct().size == tiles.size)
        require(start in tiles && goal in tiles)
        require(tiles.all { (id, tile) -> id == tile.id && tile.next.distinct().size == tile.next.size && tile.next.all { it in tiles } })
        require(tiles.getValue(goal).next.isEmpty())
    }
    // Branches are represented in data. Choosing an alternative is a future UI feature.
    fun next(tileId: String): String? = tiles.getValue(tileId).next.firstOrNull()
}

enum class PlayerControl(val label: String) { HUMAN("Humano"), EASY("Fácil"), MEDIUM("Medio"), HARD("Difícil") }
data class SpiderState(val tileId: String, val defeated: Boolean = false)
enum class MotionKind { FORWARD, RETREAT }
data class GameMotion(val playerId: String, val from: String, val to: String, val kind: MotionKind = MotionKind.FORWARD)
data class SpiderCombat(val playerDie: Int, val spiderDie: Int)

object FirstGameMap {
    val value: GameMap = run {
        // Five columns keep tokens readable on portrait phones. Identity is independent of placement.
        // Clockwise perimeter; the gap separates start and finish. Keep all 27 saved tile IDs.
        val path = (0..4).map { 0 to it } + (1..10).map { it to 4 } +
            (3 downTo 0).map { 10 to it } + (9 downTo 2).map { it to 0 }
        val ids = path.indices.map { "tile-$it" }
        val byPosition = path.zip(ids).toMap()
        GameMap("meadow-v1", 5, 11, (0 until 11).flatMap { row -> (0 until 5).map { col ->
            val id = byPosition[row to col]
            MapSlot(row, col, if (id != null) SlotType.TILE else if (col == 0 && row == 1) SlotType.EMPTY else SlotType.FOREST, id)
        } }, ids.mapIndexed { index, id -> id to GameTile(id, ids.getOrNull(index + 1)?.let { listOf(it) }.orEmpty()) }.toMap(), ids.first(), ids.last())
    }
}

enum class RatCharacter(val label: String) {
    KNIGHT("Caballero"), MAGE("Mago"), HUNTRESS("Cazadora"), NECROMANCER("Nigromante"), MONK("Monje"), ROGUE("Pícaro")
}
enum class TokenQuadrant(val row: Int, val column: Int) {
    TOP_LEFT(0, 0), TOP_RIGHT(0, 1), BOTTOM_LEFT(1, 0), BOTTOM_RIGHT(1, 1)
}
data class GamePlayer(val id: String, val name: String, val character: RatCharacter, val tileId: String,
    val quadrant: TokenQuadrant, val health: Int = 100, val maxHealth: Int = 100,
    val coins: Int = 0, val abilityCharges: Int = 0,
    val inventory: List<String> = emptyList(), val statuses: List<String> = emptyList(),
    val exhausted: Boolean = false, val skippedTurns: Int = 0, val completedLaps: Int = 0,
    val visionModifier: Int = 0, val control: PlayerControl = PlayerControl.HUMAN,
    val trapped: Boolean = false, val engagedSpider: Boolean = false,
    val routeHistory: List<String> = emptyList(), val explored: Set<String> = emptySet(),
    val eyeUses: Int = 3, val eyeTurns: Int = 0)
enum class TurnPhase { HANDOFF, DAMAGE, REST, READY, PLACING, ROLLING, MOVING, WON,
    CHOOSING_ROUTE, ANIMATING, RESOLVING, COMBAT, RESULT }
data class GameSession(val mapId: String, val players: List<GamePlayer>, val currentIndex: Int = 0,
    val round: Int = 1, val phase: TurnPhase = TurnPhase.HANDOFF,
    val lastRoll: Int? = null, val remainingSteps: Int = 0, val lastPlayerId: String? = null,
    val winnerId: String? = null, val targetLaps: Int = 1,
    val objects: List<BoardObject> = emptyList(), val pendingDamage: List<PendingDamage> = emptyList(),
    val ranking: List<String> = emptyList(), val selectedPlacementTile: String? = null,
    val nextEventId: Long = 1, val board: GameMap? = null, val spider: SpiderState? = null,
    val motion: GameMotion? = null, val retreatPath: List<String> = emptyList(),
    val result: String? = null, val combat: SpiderCombat? = null,
    val resultDeadline: Long? = null, val lapReset: Boolean = false) {
    val currentPlayer get() = players[currentIndex]
}
