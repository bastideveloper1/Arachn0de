package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.state.OperationState
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ImmediateTagAssignmentTest {
    @Test fun pendingWriteBlocksDuplicatesAndFailureOrCancellationRestoresSelection()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val operation=OperationState(scope)
        val draft=EditorDraft("node",null,"","").apply {tagIds=listOf("before")}
        val release=CompletableDeferred<Boolean>()
        var writes=0
        submitTagAssignment(draft,setOf("after"),operation,{writes++;release.await()},{})
        assertEquals(listOf("after"),draft.tagIds);assertTrue(operation.busy)
        submitTagAssignment(draft,setOf("duplicate"),operation,{writes++;true},{})
        assertEquals(1,writes)
        release.complete(false)
        assertEquals(listOf("before"),draft.tagIds);assertFalse(operation.busy)
        submitTagAssignment(draft,setOf("after"),operation,{error("Write failure")},{})
        assertEquals(listOf("before"),draft.tagIds);assertFalse(operation.busy)
        submitTagAssignment(draft,setOf("after"),operation,{awaitCancellation()},{})
        scope.cancel()
        assertEquals(listOf("before"),draft.tagIds);assertFalse(operation.busy)
    }
}
