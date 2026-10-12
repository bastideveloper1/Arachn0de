package com.r0ybt.arachn0de.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val RaceColors=listOf(Color(0xFFEF5350),Color(0xFF42A5F5),Color(0xFFFFCA28),Color(0xFF66BB6A),Color(0xFFAB47BC),Color(0xFFFF7043),Color(0xFF26C6DA),Color(0xFFEC407A),Color(0xFFBDBDBD),Color(0xFF8D6E63))

@Composable internal fun GameScreen(onBack: ()->Unit, @Suppress("UNUSED_PARAMETER") clock: ()->Long = { System.currentTimeMillis() }) {
    val database=(LocalContext.current.applicationContext as Arachn0deApplication).database
    val repo=remember(database) { RaceRepository(database) }; val scope=rememberCoroutineScope()
    val saved by remember(repo) { repo.observe() }.collectAsState(initial=null)
    val art=raceArt()
    var page by rememberSaveable { mutableStateOf("home") }
    var viewer by rememberSaveable { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var help by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<Int?>(null) }
    val s=saved?.session
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) { val observer=LifecycleEventObserver { _,_->resumed=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }; lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) } }
    fun act(action: (RaceSession)->RaceSession) {
        val revision=saved?.revision ?: return
        scope.launch {
            try { repo.act(revision,action) }
            catch(e: IllegalArgumentException) { error=e.message ?: "Selección inválida; el objeto se conserva." }
            catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(_: Exception) { error="No se pudo guardar. El estado anterior se conserva; reintenta." }
        }
    }
    BackHandler(page!="home") { page="home"; draft=null }
    // Lifecycle and CAS protect against duplicate animation/bot callbacks and stale restored commands.
    LaunchedEffect(saved?.revision,page,resumed) {
        if(page!="race" || s==null || !resumed) return@LaunchedEffect
        if(s.phase==RacePhase.WON) return@LaunchedEffect
        if(!s.actor.human) { delay(if(s.phase==RacePhase.MOVING)260 else if(s.phase==RacePhase.PRIZE)800 else 550); act(RaceBots::act) }
        else when(s.phase) {
            RacePhase.MOVING -> { delay(300); act { RaceRules.step(it) } }
            RacePhase.END,RacePhase.SKIPPED -> { delay(if(s.phase==RacePhase.SKIPPED)1300 else 650); act(RaceRules::end) }
            else -> Unit
        }
    }
    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={ if(page=="home")onBack() else { page="home"; draft=null } }) { Text("Atrás") }
            Text("Carrera táctica",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            TextButton(onClick={help=true}) { Text("Reglas") }
            com.r0ybt.arachn0de.ui.GlobalChromeActions()
        }
        when(page) {
            "home" -> {
                Text("Avanza, elige rutas y recoge objetos. De 2 a 10 corredores · juego local offline.")
                if(saved?.legacy==true) Text("La partida anterior se conserva en el respaldo. Iniciar una carrera la sustituye; exporta antes si quieres conservarla.")
                if(saved?.corrupt==true) Text("Partida no válida. No se ha reemplazado ni eliminado. Conserva un respaldo antes de iniciar otra.",color=MaterialTheme.colorScheme.error)
                if(s!=null) Button(onClick={page="race"; viewer=if(s.actor.human)s.actor.id else s.players.firstOrNull { it.human }?.id ?: 0}) { Text("Continuar carrera · ronda ${s.round}") }
                Button(onClick={page="setup"}) { Text("${if(s==null)"Nueva" else "Sustituir por nueva"} carrera") }
                Text("Un objeto antes del dado cuesta −1 casilla. Los golpes hacen perder el próximo movimiento; no se acumulan.")
            }
            "setup" -> RaceSetup(art,onStart={ characters,humans,laps ->
                scope.launch { try { repo.start(RaceRules.newGame(characters,humans,laps)); viewer=humans.firstOrNull() ?: 0; page="race" } catch(_: Exception) { error="No se pudo iniciar; la partida anterior se conserva." } }
            })
            "race" -> if(s!=null) {
                val local=s.players.getOrNull(viewer) ?: s.players.first()
                val handoff=s.phase==RacePhase.HANDOFF && s.actor.human
                Text("Turno ${s.actor.id+1}: ${s.actor.character.label} · ronda ${s.round}",fontWeight=FontWeight.Bold)
                if(handoff) {
                    Text("Entrega el dispositivo al corredor ${s.actor.id+1}. La información privada se oculta durante el cambio.")
                    Button(onClick={viewer=s.actor.id; draft=null; act(RaceRules::begin)}) { Text("Ver mi turno · ${s.actor.character.label}") }
                    Spacer(Modifier.weight(1f))
                } else {
                    Text("Tú: ${local.character.label} · vuelta ${minOf(local.lap+1,s.laps)}/${s.laps} · ${if(s.actor.invisible && s.actor.id!=viewer) "Corredor invisible: posición oculta" else s.message}",style=MaterialTheme.typography.bodySmall)
                    RaceRanking(s,art,viewer)
                    RaceBoard(s,viewer,art,Modifier.weight(1f))
                    if(s.effect!=null) Row(verticalAlignment=Alignment.CenterVertically) { RaceImage(art,s.effect.file,s.effect.label,Modifier.size(42.dp)); Text(s.effect.label,style=MaterialTheme.typography.bodySmall) }
                    if(s.phase==RacePhase.WON) Text(s.message,style=MaterialTheme.typography.titleLarge)
                    RaceInventory(s,viewer,art,onUse={draft=it})
                    if(s.actor.human && s.actor.id==viewer) when(s.phase) {
                        RacePhase.READY -> Button(onClick={act(RaceRules::roll)},Modifier.fillMaxWidth()) { Text("Lanzar dado${if(s.cost==1) " · −1 casilla" else ""}") }
                        RacePhase.REROLL -> Row { Button(onClick={act { RaceRules.reroll(it,false) }},Modifier.weight(1f)) { Text("Conservar ${s.die}") }; Button(onClick={act { RaceRules.reroll(it,true) }},Modifier.weight(1f)) { Text("Repetir una vez") } }
                        RacePhase.BRANCH -> Row { s.track.tile(s.actor.tile).next.forEachIndexed { i,to -> Button(onClick={act { RaceRules.step(it,to) }},Modifier.weight(1f)) { Text(if(i==0)"Directa" else "+2 casillas · caja") } } }
                        RacePhase.EYE -> EyeChoice(s,viewer,art) { target->act { RaceRules.eye(it,target) } }
                        else -> Unit
                    }
                    if(s.die>0 && s.phase!=RacePhase.REROLL && s.visible(viewer).any { it.id==s.actor.id }) Row(verticalAlignment=Alignment.CenterVertically) {
                        val dice=listOf(R.drawable.dados1,R.drawable.dados2,R.drawable.dados3,R.drawable.dados4,R.drawable.dados5,R.drawable.dados6)
                        Image(painterResource(dice[s.die-1]),"Dado ${s.die}",Modifier.size(30.dp))
                        Text("Dado: ${s.die} · pasos restantes: ${if(s.actor.invisible && s.actor.id!=viewer) "—" else s.steps}",style=MaterialTheme.typography.bodySmall)
                    }
                }
                if(s.phase==RacePhase.PRIZE && !handoff && s.visible(viewer).any { it.id==s.actor.id }) PrizeDialog(s,art) { replace->act { RaceRules.prize(it,replace) } }
                draft?.let { slot -> if(s.phase==RacePhase.READY && s.actor.id==viewer && s.actor.human) ItemDialog(s,slot,art,onCancel={draft=null},onConfirm={target,cells,choices -> draft=null; act { RaceRules.use(it,slot,target,cells,choices) } }) else draft=null }
            }
        }
    }
    error?.let { message -> AlertDialog(onDismissRequest={error=null},title={Text("Acción sin confirmar")},text={Text(message)},confirmButton={TextButton(onClick={error=null}) { Text("Entendido") }}) }
    if(help) AlertDialog(onDismissRequest={help=false},title={Text("Carrera táctica")},text={ LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item { Text("Completa las vueltas y cruza la meta. Comparte casillas sin daño ni bloqueo. Cada caja se recoge una vez por vuelta, al pasar; inventario de dos objetos, disponibles desde el siguiente turno. La ruta con caja tiene dos pasos más. Aturdido: omites objetos y dado; los temporizadores siguen. Un objeto antes del dado cuesta −1, mínimo cero.") }
        RaceItem.entries.forEach { item -> item { Row(verticalAlignment=Alignment.CenterVertically) { RaceImage(art,item.file,item.label,Modifier.size(50.dp)); Column { Text(item.label,fontWeight=FontWeight.Bold); Text(item.help) } } } }
    }},confirmButton={TextButton(onClick={help=false}) { Text("Jugar") }})
}

