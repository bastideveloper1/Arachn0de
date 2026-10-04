package com.r0ybt.arachn0de.domain.model

import java.math.BigInteger
import java.util.TimeZone

/** Financial state is derived from the existing completion flag, never persisted. */
enum class FinancialState { NO_OBLIGATIONS, PENDING, PARTIAL, COMPLETED }
enum class FinancialPeriod {
    ALL, THIS_MONTH, PREVIOUS_MONTH, NEXT_MONTH;
    fun month(current: CalendarMonth): CalendarMonth? = when (this) {
        ALL -> null
        THIS_MONTH -> current
        PREVIOUS_MONTH -> current.shifted(-1)
        NEXT_MONTH -> current.shifted(1)
    }
}
data class FinancialSelection(val period: FinancialPeriod = FinancialPeriod.THIS_MONTH, val personId: String? = null)

/** Exact aggregates may exceed Long even though each persisted amount fits Long. */
data class CurrencyTotals(
    val totalMinor: BigInteger = BigInteger.ZERO,
    val pendingMinor: BigInteger = BigInteger.ZERO,
    val completedMinor: BigInteger = BigInteger.ZERO,
    val pendingCount: Int = 0,
    val completedCount: Int = 0,
    val thisMonthPendingMinor: BigInteger = BigInteger.ZERO,
    val nextMonthPendingMinor: BigInteger = BigInteger.ZERO,
) {
    val count: Int get() = pendingCount + completedCount
    operator fun plus(other: CurrencyTotals) = CurrencyTotals(totalMinor + other.totalMinor,
        pendingMinor + other.pendingMinor, completedMinor + other.completedMinor,
        pendingCount + other.pendingCount, completedCount + other.completedCount,
        thisMonthPendingMinor + other.thisMonthPendingMinor, nextMonthPendingMinor + other.nextMonthPendingMinor)
}
data class FinancialSummary(val byCurrency: Map<String, CurrencyTotals> = emptyMap()) {
    val pendingCount: Int get() = byCurrency.values.sumOf { it.pendingCount }
    val completedCount: Int get() = byCurrency.values.sumOf { it.completedCount }
    val count: Int get() = pendingCount + completedCount
    val state: FinancialState get() = when {
        count == 0 -> FinancialState.NO_OBLIGATIONS
        pendingCount == 0 -> FinancialState.COMPLETED
        completedCount == 0 -> FinancialState.PENDING
        else -> FinancialState.PARTIAL
    }
    operator fun plus(other: FinancialSummary): FinancialSummary {
        val result = byCurrency.toMutableMap()
        other.byCurrency.forEach { (code, totals) -> result[code] = (result[code] ?: CurrencyTotals()) + totals }
        return FinancialSummary(result.toSortedMap())
    }
}

/** One read-only financial projection, shared by scopes and the transversal view. */
class FinancialSnapshot(
    val tree: NodeTreeSnapshot,
    responsibleByNode: Map<String, List<Person>>,
    val selection: FinancialSelection,
    currentMonth: CalendarMonth,
    zone: TimeZone,
) {
    val zoneId: String = zone.id
    val periodMonth: CalendarMonth? = selection.period.month(currentMonth)
    val responsibleByNode = responsibleByNode.mapValues { (_, people) -> people.toList() }.toMap()
    val tasks: List<Node>
    val summary: FinancialSummary
    val overview: FinancialSummary
    val overviewByNodeId: Map<String, FinancialSummary>
    val overviewByProjectId: Map<String, FinancialSummary>
    val byNodeId: Map<String, FinancialSummary>
    val byProjectId: Map<String, FinancialSummary>

    init {
        val remaining = tree.nodes.associate { it.id to 0 }.toMutableMap()
        tree.nodes.forEach { node -> node.parentId?.let { remaining[it] = remaining.getValue(it) + 1 } }
        val queue = ArrayDeque<Node>()
        val nodeTotals = mutableMapOf<String, FinancialSummary>()
        val projectTotals = mutableMapOf<String, FinancialSummary>()
        val overviewNodes = mutableMapOf<String, FinancialSummary>()
        val overviewProjects = mutableMapOf<String, FinancialSummary>()
        val included = mutableListOf<Node>()
        tree.nodes.forEach { node ->
            if (remaining.getValue(node.id) == 0) {
                val dueMonth = node.dueAt?.let { CalendarDates.localDay(it, zone).calendarMonth }
                val qualifies = node.purpose == NodePurpose.ACTION && node.obligation != null &&
                    (selection.personId == null || this.responsibleByNode[node.id].orEmpty().any { it.id == selection.personId })
                val own = if (qualifies) {
                    val obligation = checkNotNull(node.obligation)
                    val amount = BigInteger.valueOf(obligation.amountMinor)
                    FinancialSummary(mapOf(obligation.currencyCode to if (node.isCompleted)
                        CurrencyTotals(amount, completedMinor = amount, completedCount = 1)
                    else CurrencyTotals(amount, pendingMinor = amount, pendingCount = 1,
                        thisMonthPendingMinor = if (dueMonth == currentMonth) amount else BigInteger.ZERO,
                        nextMonthPendingMinor = if (dueMonth == currentMonth.shifted(1)) amount else BigInteger.ZERO)))
                } else FinancialSummary()
                overviewNodes[node.id] = own
                nodeTotals[node.id] = if (qualifies && (periodMonth == null || dueMonth == periodMonth)) {
                    included.add(node)
                    own
                } else FinancialSummary()
                queue.addLast(node)
            }
        }
        var processed = 0
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst(); processed++
            val own = nodeTotals.getOrDefault(node.id, FinancialSummary())
            nodeTotals[node.id] = own
            val overviewOwn = overviewNodes.getOrDefault(node.id, FinancialSummary())
            overviewNodes[node.id] = overviewOwn
            val parent = node.parentId
            if (parent == null) {
                projectTotals[node.projectId] = (projectTotals[node.projectId] ?: FinancialSummary()) + own
                overviewProjects[node.projectId] = (overviewProjects[node.projectId] ?: FinancialSummary()) + overviewOwn
            }
            else {
                overviewNodes[parent] = (overviewNodes[parent] ?: FinancialSummary()) + overviewOwn
                nodeTotals[parent] = (nodeTotals[parent] ?: FinancialSummary()) + own
                remaining[parent] = remaining.getValue(parent) - 1
                if (remaining[parent] == 0) queue.addLast(tree.nodesById.getValue(parent))
            }
        }
        check(processed == tree.nodes.size) { "Invalid financial hierarchy" }
        overviewByNodeId = overviewNodes.toMap()
        overviewByProjectId = overviewProjects.toMap()
        overview = overviewProjects.values.fold(FinancialSummary(), FinancialSummary::plus)
        byNodeId = nodeTotals.toMap()
        byProjectId = projectTotals.toMap()
        summary = projectTotals.values.fold(FinancialSummary(), FinancialSummary::plus)
        tasks = included.sortedWith(compareBy<Node> { it.dueAt == null }.thenBy { it.dueAt }.thenBy { it.createdAt }.thenBy { it.id })
    }
    fun pathTo(id: String): List<Node> {
        val path = mutableListOf<Node>()
        var node = tree.nodesById[id]
        while (node != null) { path.add(node); node = node.parentId?.let(tree.nodesById::getValue) }
        return path.asReversed()
    }
}
