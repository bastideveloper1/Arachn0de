package com.r0ybt.arachn0de.templates

import android.content.Context
import android.util.Base64
import androidx.room.withTransaction
import com.r0ybt.arachn0de.backup.validateProjectPhoto
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.data.repository.ProjectPhotoRepository
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.security.SecureFiles
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.io.File

/** Operation journal only; restart can release imports without expiring an editor lease. */
internal object TemplateInsertionRecovery {
    private val active=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private fun directory(context:Context)=File(context.filesDir,"template-insertions")
    suspend fun begin(context:Context,id:String,sync:(File)->Unit=::syncTemplateDirectory)=AttachmentRepository.fileOperations.withLock {
        val dir=directory(context);check(dir.isDirectory || dir.mkdirs());active.add(id)
        com.r0ybt.arachn0de.backup.durableWrite(File(dir,"$id.pending"),id.toByteArray())
        sync(dir);sync(context.filesDir)
    }
    suspend fun finish(context:Context,id:String,attachments:AttachmentRepository,sync:(File)->Unit=::syncTemplateDirectory) {
        try {
            attachments.discardDraft("template:$id")
            AttachmentRepository.fileOperations.withLock {
                val dir=directory(context);val file=File(dir,"$id.pending")
                check(!file.exists() || file.delete());if(dir.isDirectory)sync(dir)
            }
        } finally {active.remove(id)}
    }
    suspend fun recover(context:Context,attachments:AttachmentRepository,sync:(File)->Unit=::syncTemplateDirectory) {
        val pending=AttachmentRepository.fileOperations.withLock {
            val dir=directory(context)
            dir.listFiles().orEmpty().filter {it.isFile && it.canonicalFile.parentFile==dir.canonicalFile && it.name.matches(Regex("[a-f0-9-]{36}\\.pending"))}
                .map {it.name.removeSuffix(".pending").also {id->require(UUID.fromString(id).toString()==id)}}.filterNot {it in active}
        }
        pending.forEach {finish(context,it,attachments,sync)}
    }
}
private fun syncTemplateDirectory(file:File)=com.r0ybt.arachn0de.backup.syncBackupDirectory(file)

