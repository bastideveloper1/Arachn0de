package com.r0ybt.arachn0de.game

enum class FogLevel { VISIBLE, EXPLORED, UNEXPLORED }
object GameFog {
    fun cells(s: GameSession, map: GameMap, playerId: String): Map<Pair<Int, Int>, FogLevel> {
        val visible = GameVision.visibleTiles(s, map, playerId)
        val explored = s.players.first { it.id == playerId }.explored
        fun nearby(tiles: Set<String>): Set<Pair<Int, Int>> = map.slots.filter { it.tileId in tiles }.flatMap { slot ->
            listOf(slot.row to slot.column, slot.row - 1 to slot.column, slot.row + 1 to slot.column,
                slot.row to slot.column - 1, slot.row to slot.column + 1)
        }.toSet()
        val lit = nearby(visible); val known = nearby(explored)
        return map.slots.associate { slot -> val key = slot.row to slot.column
            key to when { key in lit -> FogLevel.VISIBLE; key in known -> FogLevel.EXPLORED; else -> FogLevel.UNEXPLORED }
        }
    }
}
