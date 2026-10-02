package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.ui.state.LayerHierarchyIndex
import org.junit.Assert.*
import org.junit.Test

class LayerHierarchyIndexTest {
    private fun node(id: String, parent: String? = null, position: Int = 0) =
        Node(id, "project", parent, id, "", false, position, 0, 0, false)

    @Test fun tenThousandDeepNodesNeedNoCallStack() {
        val nodes = (0 until 10_000).map { node("n$it", if (it == 0) null else "n${it - 1}") }
        val index = LayerHierarchyIndex(nodes)
        assertEquals(10_000, index.subtreeIds("n0").size)
        assertEquals(setOf("n9999"), index.subtreeIds("n9999"))
        assertEquals(1, index.visibleRows(emptySet()).size)
        val rows = index.visibleRows(nodes.map { it.id }.toSet())
        assertEquals(10_000, rows.size)
        assertEquals(9_999, rows.last().depth)
        assertEquals(nodes.map { it.id }, rows.map { it.node.id })
    }

    @Test fun largeMixedBranchesExpandAndCollapseWithoutLosingOrder() {
        val nodes = buildList {
            repeat(2_000) { i ->
                add(node("layer$i", position = i))
                repeat(5) { j -> add(node("task$i-$j", "layer$i", j)) }
            }
        }
        val index = LayerHierarchyIndex(nodes.reversed())
        val expanded = setOf("layer0", "layer1999")
        val rows = index.visibleRows(expanded)
        assertEquals(2_010, rows.size)
        assertEquals(listOf("layer0", "task0-0", "task0-1"), rows.take(3).map { it.node.id })
        assertEquals("task1999-4", rows.last().node.id)
        assertEquals(2_000, index.visibleRows(emptySet()).size)
        assertEquals(rows, index.visibleRows(expanded))
    }

    @Test fun tenThousandSiblingTasksHaveStableTieBreakers() {
        val nodes = (0 until 10_000).map { node(it.toString().padStart(5, '0')) }
        val rows = LayerHierarchyIndex(nodes.reversed()).visibleRows(emptySet())
        assertEquals(nodes.map { it.id }, rows.map { it.node.id })
        assertTrue(rows.none { it.hasChildren })
    }

    @Test fun collapsingAncestorPreservesDescendantExpansionChoice() {
        val index = LayerHierarchyIndex(listOf(node("a"), node("b", "a"), node("c", "b")))
        assertEquals(listOf("a", "b", "c"), index.visibleRows(setOf("a", "b")).map { it.node.id })
        assertEquals(listOf("a"), index.visibleRows(setOf("b")).map { it.node.id })
        assertEquals(listOf("a", "b", "c"), index.visibleRows(setOf("a", "b")).map { it.node.id })
    }

    @Test fun corruptCycleIsRejectedWithoutHanging() {
        try { LayerHierarchyIndex(listOf(node("a", "b"), node("b", "a"))); fail("Cycle accepted") }
        catch (expected: IllegalArgumentException) { }
    }
}