/** Version 2 of saved_templates: portable, encrypted inline resources, never device paths. */
internal object StructuralTemplateCodec {
    const val MAX_BYTES=8*1024*1024
    fun isStructural(raw:String):Boolean {
        require(raw.toByteArray().size<=MAX_BYTES)
        android.util.JsonReader(raw.reader()).use {reader->
            reader.beginObject()
            while(reader.hasNext()) {if(reader.nextName()=="version")return reader.nextInt()==2 else reader.skipValue()}
        }
        return false
    }
    fun read(raw:String):JSONObject {
        require(raw.toByteArray().size<=MAX_BYTES) {"La plantilla supera 8 MiB."}
        android.util.JsonReader(raw.reader()).use {reader->
            fun bounded(depth:Int) {
                require(depth<=12)
                when(reader.peek()) {
                    android.util.JsonToken.BEGIN_OBJECT->{reader.beginObject();val keys=hashSetOf<String>();while(reader.hasNext()) {require(keys.size<4096 && keys.add(reader.nextName()));bounded(depth+1)};reader.endObject()}
                    android.util.JsonToken.BEGIN_ARRAY->{reader.beginArray();var count=0;while(reader.hasNext()) {require(++count<=4096);bounded(depth+1)};reader.endArray()}
                    else->reader.skipValue()
                }
            }
            bounded(0);require(reader.peek()==android.util.JsonToken.END_DOCUMENT)
        }
        val o=JSONObject(raw)
        require(o.getInt("version")==2)
        val nodes=o.getJSONArray("elements");require(nodes.length() in 1..4096)
        val resources=o.getJSONObject("resources")
        require(resources.length()<=4096)
        resources.keys().forEach {id->
            require(UUID.fromString(id).toString()==id)
            val f=resources.getJSONObject(id);val bytes=Base64.decode(f.getString("bytes"),Base64.NO_WRAP)
            require(bytes.isNotEmpty() && bytes.size<=MAX_BYTES)
            require(hash(bytes)==f.getString("sha256")) {"Recurso de plantilla dañado."}
            require(f.getString("name").length in 1..240 && !f.getString("name").contains('/'))
            val mime=f.getString("mime")
            if(mime=="audio/aac") com.r0ybt.arachn0de.audio.AacFrames.duration(bytes.inputStream())
            else {
                val bounds=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
                android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                require(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outWidth.toLong()*bounds.outHeight<=100_000_000)
                require(mime in setOf("image/png","image/jpeg","image/gif"))
            }
        }
        val depths=IntArray(nodes.length())
        for(i in 0 until nodes.length()) {
            val n=nodes.getJSONObject(i);val kind=n.getString("kind")
            require(kind in setOf("CORE","LAYER","ACTION","NOTE"))
            require(kind!="CORE" || i==0)
            val parent=n.getInt("parent");require(if(i==0) parent== -1 else parent in 0 until i)
            depths[i]=if(i==0)0 else depths[parent]+1;require(depths[i]<128)
            if(i>0) require(nodes.getJSONObject(parent).getString("kind") in setOf("CORE","LAYER"))
            require(n.getString("title").isNotBlank() && TitleLimits.count(n.getString("title"))<=if(kind=="CORE")TitleLimits.PROJECT else TitleLimits.NODE)
            require(n.getString("description").length<=262144)
            require(AttachmentReferences.ids(n.getString("description")).all {resources.has(it)})
            Priority.valueOf(n.getString("priority"));require(n.getInt("effort") in 0..5)
            require(n.getInt("states") in listOf(0,2,3))
            require(!n.getBoolean("pinned") || kind in setOf("CORE","LAYER"))
            if(n.has("amount")) {require(kind=="ACTION");Obligation(n.getLong("amount"),n.getString("currency"))}
            if(n.has("defaults")) {
                val d=n.getJSONObject("defaults");defaults(d,"template",if(kind=="CORE")null else "element")
                val tags=d.getJSONArray("tags");require(d.getBoolean("tagsOverride") || tags.length()==0)
                for(t in 0 until tags.length())require(tags.getString(t).isNotBlank() && tags.getString(t).length<=240)
            }
            val tags=n.getJSONArray("tags");require(tags.length()<=4096)
            for(t in 0 until tags.length()) require(tags.getString(t).isNotBlank() && tags.getString(t).length<=240)
            val files=n.getJSONArray("files");require(files.length()<=4096)
            val fileIds=(0 until files.length()).map {files.getString(it)}
            require(fileIds.distinct().size==fileIds.size && fileIds.all {resources.has(it)})
            require(AttachmentReferences.ids(n.getString("description")).all {it in fileIds}) {"Referencia de adjunto sin propietario."}
            if(n.has("photo")) {
                require(kind in setOf("CORE","LAYER"))
                val p=n.getJSONObject("photo")
                validateProjectPhoto(Base64.decode(p.getString("bytes"),Base64.NO_WRAP))
                AvatarFraming(p.getDouble("zoom").toFloat(),p.getDouble("x").toFloat(),p.getDouble("y").toFloat()).validate()
            }
        }
        return o
    }
    fun hash(bytes:ByteArray)=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
    fun defaults(o:JSONObject,project:String,node:String?):CreationDefaultsEntity {
        fun text(k:String)=if(o.isNull(k))null else o.getString(k)
        fun number(k:String)=if(o.isNull(k))null else o.getInt(k)
        val row=CreationDefaultsEntity(if(node==null)"P:$project" else "N:$node",project,node,
            text("purpose"),if(o.isNull("obligation"))null else o.getBoolean("obligation"),text("currency"),text("priority"),
            o.getBoolean("tagsOverride"),true,text("startRule"),number("startNumber"),number("startMinute"),text("dueRule"),number("dueNumber"),number("dueMinute"))
        com.r0ybt.arachn0de.data.repository.CreationDefaultsCodec.decode(row)
        return row
    }
    fun kind(raw:String)=read(raw).getJSONArray("elements").getJSONObject(0).getString("kind")
    fun document(raw:String):JSONObject {
        if(isStructural(raw))return read(raw)
        val c=SavedTemplateCodec.decode(raw)
        require(c.metroPlan==null) {"La plantilla histórica de Metro se conserva sin modificar."}
        val element=
            JSONObject().put("kind",c.purpose.name).put("parent",-1).put("title",c.title).put("description",c.description)
                .put("priority",c.priority.name).put("effort",0).put("pinned",false).put("states",0).put("tags",JSONArray()).put("files",JSONArray())
        if(c.amount!=null)element.put("amount",c.amount).put("currency",c.currency)
        return JSONObject().put("version",2).put("resources",JSONObject()).put("elements",JSONArray().put(element))
    }
}

