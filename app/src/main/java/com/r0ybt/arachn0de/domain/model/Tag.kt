package com.r0ybt.arachn0de.domain.model

import java.util.Locale

data class Tag(val id: String, val name: String, val normalizedName: String)
object TagNames {
    fun display(value: String): String = value.trim().replace(Regex("\\s+"), " ").also {
        require(it.isNotEmpty()) { "La etiqueta no puede estar vacía." }
    }
    fun normalize(value: String): String = display(value).lowercase(Locale.ROOT)
}
data class TagState(val tags: List<Tag> = emptyList(), val nodeIds: Map<String, Set<String>> = emptyMap(),
    val ruleIds: Map<String, Set<String>> = emptyMap()) {
    private val byId = tags.associateBy { it.id }
    fun forNode(id: String): List<Tag> = nodeIds[id].orEmpty().mapNotNull(byId::get).sortedBy { it.normalizedName }
    fun usage(id: String): Int = nodeIds.values.count { id in it } + ruleIds.values.count { id in it }
}
