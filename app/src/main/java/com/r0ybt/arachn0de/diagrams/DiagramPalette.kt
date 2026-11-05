package com.r0ybt.arachn0de.diagrams

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils

/** Shared picker; retained entry point for existing Diagramas callers. */
@Composable internal fun DiagramPalette(initial:Int,favorites:List<Int>,favorite:(Int)->Unit,dismiss:()->Unit,confirm:(Int)->Unit)=com.r0ybt.arachn0de.visual.VisualColorPicker(initial,favorites,favorite,dismiss,confirm)
@Composable internal fun DiagramGradientDialog(initial:DiagramGradient?,favorites:List<Int>,favorite:(Int)->Unit,dismiss:()->Unit,confirm:(DiagramGradient?)->Unit) {
    var colors by remember {mutableStateOf(initial?.colors ?: listOf(0xff0d131b.toInt(),0xff23628d.toInt()))}
    var angle by remember {mutableFloatStateOf(initial?.angle ?: 90f)}
    var editing by remember {mutableStateOf<Int?>(null)}
    AlertDialog(onDismissRequest=dismiss,modifier=Modifier.widthIn(max=560.dp).fillMaxWidth(.92f),properties=DialogProperties(usePlatformDefaultWidth=false),title={Text("Degradado lineal")},text={Column(Modifier.fillMaxWidth().heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {drawContext.canvas.nativeCanvas.drawRect(0f,0f,size.width,size.height,android.graphics.Paint().apply {shader=diagramShader(DiagramGradient(colors,angle),android.graphics.RectF(0f,0f,size.width,size.height))})}
        DiagramToolRow {colors.forEachIndexed {i,c->DiagramSwatch("Color ${i+1}",c) {editing=i}}}
        Row {TextButton(enabled=colors.size<8,onClick={colors=colors+colors.last()}) {Text("Añadir color")};TextButton(enabled=colors.size>2,onClick={colors=colors.dropLast(1)}) {Text("Quitar último")}}
        Text("Dirección · ${angle.toInt()}°");Slider(angle,{angle=it},modifier=Modifier.fillMaxWidth().height(48.dp),valueRange=0f..360f)
        DiagramToolRow {listOf("Azul nocturno" to 0xff23628d,"Cian eléctrico" to 0xff43e8ff,"Verde oscuro" to 0xff176a49,"Ámbar suave" to 0xffffbd59,"Violeta profundo" to 0xff673da0).forEach {(name,c)->TextButton(onClick={colors=listOf(DIAGRAM_COAL,c.toInt())}) {Text(name)}}}
        TextButton(onClick={confirm(null)}) {Text("Quitar degradado")}
    }},confirmButton={TextButton(onClick={confirm(DiagramGradient(colors,angle))}) {Text("Aplicar degradado")}},dismissButton={TextButton(onClick=dismiss) {Text("Cancelar")}})
    editing?.let {i->DiagramPalette(colors[i],favorites,favorite,{editing=null}) {c->colors=colors.toMutableList().also {it[i]=c};editing=null}}
}

@Composable private fun DiagramSwatch(label:String,color:Int,click:()->Unit) {
    OutlinedButton(onClick=click,contentPadding=PaddingValues(8.dp),modifier=Modifier.size(48.dp).semantics {contentDescription=label}) {Box(Modifier.size(28.dp).background(Color(color)))}
}
