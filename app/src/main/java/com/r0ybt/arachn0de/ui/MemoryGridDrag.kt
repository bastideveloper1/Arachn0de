package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput

/** Two-dimensional hit testing for the existing three-column rows; commit only on drop. */
internal class MemoryGridDrag {
    val bounds=mutableStateMapOf<String,Rect>()
    var groups:Map<String,Boolean> = emptyMap()
    var enabled=true
    var dragged by mutableStateOf<String?>(null);private set
    var point by mutableStateOf(Offset.Zero);private set
    var grab by mutableStateOf(Offset.Zero);private set
    var size by mutableStateOf(Size.Zero);private set
    private var target:String?=null
    private var sourceGroup=false
    fun start(at:Offset) {
        if(!enabled) return
        val source=bounds.entries.firstOrNull {it.key in groups && it.value.contains(at)} ?: return
        dragged=source.key;point=at;grab=at-source.value.topLeft;size=source.value.size;sourceGroup=groups.getValue(source.key);target=source.key
    }
    fun move(at:Offset) {
        val source=dragged ?: return
        if(!enabled || groups[source]!=sourceGroup) {cancel();return}
        point=at
        target=bounds.entries.firstOrNull {it.key in groups && it.value.contains(at)}?.key?.takeIf {groups[it]==sourceGroup}
    }
    fun drop():Pair<String,String>? {
        val source=dragged;val destination=target
        val valid=enabled && source!=null && destination!=null && source!=destination && groups[source]==sourceGroup && groups[destination]==sourceGroup
        cancel()
        return if(valid) source!! to destination!! else null
    }
    fun cancel() {dragged=null;target=null}
}

@Composable internal fun Modifier.memoryGridGestures(drag:MemoryGridDrag,origin:Offset,onDrop:(String,String)->Unit):Modifier {
    val currentOrigin by rememberUpdatedState(origin)
    val currentDrop by rememberUpdatedState(onDrop)
    return pointerInput(drag) {
        detectDragGesturesAfterLongPress(
            onDragStart={drag.start(it+currentOrigin)},
            onDrag={change,_->if(drag.dragged!=null) {change.consume();drag.move(change.position+currentOrigin)}},
            onDragEnd={drag.drop()?.let {(source,target)->currentDrop(source,target)}},onDragCancel={drag.cancel()})
    }
}

@Composable internal fun MemoryGridAutoScroll(drag:MemoryGridDrag,list:LazyListState,viewport:Rect) {
    LaunchedEffect(drag.dragged,viewport) {
        if(drag.dragged==null) return@LaunchedEffect
        var last=withFrameNanos {it}
        while(drag.dragged!=null) {
            val now=withFrameNanos {it};val seconds=((now-last)/1_000_000_000f).coerceAtMost(.05f);last=now
            val edge=(viewport.height*.12f).coerceAtMost(80f)
            val speed=computeAutoScrollVelocity(drag.point.y-viewport.top,viewport.height,edge,480f)
            if(speed!=0f) {list.scrollBy(speed*seconds);drag.move(drag.point)}
        }
    }
}
