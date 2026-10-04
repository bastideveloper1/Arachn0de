package com.r0ybt.arachn0de.ui.state

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.r0ybt.arachn0de.domain.model.Project
import com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot
import com.r0ybt.arachn0de.export.*
import kotlinx.coroutines.*

/** Screen-owned operation: scrolling a card out of composition cannot cancel copying. */
internal class NodeCopyActions(context: Context, private val scope: CoroutineScope, private val people: suspend () -> Map<String,List<com.r0ybt.arachn0de.domain.model.Person>> = { emptyMap() }) {
    private val appContext = context.applicationContext
    private val clipboard = ContextClipboard(appContext)
    var busy by mutableStateOf(false)
        private set
    fun copy(tree: NodeTreeSnapshot, id: String, descendants: Boolean) {
        if(descendants) copyText { check -> PendingChatRenderer.render(tree,id,people=people(),checkCancelled=check) }
        else copySnapshot { check -> NodeExportSnapshot.capture(tree,id,false,check) }
    }
    fun copyProject(project: Project, tree: NodeTreeSnapshot, descendants: Boolean) {
        if(descendants) copyText { check -> PendingChatRenderer.render(tree,null,project,people(),check) }
        else copySnapshot { check -> NodeExportSnapshot.captureProject(project,tree,false,check) }
    }
    private fun copySnapshot(capture: (() -> Unit) -> NodeExportSnapshot) {
        copyText { check -> NodeMarkdownRenderer.render(capture(check),check) }
    }
    private fun copyText(render: suspend (() -> Unit) -> String) {
        if (busy) return
        busy = true
        scope.launch(Dispatchers.Main.immediate) {
            try {
                val text = withContext(Dispatchers.Default) {
                    val job = currentCoroutineContext()
                    render { job.ensureActive() }
                }
                clipboard.copy(text)
                // Android 13+ supplies its own clipboard confirmation.
                if (Build.VERSION.SDK_INT < 33) message("Contexto copiado")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ContextTooLargeException) {
                message(checkNotNull(failure.message))
            } catch (_: OutOfMemoryError) {
                message("No hay memoria suficiente para copiar este contexto. Elige una capa más pequeña.")
            } catch (_: Exception) {
                message("No se pudo copiar el contexto. Puedes reintentar.")
            } finally { busy = false }
        }
    }
    private fun message(text: String) { Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show() }
}
