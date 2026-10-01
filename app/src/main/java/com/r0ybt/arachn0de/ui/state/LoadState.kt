package com.r0ybt.arachn0de.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

/** A failed observation can be explicitly restarted without discarding the last valid data. */
internal class LoadState {
    var failed by mutableStateOf(false)
        private set
    var attempt by mutableStateOf(0)
        private set
    fun retry() { failed = false; attempt++ }
    suspend fun <T> collect(flow: Flow<T>, onValue: (T) -> Unit) {
        try {
            flow.collect { onValue(it); failed = false }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failed = true
        }
    }
}
