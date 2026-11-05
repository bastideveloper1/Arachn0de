package com.r0ybt.arachn0de.backup.legacy.game

internal fun validateGeneratedMap(map: GameMap) {
    require(map.columns in 1..32 && map.rows in 1..64 && map.tiles.size <= 1024)
    val positions = map.slots.filter { it.tileId != null }.associateBy { it.tileId!! }
    map.tiles.values.forEach { tile -> tile.next.forEach { next ->
        val a = positions.getValue(tile.id); val b = positions.getValue(next)
        require(kotlin.math.abs(a.row - b.row) + kotlin.math.abs(a.column - b.column) == 1) { "Camino no contiguo." }
    } }
    val visited = hashSetOf<String>(); val queue = ArrayDeque<String>(); queue.add(map.start)
    while (queue.isNotEmpty()) { val id = queue.removeFirst(); if (visited.add(id)) queue.addAll(map.tiles.getValue(id).next) }
    require(visited == map.tiles.keys) { "Ruta inaccesible." }
    val incoming = map.tiles.keys.associateWith { 0 }.toMutableMap()
    map.tiles.values.forEach { it.next.forEach { next -> incoming[next] = incoming.getValue(next) + 1 } }
    queue.addAll(incoming.filterValues { it == 0 }.keys); var processed = 0
    while (queue.isNotEmpty()) { val id = queue.removeFirst(); processed++; map.tiles.getValue(id).next.forEach { next ->
        incoming[next] = incoming.getValue(next) - 1; if (incoming[next] == 0) queue.add(next)
    } }
    require(processed == map.tiles.size && map.tiles.values.all { it.id == map.goal || it.next.isNotEmpty() }) { "Ciclo o camino cortado." }
    require(map.mainRoute.size in 41..47 && map.mainRoute.first() == map.start && map.mainRoute.last() == map.goal)
    require(map.mainRoute.zipWithNext().all { (a, b) -> b in map.tiles.getValue(a).next })
    require(map.tiles.values.count { it.terrain == Terrain.SWAMP } == 6)
    require(map.tiles.values.any { it.terrain == Terrain.CAVE })
}
