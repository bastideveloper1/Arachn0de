package com.r0ybt.arachn0de.calendar

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.r0ybt.arachn0de.security.EncryptedPreferences

internal data class CalendarSpecialVisibility(val holidays:Boolean=true,val saints:Boolean=false,val commemorations:Boolean=false,val birthdays:Boolean=true)
internal class CalendarSpecialPreferences(private val storage:SharedPreferences) {
    private fun read()=decodeValues(storage.all)
    var settings by mutableStateOf(read())
        private set
    private val listener=SharedPreferences.OnSharedPreferenceChangeListener {_,_->settings=read()}
    fun observe() {storage.registerOnSharedPreferenceChangeListener(listener);settings=read()}
    fun unobserve() {storage.unregisterOnSharedPreferenceChangeListener(listener)}
    fun set(value:CalendarSpecialVisibility):Boolean {
        val saved=storage.edit().putBoolean("holidays",value.holidays).putBoolean("saints",value.saints).putBoolean("commemorations",value.commemorations).putBoolean("birthdays",value.birthdays).commit()
        if(saved) settings=value
        return saved
    }
    companion object {
        const val NAME="calendar_special_preferences"
        private fun decodeValues(values:Map<String,*>):CalendarSpecialVisibility {
            require(values.keys.all {it in setOf("holidays","saints","commemorations","birthdays")})
            require(values.values.all {it is Boolean}) {"Preferencias de fechas informativas inválidas."}
            return CalendarSpecialVisibility(values["holidays"] as? Boolean ?: true,values["saints"] as? Boolean ?: false,values["commemorations"] as? Boolean ?: false,values["birthdays"] as? Boolean ?: true)
        }
        fun validatePayload(payload:String) {decodeValues(EncryptedPreferences.decode(payload))}
    }
}
