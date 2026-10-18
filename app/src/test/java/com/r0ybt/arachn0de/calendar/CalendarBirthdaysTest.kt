package com.r0ybt.arachn0de.calendar

import com.r0ybt.arachn0de.data.local.PersonBirthdayEntity
import com.r0ybt.arachn0de.domain.model.CalendarDay
import com.r0ybt.arachn0de.domain.model.Person
import org.junit.Assert.*
import org.junit.Test

class CalendarBirthdaysTest {
    @Test fun annualProjectionIncludesEveryoneComputesOnlyKnownAgesAndDoesNotMoveLeapBirthdays() {
        val people=listOf(Person("a","Ana",null),Person("b","Bruno",null),Person("c","Carla",null))
        val birthdays=listOf(PersonBirthdayEntity("a","1990-07-16"),PersonBirthdayEntity("b","--07-16"),PersonBirthdayEntity("c","2000-02-29"),PersonBirthdayEntity("foreign","1980-07-16"))
        val index=CalendarBirthdays(people,birthdays)
        assertEquals(listOf("a","b"),index.on(CalendarDay(2026,7,16)).map {it.person.id})
        assertEquals(listOf(36,null),index.on(CalendarDay(2026,7,16)).map {it.age})
        assertEquals(37,index.on(CalendarDay(2027,7,16)).first().age)
        assertEquals(listOf("b"),index.on(CalendarDay(1989,7,16)).map {it.person.id})
        assertTrue(index.on(CalendarDay(2027,2,28)).isEmpty());assertTrue(index.on(CalendarDay(2027,3,1)).isEmpty())
        assertTrue(index.on(CalendarDay(2027,2,29)).isEmpty())
        assertEquals(28,index.on(CalendarDay(2028,2,29)).single().age)
        assertTrue(CalendarBirthdays(people,emptyList()).on(CalendarDay(2026,7,16)).isEmpty())
        assertEquals(listOf("b"),CalendarBirthdays(people.filter {it.id!="a"},birthdays).on(CalendarDay(2026,7,16)).map {it.person.id})
    }
}
