package com.r0ybt.arachn0de.ui.state

import com.r0ybt.arachn0de.domain.model.Node

internal data class LayerMapRow(val node: Node, val depth: Int, val hasChildren: Boolean)

/** Indexed once per snapshot; expansion visits only visible nodes, without recursive calls. */
internal class LayerMapIndex(nodes: List<Node>) {
    private val byId = nodes.associateBy { it.id }
    private val children = nodes.groupBy { it.parentId }.mapValues { (_, siblings) ->
        siblings.sortedWith(compareBy<Node> { it.position }.thenBy { it.createdAt }.thenBy { it.id })
    }
    init {
        require(byId.size == nodes.size) { "Duplicate node ID" }
        require(nodes.all { it.parentId == null || byId[it.parentId]?.projectId == it.projectId }) {
            "Invalid parent"
        }
        val pending = ArrayDeque<Node>()
        pending.addAll(children[null].orEmpty())
        var count = 0
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            count++
            pending.addAll(children[node.id].orEmpty())
        }
        require(count == nodes.size) { "Cyclic tree" }
    }

    fun visibleRows(expanded: Set<String>): List<LayerMapRow> = buildList {
        val pending = ArrayDeque<Pair<Node, Int>>()
        children[null].orEmpty().asReversed().forEach { pending.addLast(it to 0) }
        while (pending.isNotEmpty()) {
            val (node, depth) = pending.removeLast()
            val descendants = children[node.id].orEmpty()
            add(LayerMapRow(node, depth, descendants.isNotEmpty()))
            if (node.id in expanded) {
                descendants.asReversed().forEach { pending.addLast(it to depth + 1) }
            }
        }
    }
}
