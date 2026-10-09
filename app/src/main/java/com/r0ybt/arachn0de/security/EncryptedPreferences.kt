package com.r0ybt.arachn0de.security

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.r0ybt.arachn0de.backup.syncBackupDirectory

/** Commit is atomic/durable; no Android XML file containing private preferences is created. */
internal open class EncryptedPreferences(private val session:SecureFiles.Session,
    private val load:()->Map<String,Any>, private val persist:(Map<String,Any>)->Unit):SharedPreferences {
    constructor(file:File,session:SecureFiles.Session,sync:(File)->Unit=::syncBackupDirectory):this(session,
        load={ DurableVaultFile.recover(file,sync);if(file.exists()) decode(SecureFiles.text(file)) else emptyMap() },
        persist={ values ->
            val plain=encode(values).toByteArray(Charsets.UTF_8)
            val encrypted=java.io.ByteArrayOutputStream()
            try { session.withKey { VaultCrypto.encrypt(java.io.ByteArrayInputStream(plain),encrypted,it,"private-v1:${session.id}") } }
            finally { plain.fill(0) }
            DurableVaultFile.replace(file,encrypted.toByteArray(),sync)
        })
    private var values:Map<String,Any> = load()
    private val listeners=java.util.concurrent.CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()
    companion object {
        fun encode(values:Map<String,*>):String = JSONObject().apply {
            values.toSortedMap().forEach { (key,value) ->
                val type=when(value) { is String->"string";is Boolean->"boolean";is Int->"int";is Long->"long";is Float->"float";is Set<*>->"set";else->error("Preferencia incompatible") }
                put(key,JSONObject().put("type",type).put("value",if(value is Set<*>) JSONArray(value.toList().sortedBy { it.toString() }) else value))
            }
        }.toString()
        fun decode(text:String):Map<String,Any> {
            val json=JSONObject(text)
            return json.keys().asSequence().associateWith { key ->
                val row=json.getJSONObject(key)
                require(row.keys().asSequence().toSet()==setOf("type","value"))
                require(row.get("type") is String)
                val value=row.get("value")
                when(row.getString("type")) {
                    "string"->(value as? String ?: error("Tipo inválido"));"boolean"->(value as? Boolean ?: error("Tipo inválido"));"int"->(value as? Int ?: error("Tipo inválido"))
                    "long"->when(value) { is Int->value.toLong();is Long->value;else->error("Tipo inválido") };"float"->(value as? Number ?: error("Tipo inválido")).toFloat().also { require(it.isFinite()) }
                    "set"->row.getJSONArray("value").let { a -> (0 until a.length()).map { (a.get(it) as? String ?: error("Tipo inválido")) }.toSet() }
                    else->error("Preferencia incompatible")
                }
            }
        }
    }
    @Synchronized override fun getAll():MutableMap<String,*> { session.checkOpen();return values.mapValues { (_,v)->if(v is Set<*>) v.toSet() else v }.toMutableMap() }
    @Synchronized private fun value(key:String?):Any? { session.checkOpen();return values[key] }
    override fun getString(key:String?,defValue:String?):String?=value(key) as? String ?: defValue
    override fun getStringSet(key:String?,defValues:MutableSet<String>?):MutableSet<String>? =
        (value(key) as? Set<*>)?.map { it as String }?.toMutableSet() ?: defValues?.toMutableSet()
    override fun getInt(key:String?,defValue:Int)=value(key) as? Int ?: defValue
    override fun getLong(key:String?,defValue:Long)=value(key) as? Long ?: defValue
    override fun getFloat(key:String?,defValue:Float)=value(key) as? Float ?: defValue
    override fun getBoolean(key:String?,defValue:Boolean)=value(key) as? Boolean ?: defValue
    override fun contains(key:String?)=value(key)!=null
    override fun registerOnSharedPreferenceChangeListener(listener:SharedPreferences.OnSharedPreferenceChangeListener?) { listener?.let(listeners::add) }
    override fun unregisterOnSharedPreferenceChangeListener(listener:SharedPreferences.OnSharedPreferenceChangeListener?) { listeners.remove(listener) }
    override fun edit():SharedPreferences.Editor {
        session.checkOpen()
        return object:SharedPreferences.Editor {
            private val pending=linkedMapOf<String,Any?>();private var clear=false
            private fun put(key:String?,value:Any?):SharedPreferences.Editor { requireNotNull(key);pending[key]=value;return this }
            override fun putString(key:String?,value:String?)=put(key,value)
            override fun putStringSet(key:String?,values:MutableSet<String>?)=put(key,values?.toSet())
            override fun putInt(key:String?,value:Int)=put(key,value)
            override fun putLong(key:String?,value:Long)=put(key,value)
            override fun putFloat(key:String?,value:Float)=put(key,value)
            override fun putBoolean(key:String?,value:Boolean)=put(key,value)
            override fun remove(key:String?)=put(key,null)
            override fun clear():SharedPreferences.Editor { clear=true;return this }
            override fun commit():Boolean {
                val changed:Set<String>
                synchronized(this@EncryptedPreferences) {
                    session.checkOpen()
                    val next=if(clear) linkedMapOf() else this@EncryptedPreferences.values.toMutableMap()
                    pending.forEach { (key,value)->if(value==null) next.remove(key) else next[key]=value }
                    changed=(next.keys+this@EncryptedPreferences.values.keys).filter { next[it]!=this@EncryptedPreferences.values[it] }.toSet()
                    try {
                        persist(next)
                        this@EncryptedPreferences.values=next
                    } catch(_:Exception) { return false }
                }
                changed.forEach { key->listeners.forEach { it.onSharedPreferenceChanged(this@EncryptedPreferences,key) } }
                return true
            }
            override fun apply() { check(commit()) { "No se pudo conservar la configuración." } }
        }
    }
    fun reload() {
        val changed:Set<String>
        synchronized(this) {
            session.checkOpen();val next=load()
            changed=(values.keys+next.keys).filter {values[it]!=next[it]}.toSet();values=next
        }
        changed.forEach { key -> listeners.forEach {it.onSharedPreferenceChanged(this,key)} }
    }
    @Synchronized fun clearMemory() { values=emptyMap();listeners.clear() }
}
