package com.r0ybt.arachn0de.update

import java.math.BigInteger

/** SemVer precedence; build metadata has no effect on ordering. */
internal class SemanticVersion private constructor(
    val major: BigInteger, val minor: BigInteger, val patch: BigInteger,
    val pre: List<String>,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        for ((left, right) in listOf(major to other.major, minor to other.minor, patch to other.patch)) {
            val result = left.compareTo(right)
            if (result != 0) return result
        }
        if (pre.isEmpty() || other.pre.isEmpty()) return when {
            pre.isEmpty() && other.pre.isEmpty() -> 0
            pre.isEmpty() -> 1
            else -> -1
        }
        for (i in 0 until minOf(pre.size, other.pre.size)) {
            val a = pre[i]; val b = other.pre[i]
            val an = a.toBigIntegerOrNull(); val bn = b.toBigIntegerOrNull()
            val result = when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1
                bn != null -> 1
                else -> a.compareTo(b)
            }
            if (result != 0) return result
        }
        return pre.size.compareTo(other.pre.size)
    }

    companion object {
        private val pattern = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$")
        fun parse(value: String): SemanticVersion? {
            if (value.length > 200) return null
            val match = pattern.matchEntire(value.removePrefix("v")) ?: return null
            val pre = match.groupValues[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
            if (pre.any { it.all(Char::isDigit) && it.length > 1 && it.startsWith('0') }) return null
            return SemanticVersion(match.groupValues[1].toBigInteger(), match.groupValues[2].toBigInteger(), match.groupValues[3].toBigInteger(), pre)
        }
    }
}
