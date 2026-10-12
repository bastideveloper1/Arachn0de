package com.r0ybt.arachn0de.metro

import android.content.SharedPreferences

internal data class MetroHoursPeriod(val open:Int,val close:Int) {
    init {require(open in 0..1439 && close in 0..1439 && open<close) {"La apertura debe ser anterior al cierre en el mismo día."}}
}
internal data class MetroHoursConfig(
    val weekday:MetroHoursPeriod=MetroHoursPeriod(360,1380),
    val saturday:MetroHoursPeriod=MetroHoursPeriod(390,1380),
    val sunday:MetroHoursPeriod=MetroHoursPeriod(450,1380)
) {
    fun encode()=listOf(weekday,saturday,sunday).flatMap {listOf(it.open,it.close)}.joinToString(",")
    companion object {
        const val NAME="metro_hours"
        fun decode(value:String):MetroHoursConfig {
            val parts=value.split(',').map {it.toInt()};require(parts.size==6)
            return MetroHoursConfig(MetroHoursPeriod(parts[0],parts[1]),MetroHoursPeriod(parts[2],parts[3]),MetroHoursPeriod(parts[4],parts[5]))
        }
        fun read(preferences:SharedPreferences)=preferences.getString("hours",null)?.let(::decode) ?: MetroHoursConfig()
        fun validatePayload(payload:String) {
            val values=com.r0ybt.arachn0de.security.EncryptedPreferences.decode(payload)
            require(values.keys.all {it=="hours"})
            values["hours"]?.let {require(it is String);decode(it)}
        }
    }
}
