package com.r0ybt.arachn0de.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import com.r0ybt.arachn0de.document.DocumentReportDialog
import com.r0ybt.arachn0de.document.ReportFormat

internal data class ElementOptionsExport(val projectId:String?,val nodeId:String?,val container:Boolean,
    val canCopy:Boolean,val canCopyDescendants:Boolean,val copy:(Boolean)->Unit)

internal data class ElementOptionsExportState(val actions:List<ContextAction>,val message:String?,val busy:Boolean)

/** Menu routing only. Existing generators, pickers, clipboard and confirmations own execution. */
@Composable internal fun elementOptionsExports(spec:ElementOptionsExport,onClose:()->Unit,enabled:Boolean):ElementOptionsExportState {
    val image=if(spec.nodeId!=null)rememberCardImageCommands(spec.nodeId) else null
    val tree=rememberCardTreeImageCommands(spec.projectId,spec.nodeId,spec.container)
    val single=rememberElementExport(spec.projectId,spec.nodeId,false)
    val branch=rememberElementExport(spec.projectId,spec.nodeId,true)
    var document by remember {mutableStateOf<ReportFormat?>(null)}
    document?.let {format->DocumentReportDialog(requireNotNull(spec.projectId),spec.nodeId,initialFormat=format) {document=null}}
    val busy=image?.busy==true || tree.busy || single.busy || branch.busy
    val available=enabled && !busy
    fun scoped(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,solo:ContextAction?,descendants:ContextAction?):ContextAction {
        val choices=listOfNotNull(solo,descendants)
        return if(choices.size==1)choices.single().copy(label=label,icon=icon)
        else ContextAction(label,icon,{},enabled=available && choices.any {it.enabled},children=choices)
    }
    fun option(label:String,allowed:Boolean=true,action:()->Unit)=ContextAction(label,Icons.Default.Description,action,enabled=available && allowed)
    val actions=buildList {
        add(scoped("Copiar elemento",Icons.Default.ContentCopy,
            option("Solo elemento",spec.canCopy) {onClose();spec.copy(false)},
            if(spec.container && tree.hasDescendants)option("Con descendientes",spec.canCopyDescendants) {onClose();spec.copy(true)} else null))
        if(image!=null || tree.hasDescendants)add(scoped("Guardar imagen",Icons.Default.Image,
            image?.let {option("Solo elemento",action=it.save)},
            if(tree.hasDescendants)option("Con descendientes",action=tree.save) else null))
        image?.let {add(ContextAction("Compartir imagen",Icons.Default.Share,it.share,enabled=available))}
        if(spec.projectId!=null && spec.container) {
            add(ContextAction("PDF / Word",Icons.Default.Description,{},enabled=available,children=listOf(
                ContextAction("PDF",Icons.Default.Description,{document=ReportFormat.PDF},enabled=available),
                ContextAction("Word / DOCX",Icons.Default.Description,{document=ReportFormat.DOCX},enabled=available))))
        }
        add(scoped("Exportar Markdown",Icons.Default.Code,option("Solo elemento") {single.open(false)},
            if(spec.container)option("Con descendientes") {branch.open(false)} else null))
        add(scoped("Exportar JSON",Icons.Default.DataObject,option("Solo elemento") {single.open(true)},
            if(spec.container)option("Con descendientes") {branch.open(true)} else null))
    }
    return ElementOptionsExportState(actions,image?.message ?: tree.message ?: single.message ?: branch.message,busy)
}