@Composable private fun RaceSetup(art: Map<String,ImageBitmap>,onStart: (List<Racer>,Set<Int>,Int)->Unit) {
    var count by rememberSaveable { mutableIntStateOf(10) }; var laps by rememberSaveable { mutableIntStateOf(3) }
    var selected by remember { mutableStateOf(Racer.entries.toList()) }; var humans by remember { mutableStateOf(setOf(Racer.RABBIT)) }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Corredores: $count",Modifier.weight(1f)); TextButton(onClick={count=(count-1).coerceAtLeast(2); selected=selected.take(count)}) { Text("−") }; TextButton(onClick={count=(count+1).coerceAtMost(10); selected=(selected+Racer.entries.filterNot { it in selected }).take(count)}) { Text("+") } }
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Vueltas: $laps",Modifier.weight(1f)); TextButton(onClick={laps=(laps-1).coerceAtLeast(1)}) { Text("−") }; TextButton(onClick={laps=(laps+1).coerceAtMost(9)}) { Text("+") } }
        Text("Elige personajes; los puestos vacantes se completan con bots. Pulsa Humano/Bot para cambiar el control.",style=MaterialTheme.typography.bodySmall)
        LazyVerticalGrid(GridCells.Adaptive(112.dp),Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(Racer.entries,key={it.name}) { racer -> val chosen=racer in selected
                Card(Modifier.fillMaxWidth().border(if(chosen)2.dp else 0.dp,if(chosen)MaterialTheme.colorScheme.primary else Color.Transparent,RoundedCornerShape(12.dp))) {
                    Column(Modifier.padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        RaceImage(art,racer.file,racer.label,Modifier.size(88.dp).clickable { selected=if(chosen)selected-racer else (selected+racer).takeLast(count) })
                        Text(racer.label,style=MaterialTheme.typography.labelLarge)
                        TextButton(onClick={ if(!chosen)selected=(selected+racer).takeLast(count); humans=if(racer in humans)humans-racer else humans+racer }) { Text(if(racer in humans && chosen)"Humano" else "Bot") }
                        Checkbox(chosen,onCheckedChange={selected=if(chosen)selected-racer else (selected+racer).takeLast(count)})
                    }
                }
            }
        }
        Button(onClick={confirm=true},Modifier.fillMaxWidth()) { Text("Iniciar · $count corredores") }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Iniciar nueva carrera")},text={Text("Sustituye la partida guardada. Los datos de productividad se conservan. Puedes cancelar y exportar un respaldo antes.")},dismissButton={TextButton(onClick={confirm=false}){Text("Cancelar")}},confirmButton={TextButton(onClick={
        val chars=(selected+Racer.entries.filterNot { it in selected }).take(count)
        onStart(chars,chars.mapIndexedNotNull { i,c->i.takeIf { c in humans && c in selected } }.toSet(),laps)
        confirm=false
    }) { Text("Iniciar") }})
}

