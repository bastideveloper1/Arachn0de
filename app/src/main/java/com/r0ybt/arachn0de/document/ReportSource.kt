package com.r0ybt.arachn0de.document

import androidx.room.withTransaction
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.toNode
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.security.SecureFiles
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.text.DateFormat
import java.util.Date

/** One committed metadata snapshot, with file reads protected against restore/delete. No writes. */
internal suspend fun captureReport(app:Arachn0deApplication,projectId:String,layerId:String?):ReportContent=withContext(Dispatchers.IO) {
    val job=currentCoroutineContext();val images=linkedMapOf<String,ReportImage>()
    try {AttachmentRepository.fileOperations.withLock {app.database.withTransaction {
        val db=app.database
        val project=requireNotNull(db.projectDao().getById(projectId)) {"El Núcleo ya no existe."}
        val rows=db.nodeDao().getProjectNodes(projectId)
        val parents=rows.mapNotNull {it.parentId}.toSet()
        val tree=NodeTreeSnapshot(rows.map {it.toNode(it.id in parents)})
        val root=layerId?.let {requireNotNull(tree.nodesById[it]) {"La Capa ya no existe."}.also {n->require(n.isStructural)}}
        val ordered=mutableListOf<Pair<Node?,Int>>(root to 0)
        val pending=ArrayDeque<Pair<Node,Int>>()
        tree.childrenOf(root?.id).asReversed().forEach {pending.addLast(it to 1)}
        while(pending.isNotEmpty()) {job.ensureActive();val (node,depth)=pending.removeLast();ordered+=node to depth;require(ordered.size<=5000) {"Máximo 5000 elementos por informe; exporta una Capa más pequeña."};tree.childrenOf(node.id).asReversed().forEach {pending.addLast(it to depth+1)}}
        val numbers=mutableListOf<Int>();var totalText=0L;var totalBytes=0L
        val sections=ordered.map {(node,depth)->
            job.ensureActive()
            val title=node?.title ?: project.name;val description=node?.description ?: project.description
            totalText+=title.length+description.length;require(totalText<=1_000_000) {"El informe supera el límite de texto. Exporta una Capa más pequeña."}
            val files=if(node==null)app.attachmentRepository.filesForProject(projectId) else app.attachmentRepository.filesForNode(node.id)
            val imageRows=files.filter {it.mimeType.startsWith("image/")};val audioIds=files.filter {it.mimeType.startsWith("audio/")}.mapTo(hashSetOf()) {it.id}
            imageRows.forEach {row->if(row.id !in images) {
                require(images.size<64) {"Máximo 64 imágenes por informe."};require(row.byteSize in 1..20L*1024*1024)
                val raw=SecureFiles.input(app.attachmentRepository.localFile(row)).use {input->
                    val out=java.io.ByteArrayOutputStream();val block=ByteArray(8192)
                    while(true) {job.ensureActive();val n=input.read(block);if(n<0)break;require(out.size().toLong()+n<=row.byteSize);out.write(block,0,n)};out.toByteArray()
                }
                try {
                    require(raw.size.toLong()==row.byteSize&&java.security.MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") {"%02x".format(it)}==row.sha256) {"Adjunto incompleto o modificado."}
                    val image=reportImage(raw)
                    totalBytes+=image.bytes.size;require(totalBytes<=32L*1024*1024) {"Las imágenes superan 32 MiB. Exporta una Capa más pequeña."};images[row.id]=image
                } finally {raw.fill(0)}
            }}
            val parts=reportParts(description,imageRows.mapTo(hashSetOf()) {it.id},audioIds).toMutableList()
            val referenced=parts.filterIsInstance<ReportPart.Image>().map {it.id}.toSet()
            imageRows.filter {it.id !in referenced}.forEach {parts+=ReportPart.Image(it.id,it.originalName)}
            val metadata=buildList {
                if(node==null) {
                    val tech=db.technologyDao().projects().filter {it.projectId==projectId}.sortedBy {it.position}.mapNotNull {db.technologyDao().get(it.technologyId)?.name}
                    if(tech.isNotEmpty())add("Tecnologías: ${tech.joinToString()}")
                }
                if(node!=null) {
                    if(node.purpose==NodePurpose.ACTION)add("Estado: ${node.workState?.label ?: node.taskStatus.label}")
                    if(node.priority!=Priority.NONE)add("Prioridad: ${when(node.priority) {Priority.HIGH->"Alta";Priority.MEDIUM->"Media";Priority.LOW->"Baja";else->"Sin prioridad"}}")
                    fun date(label:String,value:Long?) {value?.let {add("$label: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(it))}")}}
                    date("Inicio",node.startAt);date("Vencimiento",node.dueAt)
                    node.obligation?.let {add("Pago: ${Money.format(java.math.BigInteger.valueOf(it.amountMinor),it.currencyCode,java.util.Locale.getDefault())}")}
                    val people=db.personDao().assignmentsForNodes(listOf(node.id)).mapNotNull {db.personDao().get(it.personId)?.name}
                    if(people.isNotEmpty())add("Responsables: ${people.joinToString()}")
                    val tags=db.tagDao().tagsForNodes(listOf(node.id)).mapNotNull {db.tagDao().get(it.tagId)?.name}
                    if(tags.isNotEmpty())add("Etiquetas: ${tags.joinToString()}")
                    val tech=db.technologyDao().forNodes(listOf(node.id)).mapNotNull {db.technologyDao().get(it.technologyId)?.name}
                    if(tech.isNotEmpty())add("Tecnologías: ${tech.joinToString()}")
                }
            }
            val number=if(depth==0)"" else {while(numbers.size>depth)numbers.removeAt(numbers.lastIndex);while(numbers.size<depth)numbers+=0;numbers[depth-1]++;numbers.joinToString(".")}
            ReportSection(title,depth,number,metadata,parts)
        }
        ReportContent(sections,images)
    }}} catch(t:Throwable) {images.values.forEach {it.bytes.fill(0)};throw t}
}
