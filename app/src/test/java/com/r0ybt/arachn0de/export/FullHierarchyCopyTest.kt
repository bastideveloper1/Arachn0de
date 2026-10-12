package com.r0ybt.arachn0de.export

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Test
import org.junit.Assert.*

class FullHierarchyCopyTest {
    private fun node(id:String,parent:String?=null,layer:Boolean=false)=Node(id,"p",parent,id,"",false,0,1,1,false).copy(purpose=if(layer) NodePurpose.LAYER else NodePurpose.ACTION)
    private fun render(tree:NodeTreeSnapshot,id:String)=PendingChatRenderer.render(tree,id,completeHierarchy=true)
    @Test fun emptyLayerAndMultilineContentAreComplete() {
        val n=node("root",layer=true).copy(title="Título\nsegunda línea",description="\nDescripción\n\nfinal\n")
        val text=render(NodeTreeSnapshot(listOf(n)),n.id)
        assertTrue(text.contains("Título\nsegunda línea"));assertTrue(text.contains("  Descripción\n  \n  final\n"))
        assertFalse(text.contains("Sin pendientes"))
    }
    @Test fun allNodesIncludingCompletedNotesAndEmptyLayersKeepSnapshotOrderAndTree() {
        val nodes=listOf(node("root",layer=true),node("layer","root",true),node("done","layer").copy(isCompleted=true),node("note","layer").copy(purpose=NodePurpose.NOTE),node("empty","root",true).copy(position=2))
        val tree=NodeTreeSnapshot(nodes);val before=tree.nodes.toList();val text=render(tree,"root")
        assertTrue(text.contains("[x] done"));assertTrue(text.contains("Nota: note"));assertTrue(text.contains("empty"))
        val order=listOf("root","layer","note","done","empty")
        assertEquals(order.map {text.indexOf(it)}.sorted(),order.map {text.indexOf(it)})
        assertEquals(before,tree.nodes);assertEquals(1,Regex("\\[x] done").findAll(text).count())
    }
    @Test fun multilineTaskTitleDoesNotLookLikeDuplicateTasks() {
        val text=render(NodeTreeSnapshot(listOf(node("t").copy(title="Primera\nSegunda"))),"t")
        assertTrue(text.contains("[ ] Primera\n    Segunda"))
        assertEquals(1,Regex("\\[ \\]").findAll(text).count())
    }
    @Test fun taskMetadataIsRetainedWithoutAddingLayerAssociations() {
        val root=node("root",layer=true)
        val task=node("task","root").copy(priority=Priority.HIGH,dueAt=1800000000000)
        val text=PendingChatRenderer.render(NodeTreeSnapshot(listOf(root,task)),"root",people=mapOf("root" to listOf(Person("a","Layer private person",null)),"task" to listOf(Person("b","Task person",null))),completeHierarchy=true)
        assertTrue(text.contains("Responsables: Task person"));assertTrue(text.contains("Vence:"));assertTrue(text.contains("Prioridad: Alta"))
        assertFalse(text.contains("Layer private person"));assertFalse(text.contains("1800000000000"))
    }
    @Test fun deepTreesUseExplicitLevelsWithoutRecursionOrQuadraticIndentation() {
        val nodes=(0..600).map {node("n$it",if(it==0) null else "n${it-1}",it<600)}
        val text=render(NodeTreeSnapshot(nodes),"n0")
        assertTrue(text.contains("Nivel 601"));assertTrue(text.contains("[ ] n600"));assertTrue(text.length<100000)
    }
    @Test fun largeSafeCopyAndOverflowAreExplicit() {
        val root=node("root",layer=true).copy(description="d".repeat(180000))
        assertTrue(render(NodeTreeSnapshot(listOf(root)),"root").contains(root.description))
        assertThrows(ContextTooLargeException::class.java) {render(NodeTreeSnapshot(listOf(root.copy(description="d".repeat(200001)))),"root")}
        assertThrows(ContextTooLargeException::class.java) {render(NodeTreeSnapshot(listOf(root.copy(description="line\n".repeat(35000)))),"root")}
    }
    @Test fun projectIncludesFullDescriptionAndAllRoots() {
        val project=Project("p","Proyecto\ncompleto","Descripción\ncompleta",1,1)
        val text=PendingChatRenderer.render(NodeTreeSnapshot(listOf(node("first"),node("second").copy(isCompleted=true))),null,project,completeHierarchy=true)
        assertTrue(text.contains(project.name));assertTrue(text.contains(project.description));assertTrue(text.contains("[x] second"))
    }
    @Test fun pendingFilterKeepsContainersNotesMultilineAndSprintCompletion() {
        val nodes=listOf(node("root",layer=true),node("nested","root",true),
            node("pending","nested").copy(title="Pendiente\nsegunda",description="Descripción\nlarga",workState=WorkState.DOING),
            node("done","nested").copy(isCompleted=true,workState=WorkState.DONE),
            node("validated","nested").copy(isCompleted=true,workState=WorkState.VALIDATED),
            node("note","nested").copy(purpose=NodePurpose.NOTE),node("empty","root",true))
        val tree=NodeTreeSnapshot(nodes)
        val text=PendingChatRenderer.render(tree,"root",completeHierarchy=true,onlyPending=true)
        assertTrue(text.contains("nested"));assertTrue(text.contains("empty"));assertTrue(text.contains("Nota: note"))
        assertTrue(text.contains("[ ] Pendiente\n        segunda"));assertTrue(text.contains("Descripción\n      larga"))
        assertFalse(text.contains("[x] done"));assertFalse(text.contains("validated"));assertEquals(nodes,tree.nodes)
        assertTrue(render(tree,"root").contains("[x] validated"))
    }
    @Test fun pendingFilterRejectsOversizeWithoutReturningTruncatedText() {
        val root=node("root",layer=true).copy(description="d".repeat(200001))
        assertThrows(ContextTooLargeException::class.java) {PendingChatRenderer.render(NodeTreeSnapshot(listOf(root)),"root",completeHierarchy=true,onlyPending=true)}
    }

}
