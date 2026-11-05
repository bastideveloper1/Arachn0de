package com.r0ybt.arachn0de.security

/** One revocation boundary for memory caches; no files or bitmap recycling. */
internal class PrivateCacheScope : AutoCloseable {
    @Volatile var isOpen=true
        private set
    private val caches=mutableSetOf<AutoCloseable>()
    @Synchronized fun register(cache:AutoCloseable) {
        if(isOpen)caches.add(cache) else cache.close()
    }
    @Synchronized fun release(cache:AutoCloseable) {caches.remove(cache)}
    @Synchronized override fun close() {
        isOpen=false
        caches.forEach {it.close()}
        caches.clear()
    }
}

/** Lookup and publication share the cache lock with close; work runs outside it. */
internal class PrivateCache<K,V>(private val scope:PrivateCacheScope?,private val limit:Int) : AutoCloseable {
    private val values=linkedMapOf<K,V>()
    private var closed=false
    init {scope?.register(this)}
    @Synchronized fun get(key:K):V?=if(closed || scope?.isOpen==false)null else values[key]
    @Synchronized fun put(key:K,value:V) {
        if(closed || scope?.isOpen==false)return
        values[key]=value
        while(values.size>limit)values.remove(values.keys.first())
    }
    @Synchronized override fun close() {closed=true;values.clear()}
    fun dispose() {close();scope?.release(this)}
}
