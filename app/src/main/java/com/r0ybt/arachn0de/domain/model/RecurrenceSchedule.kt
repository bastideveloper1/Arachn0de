package com.r0ybt.arachn0de.domain.model

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** Calendar dates stored as UTC epoch days; occurrences use the rule's fixed device time zone. */
enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }
enum class RecurrenceStatus { ACTIVE, PAUSED, FINISHED }

object RecurrenceSchedule {
    private const val DAY = 86_400_000L
    private fun calendar(day: Long) = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
        isLenient = false
        timeInMillis = Math.multiplyExact(day, DAY)
        require(get(Calendar.YEAR) >= 1 && get(Calendar.ERA) == GregorianCalendar.AD)
    }
    fun parse(text: String): Long {
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(text)) { "Usa AAAA-MM-DD." }
        val parts = text.split('-').map(String::toInt)
        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear(); isLenient = false; set(parts[0], parts[1] - 1, parts[2])
        }
        return (cal.timeInMillis / DAY).also { calendar(it) }
    }
    fun format(day: Long): String = calendar(day).let {
        String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
    }
    fun localDay(now: Long, zone: String): Long {
        require(zone in TimeZone.getAvailableIDs()) { "Zona horaria inválida." }
        val cal = GregorianCalendar(TimeZone.getTimeZone(zone)).apply { timeInMillis = now }
        return parse(String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)))
    }
    /** Always calculate from the original anchor: Jan 31 → Feb 28 → Mar 31. */
    fun date(start: Long, frequency: RecurrenceFrequency, interval: Int, index: Long): Long {
        require(interval in 1..10000 && index >= 0)
        val step = Math.multiplyExact(index, interval.toLong())
        val cal = calendar(start)
        when (frequency) {
            RecurrenceFrequency.DAILY -> return Math.addExact(start, step).also { calendar(it) }
            RecurrenceFrequency.WEEKLY -> return Math.addExact(start, Math.multiplyExact(step, 7)).also { calendar(it) }
            RecurrenceFrequency.MONTHLY, RecurrenceFrequency.YEARLY -> {
                require(step <= Int.MAX_VALUE)
                val day = cal.get(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(if (frequency == RecurrenceFrequency.MONTHLY) Calendar.MONTH else Calendar.YEAR, step.toInt())
                cal.set(Calendar.DAY_OF_MONTH, minOf(day, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
                return (cal.timeInMillis / DAY).also { calendar(it) }
            }
        }
    }
    /** First scheduled date on or after resume; no loop over skipped pause periods. */
    fun indexOnOrAfter(start: Long, frequency: RecurrenceFrequency, interval: Int, day: Long): Long {
        if (day <= start) return 0
        val anchor = calendar(start); val target = calendar(day)
        val units = when (frequency) {
            RecurrenceFrequency.DAILY -> day - start
            RecurrenceFrequency.WEEKLY -> (day - start) / 7
            RecurrenceFrequency.MONTHLY -> (target.get(Calendar.YEAR) - anchor.get(Calendar.YEAR)).toLong() * 12 + target.get(Calendar.MONTH) - anchor.get(Calendar.MONTH)
            RecurrenceFrequency.YEARLY -> (target.get(Calendar.YEAR) - anchor.get(Calendar.YEAR)).toLong()
        }
        var index = units / interval
        if (date(start, frequency, interval, index) < day) index++
        return index
    }
    fun timestamp(day: Long, zone: String, minute: Int): Long {
        require(minute in 0..1439 && zone in TimeZone.getAvailableIDs())
        val source = calendar(day)
        return GregorianCalendar(TimeZone.getTimeZone(zone)).apply {
            clear(); set(source.get(Calendar.YEAR), source.get(Calendar.MONTH), source.get(Calendar.DAY_OF_MONTH), minute / 60, minute % 60)
        }.timeInMillis
    }
}
