package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class InteriorTaskCountsTest {
    private fun node(id:String,parent:String?=null,kind:NodePurpose=NodePurpose.ACTION,done:Boolean=false,verify:Boolean=false,priority:Priority=Priority.NONE,project:String="p")=
        Node(id,project,parent,id,"",done,0,0,0,kind==NodePurpose.LAYER,purpose=kind,awaitingVerification=verify,priority=priority)
    @Test fun countsUseTheCurrentHierarchyAndIncludeVerificationWithoutDuplicates() {
        val tree=NodeTreeSnapshot(listOf(node("layer",kind=NodePurpose.LAYER),node("nested","layer",NodePurpose.LAYER),
            node("pending","layer"),node("verify","nested",verify=true,priority=Priority.HIGH),
            node("done","nested",done=true,priority=Priority.HIGH),node("note","layer",NodePurpose.NOTE,priority=Priority.HIGH),
            node("sibling",priority=Priority.HIGH),node("other",project="other")))
        assertEquals(setOf("pending","sibling"),interiorTaskCounts(tree,"p",null).pending)
        assertEquals(setOf("verify","sibling"),interiorTaskCounts(tree,"p",null).highPriority)
        assertEquals(InteriorTaskCounts(setOf("pending"),setOf("verify"),setOf("verify")),interiorTaskCounts(tree,"p","layer"))
        assertTrue(interiorTaskCounts(tree,"p","nested").pending.isEmpty())
        assertEquals(setOf("verify"),interiorTaskCounts(tree,"p","nested").verification)
        assertEquals(setOf("verify"),interiorTaskCounts(tree,"p",null).verification)
    }
    @Test fun emptyAndCompletedOnlyScopesHaveNoBadgesToShow() {
        val tree=NodeTreeSnapshot(listOf(node("layer",kind=NodePurpose.LAYER),node("done","layer",done=true,priority=Priority.HIGH)))
        assertTrue(interiorTaskCounts(tree,"p",null).pending.isEmpty())
        assertTrue(interiorTaskCounts(tree,"p","layer").highPriority.isEmpty())
        assertTrue(interiorTaskCounts(NodeTreeSnapshot(emptyList()),"p",null).pending.isEmpty())
    }
    @Test fun singleStateScopesKeepDisjointUniqueTaskIdentities() {
        val layer=node("layer",kind=NodePurpose.LAYER)
        val pending=node("pending","layer")
        val verification=node("verify","layer",verify=true)
        for(task in listOf(pending,verification)) {
            val tree=NodeTreeSnapshot(listOf(layer,task,node("done","layer",done=true),node("note","layer",NodePurpose.NOTE)))
            for(parent in listOf(null,"layer")) {
                val counts=interiorTaskCounts(tree,"p",parent)
                assertEquals(if(task==pending)setOf(task.id) else emptySet<String>(),counts.pending)
                assertEquals(if(task==verification)setOf(task.id) else emptySet<String>(),counts.verification)
                assertTrue(counts.pending.intersect(counts.verification).isEmpty())
            }
        }
    }

}
