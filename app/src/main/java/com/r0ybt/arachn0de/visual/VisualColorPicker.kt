package com.r0ybt.arachn0de.visual

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils

/** Edits stay local until confirmation; favorites are explicit independent library writes. */
@Composable internal fun VisualColorPicker(initial:Int,favorites:List<Int>,favorite:(Int)->Unit,dismiss:()->Unit,confirm:(Int)->Unit) {
    val initialHsl=remember {FloatArray(3).also {ColorUtils.colorToHSL(initial,it)}}
    var hue by rememberSaveable {mutableFloatStateOf(initialHsl[0])}
    var saturation by rememberSaveable {mutableFloatStateOf(initialHsl[1])}
    var lightness by rememberSaveable {mutableFloatStateOf(initialHsl[2])}
    var color by rememberSaveable {mutableIntStateOf(initial)}
    var hex by rememberSaveable {mutableStateOf("%08X".format(initial))}
    fun use(value:Int) {color=value;hex="%08X".format(value);val h=FloatArray(3);ColorUtils.colorToHSL(value,h);hue=h[0];saturation=h[1];lightness=h[2]}
    fun hsl() {color=ColorUtils.HSLToColor(floatArrayOf(hue,saturation,lightness));hex="%08X".format(color)}
    AlertDialog(onDismissRequest=dismiss,modifier=Modifier.widthIn(max=560.dp).fillMaxWidth(.92f),properties=DialogProperties(usePlatformDefaultWidth=false),title={Text("Color personalizado")},text={Column(Modifier.fillMaxWidth().heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(40.dp).background(Color(color)))
        Text("Tono");Slider(hue,{hue=it;hsl()},modifier=Modifier.fillMaxWidth().height(48.dp),valueRange=0f..360f)
        Text("Saturación");Slider(saturation,{saturation=it;hsl()},modifier=Modifier.fillMaxWidth().height(48.dp))
        Text("Luminosidad");Slider(lightness,{lightness=it;hsl()},modifier=Modifier.fillMaxWidth().height(48.dp))
        OutlinedTextField(hex,{value->hex=value.removePrefix("#").take(8);if(hex.length==6||hex.length==8)hex.toLongOrNull(16)?.let {parsed->color=if(hex.length==6)(parsed or 0xff000000).toInt() else parsed.toInt();val h=FloatArray(3);ColorUtils.colorToHSL(color,h);hue=h[0];saturation=h[1];lightness=h[2]}},label={Text("Hex · RRGGBB / AARRGGBB")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {listOf(0xff43e8ff,0xff45ff8b,0xff358dff,0xffffbd59,0xffab73ff,0xffff5252).forEach {v->ColorSwatch("Color %08X".format(v),v.toInt()) {use(v.toInt())}}}
        Text("Favoritos · ${favorites.size}/32")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {favorites.forEach {v->ColorSwatch("Favorito %08X".format(v),v) {use(v)}}}
        TextButton(enabled=favorites.size<32&&color !in favorites,onClick={favorite(color)}) {Text("Guardar color favorito")}
    }},confirmButton={TextButton(enabled=hex.length in listOf(6,8)&&hex.toLongOrNull(16)!=null,onClick={confirm(color)}) {Text("Aplicar color")}},dismissButton={TextButton(onClick=dismiss) {Text("Cancelar")}})
}
@Composable private fun ColorSwatch(label:String,color:Int,click:()->Unit) {
    OutlinedButton(onClick=click,contentPadding=PaddingValues(8.dp),modifier=Modifier.size(48.dp).semantics {contentDescription=label}) {Box(Modifier.size(28.dp).background(Color(color)))}
}