@Composable private fun RaceRanking(s: RaceSession,art: Map<String,ImageBitmap>,viewer: Int) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        s.ranking().forEachIndexed { rank,p -> Row(verticalAlignment=Alignment.CenterVertically) {
            RaceImage(art,p.character.file,null,Modifier.size(28.dp).border(2.dp,RaceColors[p.id],CircleShape))
            Text("${rank+1}º · #${p.id+1}${if(p.id==viewer)" Tú" else ""}",style=MaterialTheme.typography.labelSmall)
        } }
    }
}
@Composable private fun RaceInventory(s: RaceSession,viewer: Int,art: Map<String,ImageBitmap>,onUse: (Int)->Unit) {
    val p=s.players[viewer]
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        repeat(2) { slot -> val item=p.inventory.getOrNull(slot)
            OutlinedCard(Modifier.weight(1f)) { Row(Modifier.padding(4.dp),verticalAlignment=Alignment.CenterVertically) {
                if(item!=null) { RaceImage(art,item.item.file,item.item.label,Modifier.size(42.dp)); Column(Modifier.weight(1f)) {
                    Text(item.item.label,style=MaterialTheme.typography.labelMedium)
                    if(item.readyTurn>p.turns) Text("Próximo turno",style=MaterialTheme.typography.labelSmall)
                    if(s.current==viewer && p.human && s.phase==RacePhase.READY && !s.itemUsed && item.readyTurn<=p.turns) TextButton(onClick={onUse(slot)},contentPadding=PaddingValues(0.dp)) { Text("Usar · −1") }
                } } else Text("Espacio ${slot+1} vacío",Modifier.padding(8.dp),style=MaterialTheme.typography.labelSmall)
            } }
        }
    }
}

