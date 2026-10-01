package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.ui.state.OperationState
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OperationStateTest {
    @Test fun failureAndFalseNeverCallSuccessAndCanRetry() = runBlocking {
        val state = OperationState(CoroutineScope(coroutineContext + Dispatchers.Unconfined))
        var successes = 0
        state.submit("No guardado", { throw IllegalStateException("storage failure") }, { successes++ })
        assertEquals("No guardado", state.error)
        assertFalse(state.busy)
        state.submit("No guardado", { false }, { successes++ })
        assertNotNull(state.error)
        assertEquals(0, successes)
        state.submit("No guardado", { true }, { successes++ })
        assertEquals(1, successes)
        assertNull(state.error)
    }

    @Test fun overlappingSubmissionsExecuteOnlyOnce() = runBlocking {
        val state = OperationState(CoroutineScope(coroutineContext + Dispatchers.Unconfined))
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        state.submit("error", { writes++; gate.await(); true })
        state.submit("error", { writes++; true })
        assertTrue(state.busy)
        assertEquals(1, writes)
        gate.complete(Unit)
        yield()
        assertFalse(state.busy)
    }

    @Test fun cancellationIsNotDisplayedAsAnErrorOrSuccess() = runBlocking {
        val job = SupervisorJob()
        val state = OperationState(CoroutineScope(job + Dispatchers.Unconfined))
        var success = false
        state.submit("error", { awaitCancellation() }, { success = true })
        job.cancel()
        assertFalse(state.busy)
        assertNull(state.error)
        assertFalse(success)
    }
}
