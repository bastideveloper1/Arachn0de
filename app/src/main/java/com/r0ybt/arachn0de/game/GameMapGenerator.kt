package com.r0ybt.arachn0de.game

import kotlin.random.Random

/** A 43-tile perimeter (formerly 27), with disjoint physical interior chords. */
object GameMapGenerator {
    fun generate(seed: Long): GameMap {
        val random = Random(seed)
        val rows = 17; val columns = 7
        val main = (0..6).map { 0 to it } + (1..16).map { it to 6 } +
            (5 downTo 0).map { 16 to it } + (15 downTo 2).map { it to 0 }
        val locations = linkedMapOf<Pair<Int, Int>, String>()
        val tiles = linkedMapOf<String, GameTile>()
        main.forEachIndexed { i, position -> locations[position] = "main-$i" }
        val mainIds = locations.values.toList()
        mainIds.forEachIndexed { i, id -> tiles[id] = GameTile(id, listOfNotNull(mainIds.getOrNull(i + 1))) }
        val branches = listOf(4, 8, 12, 14).shuffled(random).take(random.nextInt(3, 5)).sorted()
        branches.forEachIndexed { branch, row ->
            // Each winding chord owns a separate row band; reversing the bend
            // would collide with its neighbour and overwrite an existing edge.
            val bend = 1
            val route = when {
                row <= 8 -> listOf(row to 6, row to 5, row - bend to 5, row - bend to 4,
                    row to 4, row + bend to 4, row + bend to 5, row + 2 * bend to 5,
                    row + 2 * bend to 4, row + 2 * bend to 3, row + bend to 3, row to 3,
                    row - bend to 3, row - bend to 2, row to 2, row + bend to 2,
                    row + 2 * bend to 2, row + 2 * bend to 1, row + bend to 1, row to 1, row to 0)
                row == 12 -> listOf(row to 6, row to 5, row - bend to 5, row - bend to 4,
                    row to 4, row + bend to 4, row + bend to 3, row to 3, row - bend to 3,
                    row - bend to 2, row - bend to 1, row to 1, row to 0)
                else -> listOf(row to 6, row to 5, row + bend to 5, row + bend to 4,
                    row + bend to 3, row + bend to 2, row + bend to 1, row to 1, row to 0)
            }
            val ids = route.mapIndexed { i, position -> locations[position] ?: "shortcut-$branch-$i".also { locations[position] = it } }
            route.indices.drop(1).dropLast(1).forEach { i ->
                // Entry/exit remain dry grass; cave floor is continuous within each cave route.
                tiles[ids[i]] = GameTile(ids[i], listOf(ids[i + 1]), if (i in 2..(ids.lastIndex - 2) && branch % 2 == 0) Terrain.CAVE else Terrain.DRY_GRASS)
            }
            val entry = tiles.getValue(ids.first())
            tiles[entry.id] = entry.copy(next = entry.next + ids[1])
        }
        val critical = tiles.values.filter { it.next.size > 1 }.mapTo(hashSetOf()) { it.id }
        val indegree = tiles.values.flatMap { it.next }.groupingBy { it }.eachCount()
        mainIds.drop(1).dropLast(1).filter { it !in critical && indegree[it] == 1 }.shuffled(random).take(6).forEach { id ->
            tiles[id] = tiles.getValue(id).copy(terrain = Terrain.SWAMP)
        }
        val walls = linkedMapOf<Pair<Int, Int>, Int>()
        locations.filterValues { tiles.getValue(it).terrain == Terrain.CAVE }.keys.forEach { (r, c) ->
            listOf(Triple(r - 1, c, 0), Triple(r + 1, c, 180), Triple(r, c - 1, 270), Triple(r, c + 1, 90)).forEach { (wr, wc, angle) ->
                if (wr in 0 until rows && wc in 0 until columns && (wr to wc) !in locations) walls.putIfAbsent(wr to wc, angle)
            }
        }
        return GameMap("meadow-$seed", columns, rows, (0 until rows).flatMap { r -> (0 until columns).map { c ->
            val id = locations[r to c]
            MapSlot(r, c, when { id != null -> SlotType.TILE; (r to c) in walls -> SlotType.CAVE_WALL; r == 1 && c == 0 -> SlotType.EMPTY; else -> SlotType.FOREST }, id, walls[r to c] ?: 0)
        } }, tiles, mainIds.first(), mainIds.last(), seed, mainIds).also(::validateGeneratedMap)
    }
}

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
