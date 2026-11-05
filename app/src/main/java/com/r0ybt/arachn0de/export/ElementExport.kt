package com.r0ybt.arachn0de.export

import androidx.room.withTransaction
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.BuildConfig
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.domain.model.AttachmentReferences
import com.r0ybt.arachn0de.security.SecureFiles
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Data-only, committed subset. Original foreign IDs are deliberately retained; never feed this to restore. */
internal data class ElementExport(val data:BackupData,val order:List<Pair<String,Int>>,val projectId:String?,val nodeId:String?,val descendants:Boolean) {
    val bundled get()=data.attachmentFiles.isNotEmpty() || data.imageFiles.isNotEmpty() || data.projectPhotos.isNotEmpty() || data.persons.any {it.avatarFile!=null} || data.technologies.any {it.iconFile!=null}
    fun json():ByteArray {
        require(data.privatePreferences.isEmpty() && data.storeId==null && data.gameSession==null && data.metroPreferences==null && data.savedTemplates.isEmpty()) {"La exportación parcial no admite preferencias privadas ni datos globales."}
        return JSONObject()
        .put("kind","arachn0de-element-export").put("schemaVersion",1).put("partial",true)
        .put("root",JSONObject().put("projectId",projectId ?: JSONObject.NULL).put("nodeId",nodeId ?: JSONObject.NULL).put("descendants",descendants))
        .put("referencePolicy","IDs outside records are external references; not a complete application backup")
        .put("records",JSONObject(String(BackupJson.encodePartialRecords(data),Charsets.UTF_8)))
        .put("attachmentPaths",JSONObject().apply {data.attachmentFiles.forEach {put(it.id,attachmentPath(it))}})
        .put("imagePaths",JSONObject().apply {data.imageFiles.forEach {put(it.key,"files/"+it.key)}})
        .toString(2).toByteArray(Charsets.UTF_8)
    }

