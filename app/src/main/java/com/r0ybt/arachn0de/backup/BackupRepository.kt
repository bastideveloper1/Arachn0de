package com.r0ybt.arachn0de.backup

import android.content.Context
import androidx.room.withTransaction
import com.r0ybt.arachn0de.BuildConfig
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

internal class BackupRepository(
    private val database: Arachn0deDatabase,
    context: Context,
    private val avatars: BackupAvatarFiles = BackupAvatarFiles(context.applicationContext),
    private val attachments: BackupAttachmentFiles = BackupAttachmentFiles(context.applicationContext, avatars.syncDirectory),
    private val technologyIcons: BackupAvatarFiles = BackupAvatarFiles(context.applicationContext, "technology-icons", "technology-icon-journal", avatars.syncDirectory),
) {
    private val cache = File(context.cacheDir, "backups")
    private val mutex = Mutex()
    companion object { private val inspectedDirectories = java.util.concurrent.ConcurrentHashMap.newKeySet<String>() }
    private fun pendingMarker(name: String) = File(cache, "$name.keep")

    private suspend fun <T> withFiles(work: suspend () -> T): T =
        mutex.withLock { AttachmentRepository.fileOperations.withLock { work() } }

    suspend fun snapshot(): BackupData = withContext(Dispatchers.IO) {
        withFiles { snapshotLocked() }
    }

    private suspend fun snapshotLocked(): BackupData = database.withTransaction {
        val dao = database.backupDao()
        val projects = dao.projects()
        val nodes = dao.nodes()
        val people = dao.persons()
        val assignments = dao.assignments()
        require(projects.size.toLong() + nodes.size + people.size + assignments.size <= BackupLimits.RECORDS) { "Demasiados registros en el backup." }
        var totalBytes = 0L
        val images = people.mapNotNull { it.avatarFile }.toSet().associateWith { name ->
            avatars.read(name).also {
                totalBytes += it.size
                require(totalBytes <= BackupLimits.TOTAL_AVATAR_BYTES) { "Avatares demasiado grandes." }
            }
        }
        val technologies = database.technologyDao().catalog()
        val icons = technologies.mapNotNull { it.iconFile }.toSet().associateWith { name ->
            val bytes = try { technologyIcons.read(name) } catch (failure: Exception) {
                throw AttachmentBackupException("No se puede incluir el icono de «${technologies.first { it.iconFile == name }.name}»: falta o no es accesible.", failure)
            }
            bytes.also { totalBytes += it.size; require(totalBytes <= BackupLimits.TOTAL_AVATAR_BYTES) { "Avatares e iconos demasiado grandes." } }
        }
        val attachmentDao = database.attachmentDao()
        val nodeAttachments = attachmentDao.nodeAttachments()
        val projectAttachments = attachmentDao.projectAttachments()
        val ids = (nodeAttachments.map { it.attachmentId } + projectAttachments.map { it.attachmentId }).toSet()
        // Editor reservations and DELETE_PENDING rows are not confirmed user data.
        val files = attachmentDao.files().filter { it.id in ids }
        val contents = files.associate { it.storageName to attachments.file(it.storageName) }
        files.forEach { verifyBackupAttachment(it, contents.getValue(it.storageName)) }
        BackupData(BuildConfig.VERSION_NAME, System.currentTimeMillis(), projects,
            nodes, people, assignments, images, database.recurrenceDao().rules(), database.recurrenceDao().occurrences(), database.recurrenceDao().assignments(), database.tagDao().tags(), database.tagDao().nodeTags(), database.tagDao().ruleTags(), database.nodeEventDao().all(), database.creationDefaultsDao().all(), database.creationDefaultsDao().tags(), database.creationDefaultsDao().people(), files, nodeAttachments, projectAttachments, contents, technologies = technologies, nodeTechnologies = database.technologyDao().nodes(), projectTechnologies = database.technologyDao().projects(), technologyIcons = icons).also { it.validate() }
    }

    suspend fun create(): File = withContext(Dispatchers.IO) {
        withFiles {
            // Serialize file cleanup/imports through completion, and capture all DB rows in one transaction.
            val data = snapshotLocked()
            check(cache.isDirectory || cache.mkdirs())
            val temporary = File.createTempFile("backup-", ".part", cache)
            val complete = File(cache, "${UUID.randomUUID()}.arachnode")
            try {
                FileOutputStream(temporary).use { output ->
                    BackupContainer.writeBackup(data, output)
                    output.flush(); output.fd.sync()
                }
                currentCoroutineContext().ensureActive()
                check(temporary.renameTo(complete))
                durableWrite(pendingMarker(complete.name), "pending SAF export".toByteArray())
                complete
            } catch (failure: Throwable) {
                complete.delete(); pendingMarker(complete.name).delete()
                throw failure
            } finally { temporary.delete() }
        }
    }

    suspend fun inspect(input: InputStream): BackupData = withContext(Dispatchers.IO) {
        check(cache.isDirectory || cache.mkdirs())
        val directory = File(cache, "inspect-${UUID.randomUUID()}").apply { check(mkdir()) }
        inspectedDirectories.add(directory.absolutePath)
        try { input.use { BackupContainer.readBackup(it, directory) } }
        catch (failure: Throwable) { inspectedDirectories.remove(directory.absolutePath); deleteInspection(directory); throw failure }
    }

    /** A failure/cancellation before commit rolls back Room and removes only new unreferenced files. */
    suspend fun restore(data: BackupData) = withContext(Dispatchers.IO) {
        withFiles {
            val ordered = data.validate()
            require(data.attachmentContents.keys == data.attachmentFiles.map { it.storageName }.toSet()) { "Faltan archivos adjuntos." }
            data.attachmentFiles.forEach { verifyBackupAttachment(it, data.attachmentContents.getValue(it.storageName)) }
            recoverFiles() // Complete cleanup from a previous interrupted operation before replacing its journal.
            val iconNames = data.technologyIcons.keys.associateWith { "${UUID.randomUUID()}.png" }
            val names = data.avatars.keys.associateWith { "${UUID.randomUUID()}.png" }
            val attachmentNames = data.attachmentFiles.associate { it.storageName to "${UUID.randomUUID()}.${it.storageName.substringAfterLast('.')}" }
            try {
                technologyIcons.record(iconNames.values.toSet())
                data.technologyIcons.forEach { (name, bytes) ->
                    currentCoroutineContext().ensureActive()
                    technologyIcons.write(iconNames.getValue(name), bytes)
                }
                technologyIcons.finishStaging()
                attachments.record(attachmentNames.values.toSet())
                data.attachmentFiles.forEach { row -> attachments.stage(row, data.attachmentContents.getValue(row.storageName), attachmentNames.getValue(row.storageName)) }
                attachments.finishStaging()
                avatars.record(names.values.toSet())
                data.avatars.forEach { (name, bytes) ->
                    currentCoroutineContext().ensureActive()
                    avatars.write(names.getValue(name), bytes)
                }
                avatars.finishStaging()
                database.withTransaction {
                    val technologyDao = database.technologyDao()
                    technologyIcons.record(technologyDao.catalog().mapNotNull { it.iconFile }.toSet() + iconNames.values)
                    val attachmentDao = database.attachmentDao()
                    attachments.record(attachmentDao.files().map { it.storageName }.toSet() + attachmentNames.values)
                    val dao = database.backupDao()
                    val previous = dao.persons().mapNotNull { it.avatarFile }.toSet()
                    // Persist both sets before DB changes: recovery queries committed references after a crash.
                    avatars.record(previous + names.values)
                    val recurrence = database.recurrenceDao()
                    database.creationDefaultsDao().clear()
                    database.tagDao().clear()
                    recurrence.deleteOccurrences(); recurrence.deleteAssignments(); recurrence.deleteRules()
                    technologyDao.clear()
                    attachmentDao.clearNodes(); attachmentDao.clearProjects(); attachmentDao.clearFiles()
                    dao.deleteAssignments()
                    dao.detachNodes() // Avoid SQLite's recursive cascade depth limit.
                    dao.deleteNodes(); dao.deleteProjects(); dao.deletePersons()
                    data.projects.forEach { database.projectDao().insert(it) }
                    data.persons.forEach { person -> database.personDao().insert(person.copy(avatarFile = person.avatarFile?.let(names::getValue))) }
                    ordered.forEach { database.nodeDao().insert(it) }
                    data.attachmentFiles.forEach { attachmentDao.insert(it.copy(storageName = attachmentNames.getValue(it.storageName))) }
                    attachmentDao.attachNode(data.nodeAttachments)
                    attachmentDao.attachProject(data.projectAttachments)
                    data.technologies.forEach { technologyDao.insert(it.copy(iconFile = it.iconFile?.let(iconNames::getValue))) }
                    technologyDao.assignNodes(data.nodeTechnologies)
                    technologyDao.assignProjects(data.projectTechnologies)
                    database.personDao().assign(data.assignments)
                    data.recurrenceRules.forEach { recurrence.insert(it) }
                    recurrence.assign(data.recurrenceAssignments)
                    data.recurrenceOccurrences.forEach { recurrence.record(it) }
                    database.tagDao().insert(data.tags)
                    database.tagDao().assignNodes(data.nodeTags)
                    database.tagDao().assignRules(data.recurrenceTags)
                    database.nodeEventDao().insertAll(data.nodeEvents)
                    data.creationDefaults.forEach { database.creationDefaultsDao().save(it) }
                    database.creationDefaultsDao().insertTags(data.defaultsTags)
                    database.creationDefaultsDao().insertPeople(data.defaultsPeople)
                    currentCoroutineContext().ensureActive()
                }
            } finally {
                // Room has either committed or rolled back. Cleanup failure must never report a committed restore as failed.
                withContext(NonCancellable) { runCatching { recoverFiles() } }
            }
        }
    }

    fun discard(data: BackupData?) {
        data?.inspectionDirectory?.takeIf { it.parentFile == cache && it.name.matches(Regex("inspect-[a-f0-9-]{36}")) }?.let {
            inspectedDirectories.remove(it.absolutePath); deleteInspection(it)
        }
    }

    suspend fun recover() = withContext(Dispatchers.IO) {
        withFiles {
            recoverFiles()
            cache.listFiles().orEmpty().filter { System.currentTimeMillis() - it.lastModified() > 24L * 60 * 60 * 1000 }.forEach { file ->
                if (file.canonicalFile.parentFile != cache.canonicalFile) return@forEach
                when {
                    file.isFile && file.name.matches(Regex("[a-f0-9-]{36}\\.arachnode")) && !pendingMarker(file.name).exists() -> file.delete()
                    file.isFile && file.name.matches(Regex("backup-[a-zA-Z0-9-]+\\.part")) -> file.delete()
                    file.isDirectory && file.name.matches(Regex("inspect-[a-f0-9-]{36}")) && file.absolutePath !in inspectedDirectories -> deleteInspection(file)
                    file.isFile && file.name.matches(Regex("[a-f0-9-]{36}\\.arachnode\\.keep")) && !File(cache, file.name.removeSuffix(".keep")).exists() -> file.delete()
                }
            }
        }
    }

    private suspend fun recoverFiles() {
        val names = avatars.recorded()
        database.withTransaction {
            names.forEach { if (database.personDao().avatarReferences(it) == 0) avatars.delete(it) }
        }
        avatars.clearJournal()
        val icons = technologyIcons.recorded()
        database.withTransaction { icons.forEach { if (database.technologyDao().iconReferences(it) == 0) technologyIcons.delete(it) } }
        technologyIcons.clearJournal()
        val attachmentNames = attachments.recorded()
        database.withTransaction {
            attachmentNames.forEach { if (database.attachmentDao().storageReferences(it) == 0) attachments.delete(it) }
        }
        attachments.clearJournal()
    }

    private fun deleteInspection(directory: File) {
        // Inspection contains only flat, generated PNG/JPEG files. Preserve unknown children.
        directory.listFiles().orEmpty().filter { it.isFile && it.canonicalFile.parentFile == directory.canonicalFile && BackupLimits.attachmentName.matches(it.name) }.forEach { it.delete() }
        directory.delete()
    }
    fun discardPending(name: String) {
        require(name.matches(Regex("[a-f0-9-]{36}\\.arachnode")))
        check(!pendingMarker(name).exists() || pendingMarker(name).delete())
        check(!File(cache, name).exists() || File(cache, name).delete())
    }
    fun pendingFile(name: String): File {
        require(name.matches(Regex("[a-f0-9-]{36}\\.arachnode")))
        return File(cache, name).also { require(it.isFile) { "El backup temporal ya no está disponible. Crea otro." } }
    }
}
