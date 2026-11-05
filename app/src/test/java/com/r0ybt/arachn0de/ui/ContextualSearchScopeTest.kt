package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.access.ElementResult
import org.junit.Assert.*
import org.junit.Test
import com.r0ybt.arachn0de.domain.model.*

class ContextualSearchScopeTest {
    private fun result(key:String,project:String?,parent:String?,description:String="") =
        ElementResult(key,project,key.takeIf {it.startsWith("node:")},"Título",description,"Capa","Ruta",true,parent)
    private val index=listOf(result("project:p","p",null),result("node:a","p","project:p"),
        result("node:b","p","node:a","coincidencia profunda"),result("node:c","p","project:p"),
        result("project:q","q",null),result("node:d","q","project:q"),result("node:draft",null,null))
    @Test fun dashboardIncludesCoresAndDescendantsWithoutDrafts() {
        assertEquals(6,contextualResults(index,"título",null,null).size)
        assertTrue(contextualResults(index,"",null,null).isEmpty())
    }
    @Test fun coreLayerAndNestedLayerStayInTheirOwnBranch() {
        assertEquals(setOf("project:p","node:a","node:b","node:c"),contextualResults(index,"titulo","p",null).map {it.key}.toSet())
        assertEquals(setOf("node:a","node:b"),contextualResults(index,"titulo","p","a").map {it.key}.toSet())
        assertTrue(contextualResults(index,"profunda","p","b").isEmpty())
        assertEquals(listOf("node:b"),contextualResults(index,"titulo","p","b").map {it.key})
        assertTrue(contextualResults(index,"titulo","q","a").isEmpty())
    }
    @Test fun cyclesAndMissingParentsCannotEscapeTheBranch() {
        val broken=index+result("node:x","p","node:y")+result("node:y","p","node:x")+result("node:z","p","node:missing")
        assertEquals(setOf("node:a","node:b"),contextualResults(broken,"titulo","p","a").map {it.key}.toSet())
    }
    @Test fun inlineCardsMatchOnlyNamesWithinDescendantsAndKeepExistingFiltersAndOrder() {
        val root=Node("root","p",null,"Café superior","",false,0,1,1,true,purpose=NodePurpose.LAYER)
        val child=root.copy(id="child",parentId="root",title="Subcapa")
        val first=root.copy(id="first",parentId="child",title="Café profundo",hasChildren=false,purpose=NodePurpose.ACTION,priority=Priority.HIGH)
        val second=first.copy(id="second",parentId="root",title="Café directo",position=1,priority=Priority.NONE)
        val sibling=first.copy(id="sibling",parentId=null)
        val descriptionOnly=first.copy(id="description",title="Otro",description="café",position=2)
        val foreign=first.copy(id="foreign",projectId="q",parentId=null)
        val tree=NodeTreeSnapshot(listOf(root,child,first,second,sibling,descriptionOnly,foreign))
        val before=tree.nodes.toList()
        val filter=NodeFilter(priority=Priority.HIGH)
        assertEquals(listOf(first.id),inlineNameRows(tree,"p","root","cafe",filter).map {it.node.id})
        assertEquals(listOf(first.id,second.id),inlineNameRows(tree,"p","root","CAFÉ").map {it.node.id})
        assertEquals("Núcleo › Café superior › Subcapa",originalNodePath(first,tree,mapOf("p" to "Núcleo")))
        assertEquals(before,tree.nodes)
        assertEquals(Priority.HIGH,filter.priority)
    }
}