    fun markdown():String {
        val nodes=data.nodes.associateBy {it.id}
        val files=data.attachmentFiles.associateBy {it.id}
        val tags=data.tags.associate {it.id to it.name}
        val people=data.persons.associate {it.id to it.name}
        fun description(raw:String):String {
            val parser=com.r0ybt.arachn0de.domain.model.DescriptionParser
            if(parser.hasExtendedFormats(raw))return parser.parse(raw).joinToString("") {part->
                if(part.imageId!=null) {
                    val file=requireNotNull(files[part.imageId]);val label=literal(part.label ?: file.originalName)
                    (if(file.mimeType.startsWith("image/"))"!" else "")+"[$label](${attachmentPath(file)})"
                } else {
                    val f=part.formats
                    var value=part.text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                    if(com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Code in f)value="<code style=\"white-space:pre-wrap\">$value</code>"
                    if(com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Bold in f)value="<strong>$value</strong>"
                    if(com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Italic in f)value="<em>$value</em>"
                    if(com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Underline in f)value="<u>$value</u>"
                    if(com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Title in f)value="<strong style=\"font-size:1.2em\">$value</strong>"
                    if(com.r0ybt.arachn0de.domain.model.DescriptionParser.Format.Highlight in f)value="<mark>$value</mark>"
                    value
                }
            }
            val result=StringBuilder(raw)
            AttachmentReferences.matches(raw).toList().asReversed().forEach {match->
                val file=requireNotNull(files[match.groupValues[1]]) {"La descripción contiene un adjunto sin referencia confirmada."}
                val label=literal(AttachmentReferences.label(match) ?: file.originalName)
                val link=(if(file.mimeType.startsWith("image/")) "!" else "")+"[$label](${attachmentPath(file)})"
                result.replace(match.range.first,match.range.last+1,link)
            }
            return result.toString()
        }
        return buildString {
            order.forEach {(id,depth)->
                val n=nodes[id];val project=if(n==null)data.projects.single {it.id==id} else null
                val title=n?.title ?: requireNotNull(project).name
                append("#".repeat((depth+1).coerceAtMost(6))).append(' ').append(literal(title.replace('\n',' '))).append("\n\n")
                if(depth>=6)append("Nivel: ${depth+1}\n\n")
                val metadata=buildList {
                    if(n!=null) {
                        add("Tipo: ${n.purpose}")
                        if(n.purpose=="ACTION")add("Estado: ${if(n.isCompleted)"Completada" else if(n.awaitingVerification)"Por verificar" else "Pendiente"}")
                        if(n.priority!="NONE")add("Prioridad: ${n.priority}")
                        n.startAt?.let {add("Inicio: ${java.time.Instant.ofEpochMilli(it)}")}
                        n.dueAt?.let {add("Vencimiento: ${java.time.Instant.ofEpochMilli(it)}")}
                        n.amountMinor?.let {minor->
                            val scale=java.util.Currency.getInstance(n.currencyCode).defaultFractionDigits.coerceAtLeast(0)
                            add("Pago: ${java.math.BigDecimal.valueOf(minor,scale).toPlainString()} ${n.currencyCode}")
                        }
                        n.workState?.let {add("Estado de trabajo: $it")}
                        n.taskStateCount?.let {add("Estados de tareas: $it")}
                        data.recurrenceOccurrences.filter {it.nodeId==id}.forEach {occ->
                            data.recurrenceRules.firstOrNull {it.id==occ.ruleId}?.let {add("Recurrencia: ${it.frequency}, intervalo ${it.interval}, zona ${it.zoneId}")}
                        }
                    }
                    if(project!=null) {
                        data.conversionRoots.firstOrNull {it.projectId==id}?.let {root->
                            root.startAt?.let {add("Inicio: ${java.time.Instant.ofEpochMilli(it)}")}
                            root.dueAt?.let {add("Vencimiento: ${java.time.Instant.ofEpochMilli(it)}")}
                            if(root.priority!="NONE")add("Prioridad: ${root.priority}")
                        }
                    }
                    val ownTags=if(n!=null)data.nodeTags.filter {it.nodeId==id}.map {it.tagId}
                        else data.conversionTags.filter {link->data.conversionRoots.any {it.id==link.rootId && it.projectId==id}}.map {it.tagId}
                    ownTags.mapNotNull {tags[it]}.takeIf {it.isNotEmpty()}?.let {add("Etiquetas: ${it.joinToString()}")}
                    val responsible=if(n!=null)data.assignments.filter {it.nodeId==id}.map {it.personId}
                        else data.conversionPeople.filter {link->data.conversionRoots.any {it.id==link.rootId && it.projectId==id}}.map {it.personId}
                    responsible.mapNotNull {people[it]}.takeIf {it.isNotEmpty()}?.let {add("Responsables: ${it.joinToString()}")}
                    val techIds=if(n!=null)data.nodeTechnologies.filter {it.nodeId==id}.map {it.technologyId} else data.projectTechnologies.filter {it.projectId==id}.map {it.technologyId}
                    data.technologies.filter {it.id in techIds}.takeIf {it.isNotEmpty()}?.let {add("Tecnologías: ${it.joinToString {it.name}}")}
                }
                metadata.forEach {append("- ").append(literal(it)).append('\n')}
                if(metadata.isNotEmpty())append('\n')
                val raw=n?.description ?: requireNotNull(project).description
                if(raw.isNotBlank())append(description(raw)).append("\n\n")
                val ownFiles=if(n==null)data.projectAttachments.filter {it.projectId==id}.map {it.attachmentId} else data.nodeAttachments.filter {it.nodeId==id}.map {it.attachmentId}
                ownFiles.filter {it !in AttachmentReferences.ids(raw)}.forEach {fileId->
                    val file=files.getValue(fileId)
                    append(if(file.mimeType.startsWith("image/"))"!" else "").append('[').append(literal(file.originalName)).append("](").append(attachmentPath(file)).append(")\n\n")
                }
                data.projectPhotos.filter {if(n==null)it.projectId==id else it.nodeId==id}.forEach {
                    append("![Fotografía](files/project-photos/").append(it.file).append(")\n\n")
                }
            }
        }.also {require(it.toByteArray().size<=BackupLimits.PAYLOAD_BYTES) {"Demasiado texto; exporta una Capa más pequeña."}}
    }
    companion object {
        fun literal(value:String)=value.replace("\\","\\\\").replace("[","\\[").replace("]","\\]").replace("*","\\*").replace("_","\\_").replace("`","\\`").replace("<","&lt;").replace(">","&gt;")
        fun attachmentPath(file:AttachmentFileEntity):String {
            require(file.id.matches(Regex("[a-f0-9-]{36}"))) {"Identificador de adjunto inválido."}
            val extension=when(file.mimeType) {"image/png"->"png";"image/jpeg"->"jpg";"image/webp"->"webp";"image/gif"->"gif";"audio/aac"->"aac";else->"bin"}
            return "files/attachments/${file.id}.$extension"
        }
    }
}

