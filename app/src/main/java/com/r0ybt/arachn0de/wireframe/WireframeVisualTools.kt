package com.r0ybt.arachn0de.wireframe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette
import com.r0ybt.arachn0de.ui.theme.AppearancePalette
import kotlin.math.roundToInt

internal enum class WireVisualTool(val label:String,val hint:String) {
    GUIDES("Guías","Muestra líneas al alinear elementos"),
    MARGINS("Márgenes","Márgenes y zonas seguras genéricas; no representan un dispositivo específico"),
    DEVICE("Dispositivo","Muestra el marco del teléfono o pantalla"),
    MAGNET("Imán","Ajusta elementos a las líneas de alineación")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WireVisualToolbar(view:WireViewport,guides:Boolean,margins:Boolean,device:Boolean,magnetic:Boolean,enabled:Boolean,composition:WireComposition=WireComposition.NONE,onCompositionChange:(WireComposition)->Unit={},onToggle:(WireVisualTool)->Unit) {
    val bounds=view.toolbar ?: return
    val density=LocalDensity.current
    val palette=LocalAppearancePalette.current
    @Composable fun button(tool:WireVisualTool) {
        val active=when(tool) {WireVisualTool.GUIDES->guides;WireVisualTool.MARGINS->margins;WireVisualTool.DEVICE->device;WireVisualTool.MAGNET->magnetic}
        var pressed by remember {mutableStateOf(false)}
        val toggle by rememberUpdatedState(onToggle)
        val secondaryStart=with(density) {28.dp.toPx()}
        var help by remember {mutableStateOf(false)}
        var accessibilityHelp by remember {mutableStateOf(false)}
        var menu by remember {mutableStateOf(false)}
        LaunchedEffect(accessibilityHelp) {if(accessibilityHelp) {delay(3000);accessibilityHelp=false}}
        Box(Modifier.size(48.dp).testTag("wire-visual:${tool.name}")
            .clip(RoundedCornerShape(10.dp))
            .background(if(pressed) palette.outline else if(active) palette.selection else Color.Transparent)
            .semantics(mergeDescendants=true) {
                contentDescription=tool.label;role=Role.Checkbox
                toggleableState=if(active) ToggleableState.On else ToggleableState.Off
                stateDescription=if(active) "Activado" else "Desactivado"
                if(!enabled) disabled()
                onClick("Alternar ${tool.label}") {if(enabled) {onToggle(tool);true} else false}
                onLongClick("Ayuda de ${tool.label}") {accessibilityHelp=true;true}
                if(tool==WireVisualTool.GUIDES) customActions=listOf(CustomAccessibilityAction("Configurar guías de composición") {menu=true;true})
            }
            .pointerInput(enabled,tool,secondaryStart) {
                if(enabled) detectTapGestures(
                    onPress={pressed=true;try {tryAwaitRelease()} finally {pressed=false;help=false}},
                    onLongPress={help=true},
                    onTap={point->if(tool==WireVisualTool.GUIDES && point.x>=secondaryStart && point.y>=secondaryStart) menu=true else toggle(tool)})
            },contentAlignment=Alignment.Center) {
                if(tool==WireVisualTool.MAGNET) com.r0ybt.arachn0de.visual.ToolMagnetIcon(if(active) palette.primary else palette.muted) else Icon(when(tool) {WireVisualTool.GUIDES->Icons.Default.GridOn;WireVisualTool.MARGINS->Icons.Default.CropFree;else->Icons.Default.Devices},null,Modifier.size(22.dp),tint=if(active) palette.primary else palette.muted)
                if(tool==WireVisualTool.GUIDES) {
                    Box(Modifier.align(Alignment.BottomEnd).size(20.dp).clip(RoundedCornerShape(topStart=8.dp)).background(palette.raised)
                        .testTag("wire-composition-menu")
                        .semantics(mergeDescendants=true) {contentDescription="Configurar guías de composición";role=Role.Button;onClick {if(enabled) {menu=true;true} else false}},contentAlignment=Alignment.Center) {Icon(Icons.Default.ExpandMore,null,Modifier.size(14.dp))}
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                        DropdownMenuItem(text={Text("Guías inteligentes")},onClick={onToggle(tool)},trailingIcon={if(guides) Icon(Icons.Default.Check,null)})
                        HorizontalDivider()
                        Text("Composición",Modifier.padding(horizontal=12.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall)
                        WireComposition.entries.forEach {mode->DropdownMenuItem(text={Text(mode.label)},onClick={onCompositionChange(mode);menu=false},
                            modifier=Modifier.testTag("wire-composition:${mode.name}").semantics {selected=composition==mode},trailingIcon={if(composition==mode) Icon(Icons.Default.Check,null)})}
                    }
                }
                if(help && pressed || accessibilityHelp) Popup(popupPositionProvider=TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    properties=PopupProperties(focusable=false,dismissOnBackPress=false,dismissOnClickOutside=false)) {
                    Surface(color=palette.raised,contentColor=palette.text,shape=RoundedCornerShape(8.dp),border=BorderStroke(1.dp,palette.outline),modifier=Modifier.widthIn(max=240.dp).testTag("wire-help:${tool.name}")) {
                        Column(Modifier.padding(8.dp)) {Text(tool.label,style=MaterialTheme.typography.labelLarge);Text(tool.hint,style=MaterialTheme.typography.bodySmall)}
                    }
                }
        }
    }

    Surface(color=palette.taskPanel,contentColor=palette.text,shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,palette.outline),
        modifier=Modifier.offset {IntOffset(bounds.left.roundToInt(),bounds.top.roundToInt())}.width(with(density) {bounds.width.toDp()}).height(with(density) {bounds.height.toDp()}).testTag("wireframe-visual-toolbar")) {
        when {
            view.verticalToolbar->Column {WireVisualTool.entries.forEach {button(it)}}
            else->Row(Modifier.horizontalScroll(rememberScrollState())) {WireVisualTool.entries.forEach {button(it)}}
        }
    }
}

