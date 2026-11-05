package com.r0ybt.arachn0de.slots

import kotlin.random.Random

/** Ephemeral play only; optional fictional credits never represent money or persistent data. */
internal class SlotMachine(private val random:Random=Random.Default, val creditsEnabled:Boolean=false, symbolCount:Int=8) {
    var symbolCount=symbolCount;private set
    enum class Phase { EMPTY, INSERTING, READY, SPINNING, FINISHED }
    var phase=Phase.EMPTY;private set
    var results:List<Int> = (0 until symbolCount).shuffled(random).take(3);private set
    var credits=0L;private set
    var lastPrize=0;private set
    fun canSpin()=if(creditsEnabled) credits>0 && (phase==Phase.READY || phase==Phase.FINISHED) else phase==Phase.READY
    fun canInsert()=phase==Phase.EMPTY || phase==Phase.FINISHED || (creditsEnabled && phase==Phase.READY)
    private var starts=results
    private var distances=listOf(0,0,0)
    var elapsed=0L;private set
    fun insert():Boolean {if(!canInsert())return false;phase=Phase.INSERTING;return true}
    fun inserted() {if(phase==Phase.INSERTING) {if(creditsEnabled)credits+=5;phase=Phase.READY}}
    fun spin():Boolean {
        if(!canSpin())return false
        if(creditsEnabled)credits--
        lastPrize=0
        starts=results;results=List(3) {random.nextInt(symbolCount)}
        distances=List(3) {i->(6+i)*symbolCount+Math.floorMod(results[i]-starts[i],symbolCount)}
        elapsed=0;phase=Phase.SPINNING;return true
    }
    fun showCommitted(result:List<Int>,count:Int=symbolCount) {
        require(count in 7..8);symbolCount=count
        require(!creditsEnabled && result.size==3 && result.all {it in 0 until symbolCount})
        if(phase!=Phase.SPINNING) {results=result.toList();phase=Phase.FINISHED;finishedBefore=true}
    }
    /** Rendering-only entry point for a result already committed by Ahorro. */
    fun animateCommitted(result:List<Int>,count:Int=symbolCount):Boolean {
        require(count in 7..8)
        if(phase==Phase.SPINNING)return false
        symbolCount=count
        require(!creditsEnabled && result.size==3 && result.all {it in 0 until symbolCount})
        if(phase==Phase.SPINNING)return false
        starts=results;results=result.toList()
        distances=List(3) {i->(6+i)*symbolCount+Math.floorMod(results[i]-starts[i],symbolCount)}
        elapsed=0;lastPrize=0;phase=Phase.SPINNING;return true
    }
    fun advance(ms:Long) {if(phase==Phase.SPINNING) {elapsed=(elapsed+ms.coerceAtLeast(0)).coerceAtMost(4200);if(elapsed==4200L) {phase=Phase.FINISHED;finishedBefore=true;if(creditsEnabled) {lastPrize=prize(results);credits+=lastPrize}}}}
    fun position(reel:Int):Float {
        if(phase!=Phase.SPINNING)return results[reel].toFloat()
        val t=(elapsed.toFloat()/duration(reel)).coerceIn(0f,1f)
        // Monotonic cubic ease-out: continuous forward travel, zero speed at the exact endpoint.
        val progress=1f-(1f-t)*(1f-t)*(1f-t)
        return starts[reel]+distances[reel]*progress
    }
    /** Presentation only: independent cyclic strips; never consumes random values or selects prizes. */
    fun visualSymbol(reel:Int,index:Int):Int {
        require(reel in 0..2)
        val stride=if(symbolCount==8) listOf(1,3,5)[reel] else listOf(1,2,3)[reel]
        return Math.floorMod(Math.floorMod(index,symbolCount)*stride,symbolCount)
    }
    private fun visualIndex(reel:Int,symbol:Int)= (0 until symbolCount).first {visualSymbol(reel,it)==Math.floorMod(symbol,symbolCount)}
    fun visualPosition(reel:Int):Float {
        val end=visualIndex(reel,results[reel])
        if(phase!=Phase.SPINNING)return end.toFloat()
        val start=visualIndex(reel,starts[reel])
        val distance=(6+reel)*symbolCount+Math.floorMod(end-start,symbolCount)
        val t=(elapsed.toFloat()/duration(reel)).coerceIn(0f,1f)
        return start+distance*(1f-(1f-t)*(1f-t)*(1f-t))
    }
    fun lit():Boolean=when(phase) {Phase.SPINNING->elapsed/450%2==0L;Phase.FINISHED->true;else->finishedBefore}
    private var finishedBefore=false
    companion object {
        val symbols=listOf("1cereza.png","2platano.png","3guacamayo.png","4Lingotes.png","5lingotes.png","6rubi.png","7esmeralda.png","8mascara.png").map {"jungle/iconos/$it"}
        // Normal mode keeps fictional credits: one credit is the nominal CLP 1,000 unit.
        val payouts=listOf(1,2,3,4,5,7,10,20)
        fun prize(results:List<Int>):Int=if(results.size==3 && results.distinct().size==1) payouts.getOrElse(results.first()) {0} else 0
        fun symbol(index:Int)=Math.floorMod(index,8)
        fun duration(reel:Int)=3000L+600L*reel
    }
}
