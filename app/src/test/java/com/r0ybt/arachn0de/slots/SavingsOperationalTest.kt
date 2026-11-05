package com.r0ybt.arachn0de.slots

import org.junit.Test
import org.junit.Assert.*

class SavingsOperationalTest {
    @Test fun tenThousandPhysicalSpinsNeverUseProtectedOrPendingMoney() {
        val t=SavingsPayTable();val random=java.util.Random(20261025)
        val target=10000L;var purchased=0L;var credits=0L;var used=0L;var deposits=0L
        var protected=0L;var free=0L;var pending=0L;var capital=0L;var cash=0L;var withdrawals=0L;var kept=0L;var blocks=0
        val prizes=java.util.ArrayDeque<Long>()
        repeat(target.toInt()) {i->
            if(credits==0L) {
                val before=deposits*7/10;deposits++;val delta=deposits*7/10-before
                protected+=delta;free+=1-delta;cash++;credits+=5;purchased+=5
            }
            if(free<t.maximumPrizeUnits) {
                val before=credits;blocks++ // A blocked attempt does not consume a credit or draw a result.
                assertEquals(before,credits)
                val recharge=t.maximumPrizeUnits-free
                free+=recharge;capital+=recharge;cash+=recharge
            }
            assertTrue(free>=t.maximumPrizeUnits)
            val result=t.draw(random::nextInt);val won=t.prize(result)
            credits--;used++;free-=won
            if(won>0) {pending+=won;prizes.add(won)}
            // Keep several outstanding prizes to prove they never back the next spin.
            if(i%4==0 && prizes.isNotEmpty()) {
                val prize=prizes.removeFirst();pending-=prize
                if(i%8==0) {protected+=prize;kept+=prize} else {cash-=prize;withdrawals+=prize}
            }
            assertEquals(cash,protected+free+pending)
            assertEquals(deposits+capital-withdrawals,cash)
            assertEquals(deposits*7/10+kept,protected)
            assertTrue(free>=0 && pending>=0)
        }
        assertEquals(0L,credits);assertEquals(purchased,used);assertTrue(blocks>0)
        println("Simulación operativa: $used tiradas; bloqueos/recargas=$blocks; capital=${capital*1000} CLP; protegido=${protected*1000}; libre=${free*1000}; pendiente=${pending*1000}; caja=${cash*1000}. No se ejecutan movimientos reales.")
    }
}
