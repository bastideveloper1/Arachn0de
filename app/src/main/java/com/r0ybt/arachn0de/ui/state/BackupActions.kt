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
    private fun run(error: String, onCompletion:()->Unit={}, work: suspend () -> Unit) {
        if (busy) { onCompletion();return }
        busy = true
        notice = null
        scope.launch(Dispatchers.Main.immediate) {
            try { work() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (attachment: AttachmentBackupException) { notice = attachment.message }
            catch (_: Exception) { notice = error }
            catch (_: OutOfMemoryError) { notice = "No hay memoria suficiente para procesar este backup." }
            finally { busy = false }
        }.invokeOnCompletion { onCompletion() }
    }
    fun create(ready:(File)->Unit)=create(null,ready)
    fun create(password:CharArray?,ready: (File) -> Unit) = run("No se pudo crear el backup. Revisa el espacio disponible; los datos actuales se conservan.",onCompletion={password?.fill('\u0000')}) {
        val file = try { repository.create(password) } finally { password?.fill('\u0000') }
        try { ready(file) } catch (failure: Exception) { runCatching { repository.discardPending(file.name) }; throw failure }
    }
    fun save(name: String, uri: Uri) = run("No se pudo guardar el backup. Si quedó un archivo incompleto en el destino, elimínalo y vuelve a intentarlo.") {
        val file = repository.pendingFile(name)
        try {
            documents.save(file, uri)
            notice = "Backup guardado correctamente."
        } finally { runCatching { repository.discardPending(file.name) } }
    }
    fun cancelSave(name: String?) { name?.let { runCatching { repository.discardPending(it) } } }
    fun inspect(uri: Uri,password:CharArray?=null) = run("No se pudo validar el backup: puede estar dañado, ser incompatible o superar los límites admitidos. No se han cambiado los datos actuales.",onCompletion={password?.fill('\u0000')}) {
        repository.discard(candidate)
        candidate = null
        candidate = try { documents.read(repository, uri,password) } finally { password?.fill('\u0000') }
    }
    fun noticeSelectionFailed() { notice = "Android no pudo abrir el selector de archivos. Vuelve a intentarlo." }
    fun dismiss() { if (!busy) { repository.discard(candidate); candidate = null } }
    fun restore(onRestored: () -> Unit) {
        val data = candidate ?: return
        run("No se pudo restaurar el backup. Se conservan los datos anteriores; puedes volver a intentarlo.") {
            repository.restore(data)
            repository.discard(data)
            candidate = null
            notice = "Backup restaurado correctamente."
            try { onRestored() } catch (_: Exception) {
                notice = "Los datos se restauraron, pero no se pudo actualizar la pantalla. Vuelve a Proyectos."
            }
        }
    }
}
