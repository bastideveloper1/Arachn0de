package com.r0ybt.arachn0de.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.r0ybt.arachn0de.update.*
import kotlinx.coroutines.*

internal sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: GitHubRelease) : UpdateState
    data class Downloading(val release: GitHubRelease) : UpdateState
    data class Verifying(val release: GitHubRelease) : UpdateState
    data class Ready(val update: VerifiedUpdate) : UpdateState
    data object Error : UpdateState
}

internal class UpdateActions(private val repository: UpdateRepository, private val scope: CoroutineScope, private val installed: String,
    private val downloads: UpdateDownloads? = null) {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    var downloadRetry by mutableStateOf<GitHubRelease?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var awaitingAndroid by mutableStateOf(false)
        private set
    private var recovery: Job? = null
    val busy get() = awaitingAndroid || state == UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Verifying

    fun recover() {
        if (downloads == null || state != UpdateState.Idle) return
        recovery = scope.launch {
            val update = try { downloads.recover() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
            if (state == UpdateState.Idle && update != null) state = UpdateState.Ready(update)
        }
    }
    fun check() {
        if (busy) return
        recovery?.cancel(); downloadRetry = null; notice = null
        state = UpdateState.Checking
        scope.launch {
            try {
                val release = repository.newerRelease(installed)
                state = if (release == null) UpdateState.UpToDate else UpdateState.Available(release)
            } catch (cancelled: CancellationException) {
                state = UpdateState.Idle
                throw cancelled
            } catch (_: Exception) { state = UpdateState.Error }
        }
    }
    fun download() {
        if (busy || downloads == null) return
        val release = (state as? UpdateState.Available)?.release ?: downloadRetry ?: return
        recovery?.cancel(); notice = null; downloadRetry = release
        state = UpdateState.Downloading(release)
        scope.launch {
            try {
                val update = downloads.download(release) {
                    withContext(scope.coroutineContext.minusKey(Job)) { state = UpdateState.Verifying(release) }
                }
                state = UpdateState.Ready(update); downloadRetry = null
            } catch (cancelled: CancellationException) {
                state = UpdateState.Available(release)
                throw cancelled
            } catch (failure: Exception) {
                state = UpdateState.Error
                notice = (failure as? UpdateRejected)?.message ?: "No se pudo descargar o verificar la actualización. Comprueba la conexión y el espacio disponible. Se exige checksum, paquete, versión y firma válidos."
            }
        }
    }
    fun prepareInstall(onReady: (VerifiedUpdate) -> Unit) {
        if (busy || downloads == null) return
        val update = (state as? UpdateState.Ready)?.update ?: return
        notice = null; state = UpdateState.Verifying(update.release)
        scope.launch {
            try {
                downloads.revalidate(update)
                state = UpdateState.Ready(update); awaitingAndroid = true
                onReady(update)
            } catch (cancelled: CancellationException) { state = UpdateState.Ready(update); throw cancelled }
            catch (_: Exception) {
                awaitingAndroid = false; state = UpdateState.Error; downloadRetry = update.release
                notice = "La actualización ya no está disponible o no supera la verificación. Descárgala de nuevo."
            }
        }
    }
    fun androidReturned(cancelled: Boolean, settings: Boolean = false) {
        awaitingAndroid = false
        notice = if (settings) "Vuelve a pulsar Instalar actualización para continuar. Android debe autorizar esta fuente."
            else if (cancelled) "La instalación se canceló o no se completó. Puedes volver a intentarlo."
            else "La solicitud de instalación finalizó. Android determina el resultado; no se confirma una actualización desde esta pantalla."
    }
    fun launchFailed() {
        awaitingAndroid = false
        notice = "No se pudo abrir el instalador o la configuración de Android. Puedes volver a intentarlo."
    }
}