/** Decorative bezel, camera and stand are outside the sheet; the screen is never cropped. */
internal fun DrawScope.wireDevice(view:WireViewport,format:WireFormat,palette:AppearancePalette) {
    val f=view.frame;val s=view.sheet;val radius=if(format==WireFormat.MOBILE) f.width*.075f else f.width*.015f
    val bezelBottom=if(format==WireFormat.DESKTOP) s.bottom+60*view.scale else f.bottom
    drawRoundRect(palette.controls,Offset(f.left,f.top),Size(f.width,bezelBottom-f.top),androidx.compose.ui.geometry.CornerRadius(radius))
    drawRoundRect(palette.muted.copy(alpha=.8f),Offset(f.left,f.top),Size(f.width,bezelBottom-f.top),androidx.compose.ui.geometry.CornerRadius(radius),style=Stroke(1.dp.toPx()))
    if(format==WireFormat.MOBILE) {
        val cameraY=(f.top+s.top)/2
        drawRoundRect(palette.background,Offset(s.center.x-55*view.scale,cameraY-10*view.scale),Size(110*view.scale,20*view.scale),androidx.compose.ui.geometry.CornerRadius(10*view.scale))
        drawRoundRect(palette.muted,Offset(s.center.x-100*view.scale,(s.bottom+f.bottom)/2-5*view.scale),Size(200*view.scale,10*view.scale),androidx.compose.ui.geometry.CornerRadius(5*view.scale))
    } else {
        drawCircle(palette.muted,6*view.scale,Offset(s.center.x,(f.top+s.top)/2))
        drawRect(palette.controls,Offset(s.center.x-60*view.scale,bezelBottom),Size(120*view.scale,(f.bottom-bezelBottom)*.75f))
        drawRoundRect(palette.muted,Offset(s.center.x-180*view.scale,f.bottom-18*view.scale),Size(360*view.scale,18*view.scale),androidx.compose.ui.geometry.CornerRadius(8*view.scale))
    }
}

