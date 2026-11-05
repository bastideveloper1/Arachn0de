package com.r0ybt.arachn0de.slots

import org.junit.Assert.*
import org.junit.Test

class SavingsRenderingTest {
    @Test fun committedSymbolsRemainExactAfterAnimationAndRecovery() {
        val table=SavingsPayTable()
        for(symbol in 0..7) {
            val result=List(3) {symbol}
            val machine=SlotMachine()
            assertTrue(machine.animateCommitted(result))
            assertFalse(machine.animateCommitted(listOf(0,1,2)))
            assertEquals(result,machine.results)
            machine.advance(4200)
            result.forEachIndexed {i,s->assertEquals(s.toFloat(),machine.position(i),0f)}
            assertEquals(table.prizeUnits[symbol],table.prize(machine.results))
            assertEquals(0L,machine.credits)
            val restored=SlotMachine()
            restored.showCommitted(result)
            assertEquals(SlotMachine.Phase.FINISHED,restored.phase)
            result.forEachIndexed {i,s->assertEquals(s.toFloat(),restored.position(i),0f)}
        }
    }
}