/** Called under the shared file lock. No full backup, global preferences or unrelated profiles are read. */
internal suspend fun captureElement(app:Arachn0deApplication,projectId:String?,nodeId:String?,descendants:Boolean,inspectFiles:Boolean=true):ElementExport=app.database.withTransaction {
    val db=app.database
    require(projectId!=null || nodeId!=null)
    val all=if(projectId!=null)db.nodeDao().getProjectNodes(projectId) else listOf(requireNotNull(db.nodeDao().getById(requireNotNull(nodeId))))
    val byId=all.associateBy {it.id};val children=all.groupBy {it.parentId}
    val project=if(nodeId==null)requireNotNull(db.projectDao().getById(requireNotNull(projectId))) else null
    val order=mutableListOf<Pair<String,Int>>();val pending=ArrayDeque<Pair<String,Int>>()
    if(nodeId!=null) {require(nodeId in byId) {"El elemento ya no existe."};pending.addLast(nodeId to 0)}
    else {order+=requireNotNull(project).id to 0;if(descendants)children[null].orEmpty().sortedBy {it.position}.asReversed().forEach {pending.addLast(it.id to 1)}}
    while(pending.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val item=pending.removeLast();order+=item;require(order.size<=5000) {"Máximo 5000 elementos; exporta una Capa más pequeña."}
        if(descendants)children[item.first].orEmpty().sortedBy {it.position}.asReversed().forEach {pending.addLast(it.id to item.second+1)}
    }
    val ids=order.mapTo(hashSetOf()) {it.first};val nodes=order.mapNotNull {byId[it.first]}
    val projectIds=listOfNotNull(project?.id).toSet()
    val roots=db.conversionDao().roots().filter {it.projectId in projectIds || it.nodeId in ids};val rootIds=roots.mapTo(hashSetOf()) {it.id}
    val conversionTags=db.conversionDao().tags().filter {it.rootId in rootIds}
    val conversionPeople=db.conversionDao().people().filter {it.rootId in rootIds}
    val assignments=db.backupDao().assignments().filter {it.nodeId in ids}
    val occurrences=db.recurrenceDao().occurrences().filter {it.nodeId in ids}
    val rules=db.recurrenceDao().rules().filter {rule->occurrences.any {it.ruleId==rule.id} || (descendants && rule.projectId==project?.id) || (descendants && rule.parentId in ids)}
    val ruleIds=rules.mapTo(hashSetOf()) {it.id}
    val rulePeople=db.recurrenceDao().assignments().filter {it.ruleId in ruleIds}
    val ruleTags=db.tagDao().ruleTags().filter {it.ruleId in ruleIds}
    val nodeTags=db.tagDao().nodeTags().filter {it.nodeId in ids}
    val defaults=db.creationDefaultsDao().all().filter {it.nodeId in ids || (it.nodeId==null && it.projectId in projectIds)}
    val defaultIds=defaults.mapTo(hashSetOf()) {it.id}
    val defaultTags=db.creationDefaultsDao().tags().filter {it.defaultsId in defaultIds}
    val defaultPeople=db.creationDefaultsDao().people().filter {it.defaultsId in defaultIds}
    val layers=db.managementLayerDao().all().filter {it.nodeId in ids}
    val roles=db.backlogDao().roles().filter {it.nodeId in ids}
    val entries=db.backlogDao().entries().filter {it.nodeId in ids}
    val plans=db.iterationDao().plans().filter {it.sprintId in ids}
    val contextIds=(projectIds+ids+layers.map {it.contextId}+roles.map {it.contextId}+entries.map {it.contextId}+plans.map {it.contextId}).toSet()
    // Shared context definitions are explicit dependencies, never their unrelated tasks.
    val contexts=db.managementContextDao().all().filter {it.id in contextIds}
    val objectives=db.planningDao().objectives().filter {it.contextId in contextIds}
    val objectiveIds=objectives.mapTo(hashSetOf()) {it.id}
    val planIds=plans.mapTo(hashSetOf()) {it.id}
    val cycles=db.iterationDao().cycles().filter {it.planId in planIds}
    val cycleIds=cycles.mapTo(hashSetOf()) {it.id}
    val participants=db.participationDao().participants().filter {it.contextId in contextIds}
    val participantRoles=db.participationDao().roles().filter {it.contextId in contextIds}
    val objectiveResponsibilities=db.participationDao().objectives().filter {it.objectiveId in objectiveIds}
    val nodeResponsibilities=db.participationDao().nodes().filter {it.nodeId in ids}
    val personIds=(assignments.map {it.personId}+conversionPeople.map {it.personId}+rulePeople.map {it.personId}+defaultPeople.map {it.personId}+participants.map {it.personId}+participantRoles.map {it.personId}+objectiveResponsibilities.map {it.personId}+nodeResponsibilities.map {it.personId}).toSet()
    val tagIds=(nodeTags.map {it.tagId}+conversionTags.map {it.tagId}+ruleTags.map {it.tagId}+defaultTags.map {it.tagId}).toSet()
    val people=db.backupDao().persons().filter {it.id in personIds}
    val nodeAttachments=db.attachmentDao().nodeAttachments().filter {it.nodeId in ids}
    val projectAttachments=db.attachmentDao().projectAttachments().filter {it.projectId in projectIds}
    val attachmentIds=(nodeAttachments.map {it.attachmentId}+projectAttachments.map {it.attachmentId}).toSet()
    val attachments=db.attachmentDao().files().filter {it.id in attachmentIds}
    require(attachments.size==attachmentIds.size)
    val nodeTech=db.technologyDao().nodes().filter {it.nodeId in ids}
    val projectTech=db.technologyDao().projects().filter {it.projectId in projectIds}
    val techIds=(nodeTech.map {it.technologyId}+projectTech.map {it.technologyId}).toSet()
    val tech=db.technologyDao().catalog().filter {it.id in techIds}
    val photos=db.projectPhotoDao().confirmed().filter {it.projectId in projectIds || it.nodeId in ids}
    val images=mutableListOf<BackupImageFile>();val contents=linkedMapOf<String,java.io.File>()
    fun image(directory:String,name:String) {
        val journal=when(directory) {"avatars"->"backup-restore-journal";"project-photos"->"project-photo-journal";else->"technology-icon-journal"}
        val file=BackupAvatarFiles(app.privateContext,directory,journal).file(name)
        val entry=inspectImage(directory,name,file);images+=entry;contents[entry.key]=file
    }
    if(inspectFiles) {
    people.mapNotNull {it.avatarFile}.toSet().forEach {image("avatars",it)}
    tech.mapNotNull {it.iconFile}.toSet().forEach {image("technology-icons",it)}
    photos.map {it.file}.toSet().forEach {image("project-photos",it)}
    }
    require(attachments.sumOf {it.byteSize}+images.sumOf {it.byteSize}<=256L*1024*1024) {"Máximo 256 MiB por exportación; exporta una Capa más pequeña."}
    val requiredIds=(nodes.map {it.description}+listOfNotNull(project?.description)).flatMap {AttachmentReferences.ids(it)}.toSet()
    require(attachmentIds.containsAll(requiredIds)) {"Hay adjuntos sin referencia confirmada; no se exportó un documento incompleto."}
    val data=BackupData(BuildConfig.VERSION_NAME,System.currentTimeMillis(),listOfNotNull(project),nodes,people,assignments,emptyMap(),
        recurrenceRules=rules,recurrenceOccurrences=occurrences,recurrenceAssignments=rulePeople,tags=db.tagDao().tags().filter {it.id in tagIds},
        nodeTags=nodeTags,recurrenceTags=ruleTags,nodeEvents=db.nodeEventDao().all().filter {it.nodeId in ids},
        creationDefaults=defaults,defaultsTags=defaultTags,defaultsPeople=defaultPeople,
        attachmentFiles=attachments,nodeAttachments=nodeAttachments,projectAttachments=projectAttachments,
        attachmentContents=attachments.associate {it.storageName to app.attachmentRepository.localFile(it)},
        technologies=tech,nodeTechnologies=nodeTech,projectTechnologies=projectTech,projectPhotos=photos,imageFiles=images,imageContents=contents,
        conversionRoots=roots,conversionPeople=conversionPeople,conversionTags=conversionTags,
        managementContexts=contexts,managementLayers=layers,managementRoles=roles,backlogEntries=entries,
        projectCharters=db.planningDao().charters().filter {it.contextId in contextIds},managementObjectives=objectives,
        acceptanceCriteria=db.planningDao().criteria().filter {it.objectiveId in objectiveIds},
        objectiveEpics=db.backlogDao().objectiveEpics().filter {it.epicId in ids},
        epicStories=db.backlogDao().epicStories().filter {it.epicId in ids || it.storyId in ids},
        storyTasks=db.backlogDao().storyTasks().filter {it.storyId in ids || it.taskId in ids},
        sprintStories=db.cascadeRelationDao().stories().filter {it.sprintId in ids || it.storyId in ids},
        objectiveWork=db.cascadeRelationDao().objectives().filter {it.nodeId in ids},
        sprintLinks=db.sprintLinkDao().all().filter {it.taskId in ids || it.sprintId in ids},
        ganttAnnotations=db.ganttAnnotationDao().all().filter {it.contextId in contextIds},
        iterationPlans=plans,iterationCycles=cycles,
        iterationCommitments=db.iterationDao().commitments().filter {it.cycleId in cycleIds && it.taskId in ids},
        iterationClosures=db.iterationDao().closures().filter {it.cycleId in cycleIds && it.taskId in ids},
        managementParticipants=participants,participantRoles=participantRoles,
        objectiveResponsibilities=objectiveResponsibilities,nodeResponsibilities=nodeResponsibilities,
        metroJourneys=db.metroDao().journeys().filter {it.nodeId in ids},
        conversionEvents=db.conversionDao().events().filter {it.rootId in rootIds},conversionWorkStates=db.conversionDao().workStates().filter {it.nodeId in ids})
    ElementExport(data,order,projectId,nodeId,descendants)
}

