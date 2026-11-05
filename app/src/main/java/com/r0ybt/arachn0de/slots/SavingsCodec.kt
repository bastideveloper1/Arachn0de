package com.r0ybt.arachn0de.slots

import com.r0ybt.arachn0de.security.EncryptedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Same SQLCipher/private-preferences snapshot and restore as the other offline modules. */
internal object SavingsCodec {
    const val NAME="slot_savings"
    const val MAX_BYTES=8*1024*1024
    fun encode(state:SavingsState):String {
        val configs=JSONArray().apply {state.configs.forEach {c->put(JSONObject().put("id",c.id).put("root",c.root).put("account",c.account).put("price",c.table.priceUnits)
            .put("prizes",JSONArray(c.table.prizeUnits)).put("tickets",JSONArray(c.table.winningTickets)).put("saving",c.savingsBps).put("target",c.targetBps).put("from",c.fromEvent).put("pack",c.table.creditsPerPack).put("denominator",c.table.denominator))}}
        val events=JSONArray().apply {state.events.forEach {e->put(JSONObject().put("id",e.id).put("kind",e.kind).put("config",e.config).put("at",e.at).put("units",e.units).put("count",e.count).put("result",JSONArray(e.result)).put("reference",e.reference ?: JSONObject.NULL))}}
        return EncryptedPreferences.encode(mapOf("state" to JSONObject().put("version",4).put("configs",configs).put("events",events).put("animation",state.animation ?: JSONObject.NULL).toString()))
    }
    fun decode(payload:String):SavingsState {
        require(payload.toByteArray().size<=MAX_BYTES)
        val values=EncryptedPreferences.decode(payload);require(values.keys==setOf("state"))
        val j=JSONObject(values["state"] as String);keys(j,"version","configs","events","animation");require(j.get("version") in setOf(1,2,3,4))
        val cs=j.getJSONArray("configs");val es=j.getJSONArray("events");require(cs.length()<=10000 && es.length()<=100000)
        val configs=(0 until cs.length()).map {i->val c=cs.getJSONObject(i);keys(c,*(listOf("id","root","account","price","prizes","tickets","saving","target","from")+if(j.get("version")==4)listOf("pack","denominator") else emptyList()).toTypedArray())
            val prizes=c.getJSONArray("prizes");val tickets=c.getJSONArray("tickets");require(prizes.length() in 7..8 && tickets.length()==prizes.length())
            SavingsConfig(c.getString("id"),c.getString("root"),c.getString("account"),SavingsPayTable(integer(c,"price"),(0 until prizes.length()).map {number(prizes.get(it))},(0 until tickets.length()).map {Math.toIntExact(number(tickets.get(it)))},if(j.get("version")==4)Math.toIntExact(integer(c,"pack")) else 1,if(j.get("version")==4)Math.toIntExact(integer(c,"denominator")) else 10000),Math.toIntExact(integer(c,"saving")),Math.toIntExact(integer(c,"target")),Math.toIntExact(integer(c,"from")))
        }
        val events=(0 until es.length()).map {i->val e=es.getJSONObject(i);keys(e,"id","kind","config","at","units","count","result","reference")
            val result=e.getJSONArray("result");require(result.length()<=3)
            SavingsEvent(e.getString("id"),e.getString("kind"),e.getString("config"),integer(e,"at"),integer(e,"units"),integer(e,"count"),(0 until result.length()).map {Math.toIntExact(number(result.get(it)))},if(e.isNull("reference"))null else e.getString("reference"))
        }
        require(j.get("version") in setOf(2,3,4) || events.none {it.kind=="ASSIGN"})
        require(j.get("version") in setOf(3,4) || configs.all {it.table.symbolCount==7})
        return SavingsState(configs,events,if(j.isNull("animation"))null else j.getString("animation")).also {it.book()}
    }
    private fun keys(j:JSONObject,vararg names:String) {require(j.keys().asSequence().toSet()==names.toSet())}
    private fun integer(j:JSONObject,key:String)=number(j.get(key))
    private fun number(n:Any):Long {require(n is Int || n is Long);return (n as Number).toLong()}
}
