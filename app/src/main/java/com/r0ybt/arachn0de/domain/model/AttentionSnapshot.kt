package com.r0ybt.arachn0de.domain.model

enum class AttentionLevel { NONE, PRIORITY, UPCOMING, OVERDUE }
enum class AttentionReason { OVERDUE, DUE_TODAY, UPCOMING, HIGH_PRIORITY, MEDIUM_PRIORITY }

object AttentionPolicy {
    fun reasons(node: Node, now: Long, zone: java.util.TimeZone): List<AttentionReason> {
        if (!node.isCompletable || node.isCompleted) return emptyList()
        val temporal = TaskTemporal.state(node, now)
        val reason = when {
            temporal == TaskTemporalState.OVERDUE -> AttentionReason.OVERDUE
            temporal != TaskTemporalState.SCHEDULED && node.dueAt != null &&
                CalendarDates.localDay(node.dueAt, zone) == CalendarDates.localDay(now, zone) -> AttentionReason.DUE_TODAY
            temporal == TaskTemporalState.UPCOMING -> AttentionReason.UPCOMING
            else -> null
        }
        return buildList {
            reason?.let { add(it) }
            if (node.effectivePriority == Priority.HIGH) add(AttentionReason.HIGH_PRIORITY)
            else if (node.effectivePriority == Priority.MEDIUM && reason != null) add(AttentionReason.MEDIUM_PRIORITY)
        }
    }
}


data class AttentionSummary(val upcoming: Int = 0, val overdue: Int = 0, val priorityOnly: Int = 0) {
    val total: Int get() = upcoming + overdue + priorityOnly
    val level: AttentionLevel get() = when {
        overdue > 0 -> AttentionLevel.OVERDUE
        upcoming > 0 -> AttentionLevel.UPCOMING
        priorityOnly > 0 -> AttentionLevel.PRIORITY
        else -> AttentionLevel.NONE
    }
    operator fun plus(other: AttentionSummary) = AttentionSummary(upcoming + other.upcoming, overdue + other.overdue, priorityOnly + other.priorityOnly)
}

/** Reusable derived projection of a validated tree at one explicit instant; never persisted. */
class AttentionSnapshot(val tree: NodeTreeSnapshot, val now: Long, val zone: java.util.TimeZone = java.util.TimeZone.getDefault()) {
    val cardAlertsByNodeId: Map<String, ProjectCardAlerts>
    val byNodeId: Map<String, AttentionSummary>
    val byProjectId: Map<String, AttentionSummary>
    val reasonsByNodeId: Map<String, List<AttentionReason>>
    val tasks: List<Node>

    init {
        val todayRange=TemporalRanges.day(CalendarDates.localDay(now,zone),zone)
        val remaining = tree.nodes.associate { it.id to 0 }.toMutableMap()
        tree.nodes.forEach { node -> node.parentId?.let { remaining[it] = remaining.getValue(it) + 1 } }
        val queue = ArrayDeque<Node>()
        val cardCounts=mutableMapOf<String,ProjectCardAlerts>()
        val counts = mutableMapOf<String, AttentionSummary>()
        val projects = mutableMapOf<String, AttentionSummary>()
        val reasons = mutableMapOf<String, List<AttentionReason>>()
        val sources = mutableListOf<Node>()
        tree.nodes.forEach { node ->
            if (remaining.getValue(node.id) == 0) {
                // Relationships, rather than a caller's cached type flag, define a leaf.
                val signals = AttentionPolicy.reasons(node.copy(hasChildren = false), now, zone)
                reasons[node.id] = signals
                val summary = when (signals.firstOrNull()) {
                    AttentionReason.OVERDUE -> AttentionSummary(overdue = 1)
                    AttentionReason.DUE_TODAY, AttentionReason.UPCOMING -> AttentionSummary(upcoming = 1)
                    AttentionReason.HIGH_PRIORITY -> AttentionSummary(priorityOnly = 1)
                    else -> AttentionSummary()
                }
                val active=node.isCompletable && !node.isCompleted
                cardCounts[node.id]=ProjectCardAlerts(highPriority=if(active && node.effectivePriority==Priority.HIGH) 1 else 0,
                    dueToday=if(active && node.dueAt?.let {it in todayRange}==true) 1 else 0,
                    futureDue=if(active && node.dueAt!=null && node.dueAt>=todayRange.endExclusive) 1 else 0)
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
                val inherited=cardCounts[node.id] ?: ProjectCardAlerts()
                val previous=cardCounts[parent] ?: ProjectCardAlerts()
                cardCounts[parent]=ProjectCardAlerts(previous.highPriority+inherited.highPriority,previous.dueToday+inherited.dueToday,previous.futureDue+inherited.futureDue)
                counts[parent] = counts.getOrDefault(parent, AttentionSummary()) + summary
                remaining[parent] = remaining.getValue(parent) - 1
                if (remaining[parent] == 0) queue.addLast(tree.nodesById.getValue(parent))
            }
        }
        check(processed == tree.nodes.size) { "Invalid attention hierarchy" }
        cardAlertsByNodeId=cardCounts.toMap()
        byNodeId = counts.toMap()
        byProjectId = projects.toMap()
        reasonsByNodeId = reasons.toMap()
        tasks = sources.sortedWith(
            compareBy<Node> { reasons.getValue(it.id).first().ordinal }
                .thenBy { it.dueAt == null }.thenBy { it.dueAt }.thenByDescending { it.effectivePriority.ordinal }.thenBy { it.createdAt }.thenBy { it.id },
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
