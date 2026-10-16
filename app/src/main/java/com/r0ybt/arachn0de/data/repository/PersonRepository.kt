package com.r0ybt.arachn0de.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.Person
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

class PersonRepository(private val database: Arachn0deDatabase, private val avatars: AvatarStore) {
    internal suspend fun favoritePeople():Set<String> = withContext(Dispatchers.IO) {
        val row=database.privatePreferenceDao().get(PersonFavorites.NAME)
        row?.let {PersonFavorites.decode(it.payload)} ?: emptySet()
    }
    internal fun observeFavorites()=database.invalidationTracker.createFlow("private_preferences").map {favoritePeople()}.distinctUntilChanged().flowOn(Dispatchers.IO)
    internal suspend fun toggleFavorite(person:String)=withContext(Dispatchers.IO) {database.withTransaction {
        require(dao.get(person)!=null) {"La Persona ya no existe."}
        val ids=favoritePeople()
        database.privatePreferenceDao().save(PrivatePreferenceEntity(PersonFavorites.NAME,PersonFavorites.encode(if(person in ids) ids-person else ids+person)))
    }}
    internal fun observePublications()=database.personMemoryDao().observeMemories()
    internal suspend fun exportAvatar(file:String,destination:Uri)=files {avatars.export(file,destination)}
    internal fun observeSelf()=database.cascadeRelationDao().observeSelf()
    internal suspend fun identifySelf(person:String?)=database.withTransaction {
        if(person==null) database.cascadeRelationDao().clearIdentity() else {
            require(database.personDao().get(person)!=null) {"La Persona ya no existe."}
            database.cascadeRelationDao().save(LocalIdentityEntity(personId=person))
        }
    }
    private val dao = database.personDao()
    internal fun observePersonTechnologies()=database.personIntegrationDao().observeTechnologies()
    internal fun observeTechnologyCatalog()=database.technologyDao().observeCatalog()
    internal fun observeWork(id:String)=database.personIntegrationDao().observeWork(id)
    internal suspend fun saveTechnology(person:String,technology:String,proficiency:PersonProficiency)=database.withTransaction {
        val row=PersonTechnologyEntity(person,technology,proficiency.name)
        require(dao.get(person)!=null) {"La Persona ya no existe."}
        require(database.technologyDao().get(technology)!=null) {"La Tecnología ya no existe en el catálogo."}
        database.personIntegrationDao().save(row)
    }
    internal suspend fun removeTechnology(person:String,technology:String)=database.withTransaction {database.personIntegrationDao().remove(person,technology)}