/** Read/copy operations extend the existing saved-template repository and table. */
internal suspend fun SavedTemplateRepository.captureStructure(context:Context,attachments:AttachmentRepository,
    projectId:String?,nodeId:String?,includeFiles:Boolean=true):SavedTemplateEntity=withContext(Dispatchers.IO) {
    AttachmentRepository.fileOperations.withLock {db.withTransaction {
        val project=projectId?.let {requireNotNull(db.projectDao().getById(it))}
        require(project!=null || nodeId!=null)
        require(db.bankDao().roots().none {it.id==projectId}) {"Banca conserva sus datos operativos y no se guarda como plantilla."}
        val all=db.nodeDao().getProjectNodes(projectId)
        val root=nodeId?.let {id->requireNotNull(all.firstOrNull {it.id==id})}
        val ordered=mutableListOf<NodeEntity>()
        fun visit(n:NodeEntity,depth:Int=0) {require(ordered.size<4096 && depth<128) {"La estructura supera el límite de plantilla."};ordered.add(n);all.filter {it.parentId==n.id}.sortedBy {it.position}.forEach {visit(it,depth+1)}}
        if(root!=null) visit(root) else all.filter {it.parentId==null}.sortedBy {it.position}.forEach {visit(it)}
        val indexes=ordered.mapIndexed {i,n->n.id to i+(if(root==null)1 else 0)}.toMap()
        val resources=JSONObject();val elements=JSONArray()
        var budget=0L
        fun reserve(size:Long) {budget+=size;require(budget<=StructuralTemplateCodec.MAX_BYTES) {"La plantilla supera 8 MiB. Excluye adjuntos o guarda una estructura menor."}}
        val tags=db.tagDao().tags().associateBy {it.id};val links=db.tagDao().nodeTags().groupBy {it.nodeId}
        val conversion=projectId?.let {db.conversionDao().project(it)}
        val coreTags=conversion?.let {c->db.conversionDao().tags().filter {it.rootId==c.id}.map {it.tagId}}.orEmpty()
        val defaults=db.creationDefaultsDao().forProject(projectId ?: "")
        val defaultTags=db.creationDefaultsDao().tags()
        val photos=db.projectPhotoDao().all();val photoStore=ProjectPhotoStore(context)
        fun states(n:NodeEntity?):Int {
            if(n==null)return if(project?.verificationEnabled==true)3 else 2
            n.taskStateCount?.let {return it}
            if(n==root) {
                var parent=n.parentId
                while(parent!=null) {val row=all.first {it.id==parent};row.taskStateCount?.let {return it};parent=row.parentId}
                return if(project?.verificationEnabled==true)3 else 2
            }
            return 0
        }
        suspend fun element(owner:String,kind:String,title:String,description:String,parent:Int,n:NodeEntity?=null) {
            val files=if(!includeFiles) emptyList() else if(kind=="CORE")attachments.filesForProject(owner) else attachments.filesForNode(owner)
            files.forEach {f->
                if(resources.has(f.id))return@forEach
                require(f.byteSize in 1..StructuralTemplateCodec.MAX_BYTES.toLong()) {"Un adjunto supera el límite de plantilla."}
                reserve((f.byteSize*4+2)/3+512)
                val bytes=SecureFiles.read(attachments.localFile(f))
                require(bytes.size.toLong()==f.byteSize && StructuralTemplateCodec.hash(bytes)==f.sha256)
                resources.put(f.id,JSONObject().put("name",f.originalName).put("mime",f.mimeType).put("sha256",f.sha256).put("bytes",Base64.encodeToString(bytes,Base64.NO_WRAP)))
            }
            val text=if(includeFiles) description else AttachmentReferences.withoutReferences(description)
            reserve(JSONObject.quote(text).toByteArray().size.toLong()+JSONObject.quote(title).toByteArray().size+512)
            val e=JSONObject().put("kind",kind).put("parent",parent).put("title",title).put("description",text)
                .put("priority",n?.priority ?: conversion?.priority ?: "NONE").put("effort",n?.effort ?: 0).put("pinned",n?.isPinned ?: project?.isPinned ?: false)
                .put("states",states(n))
                .put("tags",JSONArray((if(kind=="CORE")coreTags else links[owner].orEmpty().map {it.tagId}).mapNotNull {tags[it]?.name}))
                .put("files",JSONArray(files.map {it.id}))
            if(n?.amountMinor!=null) e.put("amount",n.amountMinor).put("currency",n.currencyCode)
            defaults.firstOrNull {it.projectId==projectId && it.nodeId==(if(kind=="CORE")null else owner)}?.let {d->
                val json=JSONObject().put("purpose",d.purpose ?: JSONObject.NULL).put("obligation",d.obligation ?: JSONObject.NULL)
                    .put("currency",d.currency ?: JSONObject.NULL).put("priority",d.priority ?: JSONObject.NULL).put("tagsOverride",d.tagsOverride)
                    .put("startRule",d.startRule ?: JSONObject.NULL).put("startNumber",d.startNumber ?: JSONObject.NULL).put("startMinute",d.startMinute ?: JSONObject.NULL)
                    .put("dueRule",d.dueRule ?: JSONObject.NULL).put("dueNumber",d.dueNumber ?: JSONObject.NULL).put("dueMinute",d.dueMinute ?: JSONObject.NULL)
                    .put("tags",JSONArray(defaultTags.filter {it.defaultsId==d.id}.mapNotNull {tags[it.tagId]?.name}))
                e.put("defaults",json)
            }
            if(includeFiles) photos.firstOrNull {if(kind=="CORE")it.projectId==owner else it.nodeId==owner}?.let {p->
                val bytes=photoStore.durable.read(p.file);reserve((bytes.size.toLong()*4+2)/3+256)
                e.put("photo",JSONObject().put("bytes",Base64.encodeToString(bytes,Base64.NO_WRAP))
                    .put("zoom",p.photoZoom).put("x",p.photoX).put("y",p.photoY))
            }
            elements.put(e)
        }
        if(root==null) {val core=requireNotNull(project);element(core.id,"CORE",core.name,core.description,-1)}
        for(n in ordered) element(n.id,n.purpose,n.title,n.description,if(n==root)-1 else n.parentId?.let(indexes::get) ?: 0,n)
        val raw=JSONObject().put("version",2).put("elements",elements).put("resources",resources).toString()
        StructuralTemplateCodec.read(raw)
        SavedTemplateEntity(UUID.randomUUID().toString(),root?.title ?: requireNotNull(project).name,raw)
    }}
}

