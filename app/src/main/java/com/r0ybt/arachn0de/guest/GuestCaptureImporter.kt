package com.r0ybt.arachn0de.guest

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.r0ybt.arachn0de.security.*
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

internal object GuestCaptureImporter {
    val returnToPerson=MutableStateFlow<String?>(null)
    val error=MutableStateFlow<String?>(null)
    suspend fun importPending(base:Context,session:VaultSession) {
        if(!session.primary)return
        val queue=GuestCaptureQueue(base)
        for(id in queue.ids()) {
            try {
                val row=queue.read(id)
                try {
                    if(row.vault!=session.access.id.toString()) continue
                    if(!row.accepted) {queue.remove(id);continue}
                    val db=session.database
                    require(db.personDao().get(row.person)!=null) {"El perfil de la captura ya no existe. La fotografía cifrada se conserva."}
                    val draft="guest:$id"
                    val existing=db.personMemoryDao().memories().firstOrNull {it.id==id}
                    val file=if(existing==null) session.attachments.importGuestBytes(row.bytes,draft) else {
                        require(existing.personId==row.person)
                        val image=db.personMemoryDao().images().single {it.memoryId==id}
                        requireNotNull(db.attachmentDao().get(requireNotNull(image.attachmentId)))
                    }
                    if(existing==null) session.attachments.saveGalleryDraft(draft,setOf(file.id)) {
                        require(db.personMemoryDao().memories().none {it.id==id})
                        val position=db.personMemoryDao().memories().count {it.personId==row.person && !it.isPinned}
                        db.personMemoryDao().save(PersonMemoryEntity(id,row.person,java.time.LocalDate.now().toString(),"",position))
                        db.personMemoryDao().images(listOf(PersonMemoryImageEntity(id,id,file.id,null,0,zoom=row.framing.zoom,x=row.framing.x,y=row.framing.y)))
                    }
                    val person=requireNotNull(db.personDao().get(row.person))
                    if(person.avatarFile==null) {
                        val source=AttachmentStore(session.context).file(file.storageName)
                        val avatar=session.people.importAvatar(Uri.EMPTY,source=source)
                        try {
                            session.people.setGuestAvatarIfAbsent(row.person,avatar,row.framing)
                        } finally {withContext(NonCancellable) {session.people.discardAvatar(avatar,draft)}}
                    }
                    check(db.personMemoryDao().images().any {it.memoryId==id && it.attachmentId==file.id})
                    com.r0ybt.arachn0de.backup.verifyBackupAttachment(file,AttachmentStore(session.context).file(file.storageName))
                    returnToPerson.value=row.person;queue.remove(id);error.value=null
                } finally {row.bytes.fill(0)}
            } catch(c:CancellationException) {throw c} catch(e:Exception) {error.value=e.message ?: "No se pudo importar la captura. Se conserva cifrada para reintentar."}
        }
    }
}
