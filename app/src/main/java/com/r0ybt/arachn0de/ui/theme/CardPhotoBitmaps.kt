package com.r0ybt.arachn0de.ui.theme

import android.graphics.Bitmap

/** Session-owned 4 MiB cache for normal project photographs; active draw references are not recycled. */
internal class CardPhotoBitmaps(private val scope:com.r0ybt.arachn0de.security.PrivateCacheScope?=null) : AutoCloseable {
    private var closed=false
    private fun available()=!closed && scope?.isOpen!=false
    private var photoContext:android.content.Context?=null
    private val photos=object:android.util.LruCache<String,Bitmap>(4*1024*1024) {
        override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount
    }
    @Synchronized fun photo(context:android.content.Context,key:String):Bitmap? {
        if(!available())return null
        if(photoContext!==context) {clearPhotos();photoContext=context}
        return photos.get(key)
    }
    @Synchronized fun retainPhoto(context:android.content.Context,key:String,bitmap:Bitmap) {
        if(available() && photoContext===context) photos.put(key,bitmap)
    }
    private val loading=mutableMapOf<String,kotlinx.coroutines.CompletableDeferred<Bitmap?>>()
    suspend fun loadPhoto(context:android.content.Context,key:String,load:suspend ()->Bitmap?):Bitmap? {
        var owner=false
        val pending=synchronized(this) {
            if(!available())return null
            photo(context,key)?.let {return it}
            loading[key] ?: kotlinx.coroutines.CompletableDeferred<Bitmap?>().also {loading[key]=it;owner=true}
        }
        if(!owner)return pending.await()
        try {
            val bitmap=load()
            return synchronized(this) {
                if(available() && loading[key]===pending && photoContext===context) {
                    if(bitmap!=null)retainPhoto(context,key,bitmap)
                    pending.complete(bitmap)
                    bitmap
                } else null
            }
        } catch(failure:Throwable) {
            pending.completeExceptionally(failure)
            throw failure
        } finally {synchronized(this) {if(loading[key]===pending)loading.remove(key)}}
    }
    @Synchronized fun clearPhotos() {
        photos.evictAll();photoContext=null
        loading.values.forEach {it.cancel()};loading.clear()
    }
    init {scope?.register(this)}
    @Synchronized override fun close() {closed=true;clearPhotos()}
    fun dispose() {close();scope?.release(this)}
}
internal val LocalCardPhotoBitmaps=androidx.compose.runtime.staticCompositionLocalOf<CardPhotoBitmaps?> {null}
