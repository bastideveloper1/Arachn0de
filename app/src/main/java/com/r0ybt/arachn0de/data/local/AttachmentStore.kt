package com.r0ybt.arachn0de.data.local

import com.r0ybt.arachn0de.security.SecureFiles
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Original bytes only. Reservations survive editor closure and process death until explicit discard. */
open class AttachmentStore(context: Context, private val syncDirectory: (File) -> Unit = ::syncAttachmentDirectory) {
    private val context = context.applicationContext
    private val root = File(context.filesDir, "attachments")
    private val staging = File(root, "staging")
    private fun uuid(id: String) { require(UUID.fromString(id).toString() == id) }
    private fun lease(id: String) = File(staging, "$id.lease").also { uuid(id) }
    fun reserved(id: String) = lease(id).isFile
    fun release(id: String) {
        if (lease(id).exists()) { check(lease(id).delete()); syncDirectory(staging) }
    }
    fun reservations(draftId: String): Set<String> = staging.listFiles().orEmpty().filter { it.extension == "lease" && SecureFiles.text(it) == draftId }.map { it.nameWithoutExtension }.toSet()
    fun file(name: String): File {
        require(name.matches(Regex("[a-f0-9-]{36}\\.(png|jpg)")))
        uuid(name.substringBefore('.'))
        return File(root, name)
    }
    open fun delete(name: String): Boolean = file(name).let {
        if (!it.exists()) true else if (it.delete()) { syncDirectory(root); PrivateImageCache.invalidate(it); true } else false
    }

    suspend fun import(uri: Uri, draftId: String): AttachmentFileEntity {
        require(draftId.isNotBlank())
        check(staging.isDirectory || staging.mkdirs())
        val id = UUID.randomUUID().toString()
        val part = File(staging, "$id.part")
        var published: File? = null
        try {
            SecureFiles.output(lease(id)).use { it.write(draftId.toByteArray()); it.flush() }
            syncDirectory(staging)
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Imagen no disponible." }
                SecureFiles.output(part).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        require(size <= 20L * 1024 * 1024) { "La imagen supera 20 MiB." }
                        output.write(buffer, 0, count); digest.update(buffer, 0, count)
                    }
                    output.flush()
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            SecureFiles.decoded(part, bounds)
            val mime = bounds.outMimeType
            require(mime == "image/png" || mime == "image/jpeg") { "Solo se admiten PNG y JPEG." }
            require(bounds.outWidth in 1..32000 && bounds.outHeight in 1..32000) { "Imagen inválida." }
            val signature = SecureFiles.input(part).use { input -> ByteArray(8).also { require(input.read(it) == 8) } }
            require(if (mime == "image/png") signature.contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10)) else signature[0] == (-1).toByte() && signature[1] == (-40).toByte()) { "Imagen inválida." }
            var sample = 1
            while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
            requireNotNull(SecureFiles.decoded(part, BitmapFactory.Options().apply { inSampleSize = sample })) { "Imagen inválida." }.recycle()
            val original = runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } }.getOrNull()?.takeIf { it.isNotBlank() } ?: (if (uri.scheme == "file") uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } else null) ?: "imagen.${if (mime == "image/png") "png" else "jpg"}"
            val name = "$id.${if (mime == "image/png") "png" else "jpg"}"
            published = file(name)
            check(part.renameTo(published)) { "No se pudo guardar la imagen." }
            syncDirectory(root); syncDirectory(staging)
            currentCoroutineContext().ensureActive()
            return AttachmentFileEntity(id, name, original, mime, size, bounds.outWidth, bounds.outHeight,
                digest.digest().joinToString("") { "%02x".format(it) }, System.currentTimeMillis())
        } catch (failure: Throwable) {
            // Leave a reservation behind if a failed physical deletion needs recovery.
            if (published == null || delete(published.name)) release(id)
            throw failure
        } finally { part.delete() }
    }

    /** Run only while imports are serialized. Known reservations are never expired by age. */
    fun recover(known: List<AttachmentFileEntity>, protected: Set<String> = emptySet()) {
        require(root.canonicalFile == File(context.filesDir.canonicalFile, "attachments") && staging.canonicalFile == File(root.canonicalFile, "staging")) { "Directorio de adjuntos fuera del almacenamiento controlado." }
        val names = known.mapTo(hashSetOf()) { it.storageName } + protected
        val ids = known.mapTo(hashSetOf()) { it.id }
        val rootFiles = root.listFiles().orEmpty().filter { it.isFile && it.canonicalFile.parentFile == root.canonicalFile }
        fun canonicalId(file: File) = runCatching { uuid(file.nameWithoutExtension); true }.getOrDefault(false)
        staging.listFiles().orEmpty().filter { it.isFile && it.extension == "part" && canonicalId(it) && it.canonicalFile.parentFile == staging.canonicalFile }.forEach { check(it.delete()) }
        rootFiles.filter { it.name !in names }.forEach { file ->
            if (file.name.matches(Regex("[a-f0-9-]{36}\\.(png|jpg)")) && canonicalId(file) && delete(file.name)) release(file.nameWithoutExtension)
        }
        val remaining = root.listFiles().orEmpty().mapTo(hashSetOf()) { it.nameWithoutExtension }
        staging.listFiles().orEmpty().filter { it.isFile && it.extension == "lease" && canonicalId(it) && it.nameWithoutExtension !in ids && it.nameWithoutExtension !in remaining }.forEach { check(it.delete()) }
    }
    fun backupProtectedNames() = com.r0ybt.arachn0de.backup.BackupAttachmentFiles(context).recorded()

}

private fun syncAttachmentDirectory(directory: File) {
    val descriptor = android.system.Os.open(directory.path, android.system.OsConstants.O_RDONLY, 0)
    try { android.system.Os.fsync(descriptor) } finally { android.system.Os.close(descriptor) }
}
