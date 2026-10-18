package com.r0ybt.arachn0de.calendar

import android.content.Context
import com.r0ybt.arachn0de.domain.model.CalendarDay
import com.r0ybt.arachn0de.domain.model.CalendarDates
import com.r0ybt.arachn0de.domain.model.TemporalRanges
import org.json.JSONObject
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

internal enum class SpecialDateKind(val label:String) {HOLIDAY("Feriado nacional"),SAINT("Santoral · tradición católica chilena"),OBSERVANCE("Fecha conmemorativa")}
internal data class SpecialDateSource(val name:String,val url:String)
internal data class SpecialDateEntry(val id:String,val kind:SpecialDateKind,val name:String,val month:Int,val day:Int,val year:Int?,val rule:String,val description:String,val source:SpecialDateSource)

/** Read-only APK catalog. Informational dates never become personal events or notifications. */
internal class CalendarSpecialDates private constructor(val holidayYears:Set<Int>,val verifiedOn:String,val entries:List<SpecialDateEntry>) {
    private val holidays=holidayYears.flatMap {year->entries.filter {it.kind==SpecialDateKind.HOLIDAY && (it.year==null || it.year==year)}.map {entry->
        transferred(CalendarDay(year,entry.month,entry.day),entry.rule) to entry
    }}.groupBy({it.first},{it.second})
    private val annual=entries.filter {it.kind!=SpecialDateKind.HOLIDAY}.groupBy {it.month to it.day}
    val saintDays=entries.filter {it.kind==SpecialDateKind.SAINT}.map {it.month to it.day}.distinct().size
    val commemorations=entries.count {it.kind==SpecialDateKind.OBSERVANCE}
    fun on(day:CalendarDay,settings:CalendarSpecialVisibility):List<SpecialDateEntry> =
        (if(settings.holidays) holidays[day].orEmpty() else emptyList())+
            annual[day.month to day.day].orEmpty().filter {if(it.kind==SpecialDateKind.SAINT) settings.saints else settings.commemorations}
    companion object {
        fun load(context:Context)=decode(context.assets.open("calendar_special_dates.json").bufferedReader(Charsets.UTF_8).use {it.readText()})
        fun decode(text:String):CalendarSpecialDates {
            val root=JSONObject(text);require(root.getInt("version")==1)
            val years=root.getJSONArray("holidayYears").let {a->(0 until a.length()).map {a.getInt(it).also {y->require(y in 1900..9999)}}.toSet()}
            require(years.isNotEmpty())
            val sources=root.getJSONObject("sources").let {s->s.keys().asSequence().associateWith {key->s.getJSONObject(key).let {SpecialDateSource(it.getString("name"),it.getString("url").also {url->require(url.startsWith("https://"))})}}}
            val entries=root.getJSONArray("entries").let {a->(0 until a.length()).map {index->
                val row=a.getJSONObject(index);val date=row.getString("date")
                require(date.matches(Regex("(?:[0-9]{4}-)?[0-9]{2}-[0-9]{2}")))
                val parts=date.split('-').map(String::toInt);val year=if(parts.size==3) parts[0] else null
                val month=parts[parts.size-2];val day=parts.last()
                GregorianCalendar(TimeZone.getTimeZone("UTC"),Locale.ROOT).apply {isLenient=false;clear();set(year ?: 2000,month-1,day);timeInMillis}
                val kind=SpecialDateKind.valueOf(row.getString("kind"));val rule=row.getString("rule")
                require(rule in setOf("fixed","monday","evangelical"))
                require(kind==SpecialDateKind.HOLIDAY || (year==null && rule=="fixed"))
                require(year==null || year in years)
                SpecialDateEntry(row.getString("id"),kind,row.getString("name"),month,day,year,rule,row.getString("description"),sources.getValue(row.getString("source")))
            }}
            require(entries.map {it.id}.distinct().size==entries.size)
            return CalendarSpecialDates(years,root.getString("verifiedOn"),entries)
        }
        /** Laws 19.668 and 20.299. Used only inside the catalog's explicit year coverage. */
        internal fun transferred(day:CalendarDay,rule:String):CalendarDay {
            val weekday=GregorianCalendar(TimeZone.getTimeZone("UTC"),Locale.ROOT).apply {timeInMillis=CalendarDates.labelInstant(day)}.get(Calendar.DAY_OF_WEEK)
            val shift=when(rule) {
                "monday"->when(weekday) {Calendar.TUESDAY->-1;Calendar.WEDNESDAY->-2;Calendar.THURSDAY->-3;Calendar.FRIDAY->3;else->0}
                "evangelical"->when(weekday) {Calendar.TUESDAY->-4;Calendar.WEDNESDAY->2;else->0}
                else->0
            }
            return TemporalRanges.shift(day,shift)
        }
    }
}
