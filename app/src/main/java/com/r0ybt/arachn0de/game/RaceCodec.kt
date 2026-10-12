package com.r0ybt.arachn0de.game

import org.json.JSONArray
import org.json.JSONObject

/** V4 replaces playable rules, but legacy V1–V3 remain valid export/restore payloads. */
object RaceCodec {
    private fun obj(vararg pairs: Pair<String,Any?>) = JSONObject().apply { pairs.forEach { put(it.first,it.second ?: JSONObject.NULL) } }
    private fun prepared(p: RacePrepared) = obj("owner" to p.owner,"center" to p.center,"zone" to JSONArray(p.zone.sorted()),"due" to p.dueTurn)
    fun encode(s: RaceSession): String = obj(
        "version" to 4,"laps" to s.laps,"current" to s.current,"round" to s.round,"phase" to s.phase.name,
        "random" to s.random,"die" to s.die,"steps" to s.steps,"used" to s.itemUsed,"cost" to s.cost,"second" to s.secondChance,
        "winner" to s.winner,"event" to s.event,"rockHits" to s.rockHits,"message" to s.message,"effect" to s.effect?.name,"prize" to s.prize?.item?.name,
        "balance" to JSONArray(listOf(s.balance.rock,s.balance.wasps,s.balance.placement,s.balance.eye,s.balance.blastRadius,s.balance.eyeRadius,s.balance.trapRounds)),
        "track" to obj("length" to s.track.length,"boxes" to JSONArray(s.track.boxes.sorted()),"tiles" to JSONArray(s.track.tiles.map { t ->
            obj("id" to t.id,"x" to t.x,"y" to t.y,"progress" to t.progress,"next" to JSONArray(t.next)) })),
        "players" to JSONArray(s.players.map { p -> obj("id" to p.id,"character" to p.character.name,"human" to p.human,"tile" to p.tile,
            "lap" to p.lap,"turns" to p.turns,"stunned" to p.stunned,"barrier" to p.barrier,"invisible" to p.invisible,
            "inventory" to JSONArray(p.inventory.map { obj("item" to it.item.name,"ready" to it.readyTurn) }),"collected" to JSONArray(p.collected.sorted()),"history" to JSONArray(p.history)) }),
        "traps" to JSONArray(s.traps.map { obj("owner" to it.owner,"tile" to it.tile,"expires" to it.expiresRound) }),
        "dynamites" to JSONArray(s.dynamites.map(::prepared)),"eyes" to JSONArray(s.eyes.map(::prepared)))
        .toString().also { require(it.toByteArray(Charsets.UTF_8).size<=GameSessionCodec.MAX_BYTES) }
    private fun JSONObject.int(k: String) = (get(k) as? Number)?.toLong()?.also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }?.toInt() ?: error("Entero: $k")
    private fun JSONObject.str(k: String) = get(k) as? String ?: error("Cadena: $k")
    private fun JSONObject.bool(k: String) = get(k) as? Boolean ?: error("Booleano: $k")
    private fun JSONArray.ints(): List<Int> = (0 until length()).map { i -> (get(i) as? Number)?.toLong()?.also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }?.toInt() ?: error("Entero") }
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun unique(values: List<Int>): Set<Int> { require(values.distinct().size==values.size); return values.toSet() }
    private fun decodePrepared(o: JSONObject) = RacePrepared(o.int("owner"),o.int("center"),unique(o.getJSONArray("zone").ints()),o.int("due"))
    fun decode(raw: String): RaceSession? = runCatching {
        require(raw.toByteArray(Charsets.UTF_8).size<=GameSessionCodec.MAX_BYTES)
        GameSessionCodec.validateJson(raw)
        val o=JSONObject(raw); require(o.int("version")==4)
        val t=o.getJSONObject("track")
        val track=RaceTrack(t.getJSONArray("tiles").objects().map { RaceTile(it.int("id"),it.int("x"),it.int("y"),it.int("progress"),it.getJSONArray("next").ints()) },unique(t.getJSONArray("boxes").ints()),t.int("length"))
        val b=o.getJSONArray("balance").ints(); require(b.size==7 && b.all { it in 1..8 })
        val players=o.getJSONArray("players").objects().map { p -> RacePlayer(p.int("id"),Racer.valueOf(p.str("character")),p.bool("human"),p.int("tile"),p.int("lap"),p.int("turns"),p.bool("stunned"),p.bool("barrier"),p.bool("invisible"),
            p.getJSONArray("inventory").objects().map { HeldRaceItem(RaceItem.valueOf(it.str("item")),it.int("ready")) },unique(p.getJSONArray("collected").ints()),p.getJSONArray("history").ints()) }
        RaceSession(track,players,RaceBalance(b[0],b[1],b[2],b[3],b[4],b[5],b[6]),o.int("laps"),o.int("current"),o.int("round"),RacePhase.valueOf(o.str("phase")),
            (o.get("random") as? Number)?.toLong() ?: error("Semilla"),o.int("die"),o.int("steps"),o.bool("used"),o.int("cost"),o.bool("second"),
            o.getJSONArray("traps").objects().map { RaceTrap(it.int("owner"),it.int("tile"),it.int("expires")) },
            o.getJSONArray("dynamites").objects().map(::decodePrepared),o.getJSONArray("eyes").objects().map(::decodePrepared),
            if(o.isNull("prize"))null else RacePrize(RaceItem.valueOf(o.str("prize"))),if(o.isNull("winner"))null else o.int("winner"),o.int("event"),o.int("rockHits"),o.str("message"),
            if(o.isNull("effect"))null else RaceItem.valueOf(o.str("effect"))).also(::validate)
    }.getOrNull()
    fun validPayload(raw: String) = decode(raw)!=null || GameSessionCodec.decode(raw)!=null
    fun validate(s: RaceSession) {
        val t=s.track; val ids=t.tiles.map { it.id }.toSet()
        require(t.length==32 && t.tiles.size==40 && ids==(0..39).toSet())
        require(t.tiles.map { it.x to it.y }.distinct().size==40)
        require(t.tiles.all { it.x in 0..30 && it.y in 0..30 && it.progress in 0 until t.length*60 && it.next.size in 1..2 && it.next.distinct()==it.next && it.next.all(ids::contains) })
        // Canonical topology/progress is versioned. Corrupt branches cannot change lap counting or ranges.
        val expected=RaceTracks.circuit()
        require(t.tiles.associateBy { it.id } == expected.tiles.associateBy { it.id })
        require(t.boxes.size in 2..20 && t.boxes.containsAll(expected.boxes) && t.boxes.all { it in ids && it!=0 })
        require(s.players.size in 2..10 && s.players.map { it.id }==s.players.indices.toList() && s.players.map { it.character }.distinct().size==s.players.size)
        require(s.laps in 1..9 && s.current in s.players.indices && s.round in 1..1000000 && s.event in 0..10000000 && s.rockHits in 0..s.event)
        require(s.die in 0..6 && s.steps in 0..6 && s.cost in 0..1 && (!s.itemUsed || !s.secondChance || s.cost==1))
        require(s.cost==0 || s.itemUsed)
        require(s.message.length<=300 && s.message.none { it=='\u0000' })
        require(s.players.all { p -> p.tile in ids && p.lap in 0..s.laps && p.turns in 0..1000000 && p.inventory.size<=2 && p.inventory.all { it.readyTurn in 0..p.turns+1 } && p.collected.all(t.boxes::contains) && p.history.size in 1..16 && p.history.last()==p.tile && p.history.all(ids::contains) && p.history.zipWithNext().all { (a,b)->b in t.tile(a).next } })
        require((s.phase==RacePhase.PRIZE)==(s.prize!=null))
        require((s.phase==RacePhase.WON)==(s.winner!=null))
        require(s.winner==null || s.winner in s.players.indices && s.players[s.winner].lap==s.laps)
        require(s.players.all { it.lap<s.laps || it.id==s.winner })
        require(s.phase !in setOf(RacePhase.MOVING,RacePhase.BRANCH) || s.steps>0)
        require(s.phase!=RacePhase.BRANCH || t.tile(s.actor.tile).next.size==2)
        require(s.phase!=RacePhase.REROLL || s.secondChance && s.die in 1..6 && s.itemUsed && s.cost==1)
        require(s.phase!=RacePhase.SKIPPED || s.actor.stunned)
        require(s.traps.size<=s.players.size*2*s.balance.trapRounds && s.traps.all { it.owner in s.players.indices && it.tile in ids && it.expiresRound in s.round+1..s.round+s.balance.trapRounds })
        require(s.dynamites.size<=10 && s.eyes.size<=10 && s.eyes.map { it.owner }.distinct().size==s.eyes.size)
        (s.dynamites.map { it to s.balance.blastRadius }+s.eyes.map { it to s.balance.eyeRadius }).forEach { (p,radius) ->
            require(p.owner in s.players.indices && p.center in ids && p.zone.contains(p.center) && p.zone.all(ids::contains) && p.zone.size in 1..1+2*radius)
            require(p.dueTurn in s.players[p.owner].turns..s.players[p.owner].turns+1)
            val junctions=t.tiles.mapNotNull { tile ->
                val links=if(tile.next.size>1)tile.next else t.tiles.filter { tile.id in it.next }.map { it.id }.takeIf { it.size>1 }
                links?.let { tile.id to it }
            }
            var choices=listOf(emptyMap<Int,Int>())
            junctions.forEach { (from,links) -> choices=choices.flatMap { route -> links.map { route+(from to it) } } }
            require(choices.any { t.zone(p.center,radius,it)==p.zone })
        }
        require(s.phase!=RacePhase.EYE || s.eyes.any { it.owner==s.actor.id && it.dueTurn==s.actor.turns } && !s.actor.stunned)
    }
}
