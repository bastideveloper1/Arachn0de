package com.r0ybt.arachn0de.domain.model

/**
 * A node represents a single item in the onion-layer hierarchy.
 * It can be either structural (container) or completable (actionable), never both.
 */
data class Node(
    val id: String,
    val projectId: String,
    val parentId: String?,
    val title: String,
    val description: String,
    val isStructural: Boolean,
    val isCompletable: Boolean,
    val isCompleted: Boolean,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
