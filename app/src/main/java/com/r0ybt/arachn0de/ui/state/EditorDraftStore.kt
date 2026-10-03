package com.r0ybt.arachn0de.ui.state

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver

/** Screen/project-owned input, scoped separately to each destination or edited Node. */
internal class EditorDraftStore {
    private val entries = mutableStateMapOf<String, EditorDraft>()
    var activeKey by mutableStateOf<String?>(null)
        private set
    val active: EditorDraft? get() = activeKey?.let(entries::get)
    fun open(parentId: String?, nodeId: String? = null, seed: () -> EditorDraft) {
        val key = if (nodeId == null) "new:${parentId.orEmpty()}" else "edit:$nodeId"
        entries.getOrPut(key, seed)
        activeKey = key
    }
    fun close() { active?.showResponsible = false; activeKey = null }
    fun clear() { activeKey?.let(entries::remove); activeKey = null }
    companion object {
        val Saver = listSaver<EditorDraftStore, String>(
            save = { store ->
                val scope = this
                buildList {
                add(store.activeKey.orEmpty())
                store.entries.forEach { (key, draft) ->
                    val data = (checkNotNull(with(EditorDraft.Saver) { scope.save(draft) }) as List<*>).map { it as String }
                    add(key); add(data.size.toString()); addAll(data)
                }
            } },
            restore = { values -> EditorDraftStore().apply {
                activeKey = values.first().ifEmpty { null }
                var index = 1
                while (index < values.size) {
                    val key = values[index++]; val count = values[index++].toInt()
                    entries[key] = checkNotNull(EditorDraft.Saver.restore(values.subList(index, index + count)))
                    index += count
                }
            } },
        )
    }
}
