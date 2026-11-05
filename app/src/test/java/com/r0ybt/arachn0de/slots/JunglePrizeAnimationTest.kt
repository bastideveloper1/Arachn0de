package com.r0ybt.arachn0de.slots

import org.junit.Assert.*
import org.junit.Test

class JunglePrizeAnimationTest {
    @Test fun eachWinningSymbolSelectsItsOwnAnimation() {
        val names=listOf("cereza","banana","guacamayo","plata","oro","rubi","esmeralda","premio_mayor")
        val table=SavingsPayTable()
        names.forEachIndexed {symbol,name->
            assertEquals("premios/premios_$name.gif",junglePrizeGif(List(3) {symbol},table,table.prizeUnits[symbol]))
        }
    }
    @Test fun mismatchedAmountsLegacyTablesAndLosingResultsNeverAdvertiseWrongPrize() {
        val table=SavingsPayTable()
        assertNull(junglePrizeGif(listOf(0,1,0),table,1))
        assertNull(junglePrizeGif(listOf(7,7,7),table,10))
        assertNull(junglePrizeGif(listOf(0,0,0),table.copy(prizeUnits=table.prizeUnits.map {it*2}),2))
        assertNull(junglePrizeGif(listOf(0,0,0),table.copy(prizeUnits=table.prizeUnits.take(7)),1))
        assertNull(junglePrizeGif(listOf(0,0,0),table.copy(prizeUnits=table.prizeUnits.dropLast(1)+30L),1))
    }
}
