package com.r0ybt.arachn0de.domain.model

/**
 * Progress derived from a node's completable descendants.
 * A total of zero means there is no measurable work under this structure.
 */
enum class NodeProgressState {
    NO_WORK,
    NOT_STARTED,
    PARTIAL,
    COMPLETE,
}

data class NodeProgress(
    val nodeId: String,
    val completed: Int,
    val total: Int,
    val percentage: Int,
    val state: NodeProgressState,
) {
    val isComplete: Boolean
        get() = state == NodeProgressState.COMPLETE
}
