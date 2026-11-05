package com.r0ybt.arachn0de.data.local

import com.r0ybt.arachn0de.security.SecureFiles
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/** Shared, byte-bounded decoded thumbnails. Never recycle a bitmap still owned by Compose. */
internal object PrivateImageCache {
    const val MAX_BYTES = 4 * 1024 * 1024
    internal class Entries(private val scope:com.r0ybt.arachn0de.security.PrivateCacheScope?=null):AutoCloseable {
        private var closed=false
        private var generation=0L
        private val values=object:LruCache<String,Bitmap>(MAX_BYTES) {
            override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount
        }
        init {scope?.register(this)}
        @Synchronized fun get(key:String):Bitmap?=if(closed || scope?.isOpen==false)null else values.get(key)
        @Synchronized fun generation()=generation
        @Synchronized fun put(key:String,bitmap:Bitmap,expected:Long):Boolean {
            if(closed || scope?.isOpen==false || generation!=expected)return false
            values.put(key,bitmap);return true
        }
        @Synchronized fun invalidate(path:String) {values.snapshot().keys.filter {it.startsWith("$path:")}.forEach(values::remove)}
        @Synchronized fun clear() {generation++;values.evictAll()}
        @Synchronized fun sizeBytes()=values.size()
        @Synchronized override fun close() {closed=true;values.evictAll()}
    }
    @Volatile private var legacyRetired=false
    private val legacy=lazy {Entries().also {if(legacyRetired)it.close()}}
    private val legacyEntries get()=legacy.value
    @Synchronized fun read(file: File, maxDimension: Int = 96,immutable:Boolean=false,oriented:Boolean=false): Bitmap? {
        val owner=SecureFiles.owner(file)
        if(owner!=null)SecureFiles.requireActive(file)
        val entries=owner?.imageCache ?: legacyEntries
        val generation=entries.generation()
        if (!file.isFile) { invalidate(file); return null }
        val key = "${file.absolutePath}:${file.length()}:${if(immutable) 0 else file.lastModified()}:$maxDimension:$oriented"
        entries.get(key)?.let { if (!it.isRecycled) return it else entries.invalidate(file.absolutePath) }
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
        val retained=if(owner==null && legacyRetired)false else entries.put(key,bitmap,generation)
        return if(retained || owner==null)bitmap else null
    }
    @Synchronized fun invalidate(file: File) {
        legacyEntries.invalidate(file.absolutePath)
        runCatching {SecureFiles.owner(file)?.imageCache?.invalidate(file.absolutePath)}
    }
    // Legacy paths have no revocable owner; once a vault is used, decode without retaining them.
    fun retireLegacy() {legacyRetired=true;if(legacy.isInitialized())legacy.value.close()}
    fun clearLegacy() {if(legacy.isInitialized())legacy.value.clear()}
    fun clear() {clearLegacy();SecureFiles.clearImageCaches()}
    fun sizeBytes() = (if(legacy.isInitialized())legacy.value.sizeBytes() else 0)+SecureFiles.imageCacheBytes()
}
