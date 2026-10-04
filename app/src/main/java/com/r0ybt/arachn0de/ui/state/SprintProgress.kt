package com.r0ybt.arachn0de.ui.state

import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.domain.model.WorkState

/** All direct Sprint actions contribute equally, independently of completion and UI filters. */
internal fun sprintProgressPercentage(nodes: List<Node>, layerId: String): Double? {
    val phases = nodes.filter { it.parentId == layerId && it.purpose == NodePurpose.ACTION }
        .mapNotNull { it.workState }
    if (phases.isEmpty()) return null
    return phases.sumOf { phase ->
        when (phase) {
            WorkState.UNPLANNED -> 0.0
            WorkState.PLANNED -> 25.0
            WorkState.DOING -> 50.0
            WorkState.DONE -> 75.0
            WorkState.VALIDATED -> 100.0
        }
    } / phases.size
}
