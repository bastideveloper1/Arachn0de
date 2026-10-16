package com.r0ybt.arachn0de.metro

import com.r0ybt.arachn0de.data.local.MetroLearningEventEntity
import org.json.JSONObject
import kotlin.math.abs

internal enum class MetroCaptureKind { DOORS, CORRECTION, ARRIVAL, PAUSE, RESUME, PAUSED_LOCATION }

/** All values are captured at the user's action, never reconstructed from wall-clock history. */
internal data class MetroCapture(
    val route: MetroRoute,
    val station: String,
    val stepIndex: Int,
    val line: String,
    val direction: Int,
    val service: MetroService,
    val kind: MetroCaptureKind,
    val time: MetroTime,
    val clockDomain: String,
    val paused: Boolean,
    val pauseMillis: Long,
    val valid: Boolean,
    val exclusion: String?,
    val estimatedOffset: Long,
    val etaMillis: Long,
)

internal object MetroLearningCodec {
    fun encode(e: MetroCapture): String = JSONObject().apply {
        put("version", if(e.kind in setOf(MetroCaptureKind.PAUSE,MetroCaptureKind.RESUME,MetroCaptureKind.PAUSED_LOCATION)) 2 else 1)
        put("route", MetroCodec.journey(MetroJourneyData(e.route)))
        put("station", e.station); put("stepIndex", e.stepIndex)
        put("line", e.line); put("direction", e.direction); put("service", e.service.name)
        put("kind", e.kind.name); put("wall", e.time.wall); put("elapsed", e.time.elapsed); put("boot", e.time.boot)
        put("clockDomain", e.clockDomain); put("paused", e.paused); put("pauseMillis", e.pauseMillis)
        put("valid", e.valid); put("exclusion", e.exclusion ?: JSONObject.NULL)
        put("estimatedOffset", e.estimatedOffset); put("etaMillis", e.etaMillis)
    }.toString()

    fun decode(raw: String, net: MetroNetwork): MetroCapture {
        val o = MetroCodec.boundedObject(raw)
        val fields = setOf("version", "route", "station", "stepIndex", "line", "direction", "service", "kind", "wall", "elapsed", "boot", "clockDomain", "paused", "pauseMillis", "valid", "exclusion", "estimatedOffset", "etaMillis")
        require(o.keys().asSequence().toSet() == fields) { "Campos de medición Metro inválidos." }
        fun long(k: String): Long { val v=o.get(k); require(v is Int || v is Long); return (v as Number).toLong() }
        fun int(k: String): Int { val v=long(k); require(v in Int.MIN_VALUE..Int.MAX_VALUE); return v.toInt() }
        fun text(k: String): String = o.get(k) as? String ?: error("Texto de medición inválido.")
        fun bool(k: String): Boolean = o.get(k) as? Boolean ?: error("Booleano de medición inválido.")
        val version=int("version");require(version in 1..2)
        val journey = MetroCodec.journey(text("route"), net)
        require(journey.sessions.isEmpty()) { "Una captura debe contener solo su recorrido." }
        val e=MetroCapture(journey.plan, text("station"), int("stepIndex"), text("line"), int("direction"), MetroService.valueOf(text("service")), MetroCaptureKind.valueOf(text("kind")),
            MetroTime(long("wall"), long("elapsed"), int("boot")), text("clockDomain"), bool("paused"), long("pauseMillis"), bool("valid"),
            if(o.isNull("exclusion")) null else text("exclusion"), long("estimatedOffset"), long("etaMillis"))
        if(e.kind in setOf(MetroCaptureKind.PAUSE,MetroCaptureKind.RESUME,MetroCaptureKind.PAUSED_LOCATION)) {
            require(version==2 && !e.valid)
            require(e.paused==(e.kind!=MetroCaptureKind.RESUME))
            require(e.exclusion==(if(e.kind==MetroCaptureKind.PAUSED_LOCATION) "PAUSED" else "PAUSE"))
        } else require(version==1)
        require(e.time.wall >= 0 && e.time.elapsed >= 0 && e.time.boot >= 0 && e.pauseMillis >= 0)
        require(java.util.UUID.fromString(e.clockDomain).toString() == e.clockDomain)
        require(e.stepIndex in 0..e.route.steps.size && e.direction in setOf(-1,1))
        val ride = referenceRide(e.route, e.stepIndex)
        if(ride==null) require(e.route.steps.none {it.kind==MetroStepKind.RIDE} && e.exclusion in setOf("NO_RAIL","PAUSE","PAUSED") && e.line in net.accesses(e.station).map {it.id} && e.service==MetroService.NORMAL)
        else require(ride.line == e.line && ride.direction == e.direction && ride.service == e.service)
        val station = e.route.steps.getOrNull(e.stepIndex)?.from ?: e.route.stops.last()
        require(e.station == station)
        require(e.estimatedOffset in 0..e.route.totalMillis && e.etaMillis in 0..e.route.totalMillis)
        require(e.exclusion == null || e.exclusion in captureExclusions)
        require(e.valid == (e.kind != MetroCaptureKind.CORRECTION && !e.paused && e.exclusion == null))
        return e
    }

