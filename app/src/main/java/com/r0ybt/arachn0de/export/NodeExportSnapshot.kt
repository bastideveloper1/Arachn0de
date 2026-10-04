package com.r0ybt.arachn0de.export

import com.r0ybt.arachn0de.domain.model.Project
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot
import java.util.Collections

enum class ExportKind { LAYER, ACTION, NOTE }
data class ExportEntry(val title: String, val description: String, val depth: Int,
    val kind: ExportKind, val completed: Boolean)
class ContextTooLargeException : Exception("El contexto es demasiado grande para el portapapeles. Copia una capa más pequeña.")

/** Only presentation content leaves the existing tree; no IDs, money, dates, or associations. */
class NodeExportSnapshot private constructor(val entries: List<ExportEntry>) {
    companion object {
        // About 400 KiB of UTF-16, leaving headroom below Android's shared Binder transaction budget.
        const val MAX_TEXT_CHARS = 200_000
        fun captureProject(project: Project, tree: NodeTreeSnapshot, descendants: Boolean,
            checkCancelled: () -> Unit = {}): NodeExportSnapshot {
            checkCancelled()
            val result = mutableListOf(ExportEntry(project.name,project.description,0,ExportKind.LAYER,false))
            var size = project.name.length.toLong() + project.description.length + 8
            if (size > MAX_TEXT_CHARS) throw ContextTooLargeException()
            if (descendants) tree.childrenOf(null).filter { it.projectId == project.id }.forEach { root ->
                val branch = capture(tree,root.id,true,checkCancelled)
                branch.entries.forEach { entry ->
                    checkCancelled()
                    size += entry.title.length.toLong() + entry.description.length + 8
                    if(size > MAX_TEXT_CHARS) throw ContextTooLargeException()
                    result.add(entry.copy(depth=entry.depth+1))
                }
            }
            return NodeExportSnapshot(Collections.unmodifiableList(result))
        }
        fun capture(tree: NodeTreeSnapshot, rootId: String, descendants: Boolean,
            checkCancelled: () -> Unit = {}): NodeExportSnapshot {
            require(rootId in tree.nodesById) { "El elemento ya no existe." }
            val result = mutableListOf<ExportEntry>()
            val pending = ArrayDeque<Pair<String, Int>>()
            pending.addLast(rootId to 0)
            var contentSize = 0L
            while (pending.isNotEmpty()) {
                checkCancelled()
                val (id, depth) = pending.removeLast()
                val node = tree.nodesById.getValue(id)
                val children = tree.childrenOf(id)
                contentSize += node.title.length.toLong() + node.description.length + 8
                if (contentSize > MAX_TEXT_CHARS) throw ContextTooLargeException()
                result.add(ExportEntry(node.title, node.description, depth,
                    if (node.isStructural) ExportKind.LAYER else if (node.purpose == NodePurpose.NOTE) ExportKind.NOTE else ExportKind.ACTION,
                    node.isCompleted))
                if (descendants) children.asReversed().forEach { pending.addLast(it.id to depth + 1) }
            }
            return NodeExportSnapshot(Collections.unmodifiableList(result))
        }
    }
}
