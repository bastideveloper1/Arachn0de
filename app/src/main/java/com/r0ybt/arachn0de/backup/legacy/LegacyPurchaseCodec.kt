package com.r0ybt.arachn0de.backup.legacy

import com.r0ybt.arachn0de.security.EncryptedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Read-only historical payload; no notification access or task creation. */
internal data class Purchase(val merchant:String,val amount:Long,val receivedAt:Long)
internal data class PurchaseSuggestion(val id:String,val fingerprint:String,val purchase:Purchase)
internal data class PurchaseState(val enabled:Boolean=false,val vault:String?=null,val pending:List<PurchaseSuggestion> = emptyList(),val seen:Map<String,Long> = emptyMap(),val dismissed:Set<String> = emptySet())

internal object PurchaseCodec {
    const val NAME="purchase_detection"
    fun encode(state:PurchaseState):String {
        val json=JSONObject().put("version",1).put("enabled",state.enabled).put("vault",state.vault ?: JSONObject.NULL)
            .put("pending",JSONArray().apply {state.pending.forEach {e->put(JSONObject().put("id",e.id).put("fingerprint",e.fingerprint).put("merchant",e.purchase.merchant).put("amount",e.purchase.amount).put("receivedAt",e.purchase.receivedAt))}})
            .put("dismissed",JSONArray(state.dismissed.toList())).put("seen",JSONObject().apply {state.seen.forEach {(key,time)->put(key,time)}})
        return EncryptedPreferences.encode(mapOf("state" to json.toString()))
    }
    fun decode(payload:String):PurchaseState {
        require(payload.toByteArray().size<=256*1024)
        val values=EncryptedPreferences.decode(payload);require(values.keys==setOf("state"))
        val j=JSONObject(values["state"] as String)
        require(j.keys().asSequence().toSet()==setOf("version","enabled","vault","pending","seen","dismissed") && j.get("version")==1 && j.get("enabled") is Boolean)
        val vault=if(j.isNull("vault"))null else j.getString("vault").also {require(UUID.fromString(it).toString()==it)}
        val a=j.getJSONArray("pending");require(a.length()<=100)
        val pending=(0 until a.length()).map {i->val r=a.getJSONObject(i)
            require(r.keys().asSequence().toSet()==setOf("id","fingerprint","merchant","amount","receivedAt"))
            val id=r.getString("id").also {require(UUID.fromString(it).toString()==it)}
            val fingerprint=r.getString("fingerprint").also {require(it.matches(Regex("[0-9a-f]{64}")))}
            val merchant=r.getString("merchant").also {require(it.isNotBlank() && it.length<=100 && it.none {c->c.isISOControl()})}
            fun positive(key:String):Long {val n=r.get(key);require(n is Int || n is Long);return (n as Number).toLong().also {require(it>0)}}
            PurchaseSuggestion(id,fingerprint,Purchase(merchant,positive("amount"),positive("receivedAt")))
        }
        val seenJson=j.getJSONObject("seen");require(seenJson.length()<=2000)
        val seen=seenJson.keys().asSequence().toSet().associateWith {key->require(key.matches(Regex("[0-9a-f]{64}")));val n=seenJson.get(key);require(n is Int || n is Long);(n as Number).toLong().also {require(it>0)}}
        require(pending.map {it.id}.distinct().size==pending.size && pending.map {it.fingerprint}.distinct().size==pending.size)
        require(pending.all {seen[it.fingerprint]==it.purchase.receivedAt})
        require((!j.getBoolean("enabled") && pending.isEmpty()) || vault!=null)
        val discarded=j.getJSONArray("dismissed");require(discarded.length()<=2000)
        val dismissed=(0 until discarded.length()).map {discarded.getString(it).also {id->require(UUID.fromString(id).toString()==id)}}.toSet()
        require(pending.none {it.id in dismissed})
        return PurchaseState(j.getBoolean("enabled"),vault,pending,seen,dismissed)
    }
}
