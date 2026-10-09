package com.r0ybt.arachn0de.metro

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.ui.state.EditorDraft

/** Edits the existing task draft; the normal save transaction owns task/container creation.
 * A template contributes only a plan. Sessions and histories never enter the draft. */
@Composable internal fun MetroDraftFields(draft: EditorDraft, enabled: Boolean, onValidity: (Boolean)->Unit) {
    val app=LocalContext.current.applicationContext as? Arachn0deApplication ?: return
    var snapshot by remember {mutableStateOf<MetroSnapshot?>(null)}
    LaunchedEffect(app) {app.metroRepository.observe().collect {snapshot=it}}
    var adding by rememberSaveable(draft.creationId) {mutableStateOf(false)}
    var chooseExisting by rememberSaveable {mutableStateOf(false)}
    var reload by remember {mutableStateOf<MetroJourney?>(null)}
    val isTemplate=draft.id?.startsWith("template:")==true
    val existing=snapshot?.journeys?.firstOrNull {draft.id!=null && it.row.nodeId==draft.id}
    fun select(journey:MetroJourney) {
        val preferences=requireNotNull(snapshot).preferences
        val plan=MetroCodec.journey(MetroJourneyData(journey.data.plan))
        draft.metroJourneyId=journey.row.id;draft.metroJourneyRevision=journey.row.revision
        draft.metroOriginalPlan=plan;draft.metroOriginalTraveler=journey.row.personId
        draft.templateMetroCatalog=preferences.catalog;draft.templateMetroPlan=plan;draft.templateTraveler=journey.row.personId
        draft.metroEditorRevision++;adding=true;chooseExisting=false
    }
    LaunchedEffect(existing?.row?.id) {
        if(existing!=null && !isTemplate && draft.metroJourneyId==null && draft.templateMetroPlan==null) select(existing)
    }
    val eligible=draft.id==null || isTemplate || draft.purpose==NodePurpose.ACTION
    if(!eligible) return
    val hasPlan=draft.templateMetroPlan!=null
    val metro=hasPlan || adding || draft.templateMetroCatalog!=null || draft.metroJourneyId!=null
    Text("Contenido",style=MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        FilterChip(selected=!metro,enabled=enabled && existing==null,onClick={
            draft.templateMetroPlan=null;draft.templateMetroCatalog=null;draft.templateTraveler=null
            draft.metroJourneyId=null;draft.metroJourneyRevision=null;draft.metroOriginalPlan=null;draft.metroOriginalTraveler=null
            adding=false;onValidity(true)
        },label={Text("Habitual")})
        FilterChip(selected=metro,enabled=enabled,onClick={
            if(draft.id==null || isTemplate) draft.purpose=NodePurpose.ACTION
            adding=true
        },label={Text("Viaje Metro")})
    }
    if(existing!=null && draft.metroJourneyId==null) {LaunchedEffect(Unit) {onValidity(false)};Text("Cargando itinerario…");return}
    val selected=snapshot?.journeys?.firstOrNull {it.row.id==draft.metroJourneyId}
    if(draft.metroJourneyId!=null && (selected==null && snapshot!=null || selected?.row?.revision!=draft.metroJourneyRevision)) {
        Text("El viaje seleccionado cambió o dejó de estar disponible. Revisa antes de guardar.",color=MaterialTheme.colorScheme.error)
        if(selected!=null) OutlinedButton(enabled=enabled,onClick={reload=selected}) {Text("Recargar viaje")}
    }
    reload?.let {journey->AlertDialog(onDismissRequest={reload=null},title={Text("¿Recargar configuración del viaje?")},text={Text("Se reemplazarán únicamente los campos Metro del borrador con el estado actual. Los demás campos se conservan.")},confirmButton={Button(onClick={select(journey);reload=null}) {Text("Recargar")}},dismissButton={TextButton(onClick={reload=null}) {Text("Cancelar")}})}
    if(draft.purpose!=NodePurpose.ACTION) {
        if(metro) {
            Text("El plan Metro requiere tipo Tarea.",color=MaterialTheme.colorScheme.error)
            LaunchedEffect(Unit) {onValidity(false)}
        } else LaunchedEffect(Unit) {onValidity(true)}
        return
    }
    val p=snapshot?.preferences
    val catalog=draft.templateMetroCatalog ?: p?.catalog
    if(!metro) {LaunchedEffect(Unit) {onValidity(true)};return}
    if(existing==null && !isTemplate) {
        OutlinedButton(enabled=enabled && snapshot!=null,onClick={chooseExisting=true}) {Text("Elegir viaje existente")}
        if(draft.metroJourneyId!=null) OutlinedButton(enabled=enabled,onClick={
            draft.metroJourneyId=null;draft.metroJourneyRevision=null;draft.metroOriginalPlan=null;draft.metroOriginalTraveler=null
            draft.metroEditorRevision++;adding=true
        }) {Text("Crear un viaje nuevo con este recorrido")}
    }
    if(chooseExisting) AlertDialog(onDismissRequest={chooseExisting=false},title={Text("Elegir viaje existente")},text={Column {
        Text("Vincula un viaje independiente sin duplicarlo. Los viajes de otras tarjetas conservan su vínculo.",style=MaterialTheme.typography.bodySmall)
        val available=snapshot?.journeys.orEmpty().filter {it.row.nodeId==null}
        androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max=360.dp)) {
            items(available.size) {i->val journey=available[i]
                OutlinedButton(onClick={select(journey)},modifier=Modifier.fillMaxWidth()) {Column {
                    Text(journey.data.plan.stops.joinToString(" → ") {snapshot!!.preferences.network.stations.getValue(it).name})
                    Text(if(journey.data.active!=null) "Seguimiento activo · se conserva" else if(journey.row.enabled) "Viaje independiente" else "Modo desactivado · se conserva",style=MaterialTheme.typography.labelSmall)
                }}
            }
            if(available.isEmpty()) item {Text("No hay viajes independientes disponibles. Puedes configurar uno nuevo aquí.")}
        }
    }},confirmButton={TextButton(onClick={chooseExisting=false}) {Text("Cerrar")}})
    if(catalog==null) {LaunchedEffect(Unit) {onValidity(false)};Text("Cargando Metro…");return}
    // Re-enter this keyed editor when Guardados replaces a draft plan.
    key(draft.creationId,catalog,draft.metroEditorRevision) {
        val source=remember(catalog) {MetroNetwork.decode(catalog)}
        val net=remember(source) {MetroCatalogRevision.forPlanning(source)}
        val initial=remember {draft.templateMetroPlan?.let {MetroCodec.journey(it,source).plan}}
        var origin by rememberSaveable {mutableStateOf(initial?.stops?.first() ?: p?.home)}
        var destination by rememberSaveable {mutableStateOf(initial?.stops?.last())}
        var vias by rememberSaveable {mutableStateOf(initial?.stops?.drop(1)?.dropLast(1).orEmpty())}
        var express by rememberSaveable {mutableStateOf(initial?.express ?: false)}
        var scheduled by rememberSaveable {mutableStateOf(initial?.departure!=null)}
        var departure by rememberSaveable {mutableLongStateOf(initial?.departure ?: System.currentTimeMillis())}
        var routeChanged by rememberSaveable {mutableStateOf(false)}
        var picker by rememberSaveable {mutableStateOf<Int?>(null)}
        var travelerPicker by rememberSaveable {mutableStateOf(false)}
        val restrictions=p?.restrictions ?: MetroRestrictions()
        val stops=if(origin!=null && destination!=null) listOf(origin!!)+vias+destination!! else null
        val route=if(!routeChanged && draft.metroJourneyId!=null && initial!=null) initial
            else if(scheduled) scheduledMetroPlan(net,stops,departure,restrictions).route
            else remember(stops,express,restrictions) {stops?.let {MetroPlanner.plan(net,it,express,restrictions)}}
        val linkValid=draft.metroJourneyId==null || selected!=null && selected.row.revision==draft.metroJourneyRevision && (selected.row.nodeId==null || selected.row.nodeId==draft.id)
        LaunchedEffect(route,linkValid) {
            onValidity(route!=null && linkValid)
            draft.templateMetroCatalog=catalog
            draft.templateMetroPlan=route?.let {MetroCodec.journey(MetroJourneyData(it))}
        }
        OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("Viaje Metro",style=MaterialTheme.typography.titleMedium)
            Text("Se guardará en el contenedor de esta tarea.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled=enabled,onClick={picker=0},modifier=Modifier.fillMaxWidth()) {Text("Origen: ${origin?.let {net.stations.getValue(it).name} ?: "Elegir estación"}")}
            vias.forEachIndexed {i,id->Column {
                OutlinedButton(enabled=enabled,onClick={picker=i+1},modifier=Modifier.fillMaxWidth()) {Text("Vía: ${net.stations.getValue(id).name}")}
                Row {
                    TextButton(enabled=enabled && i>0,onClick={routeChanged=true;vias=vias.toMutableList().apply {add(i-1,removeAt(i))}}) {Text("↑")}
                    TextButton(enabled=enabled && i<vias.lastIndex,onClick={routeChanged=true;vias=vias.toMutableList().apply {add(i+1,removeAt(i))}}) {Text("↓")}
                    TextButton(enabled=enabled,onClick={routeChanged=true;vias=vias.filterIndexed {j,_->i!=j}}) {Text("Quitar parada")}
                }
            } }
            OutlinedButton(enabled=enabled,onClick={picker=-1},modifier=Modifier.fillMaxWidth()) {Text("Destino: ${destination?.let {net.stations.getValue(it).name} ?: "Elegir estación"}")}
            Row {
                OutlinedButton(enabled=enabled && vias.size<30,onClick={picker=-2}) {Text("Añadir parada")}
                Spacer(Modifier.width(6.dp))
                OutlinedButton(enabled=enabled && origin!=null && destination!=null,onClick={routeChanged=true;val old=origin;origin=destination;destination=old;vias=vias.reversed()}) {Text("Invertir")}
            }
            Row {FilterChip(selected=!scheduled && !express,enabled=enabled,onClick={routeChanged=true;scheduled=false;express=false},label={Text("Normal")});Spacer(Modifier.width(6.dp));FilterChip(selected=!scheduled && express,enabled=enabled,onClick={routeChanged=true;scheduled=false;express=true},label={Text("Expresa")})}
            Text("Servicio elegido manualmente; verifica el horario.",style=MaterialTheme.typography.labelSmall)
            FilterChip(selected=scheduled,enabled=enabled,onClick={scheduled=!scheduled;routeChanged=true},label={Text("Simular horario de referencia")})
            if(scheduled) MetroDepartureField(departure) {departure=it;routeChanged=true}
            if(scheduled) Text("Referencia histórica; festivos, vigencia y excepciones no confirmados.",style=MaterialTheme.typography.bodySmall)
            val people by remember(app) {app.personRepository.observePeople()}.collectAsState(emptyList())
            OutlinedButton(enabled=enabled,onClick={travelerPicker=true}) {Text("Persona viajera: ${people.firstOrNull {it.id==draft.templateTraveler}?.name ?: "Sin Persona"}")}
            if(travelerPicker) AlertDialog(onDismissRequest={travelerPicker=false},title={Text("Persona viajera")},text={androidx.compose.foundation.lazy.LazyColumn {
                item {TextButton(onClick={draft.templateTraveler=null;travelerPicker=false}) {Text("Sin Persona")}}
                items(people.size) {i->TextButton(onClick={draft.templateTraveler=people[i].id;travelerPicker=false}) {Text(people[i].name)}}
            }},confirmButton={TextButton(onClick={travelerPicker=false}) {Text("Cerrar")}})
            if(route!=null) {
                if(MetroPlanner.affected(route,net,restrictions)) Text("El catálogo o las restricciones afectan este plan. Se conserva hasta que cambies las estaciones o el servicio.",style=MaterialTheme.typography.bodySmall)
                Text("≈ ${route.minutes} min · ${MetroPresentation.stations(route)} estaciones · ${route.transfers} combinaciones",style=MaterialTheme.typography.bodyMedium)
                val instructions=route.steps.mapIndexedNotNull {i,s->if(i==0 || s.kind!=MetroStepKind.RIDE) MetroPresentation.instruction(route,i,MetroCatalogRevision.forRoute(source,route.networkVersion)) else null}
                instructions.forEach {Text(it,style=MaterialTheme.typography.bodySmall)}
            } else Text("Elige origen y destino con una ruta válida para guardar.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            if(existing==null) TextButton(enabled=enabled,onClick={
                draft.templateMetroPlan=null;draft.templateMetroCatalog=null;draft.templateTraveler=null
                draft.metroJourneyId=null;draft.metroJourneyRevision=null;draft.metroOriginalPlan=null;draft.metroOriginalTraveler=null
                adding=false;onValidity(true)
            }) {Text("Quitar plan Metro")}
            else Text("Editar este plan conserva la sesión e historial existentes.",style=MaterialTheme.typography.bodySmall)
        } }
        picker?.let {index->StationPicker(net,onDismiss={picker=null},preferences=p) {id->
            routeChanged=true
            when(index) {0->origin=id;-1->destination=id;-2->vias=vias+id;else->vias=vias.mapIndexed {i,current->if(i+1==index) id else current}}
            picker=null
        }}
    }
}
