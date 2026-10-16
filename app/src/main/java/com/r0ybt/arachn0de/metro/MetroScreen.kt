package com.r0ybt.arachn0de.metro

import androidx.compose.animation.core.animateFloat
import com.r0ybt.arachn0de.ui.ContextActionMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.LocalFireDepartment
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditLocationAlt
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
import kotlinx.coroutines.*

/** The map, tasks and standalone trips share the same persisted plans and planner. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MetroScreen(nodes: List<Node>, projects: List<Project>, nodeId: String?=null, requestToken: Long=0, journeyId:String?=null, onBack: ()->Unit) {
    val context=LocalContext.current
    val app=context.applicationContext as Arachn0deApplication
    val repository=app.metroRepository
    val hoursPreferences=remember(repository) {app.privateContext.getSharedPreferences(MetroHoursConfig.NAME,0)}
    var hours by remember(hoursPreferences) {mutableStateOf(MetroHoursConfig.read(hoursPreferences))}
    var contextMenu by remember {mutableStateOf(false)}
    var tripsExpanded by rememberSaveable {mutableStateOf(false)}
    var preferencesExpanded by rememberSaveable {mutableStateOf(false)}
    var settingsExpanded by rememberSaveable {mutableStateOf(false)}
    var editHours by remember {mutableStateOf(false)}
    DisposableEffect(hoursPreferences) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,_->hours=MetroHoursConfig.read(hoursPreferences)}
        hoursPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {hoursPreferences.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    if(editHours) MetroHoursEditor(hours,onDismiss={editHours=false},onSave={value->
        if(hoursPreferences.edit().putString("hours",value.encode()).commit()) {hours=value;editHours=false;true} else false
    })
    var snapshot by remember { mutableStateOf<MetroSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
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
    var referenceSchedule by rememberSaveable {mutableStateOf(false)}
    var departure by rememberSaveable {mutableLongStateOf(System.currentTimeMillis())}
    var traveler by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pick by rememberSaveable { mutableStateOf<String?>(null) }
    var mapSelection by rememberSaveable {mutableStateOf<String?>(null)}
    var tapped by rememberSaveable { mutableStateOf<String?>(null) }
    var managing by rememberSaveable { mutableStateOf(false) }
    var restrictionAction by remember { mutableStateOf<Pair<String,()->Unit>?>(null) }
    var trackingOptions by rememberSaveable {mutableStateOf(false)}
    var searchPreview by rememberSaveable {mutableStateOf(false)}
    var searched by rememberSaveable { mutableStateOf(false) }
    var returnDraft by rememberSaveable {mutableStateOf(false)}
    var candidate by remember { mutableStateOf<MetroRoute?>(null) }
    var candidateActive by remember { mutableStateOf(false) }
    var destinationDraft by rememberSaveable {mutableStateOf<String?>(null)}
    var destinationStation by rememberSaveable {mutableStateOf<String?>(null)}
    var destinationJourney by remember {mutableStateOf<MetroJourney?>(null)}
    var destinationRoute by remember {mutableStateOf<MetroRoute?>(null)}
    val lineList=rememberLazyListState()
    val planningList=rememberLazyListState()
    var candidateOld by remember { mutableStateOf<MetroRoute?>(null) }
    var now by remember { mutableStateOf(metroTime(context)) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle,page,pick,tapped) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            now=metroTime(context)
            if(page=="Seguimiento" && pick==null && tapped==null) while(isActive) {delay(1000);now=metroTime(context)}
            else if(page in setOf("Planificar","Recorrido")) while(isActive) {delay(30_000);now=metroTime(context)}
        }
    }
    LaunchedEffect(pick,destinationJourney) {
        if(pick in setOf("persona","persona-activa")) pick=null
        if(pick in setOf("nuevo-destino","ubicacion-destino") && destinationJourney==null) {pick=null;destinationDraft=null}
    }
    val state=snapshot
    val selected=state?.journeys?.firstOrNull { it.row.id==selectedId }
    val active=state?.journeys?.firstOrNull { it.data.active!=null }
    var opened by rememberSaveable(nodeId,journeyId) { mutableStateOf(false) }
    LaunchedEffect(nodeId,journeyId,state?.journeys?.map { it.row.id }) {
        if(state!=null && !opened) {
            opened=true
            if(journeyId!=null) {
                val trip=state.journeys.firstOrNull {it.row.id==journeyId}
                selectedId=trip?.row?.id;page=if(trip?.data?.active!=null) "Seguimiento" else "Recorrido"
            } else if(nodeId!=null) {
                val attached=state.journeys.firstOrNull {it.row.nodeId==nodeId}
                selectedId=attached?.row?.id;page=if(attached?.data?.active!=null) "Seguimiento" else if(attached!=null) "Recorrido" else "Planificar"
            } else if(active!=null) {selectedId=active.row.id;page="Seguimiento"}
        }
    }
    var handledRequest by rememberSaveable {mutableStateOf(0L)}
    LaunchedEffect(requestToken,state) {
        if(requestToken!=0L && handledRequest!=requestToken && state!=null) {
            handledRequest=requestToken;searchPreview=false
            val requested=if(journeyId!=null) state.journeys.firstOrNull {it.row.id==journeyId} else if(nodeId!=null) state.journeys.firstOrNull {it.row.nodeId==nodeId} else active
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
    fun navigateBack() {
        if(full) full=false
        else if(page=="Recorrido" && searchPreview) {searchPreview=false;page="Planificar"}
        else if(nodeId!=null || journeyId!=null) onBack()
        else if(page=="Recorrido") page="Viajes"
        else if(page!="Inicio") page="Inicio"
        else onBack()
    }
    LaunchedEffect(page) {if(page=="Recientes") page="Viajes"}
    BackHandler {navigateBack()}
    var pendingBegin by rememberSaveable {mutableStateOf<String?>(null)}
    suspend fun startJourney(journey:MetroJourney) {
        val fresh=repository.snapshot()
        check(!(if(journey.data.plan.departure!=null) MetroPlanner.planAt(fresh.preferences.planningNetwork,journey.data.plan.stops,System.currentTimeMillis(),fresh.preferences.restrictions)?.steps!=journey.data.plan.steps else MetroPlanner.affected(journey.data.plan,fresh.preferences.planningNetwork,fresh.preferences.restrictions))) {"Revisa las restricciones antes de iniciar."}
        check(repository.begin(journey.row.id,journey.row.revision,metroTime(context))) {"El viaje cambió. Reintenta."}
        try {MetroTrackingService.start(context)} catch(e:Exception) {error="Sesión guardada; Android no inició la notificación. Reintenta recuperar notificación: ${e.message}"}
        searchPreview=false;selectedId=journey.row.id;pendingTracking=journey.row.id
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
    fun deleteJourney(journey:MetroJourney) {
        restrictionAction="¿Eliminar este viaje, su historial y su vínculo con la tarea? La tarea se conservará. Esta acción no se puede deshacer." to {work {
            repository.remove(journey.row.id,journey.row.revision)
            if(selectedId==journey.row.id) selectedId=null
            page="Viajes"
        }}
    }
    fun editJourney(journey:MetroJourney) { searchPreview=false;returnDraft=false;selectedId=journey.row.id;origin=journey.data.plan.stops.first();destination=journey.data.plan.stops.last();vias=ArrayList(journey.data.plan.stops.drop(1).dropLast(1));express=journey.data.plan.express;referenceSchedule=journey.data.plan.departure!=null;departure=journey.data.plan.departure ?: System.currentTimeMillis();traveler=journey.row.personId;searched=true;page="Planificar" }
    fun planReturn(journey:MetroJourney) {
        searchPreview=false
        val draft=MetroReturn.draft(journey)
        // A standalone draft: saving it cannot replace the outbound row or task link.
        selectedId=null;departure=System.currentTimeMillis();origin=draft.origin;destination=draft.destination
        vias=arrayListOf();express=draft.express;referenceSchedule=(journey.data.sessions.lastOrNull()?.route ?: journey.data.plan).departure!=null;traveler=draft.personId
        searched=true;full=false;returnDraft=true;page="Planificar"
    }
    fun recoverArrival(journey:MetroJourney,undo:Boolean) {
        if(undo) work {
            check(repository.undoArrival(journey.row.id,journey.row.revision,metroTime(context))) {"El viaje cambió. Revisa su estado."}
            searchPreview=false;selectedId=journey.row.id;pendingTracking=journey.row.id;MetroTrackingService.start(context)
        } else pick="corregir-llegada"
    }
    var detailOptions by rememberSaveable {mutableStateOf(false)}
    fun updateSession(journey: MetroJourney, action: (MetroSession)->MetroSession) = work {
        check(repository.tracking(journey.row.id,journey.row.revision,action)) { "La sesión cambió; vuelve a pulsar." }
    }
    if(state==null) {
        Column(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=10.dp)) {
            com.r0ybt.arachn0de.ui.HeaderBar(onMenuClick=onBack,title="Metro",onBack=onBack)
            Text(error ?: "Cargando red offline…")
        }; return
    }
    val preferences=state.preferences
    val net=remember(preferences.catalog) { preferences.planningNetwork }
    fun name(id: String?)=id?.let { net.stations[it]?.name } ?: "Elegir estación"
    val stops=origin?.let { o->destination?.let { d->listOf(o)+vias+listOf(d) } }
    val scheduledResult=if(referenceSchedule) scheduledMetroPlan(net,stops,departure,preferences.restrictions) else null
    val baseRoute=if(referenceSchedule) scheduledResult!!.route else remember(net,stops,express,preferences.restrictions) {stops?.let {MetroPlanner.plan(net,it,express,preferences.restrictions)}}
    val route=remember(baseRoute,state.observations) {baseRoute?.let {MetroEstimates.adapt(it,state.observations)}}
    fun estimatePlan(plan:MetroRoute)=MetroEstimates.adapt(plan.copy(prediction=null),state.observations)
    fun recordLocation(journey:MetroJourney,station:String,arrival:Boolean=false) {
        val shown=journey.data.active?.let {MetroTracking.position(it,now).offset}
        val eta=journey.data.active?.let {MetroEstimates.remaining(it,now,true)}
        val time=metroTime(context)
        work {
        check(repository.confirmDoors(journey.row.id,journey.row.revision,station,time,arrival,shown,eta)) {"La sesión cambió; vuelve a pulsar."}
        now=time
        }
    }
    fun propose(old: MetroRoute, activeSession: Boolean, from: String?=null) {
        val pending=if(activeSession && active?.data?.active!=null) MetroTracking.remainingStops(active.data.active!!,now) else old.stops.drop(1)
        val alternative=if(old.departure!=null) MetroPlanner.planAt(net,listOf(from ?: old.stops.first())+pending,System.currentTimeMillis(),preferences.restrictions) else MetroPlanner.plan(net,listOf(from ?: old.stops.first())+pending,old.express,preferences.restrictions)
        if(alternative==null) error="No existe otra ruta con estas restricciones y paradas." else { candidate=alternative;candidateOld=old;candidateActive=activeSession }
    }
    fun prepareDestination(journey:MetroJourney,destination:String,station:String) {
        destinationDraft=destination;destinationStation=station;destinationJourney=journey
        work {
            destinationRoute=withContext(Dispatchers.Default) {if(journey.data.active!!.route.departure!=null) MetroPlanner.planAt(net,listOf(station,destination),System.currentTimeMillis(),preferences.restrictions) else MetroPlanner.plan(net,listOf(station,destination),journey.data.active!!.route.express,preferences.restrictions)}
            if(destinationRoute==null) {destinationDraft=null;error="No existe ruta disponible; se conserva el recorrido anterior."}
        }
    }
    fun selectDestination(journey:MetroJourney,destination:String) {
        destinationJourney=journey;destinationDraft=destination
        val station=MetroDestination.location(journey.data.active!!,metroTime(context))
        if(station==null) pick="ubicacion-destino" else prepareDestination(journey,destination,station)
    }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal=12.dp,vertical=10.dp)) {
        com.r0ybt.arachn0de.ui.HeaderBar(onMenuClick=onBack,title="Metro",onBack={navigateBack()},trailingAction={
            Box {
                IconButton(onClick={contextMenu=true},modifier=Modifier.size(48.dp)) {com.r0ybt.arachn0de.ui.LucideIcon(Icons.Default.MoreVert,"Opciones de Metro")}
                DropdownMenu(contextMenu,{contextMenu=false}) {
                    if(page=="Recorrido" && searchPreview && route!=null) DropdownMenuItem(text={Text("Guardar ruta")},enabled=!busy,onClick={
                        contextMenu=false
                        work {
                            selectedId=repository.savePlan(route,if(returnDraft) null else nodeId,traveler,selectedId)
                            searchPreview=false;page="Viajes"
                        }
                    })
                    fun navigate(target:String) {searchPreview=false;page=target;full=false;managing=false;contextMenu=false}
                    DropdownMenuItem(text={Text("Viajes")},trailingIcon={Icon(if(tripsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)},onClick={tripsExpanded=!tripsExpanded})
                    if(tripsExpanded) {
                        DropdownMenuItem(text={Text("Viajar")},onClick={navigate("Planificar")})
                        DropdownMenuItem(text={Text("Viajes guardados")},onClick={navigate("Viajes")})
                    }
                    DropdownMenuItem(text={Text("Preferencias")},trailingIcon={Icon(if(preferencesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)},onClick={preferencesExpanded=!preferencesExpanded})
                    if(preferencesExpanded) {
                        DropdownMenuItem(text={Text("Restricciones de viaje")},onClick={navigate("Restricciones")})
                        DropdownMenuItem(text={Text("Estaciones favoritas")},onClick={navigate("Favoritos")})
                        DropdownMenuItem(text={Text("Configurar vibración")},onClick={navigate("Vibración")})
                    }
                    DropdownMenuItem(text={Text("Configuración")},trailingIcon={Icon(if(settingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)},onClick={settingsExpanded=!settingsExpanded})
                    if(settingsExpanded) DropdownMenuItem(text={Text("Configuración general de Metro")},onClick={navigate("Configuración")})
                }
            }
        })
        if(!full && !(page=="Recorrido" && searchPreview)) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf("Inicio","Líneas").forEach {tab->
                val chosen=page==tab || tab=="Viajes" && page in setOf("Seguimiento","Recorrido")
                Surface(onClick={searchPreview=false;page=tab;managing=false},modifier=Modifier.weight(1f),shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color=if(chosen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    border=androidx.compose.foundation.BorderStroke(1.dp,if(chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                    Box(Modifier.heightIn(min=48.dp).padding(horizontal=4.dp,vertical=8.dp),contentAlignment=Alignment.Center) {Text(tab,style=MaterialTheme.typography.labelMedium.copy(fontSize=12.sp,letterSpacing=0.sp))}
                }
            }
        }
        if(!full && !(page=="Recorrido" && searchPreview)) { Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp)) }
        when(page) {
            "Inicio" -> LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=8.dp)) {
                item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Tu próximo recorrido",style=MaterialTheme.typography.titleLarge)
                    Text("Planifica y sigue un viaje sin conexión. Posición estimada por tiempo, sin GPS.",style=MaterialTheme.typography.bodyMedium)
                    Button(onClick={page="Planificar"},modifier=Modifier.fillMaxWidth()) {Text("Planificar viaje")}
                    if(active!=null) FilledTonalButton(onClick={selectedId=active.row.id;page="Seguimiento"},modifier=Modifier.fillMaxWidth()) { Text("Abrir seguimiento activo") }
                } } }
                if(state.journeys.isNotEmpty()) item {OutlinedButton(onClick={page="Viajes"}) {Text("Ver planes guardados")}}

            }
            "Configuración" -> LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                item {Text("Configuración general de Metro",style=MaterialTheme.typography.titleMedium)}
                item { OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                    Text("Casa: ${name(preferences.home)}",style=MaterialTheme.typography.titleSmall)
                    Row { OutlinedButton(onClick={pick="casa"}) { Text("Elegir Casa") };Spacer(Modifier.width(6.dp)); if(preferences.home!=null) FilledTonalButton(onClick={destination=preferences.home;searched=false;page="Planificar"}) { Text("Volver a casa") } }
                } } }
                item {TextButton(onClick={editHours=true}) {Text("Horarios")}}
            }
            "Favoritos" -> LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                item {Text("Estaciones favoritas",style=MaterialTheme.typography.titleMedium)}
                if(preferences.favorites.isEmpty()) item {Text("No hay estaciones favoritas.")}
                items(preferences.favorites.sorted()) {id->OutlinedButton(onClick={tapped=id},modifier=Modifier.fillMaxWidth()) {Text("★ ${name(id)}")}}
                item {TextButton(onClick={page="Líneas"}) {Text("Consultar estaciones")}}
            }
            "Vibración" -> LazyColumn(Modifier.weight(1f)) {
                item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Text("Vibración previa");Text("Una estación antes de combinación o destino estimados",style=MaterialTheme.typography.bodySmall)}
                    Switch(preferences.vibration,{enabled->work {repository.settings {it.copy(vibration=enabled)}}},enabled=!busy)
                } }
            }
            "Líneas" -> {
                if(!full) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { net.lines.values.forEach { line->
                        FilterChip(selected=lineId==line.id,onClick={lineId=line.id},label={Text(line.id,color=Color(line.color))},border=androidx.compose.foundation.BorderStroke(if(lineId==line.id) 2.dp else 1.dp,Color(line.color)))
                    } }
                }
                val line=net.lines.getValue(lineId)
                if(!full) OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(10.dp)) {
                    Text("${line.id} · ${line.stations.size} estaciones",color=Color(line.color),style=MaterialTheme.typography.titleMedium)
                    Text("${name(if(reversed) line.stations.last() else line.stations.first())} → ${name(if(reversed) line.stations.first() else line.stations.last())}",style=MaterialTheme.typography.bodySmall)
                    if(!full) Row {OutlinedButton(onClick={reversed=!reversed}) {Text("Invertir dirección")};Spacer(Modifier.width(6.dp));if(managing) FilledTonalButton(onClick={managing=false}) {Text("Salir de gestión")}}
                    if(managing) Text("Administración de restricciones locales",color=MaterialTheme.colorScheme.primary)
                    if(line.express.isNotEmpty()) Text("Roja / Verde: clasificación permanente. Servicio según horario; sin operación en tiempo real.",style=MaterialTheme.typography.labelSmall)
                } }
                val ids=if(reversed) line.stations.reversed() else line.stations
                val rows=ids.mapIndexed {i,id->val next=ids.getOrNull(i+1);MetroTimelineNode(id,lineId,if(next!=null && MetroRestrictions.segment(lineId,id,next) in preferences.restrictions.interrupted) "Tramo siguiente interrumpido" else "")}
                MapToolbar(full,onExpand={full=!full})
                MetroVerticalTimeline(rows,net,Modifier.weight(1f),list=lineList,selectedStation=mapSelection,preferences=preferences,onStation={mapSelection=it;tapped=it},extra={i->
                    val next=ids.getOrNull(i+1)
                    if(managing && next!=null) OutlinedButton(enabled=!busy,onClick={restrictionAction="¿Cambiar interrupción ${name(ids[i])} ↔ ${name(next)}?" to {work {repository.settings {p->val key=MetroRestrictions.segment(lineId,ids[i],next);p.copy(restrictions=p.restrictions.copy(interrupted=p.restrictions.interrupted.toggle(key)))}}}}) {Text("${if(MetroRestrictions.segment(lineId,ids[i],next) in preferences.restrictions.interrupted) "Reabrir" else "Interrumpir"} tramo",style=MaterialTheme.typography.labelMedium)}
                })
            }
            "Planificar" -> LazyColumn(Modifier.weight(1f).testTag("metro-planning"),state=planningList,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                item { TextButton(onClick={searchPreview=false;selectedId=null;origin=preferences.home;destination=null;vias=arrayListOf();traveler=null;searched=false;returnDraft=false;referenceSchedule=false}) {Text("Nuevo plan")} }
                if(preferences.favorites.isNotEmpty()) item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {preferences.favorites.sorted().forEach {id->AssistChip(onClick={destination=id;searched=false},label={Text("★ ${name(id)}")})}}}
                if(returnDraft) item { Text("Regreso independiente. No se incluyen las paradas de ida; puedes añadirlas. El viaje de ida y sus vínculos se conservan. Si hay seguimiento activo, cancélalo o confirma la llegada antes de iniciar el regreso.",style=MaterialTheme.typography.bodySmall) }
                item { StationField("Origen",origin?.let {name(it)},pick=="origen",origin in preferences.restrictions.closed) {pick="origen"} }
                item { StationField("Destino",destination?.let {name(it)},pick=="destino",destination in preferences.restrictions.closed) {pick="destino"} }
                item { FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={pick="parada"},enabled=vias.size<30) {Text("+ Agregar parada")}
                } }
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
                item { Row { FilterChip(selected=!referenceSchedule && !express,onClick={referenceSchedule=false;express=false},label={Text("Normal")}); Spacer(Modifier.width(8.dp));FilterChip(selected=!referenceSchedule && express,onClick={referenceSchedule=false;express=true},label={Text("Expresa")}) }; Text("Elección manual, independiente del horario. No identifica el próximo tren.",style=MaterialTheme.typography.labelSmall)
                    FilterChip(selected=referenceSchedule,onClick={referenceSchedule=!referenceSchedule;searched=false},label={Text("Simular horario")})
                    if(referenceSchedule) MetroDepartureField(departure) {departure=it;searched=false}
                    if(referenceSchedule) Text("Referencia histórica, no horario vigente confirmado. Festivos y excepciones sin verificar. No inicia seguimiento.",style=MaterialTheme.typography.bodySmall) }
                item {Button(enabled=stops!=null && !busy && scheduledResult?.ready!=false,onClick={
                    searched=true
                    if(route!=null) {searchPreview=true;full=false;page="Recorrido"}
                },modifier=Modifier.fillMaxWidth()) {Text("Buscar viaje")}}
                if(referenceSchedule && scheduledResult?.ready==false) item {Text("Calculando horario de referencia…")}
                if(searched && stops!=null && route==null && scheduledResult?.ready!=false) item { Text(if(referenceSchedule) "No hay ruta comprobable con este horario de referencia y restricciones. Puede cruzar una franja de servicio; prueba otra hora o revisa el servicio manualmente." else "No existe ruta con estas restricciones.") }
            }
            "Restricciones" -> LazyColumn(Modifier.weight(1f)) {
                item { Text("Locales y manuales. Cierre: no subir, bajar ni combinar; permite atravesar. Interrupción: no atravesar. Evitar: preferencia, con alternativa si es posible.") }
                items(preferences.restrictions.closed.sorted()) { id->TextButton(onClick={restrictionAction="¿Quitar esta restricción?" to {work { repository.settings { p->p.copy(restrictions=p.restrictions.copy(closed=p.restrictions.closed-id)) } }}}) { Text("Cerrada: ${name(id)} · revertir") } }
                items(preferences.restrictions.avoided.sorted()) { id->TextButton(onClick={restrictionAction="¿Quitar esta restricción?" to {work { repository.settings { p->p.copy(restrictions=p.restrictions.copy(avoided=p.restrictions.avoided-id)) } }}}) { Text("Evitar: ${name(id)} · revertir") } }
                items(preferences.restrictions.interrupted.sorted()) { key->val parts=key.split(':'); TextButton(onClick={restrictionAction="¿Quitar esta restricción?" to {work { repository.settings { p->p.copy(restrictions=p.restrictions.copy(interrupted=p.restrictions.interrupted-key)) } }}}) { Text("${parts[0]} ${name(parts[1])} ↔ ${name(parts[2])} · revertir") } }
                item { OutlinedButton(onClick={page="Líneas";managing=true}) { Text("Agregar desde el mapa") } }
            }
            "Recorrido" -> {
                if(searchPreview) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("metro-search-result")) {
                        val areaHeight=maxHeight
                        Column(Modifier.fillMaxSize()) {
                            if(route==null) {
                                Text(if(scheduledResult?.ready==false) "Calculando horario de referencia…" else "El recorrido ya no está disponible con estas opciones o restricciones.")
                                TextButton(onClick={navigateBack()}) {Text("Editar búsqueda")}
                                Spacer(Modifier.weight(1f))
                            } else {
                                if(!full) Column(Modifier.fillMaxWidth().heightIn(max=areaHeight*.25f).verticalScroll(rememberScrollState())) {
                                    RouteSummary(route,net,showTechnical=false,compact=true)
                                    TextButton(onClick={navigateBack()},modifier=Modifier.heightIn(min=48.dp)) {Text("Editar búsqueda")}
                                }
                                MapToolbar(full,onExpand={full=!full})
                                RouteTimeline(route,net,Modifier.weight(1f).fillMaxWidth(),selectedStation=mapSelection,onStation={mapSelection=it;tapped=it})
                                if(!full) Column(Modifier.fillMaxWidth().heightIn(max=areaHeight*.32f).verticalScroll(rememberScrollState()).testTag("metro-result-actions")) {
                                    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                        Button(enabled=!busy && active==null,onClick={work {
                                            selectedId=repository.savePlan(route,if(returnDraft) null else nodeId,traveler,selectedId)
                                            val saved=repository.snapshot().journeys.first {it.row.id==selectedId}
                                            searchPreview=false
                                            if(needsNotificationPermission()) {pendingBegin=saved.row.id;notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)} else startJourney(saved)
                                        }}) {Text("Iniciar viaje")}
                                    }
                                    ActiveJourneyNotice(active,selectedId,nodes,net)
                                    MetroHoursNotice(if(referenceSchedule) departure else now.wall,route.minutes,hours)
                                    if(route.usedExpress || route.departure!=null) Text(MetroSchedule().warning(route,route.departure ?: now.wall),style=MaterialTheme.typography.bodySmall)
                                    if(express && !route.usedExpress) Text("Este recorrido no utiliza servicio expreso.")
                                }
                            }
                        }
                    }
                } else {
                    val journey=selected
                    if(journey==null) {Text("El viaje ya no está disponible.");Spacer(Modifier.weight(1f))}
                    else {
                        val completed=journey.data.sessions.lastOrNull()?.takeIf {it.ended!=null}
                        val viewed=journey.data.active?.route ?: completed?.route ?: estimatePlan(journey.data.plan)
                        val routeNet=MetroCatalogRevision.forRoute(net,viewed.networkVersion)
                        RouteSummary(viewed,routeNet,showTechnical=false)
                        MetroHoursNotice(now.wall,journey.data.plan.minutes,hours)
                        Text(if(completed?.cancelled==true) "Cancelado" else if(completed!=null) "Finalizado" else "Pendiente",style=MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(0.dp),itemVerticalAlignment=Alignment.CenterVertically) {
                            Button(enabled=!busy && active==null,onClick={begin(journey)},
                                contentPadding=PaddingValues(horizontal=10.dp,vertical=6.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),elevation=null,
                                colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.surfaceVariant,contentColor=MaterialTheme.colorScheme.onSurface)) {Text(if(completed!=null) "Iniciar nuevo seguimiento" else "Comenzar viaje")}
                            JourneyMenu(journey,busy,onEdit={editJourney(journey)},onDuplicate={work {selectedId=repository.duplicate(journey.row.id,journey.row.revision);page="Recorrido"}},onDelete={deleteJourney(journey)})
                            if(completed!=null) TextButton(onClick={planReturn(journey)}) {Text("Planificar regreso")}
                        }
                        ActiveJourneyNotice(active,journey.row.id,nodes,net)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(0.dp),itemVerticalAlignment=Alignment.CenterVertically) {
                            ArrivalRecovery(journey,busy) {recoverArrival(journey,it)}
                            TextButton(onClick={detailOptions=!detailOptions}) {Text("Opciones del viaje")}
                        }
                        if(detailOptions) {
                            RouteTechnicalDetails(viewed,routeNet)
                            completed?.let {last->
                                Text("${if(last.cancelled) "Seguimiento interrumpido" else "Duración"}: ${MetroTracking.totalMillis(last,now)/60_000} min · pausas: ${last.pausedMillis/60_000} min",style=MaterialTheme.typography.bodySmall)
                                Text("Última posición estimada: ${MetroPresentation.positionText(viewed,MetroTracking.position(last,now),routeNet)}",style=MaterialTheme.typography.bodySmall)
                                if(!last.cancelled) Text("Estimado ${last.originalRoute.minutes} min · real ${MetroTracking.totalMillis(last,now)/60_000} min · ${last.events.count {it.kind in setOf("CONFIRM","BETWEEN","REPLAN")}} correcciones")
                                last.control?.records?.forEachIndexed {i,r->Text("Etapa ${i+1}: ${r.railMillis/1000} s · combinación ${r.transferMillis/1000} s")}
                                if(!last.historyElapsedTrusted || last.startBoot!=last.boot) Text("Duración recuperada con reloj civil; puede ser incierta si cambió la hora.")
                            }
                        }
                        RouteTimeline(viewed,routeNet,Modifier.weight(1f),selectedStation=mapSelection) {mapSelection=it;tapped=it}
                    }
                }
            }
            "Seguimiento" -> {
                val journey=selected?.takeIf {it.data.active!=null} ?: if(selectedId==null) active else null
                val session=journey?.data?.active
                if(journey==null || session==null) { Text("No hay seguimiento activo."); TextButton(onClick={page="Viajes"}) { Text("Ver viajes") }; Spacer(Modifier.weight(1f)) }
                else {
                    val sessionNet=remember(preferences.catalog,session.route.networkVersion) {MetroCatalogRevision.forRoute(preferences.network,session.route.networkVersion)}
                    val position=MetroTracking.position(session,now)
                    if(!full) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Posición estimada",style=MaterialTheme.typography.labelSmall)
                            Text(MetroPresentation.positionText(session.route,position,sessionNet),style=MaterialTheme.typography.titleSmall)
                            Text(listOfNotNull(session.route.steps.getOrNull(position.step)?.let {"Próxima: ${name(it.to)}"},
                                "Destino: ${name(session.route.stops.last())}").joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                            Text(MetroEstimates.remainingText(session,now,sessionNet),style=MaterialTheme.typography.bodySmall)
                        }
                        MetroActions("Seguimiento",busy,listOf(
                            "Detalle" to {selectedId=journey.row.id;page="Recorrido"},
                            "Cambiar destino" to {destinationJourney=journey;pick="nuevo-destino"},
                            "Estoy aquí" to {selectedId=journey.row.id;pick="estoy-aqui"},
                            "Tiempos" to {trackingOptions=!trackingOptions},
                            "Recuperar notificación" to {work {MetroTrackingService.start(context)}},
                            "Cancelar seguimiento" to {restrictionAction="¿Cancelar seguimiento?" to {updateSession(journey) {MetroTracking.cancel(it,metroTime(context))}}}
                        ))
                    }
                    if(!full && trackingOptions) {
                        Text("Real ${MetroTracking.totalMillis(session,now)/60_000} min · pausas ${(session.pausedMillis+MetroTracking.currentPauseMillis(session,now))/60_000} min. Predicción original ${session.originalRoute.minutes} min · modelo fijo del viaje; no es ubicación GPS.",style=MaterialTheme.typography.bodySmall)
                        MetroStages.control(session).transferStarted?.let {Text("Tiempo de combinación (incluye pausas): ${MetroStages.duration(it,now,MetroStages.control(session).clockTrusted)/1000} s",style=MaterialTheme.typography.bodySmall)}
                    }
                    if(position.uncertain) Text("Posición incierta · confirma Estoy aquí en tu estación real.",style=MaterialTheme.typography.bodySmall)
                    if(MetroPlanner.affected(session.route,net,preferences.restrictions)) TextButton(onClick={selectedId=journey.row.id;propose(session.route,true,position.station)}) {Text("Revisar ruta")}
                    TrackingMap(session,position,sessionNet,full,Modifier.weight(1f),selectedStation=mapSelection,onExpand={full=!full},onStation={mapSelection=it;tapped=it})
                    val control=MetroStages.control(session)
                    val boundary=session.route.steps.getOrNull(MetroStages.boundary(session))
                    val arrivalContext=when(MetroStages.gateKind(session)) {MetroStepKind.TRANSFER,MetroStepKind.CHANGE->"Llegué a combinación";MetroStepKind.WAYPOINT->"Llegué a parada intermedia";else->"Llegué a destino"}
                    Text(MetroActivity.status(journey,now),style=MaterialTheme.typography.labelLarge)
                    if(control.phase==MetroPhase.READY) {
                        session.route.steps.firstOrNull()?.let {next->Text("${next.line} · Embarque: ${name(next.from)} · Dirección ${MetroPresentation.direction(sessionNet,next.line,next.from,next.to)}")}
                        if(session.pausedAt!=null) FilledTonalButton(enabled=!busy,onClick={updateSession(journey) {MetroTracking.resume(it,metroTime(context))}}) {Text("Reanudar")}
                        else Button(enabled=!busy,onClick={updateSession(journey) {MetroStages.nextLine(it,metroTime(context))}}) {Text(if(control.transferStarted!=null) "Iniciar etapa" else "Iniciar etapa")}
                    } else if(control.phase==MetroPhase.RIDING) {
                        Text("$arrivalContext: ${name(boundary?.from ?: session.route.stops.last())}",style=MaterialTheme.typography.labelMedium)
                        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(enabled=!busy,onClick={updateSession(journey) {if(it.pausedAt==null) MetroTracking.pause(it,metroTime(context)) else MetroTracking.resume(it,metroTime(context))}},modifier=Modifier.weight(1f).widthIn(min=140.dp).heightIn(min=48.dp)) {
                                Icon(if(session.pausedAt==null) Icons.Default.Pause else Icons.Default.PlayArrow,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(if(session.pausedAt==null) "Pausar" else "Reanudar")
                            }
                            Button(enabled=!busy,onClick={recordLocation(journey,boundary?.from ?: session.route.stops.last(),arrival=true)},modifier=Modifier.weight(1f).widthIn(min=140.dp).heightIn(min=48.dp)) {
                                Icon(Icons.Default.LocationOn,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Llegué")
                            }
                        }
                    } else {
                        MetroStages.nextRide(session)?.let {next->Text("${next.line} · Embarque: ${name(next.from)} · Dirección ${MetroPresentation.direction(sessionNet,next.line,next.from,next.to)}")}
                        if(session.pausedAt!=null) FilledTonalButton(enabled=!busy,onClick={updateSession(journey) {MetroTracking.resume(it,metroTime(context))}}) {Text("Reanudar")}
                        else if(control.phase==MetroPhase.ARRIVED && MetroStages.gateKind(session)!=MetroStepKind.WAYPOINT) Button(enabled=!busy,onClick={updateSession(journey) {MetroStages.beginTransfer(it,metroTime(context))}},contentPadding=PaddingValues(horizontal=10.dp,vertical=6.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),elevation=null) {Text("Iniciar combinación")}
                        else {
                            Button(enabled=!busy,onClick={updateSession(journey) {MetroStages.nextLine(it,metroTime(context))}},contentPadding=PaddingValues(horizontal=10.dp,vertical=6.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),elevation=null) {Text(if(MetroStages.gateKind(session)==MetroStepKind.WAYPOINT) "Iniciar etapa" else "Iniciar etapa")}
                        }
                    }
                    ArrivalRecovery(journey,busy) {recoverArrival(journey,it)}
                }
            }
            else -> LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(state.journeys.isEmpty()) item {OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp)) {Text("Tus viajes",style=MaterialTheme.typography.titleLarge);Text("Guarda un plan para volver a usarlo.");Button(onClick={page="Planificar"}) {Text("Planificar viaje")}}}}
                items(state.journeys.sortedWith(compareBy<MetroJourney> {it.data.active==null}.thenBy {it.row.id}),key={it.row.id}) {journey->
                    val route=journey.data.active?.route ?: estimatePlan(journey.data.plan)
                    val routeNet=MetroCatalogRevision.forRoute(net,route.networkVersion)
                    Card(Modifier.fillMaxWidth().testTag("metro-trip:${journey.row.id}").clickable {selectedId=journey.row.id;detailOptions=false;page=if(journey.data.active!=null) "Seguimiento" else "Recorrido"},colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)) {
                        Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.DirectionsSubway,"Metro",tint=MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text("${routeNet.stations.getValue(route.stops.first()).name} → ${routeNet.stations.getValue(route.stops.last()).name}",maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                                Text(MetroTripPreview.facts(route),style=MaterialTheme.typography.bodySmall)
                                Text(MetroTripPreview.status(journey),style=MaterialTheme.typography.labelSmall)
                            }
                            JourneyMenu(journey,busy,onEdit={editJourney(journey)},onDuplicate={work {selectedId=repository.duplicate(journey.row.id,journey.row.revision);page="Recorrido"}},onDelete={deleteJourney(journey)})
                        }
                    }
                }
            }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    }
    if(pick!=null) {
        when(pick) {
            "nuevo-destino" -> StationPicker(net,onDismiss={pick=null},preferences=preferences,title="Cambiar destino",contextText="Destino actual: ${name(destinationJourney?.data?.active?.route?.stops?.last())}") {id->pick=null;destinationJourney?.let {selectDestination(it,id)}}
            "ubicacion-destino" -> StationPicker(net,onDismiss={pick=null;destinationDraft=null},preferences=preferences,title="Confirma ubicación actual",contextText="La posición es estimada o incierta. Selecciona tu estación real antes de cambiar el destino.") {id->pick=null;prepareDestination(destinationJourney!!,destinationDraft!!,id)}
            "estoy-aqui", "corregir-posicion" -> StationPicker(net,onDismiss={pick=null},preferences=preferences,title="Estoy aquí",contextText="Selecciona la estación donde se acaban de abrir las puertas.") {station->
                selected?.takeIf {it.data.active!=null}?.let {journey->
                    val current=journey.data.active!!
                    if(station==current.route.stops.first() || current.route.steps.any {it.to==station}) recordLocation(journey,station)
                    else propose(current.route,true,station)
                };pick=null
            }
            "corregir-llegada" -> StationPicker(net,onDismiss={pick=null},preferences=preferences) {tapped=it;pick=null}
            else -> StationPicker(net,onDismiss={pick=null},preferences=preferences) { id->when(pick) { "origen"->{origin=id;searched=false};"destino"->{destination=id;searched=false};"parada"->{vias=ArrayList(vias+id);searched=false};"casa"->work { repository.settings { it.copy(home=id) } };else->if(pick?.startsWith("via:")==true) {val index=pick!!.substringAfter(":").toInt();vias=ArrayList(vias.mapIndexed {i,old->if(i==index) id else old});searched=false} };pick=null }
        }
    }
    tapped?.let { id->
        val correctionJourney=if(page=="Recorrido" && searchPreview) null else if(page in setOf("Recorrido","Seguimiento") || nodeId!=null) selected?.takeIf {it.data.active!=null} else if(page=="Líneas") active else null
        ContextActionMenu(name(id),onDismiss={tapped=null},header={
            LineBadges(net,id)
            net.accesses(id).forEach {line->line.express.getOrNull(line.stations.indexOf(id))?.let {MetroClassification(it)}}
            if(correctionJourney?.data?.active?.pausedAt!=null) Text("«Estoy aquí» corrige la estación y mantiene la pausa.",style=MaterialTheme.typography.bodySmall)
        }) {
            if(correctionJourney!=null) {
                val journey=correctionJourney
                action("Estoy aquí",Icons.Default.LocationOn,{
                    val current=journey.data.active!!
                    if(current.route.stops.first()==id || current.route.steps.any {it.to==id}) recordLocation(journey,id)
                    else {selectedId=journey.row.id;propose(current.route,true,id)}
                    tapped=null;page="Seguimiento"
                },modifier=Modifier.testTag("metro-confirm-location"),enabled=!busy,priority=true)
                action("Cambiar destino",Icons.Default.LocalFireDepartment,{tapped=null;selectDestination(journey,id)},enabled=!busy)
            } else {
                action("Usar como origen",Icons.Default.LocationOn,{origin=id;searched=false;page="Planificar";tapped=null})
                action("Usar como destino",Icons.Default.LocalFireDepartment,{destination=id;searched=false;page="Planificar";tapped=null})
                action("Agregar parada",Icons.Default.Add,{if(vias.size<30) vias=ArrayList(vias+id);searched=false;page="Planificar";tapped=null})
            }
            action(if(id in preferences.favorites) "Quitar favorito" else "Marcar favorito",if(id in preferences.favorites) Icons.Default.Star else Icons.Default.StarBorder,{
                work { repository.settings { it.copy(favorites=it.favorites.toggle(id)) } };tapped=null
            },enabled=!busy)
            action("Asignar Casa",Icons.Default.Bookmark,{work { repository.settings { it.copy(home=id) } };tapped=null},enabled=!busy)
            if(managing) {
                action(if(id in preferences.restrictions.closed) "Reabrir para pasajeros" else "Cerrar para pasajeros",if(id in preferences.restrictions.closed) Icons.Default.Check else Icons.Default.Lock,{
                    restrictionAction="¿Cambiar cierre de ${name(id)}?" to {work {repository.settings {p->p.copy(restrictions=p.restrictions.copy(closed=p.restrictions.closed.toggle(id)))}}};tapped=null
                },enabled=!busy)
                action(if(id in preferences.restrictions.avoided) "Dejar de evitar" else "Preferir evitar",Icons.Default.SwapHoriz,{
                    restrictionAction="¿Cambiar preferencia de evitar ${name(id)}?" to {work {repository.settings {p->p.copy(restrictions=p.restrictions.copy(avoided=p.restrictions.avoided.toggle(id)))}}};tapped=null
                },enabled=!busy)
            }
        }
    }
    destinationRoute?.let {proposed->
        val owner=destinationJourney!!
        AlertDialog(onDismissRequest={destinationRoute=null;destinationDraft=null},title={Text("Confirmar cambio de destino")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Destino actual: ${name(owner.data.active!!.route.stops.last())}")
            Text("Ubicación confirmada para el cambio: ${name(destinationStation)}")
            RouteSummary(proposed,net)
            Text("Se conservan historial y pausas. Confirma el embarque para comenzar la nueva etapa.")
        }},confirmButton={Button(enabled=!busy,onClick={work {
            check(repository.changeDestination(owner.row.id,owner.row.revision,destinationStation!!,destinationDraft!!,metroTime(context),expected=proposed)) {"El viaje cambió; vuelve a seleccionar el destino."}
            selectedId=owner.row.id;pendingTracking=owner.row.id;destinationRoute=null;destinationDraft=null
        }}) {Text("Confirmar cambio")}},dismissButton={TextButton(onClick={destinationRoute=null;destinationDraft=null}) {Text("Cancelar")}})
    }
    restrictionAction?.let {(title,action)->AlertDialog(onDismissRequest={restrictionAction=null},title={Text(title)},text={Text(if(title.startsWith("¿Eliminar este viaje")) "Eliminar borra el registro. Cancelar el seguimiento conserva su historial." else if(title=="¿Cancelar seguimiento?") "Se cancelará este seguimiento sin registrar llegada al destino ni un tiempo de recorrido completo. El plan y las mediciones de estaciones ya confirmadas se conservarán." else "Restricción manual de este dispositivo. Afecta la planificación; no es un aviso oficial ni cambia la pausa del seguimiento.")},confirmButton={Button(onClick={restrictionAction=null;action()}) {Text("Confirmar")}},dismissButton={OutlinedButton(onClick={restrictionAction=null}) {Text("Cancelar")}})}
    candidate?.let { proposed->AlertDialog(onDismissRequest={candidate=null},title={Text(if(candidateActive) "Cambiar recorrido activo" else "Otra ruta")},text={LazyColumn { item { Text("Ruta anterior");RouteSummary(candidateOld!!,net);Text("Alternativa desde ${name(proposed.stops.first())}; conserva destinos pendientes");RouteSummary(proposed,net) };itemsIndexed(proposed.steps) {i,_->Text(MetroPresentation.instruction(proposed,i,net),Modifier.padding(vertical=6.dp),style=MaterialTheme.typography.bodyMedium)} }},confirmButton={TextButton(enabled=!busy,onClick={work {
        val current=state.journeys.first { it.row.id==selectedId }
        if(candidateActive) check(repository.tracking(current.row.id,current.row.revision) { MetroTracking.replan(it,proposed,metroTime(context)) }) else repository.savePlan(proposed,current.row.nodeId,current.row.personId,current.row.id)
        candidate=null
    }}) {Text("Confirmar cambio")}},dismissButton={TextButton(onClick={candidate=null}) {Text("Conservar recorrido")}}) }
    error?.let { message->AlertDialog(onDismissRequest={error=null},title={Text("Metro")},text={Text(message)},confirmButton={TextButton(onClick={error=null}) { Text("Entendido") }}) }
}

private fun Set<String>.toggle(id: String)=if(id in this) this-id else this+id
@Composable private fun RouteSummary(route: MetroRoute,net: MetroNetwork,showTechnical:Boolean=true,compact:Boolean=false) {
    Text(route.stops.joinToString(" → ") { net.stations.getValue(it).name },style=MaterialTheme.typography.titleSmall,maxLines=if(compact) 2 else Int.MAX_VALUE,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    Text("≈ ${route.minutes} min · ${MetroPresentation.stations(route)} estaciones · ${route.transfers} combinaciones",style=MaterialTheme.typography.bodyMedium)
    if(!compact && (route.usedExpress || route.departure!=null)) Text(MetroSchedule().warning(route,route.departure ?: System.currentTimeMillis()),style=MaterialTheme.typography.bodySmall)
    if(showTechnical) RouteTechnicalDetails(route,net)
}
@Composable private fun RouteTechnicalDetails(route:MetroRoute,net:MetroNetwork) {
    Text("${route.physicalSegments} tramos físicos · ${MetroPresentation.stoppingStations(route,net)} estaciones con detención · ${route.steps.map {it.line}.distinct().joinToString(" · ")}",style=MaterialTheme.typography.bodySmall)
}
@Composable private fun RouteTimeline(route:MetroRoute,net:MetroNetwork,modifier:Modifier,selectedStation:String?=null,onStation:(String)->Unit) {
    val rows=route.steps.mapIndexed {i,s->MetroTimelineNode(s.from,s.line,listOf(
        if(i==0 || route.steps.getOrNull(i-1)?.kind!=MetroStepKind.RIDE || s.kind!=MetroStepKind.RIDE) MetroPresentation.instruction(route,i,net) else if(!net.stops(s.to,s.line,s.service)) "Siguiente estación sin detención" else "",
        MetroEstimates.mapEta(route,i)).filter {it.isNotBlank()}.joinToString(" · "),s.kind!=MetroStepKind.RIDE)}+
        MetroTimelineNode(route.stops.last(),route.steps.lastOrNull()?.line ?: net.accesses(route.stops.last()).first().id,"Baja aquí · Destino · ${MetroEstimates.mapEta(route,route.steps.size)}")
    MetroVerticalTimeline(rows,net,modifier,selectedStation=selectedStation,onStation=onStation)
}
@Composable internal fun StationPicker(net: MetroNetwork,onDismiss: ()->Unit,preferences:MetroPreferences?=null,title:String="Elegir estación",contextText:String?=null,onSelect: (String)->Unit) {
    val queryState=rememberTextFieldState()
    val query=queryState.text.toString()
    var filter by rememberSaveable {mutableStateOf<String?>(null)}
    val result=remember(net,query,filter) {MetroSearch.stations(net,query,filter)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Column {
        contextText?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
        OutlinedTextField(state=queryState,label={Text("Buscar estación")},lineLimits=TextFieldLineLimits.SingleLine,modifier=Modifier.fillMaxWidth())
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
    // Informational reference, not a timetable engine. Keep content in the scrollable parent.
    TextButton(onClick={open=!open}) {Text("Horarios y tipos de servicio ${if(open) "▴" else "▾"}")}
    if(open) Text("Ruta Expresa L2, L4 y L5: días hábiles 06:00–09:00 y 18:00–21:00, con inicio y término gradual. Punta tarifaria referencial: lunes a viernes 07:00–08:59 y 18:00–19:59; festivos excluidos. No cambia automáticamente tu ruta. Sin información de operación en tiempo real.\nFuentes oficiales Metro, Red y tarjeta bip! · revisión ${net.checkedAt} · catálogo ${net.version}.")
}
@Composable private fun TrackingMap(s: MetroSession,p: MetroPosition,net: MetroNetwork,full: Boolean,modifier: Modifier,selectedStation:String?,onExpand:()->Unit,onStation: (String)->Unit) {
    val list=rememberLazyListState()
    val scope=rememberCoroutineScope()
    var follow by rememberSaveable(s.id) { mutableStateOf(true) }
    LaunchedEffect(list) { list.interactionSource.interactions.collect { if(it is androidx.compose.foundation.interaction.DragInteraction.Start) follow=false } }
    val density=androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(p.step,(p.fraction*10).toInt(),follow) {
        if(follow && !list.isScrollInProgress) {
            val item=list.layoutInfo.visibleItemsInfo.firstOrNull {it.index==p.step}
            val viewport=list.layoutInfo.viewportEndOffset-list.layoutInfo.viewportStartOffset
            val inset=with(density) {30.dp.toPx()}
            val y=(item?.offset ?: 0)+inset+(item?.size ?: 0)*p.fraction
            if(item==null || y>viewport-48 || y<24) list.animateScrollToItem(p.step.coerceAtMost(s.route.steps.size),((item?.size ?: 0)*p.fraction+inset-viewport/3).toInt().coerceAtLeast(0))
        }
    }
    val rows=s.route.steps.mapIndexed {i,step->MetroTimelineNode(step.from,step.line,listOf(if(i==p.step || i==0 || step.kind!=MetroStepKind.RIDE) MetroPresentation.instruction(s.route,i,net) else "",if(i>=p.step) MetroEstimates.mapEta(s.route,i,p.offset) else "").filter {it.isNotBlank()}.joinToString(" · "),i==p.step || step.kind!=MetroStepKind.RIDE)}+
        MetroTimelineNode(s.route.stops.last(),s.route.steps.lastOrNull()?.line ?: net.accesses(s.route.stops.last()).first().id,"Destino · ${MetroEstimates.mapEta(s.route,s.route.steps.size,p.offset)} · Confirma Llegué para finalizar",p.step==s.route.steps.size)
    Column(modifier) {
        MapToolbar(full,onExpand,onCenter={follow=true;scope.launch {list.animateScrollToItem(p.step.coerceAtMost(s.route.steps.size))}})
        MetroVerticalTimeline(rows,net,Modifier.weight(1f),list=list,selectedStation=selectedStation,progress=p.step to p.fraction,avatar={MetroTrainMarker()},onStation=onStation)
    }
}
@Composable internal fun MetroTrainMarker() {
    val light=androidx.compose.animation.core.rememberInfiniteTransition(label="metro-train-light")
    val tone by light.animateFloat(initialValue=.45f,targetValue=1f,
        animationSpec=androidx.compose.animation.core.infiniteRepeatable(
            animation=androidx.compose.animation.core.tween(900,easing=androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode=androidx.compose.animation.core.RepeatMode.Reverse),label="train-tone")
    Icon(Icons.Default.Train,"Tren · posición estimada",
        Modifier.size(20.dp).background(MaterialTheme.colorScheme.surface,androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).testTag("metro-train"),
        tint=MaterialTheme.colorScheme.primary.copy(alpha=tone))
}

@Composable private fun ArrivalRecovery(journey:MetroJourney,busy:Boolean,onRecover:(Boolean)->Unit) {
    journey.data.sessions.lastOrNull()?.control?.undo?.let {undo->
        TextButton(enabled=!busy,onClick={onRecover(undo.reversible)}) {Text(if(undo.reversible) "Deshacer llegada" else "Corregir estación actual")}
    }
}

@Composable private fun MapToolbar(full:Boolean,onExpand:()->Unit,onCenter:(()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        if(onCenter!=null) IconButton(onClick=onCenter,modifier=Modifier.size(48.dp).testTag("metro-center")) {Icon(Icons.Default.MyLocation,"Centrar tren",Modifier.size(20.dp))}
        Spacer(Modifier.weight(1f))
        IconButton(onClick=onExpand,modifier=Modifier.size(48.dp).testTag("metro-expand")) {Icon(if(full) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,if(full) "Contraer mapa" else "Ampliar mapa")}
    }
}
@Composable internal fun StationField(label:String,station:String?,editing:Boolean=false,invalid:Boolean=false,onClick:()->Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label,style=MaterialTheme.typography.labelLarge)
        OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("metro-field:$label"),
            colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface),
            border=androidx.compose.foundation.BorderStroke(if(editing) 2.dp else 1.dp,if(invalid) MaterialTheme.colorScheme.error else if(station!=null || editing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(if(station==null) Icons.Default.LocationOn else Icons.Default.CheckCircle,null,tint=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("$label: ${station ?: "Elegir estación"}")
                    Text(if(invalid) "Estación cerrada: elige otra" else if(editing) "Modificando" else if(station!=null) "Seleccionada · tocar para modificar" else "Pendiente",style=MaterialTheme.typography.labelSmall)
                }
                Icon(Icons.Default.EditLocationAlt,"Modificar $label")
            }
        }
    }
}

/** Only a different journey prevents starting the consulted trip. */
@Composable internal fun ActiveJourneyNotice(active:MetroJourney?,viewedId:String?,nodes:List<Node>,net:MetroNetwork) {
    if(active==null || active.row.id==viewedId) return
    val route=active.data.active!!.route
    val historical=MetroCatalogRevision.forRoute(net,route.networkVersion)
    val title=nodes.firstOrNull {it.id==active.row.nodeId}?.title
        ?: "${historical.stations.getValue(route.stops.first()).name} → ${historical.stations.getValue(route.stops.last()).name}"
    Column {
        Text("Ya tienes otro viaje en curso: $title. Finalízalo antes de iniciar otro.",style=MaterialTheme.typography.bodySmall)
        Text("Consulta el viaje activo en Viajes.",style=MaterialTheme.typography.bodySmall)
    }
}
