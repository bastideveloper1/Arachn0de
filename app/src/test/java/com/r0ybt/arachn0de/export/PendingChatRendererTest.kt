package com.r0ybt.arachn0de.export

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Test
import org.junit.Assert.*

class PendingChatRendererTest {
    private fun node(id:String,parent:String?=null,done:Boolean=false)=Node(id,"p",parent,id,"",done,0,1,1,false)
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
