package com.r0ybt.arachn0de.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.R
import com.r0ybt.arachn0de.pomodoro.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import com.r0ybt.arachn0de.ui.theme.LocalAppearancePreferences
import com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

internal val LocalPomodoroFinishRequest=staticCompositionLocalOf<(() -> Unit)?> {null}
internal val LocalOpenPomodoro=staticCompositionLocalOf<(() -> Unit)?> {null}

/** Repaints only while running and visible. The deadline, never this ticker, owns elapsed time. */
@Composable internal fun pomodoroRemaining(controller:PomodoroController,data:PomodoroState):Long {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val enabled=LocalAudioFooterVisible.current
    var tick by remember {mutableLongStateOf(0)}
    LaunchedEffect(controller,lifecycle,enabled,data.session?.status,data.session?.manualEnd,data.config.ticking,data.session?.phase) {
        if(enabled && data.session?.status==PomodoroStatus.RUNNING && data.session.manualEnd!=true) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(isActive) {
                tick++
                delay(250)
            }
        }
    }
    return remember(data,tick) {controller.remaining()}
}

@Composable private fun PomodoroApple(running:Boolean,modifier:Modifier=Modifier,size:androidx.compose.ui.unit.Dp=20.dp) {
    val opacity=if(running) {
        val transition=rememberInfiniteTransition(label="pomodoro-running")
        val value by transition.animateFloat(.45f,1f,infiniteRepeatable(tween(900),RepeatMode.Reverse),label="apple-opacity")
        value
    } else 1f
    Icon(painterResource(R.drawable.lucide_apple),null,modifier.size(size).alpha(opacity),tint=Arachn0deColors.TextPrimary)
}

@Composable internal fun PomodoroScreen(controller:PomodoroController,data:PomodoroState,allowLandscape:Boolean=true,onBack:()->Unit) {
    BackHandler(onBack=onBack)
    val remaining=pomodoroRemaining(controller,data)
    var settings by rememberSaveable {mutableStateOf(false)}
    ScreenOrientation(allowLandscape && !settings)
    val s=data.session
    val phase=s?.phase ?: PomodoroPhase.FOCUS
    val total=s?.total ?: data.config.duration(phase)
    val greenBash=LocalAppearancePreferences.current?.settings?.bashTheme==true
    val palette=LocalAppearancePalette.current
    CompositionLocalProvider(LocalAppearancePalette provides if(greenBash) palette else palette.copy(
        text=Color.White,muted=Color(0xFFB4B4B8),primary=Color.White)) {
    Surface(Modifier.fillMaxSize().testTag("pomodoro-screen"),color=if(greenBash) palette.background else PomodoroBackground,
        contentColor=if(greenBash) palette.text else Color.White) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                GlobalChromeActions(leading=true)
                IconButton(onClick=onBack,modifier=Modifier.size(48.dp)) {LucideIcon(Icons.Default.ArrowBack,"Volver")}
                NavigationHeading("Pomodoro",Modifier.weight(1f))
                GlobalChromeActions()
            }
            Column(Modifier.fillMaxWidth().weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                PomodoroDial(data.config.appearance,
                    if(s?.status==PomodoroStatus.FINISHED) "00:00" else pomodoroTimeText(remaining),phase.label,
                    progress={if(s==null || s.status==PomodoroStatus.FINISHED) 0f else (1f-remaining.toFloat()/total).coerceIn(0f,1f)},
                    modifier=Modifier.fillMaxWidth().weight(1f).testTag("pomodoro-dial:${data.config.appearance.name}"),animateHalo=!settings)
                Text("Sesión ${s?.number ?: 1}/${s?.cycle ?: data.config.sessions}",color=Arachn0deColors.TextSecondary)
                Text(s?.status?.label ?: "Lista para iniciar",color=Arachn0deColors.TextSecondary,modifier=Modifier.testTag("pomodoro-status"))
                val context=LocalContext.current
                if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
                    Text("Sin permiso de notificaciones no habrá avisos fuera de la aplicación.",style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary,modifier=Modifier.padding(horizontal=16.dp))
                }
            }
            if(s?.status in setOf(PomodoroStatus.RUNNING,PomodoroStatus.PAUSED)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.Center) {
                    listOf(-5,-1,1,5).forEach {minutes->
                        TextButton(onClick={controller.adjust(minutes)},modifier=Modifier.heightIn(min=48.dp).testTag("pomodoro-adjust:$minutes")) {
                            Text("${if(minutes>0) "+" else ""}$minutes min")
                        }
                    }
                }
                if(s?.manualEnd==true) Text("Límite manual: retrocede o finaliza.",style=MaterialTheme.typography.labelSmall,modifier=Modifier.align(Alignment.CenterHorizontally))
            }
            Row(Modifier.fillMaxWidth().padding(8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                PomodoroControls(controller,data,compact=true)
                IconButton(onClick={settings=true},modifier=Modifier.size(48.dp).testTag("pomodoro-settings")) {LucideIcon(Icons.Default.Settings,"Configurar Pomodoro")}
            }
        }
    }
    }
    if(settings) PomodoroSettings(controller,data.config) {settings=false}
}

