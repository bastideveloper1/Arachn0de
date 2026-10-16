package com.r0ybt.arachn0de.security

internal object ScreenCapturePreference {
    const val NAME="security_preferences"
    const val KEY="captures_until_reenabled"
    fun validate(payload:String) {
        val values=EncryptedPreferences.decode(payload)
        AutoLockPreference.validate(values)
        require(KEY !in values || values[KEY] is Boolean) {"Preferencia de capturas inválida."}
    }
}

/** Reuses the existing duration preference; -1 is session-only manual locking. */
internal object AutoLockPreference {
    const val KEY="background_timeout"
    const val MANUAL=-1L
    val options=listOf(0L to "Inmediato",30000L to "30 segundos",60000L to "1 minuto",300000L to "5 minutos",MANUAL to "Hasta bloquear manualmente")
    fun read(preferences:android.content.SharedPreferences):Long {
        val value=preferences.getLong(KEY,30000L)
        return value.takeIf {candidate->options.any {it.first==candidate}} ?: 0L
    }
    fun validate(values:Map<String,Any>) {
        require(KEY !in values || (values[KEY] is Long && options.any {it.first==values[KEY]})) {"Preferencia de bloqueo inválida."}
    }
}
