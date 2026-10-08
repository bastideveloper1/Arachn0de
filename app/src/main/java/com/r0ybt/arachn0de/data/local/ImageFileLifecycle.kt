package com.r0ybt.arachn0de.data.local

import android.content.Context
import com.r0ybt.arachn0de.backup.BackupLimits
import com.r0ybt.arachn0de.backup.durableWrite
import com.r0ybt.arachn0de.backup.syncBackupDirectory
import java.io.File
import java.util.UUID

/** Caller holds the shared file mutex and verifies committed DB references before removal. */
internal class ImageFileLifecycle(context: Context, directoryName: String,
    private val sync: (File) -> Unit = ::syncBackupDirectory, private val now: () -> Long = System::currentTimeMillis) {
    init { require(directoryName in setOf("avatars", "technology-icons")) }
    private val privateRoot = context.filesDir.canonicalFile
    private val root = File(context.filesDir, directoryName)
    private val markers = File(context.filesDir, "image-lifecycle/$directoryName")
    companion object { const val ORPHAN_GRACE_MS = 24L * 60 * 60 * 1000; const val IMPORT_OWNER = "import" }
    private fun valid(name: String) { require(BackupLimits.avatarName.matches(name)) }
    private fun ownerKey(owner: String) = UUID.nameUUIDFromBytes(owner.toByteArray()).toString()
    private fun marker(name: String, suffix: String): File { valid(name); return File(markers, "$name.$suffix") }
    private fun write(file: File, value: String) {
        check(markers.isDirectory || markers.mkdirs())
        durableWrite(file, value.toByteArray()); sync(markers)
        val parent = requireNotNull(markers.parentFile); sync(parent); sync(requireNotNull(parent.parentFile))
    }
    fun reserve(name: String, owner: String = IMPORT_OWNER) {
        val lease = marker(name, "${ownerKey(owner)}.lease")
        if (!lease.exists()) write(lease, "reserved")
    }
    fun release(name: String, owner: String = IMPORT_OWNER) {
        val lease = marker(name, "${ownerKey(owner)}.lease")
        check(!lease.exists() || lease.delete())
        if (markers.isDirectory) sync(markers)
    }
    private fun markerFiles(): Array<File> {
        if (!markers.exists()) return emptyArray()
        return requireNotNull(markers.listFiles()) { "No se pueden verificar las reservas de imágenes." }
    }
    fun reserved(name: String): Boolean {
        valid(name)
        return markerFiles().any { it.name.startsWith("$name.") && it.name.endsWith(".lease") }
    }
    fun queue(name: String) { val pending = marker(name, "delete"); if (!pending.exists()) write(pending, "pending") }
    fun remove(name: String, delete: () -> Unit) {
        queue(name)
        if (reserved(name)) return
        delete()
        val pending = marker(name, "delete"); check(!pending.exists() || pending.delete())
        val orphan = marker(name, "orphan"); check(!orphan.exists() || orphan.delete())
        sync(markers)
    }
    /** Only recognized direct children; two observations a day apart are needed for legacy orphans. */
    fun cleanup(references: Set<String>, protected: Set<String>, delete: (String) -> Unit) {
        require(root.canonicalFile == File(privateRoot, root.name) && markers.canonicalFile == File(privateRoot, "image-lifecycle/${root.name}")) { "Directorio de imágenes fuera del almacenamiento controlado." }
        root.listFiles().orEmpty().filter { it.isFile && it.canonicalFile.parentFile == root.canonicalFile }.forEach { file ->
            val name = file.name
            if (BackupLimits.avatarName.matches(name)) {
                if (name in references || name in protected || reserved(name)) {
                    marker(name, "orphan").delete()
                } else if (marker(name, "delete").isFile) delete(name)
                else {
                    val orphan = marker(name, "orphan")
                    if (!orphan.exists()) write(orphan, now().toString())
                    else if (orphan.length() <= 32 && now() - (orphan.readText().toLongOrNull() ?: now()) >= ORPHAN_GRACE_MS && now() - file.lastModified() >= ORPHAN_GRACE_MS) delete(name)
                }
            } else if (name.matches(Regex("import-[a-zA-Z0-9-]+\\.tmp"))) {
                // Imports are serialized; a remaining recognized .tmp is an interrupted operation.
                check(file.delete()); sync(root)
            }
        }
        // A deletion may have succeeded before its bookkeeping could be synced/removed.
        markerFiles().filter { it.name.endsWith(".delete") }.forEach { pending ->
            val name = pending.name.removeSuffix(".delete")
            if (BackupLimits.avatarName.matches(name) && name !in references && name !in protected && !reserved(name) && !File(root, name).exists()) delete(name)
        }
    }
}
