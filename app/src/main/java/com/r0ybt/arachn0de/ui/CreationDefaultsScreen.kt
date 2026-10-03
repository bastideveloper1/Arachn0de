package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.r0ybt.arachn0de.data.local.CreationDefaultsEntity
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.defaults.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.OperationState
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

/** Transient editor state, separate from the permanent Room configuration. */
internal val CreationDefaultsSaver = listSaver<CreationDefaults, String>(save = {
    val r = CreationDefaultsCodec.encode(DefaultsScope.Global,it)
    listOf(r.purpose.orEmpty(),r.obligation?.toString().orEmpty(),r.currency.orEmpty(),r.priority.orEmpty(),
        r.tagsOverride.toString(),r.peopleOverride.toString(),r.startRule.orEmpty(),r.startNumber?.toString().orEmpty(),
        r.startMinute?.toString().orEmpty(),r.dueRule.orEmpty(),r.dueNumber?.toString().orEmpty(),r.dueMinute?.toString().orEmpty(),
        ((it.tags as? DefaultValue.Own)?.value?.size ?: 0).toString()) +
        (it.tags as? DefaultValue.Own)?.value.orEmpty().sorted() + (it.people as? DefaultValue.Own)?.value.orEmpty().sorted()
}, restore = {
    val tagsEnd = 13 + it[12].toInt()
    CreationDefaultsCodec.decode(CreationDefaultsEntity("G",null,null,it[0].ifEmpty { null },it[1].ifEmpty { null }?.toBooleanStrict(),
        it[2].ifEmpty { null },it[3].ifEmpty { null },it[4].toBooleanStrict(),it[5].toBooleanStrict(),
        it[6].ifEmpty { null },it[7].toIntOrNull(),it[8].toIntOrNull(),it[9].ifEmpty { null },it[10].toIntOrNull(),it[11].toIntOrNull()),
        it.subList(13,tagsEnd).toSet(),it.subList(tagsEnd,it.size).toSet())
})

@Composable
internal fun CreationDefaultsScreen(repository:CreationDefaultsRepository, scope:DefaultsScope, contextName:String,
    tags:List<Tag>, people:List<Person>, onBack:()->Unit) {
    var config by remember(scope) { mutableStateOf<CreationDefaultsConfiguration?>(null) }
    var loadError by remember(scope) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(repository,scope,attempt) {
        loadError=false
        try { config=repository.configuration(scope) }
        catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
        catch(_:Exception) { loadError=true }
    }
    BackHandler(enabled=config==null,onBack=onBack)
    Surface(Modifier.fillMaxSize(),color=Arachn0deColors.Background) {
        val loaded=config
        if(loaded==null) Column(Modifier.padding(16.dp)) {
            Text("Valores predeterminados de creación")
            if(loadError) { Text("No se pudo cargar la configuración.");TextButton(onClick={ attempt++ }) { Text("Reintentar") } }
            else CircularProgressIndicator()
            TextButton(onClick=onBack) { Text("Volver") }
        } else key(CreationDefaultsCodec.key(scope)) {
            DefaultsEditor(repository,scope,contextName,loaded,tags,people,onBack)
        }
    }
}

