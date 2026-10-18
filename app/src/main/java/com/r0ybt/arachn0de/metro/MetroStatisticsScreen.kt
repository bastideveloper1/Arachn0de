package com.r0ybt.arachn0de.metro

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.HeaderBar
import kotlinx.coroutines.CancellationException

internal data class MetroStatisticsEntry(val route:MetroRoute, val session:MetroSession?=null,
    val latest:MetroCaptureStatus?=null, val label:String="Aprendizaje actual")

/** Read-only projections of original captures and fixed journey predictions. */
internal fun metroStatisticsEntries(state:MetroSnapshot,captures:List<MetroCaptureStatus>):List<MetroStatisticsEntry> = buildList {
    (state.journeys.map {it.data.plan}+captures.map {it.capture.route})
        .map {it.copy(prediction=null)}.distinct().forEach {route->
            add(MetroStatisticsEntry(MetroEstimates.adapt(route,state.observations),
                latest=captures.lastOrNull {it.capture.route.copy(prediction=null)==route}))
        }
    fun sessionEntries(session:MetroSession) {
        (listOf(session.route,session.originalRoute)+session.routeHistory).distinct().forEach {route->
            add(MetroStatisticsEntry(route,session,state.lastCaptures[session.id],"Predicción fija del viaje"))
        }
        session.routeArchives.forEach(::sessionEntries)
        session.control?.undo?.previous?.let(::sessionEntries)
    }
    state.journeys.forEach {it.data.sessions.forEach(::sessionEntries)}
}

/** Read projections only: no new acceptance rules or training writes. */
internal data class MetroLearningContextView(val key:String,val route:MetroRoute,val first:Int,val end:Int,val estimate:MetroLearningEstimate)
internal fun metroLearningContexts(state:MetroSnapshot,captures:List<MetroCaptureStatus>):List<MetroLearningContextView> {
    val contexts=linkedMapOf<String,MetroLearningContextView>()
    val byContext=state.observations.groupBy {it.context}
    val routes=(state.journeys.map {it.data.plan}+captures.map {it.capture.route}).distinct()
    fun add(route:MetroRoute,first:Int,end:Int) {
        val steps=route.steps.subList(first,end)
        if(steps.isEmpty() || steps.any {it.kind==MetroStepKind.WAYPOINT}) return
        val key=MetroLearning.context(route,first,end)
        contexts.getOrPut(key) {MetroLearningContextView(key,route,first,end,MetroLearning.estimate(route,first,end,byContext[key].orEmpty()))}
    }
    routes.forEach {route->
        var first=0
        while(first<route.steps.size) {val end=MetroExpressTiming.intervalEnd(route,first);add(route,first,end);first=end}
    }
    state.observations.filter {it.steps.isNotEmpty()}.distinctBy {it.context}.forEach {observation->
        routes.firstNotNullOfOrNull {route->route.steps.indices.firstOrNull {i->
            i+observation.steps.size<=route.steps.size && route.steps.subList(i,i+observation.steps.size)==observation.steps &&
                MetroLearning.context(route,i,i+observation.steps.size)==observation.context
        }?.let {route to it}}?.let {(route,index)->add(route,index,index+observation.steps.size)}
    }
    return contexts.values.toList()
}

