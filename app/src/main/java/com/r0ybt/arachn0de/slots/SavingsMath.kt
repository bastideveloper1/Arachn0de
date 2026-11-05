package com.r0ybt.arachn0de.slots

import java.math.BigInteger
import java.security.SecureRandom

/** Exact rational arithmetic for the disclosed pay table; never used as a bank balance. */
internal class SavingsFraction(n:BigInteger,d:BigInteger) {
    val numerator:BigInteger
    val denominator:BigInteger
    init {require(d.signum()>0);val gcd=n.gcd(d);numerator=n/gcd;denominator=d/gcd}
    constructor(n:Long,d:Long=1):this(BigInteger.valueOf(n),BigInteger.valueOf(d))
    operator fun plus(other:SavingsFraction)=SavingsFraction(numerator*other.denominator+other.numerator*denominator,denominator*other.denominator)
    operator fun minus(other:SavingsFraction)=SavingsFraction(numerator*other.denominator-other.numerator*denominator,denominator*other.denominator)
    operator fun times(other:SavingsFraction)=SavingsFraction(numerator*other.numerator,denominator*other.denominator)
    operator fun div(other:SavingsFraction):SavingsFraction {require(other.numerator.signum()>0);return SavingsFraction(numerator*other.denominator,denominator*other.numerator)}
    fun approximate()=numerator.toDouble()/denominator.toDouble()
    override fun toString()=if(denominator==BigInteger.ONE)numerator.toString() else "$numerator/$denominator"
    override fun equals(other:Any?)=other is SavingsFraction && numerator==other.numerator && denominator==other.denominator
    override fun hashCode()=31*numerator.hashCode()+denominator.hashCode()
}

/** Probabilities belong to combinations, not to independent identically weighted reels.
 * Losing triples are equiprobable conditional on losing. Never changes odds based on funds/history.
 * All amounts here are integer units of CLP 1,000, not fractional physical money.
 */
internal data class SavingsPayTable(val priceUnits:Long=1,
    val prizeUnits:List<Long> = listOf(1,2,3,4,5,7,10,20),
    val winningTickets:List<Int> = listOf(730,300,200,120,60,30,6,1),val creditsPerPack:Int=5,val denominator:Int=50000) {
    fun validate() {
        require(priceUnits in 1..Long.MAX_VALUE/1000)
        require((creditsPerPack==1 && denominator==10000) || (creditsPerPack==5 && denominator==50000))
        require(prizeUnits.size in 7..8 && winningTickets.size==prizeUnits.size)
        require(prizeUnits.all {it in 1..Long.MAX_VALUE/1000})
        require(winningTickets.all {it in 1..denominator} && winningTickets.sum()<denominator)
        val ascending=order.map {prizeUnits[it]}
        require(ascending.zipWithNext().all {(a,b)->a<b}) {"Los premios deben crecer en el orden de los símbolos de la tabla."}
        // Activation limits belong to the savings configuration policy, not to arithmetic.
        require(rtp.numerator.signum()>0)
    }
    val symbolCount:Int get()=prizeUnits.size
    val order:List<Int> get()=if(symbolCount==7)listOf(5,4,3,2,1,0,6) else (0..7).toList()
    val losingResults:List<List<Int>> get()=if(symbolCount==7)legacyLosses else losses
    val expectedPrizeUnits:SavingsFraction get()=SavingsFraction(prizeUnits.indices.fold(BigInteger.ZERO) {sum,i->sum+BigInteger.valueOf(prizeUnits[i])*BigInteger.valueOf(winningTickets[i].toLong())},BigInteger.valueOf(denominator.toLong()))
    val costPerSpin:SavingsFraction get()=SavingsFraction(priceUnits,creditsPerPack.toLong())
    val costPerSpinMinor:Long get()=savingsMoney(priceUnits)/creditsPerPack
    fun purchaseUnits(credits:Long):Long {require(credits>0 && credits%creditsPerPack==0L) {"Compra paquetes completos de $creditsPerPack tiradas."};return Math.multiplyExact(priceUnits,credits/creditsPerPack)}
    val rtp:SavingsFraction get()=expectedPrizeUnits/costPerSpin
    val houseEdge:SavingsFraction get()=SavingsFraction(1)-rtp
    val winProbability:SavingsFraction get()=SavingsFraction(winningTickets.sum().toLong(),denominator.toLong())
    val expectedSpinsPerPrize:SavingsFraction get()=SavingsFraction(1)/winProbability
    val varianceUnits:SavingsFraction get() {
        val second=SavingsFraction(prizeUnits.indices.fold(BigInteger.ZERO) {sum,i->val p=BigInteger.valueOf(prizeUnits[i]);sum+p*p*BigInteger.valueOf(winningTickets[i].toLong())},BigInteger.valueOf(denominator.toLong()))
        return second-expectedPrizeUnits*expectedPrizeUnits
    }
    val maximumPrizeUnits:Long get()=prizeUnits.max()
    fun probability(symbol:Int)=SavingsFraction(winningTickets[symbol].toLong(),denominator.toLong())
    val probabilityPerLosingCombination:SavingsFraction get()=SavingsFraction((denominator-winningTickets.sum()).toLong(),denominator.toLong()*losingResults.size)
    fun prize(result:List<Int>):Long=if(result.size==3 && result.distinct().size==1)prizeUnits.getOrElse(result[0]) {0} else 0
    fun draw(nextInt:(Int)->Int = secure::nextInt):List<Int> {
        var ticket=nextInt(denominator).also {require(it in 0 until denominator)}
        for(i in winningTickets.indices) {if(ticket<winningTickets[i])return listOf(i,i,i);ticket-=winningTickets[i]}
        return losingResults[nextInt(losingResults.size).also {require(it in losingResults.indices)}]
    }
    companion object {
        private val secure=SecureRandom()
        fun legacy()=SavingsPayTable(1,listOf(7,5,4,3,2,1,10),listOf(30,60,120,200,300,750,6),1,10000)
        private fun combinations(count:Int)=(0 until count).flatMap {a->(0 until count).flatMap {b->(0 until count).map {c->listOf(a,b,c)}}}.filter {it.distinct().size>1}
        val losses:List<List<Int>> = combinations(8)
        val legacyLosses:List<List<Int>> = combinations(7)
    }
}
