package com.r0ybt.arachn0de.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.security.SecureFiles
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.time.LocalDate
import java.util.UUID

internal data class PersonMemoryState(val entries:List<PersonMemoryEntity>,val images:List<PersonMemoryImageEntity>,val files:List<AttachmentFileEntity>,val legacy:List<PersonPhotoEntity>) {
    private val byMemory=images.groupBy {it.memoryId}.mapValues {(_,rows)->rows.sortedWith(compareBy<PersonMemoryImageEntity> {it.position}.thenBy {it.id})}
    fun photos(id:String)=byMemory[id].orEmpty()
}
internal class PersonMemoryRepository(private val db:Arachn0deDatabase,private val context:Context,
    private val attachments:AttachmentRepository,private val people:PersonRepository) {
    private val dao=db.personMemoryDao()
    fun observe()=db.invalidationTracker.createFlow("person_memories","person_memory_images","person_photos","attachment_files").map {
        db.withTransaction {PersonMemoryState(dao.memories(),dao.images(),db.attachmentDao().files(),db.personGalleryDao().photos())}
    }.flowOn(Dispatchers.IO)
    suspend fun save(entry:PersonMemoryEntity,images:List<PersonMemoryImageEntity>,draft:String) {
        attachments.saveGalleryDraft(draft,images.mapNotNullTo(hashSetOf()) {it.attachmentId}) {
            val old=dao.memories().firstOrNull {it.id==entry.id}
            require(old==null || old.personId==entry.personId)
            val before=dao.images().filter {it.memoryId==entry.id}
            require(images.all {it.memoryId==entry.id})
            val stored=old?.let {entry.copy(position=it.position,isPinned=it.isPinned)} ?: entry.copy(position=0,isPinned=false)
            if(old==null) {
                dao.memories().filter {it.personId==entry.personId && !it.isPinned}.forEachIndexed {index,row->dao.save(row.copy(position=index+1))}
            }
            validatePersonMemories(db.backupDao().persons().mapTo(hashSetOf()) {it.id},dao.memories().filter {it.id!=entry.id}+stored,
                dao.images().filter {it.memoryId!=entry.id}+images,db.attachmentDao().files(),db.personGalleryDao().photos())
            dao.clearImages(entry.id);dao.save(stored);dao.images(images)
            people.releaseGalleryLegacy(before.mapNotNullTo(hashSetOf()) {it.legacyPhotoId})
        }
        withContext(NonCancellable) {runCatching {people.cleanup()}}
    }
    private suspend fun normalize(person:String) {
        dao.memories().filter {it.personId==person}.groupBy {it.isPinned}.values.forEach {group->
            group.forEachIndexed {index,row->if(row.position!=index) dao.save(row.copy(position=index))}
        }
    }
    suspend fun pin(id:String,pinned:Boolean)=db.withTransaction {
        val row=requireNotNull(dao.memories().firstOrNull {it.id==id}) {"La entrada ya no existe."}
        if(row.isPinned!=pinned) {
            normalize(row.personId)
            val destination=dao.memories().count {it.personId==row.personId && it.isPinned==pinned}
            dao.save(row.copy(isPinned=pinned,position=destination));normalize(row.personId)
        }
    }
    suspend fun reorder(person:String,source:String,target:String)=db.withTransaction {
        val rows=dao.memories().filter {it.personId==person}
        val from=requireNotNull(rows.firstOrNull {it.id==source}) {"La entrada ya no existe."}
        val to=requireNotNull(rows.firstOrNull {it.id==target}) {"La entrada de destino ya no existe."}
        require(from.isPinned==to.isPinned) {"Solo puedes ordenar dentro del mismo grupo."}
        val group=rows.filter {it.isPinned==from.isPinned}.toMutableList()
        val destination=group.indexOf(to)
        group.remove(from);group.add(destination,from)
        if(source!=target) group.forEachIndexed {index,row->dao.save(row.copy(position=index))}
    }
    suspend fun deleteEntry(id:String) {
        attachments.saveGalleryDraft(UUID.randomUUID().toString(),emptySet()) {
            val old=dao.images().filter {it.memoryId==id}
            val person=dao.memories().firstOrNull {it.id==id}?.personId
            dao.remove(id);person?.let {normalize(it)};people.releaseGalleryLegacy(old.mapNotNullTo(hashSetOf()) {it.legacyPhotoId})
        }
        withContext(NonCancellable) {runCatching {people.cleanup()}}
    }
    suspend fun removeImage(id:String) {
        attachments.saveGalleryDraft(UUID.randomUUID().toString(),emptySet()) {
            val images=dao.images();val target=images.firstOrNull {it.id==id}
            if(target!=null) {
                val remaining=images.filter {it.memoryId==target.memoryId && it.id!=id}.sortedBy {it.position}.mapIndexed {i,row->row.copy(position=i)}
                dao.clearImages(target.memoryId)
                if(remaining.isEmpty()) {
                    val person=dao.memories().firstOrNull {it.id==target.memoryId}?.personId
                    dao.remove(target.memoryId);person?.let {normalize(it)}
                } else dao.images(remaining)
                people.releaseGalleryLegacy(setOfNotNull(target.legacyPhotoId))
            }
        }
        withContext(NonCancellable) {runCatching {people.cleanup()}}
    }
    suspend fun setAvatar(person:String,image:PersonMemoryImageEntity,state:PersonMemoryState) {
        if(image.legacyPhotoId!=null) {check(people.setPhotoAsAvatar(person,image.legacyPhotoId));return}
        val avatar=people.importAvatar(Uri.EMPTY,source=file(image,state))
        try {
            val owner=requireNotNull(db.personDao().get(person))
            check(people.save(person,owner.name,avatar,isNew=false))
        } finally {withContext(NonCancellable) {people.discardAvatar(avatar)}}
    }
    fun file(image:PersonMemoryImageEntity,state:PersonMemoryState):File {
        image.attachmentId?.let {id->return AttachmentStore(context).file(state.files.first {it.id==id}.storageName)}
        val legacy=state.legacy.first {it.id==image.legacyPhotoId}
        require(Regex("[a-f0-9-]{36}\\.png").matches(legacy.file))
        return File(File(context.filesDir,"avatars"),legacy.file)
    }
    suspend fun thumbnail(image:PersonMemoryImageEntity,state:PersonMemoryState,size:Int)=withContext(Dispatchers.IO) {PrivateImageCache.read(file(image,state),size,oriented=true)}
    suspend fun export(image:PersonMemoryImageEntity,state:PersonMemoryState,uri:Uri)=withContext(Dispatchers.IO) {
        AttachmentRepository.fileOperations.lock()
        try {SecureFiles.input(file(image,state)).use {input->requireNotNull(context.contentResolver.openOutputStream(uri,"w")).use {output->input.copyTo(output)}}}
        finally {AttachmentRepository.fileOperations.unlock()}
    }
    suspend fun create(person:String,ids:List<String>,draft:String):String {
        val id=UUID.randomUUID().toString()
        save(PersonMemoryEntity(id,person,LocalDate.now().toString(),"",0),ids.mapIndexed {i,attachment->PersonMemoryImageEntity(UUID.randomUUID().toString(),id,attachment,null,i)},draft)
        return id
    }
}
