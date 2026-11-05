package com.r0ybt.arachn0de.wireframe

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable internal fun WireIconPreview(key:String,description:String) {
    val resource=WireIcons[if(key=="calendar") "calendar_days" else key]
    if(resource!=null) Icon(painterResource(resource),description,Modifier.size(24.dp)) else Icon(when(key) {"eye"->Icons.Default.Visibility;"warning"->Icons.Default.Warning;else->Icons.Default.Info},description,Modifier.size(24.dp))
}
@Composable internal fun WireIconSlots(element:WireElement,enabled:Boolean,onOpen:(Int)->Unit) {
    val defaults=wireIconDefaults(element)
    if(defaults.isNotEmpty()) {
        Text("Iconos del componente",style=MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            defaults.forEachIndexed {slot,default->
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    IconButton(onClick={onOpen(slot)},enabled=enabled,modifier=Modifier.testTag("wire-icon-slot:$slot")) {WireIconPreview(element.icons[slot] ?: default,"Editar icono ${slot+1}")}
                    Text("${slot+1}",style=MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
@Composable internal fun WireIconPicker(element:WireElement,slot:Int,enabled:Boolean,onUpdate:(WireElement)->Unit) {
    if(slot !in wireIconDefaults(element).indices) return
    Column(Modifier.padding(8.dp)) {
        Text("Icono ${slot+1}",style=MaterialTheme.typography.titleSmall)
        LazyRow(Modifier.testTag("wire-icon-picker"),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            items(WireIcons.keys.toList(),key={it}) {icon->
                OutlinedCard(onClick={onUpdate(element.copy(icons=element.icons+(slot to icon)))},enabled=enabled,modifier=Modifier.width(76.dp).testTag("wire-icon:$icon")) {
                    Column(Modifier.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        WireIconPreview(icon,"Usar icono $icon")
                        Text(icon.replace('_',' '),maxLines=2,style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        TextButton(onClick={onUpdate(element.copy(icons=element.icons-slot))},enabled=enabled && slot in element.icons) {Text("Restaurar icono original")}
    }
}
@Composable internal fun WireAnnotationControls(document:WireDocument,selection:String?,elementId:String?,show:Boolean,placing:Boolean,enabled:Boolean,limit:Dp,
    onSelect:(String?)->Unit,onShow:(Boolean)->Unit,onPlace:()->Unit,onChange:(WireDocument)->Unit) {
    val annotation=document.annotations.firstOrNull {it.id==selection}
    fun update(next:WireAnnotation) {onChange(document.copy(annotations=document.annotations.map {if(it.id==next.id) next else it}))}
    Column(Modifier.fillMaxWidth().heightIn(max=limit).verticalScroll(rememberScrollState()).padding(horizontal=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(show,onShow,enabled=enabled,modifier=Modifier.testTag("wire-show-annotations"));Text("Mostrar marcadores")
            TextButton(onClick=onPlace,enabled=enabled && document.annotations.size<128) {Text(if(placing) "Cancelar colocación" else "Añadir anotación")}
        }
        if(placing) Text("Toca el lienzo para colocar el marcador.",style=MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            document.annotations.forEachIndexed {index,a->FilterChip(a.id==selection,{onSelect(a.id)},enabled=enabled,label={Text("${index+1}. ${a.title.ifBlank {"Observación"}.take(32)}")},modifier=Modifier.testTag("wire-annotation:${index+1}"))}
        }
        if(annotation!=null) {
            OutlinedTextField(annotation.title,{update(annotation.copy(title=it.take(200)))},label={Text("Título de observación (opcional)")},enabled=enabled,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(annotation.text,{update(annotation.copy(text=it.take(16000)))},label={Text("Observación")},enabled=enabled,modifier=Modifier.fillMaxWidth(),minLines=2)
            Text(if(annotation.elementId==null) "Posición independiente en el lienzo" else "Vinculada a un elemento",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick={val p=annotation.position(document);update(annotation.copy(elementId=null,x=p.x,y=p.y))},enabled=enabled && annotation.elementId!=null) {Text("Desvincular")}
                TextButton(onClick={val p=annotation.position(document);update(annotation.copy(elementId=elementId).at(document,p))},enabled=enabled && elementId!=null && document.elements.any {it.id==elementId}) {Text("Vincular al elemento seleccionado")}
                TextButton(onClick={onChange(document.copy(annotations=document.annotations.filterNot {it.id==annotation.id}));onSelect(null)},enabled=enabled) {Text("Eliminar anotación")}
            }
        } else if(document.annotations.isEmpty()) Text("Sin anotaciones. Son una capa independiente del prototipo.",style=MaterialTheme.typography.bodySmall)
    }
}
