package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.ui.state.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LoadStateTest {
    @Test fun readFailureKeepsLastValueAndAllowsRetry() = runBlocking {
        val state = LoadState()
        var value = 0
        state.collect(flow { emit(7); throw IllegalStateException("storage") }) { value = it }
        assertEquals(7, value)
        assertTrue(state.failed)
        state.retry()
        assertEquals(1, state.attempt)
        state.collect(flowOf(9)) { value = it }
        assertEquals(9, value)
        assertFalse(state.failed)
    }
    @Test fun cancellationIsRethrown() = runBlocking {
        val state = LoadState()
        try {
            state.collect(flow<Int> { throw CancellationException() }) {}
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) {
            assertFalse(state.failed)
        }
    }
}
