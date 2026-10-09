package com.r0ybt.arachn0de.data.local

import android.content.Context
import android.net.Uri
import com.r0ybt.arachn0de.backup.*
import java.io.File

/** Same bounded normalization, private LRU and durable operations as avatars and technology icons. */
internal class ProjectPhotoStore(context: Context, sync: (File) -> Unit = ::syncBackupDirectory) {
    private val store = AvatarStore(context, "project-photos", sync)
    val durable get() = store.durable
    val lifecycle get() = store.lifecycle
    fun readThumbnail(name: String) = store.readThumbnail(name)
    fun import(uri: Uri, cancelled: () -> Unit): String {
        val name = store.import(uri, cancelled)
        try { validateProjectPhoto(durable.read(name)); durable.finishStaging(); return name }
        catch (failure: Throwable) { runCatching { lifecycle.release(name); store.delete(name) }; throw failure }
    }
    fun cleanup(references: Set<String>) = store.cleanup(references, durable.recorded())
}
