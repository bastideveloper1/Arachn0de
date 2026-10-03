package com.r0ybt.arachn0de.ui.state

import android.net.Uri
import androidx.compose.runtime.*
import com.r0ybt.arachn0de.backup.*
import kotlinx.coroutines.*
import java.io.File

/** No backup data goes into saved UI state and no destructive operation is replayed on recreation. */
internal class BackupActions(
    private val repository: BackupRepository,
    private val documents: BackupDocuments,
    private val scope: CoroutineScope,
) {
    var busy by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var candidate by mutableStateOf<BackupData?>(null)
        private set

    suspend fun recover() { runCatching { repository.recover() } }
    private fun run(error: String, work: suspend () -> Unit) {
        if (busy) return
        busy = true
        notice = null
        scope.launch(Dispatchers.Main.immediate) {
            try { work() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notice = error }
            catch (_: OutOfMemoryError) { notice = "No hay memoria suficiente para procesar este backup." }
            finally { busy = false }
        }
    }
    fun create(ready: (File) -> Unit) = run("No se pudo crear el backup. Revisa el espacio disponible; los datos actuales se conservan.") {
        val file = repository.create()
        try { ready(file) } catch (failure: Exception) { file.delete(); throw failure }
    }
    fun save(name: String, uri: Uri) = run("No se pudo guardar el backup. Si quedó un archivo incompleto en el destino, elimínalo y vuelve a intentarlo.") {
        val file = repository.pendingFile(name)
        try {
            documents.save(file, uri)
            notice = "Backup guardado correctamente."
        } finally { file.delete() }
    }
    fun cancelSave(name: String?) { name?.let { runCatching { repository.pendingFile(it).delete() } } }
    fun inspect(uri: Uri) = run("No se pudo validar el backup: puede estar dañado, ser incompatible o superar los límites admitidos. No se han cambiado los datos actuales.") {
        candidate = null
        candidate = documents.read(repository, uri)
    }
    fun noticeSelectionFailed() { notice = "Android no pudo abrir el selector de archivos. Vuelve a intentarlo." }
    fun dismiss() { if (!busy) candidate = null }
    fun restore(onRestored: () -> Unit) {
        val data = candidate ?: return
        run("No se pudo restaurar el backup. Se conservan los datos anteriores; puedes volver a intentarlo.") {
            repository.restore(data)
            candidate = null
            notice = "Backup restaurado correctamente."
            try { onRestored() } catch (_: Exception) {
                notice = "Los datos se restauraron, pero no se pudo actualizar la pantalla. Vuelve a Proyectos."
            }
        }
    }
}
