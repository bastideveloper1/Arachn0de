package com.r0ybt.arachn0de.backup

import android.content.Context
import androidx.room.withTransaction
import com.r0ybt.arachn0de.BuildConfig
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

internal class BackupRepository(private val database: Arachn0deDatabase, context: Context, private val avatars: BackupAvatarFiles = BackupAvatarFiles(context.applicationContext)) {
    private val cache = File(context.cacheDir, "backups")
    private val mutex = Mutex()

    suspend fun snapshot(): BackupData = withContext(Dispatchers.IO) {
        mutex.withLock {
            database.withTransaction {
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
                BackupData(BuildConfig.VERSION_NAME, System.currentTimeMillis(), projects,
                    nodes, people, assignments, images, database.recurrenceDao().rules(), database.recurrenceDao().occurrences(), database.recurrenceDao().assignments(), database.tagDao().tags(), database.tagDao().nodeTags(), database.tagDao().ruleTags()).also { it.validate() }
            }
        }
    }

    suspend fun create(): File = withContext(Dispatchers.IO) {
        val payload = BackupJson.encode(snapshot())
        check(cache.isDirectory || cache.mkdirs())
        val temporary = File.createTempFile("backup-", ".part", cache)
        val complete = File(cache, "${UUID.randomUUID()}.arachnode")
        try {
            FileOutputStream(temporary).use { output ->
                BackupContainer.write(payload, output)
                output.flush(); output.fd.sync()
            }
            currentCoroutineContext().ensureActive()
            check(temporary.renameTo(complete))
            complete
        } catch (failure: Throwable) {
            complete.delete()
            throw failure
        } finally { temporary.delete() }
    }

    suspend fun inspect(input: InputStream): BackupData = withContext(Dispatchers.IO) {
        input.use { BackupJson.decode(BackupContainer.read(it)) }
    }

    /** A failure/cancellation before commit rolls back Room and removes only new unreferenced files. */
    suspend fun restore(data: BackupData) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val ordered = data.validate()
            recoverFiles() // Complete cleanup from a previous interrupted operation before replacing its journal.
            val names = data.avatars.keys.associateWith { "${UUID.randomUUID()}.png" }
            try {
                avatars.record(names.values.toSet())
                data.avatars.forEach { (name, bytes) ->
                    currentCoroutineContext().ensureActive()
                    avatars.write(names.getValue(name), bytes)
                }
                avatars.finishStaging()
                database.withTransaction {
                    val dao = database.backupDao()
                    val previous = dao.persons().mapNotNull { it.avatarFile }.toSet()
                    // Persist both sets before DB changes: recovery queries committed references after a crash.
                    avatars.record(previous + names.values)
                    val recurrence = database.recurrenceDao()
                    database.tagDao().clear()
                    recurrence.deleteOccurrences(); recurrence.deleteAssignments(); recurrence.deleteRules()
                    dao.deleteAssignments()
                    dao.detachNodes() // Avoid SQLite's recursive cascade depth limit.
                    dao.deleteNodes(); dao.deleteProjects(); dao.deletePersons()
                    data.projects.forEach { database.projectDao().insert(it) }
                    data.persons.forEach { person -> database.personDao().insert(person.copy(avatarFile = person.avatarFile?.let(names::getValue))) }
                    ordered.forEach { database.nodeDao().insert(it) }
                    database.personDao().assign(data.assignments)
                    data.recurrenceRules.forEach { recurrence.insert(it) }
                    recurrence.assign(data.recurrenceAssignments)
                    data.recurrenceOccurrences.forEach { recurrence.record(it) }
                    database.tagDao().insert(data.tags)
                    database.tagDao().assignNodes(data.nodeTags)
                    database.tagDao().assignRules(data.recurrenceTags)
                    currentCoroutineContext().ensureActive()
                }
            } finally {
                // Room has either committed or rolled back. Cleanup failure must never report a committed restore as failed.
                withContext(NonCancellable) { runCatching { recoverFiles() } }
            }
        }
    }

    suspend fun recover() = withContext(Dispatchers.IO) {
        mutex.withLock { recoverFiles() }
        // Temporary backups are private and regenerable. Do not remove a recently pending SAF export.
        cache.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24L * 60 * 60 * 1000 }?.forEach { it.delete() }
    }

    private suspend fun recoverFiles() {
        val names = avatars.recorded()
        database.withTransaction {
            names.forEach { if (database.personDao().avatarReferences(it) == 0) avatars.delete(it) }
        }
        avatars.clearJournal()
    }

    fun pendingFile(name: String): File {
        require(name.matches(Regex("[a-f0-9-]{36}\\.arachnode")))
        return File(cache, name).also { require(it.isFile) { "El backup temporal ya no está disponible. Crea otro." } }
    }
}
