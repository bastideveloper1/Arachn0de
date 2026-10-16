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

@Composable
internal fun MetroStatisticsScreen(onBack:()->Unit) {
    val app=LocalContext.current.applicationContext as Arachn0deApplication
    val repository=app.metroRepository
    var state by remember(repository) {mutableStateOf<MetroSnapshot?>(null)}
    var captures by remember(repository) {mutableStateOf(emptyList<MetroCaptureStatus>())}
    var error by remember {mutableStateOf<String?>(null)}
    var selected by remember {mutableStateOf<MetroStatisticsEntry?>(null)}
    BackHandler(onBack=onBack)
    LaunchedEffect(repository) {
        try {
            repository.observe().collect {snapshot->
                captures=repository.learningEvents().map {row->
                    MetroCaptureStatus(MetroLearningCodec.decode(row.payload,snapshot.preferences.network),row.invalidReason)
                }
                state=snapshot
            }
        } catch(e:CancellationException) {throw e}
        catch(e:Exception) {error="No se pudieron leer las estadísticas: ${e.message}"}
    }
    val entries=remember(state,captures) {state?.let {metroStatisticsEntries(it,captures)}.orEmpty()}
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=10.dp)) {
            HeaderBar(onMenuClick=onBack,title="Estadísticas de Metro",onBack=onBack)
            LazyColumn(Modifier.weight(1f).testTag("metro-statistics"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                error?.let {item {Text(it)}}
                if(state==null && error==null) item {Text("Cargando estadísticas…")}
                if(state!=null && entries.isEmpty()) item {Text("Todavía no hay rutas ni mediciones guardadas.")}
                itemsIndexed(entries) {_,entry->
                    val net=MetroCatalogRevision.forRoute(state!!.preferences.network,entry.route.networkVersion)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(entry.label,style=MaterialTheme.typography.labelLarge)
                            Text(entry.route.stops.joinToString(" → ") {net.stations.getValue(it).name})
                            Text("Tiempo estándar: ${MetroCalculation.time(entry.route.baseMillis)}",style=MaterialTheme.typography.bodySmall)
                            Text("Estimación adaptada: ${MetroCalculation.time(entry.route.totalMillis)}",style=MaterialTheme.typography.bodySmall)
                            Text("Ajuste aplicado: ${MetroCalculation.delta(entry.route.totalMillis-entry.route.baseMillis)}",style=MaterialTheme.typography.bodySmall)
                            TextButton(onClick={selected=entry},modifier=Modifier.heightIn(min=48.dp)) {Text("Ver cálculo")}
                        }
                    }
                }
            }
        }
    }
    selected?.let {entry->state?.let {snapshot->
        val session=entry.session?.let {old->snapshot.journeys.flatMap {it.data.sessions}.firstOrNull {it.id==old.id} ?: old}
        MetroCalculationDialog(MetroCalculation.lines(entry.route,snapshot.observations,
            MetroCatalogRevision.forRoute(snapshot.preferences.network,entry.route.networkVersion),session,
            session?.let {snapshot.lastCaptures[it.id]} ?: entry.latest)) {selected=null}
    }}
}
