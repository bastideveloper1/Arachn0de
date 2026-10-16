package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.cleanup.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Opens one confirmation snapshot; no late arrivals are added at confirmation time. */
@Composable internal fun completedCleanupLauncher(project:String,layer:String?=null):()->Unit {
    val app=LocalContext.current.applicationContext as? Arachn0deApplication
    val repository=app?.security?.current?.value?.cleanup
    val scope=rememberCoroutineScope()
    var open by remember(project,layer) {mutableStateOf(false)}
    var preview by remember(project,layer) {mutableStateOf<CompletedCleanup.Preview?>(null)}
    var busy by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(open,project,layer) {
        if(open && repository!=null) try {preview=repository.preview(project,layer)}
        catch(c:CancellationException) {throw c} catch(_:Exception) {error="No se pudo preparar la limpieza.";open=false}
    }
    if(open) ModularPanelDialog(onDismissRequest={if(!busy) open=false},compact=true,plainBody=true,
        title={Text("Limpiar completadas")},text={Column {
            val p=preview
            if(p==null) Text("Calculando tareas…") else {
                Text("${p.candidates.size} tareas elegibles · ${p.excluded} excluidas por restricciones")
                if(p.candidates.isEmpty()) Text("No hay tareas completadas para limpiar")
                else Text("Eliminación definitiva. Se perderá el historial asociado y podrán cambiar los porcentajes de progreso. Se conserva la estructura.")
            }
        }},dismissButton={PrimaryControlButton(onClick={open=false},enabled=!busy) {Text("Cancelar")}},
        confirmButton={PrimaryControlButton(enabled=!busy && preview?.candidates?.isNotEmpty()==true,onClick={
            val p=preview ?: return@PrimaryControlButton
            busy=true;scope.launch {try {
                val count=requireNotNull(repository).clean(p);message="Tareas eliminadas: $count.";open=false
            } catch(c:CancellationException) {throw c} catch(_:Exception) {error="No se pudo completar la limpieza."} finally {busy=false}}
        }) {Text("Limpiar")}})
    (error ?: message)?.let {text->AlertDialog(onDismissRequest={error=null;message=null},text={Text(text)},
        confirmButton={TextButton(onClick={error=null;message=null}) {Text("Cerrar")}})}
    return {preview=null;open=true}
}

@Composable internal fun CleanupSettings(repository:CompletedCleanup) {
    val days by remember(repository) {repository.observeDays()}.collectAsState(initial=null)
    val scope=rememberCoroutineScope()
    var pending by remember {mutableStateOf<Int?>(null)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    fun save(value:Int) {busy=true;scope.launch {try {repository.setDays(value);pending=null}
        catch(c:CancellationException) {throw c} catch(_:Exception) {error="No se pudo guardar la limpieza automática."} finally {busy=false}}}
    Text("Limpieza",style=MaterialTheme.typography.labelLarge)
    Text("Limpieza automática",style=MaterialTheme.typography.titleSmall)
    Text("Desde la última finalización. Se ejecuta cuando Android lo permita y el almacén esté desbloqueado.",style=MaterialTheme.typography.bodySmall)
    CleanupPreference.options.forEach {(value,label)->TextButton(enabled=!busy && days!=null,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),onClick={
        val old=days ?: return@TextButton
        if(value!=old) {if(value>0 && (old==0 || value<old)) pending=value else save(value)}
    }) {RadioButton(selected=days==value,onClick=null);Spacer(Modifier.width(8.dp));Text(label,Modifier.weight(1f))}}
    pending?.let {value->ModularPanelDialog(onDismissRequest={if(!busy) pending=null},compact=true,plainBody=true,
        title={Text("Limpieza automática")},text={Text("Al elegir ${CleanupPreference.options[value]}, algunas tareas existentes podrían cumplir ya el plazo y eliminarse definitivamente en una ejecución posterior, con su historial.")},
        dismissButton={PrimaryControlButton(enabled=!busy,onClick={pending=null}) {Text("Cancelar")}},
        confirmButton={PrimaryControlButton(enabled=!busy,onClick={save(value)}) {Text("Confirmar")}})}
    error?.let {AlertDialog(onDismissRequest={error=null},text={Text(it)},confirmButton={TextButton(onClick={error=null}) {Text("Cerrar")}})}
}
