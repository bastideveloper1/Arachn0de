package com.r0ybt.arachn0de.game

/** Universal rules; a snapshot retains its balance rather than inheriting future changes. */
data class RaceBalance(val rock: Int = 5, val wasps: Int = 8, val placement: Int = 5, val eye: Int = 8,
    val blastRadius: Int = 2, val eyeRadius: Int = 3, val trapRounds: Int = 3)
enum class Racer(val label: String, val file: String) {
    RABBIT("Conejo", "conejo.png"), SHARK("Tiburón", "tiburon.png"), CROCODILE("Cocodrilo", "cocodrilo.png"),
    PUMA("Puma", "puma.png"), TURTLE("Tortuga", "tortuga.png"), CONDOR("Cóndor", "condor.png"),
    RAT("Rata", "rata.png"), DOG("Perro", "perro.png"), CAT("Gato", "gato.png"), DINO("Dinosaurio", "dino.png")
}
enum class RaceItem(val label: String, val file: String, val help: String) {
    ROCK("Roca", "roca.png", "Primer rival por el sendero, 5 casillas. Puede golpear invisibles."),
    WASPS("Avispas", "avispas.png", "Rival visible hasta 8 casillas hacia delante."),
    REROLL("Segunda oportunidad", "dados.png", "Decide repetir una vez después de ver el dado. El segundo resultado es obligatorio."),
    CALTROPS("Trampa doble", "caltrops.png", "Dos casillas hacia delante, hasta 5. Solo al terminar allí; duran 3 rondas."),
    DYNAMITE("Dinamita", "dinamita.png", "Centro hasta 5 hacia delante; zona ±2 fijada. Explota al comenzar tu próximo turno, incluso aturdido."),
    EYE("Ojo y rayo", "ojorayo.png", "Centro hasta 8 en ambos sentidos; zona ±3. En tu próximo turno puedes disparar, incluso a invisibles detectados."),
    BARRIER("Barrera", "barricada.png", "Protección personal hasta el comienzo de tu próximo turno."),
    CLOAK("Capa invisible", "capainvisible.png", "Oculta tu ficha hasta tu próximo turno. No protege de proyectiles, trampas ni explosiones.")
}
data class RaceTile(val id: Int, val x: Int, val y: Int, val progress: Int, val next: List<Int>)
data class RaceTrack(val tiles: List<RaceTile>, val boxes: Set<Int>, val length: Int = 32) {
    fun tile(id: Int) = tiles.first { it.id == id }
    fun reachable(from: Int, range: Int, backwards: Boolean = false): Set<Int> {
        val seen = linkedSetOf(from); var frontier = setOf(from)
        repeat(range) {
            frontier = frontier.flatMap { id -> if (backwards) tiles.filter { id in it.next }.map { it.id } else tile(id).next }.filter { it !in seen }.toSet()
            seen.addAll(frontier)
        }
        return seen - from
    }
    fun path(from: Int, range: Int, choices: Map<Int, Int>, backwards: Boolean = false): List<Int> {
        var at = from; val path = mutableListOf<Int>()
        repeat(range) {
            val links = if (backwards) tiles.filter { at in it.next }.map { it.id } else tile(at).next
            at = choices[at]?.takeIf { it in links } ?: links.first()
            if (at != from && at !in path) path.add(at)
        }
        return path
    }
    fun zone(center: Int, radius: Int, choices: Map<Int, Int>) = (listOf(center) + path(center, radius, choices) + path(center, radius, choices, true)).toSet()
}
data class HeldRaceItem(val item: RaceItem, val readyTurn: Int)
data class RacePlayer(val id: Int, val character: Racer, val human: Boolean, val tile: Int = 0, val lap: Int = 0,
    val turns: Int = 0, val stunned: Boolean = false, val barrier: Boolean = false, val invisible: Boolean = false,
    val inventory: List<HeldRaceItem> = emptyList(), val collected: Set<Int> = emptySet(), val history: List<Int> = listOf(0))
data class RaceTrap(val owner: Int, val tile: Int, val expiresRound: Int)
data class RacePrepared(val owner: Int, val center: Int, val zone: Set<Int>, val dueTurn: Int)
data class RacePrize(val item: RaceItem)
enum class RacePhase { HANDOFF, READY, EYE, REROLL, MOVING, BRANCH, PRIZE, END, SKIPPED, WON }
data class RaceSession(val track: RaceTrack, val players: List<RacePlayer>, val balance: RaceBalance = RaceBalance(),
    val laps: Int = 3, val current: Int = 0, val round: Int = 1, val phase: RacePhase = RacePhase.HANDOFF,
    val random: Long = 1, val die: Int = 0, val steps: Int = 0, val itemUsed: Boolean = false,
    val cost: Int = 0, val secondChance: Boolean = false, val traps: List<RaceTrap> = emptyList(),
    val dynamites: List<RacePrepared> = emptyList(), val eyes: List<RacePrepared> = emptyList(),
    val prize: RacePrize? = null, val winner: Int? = null, val event: Int = 0,
    val rockHits: Int = 0, val message: String = "Salida · tres vueltas", val effect: RaceItem? = null) {
    val actor get() = players[current]
    fun ranking() = players.sortedWith(compareByDescending<RacePlayer> { it.lap * track.length * 60 + track.tile(it.tile).progress }.thenBy { it.id })
    fun visible(viewer: Int): List<RacePlayer> {
        val zone = eyes.firstOrNull { it.owner == viewer }?.zone.orEmpty()
        return players.filter { !it.invisible || it.id == viewer || it.tile in zone }
    }
}
object RaceTracks {
    fun circuit(): RaceTrack {
        // Clockwise circuit, two five-step branches rejoining a three-step direct route.
        val points = (0..8).map { it to 2 } + (3..10).map { 8 to it } + (7 downTo 0).map { it to 10 } + (9 downTo 3).map { 0 to it }
        val tiles = points.mapIndexed { i, (x,y) -> RaceTile(i,x+2,y,i*60,listOf((i+1)%32)) }.toMutableList()
        // 5 -> 6 -> 7 -> 8 versus 5 -> 32 -> 33 -> 34 -> 35 -> 8.
        tiles[5] = tiles[5].copy(next = listOf(6,32))
        listOf(7 to 1,8 to 1,9 to 1,10 to 1).forEachIndexed { i,p -> tiles.add(RaceTile(32+i,p.first,p.second,300+(i+1)*36,listOf(if(i==3)8 else 33+i))) }
        // 18 -> 19 -> 20 -> 21 versus 18 -> 36 -> 37 -> 38 -> 39 -> 21.
        tiles[18] = tiles[18].copy(next = listOf(19,36))
        listOf(8 to 11,7 to 11,6 to 11,5 to 11).forEachIndexed { i,p -> tiles.add(RaceTile(36+i,p.first,p.second,1080+(i+1)*36,listOf(if(i==3)21 else 37+i))) }
        return RaceTrack(tiles,setOf(33,37))
    }
}
