package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.access.ElementResult
import com.r0ybt.arachn0de.access.ElementSearch
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.ui.state.OperationState
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

internal fun excludedMoveKeys(index:List<ElementResult>,ids:Set<String>):Set<String> {
    val excluded=ids.mapTo(hashSetOf()) {"node:$it"}
    var changed=true
    while(changed) {changed=false;index.forEach {if(it.parentKey in excluded && excluded.add(it.key)) changed=true}}
    return excluded
}

/** Shared destination browser. Only confirmation mutates data; footer stays outside list scrolling. */
@Composable internal fun DestinationExplorer(index:List<ElementResult>,movingIds:Set<String>,initialKey:String?=null,
    busy:Boolean=false,bankRoots:Set<String> = emptySet(),onDismiss:()->Unit,onConfirm:(ElementResult)->Unit) {
    var currentKey by rememberSaveable {mutableStateOf(initialKey)}
    var query by rememberSaveable {mutableStateOf("")}
    val excluded=remember(index,movingIds) {excludedMoveKeys(index,movingIds)}
    val entries=index.filter {it.projectId!=null && it.key !in excluded}
    val byKey=entries.associateBy {it.key}
    val current=byKey[currentKey]
    fun valid(e:ElementResult)=e.projectId !in bankRoots && (e.nodeId==null || e.type=="Capa") && e.destination
    val path=buildList {
        var cursor=current;val seen=hashSetOf<String>()
        while(cursor!=null && seen.add(cursor.key)) {add(cursor);cursor=byKey[cursor.parentKey]}
    }.asReversed()
    val searching=query.isNotBlank()
    val shown=if(searching) entries.filter(::valid).filter {ElementSearch.normalize(query) in ElementSearch.normalize(it.title+" "+it.description+" "+it.path)} else entries.filter {it.parentKey==current?.key && (current!=null || it.nodeId==null)}
    ModularPanelDialog(onDismissRequest={if(!busy)onDismiss()},compact=true,fieldOwnsScroll=true,
        title={Text("Mover a",color=Arachn0deColors.TextPrimary)},
        text={
            LazyRow(Modifier.fillMaxWidth().testTag("destination-path")) {
                item {TextButton(enabled=!busy,onClick={currentKey=null;query=""}) {Text("Núcleos")}}
                items(path,key={it.key}) {e->TextButton(enabled=!busy,onClick={currentKey=e.key;query=""}) {Text("› ${e.title}")}}
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(min=48.dp,max=360.dp).testTag("destination-list"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                items(((if(searching)emptyList() else listOfNotNull(current))+shown).distinctBy {it.key},key={it.key}) {e->
                    val colors=CardDefaults.outlinedCardColors(containerColor=if(currentKey==e.key)Arachn0deColors.SurfaceRaised else Arachn0deColors.Surface)
                    // Avoid Material3 clickable Surface semantics traversing detached lazy-list rows.
                    OutlinedCard(
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).clip(CardDefaults.outlinedShape)
                            .clickable(enabled=!busy,onClick={currentKey=e.key;query=""})
                            .testTag("destination:${e.key}").semantics {selected=currentKey==e.key},
                        border=BorderStroke(1.dp,if(currentKey==e.key)Arachn0deColors.Accent else Arachn0deColors.Outline),
                        colors=if(busy)colors.copy(containerColor=colors.disabledContainerColor,contentColor=colors.disabledContentColor) else colors) {
                        Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp)) {
                            Text(e.title,color=Arachn0deColors.TextPrimary,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            if(e.path.isNotBlank())Text(e.path,style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },confirmButton={},actionsContent={
            Column(Modifier.fillMaxWidth().testTag("destination-footer")) {
                OutlinedTextField(query,{query=it},enabled=!busy,singleLine=true,label={Text("Buscar destino")},modifier=Modifier.fillMaxWidth().testTag("destination-search"))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    PrimaryControlButton(enabled=!busy,onClick=onDismiss,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text("Cancelar")}
                    PrimaryControlButton(enabled=!busy && current?.let(::valid)==true,onClick={current?.takeIf(::valid)?.let(onConfirm)},modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("destination-confirm")) {Text("Mover aquí")}
                }
            }
        })
}

@Composable internal fun NodeDestinationDialog(ids:Set<String>,index:List<ElementResult>?,repository:NodeRepository,
    initialKey:String?=null,onDismiss:()->Unit,onMoved:(ElementResult)->Unit={onDismiss()}) {
    if(index==null) {NavigationIndexLoading(onDismiss);return}
    val scope=rememberCoroutineScope();val operation=remember(scope,repository) {OperationState(scope)}
    DestinationExplorer(index,ids,initialKey,operation.busy,LocalBankRootIds.current,onDismiss) {target->
        operation.submit("No se pudo mover. El destino o sus relaciones son incompatibles; el contenido se conserva.",{
            repository.moveToContainer(ids,requireNotNull(target.projectId),target.nodeId)
        },{onMoved(target)})
    }
    OperationErrorDialog(operation)
}
