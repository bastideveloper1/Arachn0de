package com.r0ybt.arachn0de.calendar

import com.r0ybt.arachn0de.data.local.PersonBirthdayEntity
import com.r0ybt.arachn0de.domain.model.CalendarDay
import com.r0ybt.arachn0de.domain.model.Person
import java.time.LocalDate
import java.time.MonthDay

internal data class CalendarBirthday(val person:Person,val age:Int?)
/** Read-only projection of existing records; no stored anniversaries or synthetic events. */
internal class CalendarBirthdays(people:List<Person>,birthdays:List<PersonBirthdayEntity>) {
    private data class Birth(val person:Person,val date:MonthDay,val year:Int?)
    private val peopleById=people.associateBy {it.id}
    private val byDay=birthdays.mapNotNull {row->
        val person=peopleById[row.personId] ?: return@mapNotNull null
        val full=runCatching {LocalDate.parse(row.date)}.getOrNull()
        val date=full?.let {MonthDay.from(it)} ?: runCatching {MonthDay.parse(row.date)}.getOrNull() ?: return@mapNotNull null
        Birth(person,date,full?.year)
    }.groupBy {it.date}
    fun containsPerson(id:String)=id in peopleById
    fun on(day:CalendarDay):List<CalendarBirthday> {
        val date=runCatching {LocalDate.of(day.year,day.month,day.day)}.getOrNull() ?: return emptyList()
        return byDay[MonthDay.from(date)].orEmpty().filter {it.year==null || day.year>=it.year}
            .map {CalendarBirthday(it.person,it.year?.let {year->day.year-year})}
            .sortedWith(compareBy<CalendarBirthday> {it.person.name}.thenBy {it.person.id})
    }
}
