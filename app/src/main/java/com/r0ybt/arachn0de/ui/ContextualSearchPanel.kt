package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.access.ElementResult
import com.r0ybt.arachn0de.access.ElementSearch
import com.r0ybt.arachn0de.domain.model.*

internal fun nameMatches(title:String,query:String)=ElementSearch.normalize(query) in ElementSearch.normalize(title)

/** Same indexed branch boundaries as before; only names participate in matching. */
internal fun contextualResults(index:List<ElementResult>,query:String,projectId:String?,nodeId:String?):List<ElementResult> {
    if(query.isBlank()) return emptyList()
    val root=nodeId?.let {"node:$it"}
    val byKey=index.associateBy {it.key}
    return index.filter {result->
        result.projectId!=null && (projectId==null || result.projectId==projectId) && nameMatches(result.title,query) &&
            (root==null || run {
                val seen=hashSetOf<String>();var key:String?=result.key
                while(key!=null && seen.add(key)) {
                    if(key==root) return@run true
                    key=byKey[key]?.parentKey
                }
                false
            })
    }
}

internal fun inlineNameRows(tree:NodeTreeSnapshot,projectId:String,parentId:String?,query:String,
    filter:NodeFilter=NodeFilter(),people:Map<String,Set<String>> = emptyMap(),tags:Map<String,Set<String>> = emptyMap(),
    order:(List<FilteredNodeRow>)->List<FilteredNodeRow> = {it}):List<FilteredNodeRow> =
    order(ScopedNodeFilter.apply(tree,projectId,parentId,filter,people,tags)).filter {it.isMatch && nameMatches(it.node.title,query)}

internal fun originalNodePath(node:Node,tree:NodeTreeSnapshot,projectNames:Map<String,String>):String {
    val names=mutableListOf<String>();val seen=hashSetOf<String>();var parent=node.parentId
    while(parent!=null && seen.add(parent)) {val ancestor=tree.nodesById[parent] ?: break;names.add(ancestor.title);parent=ancestor.parentId}
    return (listOfNotNull(projectNames[node.projectId])+names.asReversed()).joinToString(" › ")
}

internal class InlineSearchState {
    var open by mutableStateOf(false)
    var query by mutableStateOf("")
    val active get()=query.isNotBlank()
    fun close() {query="";open=false}
    companion object {
        val Saver=listSaver<InlineSearchState,String>(save={listOf(it.open.toString(),it.query)},
            restore={InlineSearchState().apply {open=it[0].toBoolean();query=it[1]}})
    }
}

/** Screen-owned projection: closing never writes navigation, sorting or filter state. */
@Composable internal fun rememberInlineSearch(location:String):InlineSearchState {
    val state=rememberSaveable(location,saver=InlineSearchState.Saver) {InlineSearchState()}
    val actions=LocalGlobalNavigation.current?.actions
    val visible=LocalAudioFooterVisible.current
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    fun close() {focus.clearFocus();keyboard?.hide();state.close()}
    val token=remember {Any()}
    DisposableEffect(actions,visible,state) {
        if(visible)actions?.searching?.put(token) {state.open=true}
        onDispose {actions?.searching?.remove(token)}
    }
    BackHandler(state.open) {close()}
    if(state.open) ContextualBottomPanel {ContextualSearchPanel(state.query,{state.query=it},{close()})}
    return state
}

@Composable internal fun ContextualSearchPanel(query:String,onQuery:(String)->Unit,onDismiss:()->Unit) {
    ModularPanelDialog(onDismissRequest=onDismiss,inline=true,compact=true,plainBody=true,
        title={Text("Buscar por nombre")},text={
            OutlinedTextField(query,onQuery,singleLine=true,label={Text("Buscar")},
                modifier=modularPanelFieldModifier(autoFocus=true).testTag("contextual-search-query"))
        },confirmButton={},actionsContent={
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                PrimaryControlButton(onClick={onQuery("")},enabled=query.isNotEmpty(),modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text("Limpiar")}
                PrimaryControlButton(onClick=onDismiss,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text("Cerrar")}
            }
        })
}
