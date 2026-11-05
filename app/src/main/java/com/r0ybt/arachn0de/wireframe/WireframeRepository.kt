package com.r0ybt.arachn0de.wireframe

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.r0ybt.arachn0de.security.EncryptedPreferences
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** SQLCipher-backed preference row, included in the existing atomic backup snapshot. No loose files. */
internal class WireframeRepository(private val preferences:SharedPreferences) {
    private companion object {val writes=Mutex()}
    fun observe():Flow<List<WireDocument>> = callbackFlow {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener {_,_->trySend(Unit)}
        preferences.registerOnSharedPreferenceChangeListener(listener);trySend(Unit)
        awaitClose {preferences.unregisterOnSharedPreferenceChangeListener(listener)}
    }.conflate().map {read()}.flowOn(Dispatchers.IO)
    private fun read()=preferences.all.values.map {WireCodec.decode(it as String)}.sortedByDescending {it.modified}
    suspend fun load(id:String):Pair<WireDocument,String> = withContext(Dispatchers.IO) {
        val raw=requireNotNull(preferences.getString(id,null)) {"El prototipo ya no existe."};WireCodec.decode(raw) to raw
    }
    suspend fun save(document:WireDocument,expected:String?):String = withContext(Dispatchers.IO+NonCancellable) {writes.withLock {AttachmentRepository.fileOperations.withLock {
        WireCodec.validate(document)
        val raw=WireCodec.encode(document)
        val current=preferences.getString(document.id,null)
        // A UI recreation can interrupt delivery after a successful commit. Retrying is idempotent.
        if(current!=raw) {
            check(current==expected) {"El dibujo cambió o se restauró un backup. Tu edición se conserva; guarda una copia o vuelve a abrirlo."}
            val all=preferences.all.toMutableMap().apply {put(document.id,raw)}
            WireCodec.validatePayload(EncryptedPreferences.encode(all))
            check(preferences.edit().putString(document.id,raw).commit()) {"No se pudo guardar. Se conserva el estado anterior."}
        }
        raw
    }}}
    suspend fun create(name:String,format:WireFormat):WireDocument {
        val doc=WireDocument(name=name.trim(),format=format);save(doc,null);return doc
    }
    suspend fun duplicate(id:String):WireDocument {
        val original=load(id).first
        val now=System.currentTimeMillis()
        val doc=original.copy(id=UUID.randomUUID().toString(),name=original.name.take(90)+" · copia",created=now,modified=now)
        save(doc,null);return doc
    }
    suspend fun rename(id:String,name:String) {val (doc,raw)=load(id);save(doc.copy(name=name.trim(),modified=maxOf(System.currentTimeMillis(),doc.modified)),raw)}
    suspend fun delete(id:String,expected:String) = withContext(Dispatchers.IO+NonCancellable) {writes.withLock {AttachmentRepository.fileOperations.withLock {
        check(preferences.getString(id,null)==expected) {"El prototipo cambió. Reabre la confirmación."}
        check(preferences.edit().remove(id).commit()) {"No se pudo eliminar el prototipo."}
    }}}
}

internal suspend fun importWireImage(context:Context,uri:Uri):String = com.r0ybt.arachn0de.visual.importCanvasImage(context,uri)
