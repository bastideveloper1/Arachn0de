package com.r0ybt.arachn0de.metro

import android.animation.ValueAnimator
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.r0ybt.arachn0de.domain.model.Node

internal data class MetroActivityEntry(val journeyId:String,val nodeId:String?,val label:String,val status:String,val pulse:Boolean,val waiting:Boolean)
internal val LocalMetroActivity=staticCompositionLocalOf<Map<String,List<MetroActivityEntry>>> {emptyMap()}
internal val LocalMetroPreview=staticCompositionLocalOf<Map<String,String>> {emptyMap()}
internal object MetroActivity {
    fun status(j:MetroJourney,time:MetroTime):String {
        val s=j.data.active ?: return if(j.data.sessions.lastOrNull()?.ended!=null) "Finalizado" else "Pendiente"
        if(s.pausedAt!=null) return "Pausado"
        if(MetroStages.control(s).phase==MetroPhase.RIDING && MetroTracking.position(s,time).uncertain) return "Esperando confirmar posición"
        return when(MetroStages.control(s).phase) {MetroPhase.READY->if(MetroStages.control(s).transferStarted!=null) "Combinando" else "Esperando embarque";MetroPhase.ARRIVED->"Esperando continuación";MetroPhase.TRANSFERRING->"Combinando";MetroPhase.RIDING->if(MetroTracking.position(s,time).waiting!=null) "Esperando confirmar llegada" else "Activo"}
    }
    fun summary(j:MetroJourney,net:MetroNetwork,time:MetroTime):String {
        val route=j.data.active?.route ?: j.data.plan
        val historical=MetroCatalogRevision.forRoute(net,route.networkVersion)
        val lines=route.steps.filter {it.kind==MetroStepKind.RIDE}.map {it.line}.distinct().joinToString(" · ")
        val combination=route.steps.firstOrNull {it.kind==MetroStepKind.TRANSFER || it.kind==MetroStepKind.CHANGE}?.let {" · Combinación: ${historical.stations.getValue(it.from).name}"}.orEmpty()
        val progress=j.data.active?.let {" · ${MetroPresentation.positionText(it.route,MetroTracking.position(it,time),historical)}"}.orEmpty()
        return "$lines$combination · ${status(j,time)}$progress"
    }
    fun hierarchy(nodes:List<Node>,snapshot:MetroSnapshot,time:MetroTime):Map<String,List<MetroActivityEntry>> {
        val byId=nodes.associateBy {it.id};val result=mutableMapOf<String,MutableList<MetroActivityEntry>>()
        val network=snapshot.preferences.network
        snapshot.journeys.filter {it.data.active!=null}.forEach {j->
            val node=byId[j.row.nodeId] ?: return@forEach
            val status=status(j,time);val route=j.data.active!!.route;val net=MetroCatalogRevision.forRoute(network,route.networkVersion)
            val entry=MetroActivityEntry(j.row.id,node.id,"${net.stations.getValue(route.stops.first()).name} → ${net.stations.getValue(route.stops.last()).name}",status,status in setOf("Activo","Combinando"),status.startsWith("Esperando"))
            val visited=hashSetOf<String>();var current:Node?=node
            while(current!=null && visited.add(current.id)) {
                result.getOrPut("node:${current.id}"){mutableListOf()}.add(entry)
                current=current.parentId?.let(byId::get)?.takeIf {it.projectId==node.projectId}
            }
            result.getOrPut("project:${node.projectId}"){mutableListOf()}.add(entry)
        }
        return result.mapValues {(_,list)->list.distinctBy {it.journeyId}}
    }
}
@Composable internal fun MetroActivityIndicator(key:String) {
    val entries=LocalMetroActivity.current[key].orEmpty()
    if(entries.isEmpty()) return
    var selecting by remember {mutableStateOf(false)}
    val context=LocalContext.current
    val motion=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0 && (Build.VERSION.SDK_INT<26 || ValueAnimator.areAnimatorsEnabled())
    val opacity=if(motion && entries.any {it.pulse}) {
        val transition=rememberInfiniteTransition(label="Metro activity")
        val alpha by transition.animateFloat(.65f,1f,infiniteRepeatable(tween(1400),RepeatMode.Reverse),label="Metro gentle pulse")
        alpha
    } else 1f
    IconButton(onClick={if(entries.size==1) MetroNavigation.open(entries.single().nodeId) else selecting=true},modifier=Modifier.size(48.dp).testTag("metro-activity:$key").semantics {stateDescription=if(!motion) "Indicador fijo: movimiento reducido" else if(entries.any {it.pulse}) "Actividad Metro" else "Indicador fijo"}) {
        Icon(Icons.Default.DirectionsSubway,"Metro: ${entries.map {it.status}.distinct().joinToString()}",modifier=Modifier.size(20.dp).alpha(opacity),tint=if(entries.all {it.waiting}) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
    }
    if(selecting) AlertDialog(onDismissRequest={selecting=false},title={Text("Elegir seguimiento")},text={LazyColumn {items(entries,key={it.journeyId}) {entry->TextButton(onClick={selecting=false;MetroNavigation.open(entry.nodeId)}) {Text("${entry.label} · ${entry.status}")}}}},confirmButton={TextButton(onClick={selecting=false}) {Text("Cancelar")}})
}
