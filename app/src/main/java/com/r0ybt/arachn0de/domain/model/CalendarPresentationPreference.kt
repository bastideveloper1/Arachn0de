package com.r0ybt.arachn0de.domain.model

/** Optional visual preference carried by the existing private-preferences backup section. */
internal object CalendarPresentationPreference {
    const val NAME="calendar_presentation"
    const val KEY="collapsed"
    fun validate(values:Map<String,Any>) {
        require(KEY !in values || values[KEY] is Boolean) {"Presentación de calendario inválida"}
    }
}