    fun validate(rows: List<MetroLearningEventEntity>, net: MetroNetwork?) {
        require(rows.isEmpty() || net != null) { "Falta el catálogo de las mediciones Metro." }
        require(rows.map { it.id }.distinct().size == rows.size)
        require(rows.map { it.sessionId to it.sequence }.distinct().size == rows.size)
        rows.groupBy {it.sessionId}.values.forEach {session ->
            require(session.sortedBy {it.sequence}.map {it.sequence} == session.indices.map {it.toLong()}) {"Secuencia de capturas incompleta."}
        }
        rows.forEach { row ->
            require(row.id.isNotBlank() && row.id.length <= 256 && row.sessionId.isNotBlank() && row.sessionId.length <= 256 && row.sequence >= 0)
            require(row.invalidReason == null || row.invalidReason == "UNDONE")
            decode(row.payload, requireNotNull(net))
        }
    }

    internal fun referenceRide(route: MetroRoute, index: Int): MetroStep? =
        route.steps.getOrNull(index-1)?.takeIf {it.kind == MetroStepKind.RIDE}
            ?: route.steps.drop(index).firstOrNull { it.kind == MetroStepKind.RIDE }
            ?: route.steps.take(index).lastOrNull { it.kind == MetroStepKind.RIDE }
    private val captureExclusions = setOf("CORRECTION", "PAUSED", "PAUSE", "NOT_RIDING", "NO_RAIL", "NO_STOP")
}

internal data class MetroObservation(
    val id: String,
    val startId: String,
    val endId: String,
    val context: String,
    val steps: List<MetroStep>,
    val millis: Long,
    val baseMillis: Long,
    val at: Long,
    val exclusion: String? = null,
    val recordedOrder: Long = at,
    val sessionId:String? = null,
) { val direct: Boolean get() = steps.size == 1 }

internal data class MetroLearningEstimate(
    val baseMillis: Long,
    val finalMillis: Long,
    val observedMean: Long?,
    val weight: Double,
    val accepted: List<MetroObservation>,
    val discarded: List<MetroObservation>,
    val method: String = "Promedio filtrado por mediana/MAD, con inercia hacia el estándar",
)

/** Versionable strategy: raw events are the durable source; intervals/estimates are derived. */
internal object MetroLearning {
    const val DEFAULT_WINDOW = 100
    const val MIN_SAMPLES = 5
    fun context(network: String, steps: List<MetroStep>, next:MetroStep?=null): String = network + ":" + steps.joinToString("|") {
        "${it.from}>${it.to}/${it.line}/${it.direction}/${it.service.name}/${it.kind.name}/${it.nextLine}"
    } + if(steps.lastOrNull()?.kind in setOf(MetroStepKind.TRANSFER,MetroStepKind.CHANGE) && next!=null) "/next:${next.line}/${next.direction}/${next.service.name}" else ""