@Composable
private fun DefaultsEditor(repository:CreationDefaultsRepository, scope:DefaultsScope, contextName:String,
    configuration:CreationDefaultsConfiguration,tags:List<Tag>,people:List<Person>,onBack:()->Unit) {
    var own by rememberSaveable(stateSaver=CreationDefaultsSaver) { mutableStateOf(configuration.own) }
    var reset by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val coroutineScope=rememberCoroutineScope()
    val operation=remember(repository,coroutineScope) { OperationState(coroutineScope) }
    val validInputs=remember { mutableStateMapOf<String,Boolean>() }
    val inherited=configuration.inherited
    fun leave() { if(!operation.busy) { if(own!=configuration.own) discard=true else onBack() } }
    BackHandler { leave() }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(if(scope==DefaultsScope.Global) "Configuración" else "Valores predeterminados",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
            TextButton(enabled=!operation.busy,onClick={ leave() }) { Text("Volver") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Valores predeterminados de creación",style=MaterialTheme.typography.titleMedium)
            Text(contextName)
            Text("Solo inicializan nuevos elementos creados en este contexto. Puedes modificarlos en el formulario. Los elementos existentes y los borradores guardados se conservan.")
            Text(if(scope==DefaultsScope.Global) "Heredar usa el comportamiento inicial de la aplicación." else "Heredar usa Global → Proyecto → capas anteriores. Cada valor propio reemplaza solo esa propiedad.")
            DefaultsChoice("Tipo",own.purpose,inherited.purpose,listOf(NodePurpose.ACTION,NodePurpose.NOTE),{ if(it==NodePurpose.ACTION) "Tarea" else "Nota" },!operation.busy) { own=own.copy(purpose=it) }
            DefaultsChoice("Obligación",own.obligation,inherited.obligation,listOf(false,true),{ if(it) "Activada" else "Desactivada" },!operation.busy) { own=own.copy(obligation=it) }
            DefaultsChoice("Moneda",own.currency,inherited.currency,listOf("CLP","USD","EUR"),{ it },!operation.busy) { own=own.copy(currency=it) }
            DefaultsChoice("Prioridad",own.priority,inherited.priority,Priority.entries,{ when(it) { Priority.NONE->"Ninguna";Priority.LOW->"Baja";Priority.MEDIUM->"Media";Priority.HIGH->"Alta" } },!operation.busy) { own=own.copy(priority=it) }
            Text("Una Nota conserva Pago, Prioridad y fechas como valores latentes del formulario, disponibles si cambias a Tarea.")
            DefaultsSet("Etiquetas",own.tags,inherited.tags,tags.map { it.id to it.name },!operation.busy) { own=own.copy(tags=it) }
            DefaultsSet("Responsables",own.people,inherited.people,people.map { it.id to it.name },!operation.busy) { own=own.copy(people=it) }
            DefaultsDate("Inicio",own.start,inherited.start,!operation.busy,{ validInputs["inicio"]=it }) { own=own.copy(start=it) }
            DefaultsHour("Hora de inicio",own.startTime,inherited.startTime,!operation.busy,{ validInputs["hora-inicio"]=it }) { own=own.copy(startTime=it) }
            DefaultsDate("Vencimiento",own.due,inherited.due,!operation.busy,{ validInputs["vencimiento"]=it }) { own=own.copy(due=it) }
            DefaultsHour("Hora de vencimiento",own.dueTime,inherited.dueTime,!operation.busy,{ validInputs["hora-vencimiento"]=it }) { own=own.copy(dueTime=it) }
            Text("Día N se ajusta al último día del mes. Para vencimiento, día N y primer lunes ya pasados pasan al mes siguiente. Sin hora se usa el inicio del día local; una hora sin fecha queda latente.")
            TextButton(enabled=!operation.busy,onClick={ reset=true }) { Text(if(scope==DefaultsScope.Global) "Restablecer valores globales" else "Restablecer herencia") }
        }
        Button(enabled=!operation.busy && validInputs.values.all { it },onClick={
            operation.submit("No se pudo guardar la configuración. Revisa las etiquetas y responsables seleccionados.",{
                repository.save(scope,own);true
            },onBack)
        },modifier=Modifier.fillMaxWidth()) { Text("Guardar valores predeterminados") }
    }
    OperationErrorDialog(operation)
    if(reset) AlertDialog(onDismissRequest={ if(!operation.busy) reset=false },title={ Text("¿Restablecer valores predeterminados?") },
        text={ Text(if(scope==DefaultsScope.Global) "Se eliminará la configuración global. No cambia elementos existentes ni overrides de otros contextos." else "Este contexto volverá a heredar todas las propiedades. No cambia elementos existentes ni otras capas.") },
        confirmButton={ TextButton(enabled=!operation.busy,onClick={ operation.submit("No se pudo restablecer la configuración.",{ repository.reset(scope);true },onBack) }) { Text("Restablecer") } },
        dismissButton={ TextButton(enabled=!operation.busy,onClick={ reset=false }) { Text("Cancelar") } })
    if(discard) AlertDialog(onDismissRequest={ discard=false },title={ Text("¿Descartar cambios de configuración?") },
        confirmButton={ TextButton(onClick=onBack) { Text("Descartar cambios") } },dismissButton={ TextButton(onClick={ discard=false }) { Text("Seguir editando") } })
}

