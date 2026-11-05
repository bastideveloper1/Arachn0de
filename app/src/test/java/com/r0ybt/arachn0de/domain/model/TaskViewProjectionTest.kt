package com.r0ybt.arachn0de.domain.model

import org.junit.Test
import org.junit.Assert.*

class TaskViewProjectionTest {
    private val project=Project("p","Núcleo","",0,0)
    private fun layer(id:String,parent:String?=null,mode:Int?=null)=Node(id,"p",parent,id,"",false,0,0,0,true,purpose=NodePurpose.LAYER,taskStateCount=mode)
    private fun task(id:String,parent:String?=null,status:TaskStatus=TaskStatus.PENDING)=Node(id,"p",parent,id,"",status==TaskStatus.COMPLETED,0,0,0,false,awaitingVerification=status==TaskStatus.VERIFY)
    @Test fun countsAreScopedDeduplicatedAndIndependentOfTheActiveViewWithEmptyLayersRetained() {
        val a=layer("a",mode=3);val b=layer("b","a");val empty=layer("empty")
        val pending=task("pending","b");val verify=task("verify","a",TaskStatus.VERIFY);val completed=task("completed",status=TaskStatus.COMPLETED)
        val outside=task("outside").copy(projectId="q");val draft=task("draft").copy(projectId=null)
        val tree=NodeTreeSnapshot(listOf(a,b,empty,pending,verify,completed,outside,draft))
        val eligible=tree.nodes.mapTo(hashSetOf()) {it.id}
        for(status in listOf(null)+TaskStatus.entries) {
            val view=TaskViewProjection.build(tree,project,null,eligible,status)
            assertEquals(3,view.total);TaskStatus.entries.forEach {assertEquals(1,view.counts[it])}
            assertTrue(view.verificationAvailable)
            assertEquals(if(status==null)3 else 1,view.matchingIds.size)
            assertTrue(view.rows.any {it.node.id==empty.id})
            assertEquals(0,view.containerCounts[empty.id] ?: 0)
            assertEquals(view.rows.size,view.rows.map {it.node.id}.distinct().size)
            assertFalse(view.rows.any {it.node.id in setOf("outside","draft")})
        }
        val scoped=TaskViewProjection.build(tree,project,"a",eligible,null)
        assertEquals(2,scoped.total);assertEquals(setOf("pending","verify"),scoped.matchingIds)
        assertEquals(2,scoped.containerCounts["a"]);assertEquals(1,scoped.containerCounts["b"])
    }
    @Test fun otherFiltersAffectCountsButNotModeAvailabilityAndAllRetainsNonTaskContent() {
        val a=layer("a",mode=3)
        val p=task("p1","a").copy(priority=Priority.HIGH)
        val v=task("v","a",TaskStatus.VERIFY)
        val note=task("note","a").copy(purpose=NodePurpose.NOTE)
        val tree=NodeTreeSnapshot(listOf(a,p,v,note))
        val high=tree.nodes.filter {NodeFilter(priority=Priority.HIGH).matches(it,emptySet())}.mapTo(hashSetOf()) {it.id}
        val filtered=TaskViewProjection.build(tree,project,null,high,TaskStatus.VERIFY)
        assertEquals(1,filtered.total);assertTrue(filtered.verificationAvailable);assertTrue(filtered.matchingIds.isEmpty())
        val all=TaskViewProjection.build(tree,project,null,setOf("note"),null)
        assertTrue(all.rows.any {it.node.id=="note"});assertEquals(0,all.total)
        assertFalse(TaskViewProjection.build(tree,project,null,setOf("note"),TaskStatus.PENDING).rows.any {it.node.id=="note"})
    }
    @Test fun closestOverridesDetermineWhetherVerificationExistsEvenWithoutVerificationTasks() {
        val a=layer("a",mode=3);val p=task("t","a")
        val empty=NodeTreeSnapshot(listOf(a))
        assertFalse(TaskViewProjection.build(empty,project,null,emptySet(),null).verificationAvailable)
        val inherited=NodeTreeSnapshot(listOf(a,p))
        assertTrue(TaskViewProjection.build(inherited,project,null,setOf(p.id),null).verificationAvailable)
        val exception=NodeTreeSnapshot(listOf(a,p.copy(taskStateCount=2)))
        assertFalse(TaskViewProjection.build(exception,project,null,setOf(p.id),null).verificationAvailable)
    }
}
