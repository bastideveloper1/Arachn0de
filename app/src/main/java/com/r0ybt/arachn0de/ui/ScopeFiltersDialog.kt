package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.r0ybt.arachn0de.domain.model.*

internal class ScopeFilters {
    var priority by mutableStateOf<Priority?>(null)
    var time by mutableStateOf(TimeFilter.ALL)
    var person by mutableStateOf<String?>(null)
    var completion by mutableStateOf(CompletionFilter.ALL)
    var tag by mutableStateOf<String?>(null)
    var open by mutableStateOf(false)
    val active get() = priority != null || time != TimeFilter.ALL || person != null || completion != CompletionFilter.ALL || tag != null
    fun clear() { priority = null; time = TimeFilter.ALL; person = null; completion = CompletionFilter.ALL; tag = null }
    companion object { val Saver = listSaver<ScopeFilters, String>(save = { listOf(it.time.name, it.person.orEmpty(), it.completion.name, it.tag.orEmpty(), it.open.toString(), it.priority?.name.orEmpty()) },
        restore = { ScopeFilters().apply { time = TimeFilter.valueOf(it[0]); person = it[1].ifEmpty { null }; completion = CompletionFilter.valueOf(it[2]); tag = it[3].ifEmpty { null }; open = it[4].toBoolean(); priority = it.getOrNull(5)?.takeIf { it.isNotEmpty() }?.let(Priority::valueOf) } }) }
}
internal class ScopeFilterStore {
    val scopes = mutableStateMapOf<String, ScopeFilters>()
    fun scope(id: String): ScopeFilters = scopes.getOrPut(id) { ScopeFilters() }
    companion object { val Saver = listSaver<ScopeFilterStore, String>(
        save = { store -> listOf("priority-v1") + store.scopes.flatMap { (id, s) -> listOf(id, s.time.name, s.person.orEmpty(), s.completion.name, s.tag.orEmpty(), s.open.toString(), s.priority?.name.orEmpty()) } },
        restore = { values -> ScopeFilterStore().apply { (if (values.firstOrNull() == "priority-v1") values.drop(1).chunked(7) else values.chunked(6)).forEach { row -> scopes[row[0]] = ScopeFilters().apply { time = TimeFilter.valueOf(row[1]); person = row[2].ifEmpty { null }; completion = CompletionFilter.valueOf(row[3]); tag = row[4].ifEmpty { null }; open = row[5].toBoolean(); priority = row.getOrNull(6)?.takeIf { it.isNotEmpty() }?.let(Priority::valueOf) } } } }) }
}
@Composable internal fun ScopeFiltersDialog(state: ScopeFilters, people: List<Person>, tags: List<Tag>,showState:Boolean=true,inline:Boolean=false,externalActions:Boolean=false,taskControls:TaskViewControls?=null,onClose:()->Unit={state.open=false},cardFilters:ContextCalendarState?=null,cardOnly:Boolean=false) {
    var selector by remember {mutableStateOf<String?>(null)}
    if(selector=="Etiqueta") {
        val content: @Composable (MutableState<Boolean>)->Unit = {busy->
            TagPanel(TagState(tags),null,selected=setOfNotNull(state.tag),onChange={state.tag=it.firstOrNull()},
                single=true,singleAssignment=false,busyState=busy,backNavigation=true,onClose={selector=null})
        }
        if(inline) Box(Modifier.fillMaxWidth().heightIn(max=420.dp)) {content(remember {mutableStateOf(false)})}
        else TagPanelDialog(onClose={selector=null},content=content)
        return
    }
    val choices:List<Pair<String,String>> = when(selector) {
        "Tiempo"->TimeFilter.entries.map {it.name to timeFilterLabel(it)}
        "Persona"->listOf("" to "Todas")+people.map {it.id to it.name}
        "Estado"->CompletionFilter.entries.map {it.name to completionLabel(it)}
        "Prioridad"->listOf("" to "Todas",Priority.NONE.name to priorityLabel(Priority.NONE),Priority.HIGH.name to priorityLabel(Priority.HIGH))
        "Etiqueta"->listOf("" to "Todas las etiquetas")+tags.map {it.id to it.name}
        "Favoritos"->listOf("" to "Todas las tarjetas","ONLY" to "Solo favoritos")
        "Urgentes"->listOf("" to "Todas las tarjetas","ONLY" to "Solo urgentes pendientes")
        "Vencimiento"->listOf("" to "Cualquiera",AttentionReason.OVERDUE.name to "Vencidas",
            AttentionReason.DUE_TODAY.name to "Vencen hoy (sin vencer)",AttentionReason.UPCOMING.name to "Próximas (24 horas)")
        else->emptyList()
    }
    val current=when(selector) {"Tiempo"->state.time.name;"Persona"->state.person.orEmpty();"Estado"->state.completion.name;"Prioridad"->state.priority?.name.orEmpty();"Etiqueta"->state.tag.orEmpty();
        "Favoritos"->if(cardFilters?.favorites==true)"ONLY" else "";"Urgentes"->if(cardFilters?.urgentOnly==true)"ONLY" else "";"Vencimiento"->cardFilters?.dueCondition?.name.orEmpty();else->""}
    val panel: @Composable (@Composable ()->Unit,@Composable ()->Unit,@Composable ()->Unit)->Unit = {title,body,actions->
        if(inline) Column(Modifier.fillMaxWidth().heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {if(selector!=null)title();body();actions()}
        else ModularPanelDialog(onDismissRequest={if(selector!=null)selector=null else onClose()},compact=true,plainBody=true,
            title=title,text=body,confirmButton={},actionsContent=actions)
    }
    panel(
        {Text(selector ?: "Filtros de esta vista")},
        {
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                if(selector==null) {
                    taskControls?.let {TaskViewPanel(it.copy(expanded=false))}
                    cardFilters?.let {filters->
                        FilterSelectorCard("Favoritos",if(filters.favorites)"Solo favoritos" else "Todas las tarjetas") {selector="Favoritos"}
                        FilterSelectorCard("Urgentes",if(filters.urgentOnly)"Solo urgentes pendientes" else "Todas las tarjetas") {selector="Urgentes"}
                        FilterSelectorCard("Vencimiento",when(filters.dueCondition) {
                            AttentionReason.OVERDUE->"Vencidas";AttentionReason.DUE_TODAY->"Vencen hoy (sin vencer)";AttentionReason.UPCOMING->"Próximas (24 horas)";else->"Cualquiera"
                        }) {selector="Vencimiento"}
                    }
                    if(!cardOnly) {
                    FilterSelectorCard("Tiempo",timeFilterLabel(state.time)) {selector="Tiempo"}
                    FilterSelectorCard("Persona",people.firstOrNull {it.id==state.person}?.name ?: "Todas") {selector="Persona"}
                    if(showState)FilterSelectorCard("Estado",completionLabel(state.completion)) {selector="Estado"}
                    FilterSelectorCard("Prioridad",state.priority?.let(::priorityLabel) ?: "Todas") {selector="Prioridad"}
                    FilterSelectorCard("Etiqueta",tags.firstOrNull {it.id==state.tag}?.name ?: "Todas las etiquetas") {selector="Etiqueta"}
                    }
                } else choices.forEach {(key,label)->
                    androidx.compose.material3.OutlinedCard(onClick={
                        when(selector) {
                            "Tiempo"->state.time=TimeFilter.valueOf(key)
                            "Persona"->state.person=key.ifEmpty {null}
                            "Estado"->state.completion=CompletionFilter.valueOf(key)
                            "Prioridad"->state.priority=key.takeIf {it.isNotEmpty()}?.let(Priority::valueOf)
                            "Etiqueta"->state.tag=key.ifEmpty {null}
                            "Favoritos"->cardFilters?.favorites=key=="ONLY"
                            "Urgentes"->cardFilters?.urgentOnly=key=="ONLY"
                            "Vencimiento"->cardFilters?.dueCondition=key.takeIf {it.isNotEmpty()}?.let(AttentionReason::valueOf)
                        };selector=null
                    },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                        border=androidx.compose.foundation.BorderStroke(1.dp,if(key==current)com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Accent else com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Outline),
                        colors=CardDefaults.outlinedCardColors(containerColor=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Surface)) {
                        Text(label,Modifier.padding(12.dp),color=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextPrimary)
                    }
                }
            }
        },{
            if(selector!=null) Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick={selector=null},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                    LucideIcon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,"Volver")
                }
            } else if(!externalActions) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                PrimaryControlButton(onClick={state.clear();cardFilters?.clearCardFilters();taskControls?.onSelect?.invoke(null);onClose()},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text("Limpiar")}
                PrimaryControlButton(onClick=onClose,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text("Aplicar")}
            }
        })
}

@Composable private fun FilterSelectorCard(label:String,value:String,onClick:()->Unit) {
    OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Outline),
        colors=CardDefaults.outlinedCardColors(containerColor=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.Surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text(label,Modifier.weight(1f),color=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextPrimary)
            Text(value,Modifier.weight(1.5f),maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style=MaterialTheme.typography.bodySmall,color=com.r0ybt.arachn0de.ui.theme.Arachn0deColors.TextSecondary)
        }
    }
}
