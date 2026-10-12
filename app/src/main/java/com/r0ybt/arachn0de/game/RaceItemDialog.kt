package com.r0ybt.arachn0de.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp

@Composable internal fun ItemDialog(s: RaceSession,slot: Int,art: Map<String,ImageBitmap>,onCancel: ()->Unit,
    onConfirm: (Int?,List<Int>,Map<Int,Int>)->Unit) {
    val item=s.actor.inventory.getOrNull(slot)?.item ?: return
    var cells by remember(s.current,slot) { mutableStateOf(emptyList<Int>()) }
    var target by remember(s.current,slot) { mutableStateOf<Int?>(null) }
    var choices by remember(s.current,slot) { mutableStateOf(emptyMap<Int,Int>()) }
    val b=s.balance
    val valid=when(item) {
        RaceItem.CALTROPS,RaceItem.DYNAMITE -> s.track.reachable(s.actor.tile,b.placement)
        RaceItem.EYE -> s.track.reachable(s.actor.tile,b.eye)+s.track.reachable(s.actor.tile,b.eye,true)
        else -> emptySet()
    }
    val rivals=if(item==RaceItem.WASPS)s.visible(s.actor.id).filter { it.id!=s.actor.id && !it.invisible && it.tile in s.track.reachable(s.actor.tile,b.wasps) } else emptyList()
    val maxCells=if(item==RaceItem.CALTROPS)minOf(2,valid.size) else 1
    val preview=when(item) { RaceItem.ROCK -> s.track.path(s.actor.tile,b.rock,choices).toSet(); RaceItem.DYNAMITE,RaceItem.EYE -> cells.firstOrNull()?.let { s.track.zone(it,if(item==RaceItem.EYE)b.eyeRadius else b.blastRadius,choices) }.orEmpty(); else -> emptySet() }
    val ready=when(item) {
        RaceItem.WASPS -> target!=null
        RaceItem.CALTROPS -> cells.size==maxCells && maxCells>0
        RaceItem.DYNAMITE,RaceItem.EYE -> cells.size==1
        else -> true
    }
    AlertDialog(onDismissRequest=onCancel,title={Text("${item.label} · −1 casilla este turno")},text={Column(Modifier.heightIn(max=550.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row { RaceImage(art,item.file,item.label,Modifier.size(60.dp)); Text(item.help,Modifier.weight(1f)) }
        Text("Confirmar consume el objeto y reduce el dado en una casilla, mínimo cero. Cancelar no consume nada.")
        if(item==RaceItem.CALTROPS && valid.size==1) Text("Solo queda una casilla válida; puedes colocar una trampa.")
        if(item==RaceItem.WASPS) {
            if(rivals.isEmpty())Text("No hay rivales visibles dentro del alcance. Conservas el objeto.")
            rivals.forEach { p -> Row { RadioButton(target==p.id,onClick={target=p.id}); RaceImage(art,p.character.file,p.character.label,Modifier.size(35.dp)); TextButton(onClick={target=p.id}) { Text("#${p.id+1} ${p.character.label}") } } }
        }
        if(item==RaceItem.ROCK || item==RaceItem.DYNAMITE || item==RaceItem.EYE) {
            Text("Ramas del proyectil y de la zona (vista previa exacta):",style=MaterialTheme.typography.labelMedium)
            s.track.tiles.filter { it.next.size>1 }.forEach { tile ->
                Text("Bifurcación ${tile.id+1}")
                Row { tile.next.forEachIndexed { i,to -> FilterChip(selected=(choices[tile.id] ?: tile.next.first())==to,onClick={choices=choices+(tile.id to to)},label={Text(if(i==0)"Directa" else "+2 · caja")}) } }
            }
            s.track.tiles.filter { t -> s.track.tiles.count { t.id in it.next }>1 }.forEach { tile ->
                val previous=s.track.tiles.filter { tile.id in it.next }
                Text("Zona hacia atrás en unión ${tile.id+1}",style=MaterialTheme.typography.labelSmall)
                Row { previous.forEachIndexed { i,from -> FilterChip(selected=(choices[tile.id] ?: previous.first().id)==from.id,onClick={choices=choices+(tile.id to from.id)},label={Text(if(i==0)"Directa" else "Desvío")}) } }
            }
        }
        if(valid.isNotEmpty() || preview.isNotEmpty()) {
            RaceBoard(s,s.actor.id,art,Modifier.fillMaxWidth().height(260.dp),valid,cells.toSet(),preview,onSelect={ id -> if(id in valid)cells=if(id in cells)cells-id else (cells+id).takeLast(maxCells) })
            if(cells.isNotEmpty())Text("Casillas elegidas: ${cells.joinToString { (it+1).toString() }}")
            if(preview.isNotEmpty())Text("Zona: ${preview.sorted().joinToString { (it+1).toString() }}",style=MaterialTheme.typography.labelSmall)
        }
        if(item==RaceItem.ROCK)Text("Disparo sin seleccionar rival. Sin impacto válido, no se consume.")
    }},dismissButton={TextButton(onClick=onCancel) { Text("Cancelar") }},confirmButton={TextButton(onClick={onConfirm(target,cells,choices)},enabled=ready) { Text("Confirmar · −1 casilla") }})
}
