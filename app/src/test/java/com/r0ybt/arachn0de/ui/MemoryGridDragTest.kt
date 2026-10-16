package com.r0ybt.arachn0de.ui

import androidx.compose.ui.geometry.*
import org.junit.Test
import org.junit.Assert.*

class MemoryGridDragTest {
    @Test fun twoDimensionalDropUsesBothCoordinatesAndNeverChangesPinGroup() {
        val drag=MemoryGridDrag();drag.groups=mapOf("a" to false,"b" to false,"c" to true)
        drag.bounds.putAll(mapOf("a" to Rect(0f,0f,90f,90f),"b" to Rect(100f,100f,190f,190f),"c" to Rect(100f,0f,190f,90f)))
        drag.start(Offset(20f,20f));drag.bounds.remove("a");assertEquals(Size(90f,90f),drag.size);drag.move(Offset(140f,140f));assertEquals("a" to "b",drag.drop())
        drag.bounds["a"]=Rect(0f,0f,90f,90f)
        drag.start(Offset(20f,20f));drag.move(Offset(140f,40f));assertNull(drag.drop())
        drag.start(Offset(20f,20f));drag.move(Offset(400f,400f));assertNull(drag.drop())
    }
    @Test fun cancellationDisabledAndChangedGroupsNeverCommit() {
        val drag=MemoryGridDrag();drag.groups=mapOf("a" to false,"b" to false)
        drag.bounds.putAll(mapOf("a" to Rect(0f,0f,90f,90f),"b" to Rect(100f,0f,190f,90f)))
        drag.start(Offset(20f,20f));drag.move(Offset(120f,20f));drag.cancel();assertNull(drag.drop())
        drag.enabled=false;drag.start(Offset(20f,20f));assertNull(drag.dragged)
        drag.enabled=true;drag.start(Offset(20f,20f));drag.move(Offset(120f,20f));drag.groups=mapOf("a" to true,"b" to false);assertNull(drag.drop())
    }
}