@Composable
private fun <T> DefaultsChoice(label:String,own:DefaultValue<T>,inherited:T,values:List<T>,display:(T)->String,enabled:Boolean,onChange:(DefaultValue<T>)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label,style=MaterialTheme.typography.titleSmall)
        Text("Heredado: ${display(inherited)}",style=MaterialTheme.typography.bodySmall)
        Box {
            OutlinedButton(enabled=enabled,onClick={ expanded=true },modifier=Modifier.fillMaxWidth().testTag("defaults-choice:$label")) { Text(when(own) { DefaultValue.Inherit->"Heredar · ${display(inherited)}";is DefaultValue.Own->"Propio · ${display(own.value)}" }) }
            DropdownMenu(expanded,onDismissRequest={ expanded=false }) {
                DropdownMenuItem(text={ Text("Heredar") },onClick={ expanded=false;onChange(DefaultValue.Inherit) })
                values.forEach { value -> DropdownMenuItem(text={ Text(display(value)) },onClick={ expanded=false;onChange(DefaultValue.Own(value)) }) }
            }
        }
    }
}

@Composable
private fun DefaultsSet(label:String,own:DefaultValue<Set<String>>,inherited:Set<String>,items:List<Pair<String,String>>,enabled:Boolean,onChange:(DefaultValue<Set<String>>)->Unit) {
    var selecting by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    fun names(ids:Set<String>)=items.filter { it.first in ids }.take(3).joinToString { it.second }.ifEmpty { "Ninguno" } + if(ids.size>3) " (+${ids.size-3})" else ""
    Column {
        DefaultsChoice(label,own,inherited,listOf(emptySet()),::names,enabled,onChange)
        Text("Un conjunto propio reemplaza completamente al heredado.",style=MaterialTheme.typography.bodySmall)
        if(own is DefaultValue.Own) {
            OutlinedButton(enabled=enabled,onClick={ selecting=true }) { Text("Seleccionar $label (${own.value.size})") }
            if(items.isEmpty()) Text("Aún no hay $label disponibles.")
        }
    }
    if(selecting && own is DefaultValue.Own) AlertDialog(onDismissRequest={ selecting=false },title={ Text(label) },
        text={ Column {
            OutlinedTextField(query,{ query=it },label={ Text("Buscar") },singleLine=true)
            LazyColumn(Modifier.heightIn(max=300.dp)) {
                items(items.filter { it.second.contains(query,ignoreCase=true) },key={ it.first }) { (id,name) ->
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(id in own.value,enabled=enabled,onCheckedChange={ checked -> onChange(DefaultValue.Own(if(checked) own.value+id else own.value-id)) })
                        Text(name)
                    }
                }
            }
            TextButton(enabled=enabled,onClick={ onChange(DefaultValue.Own(emptySet())) }) { Text("Quitar todos") }
        } },confirmButton={ TextButton(onClick={ selecting=false }) { Text("Listo") } })
}