/** One image at a time into encrypted ephemeral staging; audio is verified and streamed.
 * The caller holds the common source-file lock through capture and final delivery. */
internal suspend fun ElementExport.write(output:OutputStream,json:Boolean,context:android.content.Context,syncDirectory:(java.io.File)->Unit=::syncBackupDirectory) {
    if(!bundled) {output.write(if(json)json() else markdown().toByteArray(Charsets.UTF_8));return}
    val staged=linkedMapOf<String,String>();var total=0L
    PublicExportStaging(context,sync=syncDirectory).use {stage->
        fun retain(key:String,bytes:ByteArray) {
            try {total+=bytes.size;require(total<=256L*1024*1024) {"Máximo 256 MiB por exportación pública."};staged[key]=stage.put(bytes)}
            finally {bytes.fill(0)}
        }
        val rows=data.attachmentFiles.map {row->
            currentCoroutineContext().ensureActive();val file=data.attachmentContents.getValue(row.storageName)
            require(SecureFiles.size(file)==row.byteSize) {"El adjunto cambió de tamaño."}
            if(!row.mimeType.startsWith("image/")) {
                SecureFiles.input(file).use {input->copyBackupAttachment(row,input,object:OutputStream() {override fun write(b:Int) {};override fun write(b:ByteArray,o:Int,n:Int) {}});require(input.read()==-1)}
                total+=row.byteSize;require(total<=256L*1024*1024);row
            } else {
                val raw=com.r0ybt.arachn0de.audio.WipingByteArrayOutputStream()
                val prepared=try {
                    SecureFiles.input(file).use {input->copyBackupAttachment(row,input,raw);require(input.read()==-1)}
                    val bytes=raw.toByteArray();try {com.r0ybt.arachn0de.photos.PublicImages.file(bytes)} finally {bytes.fill(0)}
                } finally {raw.erase()}
                val public=row.copy(byteSize=prepared.bytes.size.toLong(),sha256=imageHash(prepared.bytes),width=prepared.width,height=prepared.height,mimeType=prepared.mime)
                retain("attachment:${row.id}",prepared.bytes);public
            }
        }
        val imageRows=data.imageFiles.map {row->
            currentCoroutineContext().ensureActive();val file=data.imageContents.getValue(row.key);verifyImage(row,file)
            val raw=SecureFiles.input(file).use {it.readBytes()}
            val bytes=try {com.r0ybt.arachn0de.photos.PublicImages.prepare(raw)} finally {raw.fill(0)}
            val public=row.copy(byteSize=bytes.size.toLong(),sha256=imageHash(bytes));retain("files/${row.key}",bytes);public
        }
        // Inline historical images are bounded by the existing 8 MiB backup metadata budget.
        require((data.avatars.values+data.technologyIcons.values+data.projectPhotoImages.values).sumOf {it.size.toLong()}<=8L*1024*1024) {"Demasiadas imágenes inline; usa archivos por streaming."}
        val inlineBuffers=mutableListOf<ByteArray>()
        fun inline(source:Map<String,ByteArray>,directory:String)=source.mapValues {(name,raw)->
            val bytes=com.r0ybt.arachn0de.photos.PublicImages.prepare(raw);inlineBuffers+=bytes
            total+=bytes.size;require(total<=256L*1024*1024)
            staged["files/$directory/$name"]=stage.put(bytes);bytes
        }
        try {
        val publicData=data.copy(attachmentFiles=rows,imageFiles=imageRows,avatars=inline(data.avatars,"avatars"),
            technologyIcons=inline(data.technologyIcons,"technology-icons"),projectPhotoImages=inline(data.projectPhotoImages,"project-photos"))
            val copy=copy(data=publicData);val bytes=if(json)copy.json() else copy.markdown().toByteArray(Charsets.UTF_8)
            val zip=ZipOutputStream(object:java.io.FilterOutputStream(output) {override fun write(b:ByteArray,o:Int,n:Int) {out.write(b,o,n)};override fun close() {flush()}})
            try {
                zip.putNextEntry(ZipEntry(if(json)"element.json" else "contenido.md"));zip.write(bytes);zip.closeEntry()
                rows.forEach {row->
                    currentCoroutineContext().ensureActive();zip.putNextEntry(ZipEntry(ElementExport.attachmentPath(row)))
                    val input=staged["attachment:${row.id}"]?.let(stage::input) ?: SecureFiles.input(data.attachmentContents.getValue(row.storageName))
                    input.use {copyBackupAttachment(row,it,zip);require(it.read()==-1)};zip.closeEntry()
                }
                val imageIndex=imageRows.associateBy {"files/${it.key}"}
                staged.filterKeys {it.startsWith("files/")}.forEach {(name,entry)->
                    currentCoroutineContext().ensureActive();zip.putNextEntry(ZipEntry(name))
                    stage.input(entry).use {input->imageIndex[name]?.let {copyImage(it,input,zip)} ?: input.copyTo(zip);require(input.read()==-1)}
                    zip.closeEntry()
                };zip.finish()
            } finally {zip.close()}
        } finally {inlineBuffers.forEach {it.fill(0)}}
    }
}
