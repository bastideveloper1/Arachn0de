package com.r0ybt.arachn0de.metro

import java.text.Normalizer
import java.util.Locale

/** Only search keys are normalized; catalog identity and display names stay intact. */
internal object MetroSearch {
    fun key(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
    fun stations(net: MetroNetwork, query: String, line: String? = null): List<MetroStation> {
        val q=key(query); val words=q.split(' ').filter { it.isNotEmpty() }
        fun rank(name: String): Int { val n=key(name); return when {
            n==q -> 0; n.startsWith(q) -> 1; n.contains(q) -> 2
            words.all { n.contains(it) } -> 3; else -> 4
        } }
        return net.stations.values.filter { (line==null || it.id in net.lines.getValue(line).stations) && rank(it.name)<4 }
            .sortedWith(compareBy<MetroStation> { rank(it.name) }.thenBy { key(it.name) }.thenBy { it.id })
    }
}

internal object MetroPresentation {
    fun direction(net: MetroNetwork, lineId: String, boarding: String, next: String): String {
        val line=net.lines.getValue(lineId)
        val from=line.stations.indexOf(boarding); val to=line.stations.indexOf(next)
        require(from>=0 && to>=0 && from!=to) { "Dirección sin tramo válido" }
        return net.stations.getValue(if(to>from) line.stations.last() else line.stations.first()).name
    }
    fun train(service: MetroService)=when(service) {
        MetroService.NORMAL -> "Tomar tren normal"
        MetroService.RED -> "Tomar tren Ruta Roja"
        MetroService.GREEN -> "Tomar tren Ruta Verde"
    }
    fun instruction(route: MetroRoute, index: Int, net: MetroNetwork): String {
        val s=route.steps[index]; val here=net.stations.getValue(s.from).name
        val following=route.steps.drop(index+1).firstOrNull { it.kind==MetroStepKind.RIDE }
        return when(s.kind) {
            MetroStepKind.RIDE -> "${s.line} · Dirección ${direction(net,s.line,s.from,s.to)} · ${train(s.service)}" +
                if(net.stops(s.to,s.line,s.service)) "" else " · ${net.stations.getValue(s.to).name}: sin detención"
            MetroStepKind.TRANSFER -> "Baja en $here. Combina con ${s.nextLine}" + (following?.let { " · Dirección ${direction(net,it.line,it.from,it.to)} · ${train(it.service)}" } ?: "") + " · ≈ 4 min"
            MetroStepKind.CHANGE -> "Cambia de tren en $here" + (following?.let { " · ${it.line} · Dirección ${direction(net,it.line,it.from,it.to)} · ${train(it.service)}" } ?: "") + " · ≈ 4 min"
            MetroStepKind.WAYPOINT -> "Parada intermedia: $here. Permanencia no incluida; pausa si te detienes."
        }
    }
    /** Count physical station visits including boarding, without counting transfers twice.
     * Revisited stations count again. Express pass-throughs are distinguished separately. */
    fun stations(route: MetroRoute)=route.physicalSegments+1
    fun stoppingStations(route: MetroRoute, net: MetroNetwork):Int {
        val historical=MetroCatalogRevision.forRoute(net,route.networkVersion)
        return 1+route.steps.count {it.kind==MetroStepKind.RIDE && historical.stops(it.to,it.line,it.service)}
    }
    fun ridesRemaining(route: MetroRoute, position: MetroPosition)=route.steps.drop(position.step).count { it.kind==MetroStepKind.RIDE }
    fun untilTransfer(route: MetroRoute, position: MetroPosition): Int? {
        val pending=route.steps.drop(position.step)
        val transfer=pending.indexOfFirst { it.kind==MetroStepKind.TRANSFER }
        return if(transfer<0) null else pending.take(transfer).count { it.kind==MetroStepKind.RIDE }
    }
    fun positionText(route: MetroRoute, p: MetroPosition, net: MetroNetwork): String {
        val step=route.steps.getOrNull(p.step)
        return when {
            step==null -> "Destino estimado: ${net.stations.getValue(route.stops.last()).name}"
            step.kind==MetroStepKind.RIDE && p.fraction>0 -> "Entre ${net.stations.getValue(step.from).name} y ${net.stations.getValue(step.to).name}"
            else -> net.stations.getValue(p.station).name
        }
    }
}
