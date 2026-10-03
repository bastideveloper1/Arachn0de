package com.r0ybt.arachn0de.export

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ProjectExportTest {
    private val project=Project("private-p","Arachn0de","Proyecto 🕷",0,100,200)
    private fun node(id:String,parent:String?=null,title:String=id,position:Int=0,purpose:NodePurpose=NodePurpose.ACTION,completed:Boolean=false)=
        Node(id,project.id,parent,title,"",completed,position,100,200,false,dueAt=300,purpose=purpose)
    private fun render(tree:NodeTreeSnapshot,descendants:Boolean)=NodeMarkdownRenderer.render(NodeExportSnapshot.captureProject(project,tree,descendants))
    @Test fun projectOnlyAndWholeTreeUseExistingMarkdownSemanticsAndExcludePrivateMetadata() {
        val tree=NodeTreeSnapshot(listOf(node("layer",title="Android"),node("a","layer","Tarea A",position=0),node("b","layer","Nota B",1,NodePurpose.NOTE),node("other",title="Other").copy(projectId="other-p")))
        assertEquals("# Arachn0de\n\nProyecto 🕷\n",render(tree,false))
        val text=render(tree,true)
        assertTrue(text.contains("- ## Android"));assertTrue(text.contains("    - [ ] Tarea A"));assertTrue(text.contains("    - **Nota: Nota B**"))
        listOf("private-p","other-p","Other","100","200","300").forEach { assertFalse(text.contains(it)) }
        val snapshot=NodeExportSnapshot.captureProject(project,tree,true)
        assertEquals(listOf(0,1,2,2),snapshot.entries.map { it.depth })
        assertEquals(listOf(ExportKind.LAYER,ExportKind.LAYER,ExportKind.ACTION,ExportKind.NOTE),snapshot.entries.map { it.kind })
    }
    @Test fun emptyProjectHasSameRepresentationForBothScopes() {
        assertEquals(render(NodeTreeSnapshot(emptyList()),false),render(NodeTreeSnapshot(emptyList()),true))
    }
    @Test fun rootAndSiblingOrderFollowExistingCompletionAndPositionRules() {
        val nodes=listOf(node("done",position=0,completed=true),node("note",position=2,purpose=NodePurpose.NOTE),node("pending",position=1),node("nested","pending"))
        val tree=NodeTreeSnapshot(nodes)
        assertEquals(listOf("Arachn0de","pending","nested","note","done"),NodeExportSnapshot.captureProject(project,tree,true).entries.map { it.title })
        assertEquals(render(tree,true),render(NodeTreeSnapshot(nodes.reversed()),true))
    }
    @Test fun deepTreePreservesBoundedIndentAndExplicitLevelsWithoutRecursion() {
        val nodes=List(2500) { node("n$it",if(it==0)null else "n${it-1}","N") }
        val snapshot=NodeExportSnapshot.captureProject(project,NodeTreeSnapshot(nodes),true)
        assertEquals(2501,snapshot.entries.size)
        val text=NodeMarkdownRenderer.render(snapshot)
        assertTrue(text.contains("nivel 2501"));assertFalse(text.contains("#######"))
        assertTrue(text.lines().all { it.takeWhile { c -> c==' ' }.length<=24 })
    }
    @Test fun projectAndAggregateAndEscapedOutputRespectExistingLimits() {
        val empty=NodeTreeSnapshot(emptyList())
        assertTrue(runCatching { NodeExportSnapshot.captureProject(project.copy(description="x".repeat(200001)),empty,false) }.exceptionOrNull() is ContextTooLargeException)
        val tree=NodeTreeSnapshot(List(5) { node("$it").copy(description="x".repeat(45000)) })
        assertTrue(runCatching { NodeExportSnapshot.captureProject(project,tree,true) }.exceptionOrNull() is ContextTooLargeException)
        val snapshot=NodeExportSnapshot.captureProject(project.copy(description="*".repeat(100001)),empty,false)
        assertTrue(runCatching { NodeMarkdownRenderer.render(snapshot) }.exceptionOrNull() is ContextTooLargeException)
    }
    @Test fun cancellationAndImmutableSnapshotRetainExistingGuarantees() {
        val list=mutableListOf(node("a",title="Original"));val tree=NodeTreeSnapshot(list)
        val captured=NodeExportSnapshot.captureProject(project,tree,true);list.clear()
        assertEquals("Original",captured.entries[1].title)
        assertTrue(runCatching { (captured.entries as MutableList).clear() }.isFailure)
        assertTrue(runCatching { NodeExportSnapshot.captureProject(project,tree,true) { throw java.util.concurrent.CancellationException() } }.exceptionOrNull() is java.util.concurrent.CancellationException)
    }
}
