package com.r0ybt.arachn0de.diagrams

import android.content.SharedPreferences
import android.util.Base64
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.security.EncryptedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

/** Entire library commits atomically in SQLCipher. Resources are keyed by content, not device paths. */
internal class DiagramRepository(private val preferences:SharedPreferences) {
    companion object {private val writes=Mutex()}
    fun observe():Flow<DiagramLibrary> = callbackFlow {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener {_,_->trySend(Unit)}
        preferences.registerOnSharedPreferenceChangeListener(listener);trySend(Unit)
        awaitClose {preferences.unregisterOnSharedPreferenceChangeListener(listener)}
    }.conflate().map {DiagramCodec.library(preferences.all)}.flowOn(Dispatchers.IO)
    suspend fun load(id:String)=withContext(Dispatchers.IO) {val raw=requireNotNull(preferences.getString("doc_$id",null));DiagramCodec.decode(raw) to raw}
    private suspend fun write(edit:(MutableMap<String,Any?>)->Unit)=withContext(Dispatchers.IO+NonCancellable) {writes.withLock {AttachmentRepository.fileOperations.withLock {
        val values=preferences.all.toMutableMap();edit(values)
        DiagramCodec.validatePayload(EncryptedPreferences.encode(values))
        val editor=preferences.edit().clear();values.forEach {(k,v)->editor.putString(k,v as String)};check(editor.commit()) {"No se pudo guardar. Se conserva el estado anterior."}
    }}}
    suspend fun save(d:DiagramDocument,expected:String?):String {
        val raw=DiagramCodec.encode(d)
        write {v->val actual=v["doc_${d.id}"];check(actual==expected||actual==raw) {"El diagrama cambió o se restauró un respaldo. Guarda una copia para conservar tu edición."};v["doc_${d.id}"]=raw}
        return raw
    }
    suspend fun delete(id:String,expected:String)=write {v->check(v["doc_$id"]==expected);v.remove("doc_$id")}
    suspend fun importImage(png:String):String {
        val bytes=Base64.decode(png,Base64.NO_WRAP);require(com.r0ybt.arachn0de.visual.canvasImage(bytes)==png)
        val id=DiagramCodec.hash(bytes);write {it["asset_$id"]=png};return id
    }
    suspend fun favorites(colors:List<Int>)=write {require(colors.size<=32);it["favorite_colors"]=org.json.JSONArray(colors.distinct()).toString()}
    suspend fun removeAsset(id:String)=write {values->val library=DiagramCodec.library(values);require(library.documents.none {id in it.assets()}) {"Este icono se utiliza en un diagrama guardado."};values.remove("asset_$id")}
}
