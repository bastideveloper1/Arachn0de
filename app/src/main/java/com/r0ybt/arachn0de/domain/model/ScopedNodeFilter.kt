package com.r0ybt.arachn0de.domain.model

/** A presentation projection; original nodes, ordering and progress remain untouched. */
data class FilteredNodeRow(val node: Node, val depth: Int, val isMatch: Boolean)
object ScopedNodeFilter {
    fun apply(snapshot: NodeTreeSnapshot, projectId: String, parentId: String?, filter: NodeFilter,
        people: Map<String, Set<String>>, tags: Map<String, Set<String>>): List<FilteredNodeRow> {
        val ordered = mutableListOf<Pair<Node, Int>>()
        val pending = java.util.ArrayDeque<Pair<Node, Int>>()
        snapshot.childrenOf(parentId).filter { it.projectId == projectId }.asReversed().forEach { pending.addLast(it to 0) }
        while (pending.isNotEmpty()) {
            val (node, depth) = pending.removeLast()
            ordered.add(node to depth)
            snapshot.childrenOf(node.id).asReversed().forEach { pending.addLast(it to depth + 1) }
        }
        val matches = ordered.filter { filter.matches(it.first, people[it.first.id].orEmpty(), tagIds = tags[it.first.id].orEmpty()) }
            .mapTo(hashSetOf()) { it.first.id }
        val retained = matches.toMutableSet()
        // Children precede ancestors in reverse preorder: a single pass retains minimal context.
        ordered.asReversed().forEach { (node, _) -> if (node.id in retained) node.parentId?.let { retained.add(it) } }
        return ordered.filter { it.first.id in retained }.map { (node, depth) -> FilteredNodeRow(node, depth, node.id in matches) }
    }
}