@Composable internal fun PomodoroBar(controller:PomodoroController,data:PomodoroState,modifier:Modifier=Modifier,compact:Boolean=false,onCompactChange:(Boolean)->Unit={},onExpand:()->Unit) {
    val s=data.session ?: return
    if(!data.visible) return
    val remaining=pomodoroRemaining(controller,data)
    val timeStyle=TextStyle(fontSize=14.sp,fontFamily=FontFamily.Monospace)
    val measurer=rememberTextMeasurer()
    // Reserve six digits, including configured phases of 100–180 minutes; ticks never move controls.
    val timeWidth=with(LocalDensity.current) {measurer.measure("000:00",timeStyle).size.width.toDp()}
    PersistentModuleBoundary("pomodoro-module") {
    NavigationBarFrame("pomodoro-module-frame") {
    Row(modifier.fillMaxWidth()
        // Only unusually narrow windows or enlarged system fonts need horizontal scrolling.
        .horizontalScroll(rememberScrollState()).padding(horizontal=4.dp,vertical=4.dp).heightIn(min=48.dp).testTag("pomodoro-bar"),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        IconButton(onClick={onCompactChange(!compact)},modifier=Modifier.size(48.dp).testTag("pomodoro-compact-toggle")) {
            Box(Modifier.semantics {contentDescription=if(compact) "Expandir barra de Pomodoro" else "Minimizar barra de Pomodoro"}) {
                PomodoroApple(s.status==PomodoroStatus.RUNNING,Modifier.testTag("pomodoro-bar-apple"),size=16.dp)
            }
        }
        Text(pomodoroTimeText(remaining),Modifier.width(timeWidth).testTag("pomodoro-bar-time"),style=timeStyle,color=Arachn0deColors.TextPrimary,maxLines=1,softWrap=false)
        if(!compact) Text("${s.number}/${s.cycle}",Modifier.testTag("pomodoro-bar-cycle"),style=MaterialTheme.typography.labelSmall,color=Arachn0deColors.TextSecondary,maxLines=1,softWrap=false)
        Text(if(s.status==PomodoroStatus.READY) "Lista" else s.status.label,Modifier.testTag("pomodoro-bar-state"),style=MaterialTheme.typography.labelSmall,color=Arachn0deColors.TextSecondary,maxLines=1,softWrap=false)
        PomodoroControls(controller,data,compact=true)
        IconButton(onClick=onExpand,modifier=Modifier.size(48.dp).testTag("pomodoro-expand")) {
            Icon(painterResource(R.drawable.lucide_chevron_down),"Ampliar Pomodoro",Modifier.size(20.dp).rotate(180f),tint=Arachn0deColors.TextPrimary)
        }
    }
}
}

}

@Composable private fun PomodoroControls(controller:PomodoroController,data:PomodoroState,compact:Boolean,modifier:Modifier=Modifier) {
    var confirmFinish by rememberSaveable {mutableStateOf(false)}
    val globalRequest=LocalPomodoroFinishRequest.current
    val requestFinish:()->Unit={if(globalRequest!=null) globalRequest() else confirmFinish=true}
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val running=data.session?.status==PomodoroStatus.RUNNING
    val label=when(data.session?.status) {PomodoroStatus.RUNNING->"Pausar";PomodoroStatus.PAUSED->"Reanudar";else->"Iniciar"}
    val action={
        if(running) controller.pause() else {
            controller.start()
            if((data.session==null || data.session.status==PomodoroStatus.FINISHED) && Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if(compact) 0.dp else 4.dp)) {
        if(compact) IconButton(onClick=action,modifier=Modifier.size(48.dp).testTag("pomodoro-toggle")) {Icon(if(running) Icons.Default.Pause else Icons.Default.PlayArrow,label,Modifier.size(20.dp),tint=Arachn0deColors.TextPrimary)}
        else TextButton(onClick=action,modifier=Modifier.heightIn(min=48.dp).testTag("pomodoro-toggle")) {
            LucideIcon(if(running) Icons.Default.Pause else Icons.Default.PlayArrow,null,Modifier.size(20.dp));Spacer(Modifier.width(6.dp));Text(label)
        }
        if(data.visible) {
            if(compact) IconButton(onClick=requestFinish,modifier=Modifier.size(48.dp).testTag("pomodoro-finish")) {Icon(Icons.Default.Stop,"Finalizar Pomodoro",Modifier.size(20.dp),tint=Arachn0deColors.TextPrimary)}
            else TextButton(onClick=requestFinish,modifier=Modifier.heightIn(min=48.dp).testTag("pomodoro-finish")) {Text("Finalizar")}
        }
    }
    if(confirmFinish) PomodoroFinishConfirmation(controller) {confirmFinish=false}
}

@Composable internal fun PomodoroFinishConfirmation(controller:PomodoroController,onDismiss:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("Finalizar Pomodoro")},
        text={Text("¿Finalizar la sesión actual? No se completarán tareas ni se cambiarán otros elementos.")},
        confirmButton={TextButton(onClick={controller.finish();onDismiss()}) {Text("Finalizar")}},
        dismissButton={TextButton(onClick=onDismiss) {Text("Cancelar")}})
}

