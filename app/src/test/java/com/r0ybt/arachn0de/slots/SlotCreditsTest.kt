package com.r0ybt.arachn0de.slots

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class SlotCreditsTest {
    @Test fun coinAddsFiveOnceAndEachCompletedSpinSettlesOnce() {
        val m=SlotMachine(Random(1),creditsEnabled=true)
        assertFalse(m.spin());assertEquals(0L,m.credits)
        assertTrue(m.insert());assertFalse(m.insert())
        assertEquals(0L,m.credits)
        m.inserted();m.inserted();assertEquals(5L,m.credits)
        var turns=0
        while(m.credits>0 && turns++<1000) {
            val before=m.credits
            assertTrue(m.spin());assertEquals(before-1,m.credits)
            assertFalse(m.spin());assertFalse(m.insert())
            m.advance(4199);assertEquals(before-1,m.credits)
            m.advance(1)
            assertEquals(before-1+m.lastPrize,m.credits)
            val settled=m.credits
            m.advance(4200);assertEquals(settled,m.credits)
        }
        assertEquals(0L,m.credits);assertFalse(m.spin())
        assertTrue(m.insert());m.inserted();assertEquals(5L,m.credits)
    }
    @Test fun paylineRequiresThreeMatchingSymbolsAndHasExplicitPayouts() {
        for(i in 0..7)assertEquals(SlotMachine.payouts[i],SlotMachine.prize(listOf(i,i,i)))
        assertEquals(0,SlotMachine.prize(listOf(0,0,1)))
        assertEquals(0,SlotMachine.prize(listOf(1,2,3)))
        assertEquals(20,SlotMachine.prize(listOf(7,7,7)))
    }
    @Test fun topUpDoesNotReplaceExistingCreditsAndNewSessionsStartEmpty() {
        val m=SlotMachine(creditsEnabled=true)
        m.insert();m.inserted()
        assertTrue(m.insert());m.inserted();assertEquals(10L,m.credits)
        assertEquals(0L,SlotMachine(creditsEnabled=true).credits)
        val tasks=SlotMachine()
        tasks.insert();tasks.inserted();tasks.spin();tasks.advance(4200)
        assertEquals(0L,tasks.credits);assertFalse(tasks.spin())
    }
}
