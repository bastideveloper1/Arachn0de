package com.r0ybt.arachn0de.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.diagrams.DiagramExportJournal
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.export.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

internal class ElementExportCommands(val busy:Boolean,val message:String?,val open:(Boolean)->Unit)

@Composable internal fun rememberElementExport(projectId:String?,nodeId:String?,descendants:Boolean):ElementExportCommands {
    val context=LocalContext.current
    val app=context.applicationContext as? Arachn0deApplication
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    var requestedJson by rememberSaveable {mutableStateOf(false)}
    var requestedDescendants by rememberSaveable {mutableStateOf(false)}
    val journal=remember(context,app) {app?.let {DiagramExportJournal(context,it.privateContext,report=true)}}
    LaunchedEffect(journal) {
        journal?.let {val pending=it.recover();if(pending.isNotEmpty())message="Hay una exportación parcial pendiente de limpieza. Revisa el destino antes de reintentar."}
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {tree->
        busy=false
        if(tree!=null && app!=null && journal!=null) {
            busy=true;message=null
            scope.launch {
                var ticket:DiagramExportJournal.Ticket?=null
                try {
                    withContext(Dispatchers.IO) {
                        // The durable SAF journal serializes its own operations; the final capture
                        // and streaming writer hold the file lock together through the last byte.
                        val hasFiles=AttachmentRepository.fileOperations.withLock {captureElement(app,projectId,nodeId,requestedDescendants,inspectFiles=false).bundled}
                        val extension=if(hasFiles)"zip" else if(requestedJson)"json" else "md"
                        val mime=when(extension) {"zip"->"application/zip";"json"->"application/json";else->"text/markdown"}
                        withContext(NonCancellable) {ticket=journal.beginReport(tree,mime,extension)}
                        val current=requireNotNull(ticket)
                        AttachmentRepository.fileOperations.withLock {
                            val content=captureElement(app,projectId,nodeId,requestedDescendants)
                            require(content.bundled==hasFiles) {"Los adjuntos cambiaron mientras elegías el destino. Reintenta la exportación."}
                            requireNotNull(context.contentResolver.openOutputStream(requireNotNull(current.uri),"wt")).use {output->
                                content.write(output,requestedJson,app.privateContext);output.flush()
                            }
                        }
                        journal.complete(current)
                    }
                    message=null
                } catch(cancelled:CancellationException) {
                    ticket?.let {withContext(NonCancellable) {runCatching {journal.rollback(it)}}}
                    throw cancelled
                } catch(failure:Exception) {
                    val cleanup=ticket?.let {runCatching {journal.rollback(it)}.exceptionOrNull()}
                    message=if(cleanup!=null)"No se pudo completar la exportación. Quedó un archivo parcial pendiente de limpieza; reintenta con acceso a la carpeta."
                        else failure.message ?: "No se pudo exportar. Los datos originales se conservan."
                } finally {busy=false}
            }
        }
    }
    return ElementExportCommands(busy,message) {json->
        if(!busy && app!=null) {
            requestedJson=json;requestedDescendants=descendants;busy=true
            try {picker.launch(null)} catch(_:Exception) {busy=false;message="No se pudo abrir el selector de carpeta."}
        }
    }
}
