package com.r0ybt.arachn0de.data.local

import android.content.Context
import android.net.Uri
import com.r0ybt.arachn0de.backup.*
import java.io.File
import java.io.FileOutputStream

/** Reuses bounded avatar normalization; technologies own a separate directory and durable journal. */
internal class TechnologyIconStore(context: Context, syncDirectory: (File) -> Unit = ::syncBackupDirectory) {
    private val avatars = AvatarStore(context, "technology-icons", syncDirectory)
    private val root = File(context.filesDir, "technology-icons")
    val durable = BackupAvatarFiles(context, "technology-icons", "technology-icon-journal", syncDirectory)
    val lifecycle get() = avatars.lifecycle
    fun cleanup(references: Set<String>, protected: Set<String>) = avatars.cleanup(references, protected)
    fun readThumbnail(name: String) = avatars.readThumbnail(name)
    fun read(name: String) = avatars.read(name)
    fun file(name: String): File {
        require(BackupLimits.avatarName.matches(name))
        return File(root, name)
    }
    fun import(uri: Uri, checkCancelled: () -> Unit = {}): String {
        val name = avatars.import(uri, checkCancelled)
        try {
            validateAvatar(durable.read(name))
            FileOutputStream(file(name), true).use { it.fd.sync() }
            durable.finishStaging()
            return name
        } catch (failure: Throwable) { runCatching { lifecycle.release(name); durable.delete(name) }; throw failure }
    }
}
