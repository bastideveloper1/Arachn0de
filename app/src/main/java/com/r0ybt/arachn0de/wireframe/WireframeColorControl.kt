package com.r0ybt.arachn0de.wireframe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.ModularPanelDialog
import com.r0ybt.arachn0de.ui.theme.LocalAppearancePalette
import kotlin.math.roundToInt

internal val WireColorPalette=listOf(
    "Blanco" to 0xffffffffL,"Gris" to 0xff9e9e9eL,"Negro" to 0xff000000L,"Rojo" to 0xffe57373L,
    "Naranja" to 0xffffb74dL,"Amarillo" to 0xffffd54fL,"Verde" to 0xff81c784L,"Azul" to 0xff64b5f6L,
    "Violeta" to 0xffba68c8L,"Rosa" to 0xfff06292L,"Cian" to 0xff4dd0e1L,"Transparente" to 0x00000000L)
internal val WireBackgroundPalette=listOf("Blanco" to 0xffffffffL,"Gris" to 0xffeeeeeeL,"Oscuro" to 0xff202224L)+WireColorPalette.filter {it.first !in setOf("Blanco","Gris")}
internal fun wireParseColor(text:String):Long?=text.takeIf {it.length==8 && it.all {char->char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'}}?.toLongOrNull(16)
internal fun wireColorChannel(color:Long,shift:Int,value:Int):Long =
    (color and (0xffL shl shift).inv()) or (value.coerceIn(0,255).toLong() shl shift)

@Composable private fun WireColorSample(value:Long,modifier:Modifier=Modifier) {
    Canvas(modifier) {
        val side=size.minDimension/4
        if(side<=0) return@Canvas
        for(y in 0..3) for(x in 0..kotlin.math.ceil(size.width/side).toInt()) drawRect(if((x+y)%2==0) Color.White else Color(0xffbbbbbb),Offset(x*side,y*side),Size(side,side))
        drawRect(Color(value))
    }
}

/** Presets commit immediately. The custom draft commits once, so sliders do not flood undo history. */
@Composable internal fun WireColorControl(label:String,value:Long,enabled:Boolean=true,presets:List<Pair<String,Long>> = WireColorPalette,apply:(Long)->Unit) {
    var editing by rememberSaveable {mutableStateOf(false)}
    val palette=LocalAppearancePalette.current
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
            WireColorSample(value,Modifier.size(24.dp).border(1.dp,palette.outline).semantics {contentDescription="Color seleccionado de $label: #%08X".format(value)})
            TextButton(onClick={editing=true},enabled=enabled,modifier=Modifier.testTag("wire-color-custom:$label")) {Text("Personalizado",style=MaterialTheme.typography.labelSmall)}
        }
        LazyRow(horizontalArrangement=Arrangement.spacedBy(2.dp),modifier=Modifier.testTag("wire-color-palette:$label")) {
            items(presets,key={it.first}) {(name,color)->
                Box(Modifier.size(40.dp).selectable(selected=value==color,enabled=enabled,role=Role.RadioButton,onClick={if(value!=color) apply(color)})
                    .testTag("wire-color:$label:$name").semantics {contentDescription="$label: $name"}.padding(5.dp)
                    .border(if(value==color) 2.dp else 1.dp,if(value==color) palette.primary else palette.outline,RoundedCornerShape(4.dp))) {
                    WireColorSample(color,Modifier.fillMaxSize().padding(3.dp))
                }
            }
        }
    }
    if(editing) WireCustomColor(label,value,{editing=false}) {if(it!=value) apply(it);editing=false}
}

@Composable private fun WireCustomColor(label:String,value:Long,onCancel:()->Unit,onApply:(Long)->Unit) {
    var draft by rememberSaveable {mutableStateOf(value)}
    var hex by rememberSaveable {mutableStateOf("%08X".format(value))}
    val valid=wireParseColor(hex)!=null
    fun set(next:Long) {draft=next;hex="%08X".format(next)}
    ModularPanelDialog(onDismissRequest=onCancel,compact=true,title={Text("Color personalizado · $label")},text={Column {
        WireColorSample(draft,Modifier.fillMaxWidth().height(40.dp).semantics {contentDescription="Vista previa del color #%08X".format(draft)})
        listOf("Rojo" to 16,"Verde" to 8,"Azul" to 0,"Opacidad" to 24).forEach {(channel,shift)->
            val component=((draft shr shift) and 255).toInt()
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(channel,Modifier.width(76.dp),style=MaterialTheme.typography.labelSmall)
                Slider(component.toFloat(),{set(wireColorChannel(draft,shift,it.roundToInt()))},valueRange=0f..255f,steps=254,
                    modifier=Modifier.weight(1f).semantics {contentDescription="$label: $channel"}.testTag("wire-color-channel:$channel"))
                Text(if(shift==24) "${(component*100f/255).roundToInt()}%" else "$component",Modifier.width(40.dp),style=MaterialTheme.typography.labelSmall)
            }
        }
        OutlinedTextField(hex,{raw->hex=raw.take(8);wireParseColor(hex)?.let {draft=it}},
            label={Text("$label · AARRGGBB")},singleLine=true,isError=!valid,modifier=Modifier.fillMaxWidth())
    }},confirmButton={TextButton(onClick={onApply(draft)},enabled=valid) {Text("Aplicar")}},dismissButton={TextButton(onClick=onCancel) {Text("Cancelar")}})
}
