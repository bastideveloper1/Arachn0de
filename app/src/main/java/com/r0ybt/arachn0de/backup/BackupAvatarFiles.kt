package com.r0ybt.arachn0de.backup

import android.content.Context
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

/** Unique new PNGs are durable before Room can reference them. Old files are never overwritten. */
internal class BackupAvatarFiles(context: Context, private val syncDirectory: (File) -> Unit = ::syncBackupDirectory) {
    private val directory = File(context.filesDir, "avatars")
    private val journal = File(context.filesDir, "backup-restore-journal.json")
    private fun file(name: String): File {
        require(BackupLimits.avatarName.matches(name))
        return File(directory, name)
    }
    fun read(name: String): ByteArray {
        val image = file(name)
        require(image.isFile && image.length() in 1..BackupLimits.AVATAR_BYTES.toLong()) { "Avatar ausente o demasiado grande." }
        return image.inputStream().use { input -> input.readBytesBounded(BackupLimits.AVATAR_BYTES) }
    }
    fun record(names: Set<String>) {
        require(names.all { BackupLimits.avatarName.matches(it) })
        val temporary = File(journal.parentFile, "backup-restore-journal.part")
        durableWrite(temporary, JSONObject().put("files", JSONArray(names.sorted())).toString().toByteArray())
        check(temporary.renameTo(journal)) { "No se pudo preparar la restauración." }
        syncDirectory(requireNotNull(journal.parentFile))
    }
    fun write(name: String, bytes: ByteArray) {
        check(directory.isDirectory || directory.mkdirs())
        val image = file(name)
        check(!image.exists()) { "El avatar de destino ya existe." }
        durableWrite(image, bytes)
    }
    fun finishStaging() { if (directory.isDirectory) syncDirectory(directory) }
    fun recorded(): Set<String> {
        if (!journal.exists()) return emptySet()
        require(journal.length() <= 8L * 1024 * 1024) { "Diario de restauración inválido." }
        val array = JSONObject(journal.readText()).getJSONArray("files")
        return (0 until array.length()).map { array.getString(it).also { name -> require(BackupLimits.avatarName.matches(name)) } }.toSet()
    }
    fun delete(name: String) { val image = file(name); check(!image.exists() || image.delete()) }
    fun clearJournal() {
        if (directory.isDirectory) syncDirectory(directory)
        check(!journal.exists() || journal.delete())
        val temporary = File(journal.parentFile, "backup-restore-journal.part")
        check(!temporary.exists() || temporary.delete())
        syncDirectory(requireNotNull(journal.parentFile))
    }
}

internal fun durableWrite(file: File, bytes: ByteArray) {
    FileOutputStream(file).use { output -> output.write(bytes); output.flush(); output.fd.sync() }
}

private fun syncBackupDirectory(directory: File) {
    val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
    try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
}

internal fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(32 * 1024)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (count == 0) continue
        total += count
        require(total <= limit) { "Archivo demasiado grande." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
