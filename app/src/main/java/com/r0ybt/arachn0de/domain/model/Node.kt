package com.r0ybt.arachn0de.domain.model

/** LAYER is an explicit container; child presence and progress remain derived. */
data class Node(
    val id: String,
    val projectId: String?,
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
    val sprintMode: Boolean = false,
    val workState: WorkState? = null,
    val isPinned:Boolean=false,
    val awaitingVerification:Boolean=false,
    val taskStateCount:Int?=null,
    val effort:Int=0,
) {
    val taskStatus: TaskStatus get() = if (isCompleted) TaskStatus.COMPLETED else if (awaitingVerification) TaskStatus.VERIFY else TaskStatus.PENDING
    val effectivePriority: Priority get() = if (isCompletable) priority else Priority.NONE
    val canReceiveChildren: Boolean get() = projectId != null && !awaitingVerification && obligation == null && (isStructural || (purpose == NodePurpose.ACTION && !isCompleted))
    val isStructural: Boolean get() = purpose == NodePurpose.LAYER
    val isCompletable: Boolean get() = !hasChildren && purpose == NodePurpose.ACTION
}
