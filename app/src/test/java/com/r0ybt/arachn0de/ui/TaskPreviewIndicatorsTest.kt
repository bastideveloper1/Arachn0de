package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class TaskPreviewIndicatorsTest {
    private fun task(id:String,verify:Boolean=false,done:Boolean=false)=Node(id,"p","layer",id,"",done,0,0,0,false,awaitingVerification=verify)
    @Test fun independentStatesAndTwoStatePreview() {
        val nodes=listOf(task("pending"),task("verify",true),task("done",done=true)).associateBy {it.id}
        for(pending in listOf(false,true))for(verify in listOf(false,true)) {
            val ids=buildSet {if(pending)add("pending");if(verify)add("verify");add("done")}
            val split=previewTaskStates(CompactCardFacts(tasks=ids),nodes,3)
            assertEquals(pending,split.first.isNotEmpty());assertEquals(verify,split.second.isNotEmpty())
        }
        assertEquals(setOf("pending","verify"),previewTaskStates(CompactCardFacts(tasks=nodes.keys),nodes,2).first)
    }
    @Test fun nearestOverrideAndProjectInheritance() {
        val project=Project("p","Project","",0,0,0,verificationEnabled=true)
        val layer=task("layer").copy(parentId=null,purpose=NodePurpose.LAYER,hasChildren=true)
        assertEquals(3,TaskStatePolicy.count(layer,mapOf(layer.id to layer),project))
        val parent=layer.copy(id="parent",taskStateCount=2)
        val child=layer.copy(parentId=parent.id)
        assertEquals(2,TaskStatePolicy.count(child,mapOf(parent.id to parent,child.id to child),project))
        assertEquals(3,TaskStatePolicy.count(child.copy(taskStateCount=3),mapOf(parent.id to parent),project))
    }
}