    fun observations(rows: List<MetroLearningEventEntity>, net: MetroNetwork): List<MetroObservation> = buildList {
        val recordedOrder=rows.mapIndexed {index,row->row.id to index.toLong()}.toMap()
        rows.groupBy { it.sessionId }.values.forEach { session ->
            var previous: Pair<MetroLearningEventEntity, MetroCapture>? = null
            var interrupted=false
            session.sortedBy { it.sequence }.forEach { row ->
                val e=MetroLearningCodec.decode(row.payload, net)
                if(e.kind in setOf(MetroCaptureKind.PAUSE,MetroCaptureKind.RESUME)) {
                    interrupted=true
                    return@forEach
                }
                val before=previous
                // Each original adjacent pair occurs exactly once. Corrections/invalidations form barriers.
                if(before != null) {
                    val (aRow,a)=before
                    val path = if(a.route.copy(prediction=null) == e.route.copy(prediction=null) && e.stepIndex > a.stepIndex) e.route.steps.subList(a.stepIndex,e.stepIndex) else emptyList()
                    val change=path.singleOrNull()?.takeIf {it.kind in setOf(MetroStepKind.TRANSFER,MetroStepKind.CHANGE)}
                    val measuredChange=change!=null && change.line==a.line && change.direction==a.direction && change.service==a.service && change.nextLine==e.line
                    val reason=when {
                        aRow.invalidReason != null || row.invalidReason != null -> "UNDONE"
                        interrupted -> "PAUSE"
                        !a.valid || !e.valid -> e.exclusion ?: a.exclusion ?: "INVALID_CAPTURE"
                        !MetroCatalogRevision.forRoute(net,a.route.networkVersion).stops(a.station,a.line,a.service) || !MetroCatalogRevision.forRoute(net,e.route.networkVersion).stops(e.station,e.line,e.service) -> "NO_STOP"
                        a.clockDomain != e.clockDomain -> "RESTORED_CLOCK"
                        a.time.boot != e.time.boot -> "REBOOT"
                        e.time.elapsed <= a.time.elapsed -> "NON_MONOTONIC"
                        a.pauseMillis != e.pauseMillis -> "PAUSE"
                        path.isEmpty() -> "ROUTE_OR_POSITION"
                        !measuredChange && path.any { it.kind != MetroStepKind.RIDE || it.line != a.line || it.direction != a.direction || it.service != a.service } -> "CONTEXT_CHANGE"
                        else -> null
                    }
                    val base=path.sumOf { it.minutes*60_000L }
                    val duration=if(e.time.elapsed > a.time.elapsed) e.time.elapsed-a.time.elapsed else 0
                    add(MetroObservation("${aRow.id}:${row.id}",aRow.id,row.id,context(e.route.networkVersion,path,e.route.steps.getOrNull(e.stepIndex)),path,duration,base,e.time.wall,reason,recordedOrder.getValue(row.id),row.sessionId))
                }
                previous = row to e
                interrupted=false
            }
        }
    }

    fun estimate(network: String, steps: List<MetroStep>, observations: List<MetroObservation>, window: Int = DEFAULT_WINDOW, next:MetroStep?=null): MetroLearningEstimate {
        require(window in 1..10_000)
        val base=steps.sumOf { it.minutes*60_000L }
        val relevant=observations.filter { it.context == context(network,steps,next) }.distinctBy { it.id }.sortedWith(compareBy<MetroObservation> {it.recordedOrder}.thenBy {it.id})
        val usable=relevant.filter { it.exclusion == null }.takeLast(window)
        val discarded=relevant.filter { it.exclusion != null }.toMutableList()
        discarded += relevant.filter {it.exclusion==null && it !in usable}.map {it.copy(exclusion="OUTSIDE_WINDOW")}
        val bounded=usable.filter {it.millis >= maxOf(1L,base/5) && it.millis <= base*5}
        discarded += usable.filter {it !in bounded}.map {it.copy(exclusion="IMPLAUSIBLE_DURATION")}
        fun median(values: List<Long>): Double { val v=values.sorted(); return if(v.size%2==1) v[v.size/2].toDouble() else v[v.size/2-1]/2.0+v[v.size/2]/2.0 }
        val center=if(bounded.isEmpty()) 0.0 else median(bounded.map {it.millis})
        val mad=if(bounded.isEmpty()) 0.0 else median(bounded.map {abs(it.millis-center).toLong()})
        val tolerance=maxOf(base*.25, 3*1.4826*mad)
        val accepted=bounded.filter {bounded.size < MIN_SAMPLES || abs(it.millis-center) <= tolerance}
        discarded += bounded.filter {it !in accepted}.map {it.copy(exclusion="OUTLIER_MAD")}
        val mean=accepted.takeIf {it.isNotEmpty()}?.map {it.millis.toDouble()}?.average()
        val weight=if(accepted.size < MIN_SAMPLES) 0.0 else minOf(1.0,accepted.size/30.0)
        return MetroLearningEstimate(base,(base*(1-weight)+(mean ?: base.toDouble())*weight).toLong(),mean?.toLong(),weight,accepted,discarded)
    }
}
