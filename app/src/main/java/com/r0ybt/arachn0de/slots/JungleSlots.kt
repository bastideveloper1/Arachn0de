package com.r0ybt.arachn0de.slots

import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.r0ybt.arachn0de.R
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.OfficialIcon
import com.r0ybt.arachn0de.ui.PrimaryControlButton
import kotlinx.coroutines.*
import kotlin.math.*

internal object JungleAssets {
    val symbols=SlotMachine.symbols.map {it.removePrefix("jungle/")}
    val names=symbols
}
private val slotSurface=Color(0xff202124)
private val slotBorder=Color(0xff414347)
private val legacyToJungle=listOf(5,4,3,2,1,0,6)

@Composable private fun SlotBlock(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier,shape=RoundedCornerShape(12.dp),color=slotSurface,contentColor=Color.White,border=BorderStroke(1.dp,slotBorder)) {
        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}

/** Presentation only: engines, committed results and payment panels keep their existing owners. */
@Composable internal fun JungleSlots(engine:SlotMachine,revision:Int,task:TaskSlotSession,tasksMode:Boolean,savingsMode:Boolean,
    active:Boolean,busy:Boolean,soundEnabled:Boolean,savings:SavingsView?,problem:String?,tags:TagState,people:Map<String,List<Person>>,
    onSpin:()->Unit,onMode:(Int)->Unit,onVolume:()->Unit,onExit:()->Unit,onPanel:()->Unit,onCredit:()->Unit,onPay:()->Unit,onChoose:(Int)->Unit,onOpenTask:()->Unit,onOptions:()->Unit=onPanel,
    onHistory:()->Unit=onOptions,onStatistics:()->Unit=onOptions,celebrating:Boolean=false,rewardContent:@Composable ()->Unit={}) {
    val context=LocalContext.current
    var images by remember {mutableStateOf<Map<String,ImageBitmap>>(emptyMap())}
    var failure by remember {mutableStateOf(false)}
    LaunchedEffect(context) {
        try {images=withContext(Dispatchers.IO) {JungleAssets.names.associateWith {name->
            context.assets.open("slots/jungle/$name").use {requireNotNull(BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {inSampleSize=4})).asImageBitmap()}
        }}} catch(e:CancellationException) {throw e} catch(_:Exception) {failure=true}
    }
    val book=remember(savings?.state) {savings?.state?.book()}
    val table=if(savingsMode)book?.nextConfig?.table ?: SavingsPayTable() else SavingsPayTable()
    val interactive=active && !busy && images.isNotEmpty() && !failure
    val canChange=interactive && engine.phase!=SlotMachine.Phase.SPINNING
    var prizeGif by remember(engine) {mutableStateOf<String?>(null)}
    LaunchedEffect(active,celebrating,tasksMode,savingsMode,engine.phase) {
        prizeGif=null
        if(active && celebrating && !tasksMode && engine.phase==SlotMachine.Phase.FINISHED) {
            val event=if(savingsMode)savings?.state?.events?.lastOrNull {it.kind=="SPIN"} else null
            val prizeTable=if(savingsMode)savings?.state?.configs?.firstOrNull {it.id==event?.config}?.table else SavingsPayTable()
            val units=if(savingsMode)event?.units ?: 0 else engine.lastPrize.toLong()
            if(prizeTable!=null && engine.symbolCount==8)prizeGif=junglePrizeGif(engine.results,prizeTable,units)
            if(prizeGif!=null) {delay(2300);prizeGif=null}
        }
    }
    CompositionLocalProvider(LocalContentColor provides Color.White) {
    Column(Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            OfficialIcon(R.drawable.bw90_brand_mark,"Arachn0de",size=36.dp)
            Text("Tragaperras",Modifier.weight(1f).padding(start=10.dp),style=MaterialTheme.typography.titleLarge)
            IconButton(onClick=onExit,enabled=interactive) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"Salir")}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf(Triple("Tradicional",Icons.Default.Casino,0),Triple("Tareas",Icons.Default.Checklist,1),Triple("Pago",Icons.Default.AccountBalanceWallet,2)).forEach {(label,icon,mode)->
                val selected=mode==if(savingsMode)2 else if(tasksMode)1 else 0
                OutlinedButton(onClick={onMode(mode)},enabled=canChange,modifier=Modifier.weight(1f).heightIn(min=56.dp).testTag("slot-mode:$mode").semantics {this.selected=selected},
                    shape=RoundedCornerShape(8.dp),border=BorderStroke(if(selected)2.dp else 1.dp,if(selected)Color.White else slotBorder),
                    colors=ButtonDefaults.outlinedButtonColors(containerColor=if(selected)Color(0xff34363a) else slotSurface,contentColor=Color.White),contentPadding=PaddingValues(4.dp)) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {Icon(icon,null,Modifier.size(20.dp));Text(label,style=MaterialTheme.typography.labelMedium)}
                }
            }
        }
        if(failure)Text("No se pudieron cargar los símbolos de Tragaperras.")
        else if(images.isEmpty())CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        else {
            SlotBlock(Modifier.fillMaxWidth()) {
                if(table.symbolCount==7 || table.prizeUnits!=SavingsPayTable().prizeUnits)Text(if(table.symbolCount==7)"Tabla anterior" else "Tabla personalizada",style=MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    table.order.forEach {symbol->
                        val display=if(table.symbolCount==7)legacyToJungle[symbol] else symbol
                        Column(Modifier.widthIn(min=64.dp).border(1.dp,slotBorder,RoundedCornerShape(8.dp)).padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                            Image(images.getValue(JungleAssets.symbols[display]),savingsName(table,symbol),Modifier.size(32.dp),contentScale=ContentScale.Fit)
                            Text(savingsCash(table.prizeUnits[symbol]),style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                prizeGif?.let {path->JunglePrizeAnimation(path,Modifier.fillMaxWidth().height(96.dp)) {}}
            }
            SlotBlock(Modifier.fillMaxWidth().testTag("slot-stage")) {
                if(tasksMode) {
                    Row(Modifier.fillMaxWidth().height(200.dp),verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={onChoose(-1)},enabled=canChange && task.projects.isNotEmpty(),modifier=Modifier.size(48.dp).testTag("slot-core-arrow:-1")) {Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft,"Núcleo anterior")}
                        SlotTaskWindow(task,revision,busy,tags,people,onChoose,onOpenTask,Modifier.weight(1f).fillMaxHeight())
                        IconButton(onClick={onChoose(1)},enabled=canChange && task.projects.isNotEmpty(),modifier=Modifier.size(48.dp).testTag("slot-core-arrow:1")) {Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,"Núcleo siguiente")}
                    }
                } else Row(Modifier.fillMaxWidth().height(240.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    repeat(3) {reel->
                        Canvas(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(Color.Black).border(1.dp,slotBorder,RoundedCornerShape(8.dp)).testTag("slot-reel:$reel").semantics {
                            contentDescription="Rodillo ${reel+1}: ${savingsName(if(engine.symbolCount==7)SavingsPayTable.legacy() else SavingsPayTable(),engine.results[reel])}"
                        }) {
                            revision
                            val cell=size.height/3
                            val position=engine.visualPosition(reel);val current=floor(position).toInt()
                            clipRect {
                                for(step in -2..2) {
                                    val index=current+step;val symbol=engine.visualSymbol(reel,index)
                                    val display=if(engine.symbolCount==7)legacyToJungle[symbol] else symbol
                                    val image=images.getValue(JungleAssets.symbols[display])
                                    val center=size.height/2+(position-index)*cell
                                    val factor=min((size.width-12)/image.width,(cell-12)/image.height)
                                    val w=(image.width*factor).roundToInt().coerceAtLeast(1);val h=(image.height*factor).roundToInt().coerceAtLeast(1)
                                    drawImage(image,dstOffset=IntOffset(((size.width-w)/2).roundToInt(),(center-h/2).roundToInt()),dstSize=IntSize(w,h),filterQuality=FilterQuality.High)
                                }
                                drawLine(slotBorder,androidx.compose.ui.geometry.Offset(0f,cell),androidx.compose.ui.geometry.Offset(size.width,cell))
                                drawLine(slotBorder,androidx.compose.ui.geometry.Offset(0f,2*cell),androidx.compose.ui.geometry.Offset(size.width,2*cell))
                            }
                        }
                    }
                }
            }
        }
        SlotBlock(Modifier.fillMaxWidth()) {
            val credits=if(savingsMode)book?.credits ?: 0L else engine.credits
            val prize=if(savingsMode)savings?.state?.events?.lastOrNull {it.kind=="SPIN"}?.units ?: 0 else engine.lastPrize.toLong()
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {Text(if(tasksMode)"Núcleos" else "Créditos",style=MaterialTheme.typography.labelSmall);Text(if(tasksMode)"${task.projects.size}" else "$credits",style=MaterialTheme.typography.titleMedium)}
                PrimaryControlButton(onClick=onSpin,enabled=canChange,modifier=Modifier.weight(1.2f).heightIn(min=56.dp).testTag("slot-spin")) {Text("GIRAR")}
                Column(Modifier.weight(1f)) {Text("Premio",style=MaterialTheme.typography.labelSmall);Text(if(tasksMode)"Tarea" else savingsCash(prize),style=MaterialTheme.typography.labelMedium)}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                SlotTool(if(tasksMode)"Elegir Núcleo" else "Premios",Icons.Default.Receipt,interactive,onPanel,Modifier.weight(1f))
                if(savingsMode)SlotTool("Depositar",Icons.Default.Add,interactive && savings?.state?.animation==null,onPay,Modifier.weight(1f))
            }
            rewardContent()
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            SlotTool("Ajustes",Icons.Default.Settings,interactive,if(savingsMode)onOptions else onPanel,Modifier.weight(1f))
            SlotTool("Sonido",if(soundEnabled)Icons.Default.VolumeUp else Icons.Default.VolumeOff,active && images.isNotEmpty() && !failure,onVolume,Modifier.weight(1f).testTag("slot-sound").semantics {stateDescription=if(soundEnabled)"Sonido activado" else "Silenciado"})
            SlotTool("Historial",Icons.Default.History,interactive && savingsMode && savings!=null,onHistory,Modifier.weight(1f))
            SlotTool("Estadísticas",Icons.Default.BarChart,interactive && savingsMode && savings!=null,onStatistics,Modifier.weight(1f))
        }
    }
    }
}

@Composable private fun SlotTool(label:String,icon:ImageVector,enabled:Boolean,action:()->Unit,modifier:Modifier=Modifier) {
    PrimaryControlButton(onClick=action,enabled=enabled,modifier=modifier.heightIn(min=56.dp)) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {Icon(icon,null,Modifier.size(20.dp));Text(label,style=MaterialTheme.typography.labelSmall)}
    }
}
