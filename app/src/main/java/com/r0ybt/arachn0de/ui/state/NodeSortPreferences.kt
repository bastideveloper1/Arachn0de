package com.r0ybt.arachn0de.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver

/** At most 64 explicitly changed contexts in the session; untouched/evicted contexts are Manual. */
internal class NodeSortPreferences(initial: List<Pair<String, NodeSortMode>> = emptyList()) {
    private var entries by mutableStateOf(initial.takeLast(MAX_CONTEXTS))
    fun mode(context: String): NodeSortMode = entries.firstOrNull { it.first == context }?.second ?: NodeSortMode.MANUAL
    fun set(context: String, mode: NodeSortMode) {
        require(context.length in 1..256)
        val retained = entries.filterNot { it.first == context }
        entries = (if (mode == NodeSortMode.MANUAL) retained else retained + (context to mode)).takeLast(MAX_CONTEXTS)
    }
    companion object {
        const val MAX_CONTEXTS = 64
        val Saver = listSaver<NodeSortPreferences, String>(
            save = { it.entries.flatMap { (context, mode) -> listOf(context, mode.name) } },
            restore = { values -> NodeSortPreferences(values.chunked(2).mapNotNull { pair ->
                if (pair.size != 2 || pair[0].length !in 1..256) null
                else NodeSortMode.entries.firstOrNull { it.name == pair[1] && it != NodeSortMode.MANUAL }?.let { pair[0] to it }
            }) },
        )
    }
}
