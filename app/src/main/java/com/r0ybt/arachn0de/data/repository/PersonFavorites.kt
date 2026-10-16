package com.r0ybt.arachn0de.data.repository

import com.r0ybt.arachn0de.security.EncryptedPreferences

/** Antisocial-specific ids in the existing encrypted/portable preference table. */
internal object PersonFavorites {
    const val NAME="antisocial_favorites"
    fun encode(ids:Set<String>)=EncryptedPreferences.encode(mapOf("version" to 1,"people" to ids))
    fun decode(payload:String):Set<String> {
        val values=EncryptedPreferences.decode(payload)
        require(values.keys==setOf("version","people") && values["version"]==1) {"Favoritos de Personas incompatibles."}
        val ids=(values["people"] as? Set<*>)?.map {it as? String ?: error("Favorito inválido.")}?.toSet() ?: error("Favoritos inválidos.")
        require(ids.all {it.isNotBlank() && it.length<=200}) {"Identificador de Persona inválido."}
        return ids
    }
}
