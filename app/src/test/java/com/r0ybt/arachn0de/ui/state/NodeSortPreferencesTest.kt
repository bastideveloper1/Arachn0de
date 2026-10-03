package com.r0ybt.arachn0de.ui.state

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test

class NodeSortPreferencesTest {
    private val scope=object:SaverScope { override fun canBeSaved(value:Any)=true }
    @Test fun contextsAreIndependentSavedAndManualIsDefault() {
        val store=NodeSortPreferences()
        store.set("root",NodeSortMode.DUE_ASC);store.set("ideas",NodeSortMode.PRIORITY)
        val saved=checkNotNull(with(NodeSortPreferences.Saver) { scope.save(store) })
        val restored=checkNotNull(NodeSortPreferences.Saver.restore(saved))
        assertEquals(NodeSortMode.DUE_ASC,restored.mode("root"))
        assertEquals(NodeSortMode.PRIORITY,restored.mode("ideas"))
        assertEquals(NodeSortMode.MANUAL,restored.mode("new-layer"))
        restored.set("root",NodeSortMode.MANUAL)
        assertEquals(NodeSortMode.MANUAL,restored.mode("root"))
        assertEquals(NodeSortMode.PRIORITY,restored.mode("ideas"))
        assertEquals(NodeSortMode.MANUAL,NodeSortPreferences().mode("ideas"))
    }
    @Test fun savedStateIsBoundedAndEvictsLeastRecentlyChangedContexts() {
        val store=NodeSortPreferences()
        repeat(64) { store.set("$it",NodeSortMode.DUE_ASC) }
        store.set("0",NodeSortMode.DUE_DESC);store.set("64",NodeSortMode.PRIORITY)
        assertEquals(NodeSortMode.MANUAL,store.mode("1"))
        assertEquals(NodeSortMode.DUE_DESC,store.mode("0"))
        assertEquals(128,(with(NodeSortPreferences.Saver) { scope.save(store) } as List<*>).size)
    }
    @Test fun unknownOrIncompleteSavedEntriesFallBackSafely() {
        val restored=NodeSortPreferences.Saver.restore(listOf("a","UNKNOWN","b","DUE_ASC","dangling"))!!
        assertEquals(NodeSortMode.MANUAL,restored.mode("a"));assertEquals(NodeSortMode.DUE_ASC,restored.mode("b"))
    }
}
