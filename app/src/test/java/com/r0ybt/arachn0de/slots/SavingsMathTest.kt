package com.r0ybt.arachn0de.slots

import org.junit.Test
import org.junit.Assert.*

class SavingsMathTest {
    @Test fun defaultTableHasExactThirtyPercentRtpAndDisclosedVariance() {
        val table=SavingsPayTable();table.validate()
        assertEquals(SavingsFraction(3,10),table.rtp)
        assertEquals(SavingsFraction(7,10),table.houseEdge)
        assertEquals(SavingsFraction(1447,50000),table.winProbability)
        assertEquals(SavingsFraction(118,625),table.varianceUnits)
        assertEquals(20L,table.maximumPrizeUnits)
        assertEquals(504,SavingsPayTable.losses.size)
        assertTrue(SavingsPayTable.losses.all {table.prize(it)==0L})
        var probability=table.probabilityPerLosingCombination*SavingsFraction(504)
        (0..7).forEach {i->probability+=table.probability(i);assertEquals(table.prizeUnits[i],table.prize(listOf(i,i,i)))}
        assertEquals(SavingsFraction(1),probability)
    }
    @Test fun approvedFiveForThousandTableContributesExactlyThirtyPercent() {
        val t=SavingsPayTable();assertEquals(200L,t.costPerSpinMinor)
        assertEquals(SavingsFraction(3,50),t.expectedPrizeUnits)
        val expected=listOf(SavingsFraction(73,10),SavingsFraction(6),SavingsFraction(6),SavingsFraction(24,5),SavingsFraction(3),SavingsFraction(21,10),SavingsFraction(3,5),SavingsFraction(1,5))
        val contributions=t.prizeUnits.indices.map {t.probability(it)*SavingsFraction(t.prizeUnits[it])/t.costPerSpin*SavingsFraction(100)}
        assertEquals(expected,contributions);assertEquals(SavingsFraction(30),contributions.fold(SavingsFraction(0)) {a,b->a+b})
    }
    @Test fun millionDrawsMatchExpectedReturnAndAllVisibleCombinations() {
        val table=SavingsPayTable();val random=java.util.Random(20261024)
        val n=1_000_000;val counts=IntArray(8);val triples=IntArray(512);var total=0L;var squared=0L
        repeat(n) {val result=table.draw(random::nextInt);val prize=table.prize(result);total+=prize;squared+=prize*prize;triples[result[0]*64+result[1]*8+result[2]]++;if(prize>0) {assertEquals(1,result.distinct().size);counts[result[0]]++}}
        val observed=total.toDouble()/n
        val sigma=kotlin.math.sqrt(table.varianceUnits.approximate()/n)
        assertTrue(kotlin.math.abs(observed-table.expectedPrizeUnits.approximate())<6*sigma)
        counts.forEachIndexed {i,count->val p=table.probability(i).approximate();assertTrue(kotlin.math.abs(count-n*p)<6*kotlin.math.sqrt(n*p*(1-p)))}
        val losingP=table.probabilityPerLosingCombination.approximate()
        SavingsPayTable.losses.forEach {r->val count=triples[r[0]*64+r[1]*8+r[2]];assertTrue(kotlin.math.abs(count-n*losingP)<6*kotlin.math.sqrt(n*losingP*(1-losingP)))}
        val variance=squared.toDouble()/n-observed*observed
        val mean=table.expectedPrizeUnits.approximate()
        val fourth=(0..7).sumOf {i->table.probability(i).approximate()*Math.pow(table.prizeUnits[i]-mean,4.0)}+(1-table.winProbability.approximate())*Math.pow(mean,4.0)
        val varianceSigma=kotlin.math.sqrt((fourth-Math.pow(table.varianceUnits.approximate(),2.0))/n)
        assertTrue(kotlin.math.abs(variance-table.varianceUnits.approximate())<6*varianceSigma)
        println("Simulación matemática: $n tiradas; RTP observado=${observed/table.costPerSpin.approximate()*100}%; premios=${counts.sum()}; varianza=$variance; categorías=${counts.toList()}")
    }
}
