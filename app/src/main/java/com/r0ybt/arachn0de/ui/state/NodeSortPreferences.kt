package com.r0ybt.arachn0de.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver

/** Layer overrides fall back to the project root; app-owned choices persist locally. */
internal class NodeSortPreferences(initial: List<Pair<String, NodeSortMode>> = emptyList(), private val storage: android.content.SharedPreferences? = null) {
    private var entries by mutableStateOf(storage?.all?.mapNotNull { (key, value) ->
        NodeSortMode.entries.firstOrNull { it.name == value }?.let { key to it }
    } ?: initial.takeLast(MAX_CONTEXTS))
    fun mode(context: String): NodeSortMode = entries.firstOrNull { it.first == context }?.second
        ?: entries.firstOrNull { it.first == context.substringBeforeLast(':') + ":project-root" }?.second
        ?: NodeSortMode.MANUAL
    fun inherit(context: String) {
        entries = entries.filterNot { it.first == context }
        storage?.edit()?.remove(context)?.apply()
    }
    fun set(context: String, mode: NodeSortMode) {
        require(context.length in 1..256)
        val retained = entries.filterNot { it.first == context }
        entries = (retained + (context to mode)).let { if (storage == null) it.takeLast(MAX_CONTEXTS) else it }
        storage?.edit()?.putString(context, mode.name)?.apply()
    }
    companion object {
        const val MAX_CONTEXTS = 64
        val Saver = listSaver<NodeSortPreferences, String>(
            save = { it.entries.flatMap { (context, mode) -> listOf(context, mode.name) } },
            restore = { values -> NodeSortPreferences(values.chunked(2).mapNotNull { pair ->
                if (pair.size != 2 || pair[0].length !in 1..256) null
                else NodeSortMode.entries.firstOrNull { it.name == pair[1] }?.let { pair[0] to it }
            }) },
        )
    }
}
