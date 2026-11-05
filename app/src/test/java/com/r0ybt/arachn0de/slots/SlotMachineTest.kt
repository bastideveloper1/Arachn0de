package com.r0ybt.arachn0de.slots

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class SlotMachineTest {
    @Test fun initialPresentationAndSingleCoinConsumption() {
        val machine=SlotMachine(Random(24))
        assertEquals(3,machine.results.distinct().size)
        assertFalse(machine.lit())
        assertFalse(machine.spin())
        assertTrue(machine.insert())
        assertFalse(machine.insert())
        assertFalse(machine.spin())
        machine.inserted()
        assertEquals(SlotMachine.Phase.READY,machine.phase)
        assertTrue(machine.spin())
        assertFalse(machine.spin())
        assertFalse(machine.insert())
        machine.advance(4200)
        assertEquals(SlotMachine.Phase.FINISHED,machine.phase)
        assertTrue(machine.lit())
        assertFalse(machine.spin())
        val last=machine.results
        assertTrue(machine.insert())
        machine.inserted()
        assertTrue(machine.lit())
        assertTrue(machine.spin())
        assertEquals(last.map {it.toFloat()},List(3) {machine.position(it)})
    }

    @Test fun exactCycleContinuousForwardTravelAndStaggeredStops() {
        assertEquals(listOf("1cereza.png","2platano.png","3guacamayo.png","4Lingotes.png","5lingotes.png","6rubi.png","7esmeralda.png","8mascara.png").map {"jungle/iconos/$it"},SlotMachine.symbols)
        assertEquals(0,SlotMachine.symbol(8))
        assertEquals(7,SlotMachine.symbol(-1))
        val machine=SlotMachine(Random(27))
        val start=machine.results
        machine.insert();machine.inserted();machine.spin()
        var previous=List(3) {machine.position(it)}
        repeat(4199) {
            machine.advance(1)
            val current=List(3) {machine.position(it)}
            current.forEachIndexed {index,value->
                assertTrue("Rodillo $index retrocedió",value>=previous[index])
                assertTrue("Salto fuera de secuencia",value-previous[index]<1f)
            }
            previous=current
        }
        previous.forEachIndexed {i,value->
            assertTrue(value-start[i]>=42f)
            assertEquals(machine.results[i].toFloat(),((value%8)+8)%8,.001f)
        }
        machine.advance(1)
        machine.results.forEachIndexed {i,result->assertEquals(result.toFloat(),machine.position(i),0f)}
        assertTrue(SlotMachine.duration(0)<SlotMachine.duration(1))
        assertTrue(SlotMachine.duration(1)<SlotMachine.duration(2))
    }

    @Test fun independentResultsAllowMatchesAndEverySymbol() {
        val machine=SlotMachine(Random(42))
        val seen=List(3) {mutableSetOf<Int>()}
        var matched=false
        repeat(500) {
            assertTrue(machine.insert());machine.inserted();assertTrue(machine.spin())
            machine.results.forEachIndexed {i,result->assertTrue(result in 0..7);seen[i].add(result)}
            if(machine.results.distinct().size<3)matched=true
            machine.advance(4200)
        }
        assertTrue(matched)
        seen.forEach {assertEquals((0..7).toSet(),it)}
    }

    @Test fun independentVisualStripsPreserveCentralResultsAndContinuousStops() {
        for(count in 7..8) {
            val machine=SlotMachine(Random(73),symbolCount=count)
            for(symbol in 0 until count) {
                machine.showCommitted(List(3) {symbol},count)
                val centers=List(3) {machine.visualPosition(it).toInt()}
                assertEquals(List(3) {symbol},List(3) {machine.visualSymbol(it,centers[it])})
                for(offset in listOf(-1,1))assertEquals(3,List(3) {machine.visualSymbol(it,centers[it]+offset)}.distinct().size)
                for(reel in 0..2)assertEquals((0 until count).toSet(),(0 until count).map {machine.visualSymbol(reel,it)}.toSet())
                val before=List(3) {machine.visualPosition(it)}
                val target=List(3) {(symbol+2)%count}
                assertTrue(machine.animateCommitted(target,count))
                assertEquals(before,List(3) {machine.visualPosition(it)})
                var previous=before
                repeat(4199) {
                    machine.advance(1)
                    val current=List(3) {machine.visualPosition(it)}
                    current.forEachIndexed {reel,value->assertTrue(value>=previous[reel]);assertTrue(value-previous[reel]<1f)}
                    previous=current
                }
                previous.forEachIndexed {reel,value->assertEquals(target[reel],machine.visualSymbol(reel,kotlin.math.round(value).toInt()))}
                machine.advance(1)
                assertEquals(target,List(3) {machine.visualSymbol(it,machine.visualPosition(it).toInt())})
            }
        }
        val reference=SlotMachine(Random(88));val rendered=SlotMachine(Random(88))
        repeat(20) {
            reference.insert();rendered.insert();reference.inserted();rendered.inserted();reference.spin();rendered.spin()
            repeat(10) {rendered.visualPosition(it%3);rendered.visualSymbol(it%3,it)}
            assertEquals(reference.results,rendered.results)
            reference.advance(4200);rendered.advance(4200)
        }
    }

}
