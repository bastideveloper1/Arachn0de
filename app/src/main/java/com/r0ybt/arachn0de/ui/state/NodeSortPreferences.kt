package com.r0ybt.arachn0de.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver

/** Layer overrides fall back to the project root; app-owned choices persist locally. */
internal class NodeSortPreferences(initial: List<Pair<String, NodeSortMode>> = emptyList(), private val storage: android.content.SharedPreferences? = null, private val onWrite: ((String, NodeSortMode?) -> Unit)? = null, private val onLayersWrite:((String,Boolean,NodeSortMode)->Unit)?=null) {
    private var entries by mutableStateOf(storage?.all?.mapNotNull { (key, value) ->
        NodeSortMode.entries.firstOrNull { it.name == value }?.let { key to it }
    } ?: initial.takeLast(MAX_CONTEXTS))
    private var layerEntries by mutableStateOf(emptyMap<String,Boolean>())
    fun layersFirst(context:String)=layerEntries[context] ?: false
    fun setLayersFirst(context:String,enabled:Boolean) {
        require(context.length in 1..256)
        layerEntries=layerEntries+(context to enabled)
        onLayersWrite?.invoke(context,enabled,mode(context))
    }
    fun replace(rows: List<com.r0ybt.arachn0de.data.local.NodeSortPreferenceEntity>) {
        layerEntries=rows.associate {it.context to it.layersFirst}
        entries = rows.mapNotNull { row -> NodeSortMode.entries.firstOrNull { it.name == row.mode }?.let { row.context to it } }
    }
    fun mode(context: String): NodeSortMode = entries.firstOrNull { it.first == context }?.second
        ?: entries.firstOrNull { it.first == context.substringBeforeLast(':') + ":project-root" }?.second
        ?: NodeSortMode.MANUAL
    fun inherit(context: String) {
        entries = entries.filterNot { it.first == context }
        storage?.edit()?.remove(context)?.apply()
        onWrite?.invoke(context, null)
    }
    fun set(context: String, mode: NodeSortMode) {
        require(context.length in 1..256)
        val retained = entries.filterNot { it.first == context }
        entries = (retained + (context to mode)).let { if (storage == null && onWrite == null) it.takeLast(MAX_CONTEXTS) else it }
        storage?.edit()?.putString(context, mode.name)?.apply()
        onWrite?.invoke(context, mode)
    }
    companion object {
        const val MAX_CONTEXTS = 64
        val Saver = listSaver<NodeSortPreferences, String>(
            save = { it.entries.flatMap { (context, mode) -> listOf(context, mode.name) } + listOf("__layers_first__") + it.layerEntries.flatMap {(context,enabled)->listOf(context,enabled.toString())} },
            restore = { values -> NodeSortPreferences(values.takeWhile {it!="__layers_first__"}.chunked(2).mapNotNull { pair ->
                if (pair.size != 2 || pair[0].length !in 1..256) null
                else NodeSortMode.entries.firstOrNull { it.name == pair[1] }?.let { pair[0] to it }
            }).apply {layerEntries=values.dropWhile {it!="__layers_first__"}.drop(1).chunked(2).filter {it.size==2}.associate {it[0] to it[1].toBoolean()} } },
        )
    }
}
