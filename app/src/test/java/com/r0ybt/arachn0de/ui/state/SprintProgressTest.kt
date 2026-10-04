package com.r0ybt.arachn0de.ui.state

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class SprintProgressTest {
    private fun task(id: String, phase: WorkState?) = Node(id,"p","sprint",id,"",false,0,1,1,false,workState=phase)

    @Test fun uniformPhasesKeepExactWeightsForAnyTaskCount() {
        val expected=listOf(0.0,25.0,50.0,75.0,100.0)
        for ((index,phase) in WorkState.entries.withIndex()) {
            for (count in listOf(1,5,20,100)) {
                val tasks=(1..count).map { task("$it",phase) }
                assertEquals(expected[index],sprintProgressPercentage(tasks,"sprint")!!,0.00001)
                assertEquals(expected[index],sprintProgressPercentage(tasks.map { it.copy(isCompleted=true) },"sprint")!!,0.00001)
            }
        }
    }
    @Test fun mixedPhasesHaveEqualWeightAndUpdateWhenPhaseChanges() {
        val tasks=listOf(task("a",WorkState.UNPLANNED),task("b",WorkState.PLANNED),task("c",WorkState.DOING),task("d",WorkState.VALIDATED))
        val percentage=sprintProgressPercentage(tasks,"sprint")!!
        assertEquals(43.75,percentage,0.00001);assertEquals(44,percentage.roundToInt())
        val changed=tasks.map { if(it.id=="a") it.copy(workState=WorkState.PLANNED) else it }
        assertEquals(50.0,sprintProgressPercentage(changed,"sprint")!!,0.00001)
    }
    @Test fun onlyDirectActionsWithSprintPhaseCountAndEmptyHasNoPercentage() {
        val excluded=listOf(task("normal",null),task("other",WorkState.VALIDATED).copy(parentId="other"),
            task("nested",WorkState.VALIDATED).copy(parentId="sublayer"),task("note",null).copy(purpose=NodePurpose.NOTE),
            task("sublayer",null).copy(purpose=NodePurpose.LAYER))
        assertNull(sprintProgressPercentage(emptyList(),"sprint"))
        assertNull(sprintProgressPercentage(excluded,"sprint"))
        assertEquals(25.0,sprintProgressPercentage(excluded+task("included",WorkState.PLANNED),"sprint")!!,0.00001)
    }
}
