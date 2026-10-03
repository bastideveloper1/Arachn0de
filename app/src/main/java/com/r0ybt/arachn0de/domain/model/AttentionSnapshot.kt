package com.r0ybt.arachn0de.domain.model

enum class AttentionLevel { NONE, UPCOMING, OVERDUE }

data class AttentionSummary(val upcoming: Int = 0, val overdue: Int = 0) {
    val total: Int get() = upcoming + overdue
    val level: AttentionLevel get() = when {
        overdue > 0 -> AttentionLevel.OVERDUE
        upcoming > 0 -> AttentionLevel.UPCOMING
        else -> AttentionLevel.NONE
    }
    operator fun plus(other: AttentionSummary) = AttentionSummary(upcoming + other.upcoming, overdue + other.overdue)
}

/** Reusable derived projection of a validated tree at one explicit instant; never persisted. */
class AttentionSnapshot(val tree: NodeTreeSnapshot, val now: Long) {
    val byNodeId: Map<String, AttentionSummary>
    val byProjectId: Map<String, AttentionSummary>
    val tasks: List<Node>

    init {
        val remaining = tree.nodes.associate { it.id to 0 }.toMutableMap()
        tree.nodes.forEach { node -> node.parentId?.let { remaining[it] = remaining.getValue(it) + 1 } }
        val queue = ArrayDeque<Node>()
        val counts = mutableMapOf<String, AttentionSummary>()
        val projects = mutableMapOf<String, AttentionSummary>()
        val sources = mutableListOf<Node>()
        tree.nodes.forEach { node ->
            if (remaining.getValue(node.id) == 0) {
                // Relationships, rather than a caller's cached type flag, define a leaf.
                val summary = when (TaskTemporal.state(node.copy(hasChildren = false), now)) {
                    TaskTemporalState.UPCOMING -> AttentionSummary(upcoming = 1)
                    TaskTemporalState.OVERDUE -> AttentionSummary(overdue = 1)
                    else -> AttentionSummary()
                }
                counts[node.id] = summary
                if (summary.total > 0) sources.add(node)
                queue.addLast(node)
            }
        }
        var processed = 0
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            processed++
            val summary = counts.getOrDefault(node.id, AttentionSummary())
            counts[node.id] = summary
            val parent = node.parentId
            if (parent == null) projects[node.projectId] = projects.getOrDefault(node.projectId, AttentionSummary()) + summary
            else {
                counts[parent] = counts.getOrDefault(parent, AttentionSummary()) + summary
                remaining[parent] = remaining.getValue(parent) - 1
                if (remaining[parent] == 0) queue.addLast(tree.nodesById.getValue(parent))
            }
        }
        check(processed == tree.nodes.size) { "Invalid attention hierarchy" }
        byNodeId = counts.toMap()
        byProjectId = projects.toMap()
        tasks = sources.sortedWith(
            compareBy<Node> { if (counts.getValue(it.id).level == AttentionLevel.OVERDUE) 0 else 1 }
                .thenBy { it.dueAt }.thenBy { it.createdAt }.thenBy { it.id },
        )
    }

    /** Resolve only displayed/requested paths, avoiding an eager path copy for every task. */
    fun pathTo(nodeId: String): List<Node> {
        val path = mutableListOf<Node>()
        var node = tree.nodesById[nodeId]
        while (node != null) {
            path.add(node)
            node = node.parentId?.let(tree.nodesById::getValue)
        }
        return path.asReversed()
    }
}
