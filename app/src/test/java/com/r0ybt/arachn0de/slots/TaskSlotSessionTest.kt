package com.r0ybt.arachn0de.slots

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Test
import org.junit.Assert.*
import kotlin.random.Random

class TaskSlotSessionTest {
    private val project=Project("p","Núcleo","",0,0,0,verificationEnabled=true)
    private fun node(id:String,parent:String?=null,status:TaskStatus=TaskStatus.PENDING,purpose:NodePurpose=NodePurpose.ACTION,mode:Int?=null,projectId:String?="p",children:Boolean=false)=
        Node(id,projectId,parent,id,"",status==TaskStatus.COMPLETED,0,0,0,children,purpose=purpose,awaitingVerification=status==TaskStatus.VERIFY,taskStateCount=mode)

    @Test fun eligibilityUsesDescendantsOverridesAndRealTaskStates() {
        val layer=node("layer",purpose=NodePurpose.LAYER,children=true)
        val nested=node("nested","layer",purpose=NodePurpose.LAYER,children=true,mode=2)
        val nodes=listOf(layer,nested,node("pending","nested"),node("verify","layer",TaskStatus.VERIFY),
            node("hidden-verify","nested",TaskStatus.VERIFY),node("override","nested",TaskStatus.VERIFY,mode=3),
            node("done",status=TaskStatus.COMPLETED),node("note",purpose=NodePurpose.NOTE),
            node("draft",projectId=null),node("other",projectId="other"))
        assertEquals(setOf("pending","verify","override"),SlotCandidates(listOf(project),nodes).pools()["p"]!!.map {it.id}.toSet())
    }

    @Test fun emptyPoolsRejectCoinAndStaleProjectPreservesIt() {
        val session=TaskSlotSession(Random(1))
        session.refresh(SlotCandidates(listOf(project),emptyList()))
        assertFalse(session.insert())
        session.refresh(SlotCandidates(listOf(project),listOf(node("a"))))
        assertTrue(session.insert());session.machine.inserted()
        session.refresh(SlotCandidates(emptyList(),emptyList()))
        assertFalse(session.spin())
        assertEquals(SlotMachine.Phase.READY,session.machine.phase)
        assertNotNull(session.message)
    }

    @Test fun oneSelectionPerCoinUniformCandidatesAndStableFinalResult() {
        val tasks=(0..4).map {node("task$it")}
        val data=SlotCandidates(listOf(project),tasks)
        val session=TaskSlotSession(Random(4))
        session.refresh(data)
        val counts=tasks.associate {it.id to 0}.toMutableMap()
        repeat(1000) {
            assertTrue(session.insert());session.machine.inserted()
            assertTrue(session.spin());assertFalse(session.spin());assertFalse(session.insert())
            val target=session.target
            session.choose(1)
            assertEquals(target,session.target)
            session.machine.advance(4199)
            assertEquals(target.toFloat(),session.position()%tasks.size,.001f)
            session.machine.advance(1)
            assertTrue(session.machine.lit())
            val result=session.result()!!
            counts[result.id]=counts.getValue(result.id)+1
            session.refresh(data)
            assertEquals(result,session.result())
            assertEquals("p",session.selectedProjectId)
        }
        counts.values.forEach {assertTrue("Uniform independent RNG smoke check: $counts",it in 130..270)}
    }

    @Test fun singleTaskAndInvalidatedResultNeverOpenAnInvalidDestination() {
        val session=TaskSlotSession(Random(1))
        val task=node("only")
        session.refresh(SlotCandidates(listOf(project),listOf(task)))
        session.insert();session.machine.inserted();session.spin();session.machine.advance(4200)
        assertEquals(task,session.result())
        session.refresh(SlotCandidates(listOf(project),listOf(task.copy(isCompleted=true))))
        assertNull(session.result())
        assertFalse(session.insert())
        session.refresh(SlotCandidates(listOf(project),listOf(task)))
        assertEquals(task,session.result())
        session.refresh(SlotCandidates(listOf(project),emptyList()))
        assertNull(session.result())
    }

    @Test fun selectorPreparesWithoutHiddenCoinAndArrowsNeverChangeAnActiveDraw() {
        val other=project.copy(id="other",name="Otro")
        val session=TaskSlotSession();session.refresh(SlotCandidates(listOf(project,other),listOf(node("a"),node("b",projectId="other"))))
        session.prepareSelection();assertEquals(SlotMachine.Phase.READY,session.machine.phase)
        session.choose(1);assertEquals("other",session.selectedProjectId)
        assertTrue(session.spin());session.choose(-1);assertEquals("other",session.selectedProjectId)
        session.machine.advance(4200);session.choose(-1);assertEquals("p",session.selectedProjectId)
        assertEquals(0L,session.machine.credits)
    }
    @Test fun modesHaveIndependentCoinsAndEngines() {
        val session=SlotsSession()
        session.traditional.insert();session.traditional.inserted();session.traditional.spin()
        assertEquals(SlotMachine.Phase.EMPTY,session.tasks.machine.phase)
        session.tasks.refresh(SlotCandidates(listOf(project),listOf(node("a"))))
        session.tasks.insert();session.tasks.machine.inserted();session.tasks.spin()
        session.tasks.machine.advance(4200)
        assertEquals(SlotMachine.Phase.SPINNING,session.traditional.phase)
        assertTrue(session.tasks.machine.lit())
    }
}
