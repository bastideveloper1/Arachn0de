package com.r0ybt.arachn0de.data.local

import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Canonical ISO full date or ISO month/day. A missing year is never replaced with a sentinel. */
internal data class PersonBirthDate(val day: MonthDay, val year: Int?) {
    fun format(locale: Locale): String = if (year == null)
        day.format(DateTimeFormatter.ofPattern("d 'de' MMMM", locale))
    else day.atYear(year).format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu", locale))

    companion object {
        fun parse(value: String): PersonBirthDate {
            if (value.startsWith("--")) {
                val day = MonthDay.parse(value)
                require(day.toString() == value)
                return PersonBirthDate(day, null)
            }
            val date = LocalDate.parse(value)
            require(date.year in 1..9999 && date.toString() == value)
            return PersonBirthDate(MonthDay.from(date), date.year)
        }
        fun valid(value: String, today: LocalDate = LocalDate.now()): Boolean = value.isEmpty() ||
            runCatching { parse(value).let { it.year == null || !it.day.atYear(it.year).isAfter(today) } }.getOrDefault(false)
    }
}
