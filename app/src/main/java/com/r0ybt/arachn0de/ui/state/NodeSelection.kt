package com.r0ybt.arachn0de.ui.state
import androidx.compose.runtime.*
internal class NodeSelection {
    var ids by mutableStateOf(emptySet<String>())
        private set
    fun toggle(id: String) { ids = if(id in ids) ids - id else ids + id }
    fun clear() { ids = emptySet() }
    fun retain(visible: Set<String>) { ids = ids intersect visible }
}
