package com.r0ybt.arachn0de.backup

import android.content.Context
import com.r0ybt.arachn0de.data.local.AttachmentFileEntity
import java.io.*
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class AttachmentBackupException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Stream original bytes and verify both recorded size and SHA-256; never decode a full image. */
internal suspend fun copyBackupAttachment(row: AttachmentFileEntity, input: InputStream, output: OutputStream) {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(32 * 1024)
    var remaining = row.byteSize
    while (remaining > 0) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (count < 0) throw AttachmentBackupException("Adjunto incompleto: ${row.originalName}")
        if (count == 0) continue
        output.write(buffer, 0, count)
        digest.update(buffer, 0, count)
        remaining -= count
    }
    val hash = digest.digest().joinToString("") { "%02x".format(it) }
    if (hash != row.sha256) throw AttachmentBackupException("Adjunto dañado o modificado: ${row.originalName}")
}

internal suspend fun verifyBackupAttachment(row: AttachmentFileEntity, file: File) {
    try {
        require(file.isFile && file.length() == row.byteSize) { "Tamaño inválido." }
        file.inputStream().use { copyBackupAttachment(row, it, object : OutputStream() {
            override fun write(b: Int) {}
            override fun write(b: ByteArray, off: Int, len: Int) {}
        }) }
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outMimeType == row.mimeType && bounds.outWidth == row.width && bounds.outHeight == row.height) { "Imagen incompatible con sus metadatos." }
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (failure: Exception) { throw AttachmentBackupException("No se puede incluir o recuperar el adjunto «${row.originalName}»: falta, no es accesible o está dañado.", failure) }
}

/** Journal lists only files touched by restore; committed DB references decide crash recovery. */
internal class BackupAttachmentFiles(context: Context, private val syncDirectory: (File) -> Unit = ::syncBackupDirectory) {
    private val root = File(context.filesDir, "attachments")
    private val journal = File(context.filesDir, "backup-attachment-restore-journal.json")
    fun file(name: String): File {
        require(BackupLimits.attachmentName.matches(name))
        return File(root, name)
    }
    fun record(names: Set<String>) {
        require(names.all { BackupLimits.attachmentName.matches(it) })
        val part = File(journal.parentFile, "${journal.name}.part")
        durableWrite(part, JSONObject().put("files", JSONArray(names.sorted())).toString().toByteArray())
        check(part.renameTo(journal))
        syncDirectory(requireNotNull(journal.parentFile))
    }
    fun recorded(): Set<String> {
        if (!journal.exists()) return emptySet()
        require(journal.length() <= 8L * 1024 * 1024)
        val list = JSONObject(journal.readText()).getJSONArray("files")
        return (0 until list.length()).map { list.getString(it).also { name -> require(BackupLimits.attachmentName.matches(name)) } }.toSet()
    }
    suspend fun stage(row: AttachmentFileEntity, source: File, name: String) {
        check(root.isDirectory || root.mkdirs())
        val target = file(name)
        check(!target.exists())
        source.inputStream().use { input -> FileOutputStream(target).use { output ->
            copyBackupAttachment(row, input, output)
            require(input.read() == -1) { "Contenido adicional en adjunto." }
            output.flush(); output.fd.sync()
        } }
    }
    fun finishStaging() { if (root.isDirectory) syncDirectory(root) }
    fun delete(name: String) { val file = file(name); check(!file.exists() || file.delete()) }
    fun clearJournal() {
        finishStaging()
        check(!journal.exists() || journal.delete())
        val part = File(journal.parentFile, "${journal.name}.part")
        check(!part.exists() || part.delete())
        syncDirectory(requireNotNull(journal.parentFile))
    }
}
