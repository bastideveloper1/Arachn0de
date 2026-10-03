package com.r0ybt.arachn0de.export

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class NodeExportTest {
    private fun node(id: String, parent: String? = null, title: String = id, description: String = "",
        completed: Boolean = false, position: Int = 0, purpose: NodePurpose = NodePurpose.ACTION) =
        Node(id,"private-project-id",parent,title,description,completed,position,123,456,false,
            startAt=789,dueAt=900,purpose=purpose)
    private fun export(nodes: List<Node>, id: String, descendants: Boolean = true) =
        NodeMarkdownRenderer.render(NodeExportSnapshot.capture(NodeTreeSnapshot(nodes),id,descendants))

    @Test fun actualSemanticsDescriptionsAndScopesExcludePrivateFields() {
        val nodes = listOf(node("root", title="Bugs 🕷", description="Problemas encontrados."),
            node("a","root","Overlay drag","Aparece al mover hacia arriba.\nSegunda línea.",position=0),
            node("b","root","Orden final",completed=true,position=1),
            node("note","root","Investigación","El orden parece correcto.",position=2,purpose=NodePurpose.NOTE),
            node("money","root","Cuota",position=3).copy(obligation=Obligation(987654321,"USD")))
        val text=export(nodes,"root")
        assertTrue(text.startsWith("# Bugs 🕷\n")); assertTrue(text.contains("- [ ] Overlay drag"))
        assertTrue(text.contains("- [x] Orden final")); assertTrue(text.contains("**Nota: Investigación**"))
        assertTrue(text.contains("Aparece al mover hacia arriba.\n    Segunda línea."))
        assertTrue(text.contains("- [ ] Cuota"))
        listOf("root","private-project-id","987654321","USD","123","456","789","900","null","Descripción:").forEach {
            assertFalse("Excluded: $it",text.contains(it))
        }
        assertEquals("# Bugs 🕷\n\nProblemas encontrados.\n",export(nodes,"root",false))
        assertEquals("- [ ] Overlay drag\n\n    Aparece al mover hacia arriba.\n    Segunda línea.\n",export(nodes,"a",false))
        assertEquals("- [x] Orden final\n",export(nodes,"b"))
        assertTrue(export(nodes,"note").startsWith("# Nota: Investigación"))
        assertFalse(export(nodes,"note").contains("[ ]"))
    }
    @Test fun subtreeRootAndSiblingOrderReuseChildrenOfIncludingCompletionGroupsAndTies() {
        val nodes=listOf(node("outside"),node("root","outside",title="Exportado"),node("layer","root",position=2),
            node("pending","root",position=8),node("paid","root",position=0,completed=true),
            node("child","layer"),node("note","root",position=9,purpose=NodePurpose.NOTE))
        val tree=NodeTreeSnapshot(nodes)
        val snapshot=NodeExportSnapshot.capture(tree,"root",true)
        assertEquals(listOf("Exportado","layer","child","pending","note","paid"),snapshot.entries.map{it.title})
        assertEquals(tree.childrenOf("root").map{it.title}, snapshot.entries.filter{it.depth==1}.map{it.title})
        val text=NodeMarkdownRenderer.render(snapshot)
        assertTrue(text.startsWith("# Exportado"))
        assertTrue(text.contains("- ## layer\n\n    - [ ] child"))
        assertFalse(text.contains("outside"))
        assertEquals(text,export(nodes.reversed(),"root"))
    }
    @Test fun deeplyNestedHierarchyUsesValidHeadingsBoundedIndentAndExplicitLevels() {
        val nodes=List(3000){i->node("n$i",if(i==0)null else "n${i-1}")}
        val snapshot=NodeExportSnapshot.capture(NodeTreeSnapshot(nodes),"n0",true)
        val text=NodeMarkdownRenderer.render(snapshot)
        assertEquals(3000,snapshot.entries.size)
        assertTrue(text.contains("- ###### n5"))
        assertTrue(text.contains("**n6** *(nivel 7)*"))
        assertTrue(text.contains("- [ ] n2999 *(nivel 3000)*"))
        assertFalse(text.contains("#######"))
        assertTrue(text.lines().all{it.takeWhile{c->c==' '}.length<=24})
        assertTrue(export(nodes,"n2997").startsWith("# n2997"))
    }
    @Test fun wideTreeLongTitlesUnicodeAndProblematicTextRemainDeterministicAndImmutable() {
        val nodes=mutableListOf(node("root",title="Raíz"),node("symbols","root","[x] *Hola* 👩🏽‍💻",
            "# Sección falsa\n\n- Item falso\nTexto normal  \r\nÚltima línea",position=-1))
        nodes.addAll(List(4000){node("leaf$it","root",title="Elemento $it",position=it)})
        val snapshot=NodeExportSnapshot.capture(NodeTreeSnapshot(nodes),"root",true)
        val text=NodeMarkdownRenderer.render(snapshot)
        nodes.clear()
        assertEquals(4002,snapshot.entries.size)
        assertTrue(text.contains("👩🏽‍💻"))
        assertTrue(text.contains("\\[x\\] \\*Hola\\*"))
        assertTrue(text.contains("\\# Sección falsa"));assertTrue(text.contains("\\- Item falso"))
        assertFalse(text.lines().any{it.endsWith(' ')})
        assertTrue(text.endsWith("\n"));assertFalse(text.endsWith("\n\n"))
        assertEquals(text,NodeMarkdownRenderer.render(snapshot))
        assertThrows(UnsupportedOperationException::class.java){(snapshot.entries as MutableList).clear()}
        assertTrue(export(listOf(node("long",title="á".repeat(2000))),"long").contains("á".repeat(2000)))
    }
    @Test fun absentNodesAndOversizedContentFailWithoutTruncation() {
        assertThrows(IllegalArgumentException::class.java){NodeExportSnapshot.capture(NodeTreeSnapshot(emptyList()),"missing",true)}
        assertThrows(ContextTooLargeException::class.java){export(listOf(node("huge",description="x".repeat(200001))),"huge")}
        // Escaping and structural indentation also count toward the final transaction budget.
        assertThrows(ContextTooLargeException::class.java){export(listOf(node("escape",title="*".repeat(110000))),"escape")}
        assertEquals("- [ ] empty\n",export(listOf(node("empty",description=" \n ")),"empty"))
    }
}
