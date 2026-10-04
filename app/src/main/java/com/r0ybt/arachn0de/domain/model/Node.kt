package com.r0ybt.arachn0de.domain.model

/** LAYER is an explicit container; child presence and progress remain derived. */
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
    val creationGroupId: String? = null,
) {
    val effectivePriority: Priority get() = if (isCompletable) priority else Priority.NONE
    val isStructural: Boolean get() = purpose == NodePurpose.LAYER
    val isCompletable: Boolean get() = !hasChildren && purpose == NodePurpose.ACTION
}
