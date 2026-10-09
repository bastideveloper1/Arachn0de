package com.r0ybt.arachn0de.metro

import org.json.JSONObject
import java.util.PriorityQueue

internal enum class MetroService { NORMAL, RED, GREEN }
internal enum class MetroStepKind { RIDE, TRANSFER, CHANGE, WAYPOINT }
internal data class MetroStation(val id: String, val name: String)
internal data class MetroLine(val id: String, val color: Long, val stations: List<String>, val express: List<String>)
internal data class MetroNetwork(val version: String, val checkedAt: String, val sources: List<String>, val stations: Map<String, MetroStation>, val lines: Map<String, MetroLine>) {
    fun accesses(station: String) = lines.values.filter { station in it.stations }
    fun stops(station: String, line: String, service: MetroService): Boolean {
        val l = lines.getValue(line)
        if (station !in l.stations) return false
        if (service == MetroService.NORMAL) return true
        if (l.express.isEmpty()) return false
        return l.express[l.stations.indexOf(station)] in setOf("C", if (service == MetroService.RED) "R" else "V")
    }
    companion object {
        fun decode(raw: String): MetroNetwork {
            require(raw.toByteArray(Charsets.UTF_8).size <= 262144)
            val o = MetroCodec.boundedObject(raw)
            fun strings(a: org.json.JSONArray) = (0 until a.length()).map(a::getString)
            val stations = (0 until o.getJSONArray("stations").length()).map { i -> o.getJSONArray("stations").getJSONObject(i).let { MetroStation(it.getString("id"), it.getString("name")) } }
            val lines = (0 until o.getJSONArray("lines").length()).map { i -> o.getJSONArray("lines").getJSONObject(i).let {
                MetroLine(it.getString("id"), 0xff000000L or it.getString("color").toLong(16), strings(it.getJSONArray("stations")), strings(it.getJSONArray("express")))
            } }
            require(stations.size in 2..512 && stations.all { it.id.matches(Regex("[a-z0-9-]{1,80}")) && it.name.isNotBlank() && it.name.length<=128 })
            require(stations.map { it.id }.distinct().size == stations.size && lines.map { it.id }.distinct().size == lines.size)
            require(lines.map { it.id }.toSet() == setOf("L1", "L2", "L3", "L4", "L4A", "L5", "L6"))
            require(lines.all { l -> l.stations.size in 2..128 && l.stations.distinct() == l.stations && l.stations.all { id -> stations.any { it.id == id } } &&
                if (l.id in setOf("L2", "L4", "L5")) l.express.size == l.stations.size && l.express.all { it in setOf("C", "R", "V") } && l.express.first() == "C" && l.express.last() == "C" else l.express.isEmpty() })
            require(lines.flatMap { it.stations }.toSet()==stations.map { it.id }.toSet())
            val neighbors=lines.flatMap { it.stations.zipWithNext() }.flatMap { (a,b)->listOf(a to b,b to a) }.groupBy({it.first},{it.second})
            val visited=mutableSetOf(stations.first().id);val pending=java.util.ArrayDeque<String>();pending.add(stations.first().id)
            while(pending.isNotEmpty()) neighbors[pending.removeFirst()].orEmpty().forEach { if(visited.add(it)) pending.add(it) }
            require(visited.size==stations.size) { "Catálogo Metro desconectado." }
            require(o.getString("version").length in 1..128 && o.getString("checkedAt").matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
            return MetroNetwork(o.getString("version"), o.getString("checkedAt"), strings(o.getJSONArray("sources")), stations.associateBy { it.id }, lines.associateBy { it.id })
        }
    }
}
internal data class MetroRestrictions(val closed: Set<String> = emptySet(), val avoided: Set<String> = emptySet(), val interrupted: Set<String> = emptySet()) {
    companion object { fun segment(line: String, a: String, b: String) = "$line:${listOf(a, b).sorted().joinToString(":")}" }
}
internal data class MetroStep(val from: String, val to: String, val line: String, val direction: Int, val service: MetroService, val kind: MetroStepKind, val nextLine: String = line) {
    val minutes: Int get() = when (kind) { MetroStepKind.RIDE -> 2; MetroStepKind.WAYPOINT -> 0; else -> 4 }
}
internal data class MetroRoute(val networkVersion: String, val stops: List<String>, val express: Boolean, val steps: List<MetroStep>,val departure:Long?=null) {
    val minutes get() = steps.sumOf { it.minutes }
    val physicalSegments get() = steps.count { it.kind == MetroStepKind.RIDE }
    val transfers get() = steps.count { it.kind == MetroStepKind.TRANSFER }
    val serviceChanges get() = steps.count { it.kind == MetroStepKind.CHANGE }
    val usedExpress get() = steps.any { it.service != MetroService.NORMAL }
}
/** Train state retains direction and service while crossing a non-stop/closed station. */
internal object MetroPlanner {
    private data class State(val station: String, val line: String, val direction: Int, val service: MetroService, val arrival:Int=0) {
        val key get() = "$station/$line/$direction/${service.name}/$arrival"
    }
    private data class Cost(val minutes: Int = 0, val changes: Int = 0, val segments: Int = 0): Comparable<Cost> {
        override fun compareTo(other: Cost) = compareValuesBy(this, other, Cost::minutes, Cost::changes, Cost::segments)
        fun plus(step: MetroStep) = Cost(minutes + step.minutes, changes + if (step.kind != MetroStepKind.RIDE) 1 else 0, segments + if (step.kind == MetroStepKind.RIDE) 1 else 0)
    }
    private data class Entry(val state: State, val cost: Cost)
    fun plan(snapshot: MetroNetwork, stops: List<String>, express: Boolean, restrictions: MetroRestrictions): MetroRoute? {
        val net = MetroCatalogRevision.forPlanning(snapshot)
        require(stops.size in 2..32 && stops.all { it in net.stations })
        if (stops.any { it in restrictions.closed }) return null
        // Avoidance is a preference: retry without it only when no preferred route exists.
        val preferred = if (restrictions.avoided.isNotEmpty() && stops.none { it in restrictions.avoided }) planPreferred(net, stops, express, restrictions) else null
        return preferred ?: planPreferred(net, stops, express, restrictions.copy(avoided = emptySet()))
    }
    /** Scheduled reference scenario. Missing service data cannot authorize a stop.
     * The timetable is evaluated at every physical edge, including later lines and vias.
     * No concrete train is predicted, and ambiguous boundary crossings fail safely. */
    fun planAt(snapshot:MetroNetwork,stops:List<String>,departure:Long,restrictions:MetroRestrictions,schedule:MetroSchedule=MetroSchedule()):MetroRoute? {
        val net=MetroCatalogRevision.forPlanning(snapshot)
        require(departure>=0 && departure<=Long.MAX_VALUE-(net.stations.size*2+net.lines.size*4)*32*60_000L)
        require(stops.size in 2..32 && stops.all {it in net.stations})
        if(stops.any {it in restrictions.closed}) return null
        val preferred=if(restrictions.avoided.isNotEmpty() && stops.none {it in restrictions.avoided}) planPreferred(net,stops,true,restrictions,departure,schedule) else null
        return preferred ?: planPreferred(net,stops,true,restrictions.copy(avoided=emptySet()),departure,schedule)
    }
    private fun planPreferred(net: MetroNetwork, stops: List<String>, express: Boolean, restrictions: MetroRestrictions,departure:Long?=null,schedule:MetroSchedule?=null): MetroRoute? {
        val steps = mutableListOf<MetroStep>()
        stops.zipWithNext().forEachIndexed { index, (a,b) ->
            val leg = direct(net, a, b, express, restrictions,departure?.plus(steps.sumOf {it.minutes*60_000L}),schedule) ?: return null
            steps.addAll(leg)
            if (index < stops.size - 2) {
                val previous = steps.lastOrNull()
                steps.add(MetroStep(b, b, previous?.line ?: net.accesses(b).first().id, previous?.direction ?: 1, previous?.service ?: if(express && net.accesses(b).first().express.isNotEmpty()) listOf(MetroService.RED,MetroService.GREEN).first { net.stops(b,net.accesses(b).first().id,it) } else MetroService.NORMAL, MetroStepKind.WAYPOINT))
            }
        }
        return MetroRoute(net.version, stops, express, steps,departure)
    }
    private fun direct(net: MetroNetwork, origin: String, destination: String, express: Boolean, r: MetroRestrictions,departure:Long?=null,schedule:MetroSchedule?=null): List<MetroStep>? {
        if (origin == destination) return emptyList()
        fun services(line: MetroLine,at:Long?) = if(schedule!=null && at!=null) when(schedule.express(line.id,1,at)) {
            true->listOf(MetroService.RED,MetroService.GREEN);false->listOf(MetroService.NORMAL);null->emptyList()
        } else if (express && line.express.isNotEmpty()) listOf(MetroService.RED, MetroService.GREEN) else listOf(MetroService.NORMAL)
        fun board(station: String,at:Long?=departure) = net.accesses(station).flatMap { line -> services(line,at).filter { net.stops(station, line.id, it) }.flatMap { service -> listOf(-1, 1).map { State(station, line.id, it, service) } } }.sortedBy { it.key }
        val queue = PriorityQueue(compareBy<Entry> { it.cost }.thenBy { it.state.key })
        val costs = mutableMapOf<State,Cost>(); val parents = mutableMapOf<State,Pair<State,MetroStep>>()
        board(origin).forEach { costs[it] = Cost(); queue.add(Entry(it, Cost())) }
        while (queue.isNotEmpty()) {
            val (state,cost) = queue.remove()
            if (costs[state] != cost) continue
            val canStop = state.station !in r.closed && state.station !in r.avoided && net.stops(state.station, state.line, state.service)
            if (state.station == destination && parents[state]?.second?.kind==MetroStepKind.RIDE && canStop && (schedule==null || departure==null || schedule.express(state.line,state.direction,departure+cost.minutes*60_000L)==(state.service!=MetroService.NORMAL))) {
                val result = mutableListOf<MetroStep>(); var cursor = state
                while (cursor in parents) { val (prev,step) = parents.getValue(cursor); result.add(step); cursor = prev }
                return result.asReversed()
            }
            val line = net.lines.getValue(state.line)
            val next = line.stations.getOrNull(line.stations.indexOf(state.station) + state.direction)
            val edges = mutableListOf<Pair<State,MetroStep>>()
            if (next != null && next !in r.avoided && MetroRestrictions.segment(state.line, state.station, next) !in r.interrupted) {
                val ride=MetroStep(state.station,next,state.line,state.direction,state.service,MetroStepKind.RIDE)
                if(schedule==null || departure==null || schedule.permits(ride,departure+cost.minutes*60_000L))
                    edges.add(state.copy(station = next) to ride)
            }
            if (canStop) board(state.station,departure?.plus((cost.minutes+4)*60_000L)).filter { it.line!=state.line || it.direction!=state.direction || it.service!=state.service }.forEach { candidate ->
                edges.add(candidate to MetroStep(state.station, state.station, state.line, state.direction, state.service,
                    if (candidate.line == state.line) MetroStepKind.CHANGE else MetroStepKind.TRANSFER, candidate.line))
            }
            edges.sortedBy { it.first.key }.forEach { (rawTarget,step) ->
                val candidate = cost.plus(step)
                // Finite horizon; cycles cannot create an unbounded time-expanded graph.
                if(schedule!=null && candidate.minutes>net.stations.size*2+net.lines.size*4) return@forEach
                val target=if(schedule!=null) rawTarget.copy(arrival=candidate.minutes) else rawTarget
                if(schedule!=null) {
                    // A cyclic detour is not an implicit way of waiting for a new service.
                    var cursor=state;var repeats=false
                    while(true) {
                        if(cursor.station==target.station && cursor.line==target.line && cursor.direction==target.direction && cursor.service==target.service) {repeats=true;break}
                        cursor=parents[cursor]?.first ?: break
                    }
                    if(repeats) return@forEach
                }
                if (candidate < (costs[target] ?: Cost(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))) {
                    costs[target] = candidate; parents[target] = state to step; queue.add(Entry(target,candidate))
                }
            }
        }
        return null
    }
    fun affected(route: MetroRoute, net: MetroNetwork, restrictions: MetroRestrictions) =
        (if(route.departure!=null) planAt(net,route.stops,route.departure,restrictions) else plan(net, route.stops, route.express, restrictions))?.steps != route.steps
}