internal fun DrawScope.wireMargins(view:WireViewport,format:WireFormat,color:Color) {
    val x=if(format==WireFormat.MOBILE) 64f/view.width else .05f
    val top=if(format==WireFormat.MOBILE) 120f/view.height else .05f
    val bottom=if(format==WireFormat.MOBILE) 120f/view.height else .05f
    val s=view.sheet;val safeStart=view.screen(Offset(x,top));val safeEnd=view.screen(Offset(1-x,1-bottom))
    val tint=color.copy(alpha=.10f)
    drawRect(tint,s.topLeft,Size(s.width,safeStart.y-s.top))
    drawRect(tint,Offset(s.left,safeEnd.y),Size(s.width,s.bottom-safeEnd.y))
    drawRect(tint,Offset(s.left,safeStart.y),Size(safeStart.x-s.left,safeEnd.y-safeStart.y))
    drawRect(tint,Offset(safeEnd.x,safeStart.y),Size(s.right-safeEnd.x,safeEnd.y-safeStart.y))
    drawRect(color.copy(alpha=.65f),safeStart,Size(safeEnd.x-safeStart.x,safeEnd.y-safeStart.y),style=Stroke(1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),4.dp.toPx()))))
}

internal fun DrawScope.wireMeasurements(view:WireViewport,distances:List<WireSpacing>,color:Color,background:Color) {
    distances.forEach {distance->
        val a=view.screen(distance.from);val b=view.screen(distance.to)
        drawLine(color,a,b,1.dp.toPx())
        val tick=3.dp.toPx();val vertical=kotlin.math.abs(a.x-b.x)<.1f
        val offset=if(vertical) Offset(tick,0f) else Offset(0f,tick)
        drawLine(color,a-offset,a+offset,1.dp.toPx());drawLine(color,b-offset,b+offset,1.dp.toPx())
        val label="${distance.pixels.roundToInt()} px${if(distance.equal) " =" else ""}"
        val textColor=color.toArgb()
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {this.color=textColor;textSize=10.dp.toPx()}
        val center=(a+b)/2f;val width=paint.measureText(label)
        val x=if(vertical) center.x+5.dp.toPx() else center.x-width/2
        val y=if(vertical) center.y else center.y-5.dp.toPx()
        drawRect(background.copy(alpha=.9f),Offset(x-2.dp.toPx(),y+paint.ascent()),Size(width+4.dp.toPx(),paint.descent()-paint.ascent()))
        drawContext.canvas.nativeCanvas.drawText(label,x,y,paint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WireVisualBarButton(visible:Boolean,enabled:Boolean,onClick:()->Unit) {
    val label=if(visible) "Ocultar barra visual" else "Mostrar barra visual"
    TooltipBox(positionProvider=TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),tooltip={PlainTooltip {Text(label)}},state=rememberTooltipState()) {
        IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.testTag("wireframe-visual-bar-toggle")) {
            Icon(androidx.compose.material.icons.Icons.Default.GridOn,label,Modifier.size(20.dp),tint=if(visible) LocalAppearancePalette.current.primary else LocalAppearancePalette.current.muted)
        }
    }
}

/** Composition overlays stay in the editor layer and never enter WireframeRenderer. */
internal fun DrawScope.wireComposition(view:WireViewport,mode:WireComposition,background:Color) {
    val color=if(background.luminance()>.45f) Color.Black.copy(alpha=.22f) else Color.White.copy(alpha=.28f)
    wireCompositionLines(mode,view.width,view.height).forEach {line->
        drawLine(color,view.screen(line.first),view.screen(line.second),strokeWidth=.75.dp.toPx())
    }
}
