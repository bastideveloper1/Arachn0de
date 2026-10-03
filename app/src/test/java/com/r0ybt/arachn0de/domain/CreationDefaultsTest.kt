package com.r0ybt.arachn0de.domain

import com.r0ybt.arachn0de.domain.defaults.*
import com.r0ybt.arachn0de.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone
import java.util.Calendar
import java.util.Locale

class CreationDefaultsTest {
    private fun node(id:String,parent:String?,project:String="p")=Node(id,project,parent,id,"",false,0,0,0,true)
    private fun instant(day:String,minute:Int=720,zone:String="UTC")=RecurrenceSchedule.timestamp(RecurrenceSchedule.parse(day),zone,minute)
    private fun result(today:String,kind:DefaultDateKind,number:Int=0,due:Boolean=true)=RecurrenceSchedule.format(RecurrenceSchedule.localDay(DefaultDateResolver.instant(DefaultDate(kind,number),DefaultTime.Unspecified,instant(today),TimeZone.getTimeZone("UTC"),due)!!,"UTC"))
    @Test fun propertyPrecedenceIncludesCurrentLayerAndExplicitEmptyOffNone() {
        val layers=mapOf("a" to CreationDefaults(currency=DefaultValue.Own("CLP")),"b" to CreationDefaults(priority=DefaultValue.Own(Priority.NONE),obligation=DefaultValue.Own(false),tags=DefaultValue.Own(emptySet()),start=DefaultValue.Own(DefaultDate())))
        val resolved=CreationDefaultsResolver.resolve("p","b",mapOf("a" to node("a",null),"b" to node("b","a")),
            CreationDefaults(currency=DefaultValue.Own("USD"),priority=DefaultValue.Own(Priority.HIGH),tags=DefaultValue.Own(setOf("global")),obligation=DefaultValue.Own(true),start=DefaultValue.Own(DefaultDate(DefaultDateKind.TODAY))),
            CreationDefaults(people=DefaultValue.Own(setOf("Ana"))),layers)
        assertEquals("CLP",resolved.currency);assertEquals(Priority.NONE,resolved.priority);assertFalse(resolved.obligation)
        assertTrue(resolved.tags.isEmpty());assertEquals(setOf("Ana"),resolved.people);assertEquals(DefaultDate(),resolved.start)
    }
    @Test fun defaultsWithoutConfigurationMatchExistingCreation() { assertEquals(EffectiveCreationDefaults(),CreationDefaults().applyTo(EffectiveCreationDefaults())) }
    @Test fun twelveThousandAncestorsUseIterativeTraversal() {
        val nodes=(0 until 12000).associate { "$it" to node("$it",if(it==0) null else "${it-1}") }
        assertEquals("EUR",CreationDefaultsResolver.resolve("p","11999",nodes,CreationDefaults(),CreationDefaults(),mapOf("0" to CreationDefaults(currency=DefaultValue.Own("EUR")))).currency)
    }
    @Test fun corruptedCyclesMissingAncestorsAndForeignProjectsFail() {
        for(nodes in listOf(mapOf("a" to node("a","a")),mapOf("a" to node("a","missing")),mapOf("a" to node("a",null,"q"))))
            assertTrue(runCatching { CreationDefaultsResolver.resolve("p","a",nodes,CreationDefaults(),CreationDefaults(),emptyMap()) }.isFailure)
    }
    @Test fun monthDayClampsIncludingLeapYear() {
        assertEquals("2027-02-28",result("2027-02-10",DefaultDateKind.DAY_OF_MONTH,31))
        assertEquals("2028-02-29",result("2028-02-10",DefaultDateKind.DAY_OF_MONTH,31))
        assertEquals("2026-04-30",result("2026-03-10",DefaultDateKind.DAY_NEXT_MONTH,31))
    }
    @Test fun dueDayUsesNextCivilOccurrenceButSameDayStaysEvenAfterChosenTime() {
        assertEquals("2026-10-15",result("2026-10-10",DefaultDateKind.DAY_OF_MONTH,15))
        assertEquals("2026-10-15",result("2026-10-15",DefaultDateKind.DAY_OF_MONTH,15))
        assertEquals("2026-11-15",result("2026-10-20",DefaultDateKind.DAY_OF_MONTH,15))
        assertEquals("2026-10-15",result("2026-10-20",DefaultDateKind.DAY_OF_MONTH,15,false))
    }
    @Test fun firstMondayDistinguishesStartAnchorFromFutureDue() {
        assertEquals("2026-10-05",result("2026-10-01",DefaultDateKind.FIRST_MONDAY))
        assertEquals("2026-10-05",result("2026-10-05",DefaultDateKind.FIRST_MONDAY))
        assertEquals("2026-11-02",result("2026-10-06",DefaultDateKind.FIRST_MONDAY))
        assertEquals("2026-10-05",result("2026-10-20",DefaultDateKind.FIRST_MONDAY,due=false))
        assertEquals("2026-11-02",result("2026-10-01",DefaultDateKind.FIRST_MONDAY_NEXT))
    }
    @Test fun tomorrowRelativeAndYearBoundaryUseCalendarDates() {
        assertEquals("2027-01-01",result("2026-12-31",DefaultDateKind.TOMORROW))
        assertEquals("2027-01-03",result("2026-12-31",DefaultDateKind.IN_DAYS,3))
        assertEquals("2027-01-01",result("2026-12-15",DefaultDateKind.FIRST_DAY_NEXT))
        assertEquals("2026-12-01",result("2026-12-15",DefaultDateKind.FIRST_DAY))
    }
    @Test fun rulesAreEvaluatedAtCreationAndNoneIgnoresLatentHour() {
        assertEquals("2026-10-03",result("2026-10-03",DefaultDateKind.TODAY));assertEquals("2026-10-20",result("2026-10-20",DefaultDateKind.TODAY))
        assertNull(DefaultDateResolver.instant(DefaultDate(),DefaultTime.Minute(1320),0,TimeZone.getTimeZone("UTC"),true))
    }
    @Test fun dstTomorrowIsNotTwentyFourHoursAndGapAdvances() {
        val zone=TimeZone.getTimeZone("America/New_York");val now=instant("2026-03-07",720,zone.id)
        val next=DefaultDateResolver.instant(DefaultDate(DefaultDateKind.TOMORROW),DefaultTime.Minute(720),now,zone,true)!!
        assertEquals(23*3600000L,next-now)
        val gap=DefaultDateResolver.instant(DefaultDate(DefaultDateKind.TOMORROW),DefaultTime.Minute(150),now,zone,true)!!
        val c=Calendar.getInstance(zone).apply { timeInMillis=gap };assertEquals(3,c.get(Calendar.HOUR_OF_DAY));assertEquals(30,c.get(Calendar.MINUTE))
    }
    @Test fun repeatedDstHourUsesLaterOccurrenceAndZoneIgnoresDeviceLocale() {
        val previous=Locale.getDefault();Locale.setDefault(Locale("ar","EG"))
        try {
            val zone=TimeZone.getTimeZone("America/New_York")
            val value=DefaultDateResolver.instant(DefaultDate(DefaultDateKind.TODAY),DefaultTime.Minute(90),instant("2026-11-01",720,zone.id),zone,true)
            assertEquals(instant("2026-11-01",390,"UTC"),value)
            assertEquals(instant("2026-10-03",0,"America/Santiago"),DefaultDateResolver.instant(DefaultDate(DefaultDateKind.TODAY),DefaultTime.Unspecified,instant("2026-10-03",720,"America/Santiago"),TimeZone.getTimeZone("America/Santiago"),false))
        } finally { Locale.setDefault(previous) }
    }
    @Test fun invalidRuleArgumentsAndCurrencyAreRejected() {
        for(rule in listOf({ DefaultDate(DefaultDateKind.DAY_OF_MONTH,32) },{ DefaultDate(DefaultDateKind.IN_DAYS,0) },{ DefaultDate(DefaultDateKind.TODAY,2) },{ DefaultTime.Minute(1440) },{ CreationDefaults(currency=DefaultValue.Own("GBP")) })) assertTrue(runCatching(rule).isFailure)
    }
}
