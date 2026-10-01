package com.r0ybt.arachn0de.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One operation at a time, owned by the screen composition. Never treats cancellation as failure. */
internal class OperationState(private val scope: CoroutineScope) {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun clearError() { error = null }

    fun submit(
        failureMessage: String,
        operation: suspend () -> Boolean,
        onSuccess: () -> Unit = {},
    ) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val succeeded = try {
                if (operation()) true else {
                    error = "El elemento ya no existe o la operación ya no es válida. Revisa los datos antes de reintentar."
                    false
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failureMessage
                false
            } finally {
                busy = false
            }
            if (succeeded) onSuccess()
        }
    }
}
