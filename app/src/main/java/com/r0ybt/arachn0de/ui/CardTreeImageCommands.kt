package com.r0ybt.arachn0de.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.diagrams.DiagramExportJournal
import com.r0ybt.arachn0de.report.*
import com.r0ybt.arachn0de.security.SecureFiles
import com.r0ybt.arachn0de.security.VaultSession
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn

internal data class CardTreeImageCommands(val hasDescendants:Boolean,val busy:Boolean,val message:String?,val save:()->Unit,val cancel:()->Unit)

@Composable internal fun rememberCardTreeImageCommands(projectId:String?,nodeId:String?,container:Boolean):CardTreeImageCommands {
    val context=LocalContext.current;val app=context.applicationContext as? Arachn0deApplication
    val session=app?.security?.current?.value
    val scope=rememberCoroutineScope()
    var busy by remember(projectId,nodeId) {mutableStateOf(false)}
    var message by remember(projectId,nodeId) {mutableStateOf<String?>(null)}
    var job by remember {mutableStateOf<Job?>(null)}
    var requested by remember {mutableStateOf<VaultSession?>(null)}
    val hasDescendants=remember(session,projectId,nodeId,container) {
        if(session==null || !container)null else session.database.nodeDao().observeProjectNodes(projectId).map {rows->
            if(nodeId==null)rows.isNotEmpty() else rows.any {it.parentId==nodeId}
        }.flowOn(Dispatchers.Default)
    }?.collectAsState(initial=false)?.value ?: false
    val journal=remember(session) {session?.let {DiagramExportJournal(context,it.context,cards=true)}}
    val exporter=remember(session,journal,projectId,nodeId) {if(session!=null && journal!=null)CardTreeImages(context,session.context,journal) else null}
    val colors=CardShareColors(Arachn0deColors.Background.toArgb(),Arachn0deColors.TextPrimary.toArgb(),Arachn0deColors.PathHighlight.toArgb())
    LaunchedEffect(journal) {
        journal?.let {if(it.recover().isNotEmpty())message="Hay un guardado pendiente de limpieza. Revisa el acceso a la carpeta de destino antes de reintentar."}
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {tree->
        val owner=requested;requested=null;busy=false
        if(tree!=null && owner!=null && app!=null && journal!=null && exporter!=null) {
            busy=true;message=null
            job=scope.launch {
                try {
                    fun authorized() {check(app.security.current.value===owner);owner.access.checkOpen();check(SecureFiles.activeProfile==owner.access.id)}
                    authorized()
                    val items=withContext(Dispatchers.IO) {CardImageTree.capture(owner,projectId,nodeId)}
                    exporter.save(tree,items,colors,::authorized) {item->
                        authorized();if(item.project)CardShareSource.projectSnapshot(owner,item.id) else CardShareSource.snapshot(owner,item.id,onlyPending=true)
                    }
                    message="Exportación completada: ${exporter.saved} PNG guardados."
                } catch(cancelled:CancellationException) {
                    message="Guardado cancelado. ${exporter.saved} PNG completos guardados."
                    throw cancelled
                } catch(_:OutOfMemoryError) {
                    message="No hay memoria suficiente. ${exporter.saved} PNG completos guardados; el lote no terminó."
                } catch(failure:Exception) {
                    message="No se pudo guardar «${exporter.failedTitle ?: "el elemento"}». ${exporter.saved} PNG completos guardados; el lote no terminó. ${failure.message.orEmpty()}"+
                        if(exporter.cleanupPending)" Quedó un archivo parcial pendiente de limpieza; revisa el acceso a la carpeta." else ""
                } finally {busy=false;job=null}
            }
        }
    }
    return CardTreeImageCommands(container && hasDescendants,busy,message,{
        if(!busy && container && hasDescendants && session!=null) {
            requested=session;busy=true;message=null
            try {picker.launch(null)} catch(_:Exception) {requested=null;busy=false;message="No se pudo abrir el selector de carpeta."}
        }
    },{job?.cancel();requested=null})
}
