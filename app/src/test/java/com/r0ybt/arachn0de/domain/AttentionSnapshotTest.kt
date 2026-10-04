package com.r0ybt.arachn0de.domain

import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class AttentionSnapshotTest {
    private val now = 100_000L
    private val day = TaskTemporal.UPCOMING_WINDOW_MILLIS
    private fun node(id: String, parent: String? = null, project: String = "p", due: Long? = null, start: Long? = null, completed: Boolean = false, position: Int = 0) =
        Node(id, project, parent, id, "", completed, position, 0, 0, false, start, due)
    private fun tree(nodes: List<Node>): NodeTreeSnapshot {
        val parents = nodes.mapNotNull { it.parentId }.toSet()
        return NodeTreeSnapshot(nodes.map { it.copy(hasChildren = it.id in parents, purpose = if (it.id in parents) NodePurpose.LAYER else it.purpose) })
    }

    @Test fun onlyOperativeUpcomingAndOverdueLeavesGenerateAttention() {
        val source = tree(listOf(
            node("near", due = now + 1), node("late", due = now - 1),
            node("done", due = now - 1, completed = true), node("active", start = now - 1),
            node("far", due = now + day + 1), node("scheduled", start = now + 1, due = now + 2),
            node("neutral"), node("layer", due = now - 1), node("child", parent = "layer"),
        ))
        val result = AttentionSnapshot(source, now)
        assertEquals(AttentionLevel.UPCOMING, result.byNodeId.getValue("near").level)
        assertEquals(AttentionLevel.OVERDUE, result.byNodeId.getValue("late").level)
        listOf("done", "active", "far", "scheduled", "neutral", "layer", "child").forEach {
            assertEquals(AttentionSummary(), result.byNodeId.getValue(it))
        }
        assertEquals(listOf("late", "near"), result.tasks.map { it.id })
        assertEquals(AttentionSummary(1, 1), result.byProjectId.getValue("p"))
    }

    @Test fun multipleAncestorsBranchesAndProjectsSumLeavesOnceWithMaximumSeverity() {
        val source = tree(listOf(
            node("a"), node("b", "a"), node("c", "b", due = now - 10),
            node("late", "c", due = now - 1), node("near1", "c", due = now),
            node("branch", "a"), node("near2", "branch", due = now + 10), node("near3", "branch", due = now + 20),
            node("other", project = "q"), node("otherNear", "other", project = "q", due = now + 30),
        ))
        val result = AttentionSnapshot(source, now)
        assertEquals(AttentionSummary(3, 1), result.byNodeId.getValue("a"))
        assertEquals(AttentionSummary(1, 1), result.byNodeId.getValue("b"))
        assertEquals(AttentionSummary(1, 1), result.byNodeId.getValue("c"))
        assertEquals(AttentionSummary(2, 0), result.byNodeId.getValue("branch"))
        assertEquals(AttentionLevel.OVERDUE, result.byNodeId.getValue("a").level)
        assertEquals(4, result.byNodeId.getValue("a").total)
        assertEquals(AttentionSummary(3, 1), result.byProjectId.getValue("p"))
        assertEquals(AttentionSummary(1, 0), result.byProjectId.getValue("q"))
        assertEquals(listOf("a", "b", "c", "late"), result.pathTo("late").map { it.id })
        assertEquals(listOf("other", "otherNear"), result.pathTo("otherNear").map { it.id })
        assertTrue(result.pathTo("absent").isEmpty())
    }

    @Test fun projectionOrderingIsDeterministicAndDoesNotModifyManualOrderOrProgress() {
        val nodes = listOf(node("z", due = now + 20, position = 0), node("b", due = now - 1, position = 1),
            node("a", due = now - 1, position = 2), node("oldest", due = now - 10, position = 3),
            node("soon", due = now + 10, position = 4))
        val source = tree(nodes)
        val order = source.childrenOf(null).map { it.id }
        val progress = source.progressById.toMap()
        val result = AttentionSnapshot(source, now)
        assertEquals(listOf("oldest", "a", "b", "soon", "z"), result.tasks.map { it.id })
        assertEquals(result.tasks.map { it.id }, AttentionSnapshot(tree(nodes.reversed()), now).tasks.map { it.id })
        assertEquals(order, source.childrenOf(null).map { it.id })
        assertEquals(progress, source.progressById)
        assertEquals(nodes, source.nodes)
    }

    @Test fun changesInTimeReuseExistingTemporalBoundaries() {
        val source = tree(listOf(node("n", due = now + day + 1)))
        assertEquals(AttentionLevel.NONE, AttentionSnapshot(source, now).byNodeId.getValue("n").level)
        assertEquals(AttentionLevel.UPCOMING, AttentionSnapshot(source, now + 1).byNodeId.getValue("n").level)
        assertEquals(AttentionLevel.UPCOMING, AttentionSnapshot(source, now + day + 1).byNodeId.getValue("n").level)
        assertEquals(AttentionLevel.OVERDUE, AttentionSnapshot(source, now + day + 2).byNodeId.getValue("n").level)
    }

    @Test fun tenThousandLevelsAreIterativeAndDoNotCopyEveryAncestorPath() {
        val depth = 10_000
        val source = tree((0 until depth).map { index -> node("n$index", if (index == 0) null else "n${index - 1}", due = if (index == depth - 1) now - 1 else null) })
        val result = AttentionSnapshot(source, now)
        assertEquals(depth, result.byNodeId.size)
        assertEquals(AttentionSummary(0, 1), result.byNodeId.getValue("n0"))
        assertEquals(AttentionSummary(0, 1), result.byProjectId.getValue("p"))
        assertEquals(1, result.tasks.size)
        assertEquals(depth, result.pathTo("n${depth - 1}").size)
    }

    @Test fun emptyTreeHasNoArtificialRootOrAttention() {
        val result = AttentionSnapshot(NodeTreeSnapshot(emptyList()), now)
        assertTrue(result.tasks.isEmpty()); assertTrue(result.byNodeId.isEmpty()); assertTrue(result.byProjectId.isEmpty())
    }
}
