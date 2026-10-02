package com.r0ybt.arachn0de.domain.model

/** Limits apply to trimmed Unicode code points, not UTF-16 units (emoji count once). */
object TitleLimits {
    const val PROJECT = 60
    const val NODE = 100
    fun count(value: String): Int = value.trim().let { Character.codePointCount(it, 0, it.length) }
}