    internal fun observePhotos()=database.personGalleryDao().observePhotos()
    internal fun observeBirthdays()=database.personGalleryDao().observeBirthdays()
    internal suspend fun saveBirthday(id:String,date:String)=database.withTransaction {
        require(dao.get(id)!=null)
        if(date.isBlank()) database.personGalleryDao().removeBirthday(id) else {
            try {validatePersonGallery(setOf(id),emptyList(),listOf(PersonBirthdayEntity(id,date)))} catch(e:Exception) {throw IllegalArgumentException("Escribe una fecha real con formato AAAA-MM-DD.",e)}
            require(!java.time.LocalDate.parse(date).isAfter(java.time.LocalDate.now())) {"La fecha no puede estar en el futuro."}
            database.personGalleryDao().save(PersonBirthdayEntity(id,date))
        }
    }
    internal suspend fun savePhoto(photo:PersonPhotoEntity,draftId:String)=files {
        require(avatars.exists(photo.file));recoverLocked()
        try {
            database.withTransaction {
                val existing=database.personGalleryDao().photos()
                require(existing.none {it.id==photo.id && it.personId!=photo.personId})
                validatePersonGallery(database.backupDao().persons().mapTo(hashSetOf()) {it.id},existing.filter {it.id!=photo.id}+photo,database.personGalleryDao().birthdays())
                avatars.durable.record(setOfNotNull(photo.file,existing.firstOrNull {it.id==photo.id}?.file))
                database.personGalleryDao().save(photo)
                database.personMemoryDao().legacyMemories();database.personMemoryDao().legacyImages()
            }
            withContext(NonCancellable) {runCatching {avatars.lifecycle.release(photo.file);avatars.lifecycle.release(photo.file,draftId)}}
        } finally {withContext(NonCancellable) {runCatching {recoverLocked()}}}
    }
    internal suspend fun removePhoto(id:String)=files {
        try {database.withTransaction {
            database.personGalleryDao().photos().firstOrNull {it.id==id}?.let {removed->
                avatars.durable.record(setOf(removed.file));database.personMemoryDao().removeLegacy(id);database.personMemoryDao().removeEmpty();database.personGalleryDao().remove(id)
                database.personGalleryDao().photos().filter {it.personId==removed.personId}.sortedBy {it.position}.forEachIndexed {index,photo->database.personGalleryDao().save(photo.copy(position=index))}
            }
        }} finally {withContext(NonCancellable) {runCatching {recoverLocked()}}}
    }
    /** Caller holds the common file lock and Room transaction; physical cleanup follows commit. */
    internal suspend fun releaseGalleryLegacy(ids:Set<String>) {
        val used=database.personMemoryDao().images().mapNotNullTo(hashSetOf()) {it.legacyPhotoId}
        database.personGalleryDao().photos().filter {it.id in ids && it.id !in used}.forEach {
            avatars.durable.record(setOf(it.file));database.personGalleryDao().remove(it.id)
        }
    }
    internal suspend fun reorderPhotos(personId:String,ids:List<String>)=database.withTransaction {
        val photos=database.personGalleryDao().photos().filter {it.personId==personId}
        require(ids.size==photos.size && ids.toSet()==photos.map {it.id}.toSet())
        ids.forEachIndexed {index,id->database.personGalleryDao().save(photos.first {it.id==id}.copy(position=index))}
    }
    internal fun observeProfiles()=database.personProfileDao().observeProfiles()
    internal fun observeRelations()=database.personProfileDao().observeRelations()
    internal suspend fun saveProfile(profile:PersonProfileEntity)=database.withTransaction {
        require(dao.get(profile.personId)!=null) {"La Persona ya no existe."}
        com.r0ybt.arachn0de.domain.model.validatePersonDetails(setOf(profile.personId),listOf(profile),emptyList())
        database.personProfileDao().save(profile)
    }
    internal suspend fun saveRelation(relation:PersonRelationEntity,isNew:Boolean)=database.withTransaction {
        val details=database.personProfileDao()
        val existing=details.relations()
        require(isNew || existing.any {it.id==relation.id}) {"La relación ya no existe."}
        require(!isNew || existing.none {it.id==relation.id}) {"La relación ya existe."}
        com.r0ybt.arachn0de.domain.model.validatePersonDetails(database.backupDao().persons().mapTo(hashSetOf()) {it.id},emptyList(),existing.filter {it.id!=relation.id}+relation)
        if(isNew) details.insert(relation) else check(details.update(relation)==1)
    }
    internal suspend fun deleteRelation(id:String)=database.withTransaction {database.personProfileDao().deleteRelation(id)}

