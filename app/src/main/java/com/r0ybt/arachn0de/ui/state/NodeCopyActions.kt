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
import kotlinx.coroutines.flow.first

/** Screen-owned operation: scrolling a card out of composition cannot cancel copying. */
internal class NodeCopyActions(context: Context, private val scope: CoroutineScope, private val people: suspend () -> Map<String,List<com.r0ybt.arachn0de.domain.model.Person>> = { emptyMap() }) {
    private val appContext = context.applicationContext
    private val clipboard = ContextClipboard(appContext)
    var busy by mutableStateOf(false)
        private set
    var pendingCopy by mutableStateOf<((Boolean) -> Unit)?>(null)
        private set
    fun cancelChoice() { pendingCopy = null }
    fun confirmChoice(onlyPending: Boolean) {
        val request = pendingCopy ?: return
        pendingCopy = null
        request(onlyPending)
    }
    private fun choose(request: (Boolean) -> Unit) { if(!busy && pendingCopy == null) pendingCopy = request }
    fun copy(tree: NodeTreeSnapshot, id: String, descendants: Boolean) {
        if(descendants) choose {onlyPending->copyBranch(tree,id,null,onlyPending)}
        else copyBranch(tree,id,null,false,descendants=false)
    }
    fun copyProject(project: Project, tree: NodeTreeSnapshot, descendants: Boolean) {
        if(descendants) choose {onlyPending->copyBranch(tree,null,project,onlyPending)}
        else copySnapshot {check->NodeExportSnapshot.captureProject(project,tree,false,check)}
    }
    private fun copyBranch(tree:NodeTreeSnapshot,id:String?,knownProject:Project?,onlyPending:Boolean,descendants:Boolean=true) {
        copyText {check->
            val nodeProject=id?.let {tree.nodesById[it]?.projectId}
            if(knownProject==null && nodeProject==null) {
                val names=imageNames()
                return@copyText TaskPlainTextRenderer.render(tree,listOf(requireNotNull(id)),emptyMap(),check) {descriptionClipboardText(it,names)}
            }
            val project=knownProject ?: requireNotNull((appContext as com.r0ybt.arachn0de.Arachn0deApplication)
                .projectRepository.getProject(checkNotNull(nodeProject))) {"El Núcleo ya no existe."}
            val ids=if(descendants) TaskPlainTextRenderer.branch(tree,id,project.id,onlyPending,check) else listOf(requireNotNull(id))
            val names=imageNames()
            if(ids.isEmpty()) "Sin pendientes" else TaskPlainTextRenderer.render(tree,ids,mapOf(project.id to project),check) {descriptionClipboardText(it,names)}
        }
    }
    private fun copySnapshot(capture: (() -> Unit) -> NodeExportSnapshot) {
        copyText { check -> val names = imageNames(); NodeMarkdownRenderer.render(capture(check),check,descriptionText={ descriptionClipboardText(it, names) }) }
    }
    private suspend fun imageNames() = (appContext as com.r0ybt.arachn0de.Arachn0deApplication)
        .attachmentRepository.observeFiles().first().associate { it.id to it.originalName }
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
