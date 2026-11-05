@file:JvmName("SearchUi") // Keep generated nested class filenames short on encrypted filesystems.

package com.r0ybt.arachn0de.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.access.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

/** Favorites keep their existing view; search now belongs to the visible card list. */
@Composable internal fun ElementSearchScreen(index:List<ElementResult>,mode:String,query:String,onQuery:(String)->Unit,onMode:(String)->Unit,
    onBack:()->Unit,onOpen:(ElementResult)->Unit,listState:androidx.compose.foundation.lazy.LazyListState,ready:Boolean=true,
    criteria:SearchCriteria=SearchCriteria(),onCriteria:(SearchCriteria)->Unit={},nodes:List<Node> = emptyList(),tags:TagState=TagState(),
    onAttention:(()->Unit)?=null,onObligations:(()->Unit)?=null,onRecurrences:(()->Unit)?=null,clock:()->Long=System::currentTimeMillis,
    peopleByNode:Map<String,List<Person>> = emptyMap(),recurrenceSnapshot:SearchRecurrences?=null) {
    val search=rememberInlineSearch("favorites")
    val filteredListState=androidx.compose.foundation.lazy.rememberLazyListState()
    val favorites=LocalElementAccessState.current.favorites
    val rows=index.filter {it.key in favorites && (!search.active || nameMatches(it.title,search.query))}
    BackHandler(!search.open,onBack)
    Surface(Modifier.fillMaxSize(),color=Arachn0deColors.Background) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick=onBack) {Text("Volver")}
                NavigationHeading("Favoritos",Modifier.weight(1f))
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f),state=if(search.active)filteredListState else listState) {
                if(!ready) item {Text("Cargando…")}
                else if(rows.isEmpty()) item {Text(if(search.active)"Sin coincidencias" else "Sin favoritos")}
                items(rows,key={it.key}) {result->SearchResultRow(result,null,true,System.currentTimeMillis()) {onOpen(result)}}
            }
        }
    }
}
