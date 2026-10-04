package com.r0ybt.arachn0de.ui.state

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[28])
class NodeSortPreferencesTest {
    private val scope=object:SaverScope { override fun canBeSaved(value:Any)=true }
    @Test fun projectDefaultOverridesAndExplicitManualPersistAcrossNewInstances() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val storage=context.getSharedPreferences("sort-persistence-test",android.content.Context.MODE_PRIVATE)
        storage.edit().clear().commit()
        try {
            val store=NodeSortPreferences(storage=storage)
            store.set("p:project-root",NodeSortMode.DUE_PRIORITY)
            assertEquals(NodeSortMode.DUE_PRIORITY,store.mode("p:a"))
            store.set("p:b",NodeSortMode.PRIORITY)
            store.set("p:c",NodeSortMode.MANUAL)
            val reopened=NodeSortPreferences(storage=context.getSharedPreferences("sort-persistence-test",android.content.Context.MODE_PRIVATE))
            assertEquals(NodeSortMode.DUE_PRIORITY,reopened.mode("p:project-root"))
            assertEquals(NodeSortMode.DUE_PRIORITY,reopened.mode("p:a"))
            assertEquals(NodeSortMode.PRIORITY,reopened.mode("p:b"))
            assertEquals(NodeSortMode.MANUAL,reopened.mode("p:c"))
            reopened.set("p:project-root",NodeSortMode.DUE_ASC)
            assertEquals(NodeSortMode.DUE_ASC,reopened.mode("p:a"))
            assertEquals(NodeSortMode.PRIORITY,reopened.mode("p:b"))
            assertEquals(NodeSortMode.MANUAL,reopened.mode("p:c"))
            reopened.inherit("p:c")
            assertEquals(NodeSortMode.DUE_ASC,NodeSortPreferences(storage=storage).mode("p:c"))
            assertEquals(NodeSortMode.MANUAL,reopened.mode("other:layer"))
        } finally { storage.edit().clear().commit() }
    }
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
