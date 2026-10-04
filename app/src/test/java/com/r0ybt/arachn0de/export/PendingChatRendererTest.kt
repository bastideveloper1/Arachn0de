package com.r0ybt.arachn0de.export

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Test
import org.junit.Assert.*

class PendingChatRendererTest {
    private fun node(id:String,parent:String?=null,done:Boolean=false)=Node(id,"p",parent,id,"",done,0,1,1,false)
    @Test fun selectedLayerAndIncludedDescendantsKeepFullLongContent() {
        val title="Título largo ".repeat(200)+"FINAL TITULO"
        val description="Primera línea\n"+"Descripción extensa ".repeat(1000)+"\nFINAL DESCRIPCION"
        val root=node("root").copy(title=title,description=description,purpose=NodePurpose.LAYER)
        val layer=node("layer","root").copy(title="Capa hija $title",description="Hija $description",purpose=NodePurpose.LAYER)
        val task=node("task","layer").copy(title="Tarea $title",description="Tarea $description")
        val text=PendingChatRenderer.render(NodeTreeSnapshot(listOf(root,layer,task)),root.id)
        for(n in listOf(root,layer,task)) { assertTrue(text.contains(n.title.trim()));assertTrue(text.contains(n.description)) }
        val empty=PendingChatRenderer.render(NodeTreeSnapshot(listOf(root)),root.id)
        assertTrue(empty.contains(title.trim()));assertTrue(empty.contains(description));assertTrue(empty.contains("Sin pendientes"))
    }
    @Test fun excludesCompletedAndEmptyBranchesAndIncludesTaskDetails() {
        val tree=NodeTreeSnapshot(listOf(node("Root").copy(hasChildren=true,purpose=NodePurpose.LAYER),node("Empty","Root").copy(hasChildren=true,purpose=NodePurpose.LAYER),node("Done","Empty",true),node("Pending","Root").copy(dueAt=1800000000000,priority=Priority.HIGH)))
        val text=PendingChatRenderer.render(tree,"Root",people=mapOf("Pending" to listOf(Person("person","Roy",null))))
        assertTrue(text.contains("Root\n  • Pending"));assertTrue(text.contains("Responsables: Roy"));assertTrue(text.contains("Vence:"));assertTrue(text.contains("Prioridad: Alta"))
        assertFalse(text.contains("Done"));assertFalse(text.contains("Empty"));assertFalse(text.contains("person"))
    }
    @Test fun noPendingHasClearEmptyMessage() {
        assertTrue(PendingChatRenderer.render(NodeTreeSnapshot(listOf(node("Done",done=true))),"Done").contains("Sin pendientes"))
    }
    @Test(expected=ContextTooLargeException::class) fun respectsClipboardBudget() {
        PendingChatRenderer.render(NodeTreeSnapshot(listOf(node("x").copy(title="x".repeat(NodeExportSnapshot.MAX_TEXT_CHARS)))),"x")
    }
}
