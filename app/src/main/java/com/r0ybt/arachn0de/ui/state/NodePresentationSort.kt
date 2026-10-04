package com.r0ybt.arachn0de.ui.state

import com.r0ybt.arachn0de.domain.model.FilteredNodeRow
import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.domain.model.Priority

/** Presentation only: never writes positions or changes the source tree used by export. */
internal enum class NodeSortMode(val label: String) {
    MANUAL("Manual"), DUE_ASC("Vencimiento próximo"), DUE_DESC("Vencimiento lejano"),
    DUE_PRIORITY("Vencimiento y prioridad"), PRIORITY("Prioridad"), CREATED_NEWEST("Más recientes"), CREATED_OLDEST("Más antiguos")
}

internal object NodePresentationSort {
    private val manual = compareBy<Node> { it.position }.thenBy { it.createdAt }.thenBy { it.id }
    private val dueAscending = compareBy<Node> { it.dueAt == null }.thenBy { it.dueAt }
    private fun priority(node: Node) = when (node.effectivePriority) {
        Priority.HIGH -> 3; Priority.MEDIUM -> 2; Priority.LOW -> 1; Priority.NONE -> 0
    }
    fun comparator(mode: NodeSortMode, completedAt: Map<String, Long> = emptyMap()): Comparator<Node> {
        val criterion = when (mode) {
            NodeSortMode.MANUAL -> manual
            NodeSortMode.DUE_PRIORITY -> dueAscending.thenByDescending { priority(it) }.then(manual)
            NodeSortMode.DUE_ASC -> dueAscending.then(manual)
            NodeSortMode.DUE_DESC -> compareBy<Node> { it.dueAt == null }.thenByDescending { it.dueAt }.then(manual)
            NodeSortMode.PRIORITY -> compareByDescending<Node> { priority(it) }.then(dueAscending).then(manual)
            NodeSortMode.CREATED_NEWEST -> compareByDescending<Node> { it.createdAt }.thenBy { it.position }.thenBy { it.id }
            NodeSortMode.CREATED_OLDEST -> compareBy<Node> { it.createdAt }.thenBy { it.position }.thenBy { it.id }
        }
        // Existing Available/Completed sections are preserved, including in filtered trees.
        val completion = Comparator<Node> { a, b ->
            if (mode == NodeSortMode.MANUAL && a.isCompleted && b.isCompleted)
                compareValues(completedAt[b.id], completedAt[a.id]) else 0
        }
        return compareBy<Node> { it.isCompleted }.then(completion).then(criterion)
    }
    fun children(nodes: List<Node>, mode: NodeSortMode, completedAt: Map<String, Long> = emptyMap()): List<Node> = nodes.sortedWith(comparator(mode, completedAt))

    /** Retain minimal context, depth and matching flags; only siblings change order. */
    fun filtered(rows: List<FilteredNodeRow>, mode: NodeSortMode, completedAt: Map<String, Long> = emptyMap()): List<FilteredNodeRow> {
        if (mode == NodeSortMode.MANUAL && completedAt.isEmpty()) return rows
        val ids = rows.mapTo(hashSetOf()) { it.node.id }
        val nodeComparator = comparator(mode, completedAt)
        val comparator = Comparator<FilteredNodeRow> { a, b -> nodeComparator.compare(a.node, b.node) }
        val children = rows.groupBy { it.node.parentId }.mapValues { (_, siblings) -> siblings.sortedWith(comparator) }
        val roots = rows.filter { it.node.parentId !in ids }.sortedWith(comparator)
        val pending = java.util.ArrayDeque<FilteredNodeRow>()
        roots.asReversed().forEach(pending::addLast)
        val result = ArrayList<FilteredNodeRow>(rows.size)
        while (pending.isNotEmpty()) {
            val row = pending.removeLast()
            result.add(row)
            children[row.node.id].orEmpty().asReversed().forEach(pending::addLast)
        }
        return result
    }
}
