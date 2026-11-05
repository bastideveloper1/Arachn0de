package com.r0ybt.arachn0de.slots

import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.BashGeneralScope
import kotlinx.coroutines.*

@Composable internal fun SlotScreen(session:SlotsSession,candidates:SlotCandidates,tags:TagState,people:Map<String,List<Person>>,
    loadCandidates:suspend ()->SlotCandidates,onOpenTask:(Node)->Unit,onBack:()->Unit,onOpenBank:(String,String?)->Unit={_,_->}) {
    BackHandler(onBack=onBack)
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var savingsMode by rememberSaveable {mutableStateOf(session.savingsMode)}
    val savingsDb=(context.applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication)?.database
    val savingsRepo=remember(savingsDb) {savingsDb?.let {SavingsRepository(it)}}
    var savingsLoadError by remember {mutableStateOf<String?>(null)}
    val savingsView by produceState<SavingsView?>(null,savingsRepo) {
        try {savingsRepo?.observe()?.collect {value=it;savingsLoadError=null}}
        catch(c:CancellationException) {throw c}
        catch(_:Exception) {savingsLoadError="No se pudo cargar Ahorro. Los datos se conservan."}
    }
    val savingsBook=remember(savingsView?.state) {savingsView?.state?.book()}
    val savingsProblem=savingsView?.let {savingsBook?.reconciliation(it.bank)}
    var savingsPage by remember {mutableStateOf<String?>(null)}
    var tasksMode by remember {mutableStateOf(session.tasksMode)}
    var soundEnabled by remember {mutableStateOf(session.soundEnabled)}
    SideEffect {session.savingsMode=savingsMode;session.tasksMode=tasksMode}
    val sounds=remember {SlotSounds()}
    DisposableEffect(sounds) {onDispose {sounds.close()}}
    val task=session.tasks
    val engine=if(savingsMode)session.savingsMachine else if(tasksMode)task.machine else session.traditional
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    var actionJob by remember {mutableStateOf<Job?>(null)}
    var revision by remember {mutableIntStateOf(0)}
    var active by remember {mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    var coinProgress by remember(engine) {mutableFloatStateOf(0f)}
    var error by remember {mutableStateOf<String?>(null)}
    var showPrizes by remember {mutableStateOf(false)}
    var traditionalWin by remember {mutableStateOf<Int?>(null)}
    LaunchedEffect(candidates,tasksMode) {task.refresh(candidates);if(tasksMode && task.machine.phase==SlotMachine.Phase.EMPTY)task.prepareSelection();revision++}
    val performSpin by rememberUpdatedState(newValue={
        if(!busy && active) {actionJob=scope.launch {
            busy=true
            try {
                if(!savingsMode && !engine.canSpin() && engine.canInsert()) {
                    if(tasksMode) {task.refresh(loadCandidates());if(task.insert())engine.inserted()}
                    else if(engine.insert())engine.inserted()
                }
                val started=if(savingsMode) {
                    if(savingsBook?.canSpin!=true || savingsProblem!=null) {savingsPage=when {savingsProblem!=null->"home";savingsView?.state?.active==null || savingsBook?.credits==0L->"buy";(savingsBook?.deficit ?: 0)>0->"topup";else->"home"};false}
                    else {
                        val e=requireNotNull(savingsRepo).spin(java.util.UUID.randomUUID().toString())
                        session.savingsAnimation=e.id;engine.animateCommitted(e.result,requireNotNull(savingsView).state.configs.single {it.id==e.config}.table.symbolCount);true
                    }
                } else if(tasksMode) {task.refresh(loadCandidates());task.spin()} else engine.spin()
                if(started) {coinProgress=0f;revision++}
                else revision++
            } catch(c:CancellationException) {throw c}
            catch(e:Exception) {if(savingsMode)savingsPage="home" else error="No se pudieron consultar las tareas. La moneda se conserva."}
            finally {busy=false}
        }}
        Unit
    })
    val insertCoin by rememberUpdatedState(newValue={
        val inserted=if(savingsMode) {savingsPage=if(savingsView?.state?.active==null)"config" else "buy";false} else if(tasksMode)task.insert() else engine.insert()
        if(inserted) {coinProgress=0f;if(soundEnabled)sounds.coin();revision++}
        inserted
    })
    val openResult={
        if(!busy && active) {actionJob=scope.launch {
            busy=true
            try {task.refresh(loadCandidates());revision++;task.result()?.let(onOpenTask)}
            catch(c:CancellationException) {throw c}
            catch(_:Exception) {error="No se pudo abrir la tarea. Puedes volver a intentarlo."}
            finally {busy=false}
        }}
        Unit
    }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,event->
            if(event==Lifecycle.Event.ON_RESUME)active=true
            if(event==Lifecycle.Event.ON_PAUSE) {active=false;actionJob?.cancel();sounds.stop()}
        }
        lifecycle.addObserver(observer);onDispose {lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(active,savingsMode,savingsView?.state?.animation) {
        if(active && savingsMode) {
            val state=savingsView?.state
            state?.animation?.let {id->
                val e=state.events.single {it.id==id}
                if(session.savingsAnimation!=id || engine.phase==SlotMachine.Phase.EMPTY) {
                    session.savingsAnimation=id;engine.animateCommitted(e.result,requireNotNull(savingsView).state.configs.single {it.id==e.config}.table.symbolCount);revision++
                }
            }
            if(state?.animation==null && engine.phase==SlotMachine.Phase.EMPTY)state?.events?.lastOrNull {it.kind=="SPIN"}?.let {engine.showCommitted(it.result,state.configs.single {c->c.id==it.config}.table.symbolCount);revision++}
        }
    }
    LaunchedEffect(active,savingsMode,engine.phase,savingsView?.state?.animation) {
        val state=savingsView?.state
        if(active && savingsMode && engine.phase==SlotMachine.Phase.FINISHED && state?.animation!=null && session.savingsAnimation==state.animation) {
            val e=state.events.single {it.id==state.animation}
            if(e.units>0) {savingsPage="win";if(soundEnabled)sounds.win()}
            savingsRepo?.acknowledge(e.id)
        }
    }
    val soundOn by rememberUpdatedState(soundEnabled)
    val phase=revision.let {engine.phase}
    LaunchedEffect(active,engine,phase) {
        if(!active)return@LaunchedEffect
        if(engine.phase==SlotMachine.Phase.INSERTING) {
            // Animation resumes without consuming a second coin after backgrounding.
            while(coinProgress<1f) {delay(16);coinProgress=(coinProgress+.06f).coerceAtMost(1f)}
            engine.inserted();revision++
        } else if(engine.phase==SlotMachine.Phase.SPINNING) {
            var previous=withFrameNanos {it}
            while(isActive && engine.phase==SlotMachine.Phase.SPINNING) {
                val now=withFrameNanos {it}
                val before=engine.elapsed
                engine.advance(((now-previous)/1_000_000).coerceAtMost(50));previous=now
                if(soundOn) {
                    if(engine.phase==SlotMachine.Phase.FINISHED) {if(!savingsMode && !tasksMode && engine.lastPrize>0)sounds.win() else sounds.finish()}
                    else if(!tasksMode && (0..2).any {before<SlotMachine.duration(it) && engine.elapsed>=SlotMachine.duration(it)})sounds.stopReel()
                    else if(before/180!=engine.elapsed/180)sounds.tick()
                }
                if(engine.phase==SlotMachine.Phase.FINISHED && !savingsMode && !tasksMode && engine.lastPrize>0)traditionalWin=engine.lastPrize
                revision++
            }
        }
    }
    BashGeneralScope {
        JungleSlots(engine,revision,task,tasksMode,savingsMode,active,busy,soundEnabled,savingsView,savingsProblem,tags,people,
            onSpin=performSpin,
            onMode={mode->sounds.stop()
                savingsMode=mode==2;tasksMode=mode==1
                session.tasksMode=tasksMode;session.savingsMode=savingsMode;error=null;revision++
            },onVolume={soundEnabled=!soundEnabled;session.soundEnabled=soundEnabled;if(!soundEnabled)sounds.stop()},onExit=onBack,
            onPanel={if(savingsMode)savingsPage="prizes" else if(tasksMode) {task.prepareSelection();revision++} else showPrizes=true},
            onCredit={},onPay={savingsPage="buy"},onChoose={task.choose(it);revision++},onOpenTask=openResult,onOptions={savingsPage="home"},onHistory={savingsPage="history"},onStatistics={savingsPage="stats"},celebrating=traditionalWin!=null || savingsPage=="win",rewardContent={
    if(savingsMode && savingsPage!=null && savingsView!=null && savingsRepo!=null && savingsDb!=null) {
        SavingsPanel(requireNotNull(savingsView),savingsRepo,savingsDb,requireNotNull(savingsPage),
            onDismiss={savingsPage=null},onOpenBank=onOpenBank,onBought={if(engine.insert()) {coinProgress=0f;if(soundEnabled)sounds.coin();revision++}})
    }
        })
        traditionalWin?.let {prize->JunglePanel("¡Ganaste!",{traditionalWin=null},{traditionalWin=null},celebrate=true) {
            Text("$prize créditos",color=JungleGold,style=MaterialTheme.typography.headlineMedium)
            Text("Premio del modo Tradicional, sin valor monetario real.",color=JungleGold,style=MaterialTheme.typography.bodySmall)
        }}
        if(showPrizes)AlertDialog(onDismissRequest={showPrizes=false},title={Text("Premios")},text={Text("Tres símbolos iguales en la línea central.\n\nCerezas: 1 · Plátanos: 2 · Guacamayo: 3\nPlata: 4 · Oro: 5 · Rubí: 7\nEsmeralda: 10 · Máscara: 20\n\nEn Tradicional son créditos ficticios sin valor monetario (unidad nominal del cartel: $1.000). Cada giro consume un crédito; cada moneda añade cinco.")},confirmButton={TextButton(onClick={showPrizes=false}) {Text("Cerrar")}})
        (savingsLoadError ?: error)?.let {message->AlertDialog(onDismissRequest={error=null},text={Text(message)},confirmButton={TextButton(onClick={error=null}) {Text("Cerrar")}})}
    }
}
