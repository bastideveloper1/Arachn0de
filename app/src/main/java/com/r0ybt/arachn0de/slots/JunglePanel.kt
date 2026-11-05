package com.r0ybt.arachn0de.slots

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.BottomModulesDialog
import com.r0ybt.arachn0de.ui.PrimaryControlButton
import kotlin.math.*

internal val JungleGold=Color(0xffeeeeee)

/** Existing payment and confirmation actions, presented in the application's compact surface. */
@Composable internal fun JunglePanel(title:String,onDismiss:()->Unit,onConfirm:()->Unit,confirmLabel:String="Cerrar",enabled:Boolean=true,
    secondaryLabel:String?=null,onSecondary:()->Unit=onDismiss,secondaryEnabled:Boolean=enabled,showClose:Boolean=false,celebrate:Boolean=false,showConfirm:Boolean=true,depositFrame:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    BottomModulesDialog(onDismiss) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(),contentAlignment=Alignment.BottomCenter) {
            Box(Modifier.matchParentSize().pointerInput(onDismiss) {detectTapGestures {onDismiss()}})
            Surface(Modifier.pointerInput(Unit) {detectTapGestures {}}.widthIn(max=600.dp).fillMaxWidth().heightIn(max=560.dp).testTag("jungle-panel"),
                shape=RoundedCornerShape(16.dp),color=Color(0xff202124),contentColor=JungleGold,border=BorderStroke(1.dp,Color(0xff414347))) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                        if(showClose)IconButton(onClick=onDismiss,modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Close,"Cerrar")}
                    }
                    Column(Modifier.weight(1f,fill=false).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp),content=content)
                    if(showConfirm)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        if(secondaryLabel!=null)PrimaryControlButton(onClick=onSecondary,enabled=secondaryEnabled,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text(secondaryLabel)}
                        PrimaryControlButton(onClick=onConfirm,enabled=enabled,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text(confirmLabel)}
                    }
                }
            }
            if(celebrate)JungleConfetti(Modifier.matchParentSize())
        }
    }
}

/** A bounded, presentation-only burst. It never consumes credits or changes a prize. */
@Composable internal fun JungleConfetti(modifier:Modifier=Modifier) {
    val progress=remember {Animatable(0f)}
    LaunchedEffect(Unit) {progress.animateTo(1f,tween(2300))}
    Canvas(modifier) {
        val t=progress.value
        if(t>0 && t<1)repeat(48) {i->
            val angle=i*2.399963;val speed=.35f+(i%7)*.065f
            val x=size.width/2+cos(angle).toFloat()*size.width*speed*t
            val y=size.height*.3f+sin(angle).toFloat()*size.height*.4f*t+size.height*.7f*t*t
            val color=listOf(JungleGold,Color.LightGray,Color.Gray)[i%3].copy(alpha=(1-t).coerceIn(0f,1f))
            drawCircle(color,(2+i%3).dp.toPx(),androidx.compose.ui.geometry.Offset(x,y))
        }
    }
}
