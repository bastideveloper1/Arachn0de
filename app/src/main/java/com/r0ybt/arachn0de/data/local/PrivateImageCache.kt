package com.r0ybt.arachn0de.data.local

import com.r0ybt.arachn0de.security.SecureFiles
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/** Shared, byte-bounded decoded thumbnails. Never recycle a bitmap still owned by Compose. */
internal object PrivateImageCache {
    const val MAX_BYTES = 4 * 1024 * 1024
    private val entries = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    @Synchronized fun read(file: File, maxDimension: Int = 96,immutable:Boolean=false): Bitmap? {
        if(SecureFiles.protected(file)) SecureFiles.requireActive(file)
        if (!file.isFile) { invalidate(file); return null }
        val key = "${file.absolutePath}:${file.length()}:${if(immutable) 0 else file.lastModified()}:$maxDimension"
        entries.get(key)?.let { if (!it.isRecycled) return it else entries.remove(key) }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        SecureFiles.decoded(file, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
        return SecureFiles.decoded(file, BitmapFactory.Options().apply { inSampleSize = sample })?.also { entries.put(key, it) }
    }
    @Synchronized fun invalidate(file: File) {
        entries.snapshot().keys.filter { it.startsWith("${file.absolutePath}:") }.forEach(entries::remove)
    }
    fun clear() = entries.evictAll()
    fun sizeBytes() = entries.size()
}
