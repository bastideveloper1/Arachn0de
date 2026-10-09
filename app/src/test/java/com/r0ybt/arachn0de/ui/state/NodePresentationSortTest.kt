package com.r0ybt.arachn0de.ui.state

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class NodePresentationSortTest {
    private fun node(id: String, position: Int = 0, due: Long? = null, priority: Priority = Priority.NONE, created: Long = 100) =
        Node(id,"p",null,id,"",false,position,created,200,false,dueAt=due,priority=priority)
    private fun ids(nodes: List<Node>, mode: NodeSortMode) = NodePresentationSort.children(nodes,mode).map { it.id }
    @Test fun layersFirstUsesManualLayerSlotsAndAutomaticTaskOrderAndCanBeDisabled() {
        val first=node("first",9).copy(purpose=NodePurpose.LAYER,dueAt=1)
        val second=node("second",1).copy(purpose=NodePurpose.LAYER,dueAt=100)
        val task=node("task",2,due=2)
        val input=listOf(first,task,second)
        assertEquals(listOf("second","first","task"),NodePresentationSort.children(input,NodeSortMode.DUE_ASC,layersFirst=true).map {it.id})
        assertEquals(listOf("first","task","second"),NodePresentationSort.children(input,NodeSortMode.DUE_ASC,layersFirst=false).map {it.id})
    }
    @Test fun historyOrdersOnlyManualCompletedTasksAndPreservesFilterContext() {
        val pendingA=node("p1",0);val pendingB=node("p2",1)
        val old=node("old",2,1).copy(isCompleted=true)
        val recent=node("recent",3,2).copy(isCompleted=true)
        val unknown=node("unknown",4).copy(isCompleted=true)
        val times=mapOf("old" to 10L,"recent" to 20L)
        val input=listOf(unknown,recent,old,pendingB,pendingA)
        assertEquals(listOf("p1","p2","recent","old","unknown"),NodePresentationSort.children(input,NodeSortMode.MANUAL,times).map { it.id })
        assertEquals(listOf("p1","p2","old","recent","unknown"),NodePresentationSort.children(input,NodeSortMode.DUE_ASC,times).map { it.id })
        val layer=node("layer").copy(purpose=NodePurpose.LAYER)
        val rows=listOf(FilteredNodeRow(layer,0,false),FilteredNodeRow(old.copy(parentId=layer.id),1,true),FilteredNodeRow(recent.copy(parentId=layer.id),1,true))
        val sorted=NodePresentationSort.filtered(rows,NodeSortMode.MANUAL,times)
        assertEquals(listOf("layer","recent","old"),sorted.map { it.node.id })
        assertEquals(listOf(0,1,1),sorted.map { it.depth })
    }
    @Test fun dueAndPriorityUsesDatesThenRankThenStableTies() {
        val input=listOf(node("none",due=null,priority=Priority.HIGH),node("low",due=5,priority=Priority.LOW),node("high",due=5,priority=Priority.HIGH),node("early",due=1),node("tie",position=1,due=5,priority=Priority.HIGH))
        assertEquals(listOf("early","high","tie","low","none"),ids(input,NodeSortMode.DUE_PRIORITY))
        assertEquals(ids(input,NodeSortMode.DUE_PRIORITY),ids(input.reversed(),NodeSortMode.DUE_PRIORITY))
    }
    @Test fun manualAndAutomaticModesNeverMutatePositionsOrInput() {
        val input=listOf(node("A",0,15),node("B",1),node("C",2,5),node("D",3,20))
        val original=input.map { it.copy() }
        assertEquals(listOf("A","B","C","D"),ids(input,NodeSortMode.MANUAL))
        assertEquals(listOf("C","A","D","B"),ids(input,NodeSortMode.DUE_ASC))
        assertEquals(listOf("D","A","C","B"),ids(input,NodeSortMode.DUE_DESC))
        NodeSortMode.entries.forEach { ids(input,it) }
        assertEquals(original,input)
        assertEquals(listOf("A","B","C","D"),ids(input,NodeSortMode.MANUAL))
    }
    @Test fun priorityUsesExplicitRankThenDueNullLastAndManualTies() {
        val input=listOf(node("A",0),node("B",1,15,Priority.HIGH),node("C",2,1,Priority.LOW),
            node("D",3,1,Priority.MEDIUM),node("E",4,5,Priority.HIGH),node("F",5,null,Priority.HIGH))
        assertEquals(listOf("E","B","F","D","C","A"),ids(input,NodeSortMode.PRIORITY))
    }
    @Test fun datesCompareInstantsSafelyIncludingLongExtremesAndNulls() {
        val input=listOf(node("null"),node("max",due=Long.MAX_VALUE),node("min",due=Long.MIN_VALUE),node("zero",due=0))
        assertEquals(listOf("min","zero","max","null"),ids(input,NodeSortMode.DUE_ASC))
        assertEquals(listOf("max","zero","min","null"),ids(input,NodeSortMode.DUE_DESC))
    }
    @Test fun dueTiesUsePositionCreatedThenIdentityAndAreIndependentOfInputOrder() {
        val input=listOf(node("z",1,5,created=1),node("b",0,5,created=2),node("a",0,5,created=2),node("old",0,5,created=1))
        for(mode in listOf(NodeSortMode.DUE_ASC,NodeSortMode.DUE_DESC,NodeSortMode.PRIORITY)) {
            assertEquals(listOf("old","a","b","z"),ids(input,mode))
            assertEquals(ids(input,mode),ids(input.reversed(),mode))
        }
    }
    @Test fun createdUsesRealTimestampThenPositionAndIdNotUpdatedOrDue() {
        val input=listOf(node("a",2,1,created=10),node("b",1,1000,created=10),node("c",0,created=20),node("d",1,created=10).copy(updatedAt=Long.MAX_VALUE))
        assertEquals(listOf("c","b","d","a"),ids(input,NodeSortMode.CREATED_NEWEST))
        assertEquals(listOf("b","d","a","c"),ids(input,NodeSortMode.CREATED_OLDEST))
    }
    @Test fun notesAndLayersKeepMembershipAndUseEffectivePriorityWithoutDerivedDates() {
        val layer=node("layer",1,priority=Priority.HIGH).copy(hasChildren=true,purpose=NodePurpose.LAYER)
        val child=node("child",due=1).copy(parentId="layer")
        val note=node("note",2,priority=Priority.HIGH).copy(purpose=NodePurpose.NOTE)
        val task=node("task",3,20,Priority.LOW).copy(obligation=Obligation(100,"CLP"))
        val tree=NodeTreeSnapshot(listOf(layer,child,note,task))
        assertEquals(listOf("task","layer","note"),ids(tree.childrenOf(null),NodeSortMode.DUE_ASC))
        assertEquals(listOf("task","layer","note"),ids(tree.childrenOf(null),NodeSortMode.PRIORITY))
        assertNull(layer.dueAt)
    }
    @Test fun existingCompletionSectionsRemainAndEachSectionSortsNormally() {
        val nodes=listOf(node("pending15",0,15),node("done1",1,1).copy(isCompleted=true),node("pending5",2,5),node("done20",3,20).copy(isCompleted=true))
        assertEquals(listOf("pending5","pending15","done1","done20"),ids(nodes,NodeSortMode.DUE_ASC))
        assertEquals(listOf("pending15","pending5","done20","done1"),ids(nodes,NodeSortMode.DUE_DESC))
    }
    @Test fun tagFilterThenSortRetainsMinimalAncestorsAndOnlyReordersSiblings() {
        val nodes=listOf(node("L1",1).copy(hasChildren=true,purpose=NodePurpose.LAYER),node("L2",0).copy(hasChildren=true,purpose=NodePurpose.LAYER),
            node("late",0,15).copy(parentId="L1"),node("early",1,5).copy(parentId="L1"),
            node("middle",0,10).copy(parentId="L2"),node("excluded",2,1).copy(parentId="L1"))
        val filtered=ScopedNodeFilter.apply(NodeTreeSnapshot(nodes),"p",null,NodeFilter(tagId="tag"),emptyMap(),mapOf("late" to setOf("tag"),"early" to setOf("tag"),"middle" to setOf("tag")))
        val sorted=NodePresentationSort.filtered(filtered,NodeSortMode.DUE_ASC)
        assertEquals(listOf("L2","middle","L1","early","late"),sorted.map { it.node.id })
        assertEquals(listOf(0,1,0,1,1),sorted.map { it.depth })
        assertEquals(listOf(false,true,false,true,true),sorted.map { it.isMatch })
        assertEquals(filtered.toSet(),sorted.toSet())
        assertEquals(filtered,NodePresentationSort.filtered(filtered,NodeSortMode.MANUAL))
    }
    @Test fun personStateAndTimeFiltersRemainSeparateFromSort() {
        val nodes=listOf(node("a",0,15,Priority.LOW,30),node("b",1,5,Priority.HIGH,20),node("c",2,10,Priority.MEDIUM,10).copy(isCompleted=true),node("d",3,null,Priority.HIGH,40))
        val tree=NodeTreeSnapshot(nodes)
        fun rows(filter:NodeFilter,mode:NodeSortMode)=NodePresentationSort.filtered(ScopedNodeFilter.apply(tree,"p",null,filter,mapOf("a" to setOf("person"),"b" to setOf("person")),emptyMap()),mode).map { it.node.id }
        assertEquals(listOf("b","a"),rows(NodeFilter(personId="person"),NodeSortMode.PRIORITY))
        assertEquals(listOf("d","a","b"),rows(NodeFilter(completion=CompletionFilter.PENDING),NodeSortMode.CREATED_NEWEST))
        assertEquals(listOf("a","b"),rows(NodeFilter(range=TemporalRange(5,20),completion=CompletionFilter.PENDING),NodeSortMode.MANUAL))
        assertEquals(listOf("c"),rows(NodeFilter(completion=CompletionFilter.COMPLETED),NodeSortMode.CREATED_NEWEST))
    }
    @Test fun scopeExcludesOtherProjectsAndDoesNotSortAncestorsIntoCurrentLayer() {
        val layer=node("layer").copy(hasChildren=true,purpose=NodePurpose.LAYER)
        val nodes=listOf(layer,node("late",0,20).copy(parentId="layer"),node("early",1,5).copy(parentId="layer"),node("foreign").copy(projectId="q"))
        val rows=ScopedNodeFilter.apply(NodeTreeSnapshot(nodes),"p","layer",NodeFilter(),emptyMap(),emptyMap())
        assertEquals(listOf("early","late"),NodePresentationSort.filtered(rows,NodeSortMode.DUE_ASC).map { it.node.id })
    }
    @Test fun largeListAndDeepFilteredHierarchyAreDeterministicWithoutRecursion() {
        val nodes=List(20000) { node("id$it",it,if(it%5==0)null else (20000-it).toLong()) }
        val sorted=NodePresentationSort.children(nodes.reversed(),NodeSortMode.DUE_ASC)
        assertEquals(20000,sorted.size)
        assertTrue(sorted.take(16000).zipWithNext().all { (a,b) -> a.dueAt!! < b.dueAt!! })
        assertTrue(sorted.drop(16000).all { it.dueAt == null })
        assertEquals(sorted,NodePresentationSort.children(nodes,NodeSortMode.DUE_ASC))
        val deep=List(12000) { FilteredNodeRow(node("n$it").copy(parentId=if(it==0)null else "n${it-1}"),it,it==11999) }
        assertEquals(deep,NodePresentationSort.filtered(deep,NodeSortMode.PRIORITY))
    }
    @Test fun datedBatchUsesActualDueDatesAndReturningToManualRestoresOriginalPositions() {
        val specs=NodeBatchGenerator.generate(NodeBatchParameters("Cuota",3,NumberingMode.SUFFIX,1,
            temporalRule=BatchTemporalRule.MONTHLY,firstDueAt=1769853600000),java.util.TimeZone.getTimeZone("UTC"))
        val nodes=specs.mapIndexed { index,spec -> node(spec.title,2-index,spec.dueAt).copy(creationGroupId="group") }
        val original=nodes.map { it.copy() }
        assertEquals(listOf("Cuota 1","Cuota 2","Cuota 3"),ids(nodes,NodeSortMode.DUE_ASC))
        assertEquals(listOf("Cuota 3","Cuota 2","Cuota 1"),ids(nodes,NodeSortMode.MANUAL))
        assertEquals(original,nodes)
    }

}
