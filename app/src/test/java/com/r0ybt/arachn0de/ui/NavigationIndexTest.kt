package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import org.junit.Test
import org.junit.Assert.*

class NavigationIndexTest {
    @Test fun hiddenIndexDoesNoWorkSharesUpdatesAndReopensWithFreshData()=runBlocking<Unit> {
        val p=Project("p","Core","",0,1,1)
        val node=Node("n","p",null,"Before","",false,0,1,1,false)
        val source=MutableStateFlow<Pair<List<Project>,List<Node>>?>(listOf(p) to listOf(node))
        val index=navigationIndex(source,this)
        val first=Channel<List<com.r0ybt.arachn0de.access.ElementResult>?>(Channel.UNLIMITED)
        val second=Channel<List<com.r0ybt.arachn0de.access.ElementResult>?>(Channel.UNLIMITED)
        suspend fun awaitTitle(channel:Channel<List<com.r0ybt.arachn0de.access.ElementResult>?>,title:String)=withTimeout(5000) {
            while(channel.receive()?.any {it.title==title}!=true) {}
        }
        var a:Job?=null;var b:Job?=null
        try {
            delay(30);assertEquals(0,source.subscriptionCount.value);assertNull(index.value)
            a=launch {index.collect {first.send(it)}};b=launch {index.collect {second.send(it)}}
            awaitTitle(first,"Before");awaitTitle(second,"Before");assertEquals(1,source.subscriptionCount.value)
            source.value=listOf(p) to listOf(node.copy(title="Updated",isCompleted=true))
            awaitTitle(first,"Updated");awaitTitle(second,"Updated")
            a.cancelAndJoin();b.cancelAndJoin()
            withTimeout(5000) {while(source.subscriptionCount.value!=0 || index.value!=null)delay(5)}
            source.value=listOf(p.copy(name="Renamed")) to emptyList()
            val reopened=withTimeout(5000) {index.filterNotNull().first()}
            assertEquals(listOf("Renamed"),reopened.map {it.title})
        } finally {a?.cancel();b?.cancel();coroutineContext.cancelChildren()}
    }
}