@Composable private fun EyeChoice(s: RaceSession,viewer: Int,art: Map<String,ImageBitmap>,onChoice: (Int?)->Unit) {
    val zone=s.eyes.single { it.owner==viewer }.zone
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        s.visible(viewer).filter { it.id!=viewer && it.tile in zone }.forEach { p -> TextButton(onClick={onChoice(p.id)}) { RaceImage(art,p.character.file,p.character.label,Modifier.size(30.dp)); Text("Rayo #${p.id+1}") } }
        TextButton(onClick={onChoice(null)}) { Text("No disparar") }
    }
}

@Composable private fun PrizeDialog(s: RaceSession,art: Map<String,ImageBitmap>,onChoice: (Int?)->Unit) {
    val prize=s.prize ?: return
    var frame by remember(s.current,s.actor.collected,prize) { mutableIntStateOf(0) }
    LaunchedEffect(s.current,s.actor.collected,prize) { repeat(6) { delay(75); frame++ } }
    val shown=if(frame>=6)prize.item else RaceItem.entries[frame%8]
    AlertDialog(onDismissRequest={},title={Text(if(frame>=6)"Premio: ${prize.item.label}" else "Caja sorpresa…")},text={Column(horizontalAlignment=Alignment.CenterHorizontally) {
        RaceImage(art,shown.file,shown.label,Modifier.fillMaxWidth().height(110.dp))
        Text("Disponible desde tu siguiente turno")
        if(s.actor.human && s.actor.inventory.size==2 && frame>=6) { Text("Inventario lleno: sustituye un objeto o descarta el premio."); s.actor.inventory.forEachIndexed { i,item->TextButton(onClick={onChoice(i)}) { Text("Sustituir ${item.item.label}") } } }
    }},confirmButton={TextButton(onClick={onChoice(null)},enabled=frame>=6 && s.actor.human) { Text(if(!s.actor.human)"Bot · continúa automáticamente" else if(s.actor.inventory.size<2)"Guardar y continuar" else "Descartar premio") }})
}
