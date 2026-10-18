package com.r0ybt.arachn0de.export

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Test
import org.junit.Assert.*

class TaskPlainTextRendererTest {
    private val project=Project("p","Desarrollo","",0,0,0)
    private fun node(id:String,title:String,parent:String?=null,layer:Boolean=false,description:String="",done:Boolean=false)=
        Node(id,"p",parent,title,description,done,0,0,0,false,purpose=if(layer) NodePurpose.LAYER else NodePurpose.ACTION)
    @Test fun exactSingleTaskKeepsAbsolutePathMultilineTitleAndBodyWithoutMarkdownEscapes() {
        val task=node("t","Título completo\nSegunda línea","inner",description="Primera línea.\r\nSegunda *línea*\nhttps://example.org/a?x=1&y=2\n")
        val tree=NodeTreeSnapshot(listOf(node("layer","Interfaz",layer=true),node("inner","Tarjetas","layer",true),task))
        val text=TaskPlainTextRenderer.render(tree,listOf(task.id),mapOf("p" to project))
        assertEquals("Desarrollo/Interfaz/Tarjetas/\n${task.title}\nEstado: Pendiente\n│ ${task.description}",text)
        assertEquals(1,Regex("│ ").findAll(text).count());assertFalse(text.contains(TaskPlainTextRenderer.SEPARATOR))
    }
    @Test fun selectedOrderSeparatorRootTaskAndOptionalDescriptionAreExact() {
        val a=node("a","Primera",done=true);val b=node("b","Segunda")
        val tree=NodeTreeSnapshot(listOf(a,b))
        assertEquals("Desarrollo/\nSegunda\nEstado: Pendiente\n\n────────────────────\n\nDesarrollo/\nPrimera\nEstado: Completada",
            TaskPlainTextRenderer.render(tree,listOf("b","a"),mapOf("p" to project)))
    }
    @Test fun deepHierarchySlashesAndWorkflowRemainOriginalAndPendingFilterKeepsOrder() {
        val layers=(0..50).map {i->node("l$i",if(i==0) "Capa/Original" else "Nivel$i",if(i==0) null else "l${i-1}",true)}
        val pending=node("pending","Pendiente","l50").copy(workState=WorkState.DOING)
        val done=node("done","Terminada","l50",done=true)
        val tree=NodeTreeSnapshot(layers+pending+done)
        val ids=TaskPlainTextRenderer.branch(tree,"l50","p",onlyPending=true)
        assertEquals(listOf("l50","pending"),ids)
        val text=TaskPlainTextRenderer.render(tree,listOf("pending"),mapOf("p" to project))
        assertTrue(text.startsWith("Desarrollo/Capa/Original/Nivel1/"));assertTrue(text.endsWith("Nivel50/\nPendiente\nEstado: Haciendo"))
        assertEquals("Capa/Original",tree.nodesById.getValue("l0").title)
    }
    @Test fun excessiveOutputFailsInsteadOfTruncating() {
        val task=node("t","Título",description="x".repeat(NodeExportSnapshot.MAX_TEXT_CHARS))
        assertTrue(runCatching {TaskPlainTextRenderer.render(NodeTreeSnapshot(listOf(task)),listOf("t"),mapOf("p" to project))}.exceptionOrNull() is ContextTooLargeException)
    }
}
