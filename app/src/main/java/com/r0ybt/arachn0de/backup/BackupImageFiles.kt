package com.r0ybt.arachn0de.backup

import com.r0ybt.arachn0de.security.SecureFiles
import java.io.*
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** PNG bytes follow attachments in v2; the JSON manifest protects size, category and SHA-256. */
internal data class BackupImageFile(val directory: String, val name: String, val byteSize: Long, val sha256: String) {
    val key get() = "$directory/$name"
}
internal fun imageHash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
internal fun inspectImage(directory: String, name: String, file: File): BackupImageFile {
    require(file.isFile && SecureFiles.size(file) in 1..BackupLimits.AVATAR_BYTES.toLong()) { "Imagen ausente o demasiado grande." }
    val bytes = SecureFiles.input(file).use { it.readBytesBounded(BackupLimits.AVATAR_BYTES) }
    validateAvatar(bytes)
    return BackupImageFile(directory, name, bytes.size.toLong(), imageHash(bytes))
}
internal fun verifyImage(row: BackupImageFile, file: File) {
    require(file.isFile && SecureFiles.size(file) == row.byteSize) { "Imagen ausente o con tamaño inválido." }
    val actual = inspectImage(row.directory, row.name, file)
    require(actual == row) { "Imagen dañada o modificada." }
}
internal suspend fun copyImage(row: BackupImageFile, input: InputStream, output: OutputStream) {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(32 * 1024); var remaining = row.byteSize
    while (remaining > 0) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
        require(count >= 0) { "Fotografía incompleta." }; if (count == 0) continue
        output.write(buffer, 0, count); digest.update(buffer, 0, count); remaining -= count
    }
    require(digest.digest().joinToString("") { "%02x".format(it) } == row.sha256) { "Fotografía dañada." }
}
internal fun BackupData.imageNames(directory: String): Set<String> =
    (when (directory) { "avatars" -> avatars; "technology-icons" -> technologyIcons; else -> projectPhotoImages }).keys +
        imageFiles.filter { it.directory == directory }.map { it.name }
internal fun BackupData.imageBytes(directory: String, name: String): ByteArray {
    val inline = when (directory) { "avatars" -> avatars; "technology-icons" -> technologyIcons; else -> projectPhotoImages }
    inline[name]?.let { return it }
    val row = imageFiles.single { it.directory == directory && it.name == name }
    val file = imageContents.getValue(row.key); verifyImage(row, file)
    return SecureFiles.input(file).use { it.readBytesBounded(BackupLimits.AVATAR_BYTES) }
}
