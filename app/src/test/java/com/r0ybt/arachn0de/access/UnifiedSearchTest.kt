package com.r0ybt.arachn0de.access

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class UnifiedSearchTest {
    private val project=Project("p","Núcleo","Documentación",0,1,2)
    private fun task(id:String,completed:Boolean=false)=Node(id,"p",null,id,"Descripción técnica",completed,0,1,2,false)
    private val index=ElementSearch.index(listOf(project),listOf(task("a"),task("b",true),task("draft").copy(projectId=null),task("note").copy(purpose=NodePurpose.NOTE)))
    private val access=AccessState(favorites=setOf("project:p","node:a","node:b"),recent=listOf("node:b","node:a"),counts=mapOf("node:a" to 1,"node:b" to 5))
    private val facts=SearchFacts(attention=setOf("node:a"),obligations=setOf("node:a","node:b"),recurring=setOf("node:a"),tags=mapOf("node:a" to setOf("tag"),"project:p" to setOf("tag")))
    private fun search(query:String="",mode:String="Buscar",criteria:SearchCriteria=SearchCriteria())=searchElements(index,query,mode,criteria,access,facts).map {it.key}
    @Test fun titleDescriptionDraftsAndNotesUseExistingIndex() {
        assertEquals(listOf("project:p"),search("NUCLEO"))
        assertTrue(search("tecnica").containsAll(listOf("node:a","node:draft","node:note")))
        assertEquals(listOf("node:draft"),search("draft"))
    }
    @Test fun criteriaIntersectAndResetRestoresAllWithoutText() {
        assertEquals(setOf("project:p","node:a","node:b"),search(criteria=SearchCriteria(favorites=true)).toSet())
        assertEquals(listOf("node:a"),search(criteria=SearchCriteria(attention=true)))
        assertEquals(listOf("node:a","node:b"),search(criteria=SearchCriteria(obligations=true)))
        assertEquals(listOf("node:a"),search(criteria=SearchCriteria(recurrences=true)))
        assertEquals(listOf("node:a"),search(criteria=SearchCriteria(true,true,true,true,"tag")))
        assertTrue(search("other",criteria=SearchCriteria(attention=true)).isEmpty())
        assertEquals(index.size,search().size)
        assertEquals(listOf("project:p","node:a"),search(criteria=SearchCriteria(tag="tag")))
    }
    @Test fun orderingKeepsEveryMatchingElementWithStableUnvisitedTail() {
        val all=index.map {it.key}
        assertEquals(all,search(mode="Inicio"))
        assertEquals(listOf("node:b","node:a")+all.filter {it !in setOf("node:b","node:a")},search(mode="Recientes"))
        assertEquals(listOf("node:b","node:a")+all.filter {it !in setOf("node:b","node:a")},search(mode="Frecuentes"))
        assertEquals(listOf("node:a"),search(mode="Recientes",criteria=SearchCriteria(attention=true)))
        assertEquals(listOf("node:a"),search(mode="Frecuentes",criteria=SearchCriteria(attention=true)))
        assertEquals(listOf("project:p"),search(mode="Inicio",query="nucleo"))
        assertEquals(setOf("project:p","node:a","node:b"),search(mode="Inicio",criteria=SearchCriteria(favorites=true)).toSet())
    }
    @Test fun preparedTextMatchesOriginalCaseAndAccentInsensitiveSearchForEveryCombination() {
        val prepared=index.associate {it.key to (ElementSearch.normalize(it.title)+" "+ElementSearch.normalize(it.description))}
        for(mode in listOf("Buscar","Recientes","Frecuentes"))for(query in listOf("","TECNICA","núcleo","none"))for(mask in 0..15) {
            val c=SearchCriteria(mask and 1!=0,mask and 2!=0,mask and 4!=0,mask and 8!=0)
            assertEquals(searchElements(index,query,mode,c,access,facts),searchElements(index,query,mode,c,access,facts,prepared))
        }
    }
}
