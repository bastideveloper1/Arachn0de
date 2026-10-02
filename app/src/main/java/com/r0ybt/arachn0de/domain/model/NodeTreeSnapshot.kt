package com.r0ybt.arachn0de.domain.model

/** One database emission provides the nodes, their structure and all derived progress. */
class NodeTreeSnapshot(val nodes: List<Node>) {
    val nodesById = nodes.associateBy { it.id }
    private val childrenByParent = nodes.groupBy { it.parentId }
    val progressById: Map<String, NodeProgress> = calculateProgress()

    fun childrenOf(parentId: String?): List<Node> =
        childrenByParent[parentId].orEmpty()
            .sortedWith(
                compareBy<Node> { if (it.isCompleted) 1 else 0 }
                    .thenBy { it.position }
                    .thenBy { it.createdAt }
                    .thenBy { it.id },
            )

    private fun calculateProgress(): Map<String, NodeProgress> {
        check(nodesById.size == nodes.size) { "Duplicate node identity" }
        nodes.forEach { node ->
            if (node.parentId != null) {
                check(nodesById[node.parentId]?.projectId == node.projectId) { "Invalid parent relation" }
            }
        }
        val remaining = nodes.associate { it.id to childrenOf(it.id).size }.toMutableMap()
        val completed = mutableMapOf<String, Int>()
        val total = mutableMapOf<String, Int>()
        val queue = java.util.ArrayDeque<Node>()
        nodes.filter { remaining[it.id] == 0 }.forEach(queue::addLast)
        val result = mutableMapOf<String, NodeProgress>()
        // Bottom-up traversal: each node is processed once, without recursion or depth assumptions.
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            check(node.id !in result) { "Repeated node during traversal" }
            val leaf = childrenOf(node.id).isEmpty()
            val count = if (leaf) 1 else total.getValue(node.id)
            val done = if (leaf) { if (node.isCompleted) 1 else 0 } else completed.getValue(node.id)
            result[node.id] = NodeProgress(
                nodeId = node.id, completed = done, total = count,
                percentage = ((done.toLong() * 100) / count).toInt(),
                state = when (done) {
                    0 -> NodeProgressState.NOT_STARTED
                    count -> NodeProgressState.COMPLETE
                    else -> NodeProgressState.PARTIAL
                },
            )
            node.parentId?.let { parentId ->
                total[parentId] = total.getOrDefault(parentId, 0) + count
                completed[parentId] = completed.getOrDefault(parentId, 0) + done
                remaining[parentId] = remaining.getValue(parentId) - 1
                if (remaining[parentId] == 0) queue.addLast(nodesById.getValue(parentId))
            }
        }
        check(result.size == nodes.size) { "Cycle in node hierarchy" }
        return result
    }
}
