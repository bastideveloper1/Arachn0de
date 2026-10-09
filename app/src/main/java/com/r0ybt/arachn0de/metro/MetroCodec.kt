package com.r0ybt.arachn0de.metro

import org.json.JSONArray
import org.json.JSONObject

internal data class MetroPreferences(val catalog: String, val home: String? = null, val favorites: Set<String> = emptySet(), val restrictions: MetroRestrictions = MetroRestrictions()) {
    val network get() = MetroNetwork.decode(catalog)
    val planningNetwork get() = MetroCatalogRevision.forPlanning(network)
}
internal data class MetroJourneyData(val plan: MetroRoute, val sessions: List<MetroSession> = emptyList()) {
    val active get() = sessions.lastOrNull()?.takeIf { it.ended == null }
}
internal object MetroCodec {
    const val MAX_BYTES = 1024 * 1024
    private fun obj(vararg pairs: Pair<String,Any?>) = JSONObject().apply { pairs.forEach { (k,v) -> put(k,v ?: JSONObject.NULL) } }
    private fun JSONObject.string(k: String) = (get(k) as? String) ?: error("Texto Metro inválido")
    private fun JSONObject.long(k: String): Long { val v=get(k); require(v is Int || v is Long); return (v as Number).toLong() }
    private fun JSONObject.int(k: String): Int { val n=long(k); require(n in Int.MIN_VALUE..Int.MAX_VALUE); return n.toInt() }
    private fun JSONObject.bool(k: String) = (get(k) as? Boolean) ?: error("Booleano Metro inválido")
    private fun JSONObject.nullString(k: String) = if(isNull(k)) null else string(k)
    private fun JSONObject.nullLong(k: String) = if(isNull(k)) null else long(k)
    private fun strings(a: JSONArray) = (0 until a.length()).map { (a.get(it) as? String) ?: error("Lista Metro inválida") }
    private fun objects(a: JSONArray) = (0 until a.length()).map(a::getJSONObject)
    internal fun boundedObject(raw: String): JSONObject {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        android.util.JsonReader(java.io.StringReader(raw)).use { r ->
            fun read(depth: Int) {
                require(depth <= 16)
                when(r.peek()) {
                    android.util.JsonToken.BEGIN_OBJECT -> { r.beginObject(); val keys=hashSetOf<String>(); while(r.hasNext()) { require(keys.size < 64 && keys.add(r.nextName())); read(depth+1) }; r.endObject() }
                    android.util.JsonToken.BEGIN_ARRAY -> { r.beginArray(); var size=0; while(r.hasNext()) { require(++size <= 4096); read(depth+1) }; r.endArray() }
                    android.util.JsonToken.STRING -> require(r.nextString().length <= 262144)
                    android.util.JsonToken.NUMBER -> require(r.nextString().toLongOrNull() != null)
                    android.util.JsonToken.BOOLEAN -> r.nextBoolean()
                    android.util.JsonToken.NULL -> r.nextNull()
                    else -> error("JSON Metro inválido")
                }
            }
            read(0); require(r.peek()==android.util.JsonToken.END_DOCUMENT)
        }
        return JSONObject(raw)
    }
    private fun parse(raw: String)=boundedObject(raw).also { require(it.int("version")==1) }
    fun preferences(p: MetroPreferences) = obj("version" to 1, "catalog" to p.catalog, "home" to p.home, "favorites" to JSONArray(p.favorites.sorted()),
        "closed" to JSONArray(p.restrictions.closed.sorted()), "avoided" to JSONArray(p.restrictions.avoided.sorted()), "interrupted" to JSONArray(p.restrictions.interrupted.sorted())).toString()
    fun preferences(raw: String): MetroPreferences {
        val o=parse(raw); o.fields("version","catalog","home","favorites","closed","avoided","interrupted"); listOf("favorites","closed","avoided","interrupted").forEach { key-> val a=strings(o.getJSONArray(key));require(a.distinct().size==a.size) }; val p=MetroPreferences(o.string("catalog"),o.nullString("home"),strings(o.getJSONArray("favorites")).toSet(),
            MetroRestrictions(strings(o.getJSONArray("closed")).toSet(),strings(o.getJSONArray("avoided")).toSet(),strings(o.getJSONArray("interrupted")).toSet()))
        val net=p.network
        require(p.home==null || p.home in net.stations)
        require((p.favorites+p.restrictions.closed+p.restrictions.avoided).all { it in net.stations })
        val segments=net.lines.values.flatMap { l -> l.stations.zipWithNext().map { (a,b)->MetroRestrictions.segment(l.id,a,b) } }.toSet()
        require(p.restrictions.interrupted.all { it in segments })
        return p
    }
    private fun JSONObject.fields(vararg expected: String) { require(keys().asSequence().all { it in expected }) }
    private fun route(r: MetroRoute) = obj("network" to r.networkVersion,"stops" to JSONArray(r.stops),"express" to r.express,"steps" to JSONArray(r.steps.map { s ->
        obj("from" to s.from,"to" to s.to,"line" to s.line,"direction" to s.direction,"service" to s.service.name,"kind" to s.kind.name,"nextLine" to s.nextLine) }))
    private fun route(o: JSONObject, snapshot: MetroNetwork): MetroRoute {
        val net = MetroCatalogRevision.forRoute(snapshot,o.string("network"))
        o.fields("network","stops","express","steps")
        val r=MetroRoute(o.string("network"),strings(o.getJSONArray("stops")),o.bool("express"),objects(o.getJSONArray("steps")).map { s ->
            s.fields("from","to","line","direction","service","kind","nextLine")
            MetroStep(s.string("from"),s.string("to"),s.string("line"),s.int("direction"),MetroService.valueOf(s.string("service")),MetroStepKind.valueOf(s.string("kind")),s.string("nextLine")) })
        require(r.networkVersion==net.version && r.stops.size in 2..32 && r.stops.all { it in net.stations } && r.steps.size <= 4096)
        if(r.steps.isEmpty()) require(r.stops.distinct().size==1)
        else {
            require(r.steps.first().from==r.stops.first() && r.steps.last().to==r.stops.last())
            require(r.steps.zipWithNext().all { (a,b)->a.to==b.from })
            r.steps.forEachIndexed { i,s ->
                val line=net.lines.getValue(s.line)
                val previous=r.steps.getOrNull(i-1)
                val next=r.steps.getOrNull(i+1)
                require(s.direction in setOf(-1,1) && s.from in line.stations && s.to in line.stations && s.nextLine in net.lines)
                require(s.service==MetroService.NORMAL || r.express && line.express.isNotEmpty())
                require(!r.express || line.express.isEmpty() || s.service!=MetroService.NORMAL)
                if(s.kind==MetroStepKind.RIDE) {
                    require(line.stations.getOrNull(line.stations.indexOf(s.from)+s.direction)==s.to && s.nextLine==s.line)
                    if(previous==null || previous.kind!=MetroStepKind.RIDE) require(net.stops(s.from,s.line,s.service))
                    if(next==null || next.kind!=MetroStepKind.RIDE) require(net.stops(s.to,s.line,s.service))
                    if(next?.kind==MetroStepKind.RIDE) require(next.line==s.line && next.direction==s.direction && next.service==s.service)
                } else {
                    require(s.from==s.to && s.to in net.lines.getValue(s.nextLine).stations && net.stops(s.from,s.line,s.service))
                    if(s.kind!=MetroStepKind.WAYPOINT) {
                        require(previous?.kind==MetroStepKind.RIDE && previous.line==s.line && previous.service==s.service && previous.direction==s.direction)
                        require(next?.kind==MetroStepKind.RIDE && next.line==s.nextLine && net.stops(s.to,next.line,next.service))
                        require(if(s.kind==MetroStepKind.TRANSFER) s.nextLine!=s.line else s.nextLine==s.line && (next.service!=s.service || next.direction!=s.direction))
                    }
                }
            }
        }
        require(r.steps.filter { it.kind==MetroStepKind.WAYPOINT }.map { it.to }==r.stops.drop(1).dropLast(1))
        return r
    }
    fun journey(j: MetroJourneyData) = obj("version" to 2,"plan" to route(j.plan),"sessions" to JSONArray(j.sessions.map { s ->
        obj("id" to s.id,"route" to route(s.route),"originalRoute" to route(s.originalRoute),"personId" to s.personId,"startElapsed" to s.startElapsed,"startBoot" to s.startBoot,"pausedElapsed" to s.pausedElapsed,"pausedBoot" to s.pausedBoot,"realMillis" to s.realMillis,"routeHistory" to JSONArray(s.routeHistory.map { route(it) }),"historyElapsedTrusted" to s.historyElapsedTrusted,"automatic" to s.automatic,"start" to s.start,"anchorWall" to s.anchorWall,"anchorElapsed" to s.anchorElapsed,"boot" to s.boot,"offset" to s.offset,
            "pausedAt" to s.pausedAt,"pausedMillis" to s.pausedMillis,"ended" to s.ended,"confirmed" to s.confirmed,"uncertain" to s.uncertain,
            "events" to JSONArray(s.events.map { e->obj("kind" to e.kind,"at" to e.at,"offset" to e.offset,"station" to e.station) })) })).toString()
    fun journey(raw: String, net: MetroNetwork): MetroJourneyData {
        val o=boundedObject(raw); val version=o.int("version"); require(version in 1..2);o.fields("version","plan","sessions")
        val j=MetroJourneyData(route(o.getJSONObject("plan"),net),objects(o.getJSONArray("sessions")).map { s->
            s.fields("id","route","originalRoute","personId","start","anchorWall","anchorElapsed","boot","offset","pausedAt","pausedMillis","ended","confirmed","uncertain","events","startElapsed","startBoot","pausedElapsed","pausedBoot","realMillis","routeHistory","historyElapsedTrusted",*if(version==2) arrayOf("automatic") else emptyArray())
            MetroSession(s.string("id"),route(s.getJSONObject("route"),net),s.long("start"),s.long("anchorWall"),s.long("anchorElapsed"),s.int("boot"),s.long("offset"),s.nullLong("pausedAt"),
                s.long("pausedMillis"),s.nullLong("ended"),s.nullString("confirmed"),objects(s.getJSONArray("events")).map { e->e.fields("kind","at","offset","station");MetroEvent(e.string("kind"),e.long("at"),e.long("offset"),e.nullString("station")) },s.bool("uncertain"),if(s.has("originalRoute")) route(s.getJSONObject("originalRoute"),net) else route(s.getJSONObject("route"),net),if(s.has("personId")) s.nullString("personId") else null,
                if(s.has("startElapsed")) s.long("startElapsed") else s.long("anchorElapsed"), if(s.has("startBoot")) s.int("startBoot") else s.int("boot"),
                if(s.has("pausedElapsed")) s.nullLong("pausedElapsed") else null, if(s.has("pausedBoot")) s.nullLong("pausedBoot")?.also { require(it in 0..Int.MAX_VALUE) }?.toInt() else null,
                if(s.has("realMillis")) s.nullLong("realMillis") else null, if(s.has("routeHistory")) objects(s.getJSONArray("routeHistory")).map { route(it,net) } else emptyList(),if(s.has("historyElapsedTrusted")) s.bool("historyElapsedTrusted") else true,if(version==2) s.bool("automatic") else false) })
        require(j.sessions.size <= 1000 && j.sessions.map { it.id }.distinct().size==j.sessions.size)
        require(j.sessions.dropLast(1).all { it.ended!=null })
        j.sessions.forEach { s->
            require(s.realMillis==null || s.ended!=null)
            require(s.ended==null || s.pausedAt==null)
            require((s.pausedElapsed==null)==(s.pausedBoot==null) && (s.pausedElapsed==null || s.pausedAt!=null))
            require(s.events.isNotEmpty() && s.events.first().kind=="BOARD" && s.events.first().offset==0L)
            require((s.events.last().kind=="ARRIVED")== (s.ended!=null))
            require(s.events.all { it.kind in setOf("BOARD","PAUSE","RESUME","CONFIRM","BETWEEN","REPLAN","ARRIVED") || it.kind.matches(Regex("PASS_[0-9]{1,4}")) && it.kind.substringAfter("PASS_").toInt()<4096 })
            require(s.startElapsed>=0 && s.startBoot>=0 && (s.pausedElapsed==null || s.pausedElapsed>=0) && (s.realMillis==null || s.realMillis>=0) && s.routeHistory.size<=128)
            require(s.personId==null || s.personId.isNotBlank() && s.personId.length<=128)
            require(s.id.isNotBlank() && s.id.length<=128 && s.start>=0 && s.anchorWall>=0 && s.anchorElapsed>=0 && s.boot>=0 && s.pausedMillis>=0)
            require(s.offset in 0..s.route.minutes*60_000L && (s.ended==null || s.ended>=0) && (s.pausedAt==null || s.pausedAt>=0) && (s.confirmed==null || s.confirmed in net.stations))
            require(s.events.size<=4096 && s.events.all { it.at>=0 && it.offset in 0..(4096L*4*60_000) && it.kind.length in 1..64 && (it.station==null || it.station in net.stations) })
        }
        return j
    }
}
