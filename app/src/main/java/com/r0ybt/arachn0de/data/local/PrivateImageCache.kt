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
    @Synchronized fun read(file: File, maxDimension: Int = 96,immutable:Boolean=false,oriented:Boolean=false): Bitmap? {
        if(SecureFiles.protected(file)) SecureFiles.requireActive(file)
        if (!file.isFile) { invalidate(file); return null }
        val key = "${file.absolutePath}:${file.length()}:${if(immutable) 0 else file.lastModified()}:$maxDimension:$oriented"
        entries.get(key)?.let { if (!it.isRecycled) return it else entries.remove(key) }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        SecureFiles.decoded(file, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
        val source=SecureFiles.decoded(file, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val matrix=android.graphics.Matrix()
        if(oriented) when(runCatching {SecureFiles.orientation(file)}.getOrDefault(1)) {
            2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f)
            5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f)
            7->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)};8->matrix.setRotate(-90f)
        }
        val bitmap=if(matrix.isIdentity) source else android.graphics.Bitmap.createBitmap(source,0,0,source.width,source.height,matrix,true).also {if(it!==source) source.recycle()}
        entries.put(key,bitmap);return bitmap
    }
    @Synchronized fun invalidate(file: File) {
        entries.snapshot().keys.filter { it.startsWith("${file.absolutePath}:") }.forEach(entries::remove)
    }
    fun clear() = entries.evictAll()
    fun sizeBytes() = entries.size()
}
