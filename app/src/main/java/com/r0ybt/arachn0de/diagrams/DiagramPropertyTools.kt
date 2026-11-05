package com.r0ybt.arachn0de.diagrams

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

@Composable internal fun DiagramNodeTools(n:DiagramNode,enabled:Boolean,open:(String)->Unit,duplicate:()->Unit,remove:()->Unit,replace:()->Unit,connect:()->Unit={},expanded:Boolean=false) {
    DiagramToolRow {
        DiagramTool(if(n.shape==DiagramShape.TABLE)"Editar tabla" else if(n.shape==DiagramShape.IMAGE)"Etiqueta" else "Editar texto","text",enabled=enabled) {open("node:text")}
        if(n.shape==DiagramShape.IMAGE)DiagramTool("Reemplazar imagen","replace",enabled=enabled,onClick=replace)
        else {
            if(n.shape!=DiagramShape.TEXT)DiagramTool("Fondo del elemento","fill",enabled=enabled) {open("node:fill")}
            if(n.shape!=DiagramShape.TEXT)DiagramTool("Borde","border",enabled=enabled) {open("node:border")}
        }
        DiagramTool("Color del texto","color",enabled=enabled) {open("node:ink")}
        if(!expanded)DiagramTool("Propiedades","settings",enabled=enabled) {open("node:properties")}
        DiagramTool("Duplicar","duplicate",enabled=enabled,onClick=duplicate)
        DiagramTool("Duplicar y conectar","connect",enabled=enabled,onClick=connect)
        DiagramTool("Dirección de duplicación","arrow:both",enabled=enabled) {open("duplicate:direction")}
    }
}
@Composable internal fun DiagramEdgeTools(enabled:Boolean,open:(String)->Unit,remove:()->Unit) {
    DiagramToolRow {
        DiagramTool("Trayectoria","curve",enabled=enabled) {open("edge:route")}
        DiagramTool("Trazo","dashed",enabled=enabled) {open("edge:stroke")}
        DiagramTool("Flechas","arrow:end",enabled=enabled) {open("edge:arrows")}
        DiagramTool("Color","color",enabled=enabled) {open("edge:color")}
        DiagramTool("Grosor","border",enabled=enabled) {open("edge:width")}
        DiagramTool("Etiqueta de conexión","label",enabled=enabled) {open("edge:text")}
        DiagramTool("Puntos de recorrido","orthogonal",enabled=enabled) {open("edge:points")}
        DiagramTool("Animar ▶","play",enabled=enabled) {open("edge:flow")}
    }
}
@Composable internal fun DiagramNodeDetail(key:String,n:DiagramNode,enabled:Boolean,change:(DiagramNode)->Unit) {
    when(key) {
        "node:text"->if(n.shape==DiagramShape.TABLE)DiagramTableDetail(n,enabled,change) else OutlinedTextField(n.text,{if(it.length<=4000)change(n.copy(text=it))},enabled=enabled,label={Text("Texto / etiqueta")},maxLines=3,modifier=Modifier.fillMaxWidth())
        "node:fill"->DiagramColors(n.fill) {change(n.copy(fill=it,gradient=null))}
        "node:ink"->DiagramColors(n.ink) {change(n.copy(ink=it))}
        "node:border"->{DiagramColors(n.border) {change(n.copy(border=it))};GeometryStep("Borde",n.stroke,0f,20f,1f) {change(n.copy(stroke=it))};if(n.shape==DiagramShape.ROUND)GeometryStep("Radio",n.radius,0f,200f,2f) {change(n.copy(radius=it))}}

    }
}
@Composable internal fun DiagramNodeProperties(n:DiagramNode,enabled:Boolean,change:(DiagramNode)->Unit) {
    var precise by rememberSaveable(n.id) {mutableStateOf(false)}
    Row {Switch(n.lockAspect,{change(n.copy(lockAspect=it))},enabled=enabled,modifier=Modifier.semantics {contentDescription="Mantener proporciones"});Text("Mantener proporciones")}
    Row {Switch(n.centeredResize,{change(n.copy(centeredResize=it))},enabled=enabled,modifier=Modifier.semantics {contentDescription="Desde el centro"});Text("Desde el centro")}
    // Scaling currently changes text size; images without a label have no scalable text.
    if(n.shape!=DiagramShape.IMAGE||n.text.isNotBlank()) {
        DiagramToolRow {DiagramResizeContent.entries.forEach {value->TextButton(enabled=enabled,onClick={change(n.copy(resizeContent=value))}) {Text((if(n.resizeContent==value)"✓ " else "")+if(value==DiagramResizeContent.CONTAINER)"Ajustar contenedor" else "Escalar contenido")}}}
        GeometryStep("Tipografía",n.font,12f,192f,2f,enabled) {change(n.copy(font=it))}
    }
    TextButton(enabled=enabled,onClick={precise=!precise}) {Text(if(precise)"Ajustes precisos ▴" else "Ajustes precisos ▾")}
    if(precise) {
        GeometryStep("X",n.x,0f,1-n.w,enabled=enabled) {change(n.copy(x=it))}
        GeometryStep("Y",n.y,0f,1-n.h,enabled=enabled) {change(n.copy(y=it))}
        GeometryStep("Ancho",n.w,minOf(n.w,.025f),1-n.x,enabled=enabled) {change(n.copy(w=it))}
        GeometryStep("Alto",n.h,minOf(n.h,.025f),1-n.y,enabled=enabled) {change(n.copy(h=it))}
    }
}
@Composable internal fun DiagramTableDetail(n:DiagramNode,enabled:Boolean,change:(DiagramNode)->Unit) {
    OutlinedTextField(n.text,{if(it.length<=100)change(n.copy(text=it))},enabled=enabled,label={Text("Encabezado de tabla")},singleLine=true,modifier=Modifier.fillMaxWidth())
    n.fields.forEachIndexed {index,field->
        fun update(value:DiagramField) {change(n.copy(fields=n.fields.map {if(it.id==field.id)value else it}))}
        Text("Campo ${index+1}")
        OutlinedTextField(field.name,{if(it.length<=100)update(field.copy(name=it))},enabled=enabled,label={Text("Nombre de campo ${index+1}")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(field.type,{if(it.length<=64)update(field.copy(type=it))},enabled=enabled,label={Text("Tipo opcional ${index+1}")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(field.primaryKey,{update(field.copy(primaryKey=it))},enabled=enabled,modifier=Modifier.semantics {contentDescription="PK campo ${index+1}"});Text("PK")
            Checkbox(field.foreignKey,{update(field.copy(foreignKey=it))},enabled=enabled,modifier=Modifier.semantics {contentDescription="FK campo ${index+1}"});Text("FK")
            TextButton(enabled=enabled,onClick={change(n.copy(fields=n.fields.filterNot {it.id==field.id}))}) {Text("Eliminar campo ${index+1}")}
        }
    }
    TextButton(enabled=enabled&&n.fields.size<32,onClick={change(n.copy(fields=n.fields+DiagramField()))}) {Text("Añadir campo")}
}
@Composable internal fun DiagramEdgeDetail(key:String,e:DiagramEdge,enabled:Boolean,change:(DiagramEdge)->Unit,open:(String)->Unit,importImage:()->Unit,preview:()->Unit,lastFlow:DiagramFlow?=null) {
    when(key) {
        "edge:route"->DiagramToolRow {DiagramRoute.entries.forEach {route->DiagramTool(route.label,route.name.lowercase(),e.route==route,enabled) {change(e.copy(route=route))}}}
        "edge:stroke"->DiagramToolRow {DiagramStroke.entries.forEach {stroke->DiagramTool(when(stroke) {DiagramStroke.SOLID->"Continua";DiagramStroke.DASHED->"Discontinua";DiagramStroke.DOTTED->"Punteada"},stroke.name.lowercase(),e.stroke==stroke,enabled) {change(e.copy(stroke=stroke))}}}
        "edge:arrows"->DiagramToolRow {listOf("Sin flechas" to "none","Flecha inicial" to "start","Flecha final" to "end","Ambas flechas" to "both").forEach {(label,kind)->
            val start=kind=="start"||kind=="both";val end=kind=="end"||kind=="both"
            DiagramTool(label,"arrow:$kind",e.startArrow==start&&e.endArrow==end,enabled) {change(e.copy(startArrow=start,endArrow=end))}
        }}
        "edge:color"->DiagramColors(e.color) {change(e.copy(color=it))}
        "edge:width"->GeometryStep("Grosor",e.width,1f,20f,1f) {change(e.copy(width=it))}
        "edge:text"->OutlinedTextField(e.label,{if(it.length<=1000)change(e.copy(label=it))},enabled=enabled,label={Text("Etiqueta de conexión")},maxLines=2,modifier=Modifier.fillMaxWidth())
        "edge:points"->DiagramToolRow {
            TextButton(enabled=enabled&&e.points.size<8,onClick={change(e.copy(points=e.points+DPoint(.5f,.5f)))}) {Text("+ Punto")}
            TextButton(enabled=enabled&&e.points.isNotEmpty(),onClick={change(e.copy(points=e.points.dropLast(1)))}) {Text("− Punto")}
        }
        "edge:flow"->{
            TextButton(enabled=enabled&&lastFlow!=null,onClick={lastFlow?.let {change(e.copy(flow=it))}}) {Text("Usar última configuración")}
            Row {Switch(e.flow.enabled,{change(e.copy(flow=e.flow.copy(enabled=it)))},enabled=enabled,modifier=Modifier.semantics {contentDescription="Flujo animado"});Text("Flujo animado")}
            DiagramToolRow {
                DiagramTool("Estilo y brillo","color",enabled=enabled) {open("flow:appearance")}
                DiagramTool("Objeto móvil","image",enabled=enabled) {open("flow:object")}
                DiagramTool("Dirección","arrow:both",enabled=enabled) {open("flow:direction")}
                DiagramTool("Velocidad y cantidad","settings",enabled=enabled) {open("flow:timing")}
                DiagramTool("Importar objeto móvil","import",enabled=enabled,onClick=importImage)
                DiagramTool("Probar conexión","play",enabled=enabled&&e.flow.enabled,onClick=preview)
            }
        }
        "flow:appearance"->{
            DiagramToolRow {DiagramParticleStyle.entries.forEach {style->TextButton(onClick={change(e.copy(flow=e.flow.copy(particleStyle=style)))}) {Text((if(e.flow.particleStyle==style)"✓ " else "")+when(style) {DiagramParticleStyle.NORMAL->"Normal";DiagramParticleStyle.LUMINOUS->"Luminoso";DiagramParticleStyle.PULSE->"Pulso"})}}}
            GeometryStep("Intensidad",e.flow.glow,0f,1f,.1f) {change(e.copy(flow=e.flow.copy(glow=it)))}
            Row {Switch(e.flow.imageHalo,{change(e.copy(flow=e.flow.copy(imageHalo=it)))});Text("Halo en imagen")}
        }
        "flow:object"->{
            DiagramToolRow {FlowObject.entries.filter {it!=FlowObject.IMAGE||e.flow.asset!=null}.forEach {type->DiagramTool(type.label,when(type) {FlowObject.DOT->"particle";FlowObject.SPHERE->"sphere";FlowObject.ICON->"file";FlowObject.IMAGE->"image"},type==e.flow.objectType,enabled) {change(e.copy(flow=e.flow.copy(objectType=type)))}}}
            DiagramToolRow {DiagramTool("Imagen propia","import",enabled=enabled,onClick=importImage)}
        }
        "flow:direction"->DiagramToolRow {FlowDirection.entries.forEach {direction->DiagramTool(direction.label,when(direction) {FlowDirection.FORWARD->"arrow:end";FlowDirection.REVERSE->"arrow:start";FlowDirection.BOUNCE->"arrow:both"},direction==e.flow.direction,enabled) {change(e.copy(flow=e.flow.copy(direction=direction)))}}}
        "flow:timing"->{
            DiagramToolRow {listOf("½×" to .5f,"1×" to 1f,"2×" to 2f).forEach {(label,speed)->TextButton(enabled=enabled,onClick={change(e.copy(flow=e.flow.copy(speed=speed)))}) {Text((if(speed==e.flow.speed)"✓ " else "")+label)}}}
            GeometryStep("Cantidad",e.flow.count.toFloat(),1f,8f,1f) {change(e.copy(flow=e.flow.copy(count=it.toInt())))}
            GeometryStep("Tamaño",e.flow.size,6f,100f,2f) {change(e.copy(flow=e.flow.copy(size=it)))}
            Row {Switch(e.flow.loop,{change(e.copy(flow=e.flow.copy(loop=it)))},enabled=enabled,modifier=Modifier.semantics {contentDescription="Repetir flujo"});Text("Repetir")}
        }
    }
}
@Composable internal fun GeometryStep(label:String,value:Float,min:Float,max:Float,step:Float=.01f,enabled:Boolean=true,change:(Float)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Text("$label · ${if(step<1)"${(value*100).toInt()} %" else value.toInt()}",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
        IconStep("Reducir $label",enabled&&value>min) {change((value-step).coerceIn(min,max))}
        IconStep("Aumentar $label",enabled&&value<max) {change((value+step).coerceIn(min,max))}
    }
}
@Composable private fun IconStep(label:String,enabled:Boolean,click:()->Unit) {
    androidx.compose.material3.TextButton(onClick=click,enabled=enabled,modifier=Modifier.semanticsLabel(label)) {Text(if(label.startsWith("Reducir"))"−" else "+")}
}
private fun Modifier.semanticsLabel(label:String)=this.then(Modifier.semantics {contentDescription=label})