@Composable private fun PomodoroSettings(controller:PomodoroController,initial:PomodoroConfig,onDismiss:()->Unit) {
    val importScope=rememberCoroutineScope()
    var soundTarget by rememberSaveable {mutableStateOf<String?>(null)}
    var soundBusy by remember {mutableStateOf(false)}
    var soundRevision by remember {mutableIntStateOf(0)}
    var soundError by remember {mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        val target=soundTarget
        soundTarget=null
        if(uri!=null && target!=null) {
            soundBusy=true
            importScope.launch {
                try {withContext(Dispatchers.IO) {controller.sounds.import(target,uri)};soundRevision++}
                catch(c:kotlinx.coroutines.CancellationException) {throw c}
                catch(e:Exception) {soundError=e.message ?: "No se pudo importar el sonido. Se conserva el anterior."}
                finally {soundBusy=false}
            }
        }
    }
    var focus by rememberSaveable {mutableStateOf(initial.focus.toString())}
    var short by rememberSaveable {mutableStateOf(initial.shortBreak.toString())}
    var long by rememberSaveable {mutableStateOf(initial.longBreak.toString())}
    var sessions by rememberSaveable {mutableStateOf(initial.sessions.toString())}
    var ticking by rememberSaveable {mutableStateOf(initial.ticking)}
    var sound by rememberSaveable {mutableStateOf(initial.sound)}
    var appearanceName by rememberSaveable {mutableStateOf(initial.appearance.name)}
    val appearance=PomodoroAppearance.valueOf(appearanceName)
    val config=runCatching {PomodoroConfig(focus.toInt(),short.toInt(),long.toInt(),sessions.toInt(),ticking,sound,appearance)}.getOrNull()
    ModularPanelDialog(onDismissRequest={if(!soundBusy) onDismiss()},title={Text("Configurar Pomodoro")},compact=true,
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Apariencia",style=MaterialTheme.typography.titleSmall)
            PomodoroAppearance.entries.forEach {style->
                val selected=style==appearance
                Row(Modifier.fillMaxWidth().testTag("pomodoro-style:${style.name}")
                    .selectable(selected,role=Role.RadioButton,onClick={appearanceName=style.name})
                    .border(1.dp,if(selected) Arachn0deColors.Accent else Arachn0deColors.Outline,RoundedCornerShape(8.dp))
                    .padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    RadioButton(selected,onClick=null)
                    PomodoroDial(style,"25:00","",{.7f},Modifier.size(52.dp).background(PomodoroBackground,RoundedCornerShape(8.dp)),preview=true)
                    Text(style.label,Modifier.weight(1f))
                }
            }
            listOf(Triple("Concentración (min)",focus,{v:String->focus=v}),Triple("Descanso corto (min)",short,{v:String->short=v}),
                Triple("Descanso largo (min)",long,{v:String->long=v}),Triple("Sesiones por ciclo",sessions,{v:String->sessions=v})).forEach {(label,value,set)->
                OutlinedTextField(value,set,label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
            }
            Text("Duraciones: 1–180 min. Ciclos: 1–12 sesiones. Los cambios se aplican a las fases siguientes; el tamaño del ciclo actual se conserva.",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text("Tic-tac en concentración",Modifier.weight(1f));Switch(ticking,{ticking=it})}
            Text("Solo mientras Arachn0de está visible; se silencia durante la grabación de audio.",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text("Avisos sonoros",Modifier.weight(1f));Switch(sound,{sound=it})}
            Text("Sonidos locales · hasta 512 KiB y 30 s. La selección se guarda al elegirla.",style=MaterialTheme.typography.bodySmall)
            listOf("ticking" to "Tic-tac","completion" to "Fin de fase").forEach {(key,label)->
                val custom=remember(soundRevision,key) {controller.sounds.selected(key)}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("$label: ${if(custom) "Personalizado" else "Integrado"}",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                    TextButton(enabled=!soundBusy,onClick={soundTarget=key;picker.launch(arrayOf("audio/*"))}) {Text("Elegir")}
                    TextButton(enabled=!soundBusy && custom,onClick={
                        runCatching {controller.sounds.reset(key)}.onSuccess {soundRevision++}.onFailure {soundError="No se pudo restaurar el sonido integrado."}
                    }) {Text("Restablecer")}
                }
            }
            soundError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            Text("La siguiente fase se inicia manualmente. Android puede retrasar los avisos con la pantalla bloqueada.",style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(enabled=config!=null,onClick={config?.let {controller.configure(it);if(controller.state.value.config==it) onDismiss()}}) {Text("Guardar")}},
        dismissButton={TextButton(onClick=onDismiss) {Text("Cancelar")}})
}
