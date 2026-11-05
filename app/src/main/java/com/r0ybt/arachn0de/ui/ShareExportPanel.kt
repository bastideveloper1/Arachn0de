package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.document.*

/** Only supported actions are offered. Rendering and clipboard implementations stay with their owners. */
@Composable internal fun ShareExportPanel(
    title:String,projectId:String?,nodeId:String?,container:Boolean,
    canCopy:Boolean,canCopyDescendants:Boolean,onCopy:(Boolean)->Unit,onClose:()->Unit
) {
    var documentFormat by rememberSaveable {mutableStateOf<String?>(null)}
    var descendants by rememberSaveable {mutableStateOf(container)}
    val images=if(nodeId!=null) rememberCardImageCommands(nodeId) else null
    val treeImages=rememberCardTreeImageCommands(projectId,nodeId,container)
    val imageBusy=images?.busy==true || treeImages.busy
    fun close() {if(treeImages.busy)treeImages.cancel();onClose()}
    val openFormat=rememberElementExport(projectId,nodeId,descendants)
    documentFormat?.let {format->
        DocumentReportDialog(requireNotNull(projectId),nodeId,initialFormat=ReportFormat.valueOf(format)) {documentFormat=null}
    }
    if(documentFormat==null) ModularPanelDialog(
        onDismissRequest={if(treeImages.busy || (!imageBusy && !openFormat.busy))close()},compact=true,plainBody=true,foldLabel="Compartir y exportar",
        title={Text("Compartir y exportar")},
        text={
            OperationProgress(imageBusy || openFormat.busy)
            images?.message?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
            Text(title,style=MaterialTheme.typography.labelMedium)
            Text("Los archivos exportados no están cifrados. Compártelos solo con quien corresponda.",style=MaterialTheme.typography.bodySmall)
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Column {
                    Text("Copiar",style=MaterialTheme.typography.titleSmall)
                    ModularFieldMenu(buildList {
                        add(ModularPanelOption("Copiar elemento",Icons.Default.ContentCopy,"share-export-copy",canCopy) {onClose();onCopy(false)})
                        if(container)add(ModularPanelOption("Copiar con descendientes",Icons.Default.CopyAll,"share-export-copy-tree",canCopyDescendants) {onClose();onCopy(true)})
                    },columns=2,compact=true)
                }
                CardImageMenu(images,treeImages,enabled=!imageBusy && !openFormat.busy)
                Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    if(container) Column {
                        Text("Alcance de Markdown y JSON",style=MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            FilterChip(!descendants,{descendants=false},label={Text("Solo elemento")},modifier=Modifier.heightIn(min=48.dp))
                            FilterChip(descendants,{descendants=true},label={Text("Con descendientes")},modifier=Modifier.heightIn(min=48.dp))
                        }
                    }
                    Column {
                        Text("Documento",style=MaterialTheme.typography.titleSmall)
                        ModularFieldMenu(buildList {
                            if(projectId!=null && container) {
                                add(ModularPanelOption("Exportar PDF",Icons.Default.Description,"share-export-pdf") {documentFormat=ReportFormat.PDF.name})
                                add(ModularPanelOption("Exportar Word/DOCX",Icons.Default.Description,"share-export-docx") {documentFormat=ReportFormat.DOCX.name})
                            }
                            add(ModularPanelOption("Exportar Markdown",Icons.Default.Code,"share-export-markdown",!openFormat.busy) {openFormat.open(false)})
                        },columns=2,compact=true)
                    }
                    Column {
                        Text("Datos",style=MaterialTheme.typography.titleSmall)
                        ModularFieldMenu(listOf(ModularPanelOption("Exportar JSON",Icons.Default.DataObject,"share-export-json",!openFormat.busy) {openFormat.open(true)}),columns=2,compact=true)
                    }
                }
            }
            treeImages.message?.let {Text(it,Modifier.testTag("share-export-images-result"),style=MaterialTheme.typography.bodySmall)}
            openFormat.message?.let {Text(it,Modifier.testTag("share-export-result"),style=MaterialTheme.typography.bodySmall)}
        },confirmButton={TextButton(onClick=::close,enabled=treeImages.busy || (!imageBusy && !openFormat.busy)) {Text("Cerrar")}},
        foldEnabled=!imageBusy && !openFormat.busy)
}

/** The same image menu for a node or a Núcleo; leaves never offer a tree export. */
@Composable internal fun CardImageMenu(images:CardImageCommands?,tree:CardTreeImageCommands,enabled:Boolean) {
    if(images!=null || tree.hasDescendants) Column {
        Text("Imagen",style=MaterialTheme.typography.titleSmall)
        ModularFieldMenu(buildList {
            if(images!=null) {
                add(ModularPanelOption("Compartir como imagen",Icons.Default.Share,"share-export-image",enabled,images.share))
                add(ModularPanelOption("Guardar imagen",Icons.Default.Image,"share-export-save-image",enabled,images.save))
            }
            if(tree.hasDescendants)add(ModularPanelOption("Guardar con descendientes",Icons.Default.Collections,"share-export-save-image-tree",enabled,tree.save))
        },columns=2,compact=true)
    }
}