    fun observePeople() = dao.observePeople().map { rows -> rows.map { Person(it.id, it.name, it.avatarFile, it.avatarZoom, it.avatarX, it.avatarY) } }
    fun observeAssignments(projectId: String) = dao.observeAssignments(projectId).map { rows ->
        rows.groupBy { it.nodeId }.mapValues { (_, people) -> people.map { Person(it.id, it.name, it.avatarFile, it.avatarZoom, it.avatarX, it.avatarY) } }
    }
    fun observeAllAssignments() = dao.observeAllAssignments().map { rows ->
        rows.groupBy { it.nodeId }.mapValues { (_, people) -> people.map { Person(it.id, it.name, it.avatarFile, it.avatarZoom, it.avatarX, it.avatarY) } }
    }
    private suspend fun <T> files(work: suspend () -> T): T = withContext(Dispatchers.IO) {
        AttachmentRepository.fileOperations.withLock { work() }
    }
    suspend fun importAvatar(uri: Uri, source:java.io.File?=null): String {
        var file: String? = null
        try { return files {
            val coroutine = currentCoroutineContext()
            source?.let {require(it.canonicalFile.parentFile in listOf(java.io.File(avatars.privateRoot(),"attachments").canonicalFile,java.io.File(avatars.privateRoot(),"avatars").canonicalFile))}
            avatars.import(uri,sourceFile=source) { coroutine.ensureActive() }.also { file = it }
        } } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { discardAvatar(file) }; throw cancelled
        }
    }
    suspend fun retainAvatar(file: String?, draftId: String) = files { file?.let { avatars.lifecycle.reserve(it, draftId) } }
    suspend fun discardAvatar(file: String?, draftId: String? = null) = files {
        if (file != null) runCatching {
            avatars.lifecycle.release(file)
            draftId?.let { avatars.lifecycle.release(file, it) }
            database.withTransaction { if (dao.avatarReferences(file) == 0) avatars.delete(file) }
        }
        Unit
    }
    suspend fun save(id: String, name: String, avatarFile: String?, isNew: Boolean = true, draftId: String? = null, zoom: Float = 1f, x: Float = 0f, y: Float = 0f, birthday: String? = null, profile: PersonProfileEntity? = null, identity: Boolean? = null): Boolean = files {
        com.r0ybt.arachn0de.domain.model.AvatarFraming(zoom, x, y).validate()
        val trimmed = name.trim(); require(trimmed.isNotEmpty())
        recoverLocked()
        if (avatarFile != null) require(avatars.exists(avatarFile))
        try {
            val result = database.withTransaction {
                val current = dao.get(id)
                if (current == null && !isNew) return@withTransaction false
                if (current != null && isNew) return@withTransaction current.name == trimmed && current.avatarFile == avatarFile && current.avatarZoom == zoom && current.avatarX == x && current.avatarY == y
                val names = setOfNotNull(current?.avatarFile, avatarFile)
                if (names.isNotEmpty()) avatars.durable.record(names)
                val saved=if (current == null) { dao.insert(PersonEntity(id, trimmed, avatarFile, zoom, x, y)); true }
                else dao.update(id, trimmed, avatarFile, zoom, x, y) == 1
                if(saved) {
                    profile?.let {require(it.personId==id);saveProfile(it)}
                    if(birthday!=null) saveBirthday(id,birthday)
                    if(identity!=null) identifySelf(if(identity) id else null)
                }
                saved
            }
            if (result && avatarFile != null) withContext(NonCancellable) { runCatching {
                avatars.lifecycle.release(avatarFile); draftId?.let { avatars.lifecycle.release(avatarFile, it) }
            } }
            result
        } finally { withContext(NonCancellable) { runCatching { recoverLocked() } } }
    }
    internal suspend fun setPhotoAsAvatar(personId: String, photoId: String): Boolean = files {
        recoverLocked()
        try {database.withTransaction {
            val current=dao.get(personId) ?: return@withTransaction false
            val photo=database.personGalleryDao().photos().firstOrNull {it.id==photoId && it.personId==personId}
                ?: throw IllegalArgumentException("La fotografía ya no está disponible.")
            require(avatars.exists(photo.file)) {"La fotografía no está disponible."}
            avatars.durable.record(setOfNotNull(current.avatarFile,photo.file))
            // Exchange the reference slot with an unlisted former primary: retain every image,
            // including when all four reference slots are occupied, without copying any bytes.
            if(current.avatarFile!=null && current.avatarFile!=photo.file && database.personGalleryDao().photos().none {it.personId==personId && it.file==current.avatarFile}) {
                database.personGalleryDao().save(photo.copy(file=current.avatarFile,zoom=current.avatarZoom,x=current.avatarX,y=current.avatarY))
            }
            dao.update(current.id,current.name,photo.file,photo.zoom,photo.x,photo.y)==1
        }} finally {withContext(NonCancellable) {runCatching {recoverLocked()}}}
    }
    suspend fun delete(id: String): Boolean = files {
        recoverLocked()
        try { database.withTransaction {
            val old = dao.get(id) ?: return@withTransaction false
            avatars.durable.record(database.personGalleryDao().photos().filter {it.personId==id}.map {it.file}.toSet()+setOfNotNull(old.avatarFile))
            database.personMemoryDao().removeOwner(id)
            val removed=dao.delete(id)==1
            if(removed) {
                val ids=favoritePeople()
                if(id in ids) database.privatePreferenceDao().save(PrivatePreferenceEntity(PersonFavorites.NAME,PersonFavorites.encode(ids-id)))
            }
            removed
        } } finally { withContext(NonCancellable) { runCatching { recoverLocked() } } }
    }
    suspend fun cleanup() = files {
        recoverLocked()
        database.withTransaction {
            avatars.cleanup(database.backupDao().persons().mapNotNull { it.avatarFile }.toSet()+database.personGalleryDao().photos().map {it.file}, avatars.durable.recorded())
        }
    }
    private suspend fun recoverLocked() {
        if (!avatars.durable.hasJournal()) return
        val names = avatars.durable.recorded()
        database.withTransaction { names.forEach { if (dao.avatarReferences(it) == 0) avatars.durable.delete(it) } }
        avatars.durable.clearJournal()
    }
    suspend fun setResponsiblePeople(nodeId: String, ids: Set<String>): Boolean = database.withTransaction {
        if (dao.nodeExists(nodeId) == 0 || ids.any { dao.get(it) == null }) return@withTransaction false
        dao.clearAssignments(nodeId)
        dao.assign(ids.map { NodePersonEntity(nodeId, it) })
        true
    }
}
