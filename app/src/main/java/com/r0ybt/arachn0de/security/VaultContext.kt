package com.r0ybt.arachn0de.security

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.io.File

/** Captured context never follows the application's next session. */
internal open class VaultContext(base:Context,val session:SecureFiles.Session,subdirectory:String?=null):ContextWrapper(base) {
    private val dataRoot=if(subdirectory==null) session.root else File(session.root,subdirectory.also { require(it.matches(Regex("[a-z-]+"))) })
    var primary:Boolean=true
    lateinit var database:com.r0ybt.arachn0de.data.local.Arachn0deDatabase
    private val secrets=mutableListOf<ByteArray>()
    @Synchronized fun rememberSecret(bytes:ByteArray):ByteArray { session.checkOpen();secrets.add(bytes);return bytes }
    private val preferences=mutableMapOf<String,EncryptedPreferences>()
    override fun getApplicationContext():Context=this
    override fun getFilesDir():File { session.checkOpen();return File(dataRoot,"files").apply { check(mkdirs() || isDirectory) } }
    override fun getCacheDir():File { session.checkOpen();return File(dataRoot,"cache").apply { check(mkdirs() || isDirectory) } }
    override fun getDataDir():File { session.checkOpen();return dataRoot }
    override fun getDatabasePath(name:String):File {
        session.checkOpen();require(name==File(name).name && name=="arachn0de.db")
        return File(dataRoot,"databases/$name").apply { check(parentFile!!.isDirectory || parentFile!!.mkdirs()) }
    }
    @Synchronized override fun getSharedPreferences(name:String,mode:Int):SharedPreferences {
        session.checkOpen();require(name.matches(Regex("[a-zA-Z0-9_-]+")))
        return preferences.getOrPut(name) { EncryptedPreferences(session,load={ kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { database.privatePreferenceDao().get(name)?.payload?.let(EncryptedPreferences::decode) ?: emptyMap() } },persist={ values -> kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { database.privatePreferenceDao().save(com.r0ybt.arachn0de.data.local.PrivatePreferenceEntity(name,EncryptedPreferences.encode(values))) } }) }
    }
    @Synchronized fun reloadPreferences() { preferences.values.forEach(EncryptedPreferences::reload) }
    @Synchronized fun clearMemory() { preferences.values.forEach(EncryptedPreferences::clearMemory);preferences.clear();secrets.forEach {it.fill(0)};secrets.clear() }
}
