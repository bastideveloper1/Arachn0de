package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

internal fun sortDirection(rule:SortRule,threeStates:Boolean)=when(rule.criterion) {
    SortCriterion.STATE->if(threeStates && rule.reverse)"Por verificar primero" else "Pendientes primero"
    SortCriterion.EFFORT->if(rule.reverse)"Menor a mayor" else "Mayor a menor"
    SortCriterion.PRIORITY->if(rule.reverse)"No prioritarias primero" else "Prioritarias primero"
    SortCriterion.DUE->if(rule.reverse)"Lejanas primero" else "Próximas primero"
    SortCriterion.CREATED->if(rule.reverse)"Antiguas primero" else "Recientes primero"
    else->if(rule.reverse)"Z–A" else "A–Z"
}

@Composable
internal fun NodeSortMenu(configuration:SortConfiguration,onChange:(SortConfiguration)->Unit,onDismiss:()->Unit,
    onApply:((Throwable?)->Unit)->Unit,projectDefault:Boolean=false,onInherit:(()->Unit)?=null,owns:Boolean=true,threeStates:Boolean=false, filters:ScopeFilters?=null, people:List<Person> = emptyList(), tags:List<Tag> = emptyList(),taskControls:TaskViewControls?=null,cardFilters:ContextCalendarState?=null) {
    var filtering by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val list=rememberLazyListState()
    val ids=configuration.rules.map {it.criterion.name}
    val drag=rememberDragReorderState(list,"sort:",mapOf(false to ids),busy,null,"sort-criteria") {source,target,_->
        val reordered=configuration.rules.toMutableList()
        val from=reordered.indexOfFirst {it.criterion.name==source};val to=reordered.indexOfFirst {it.criterion.name==target}
        if(from>=0 && to>=0) {reordered.add(to,reordered.removeAt(from));onChange(configuration.copy(rules=reordered,inherited=false,legacyLayersOverride=false))}
    }
    val dragGesture=remember(drag) {drag.gestureModifier}
    val checkboxWidth=with(androidx.compose.ui.platform.LocalDensity.current) {48.dp.toPx()}
    SideEffect {drag.startAllowed={it.x>=checkboxWidth}}
    OrderingPanel(onDismiss={if(!busy)onDismiss()},
        title=if(filtering)"Filtrar" else if(projectDefault)"Orden del Núcleo" else "Ordenar",
        body={if(filtering && filters!=null) ScopeFiltersDialog(filters,people,tags,showState=false,inline=true,externalActions=true,taskControls=taskControls,onClose=onDismiss,cardFilters=cardFilters)
        else Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            if(onInherit!=null) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=!owns,onCheckedChange={if(it)onInherit() else onChange(configuration.copy(inherited=false,legacyLayersOverride=false))},enabled=!busy,modifier=Modifier.testTag("sort-inherit"))
                Text("Usar orden del Núcleo",Modifier.weight(1f),color=Arachn0deColors.TextPrimary)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(configuration.manual,{onChange(configuration.copy(manual=it,inherited=false,legacyLayersOverride=false))},enabled=!busy,modifier=Modifier.testTag("sort-manual"));Text("Manual",color=Arachn0deColors.TextPrimary)
                }
            }
            OutlinedCard(Modifier.fillMaxWidth(),border=BorderStroke(1.dp,if(configuration.layersFirst)Arachn0deColors.Accent else Arachn0deColors.Outline),
                colors=CardDefaults.outlinedCardColors(containerColor=Arachn0deColors.Surface)) {
                Column(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(configuration.layersFirst,enabled=!busy,role=Role.Switch) {
                    onChange(configuration.copy(layersFirst=it,inherited=false,legacyLayersOverride=false))
                }.testTag("sort-layers-first").padding(horizontal=16.dp,vertical=6.dp),verticalArrangement=Arrangement.Center) {
                    Text("Capas primero",color=Arachn0deColors.TextPrimary,style=MaterialTheme.typography.bodyMedium)
                    Text(if(configuration.layersFirst)"Activado" else "Desactivado",color=Arachn0deColors.TextSecondary,style=MaterialTheme.typography.labelSmall)
                }
            }
            FavoriteSortSelector(configuration.favorites,!busy) {onChange(configuration.copy(favorites=it,inherited=false,legacyLayersOverride=false))}
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=420.dp).testTag("sort-criteria").then(dragGesture),state=list,userScrollEnabled=drag.draggedId==null && !busy,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                items(drag.orderFor(false,ids),key={"sort:$it"}) {key->
                    val rule=configuration.rules.first {it.criterion.name==key}
                    OutlinedCard(Modifier.fillMaxWidth().animateItem().then(drag.cardModifier(key)),
                        border=BorderStroke(1.dp,if(rule.enabled)Arachn0deColors.Accent else Arachn0deColors.Outline),
                        colors=CardDefaults.outlinedCardColors(containerColor=Arachn0deColors.Surface)) {
                        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable(enabled=!busy) {
                            if(rule.enabled && (rule.criterion!=SortCriterion.STATE || threeStates))
                            onChange(configuration.copy(rules=configuration.rules.map {if(it==rule)it.copy(reverse=!it.reverse) else it},inherited=false,legacyLayersOverride=false))
                        }.testTag("sort-rule:$key").semantics {stateDescription=if(drag.isDragging(key))"Arrastrando" else sortDirection(rule,threeStates)},verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(rule.enabled,{onChange(configuration.copy(rules=configuration.rules.map {if(it==rule)it.copy(enabled=!it.enabled) else it},manual=if(!rule.enabled)false else configuration.manual,inherited=false,legacyLayersOverride=false))},enabled=!busy,modifier=Modifier.testTag("sort-check:$key").semantics {contentDescription="Activar ${rule.criterion.label}"})
                            Column(Modifier.weight(1f).padding(vertical=6.dp,horizontal=4.dp)) {
                                Text(rule.criterion.label,color=if(rule.enabled)Arachn0deColors.TextPrimary else Arachn0deColors.TextSecondary,style=MaterialTheme.typography.bodyMedium)
                                Text(sortDirection(rule,threeStates),color=Arachn0deColors.TextSecondary,style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            error?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        }},actions={
            if(filters!=null) OrderingPanelButton(if(filtering)"Ordenar" else "Filtrar","sort-filter-toggle",!busy) {filtering=!filtering}
            if(filtering && filters!=null) OrderingPanelButton("Limpiar","sort-filter-clear",!busy) {filters.clear();cardFilters?.clearCardFilters();taskControls?.onSelect?.invoke(null);onDismiss()}
            OrderingPanelButton(if(busy)"Aplicando…" else "Aplicar","sort-apply",!busy) {
                if(filtering) onDismiss()
                else {busy=true;error=null;onApply {failure->busy=false;if(failure==null)onDismiss() else error="No se pudo aplicar el orden. El contenido se conserva."}}
            }
        })
}

@Composable internal fun FavoriteSortSelector(value:FavoriteOrder,enabled:Boolean=true,onChange:(FavoriteOrder)->Unit) {
    var open by remember {mutableStateOf(false)}
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton({open=true},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("sort-favorites")) {
            Text("Favoritos: ${value.label}")
        }
        DropdownMenu(open,{open=false}) {
            FavoriteOrder.entries.forEach {choice->
                DropdownMenuItem(text={Text(choice.label)},enabled=enabled,modifier=Modifier.testTag("sort-favorites:${choice.name}"),
                    onClick={onChange(choice);open=false})
            }
        }
    }
}