@Composable
internal fun MetroStatisticsScreen(onBack:()->Unit,showHeader:Boolean=true) {
    val app=LocalContext.current.applicationContext as Arachn0deApplication
    val repository=app.metroRepository
    var state by remember(repository) {mutableStateOf<MetroSnapshot?>(null)}
    var captures by remember(repository) {mutableStateOf(emptyList<MetroCaptureStatus>())}
    var events by remember(repository) {mutableStateOf(emptyList<com.r0ybt.arachn0de.data.local.MetroLearningEventEntity>())}
    var error by remember {mutableStateOf<String?>(null)}
    var selected by remember {mutableStateOf<MetroStatisticsEntry?>(null)}
    var section by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf("Resumen")}
    var contextDetails by remember {mutableStateOf<MetroLearningContextView?>(null)}
    BackHandler(onBack=onBack)
    LaunchedEffect(repository) {
        try {
            repository.observe().collect {snapshot->
                val rows=repository.learningEvents()
                captures=rows.map {row->MetroCaptureStatus(MetroLearningCodec.decode(row.payload,snapshot.preferences.network),row.invalidReason)}
                events=rows;state=snapshot
            }
        } catch(e:CancellationException) {throw e}
        catch(e:Exception) {error="No se pudo leer el aprendizaje: ${e.message}"}
    }
    val contexts=remember(state,captures) {state?.let {metroLearningContexts(it,captures)}.orEmpty()}
    val accepted=remember(contexts) {contexts.flatMap {it.estimate.accepted}.distinctBy {it.id}}
    val discarded=remember(contexts,state) {(contexts.flatMap {it.estimate.discarded}+state?.observations.orEmpty().filter {it.exclusion!=null}).distinctBy {it.id}.filter {observation->accepted.none {it.id==observation.id}}}
    val entries=remember(state,captures) {state?.let {metroStatisticsEntries(it,captures)}.orEmpty()}
    val appliedContexts=remember(entries) {entries.filter {it.session==null}.flatMap {entry->
        entry.route.prediction?.parts.orEmpty().filter {it.weight>0}.map {part->
            MetroLearning.context(entry.route,part.first,part.end)
        }
    }.toSet()}
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=10.dp)) {
            if(showHeader) HeaderBar(onMenuClick=onBack,title="Aprendizaje",onBack=onBack)
            LazyColumn(Modifier.weight(1f).testTag("metro-statistics"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                error?.let {item {Text(it)}}
                if(state==null && error==null) item {Text("Cargando aprendizaje…")}
                if(state!=null && entries.isEmpty()) item {Text("Todavía no hay rutas ni mediciones guardadas.")}
                if(state!=null) when(section) {
                    "Resumen"-> {
                        item {LearningCard("¿Está aprendiendo Metro?") {
                            Text(if(contexts.any {it.estimate.weight>0}) "Hay contextos con evidencia suficiente para ajustar nuevas predicciones." else "Todavía no hay contextos con cinco mediciones válidas; se conserva el estándar.")
                            Text("Viajes registrados (planes conservados): ${state!!.journeys.size}")
                            Text("Viajes conservados con mediciones útiles: ${state!!.journeys.count {j->j.data.sessions.any {s->accepted.any {it.sessionId==s.id}}}}")
                            Text("Sesiones con eventos: ${events.map {it.sessionId}.distinct().size}")
                            Text("Sesiones con mediciones válidas actuales: ${accepted.mapNotNull {it.sessionId}.distinct().size}")
                            Text("Mediciones aceptadas: ${accepted.size}")
                            Text("Mediciones descartadas: ${discarded.size}")
                            Text("Contextos seleccionados con aprendizaje en estimaciones actuales: ${appliedContexts.size}")
                            Text("Contextos con ajuste habilitado: ${contexts.count {it.estimate.weight>0}}")
                            Text("Contextos sin evidencia suficiente (estándar o provisional): ${contexts.count {it.estimate.weight==0.0}}")
                        }}
                        item {LearningCard("¿Qué falta para mejorar?") {
                            Text("Confirma puertas abiertas en dos estaciones del mismo recorrido. Empezar un viaje o confirmar solo la llegada no crea por sí solo un intervalo medido.")
                            Text("Se necesitan al menos cinco intervalos fiables del mismo contexto. No confirmes estaciones donde el servicio expreso no se detiene.")
                            Text("Los conteos corresponden a los contextos de rutas/eventos registrados, no a toda la red. Una medición acumulada no equivale a mediciones de cada tramo. Habilitado no implica que ese contexto se haya elegido en todas las rutas.",style=MaterialTheme.typography.bodySmall)
                            Text("Los eventos sobreviven a la eliminación de un viaje; por eso las sesiones con evidencia pueden superar los viajes conservados. Sin eventos históricos no es posible demostrar una contribución.",style=MaterialTheme.typography.bodySmall)
                        }}
                    }
                    "Actividad"-> {
                        val groups=events.zip(captures).groupBy {it.first.sessionId}.entries.sortedByDescending {it.value.maxOf {it.second.capture.time.wall}}
                        item {Text("Actividad reciente · hasta 50 sesiones. Los ajustes corresponden al estado actual; no prueban contribuciones históricas.")}
                        itemsIndexed(groups.take(50),key={_,g->g.key}) {_,group->
                            val last=group.value.maxBy {it.second.capture.time.wall}.second.capture
                            val net=MetroCatalogRevision.forRoute(state!!.preferences.network,last.route.networkVersion)
                            val measurements=state!!.observations.filter {it.sessionId==group.key}.distinctBy {it.id}
                            val useful=accepted.filter {it.sessionId==group.key}
                            val rejected=discarded.filter {it.sessionId==group.key}
                            val byEvent=group.value.associate {it.first.id to it.second.capture}
                            val related=measurements.filter {it.exclusion !in setOf("NO_STOP","NO_RAIL","ROUTE_OR_POSITION","CONTEXT_CHANGE","INVALID_CAPTURE")}
                                .mapNotNull {observation->contexts.firstOrNull {it.key==observation.context}}.distinctBy {it.key}
                            val applied=useful.any {it.context in appliedContexts}
                            LearningCard("${net.stations.getValue(last.route.stops.first()).name} → ${net.stations.getValue(last.route.stops.last()).name}") {
                                Text((if(java.time.Instant.ofEpochMilli(last.time.wall).atZone(java.time.ZoneId.systemDefault()).toLocalDate()==java.time.LocalDate.now()) "Hoy · " else "")+MetroCalculation.clock(last.time.wall))
                                val services=group.value.map {it.second.capture.service}.distinct()
                                Text("Servicio: "+services.joinToString(" / ") {when(it) {MetroService.NORMAL->"NORMAL";MetroService.RED->"ROJO";MetroService.GREEN->"VERDE"}},style=MaterialTheme.typography.labelMedium)
                                Text(when {useful.isNotEmpty()->"Medición registrada";rejected.isNotEmpty()->"Medición descartada";measurements.isNotEmpty()->"Medición registrada";else->"Sin medición"},
                                    style=MaterialTheme.typography.labelLarge,color=if(useful.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Aceptadas: ${useful.size} · Descartadas: ${rejected.size}")
                                if(applied) Text("Aprendizaje aplicado en estimaciones actuales de este contexto",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                                if(measurements.isEmpty()) {
                                    Text("Confirmación en ${net.stations.getValue(last.station).name}; no existe un intervalo entre dos confirmaciones compatibles.",style=MaterialTheme.typography.bodySmall)
                                    val reasons=group.value.mapNotNull {it.first.invalidReason ?: it.second.capture.exclusion}.distinct()
                                    Text(reasons.joinToString(" · ") {MetroCalculation.reason(it)}.ifEmpty {"Falta otra confirmación fiable de puertas abiertas. «Empezar viaje» no mide una apertura."},style=MaterialTheme.typography.bodySmall)
                                    Text("Contexto medido y mediciones faltantes: no determinables sin un intervalo.",style=MaterialTheme.typography.bodySmall)
                                }
                                measurements.forEach {observation->
                                    val start=byEvent[observation.startId];val end=byEvent[observation.endId]
                                    if(start!=null && end!=null) {
                                        val names=MetroCatalogRevision.forRoute(state!!.preferences.network,end.route.networkVersion)
                                        Text("${names.stations.getValue(start.station).name} → ${names.stations.getValue(end.station).name}",style=MaterialTheme.typography.labelMedium)
                                        val comparable=start.clockDomain==end.clockDomain && start.time.boot==end.time.boot && end.time.elapsed>start.time.elapsed
                                        Text(if(comparable) "Tiempo real observado: ${MetroCalculation.time(observation.millis)}" else "Tiempo real no comparable entre estos eventos",style=MaterialTheme.typography.bodySmall)
                                        val rejectedObservation=rejected.firstOrNull {it.id==observation.id}
                                        Text(when {rejectedObservation!=null->"Medición descartada: "+MetroCalculation.reason(rejectedObservation.exclusion ?: "INVALID_CAPTURE");useful.any {it.id==observation.id}->"Medición registrada y aceptada";else->"Medición registrada; aceptación actual no verificable"},style=MaterialTheme.typography.bodySmall)
                                        if(observation.exclusion==null && observation.steps.isNotEmpty() && observation.steps.all {it.kind==MetroStepKind.RIDE && it.line==observation.steps.first().line && it.direction==observation.steps.first().direction && it.service==observation.steps.first().service} && observation.steps.first().service!=MetroService.NORMAL) {
                                            val skipped=observation.steps.dropLast(1).count {!names.stops(it.to,it.line,it.service)}
                                            Text("Estaciones intermedias sin detención según el servicio: $skipped",style=MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                related.forEach {c->
                                    val steps=c.route.steps.subList(c.first,c.end)
                                    val names=MetroCatalogRevision.forRoute(state!!.preferences.network,c.route.networkVersion)
                                    val n=c.estimate.accepted.size
                                    Text("Contexto ${names.stations.getValue(steps.first().from).name} → ${names.stations.getValue(steps.last().to).name}: $n útiles · faltan ${(MetroLearning.MIN_SAMPLES-n).coerceAtLeast(0)} para habilitar el ajuste",style=MaterialTheme.typography.bodySmall)
                                    Text("Ajuste actual del contexto: ${MetroCalculation.delta(c.estimate.finalMillis-c.estimate.baseMillis)}",style=MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick={selected=MetroStatisticsEntry(MetroEstimates.adapt(last.route.copy(prediction=null),state!!.observations),latest=group.value.maxBy {it.second.capture.time.wall}.second)}) {Text("Ver detalles")}
                            }
                        }
                        val withoutEvents=state!!.journeys.filter {j->j.data.sessions.none {s->events.any {it.sessionId==s.id}}}
                        itemsIndexed(withoutEvents,key={_,j->"plan:${j.row.id}"}) {_,journey->LearningCard("Viaje registrado") {
                            Text(journey.data.plan.stops.joinToString(" → ") {state!!.preferences.network.stations.getValue(it).name})
                            Text("Sin medición",style=MaterialTheme.typography.labelLarge)
                            Text("Sin eventos vinculados: no se puede demostrar una medición. Guardar un plan no mide su duración.")
                        }}
                    }
                    "Tramos"-> {
                        item {Text("Un resumen por catálogo, recorrido, línea, sentido y servicio. Los recorridos acumulados se identifican por separado.")}
                        itemsIndexed(contexts,key={_,c->c.key}) {_,c->
                            val steps=c.route.steps.subList(c.first,c.end)
                            val net=MetroCatalogRevision.forRoute(state!!.preferences.network,c.route.networkVersion)
                            LearningCard("${net.stations.getValue(steps.first().from).name} → ${net.stations.getValue(steps.last().to).name}") {
                                Text("${steps.first().line} · ${when(steps.first().service) {MetroService.NORMAL->"NORMAL";MetroService.RED->"ROJO";MetroService.GREEN->"VERDE"}} · sentido ${steps.first().direction} · ${c.route.networkVersion}",style=MaterialTheme.typography.bodySmall)
                                if(c.route.provisional!=null) {
                                    Text("Intervalo entre paradas efectivas",style=MaterialTheme.typography.bodySmall)
                                    Text(MetroExpressTiming.description(c.route),style=MaterialTheme.typography.bodySmall)
                                    val skipped=steps.filter {it.kind==MetroStepKind.RIDE && !net.stops(it.to,it.line,it.service)}.map {net.stations.getValue(it.to).name}
                                    if(skipped.isNotEmpty()) Text("Atravesadas sin detención: ${skipped.joinToString()}",style=MaterialTheme.typography.bodySmall)
                                }
                                if(steps.size>1) Text("Recorrido acumulado: ${steps.size} segmentos; no son mediciones individuales.",style=MaterialTheme.typography.bodySmall)
                                Text("${if(c.route.provisional==null) "Tiempo estándar" else "Base provisional"}: ${MetroCalculation.time(c.estimate.baseMillis)}")
                                Text("${if(c.estimate.weight>0) "Estimación con aprendizaje" else "Estimación sin aprendizaje"}: ${MetroCalculation.time(c.estimate.finalMillis)}")
                                Text("Ajuste: ${MetroCalculation.delta(c.estimate.finalMillis-c.estimate.baseMillis)}")
                                Text("Mediciones válidas: ${c.estimate.accepted.size}${if(c.estimate.weight==0.0) "/5" else ""}")
                                c.estimate.observedMean?.let {Text("Promedio real del intervalo: ${MetroCalculation.time(it)}")}
                                Text(if(c.estimate.weight>0) "Evidencia suficiente; ajuste habilitado" else "Aún sin aprendizaje suficiente")
                                TextButton(onClick={contextDetails=c}) {Text("Ver detalles")}
                            }
                        }
                    }
                    else-> {
                        item {Text("Diagnóstico opcional. Las predicciones fijas son fotografías históricas; sus cifras no describen necesariamente el aprendizaje actual.")}
                        item {LearningCard("Eventos originales") {
                            Text("${events.size} eventos; ${state!!.observations.size} intervalos derivados. Ventana de ${MetroLearning.DEFAULT_WINDOW} observaciones por contexto.")
                            Text("Mediana/MAD filtra valores atípicos. La inercia mantiene parte del tiempo estándar hasta acumular evidencia.")
                        }}
                        itemsIndexed(entries) {_,entry->LearningCard(entry.label) {
                            Text(entry.route.stops.joinToString(" → ") {state!!.preferences.network.stations.getValue(it).name})
                            Text("Estimación adaptada: ${MetroCalculation.time(entry.route.totalMillis)}")
                            TextButton(onClick={selected=entry}) {Text("Ver cálculo")}
                        }}
                        itemsIndexed(events.zip(captures).takeLast(100).reversed(),key={_,pair->"event:${pair.first.id}"}) {_,pair->LearningCard("Evento · ${pair.second.capture.kind.name}") {
                            Text("${MetroCalculation.clock(pair.second.capture.time.wall)} · ${pair.second.capture.station}")
                            Text((pair.first.invalidReason ?: pair.second.capture.exclusion)?.let(MetroCalculation::reason) ?: "Confirmación válida; necesita otra confirmación compatible para medir.")
                        }}
                    }
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth()) {listOf("Resumen","Actividad","Tramos","Detalles").forEach {label->
                TextButton(onClick={section=label},modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("metro-learning:$label")) {Text(label,style=MaterialTheme.typography.labelSmall)}
            }}
        }
    }
    contextDetails?.let {c->
        val evidence=c.estimate
        val lines=buildList {
            add(evidence.method);add("Ventana: ${MetroLearning.DEFAULT_WINDOW} · peso: ${(evidence.weight*100).toInt()} %")
            add("Promedio observado: ${evidence.observedMean?.let(MetroCalculation::time) ?: "sin mediciones"}")
            evidence.discarded.groupingBy {it.exclusion.orEmpty()}.eachCount().forEach {(reason,count)->add("${MetroCalculation.reason(reason)}: $count")}
            (evidence.accepted+evidence.discarded).sortedByDescending {it.recordedOrder}.forEach {add("${MetroCalculation.clock(it.at)} · ${MetroCalculation.time(it.millis)} · ${it.exclusion?.let(MetroCalculation::reason) ?: "Aceptada"} · ${it.startId} → ${it.endId}")}
        }
        MetroCalculationDialog(lines) {contextDetails=null}
    }
    selected?.let {entry->state?.let {snapshot->
        MetroCalculationDialog(MetroCalculation.lines(entry.route,snapshot.observations,
            MetroCatalogRevision.forRoute(snapshot.preferences.network,entry.route.networkVersion),entry.session,entry.latest)) {selected=null}
    }}
}

@Composable private fun LearningCard(title:String,content:@Composable ColumnScope.()->Unit) {
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(title,style=MaterialTheme.typography.titleSmall);content()
    }}
}
