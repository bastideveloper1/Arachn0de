package com.r0ybt.arachn0de.metro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.PersonAvatar
import com.r0ybt.arachn0de.ui.TaskDatesEditor
import com.r0ybt.arachn0de.ui.TaskDatePickerDraft
import com.r0ybt.arachn0de.ui.state.EditorDraft
import kotlinx.coroutines.*

/** The map, tasks and standalone trips share the same persisted plans and planner. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MetroScreen(nodes: List<Node>, projects: List<Project>, nodeId: String?=null, requestToken: Long=0, onBack: ()->Unit) {
    val context=LocalContext.current
    val app=context.applicationContext as Arachn0deApplication
    val repository=app.metroRepository
    var snapshot by remember { mutableStateOf<MetroSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val people by remember { app.personRepository.observePeople() }.collectAsState(emptyList())
    val assignments by remember { app.personRepository.observeAllAssignments() }.collectAsState(emptyMap())
    val scope=rememberCoroutineScope()
    fun work(action: suspend ()->Unit) { if(!busy) scope.launch {
        busy=true
        try { action() } catch(e: CancellationException) { throw e } catch(e: Exception) { error=e.message ?: "No se pudo guardar. Reintenta." } finally { busy=false }
    } }
    LaunchedEffect(repository) { try { repository.observe().collect { snapshot=it } } catch(e: CancellationException) { throw e } catch(e: Exception) { error="No se pudieron leer los datos Metro: ${e.message}" } }
    var page by rememberSaveable { mutableStateOf("Inicio") }
    var lineId by rememberSaveable { mutableStateOf("L1") }
    var reversed by rememberSaveable { mutableStateOf(false) }
    var full by rememberSaveable { mutableStateOf(false) }
    var origin by rememberSaveable { mutableStateOf<String?>(null) }
    var destination by rememberSaveable { mutableStateOf<String?>(null) }
    var vias by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var express by rememberSaveable { mutableStateOf(false) }
    var traveler by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pick by rememberSaveable { mutableStateOf<String?>(null) }
    var tapped by rememberSaveable { mutableStateOf<String?>(null) }
    var managing by rememberSaveable { mutableStateOf(false) }
    var restrictionAction by remember { mutableStateOf<Pair<String,()->Unit>?>(null) }
    var trackingOptions by rememberSaveable {mutableStateOf(false)}
    var searched by rememberSaveable { mutableStateOf(false) }
    var taskDialog by rememberSaveable { mutableStateOf(false) }
    var candidate by remember { mutableStateOf<MetroRoute?>(null) }
    var candidateActive by remember { mutableStateOf(false) }
    var candidateOld by remember { mutableStateOf<MetroRoute?>(null) }
    var now by remember { mutableStateOf(metroTime(context)) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { while(isActive) { now=metroTime(context); delay(1000) } } }
    val state=snapshot
    val selected=state?.journeys?.firstOrNull { it.row.id==selectedId }
    val active=state?.journeys?.firstOrNull { it.data.active!=null }
    var opened by rememberSaveable(nodeId) { mutableStateOf(false) }
    LaunchedEffect(nodeId,state?.journeys?.map { it.row.id }) {
        if(state!=null && !opened) { opened=true; if(nodeId==null && active!=null) {selectedId=active.row.id;page="Seguimiento"} }
        if(nodeId!=null && selectedId==null) {
            val attached=state?.journeys?.firstOrNull { it.row.nodeId==nodeId }
            if(attached!=null) { selectedId=attached.row.id; page=if(attached.data.active!=null) "Seguimiento" else "Recorrido" }
            else page="Planificar"
        }
    }
    var handledRequest by rememberSaveable {mutableStateOf(0L)}
    LaunchedEffect(requestToken,state) {
        if(requestToken!=0L && handledRequest!=requestToken && state!=null) {
            handledRequest=requestToken
            val requested=if(nodeId!=null) state.journeys.firstOrNull {it.row.nodeId==nodeId} else active
            selectedId=requested?.row?.id
            page=when {requested?.data?.active!=null->"Seguimiento";requested!=null->"Recorrido";nodeId!=null->"Planificar";else->"Inicio"}
        }
    }
    var pendingTracking by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(page,selected?.row?.id,selected?.data?.active,pendingTracking) {
        if(pendingTracking==selected?.row?.id && selected?.data?.active!=null) {
            pendingTracking=null;page="Seguimiento"
        }
        if(page=="Seguimiento" && selected!=null && selected.data.active==null) page="Recorrido"
    }
    LaunchedEffect(state?.preferences?.home) { if(origin==null) origin=state?.preferences?.home }
    BackHandler { if(full) full=false else if(nodeId!=null) onBack() else if(page=="Recorrido") page="Viajes" else if(page!="Inicio") page="Inicio" else onBack() }
    var pendingBegin by rememberSaveable {mutableStateOf<String?>(null)}
    suspend fun startJourney(journey:MetroJourney) {
        val fresh=repository.snapshot()
        check(journey.row.enabled) {"Activa el modo Viaje primero."}
        check(!MetroPlanner.affected(journey.data.plan,fresh.preferences.planningNetwork,fresh.preferences.restrictions)) {"Revisa las restricciones antes de iniciar."}
        check(repository.begin(journey.row.id,journey.row.revision,metroTime(context))) {"El viaje cambió. Reintenta."}
        try {MetroTrackingService.start(context)} catch(e:Exception) {error="Sesión guardada; Android no inició la notificación. Reintenta recuperar notificación: ${e.message}"}
        selectedId=journey.row.id;pendingTracking=journey.row.id
    }
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->
        val id=pendingBegin;pendingBegin=null
        if(!granted) error="Activa las notificaciones de Arachn0de para controlar la pausa en segundo plano. El plan está guardado; el seguimiento no ha empezado."
        else if(id!=null) work {startJourney(repository.snapshot().journeys.first {it.row.id==id})}
    }
    fun needsNotificationPermission()=Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
    fun begin(journey:MetroJourney) {
        if(needsNotificationPermission()) {pendingBegin=journey.row.id;notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)}
        else work {startJourney(journey)}
    }
    fun editJourney(journey:MetroJourney) { selectedId=journey.row.id;origin=journey.data.plan.stops.first();destination=journey.data.plan.stops.last();vias=ArrayList(journey.data.plan.stops.drop(1).dropLast(1));express=journey.data.plan.express;traveler=journey.row.personId;searched=true;page="Planificar" }
    fun recoverArrival(journey:MetroJourney,undo:Boolean) {
        if(undo) work {
            check(repository.undoArrival(journey.row.id,journey.row.revision,metroTime(context))) {"El viaje cambió. Revisa su estado."}
            selectedId=journey.row.id;pendingTracking=journey.row.id;MetroTrackingService.start(context)
        } else pick="corregir-llegada"
    }
    var detailOptions by rememberSaveable {mutableStateOf(false)}
    fun updateSession(journey: MetroJourney, action: (MetroSession)->MetroSession) = work {
        check(repository.tracking(journey.row.id,journey.row.revision,action)) { "La sesión cambió; vuelve a pulsar." }
    }
    if(state==null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) { Text("Metro de Santiago · Beta 1"); Text(error ?: "Cargando red offline…"); TextButton(onClick=onBack) { Text("Volver") } }; return
    }
    val preferences=state.preferences
    val net=remember(preferences.catalog) { preferences.planningNetwork }
    fun name(id: String?)=id?.let { net.stations[it]?.name } ?: "Elegir estación"
    val stops=origin?.let { o->destination?.let { d->listOf(o)+vias+listOf(d) } }
    val route=remember(stops,express,preferences.restrictions) { stops?.let { MetroPlanner.plan(net,it,express,preferences.restrictions) } }
    fun propose(old: MetroRoute, activeSession: Boolean, from: String?=null) {
        val pending=if(activeSession && active?.data?.active!=null) MetroTracking.remainingStops(active.data.active!!,now) else old.stops.drop(1)
        val alternative=MetroPlanner.plan(net,listOf(from ?: old.stops.first())+pending,old.express,preferences.restrictions)
        if(alternative==null) error="No existe otra ruta con estas restricciones y paradas." else { candidate=alternative;candidateOld=old;candidateActive=activeSession }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal=12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={ if(full) full=false else onBack() }) { Text("Volver") }
            Text("Metro de Santiago",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            if(page=="Explorar" || page=="Seguimiento") TextButton(onClick={full=!full}) { Text(if(full) "Reducir" else "Ampliar") }
        }
        if(!full) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf("Inicio","Explorar","Planificar","Viajes").forEach {tab->
                val chosen=page==tab || tab=="Viajes" && page in setOf("Seguimiento","Recorrido")
                Surface(onClick={page=tab;managing=false},modifier=Modifier.weight(1f),shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color=if(chosen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    border=androidx.compose.foundation.BorderStroke(1.dp,if(chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                    Box(Modifier.heightIn(min=48.dp).padding(horizontal=4.dp,vertical=8.dp),contentAlignment=Alignment.Center) {Text(tab,style=MaterialTheme.typography.labelMedium.copy(fontSize=12.sp,letterSpacing=0.sp))}
                }
            }
        }
        when(page) {
            "Inicio" -> LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=8.dp)) {
                item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Tu próximo recorrido",style=MaterialTheme.typography.titleLarge)
                    Text("Planifica y sigue un viaje sin conexión. Posición estimada por tiempo, sin GPS.",style=MaterialTheme.typography.bodyMedium)
                    Button(onClick={page="Planificar"},modifier=Modifier.fillMaxWidth()) {Text("Planificar viaje")}
                    if(active!=null) FilledTonalButton(onClick={selectedId=active.row.id;page="Seguimiento"},modifier=Modifier.fillMaxWidth()) { Text("Abrir seguimiento activo") }
                } } }
                item { OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                    Text("Casa: ${name(preferences.home)}",style=MaterialTheme.typography.titleSmall)
                    Row { OutlinedButton(onClick={pick="casa"}) { Text("Elegir Casa") };Spacer(Modifier.width(6.dp)); if(preferences.home!=null) FilledTonalButton(onClick={destination=preferences.home;searched=false;page="Planificar"}) { Text("Volver a casa") } }
                } } }
                if(preferences.favorites.isNotEmpty()) item {Text("Favoritos",style=MaterialTheme.typography.titleMedium)}
                items(preferences.favorites.sorted()) { id->OutlinedButton(onClick={destination=id;searched=false;page="Planificar"},modifier=Modifier.fillMaxWidth()) { Text("★ ${name(id)}") } }
                item {OutlinedButton(onClick={page="Explorar";managing=false},modifier=Modifier.fillMaxWidth()) {Text("Explorar líneas")}}
                if(state.journeys.isNotEmpty()) item {Text("Guardados y recientes",style=MaterialTheme.typography.titleMedium)}
                items(state.journeys.sortedByDescending {it.data.sessions.lastOrNull()?.start ?: 0}.take(3)) {j->OutlinedCard(Modifier.fillMaxWidth().clickable {selectedId=j.row.id;page="Viajes"}) {Column(Modifier.padding(12.dp)) {RouteSummary(j.data.plan,net);Text("Ver viaje ›",style=MaterialTheme.typography.labelLarge)}}}
                item { HorizontalDivider(); Text("Información y ajustes",style=MaterialTheme.typography.titleSmall);Text("${preferences.restrictions.closed.size+preferences.restrictions.avoided.size+preferences.restrictions.interrupted.size} restricciones locales · No son avisos oficiales.",style=MaterialTheme.typography.bodySmall) }
                item { OutlinedButton(onClick={page="Restricciones"}) { Text("Gestionar restricciones") }; MetroSchedulePanel(net) }
            }
            "Explorar" -> {
                if(!full) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { net.lines.values.forEach { line->
                        FilterChip(selected=lineId==line.id,onClick={lineId=line.id},label={Text(line.id,color=Color(line.color))},border=androidx.compose.foundation.BorderStroke(if(lineId==line.id) 2.dp else 1.dp,Color(line.color)))
                    } }
                }
                val line=net.lines.getValue(lineId)
                OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(10.dp)) {
                    Text("${line.id} · ${line.stations.size} estaciones",color=Color(line.color),style=MaterialTheme.typography.titleMedium)
                    Text("${name(if(reversed) line.stations.last() else line.stations.first())} → ${name(if(reversed) line.stations.first() else line.stations.last())}",style=MaterialTheme.typography.bodySmall)
                    if(!full) Row {OutlinedButton(onClick={reversed=!reversed}) {Text("Invertir dirección")};Spacer(Modifier.width(6.dp));if(managing) FilledTonalButton(onClick={managing=false}) {Text("Salir de gestión")}}
                    if(managing) Text("Administración de restricciones locales",color=MaterialTheme.colorScheme.primary)
                    if(line.express.isNotEmpty()) Text("Roja / Verde: clasificación permanente. Servicio según horario; sin operación en tiempo real.",style=MaterialTheme.typography.labelSmall)
                } }
                val ids=if(reversed) line.stations.reversed() else line.stations
                val rows=ids.mapIndexed {i,id->val next=ids.getOrNull(i+1);MetroTimelineNode(id,lineId,if(next!=null && MetroRestrictions.segment(lineId,id,next) in preferences.restrictions.interrupted) "Tramo siguiente interrumpido" else "")}
                MetroVerticalTimeline(rows,net,Modifier.weight(1f),preferences=preferences,onStation={tapped=it},extra={i->
                    val next=ids.getOrNull(i+1)
                    if(managing && next!=null) OutlinedButton(enabled=!busy,onClick={restrictionAction="¿Cambiar interrupción ${name(ids[i])} ↔ ${name(next)}?" to {work {repository.settings {p->val key=MetroRestrictions.segment(lineId,ids[i],next);p.copy(restrictions=p.restrictions.copy(interrupted=p.restrictions.interrupted.toggle(key)))}}}}) {Text("${if(MetroRestrictions.segment(lineId,ids[i],next) in preferences.restrictions.interrupted) "Reabrir" else "Interrumpir"} tramo",style=MaterialTheme.typography.labelMedium)}
                })
            }
            "Planificar" -> LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                item { TextButton(onClick={selectedId=null;origin=preferences.home;destination=null;vias=arrayListOf();traveler=null;searched=false}) {Text("Nuevo plan")}; if(nodeId!=null) Text("Modo Viaje: ${nodes.firstOrNull { it.id==nodeId }?.title ?: "Tarea"}") }
                if(preferences.favorites.isNotEmpty()) item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {preferences.favorites.sorted().forEach {id->AssistChip(onClick={destination=id;searched=false},label={Text("★ ${name(id)}")})}}}
                item { OutlinedButton(onClick={pick="origen"},Modifier.fillMaxWidth()) { Text("Origen: ${name(origin)}") } }
                item { Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick={val old=origin;origin=destination;destination=old;vias=ArrayList(vias.reversed());searched=false},enabled=origin!=null && destination!=null) {Text("Invertir estaciones")}
                    if(preferences.home!=null) FilledTonalButton(onClick={destination=preferences.home;searched=false}) {Text("Casa")}
                } }
                itemsIndexed(vias) {i,id->
                    OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(horizontal=10.dp,vertical=4.dp)) {
                        OutlinedButton(onClick={pick="via:$i"},modifier=Modifier.fillMaxWidth()) {Text("Parada ${i+1}: ${name(id)}")}
                        Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                            TextButton(onClick={if(i>0) vias=ArrayList(vias.toMutableList().apply {add(i-1,removeAt(i))})},enabled=i>0) {Text("↑")}
                            TextButton(onClick={if(i<vias.lastIndex) vias=ArrayList(vias.toMutableList().apply {add(i+1,removeAt(i))})},enabled=i<vias.lastIndex) {Text("↓")}
                            TextButton(onClick={vias=ArrayList(vias.filterIndexed {j,_->i!=j})}) {Text("Quitar")}
                        }
                    }}
                }
                item { OutlinedButton(onClick={pick="parada"},enabled=vias.size<30) { Text("Agregar parada") } }
                item { OutlinedButton(onClick={pick="destino"},Modifier.fillMaxWidth()) { Text("Destino: ${name(destination)}") } }
                item { Row { FilterChip(selected=!express,onClick={express=false},label={Text("Normal")}); Spacer(Modifier.width(8.dp));FilterChip(selected=express,onClick={express=true},label={Text("Expresa")}) }; Text("Elección manual, independiente del horario.",style=MaterialTheme.typography.labelSmall) }
                item { OutlinedButton(onClick={pick="persona"}) { Text("Persona viajera: ${people.firstOrNull { it.id==traveler }?.name ?: "Sin Persona"}") } }
                item {Button(enabled=stops!=null && !busy,onClick={searched=true},modifier=Modifier.fillMaxWidth()) {Text("Buscar ruta")}}
                if(searched && stops!=null && route==null) item { Text("No existe ruta con estas restricciones.") }
                if(searched && route!=null) {
                    item { RouteSummary(route,net); if(express && !route.usedExpress) Text("Este recorrido no utiliza servicio expreso.") }
                    item { Row { Button(enabled=!busy,onClick={work { selectedId=repository.savePlan(route,nodeId,traveler,selectedId);page="Viajes" }}) { Text(if(nodeId!=null) "Guardar modo Viaje" else "Guardar viaje") }; if(nodeId==null) TextButton(onClick={taskDialog=true}) { Text("Crear tarea") } } }
                    item { Button(enabled=!busy && active==null,onClick={work {
                        selectedId=repository.savePlan(route,nodeId,traveler,selectedId)
                        val saved=repository.snapshot().journeys.first {it.row.id==selectedId}
                        if(needsNotificationPermission()) {pendingBegin=saved.row.id;notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)} else startJourney(saved)
                    }},modifier=Modifier.fillMaxWidth()) {Text("Iniciar viaje")} }
                    item {RouteTimeline(route,net,Modifier.fillMaxWidth().height(440.dp),onStation={tapped=it})}
                }
            }
            "Restricciones" -> LazyColumn(Modifier.weight(1f)) {
                item { Text("Locales y manuales. Cierre: no subir, bajar ni combinar; permite atravesar. Interrupción: no atravesar. Evitar: preferencia, con alternativa si es posible.") }
                items(preferences.restrictions.closed.sorted()) { id->TextButton(onClick={restrictionAction="¿Quitar esta restricción?" to {work { repository.settings { p->p.copy(restrictions=p.restrictions.copy(closed=p.restrictions.closed-id)) } }}}) { Text("Cerrada: ${name(id)} · revertir") } }
                items(preferences.restrictions.avoided.sorted()) { id->TextButton(onClick={restrictionAction="¿Quitar esta restricción?" to {work { repository.settings { p->p.copy(restrictions=p.restrictions.copy(avoided=p.restrictions.avoided-id)) } }}}) { Text("Evitar: ${name(id)} · revertir") } }
                items(preferences.restrictions.interrupted.sorted()) { key->val parts=key.split(':'); TextButton(onClick={restrictionAction="¿Quitar esta restricción?" to {work { repository.settings { p->p.copy(restrictions=p.restrictions.copy(interrupted=p.restrictions.interrupted-key)) } }}}) { Text("${parts[0]} ${name(parts[1])} ↔ ${name(parts[2])} · revertir") } }
                item { OutlinedButton(onClick={page="Explorar";managing=true}) { Text("Agregar desde el mapa") } }
            }
            "Recorrido" -> {
                val journey=selected
                if(journey==null) {Text("El viaje ya no está disponible.");Spacer(Modifier.weight(1f))}
                else {
                    val completed=journey.data.sessions.lastOrNull()?.takeIf {it.ended!=null}
                    val viewed=completed?.route ?: journey.data.plan
                    val routeNet=MetroCatalogRevision.forRoute(net,viewed.networkVersion)
                    RouteSummary(viewed,routeNet)
                    Text(if(completed!=null) "Finalizado" else "Pendiente",style=MaterialTheme.typography.labelLarge)
                    if(completed!=null) {
                        Text("Duración: ${MetroTracking.totalMillis(completed,now)/60_000} min · pausas: ${completed.pausedMillis/60_000} min")
                        Text("Última posición estimada: ${MetroPresentation.positionText(viewed,MetroTracking.position(completed,now),routeNet)}",style=MaterialTheme.typography.bodySmall)
                    }
                    FlowRow {
                        Button(enabled=!busy && journey.row.enabled && (nodes.firstOrNull {it.id==journey.row.nodeId}?.let {it.purpose==NodePurpose.ACTION && !it.hasChildren} ?: true),onClick={begin(journey)}) {Text(if(completed!=null) "Iniciar nuevo seguimiento" else "Comenzar viaje")}
                        TextButton(onClick={editJourney(journey)}) {Text("Editar viaje")}
                    }
                    ArrivalRecovery(journey,busy) {recoverArrival(journey,it)}
                    TextButton(onClick={detailOptions=!detailOptions}) {Text("Opciones del viaje")}
                    if(detailOptions) {
                        Text("Persona: ${people.firstOrNull {it.id==journey.row.personId}?.name ?: "Sin Persona"}")
                        OutlinedButton(enabled=!busy,onClick={work {repository.enabled(journey.row.id,!journey.row.enabled)}}) {Text(if(journey.row.enabled) "Volver a modo normal" else "Activar Viaje")}
                        OutlinedButton(enabled=!busy,onClick={restrictionAction="¿Eliminar datos Metro de este viaje?" to {work {repository.remove(journey.row.id);page="Viajes"}}}) {Text("Eliminar datos Metro")}
                        completed?.let {last->
                            Text("Estimado ${last.originalRoute.minutes} min · real ${MetroTracking.totalMillis(last,now)/60_000} min · ${last.events.count {it.kind in setOf("CONFIRM","BETWEEN","REPLAN")}} correcciones")
                            last.control?.records?.forEachIndexed {i,r->Text("Etapa ${i+1}: ${r.railMillis/1000} s · combinación ${r.transferMillis/1000} s")}
                            if(!last.historyElapsedTrusted || last.startBoot!=last.boot) Text("Duración recuperada con reloj civil; puede ser incierta si cambió la hora.")
                        }
                    }
                    RouteTimeline(viewed,routeNet,Modifier.weight(1f)) {tapped=it}
                }
            }
            "Seguimiento" -> {
                val journey=selected?.takeIf {it.data.active!=null} ?: if(selectedId==null) active else null
                val session=journey?.data?.active
                if(journey==null || session==null) { Text("No hay seguimiento activo."); TextButton(onClick={page="Viajes"}) { Text("Ver viajes") }; Spacer(Modifier.weight(1f)) }
                else {
                    val sessionNet=remember(preferences.catalog,session.route.networkVersion) {MetroCatalogRevision.forRoute(preferences.network,session.route.networkVersion)}
                    val position=MetroTracking.position(session,now)
                    val pending=MetroTracking.remainingStops(session,now)
                    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(10.dp)) {
                        Text("Posición estimada",style=MaterialTheme.typography.labelLarge)
                        Text(MetroPresentation.positionText(session.route,position,sessionNet),style=MaterialTheme.typography.titleMedium)
                        val step=session.route.steps.getOrNull(position.step)
                        if(step!=null) {
                            Text("Última estación: ${name(step.from)} · Próxima: ${name(step.to)}",style=MaterialTheme.typography.bodySmall)
                            Text(MetroPresentation.instruction(session.route,position.step,sessionNet),style=MaterialTheme.typography.bodySmall)
                        }
                        Text("${MetroPresentation.ridesRemaining(session.route,position)} estaciones hasta destino · ${(session.route.minutes*60_000L-position.offset).coerceAtLeast(0)/60_000} min ≈",style=MaterialTheme.typography.labelMedium)
                        MetroPresentation.untilTransfer(session.route,position)?.let {Text("$it estaciones hasta combinación",style=MaterialTheme.typography.labelMedium)}
                        if(pending.size>1) Text("Destino inmediato: ${name(pending.first())}",style=MaterialTheme.typography.bodySmall)
                    } }
                    if(!full) OutlinedButton(onClick={trackingOptions=!trackingOptions}) {Text("Opciones de seguimiento ${if(trackingOptions) "▴" else "▾"}")}
                    if(!full && trackingOptions) {
                        if(pending.size>1) Text("Paradas posteriores: ${pending.drop(1).joinToString { name(it) }}")
                        Text("Real ${MetroTracking.totalMillis(session,now)/60_000} min · pausa ${(session.pausedMillis+MetroTracking.currentPauseMillis(session,now))/60_000} min",style=MaterialTheme.typography.labelSmall)
                        TextButton(onClick={editJourney(journey)}) {Text("Editar viaje")}
                        TextButton(onClick={work { MetroTrackingService.start(context) }}) { Text("Recuperar notificación") }
                        TextButton(onClick={page="Explorar"}) { Text("Explorar otras estaciones / corregir fuera de ruta") }
                        TextButton(onClick={selectedId=journey.row.id;pick="persona-activa"}) { Text("Cambiar Persona viajera") }
                    }
                    Text(when { position.uncertain->"Recuperación incierta: toca una estación y confirma Estoy aquí.";session.pausedAt!=null->"Estimación pausada";position.offset==session.offset && session.confirmed!=null->"Posición confirmada: ${name(session.confirmed)}";else->"Posición estimada: ${name(position.station)}" })
                    if(MetroPlanner.affected(session.route,net,preferences.restrictions)) TextButton(onClick={selectedId=journey.row.id;propose(session.route,true,position.station)}) {Text("Catálogo / restricciones: ¿Quieres buscar otra ruta?")}
                    TrackingMap(session,position,sessionNet,people.firstOrNull { it.id==(session.personId ?: journey.row.personId) },full,Modifier.weight(1f),onStation={tapped=it})
                    val control=MetroStages.control(session)
                    val boundary=session.route.steps.getOrNull(MetroStages.boundary(session))
                    val arrivalContext=when(MetroStages.gateKind(session)) {MetroStepKind.TRANSFER,MetroStepKind.CHANGE->"Llegué a combinación";MetroStepKind.WAYPOINT->"Llegué a parada intermedia";else->"Llegué a destino"}
                    Text(MetroActivity.status(journey,now),style=MaterialTheme.typography.labelLarge)
                    if(control.phase==MetroPhase.RIDING) {
                        Text("$arrivalContext: ${name(boundary?.from ?: session.route.stops.last())}",style=MaterialTheme.typography.labelMedium)
                        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(enabled=!busy,onClick={updateSession(journey) {if(it.pausedAt==null) MetroTracking.pause(it,metroTime(context)) else MetroTracking.resume(it,metroTime(context))}},modifier=Modifier.weight(1f).widthIn(min=140.dp).heightIn(min=48.dp)) {
                                Icon(if(session.pausedAt==null) Icons.Default.Pause else Icons.Default.PlayArrow,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(if(session.pausedAt==null) "Pausar" else "Reanudar")
                            }
                            Button(enabled=!busy,onClick={updateSession(journey) {MetroStages.arrive(it,metroTime(context))}},modifier=Modifier.weight(1f).widthIn(min=140.dp).heightIn(min=48.dp)) {
                                Icon(Icons.Default.LocationOn,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Llegué")
                            }
                        }
                    } else {
                        MetroStages.nextRide(session)?.let {next->Text("${next.line} · Embarque: ${name(next.from)} · Dirección ${MetroPresentation.direction(sessionNet,next.line,next.from,next.to)}")}
                        if(session.pausedAt!=null) FilledTonalButton(enabled=!busy,onClick={updateSession(journey) {MetroTracking.resume(it,metroTime(context))}}) {Text("Reanudar")}
                        else if(control.phase==MetroPhase.ARRIVED && MetroStages.gateKind(session)!=MetroStepKind.WAYPOINT) Button(enabled=!busy,onClick={updateSession(journey) {MetroStages.beginTransfer(it,metroTime(context))}},modifier=Modifier.fillMaxWidth()) {Text("Iniciar combinación")}
                        else {
                            control.transferStarted?.let {Text("Tiempo de combinación: ${MetroStages.duration(it,now,control.clockTrusted)/1000} s")}
                            Button(enabled=!busy,onClick={updateSession(journey) {MetroStages.nextLine(it,metroTime(context))}},modifier=Modifier.fillMaxWidth()) {Text(if(MetroStages.gateKind(session)==MetroStepKind.WAYPOINT) "Continuar siguiente etapa" else "Comenzar siguiente línea")}
                        }
                    }
                    ArrivalRecovery(journey,busy) {recoverArrival(journey,it)}
                }
            }
            else -> LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(state.journeys.isEmpty()) item {OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp)) {Text("Tus viajes",style=MaterialTheme.typography.titleLarge);Text("Guarda un plan para reutilizarlo y consultar sus recorridos.");Button(onClick={page="Planificar"}) {Text("Planificar viaje")}}}}
                items(state.journeys.sortedWith(compareBy<MetroJourney> {it.data.active==null}.thenByDescending {it.data.sessions.lastOrNull()?.start ?: 0}),key={it.row.id}) {journey->
                    val route=journey.data.active?.route ?: journey.data.plan
                    val routeNet=MetroCatalogRevision.forRoute(net,route.networkVersion)
                    Card(Modifier.fillMaxWidth().testTag("metro-trip:${journey.row.id}").clickable {selectedId=journey.row.id;detailOptions=false;page=if(journey.data.active!=null) "Seguimiento" else "Recorrido"}) {
                        Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.DirectionsSubway,"Metro",tint=MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text("${routeNet.stations.getValue(route.stops.first()).name} → ${routeNet.stations.getValue(route.stops.last()).name}",maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                                Text(MetroActivity.summary(journey,net,now),maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if(pick!=null) {
        when(pick) {
            "corregir-llegada" -> StationPicker(net,onDismiss={pick=null},preferences=preferences) {tapped=it;pick=null}
            "persona", "persona-activa" -> {
                val preferred=assignments[nodeId ?: selected?.row?.nodeId ?: active?.row?.nodeId].orEmpty().map { it.id }.toSet()
                AlertDialog(onDismissRequest={pick=null},title={Text("Una Persona viajera")},text={LazyColumn { item { TextButton(onClick={if(pick=="persona-activa") work { repository.traveler(active!!.row.id,null) } else traveler=null;pick=null}) { Text("Sin Persona") } };items(people.sortedBy { if(it.id in preferred) 0 else 1 }) { person->TextButton(onClick={if(pick=="persona-activa") work { repository.traveler(active!!.row.id,person.id) } else traveler=person.id;pick=null}) { TravelerAvatar(person);Spacer(Modifier.width(8.dp));Text(person.name+if(person.id in preferred) " · responsable" else "") } } }},confirmButton={TextButton(onClick={pick=null}) {Text("Cerrar")}})
            }
            else -> StationPicker(net,onDismiss={pick=null},preferences=preferences) { id->when(pick) { "origen"->{origin=id;searched=false};"destino"->{destination=id;searched=false};"parada"->{vias=ArrayList(vias+id);searched=false};"casa"->work { repository.settings { it.copy(home=id) } };else->if(pick?.startsWith("via:")==true) {val index=pick!!.substringAfter(":").toInt();vias=ArrayList(vias.mapIndexed {i,old->if(i==index) id else old});searched=false} };pick=null }
        }
    }
    tapped?.let { id->AlertDialog(onDismissRequest={tapped=null},title={Text(name(id))},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        LineBadges(net,id)
        net.accesses(id).forEach {line->line.express.getOrNull(line.stations.indexOf(id))?.let {MetroClassification(it)}}
        OutlinedButton(onClick={origin=id;searched=false;page="Planificar";tapped=null}) { Text("Usar como origen") }
        OutlinedButton(onClick={destination=id;searched=false;page="Planificar";tapped=null}) { Text("Usar como destino") }
        OutlinedButton(onClick={ if(vias.size<30) vias=ArrayList(vias+id);searched=false;page="Planificar";tapped=null }) { Text("Agregar parada") }
        OutlinedButton(enabled=!busy,onClick={work { repository.settings { it.copy(favorites=it.favorites.toggle(id)) } };tapped=null}) { Text(if(id in preferences.favorites) "Quitar favorito" else "Marcar favorito") }
        OutlinedButton(enabled=!busy,onClick={work { repository.settings { it.copy(home=id) } };tapped=null}) { Text("Asignar Casa") }
        if(managing) {
            OutlinedButton(enabled=!busy,onClick={restrictionAction="¿Cambiar cierre de ${name(id)}?" to {work {repository.settings {p->p.copy(restrictions=p.restrictions.copy(closed=p.restrictions.closed.toggle(id)))}}};tapped=null}) {Text(if(id in preferences.restrictions.closed) "Reabrir para pasajeros" else "Cerrar para pasajeros")}
            OutlinedButton(enabled=!busy,onClick={restrictionAction="¿Cambiar preferencia de evitar ${name(id)}?" to {work {repository.settings {p->p.copy(restrictions=p.restrictions.copy(avoided=p.restrictions.avoided.toggle(id)))}}};tapped=null}) {Text(if(id in preferences.restrictions.avoided) "Dejar de evitar" else "Preferir evitar")}
        }
        val correctionJourney=if(page in setOf("Recorrido","Seguimiento") || nodeId!=null) selected?.takeIf {it.data.active!=null} else active
        correctionJourney?.let { journey->
            OutlinedButton(enabled=!busy,onClick={ val s=journey.data.active!!; if(s.route.stops.first()==id || s.route.steps.any { it.to==id }) updateSession(journey) { MetroTracking.confirm(it,id,metroTime(context)) } else { selectedId=journey.row.id;propose(s.route,true,id) };tapped=null;page="Seguimiento" }) { Text("Estoy aquí") }
            if(journey.data.active!!.route.steps.any { it.from==id && it.kind==MetroStepKind.RIDE }) OutlinedButton(enabled=!busy,onClick={updateSession(journey) { MetroTracking.confirm(it,id,metroTime(context),between=true) };tapped=null;page="Seguimiento"}) { Text("Estoy entre estaciones (desde aquí, aproximado)") }
        }
    }},confirmButton={OutlinedButton(onClick={tapped=null}) {Text("Cerrar")}}) }
    restrictionAction?.let {(title,action)->AlertDialog(onDismissRequest={restrictionAction=null},title={Text(title)},text={Text("Restricción manual de este dispositivo. Afecta la planificación; no es un aviso oficial ni cambia la pausa del seguimiento.")},confirmButton={Button(onClick={restrictionAction=null;action()}) {Text("Confirmar")}},dismissButton={OutlinedButton(onClick={restrictionAction=null}) {Text("Cancelar")}})}
    candidate?.let { proposed->AlertDialog(onDismissRequest={candidate=null},title={Text(if(candidateActive) "Cambiar recorrido activo" else "Otra ruta")},text={LazyColumn { item { Text("Ruta anterior");RouteSummary(candidateOld!!,net);Text("Alternativa desde ${name(proposed.stops.first())}; conserva destinos pendientes");RouteSummary(proposed,net) };itemsIndexed(proposed.steps) {i,_->Text(MetroPresentation.instruction(proposed,i,net),Modifier.padding(vertical=6.dp),style=MaterialTheme.typography.bodyMedium)} }},confirmButton={TextButton(enabled=!busy,onClick={work {
        val current=state.journeys.first { it.row.id==selectedId }
        if(candidateActive) check(repository.tracking(current.row.id,current.row.revision) { MetroTracking.replan(it,proposed,metroTime(context)) }) else repository.savePlan(proposed,current.row.nodeId,current.row.personId,current.row.id)
        candidate=null
    }}) {Text("Confirmar cambio")}},dismissButton={TextButton(onClick={candidate=null}) {Text("Conservar recorrido")}}) }
    if(taskDialog && route!=null) MetroTaskDialog(projects,nodes,onDismiss={taskDialog=false}) { project,parent,draft->work { selectedId=repository.createTask(route,project,parent,draft.title,traveler,draft.priority,draft.startAt,draft.dueAt,draft.description);taskDialog=false;page="Viajes" } }
    error?.let { message->AlertDialog(onDismissRequest={error=null},title={Text("Metro")},text={Text(message)},confirmButton={TextButton(onClick={error=null}) { Text("Entendido") }}) }
}

private fun Set<String>.toggle(id: String)=if(id in this) this-id else this+id
@Composable private fun TravelerAvatar(person: Person?) {
    if(person?.avatarFile!=null) PersonAvatar(person) else Icon(Icons.Default.Person,"Persona viajera",Modifier.size(32.dp))
}
@Composable private fun RouteSummary(route: MetroRoute,net: MetroNetwork) {
    Text(route.stops.joinToString(" → ") { net.stations.getValue(it).name },style=MaterialTheme.typography.titleSmall)
    Text("≈ ${route.minutes} min · ${MetroPresentation.stations(route)} estaciones · ${route.transfers} combinaciones",style=MaterialTheme.typography.bodyMedium)
    Text("${route.physicalSegments} tramos físicos · ${MetroPresentation.stoppingStations(route,net)} estaciones con detención · ${route.steps.map {it.line}.distinct().joinToString(" · ")}",style=MaterialTheme.typography.bodySmall)
}
@Composable private fun RouteTimeline(route:MetroRoute,net:MetroNetwork,modifier:Modifier,onStation:(String)->Unit) {
    val rows=route.steps.mapIndexed {i,s->MetroTimelineNode(s.from,s.line,if(i==0 || route.steps.getOrNull(i-1)?.kind!=MetroStepKind.RIDE || s.kind!=MetroStepKind.RIDE) MetroPresentation.instruction(route,i,net) else if(!net.stops(s.to,s.line,s.service)) "Siguiente estación sin detención" else "",s.kind!=MetroStepKind.RIDE)}+
        MetroTimelineNode(route.stops.last(),route.steps.lastOrNull()?.line ?: net.accesses(route.stops.last()).first().id,"Baja aquí · Destino")
    MetroVerticalTimeline(rows,net,modifier,onStation=onStation)
}
@Composable internal fun StationPicker(net: MetroNetwork,onDismiss: ()->Unit,preferences:MetroPreferences?=null,onSelect: (String)->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable {mutableStateOf<String?>(null)}
    val result=remember(net,query,filter) {MetroSearch.stations(net,query,filter)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Elegir estación")},text={Column {
        OutlinedTextField(query,{query=it},label={Text("Buscar estación")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            FilterChip(selected=filter==null,onClick={filter=null},label={Text("Todas")})
            net.lines.values.forEach {line->FilterChip(selected=filter==line.id,onClick={filter=line.id},label={Text(line.id,color=Color(line.color))})}
        }
        if(preferences!=null && query.isBlank()) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            preferences.home?.let {id->AssistChip(onClick={onSelect(id)},label={Text("Casa")})}
            preferences.favorites.sorted().forEach {id->AssistChip(onClick={onSelect(id)},label={Text("★ ${net.stations.getValue(id).name}")})}
        }
        LazyColumn(Modifier.heightIn(max=400.dp)) {
            items(result,key={it.id}) {station->Column(Modifier.fillMaxWidth().clickable {onSelect(station.id)}.padding(vertical=10.dp)) {
                Text(station.name+(if(preferences?.home==station.id) " · Casa" else "")+(if(station.id in preferences?.favorites.orEmpty()) " ★" else ""),style=MaterialTheme.typography.titleSmall)
                LineBadges(net,station.id);HorizontalDivider(Modifier.padding(top=8.dp))
            } }
            if(result.isEmpty()) item {Text("No se encontraron estaciones.")}
        }
    }},confirmButton={OutlinedButton(onClick=onDismiss) {Text("Cerrar")}})
}
@Composable private fun MetroSchedulePanel(net: MetroNetwork) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick={open=!open}) {Text("Horarios y tipos de servicio ${if(open) "▴" else "▾"}")}
    if(open) Text("Ruta Expresa L2, L4 y L5: días hábiles 06:00–09:00 y 18:00–21:00, con inicio y término gradual. Punta tarifaria referencial: lunes a viernes 07:00–08:59 y 18:00–19:59; festivos excluidos. No cambia automáticamente tu ruta. Sin información de operación en tiempo real.\nFuentes oficiales Metro, Red y tarjeta bip! · revisión ${net.checkedAt} · catálogo ${net.version}.")
}
@Composable private fun TrackingMap(s: MetroSession,p: MetroPosition,net: MetroNetwork,person: Person?,full: Boolean,modifier: Modifier,onStation: (String)->Unit) {
    val list=rememberLazyListState()
    val scope=rememberCoroutineScope()
    var follow by rememberSaveable(s.id) { mutableStateOf(true) }
    LaunchedEffect(list) { list.interactionSource.interactions.collect { if(it is androidx.compose.foundation.interaction.DragInteraction.Start) follow=false } }
    val density=androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(p.step,(p.fraction*10).toInt(),follow,full) {
        if(follow && !list.isScrollInProgress) {
            val item=list.layoutInfo.visibleItemsInfo.firstOrNull {it.index==p.step}
            val viewport=list.layoutInfo.viewportEndOffset-list.layoutInfo.viewportStartOffset
            val inset=with(density) {30.dp.toPx()}
            val y=(item?.offset ?: 0)+inset+(item?.size ?: 0)*p.fraction
            if(item==null || y>viewport-48 || y<24) list.animateScrollToItem(p.step.coerceAtMost(s.route.steps.size),((item?.size ?: 0)*p.fraction+inset-viewport/3).toInt().coerceAtLeast(0))
        }
    }
    val rows=s.route.steps.mapIndexed {i,step->MetroTimelineNode(step.from,step.line,if(i==p.step || i==0 || step.kind!=MetroStepKind.RIDE) MetroPresentation.instruction(s.route,i,net) else "",i==p.step || step.kind!=MetroStepKind.RIDE)}+
        MetroTimelineNode(s.route.stops.last(),s.route.steps.lastOrNull()?.line ?: net.accesses(s.route.stops.last()).first().id,"Destino · Confirma Llegué para finalizar",p.step==s.route.steps.size)
    Column(modifier) {
        OutlinedButton(onClick={follow=true;scope.launch {list.animateScrollToItem(p.step.coerceAtMost(s.route.steps.size))}}) {Text("Centrar avatar")}
        MetroVerticalTimeline(rows,net,Modifier.weight(1f),list=list,progress=p.step to p.fraction,avatar={TravelerAvatar(person)},onStation=onStation)
    }
}
@Composable private fun MetroTaskDialog(projects: List<Project>,nodes: List<Node>,onDismiss: ()->Unit,onCreate: (String,String?,EditorDraft)->Unit) {
    var project by rememberSaveable { mutableStateOf(projects.firstOrNull()?.id) }
    var parent by rememberSaveable { mutableStateOf<String?>(null) }
    val draft=requireNotNull(rememberSaveable(stateSaver=EditorDraft.Saver) { mutableStateOf<EditorDraft?>(EditorDraft(null,null,"Viaje Metro","")) }.value)
    val picker=rememberSaveable(saver=TaskDatePickerDraft.Saver) {TaskDatePickerDraft()}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Crear una tarea Viaje")},text={LazyColumn {
        item {OutlinedTextField(draft.title,{draft.title=it},label={Text("Título")});OutlinedTextField(draft.description,{draft.description=it},label={Text("Descripción")})}
        item {Text("Proyecto")};items(projects) {p->TextButton(onClick={project=p.id;parent=null}) {Text((if(project==p.id) "✓ " else "")+p.name)}}
        item {Text("Capa (opcional)");TextButton(onClick={parent=null}) {Text("Raíz")}}
        items(nodes.filter {it.projectId==project && it.isStructural}) {n->TextButton(onClick={parent=n.id}) {Text((if(parent==n.id) "✓ " else "")+n.title)}}
        item {Text("Prioridad");Row {Priority.entries.forEach {p->TextButton(onClick={draft.priority=p}) {Text((if(draft.priority==p) "✓" else "")+p.name,style=MaterialTheme.typography.labelSmall)}}};TaskDatesEditor(draft,true,picker)}
    }},confirmButton={TextButton(enabled=project!=null && draft.title.isNotBlank(),onClick={onCreate(project!!,parent,draft)}) {Text("Crear")}},dismissButton={TextButton(onClick=onDismiss) {Text("Cancelar")}})
}

@Composable private fun ArrivalRecovery(journey:MetroJourney,busy:Boolean,onRecover:(Boolean)->Unit) {
    journey.data.sessions.lastOrNull()?.control?.undo?.let {undo->
        TextButton(enabled=!busy,onClick={onRecover(undo.reversible)}) {Text(if(undo.reversible) "Deshacer llegada" else "Corregir estación actual")}
    }
}