internal suspend fun SavedTemplateRepository.insertStructure(context:Context,attachments:AttachmentRepository,row:SavedTemplateEntity,
    projectId:String?,parentId:String?,sync:(File)->Unit=::syncTemplateDirectory):String=withContext(Dispatchers.IO) {
    val document=StructuralTemplateCodec.document(row.payload);val elements=document.getJSONArray("elements")
    for(i in 0 until elements.length()) {
        val e=elements.getJSONObject(i)
        PaymentCurrencyPolicy.requireNew(if(e.has("amount"))Obligation(e.getLong("amount"),e.getString("currency")) else null)
    }
    if(!StructuralTemplateCodec.isStructural(row.payload)) {
        val c=SavedTemplateCodec.decode(row.payload);val tags=db.tagDao().tags().associateBy {it.id}
        elements.getJSONObject(0).put("tags",JSONArray(c.tags.mapNotNull {tags[it]?.name}))
    }
    val core=elements.getJSONObject(0).getString("kind")=="CORE"
    require(if(core)projectId==null && parentId==null else projectId!=null || (parentId==null && elements.length()==1 && elements.getJSONObject(0).getString("kind") in setOf("ACTION","NOTE")))
    val operationId=UUID.randomUUID().toString();val reservation="template:$operationId";val resources=document.getJSONObject("resources")
    val imported=mutableMapOf<String,AttachmentFileEntity>();val ids=(0 until elements.length()).map {UUID.randomUUID().toString()}
    val photoStore=ProjectPhotoStore(context,sync);val newPhotos=mutableSetOf<String>()
    val at=System.currentTimeMillis()
    TemplateInsertionRecovery.begin(context,operationId,sync)
    try {
        resources.keys().forEach {old->val f=resources.getJSONObject(old);imported[old]=attachments.importGuestBytes(Base64.decode(f.getString("bytes"),Base64.NO_WRAP),reservation,f.getString("name"))}
        attachments.saveGalleryDraft(reservation,imported.values.mapTo(hashSetOf()) {it.id}) {
            val project=if(core)ids[0] else projectId
            if(!core && project!=null) requireNotNull(db.projectDao().getById(project))
            if(!core) {
                require(db.bankDao().roots().none {it.id==project}) {"Banca no admite estas plantillas."}
                val parent=parentId?.let {requireNotNull(db.nodeDao().getById(it))}
                require(parent?.awaitingVerification!=true && parent?.sprintMode!=true) {"El destino contiene una configuración histórica de Sprint. Su estructura se conserva."}
                require(parent==null || parent.purpose=="LAYER" || elements.getJSONObject(0).getString("kind") in setOf("ACTION","NOTE")) {"Una plantilla de Capa requiere un Núcleo o Capa."}
                com.r0ybt.arachn0de.data.repository.NodeRepository(db).validateParent(project,parentId)
            }
            val tagRepository=com.r0ybt.arachn0de.data.repository.TagRepository(db)
            val position=if(core)(db.projectDao().getAll().maxOfOrNull {it.position} ?: -1)+1 else
                (db.nodeDao().getProjectNodes(project).filter {it.parentId==parentId}.maxOfOrNull {it.position} ?: -1)+1
            for(i in ids.indices) {
                val e=elements.getJSONObject(i);val kind=e.getString("kind");var text=e.getString("description")
                AttachmentReferences.matches(text).toList().asReversed().forEach {m->
                    val id=requireNotNull(imported[m.groupValues[1]]).id
                    text=text.replaceRange(m.range,if(AttachmentReferences.isAudio(m))AttachmentReferences.audioToken(id) else AttachmentReferences.token(id,AttachmentReferences.label(m)))
                }
                if(kind=="CORE") db.projectDao().insert(ProjectEntity(ids[i],e.getString("title"),text,position,at,at,e.getBoolean("pinned"),e.getInt("states")==3))
                else {
                    val parent=if(i==0)parentId else e.getInt("parent").let {if(core && it==0)null else ids[it]}
                    db.nodeDao().insert(NodeEntity(ids[i],project,parent,e.getString("title"),text,false,if(i==0)position else i,at,at,
                        purpose=kind,amountMinor=if(e.has("amount"))e.getLong("amount") else null,currencyCode=if(e.has("amount"))e.getString("currency") else null,
                        priority=e.getString("priority"),isPinned=e.getBoolean("pinned"),taskStateCount=e.getInt("states").takeIf {it!=0},effort=e.getInt("effort")))
                }
                val names=e.getJSONArray("tags");val tagIds=mutableSetOf<String>()
                for(t in 0 until names.length())tagIds.add(tagRepository.create(names.getString(t)).id)
                if(kind!="CORE" && tagIds.isNotEmpty())tagRepository.assignNode(ids[i],tagIds)
                if(kind=="CORE" && (tagIds.isNotEmpty() || e.getString("priority")!="NONE")) {
                    val root=ConversionRootEntity(UUID.randomUUID().toString(),ids[i],null,ids[i],UUID.randomUUID().toString(),position,0,null,null,e.getString("priority"),null,false)
                    db.conversionDao().save(root);db.conversionDao().tags(tagIds.map {ConversionTagEntity(root.id,it)})
                }
                if(e.has("defaults")) {
                    val d=e.getJSONObject("defaults");val config=StructuralTemplateCodec.defaults(d,requireNotNull(project),if(kind=="CORE")null else ids[i])
                    db.creationDefaultsDao().save(config)
                    val tags=d.getJSONArray("tags");val copied=mutableSetOf<String>()
                    for(t in 0 until tags.length())copied.add(tagRepository.create(tags.getString(t)).id)
                    db.creationDefaultsDao().insertTags(copied.map {CreationDefaultsTagEntity(config.id,it)})
                }
                val files=e.getJSONArray("files");val fileIds=(0 until files.length()).map {requireNotNull(imported[files.getString(it)]).id}
                if(kind=="CORE")db.attachmentDao().attachProject(fileIds.map {ProjectAttachmentEntity(ids[i],it)})
                else db.attachmentDao().attachNode(fileIds.map {NodeAttachmentEntity(ids[i],it)})
                if(e.has("photo")) {
                    val p=e.getJSONObject("photo");val name="${UUID.randomUUID()}.png"
                    newPhotos.add(name)
                    photoStore.durable.record(photoStore.durable.recorded()+newPhotos)
                    photoStore.durable.write(name,Base64.decode(p.getString("bytes"),Base64.NO_WRAP));photoStore.durable.finishStaging()
                    db.projectPhotoDao().save(ProjectPhotoEntity(UUID.randomUUID().toString(),if(kind=="CORE")ids[i] else null,if(kind!="CORE")ids[i] else null,name,p.getDouble("zoom").toFloat(),p.getDouble("x").toFloat(),p.getDouble("y").toFloat()))
                }
            }
            ids[0]
        }
    } finally {withContext(NonCancellable) {
        // Existing journals/reservations keep interrupted operations recoverable.
        runCatching {TemplateInsertionRecovery.finish(context,operationId,attachments,sync)}
        runCatching {ProjectPhotoRepository(db,photoStore).cleanup()}
    }}
}
