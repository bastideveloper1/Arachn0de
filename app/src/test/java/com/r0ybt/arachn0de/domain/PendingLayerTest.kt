package com.r0ybt.arachn0de.domain
import com.r0ybt.arachn0de.domain.model.*
import org.junit.Test
import org.junit.Assert.*
class PendingLayerTest {
    @Test fun layerRecoversEmphasisWhenTaskReopensAndNotesDoNotCount() {
        val layer=Node("layer","p",null,"Layer","",false,0,1,1,true)
        val child=Node("child","p","layer","Child","",true,0,1,1,false)
        assertFalse(NodeTreeSnapshot(listOf(layer,child)).progressById.getValue("layer").hasPending)
        assertTrue(NodeTreeSnapshot(listOf(layer,child.copy(isCompleted=false))).progressById.getValue("layer").hasPending)
        assertFalse(NodeTreeSnapshot(listOf(layer,child.copy(purpose=NodePurpose.NOTE,isCompleted=false))).progressById.getValue("layer").hasPending)
    }
}
