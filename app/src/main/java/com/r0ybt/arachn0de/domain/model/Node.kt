package com.r0ybt.arachn0de.domain.model

/** Structure is derived from persisted children, never selected or stored as a type. */
data class Node(
    val id: String,
    val projectId: String,
    val parentId: String?,
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val hasChildren: Boolean,
    val startAt: Long? = null,
    val dueAt: Long? = null,
    val purpose: NodePurpose = NodePurpose.ACTION,
    val obligation: Obligation? = null,
    val priority: Priority = Priority.NONE,
) {
    val effectivePriority: Priority get() = if (isCompletable) priority else Priority.NONE
    val isStructural: Boolean get() = hasChildren
    val isCompletable: Boolean get() = !hasChildren && purpose == NodePurpose.ACTION
}
