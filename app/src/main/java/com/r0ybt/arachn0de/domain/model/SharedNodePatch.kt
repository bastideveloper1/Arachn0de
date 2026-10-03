package com.r0ybt.arachn0de.domain.model

/** Presence is distinct from a nullable replacement value. No title/date/structure fields. */
data class FieldChange<T>(val value: T)
data class SharedNodePatch(
    val description: String? = null,
    val amount: FieldChange<Long?>? = null,
    val currency: FieldChange<String?>? = null,
    val priority: Priority? = null,
    val tags: Set<String>? = null,
    val responsibleIds: Set<String>? = null,
) {
    val isEmpty get() = description == null && amount == null && currency == null && priority == null && tags == null && responsibleIds == null
}

/** Iterative forest traversal: selected descendants are covered once by their selected ancestor. */
object SelectionRoots {
    fun normalize(nodes: List<Node>, selected: Set<String>): List<String> {
        val available = nodes.mapTo(hashSetOf()) { it.id }
        require(selected.isNotEmpty() && selected.all { it in available }) { "La selección cambió." }
        val children=nodes.groupBy { it.parentId }
        val stack=ArrayDeque<Pair<Node,Boolean>>()
        children[null].orEmpty().asReversed().forEach { stack.addLast(it to false) }
        val roots=mutableListOf<String>()
        val visited = hashSetOf<String>()
        while(stack.isNotEmpty()) {
            val (node,covered)=stack.removeLast()
            require(visited.add(node.id)) { "Jerarquía inválida." }
            val chosen=node.id in selected
            if(chosen && !covered) roots.add(node.id)
            children[node.id].orEmpty().asReversed().forEach { stack.addLast(it to (covered || chosen)) }
        }
        require(visited.size == nodes.size) { "Jerarquía inválida." }
        return roots
    }
}