private fun dateLabel(rule:DefaultDate):String = when(rule.kind) {
    DefaultDateKind.NONE->"Sin fecha";DefaultDateKind.TODAY->"Hoy";DefaultDateKind.TOMORROW->"Mañana"
    DefaultDateKind.IN_DAYS->"En ${rule.number} días";DefaultDateKind.FIRST_DAY->"Primer día del mes";DefaultDateKind.FIRST_DAY_NEXT->"Primer día del mes siguiente"
    DefaultDateKind.DAY_OF_MONTH->"Día ${rule.number} del mes";DefaultDateKind.DAY_NEXT_MONTH->"Día ${rule.number} del mes siguiente"
    DefaultDateKind.FIRST_MONDAY->"Primer lunes del mes";DefaultDateKind.FIRST_MONDAY_NEXT->"Primer lunes del mes siguiente"
}
@Composable
private fun DefaultsDate(label:String,own:DefaultValue<DefaultDate>,inherited:DefaultDate,enabled:Boolean,onValidity:(Boolean)->Unit,onChange:(DefaultValue<DefaultDate>)->Unit) {
    DefaultsChoice(label,own,inherited,DefaultDateKind.entries.map { DefaultDate(it,if(it in listOf(DefaultDateKind.IN_DAYS,DefaultDateKind.DAY_OF_MONTH,DefaultDateKind.DAY_NEXT_MONTH)) 1 else 0) },::dateLabel,enabled,onChange)
    val rule=(own as? DefaultValue.Own)?.value
    val numbered=rule!=null && rule.kind in listOf(DefaultDateKind.IN_DAYS,DefaultDateKind.DAY_OF_MONTH,DefaultDateKind.DAY_NEXT_MONTH)
    if(numbered) {
        var text by rememberSaveable(rule.kind) { mutableStateOf(rule.number.toString()) }
        val maximum=if(rule.kind==DefaultDateKind.IN_DAYS) 3650 else 31
        val valid=text.toIntOrNull()?.let { it in 1..maximum } == true
        LaunchedEffect(valid) { onValidity(valid) }
        OutlinedTextField(text,{ value ->
            text=value
            value.toIntOrNull()?.takeIf { it in 1..maximum }?.let { onChange(DefaultValue.Own(rule.copy(number=it))) }
        },label={ Text(if(rule.kind==DefaultDateKind.IN_DAYS) "Cantidad de días" else "Día del mes") },supportingText={ Text("1–$maximum") },
            isError=!valid,enabled=enabled,singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
    } else LaunchedEffect(numbered) { onValidity(true) }
}
private fun timeLabel(time:DefaultTime)=when(time) { DefaultTime.Unspecified->"Sin hora";is DefaultTime.Minute->"%02d:%02d".format(java.util.Locale.ROOT,time.value/60,time.value%60) }
@Composable
private fun DefaultsHour(label:String,own:DefaultValue<DefaultTime>,inherited:DefaultTime,enabled:Boolean,onValidity:(Boolean)->Unit,onChange:(DefaultValue<DefaultTime>)->Unit) {
    DefaultsChoice(label,own,inherited,listOf(DefaultTime.Unspecified,DefaultTime.Minute(9*60)),::timeLabel,enabled,onChange)
    val minute=((own as? DefaultValue.Own)?.value as? DefaultTime.Minute)?.value
    if(minute!=null) {
        var text by rememberSaveable { mutableStateOf(timeLabel(DefaultTime.Minute(minute))) }
        val pieces=text.split(":")
        val hour=pieces.getOrNull(0)?.toIntOrNull();val part=pieces.getOrNull(1)?.toIntOrNull()
        val valid=pieces.size==2 && hour!=null && hour in 0..23 && part!=null && part in 0..59
        LaunchedEffect(valid) { onValidity(valid) }
        OutlinedTextField(text,{ value ->
            text=value
            val p=value.split(":");val h=p.getOrNull(0)?.toIntOrNull();val m=p.getOrNull(1)?.toIntOrNull()
            if(p.size==2 && h!=null && h in 0..23 && m!=null && m in 0..59) onChange(DefaultValue.Own(DefaultTime.Minute(h*60+m)))
        },label={ Text("$label (HH:mm)") },isError=!valid,enabled=enabled,singleLine=true)
    } else LaunchedEffect(minute) { onValidity(true) }
}
