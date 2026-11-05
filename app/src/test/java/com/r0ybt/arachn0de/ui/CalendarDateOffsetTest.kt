package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.data.local.CalendarEventDates
import com.r0ybt.arachn0de.ui.state.CalendarFilterState
import com.r0ybt.arachn0de.ui.state.CalendarView
import java.time.LocalDate
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class CalendarDateOffsetTest {
    @Test fun manualNavigationAndSavedIntentSurviveRollover() {
        val old=CalendarDay(2026,12,31)
        val next=CalendarDay(2027,1,1)
        val store=ContextCalendarStore()
        val automatic=store.scope("auto",old).apply {urgentOnly=true;favorites=true;dueCondition=AttentionReason.OVERDUE}
        val manual=store.scope("manual",old).apply {selected=old;anchor=CalendarDay(2027,2,28)}
        // Browsing another month without selecting a date must also stop following today.
        store.scope("browse",old).anchor=CalendarDay(2027,3,31)
        with(ContextCalendarStore.Saver) {
            val saved=with(object:androidx.compose.runtime.saveable.SaverScope {
                override fun canBeSaved(value:Any)=true
            }) {save(store)}!!
            val restored=restore(saved)!!
            assertEquals(next,restored.scope("auto",next).anchor)
            assertTrue(restored.scope("auto",next).urgentOnly)
            assertTrue(restored.scope("auto",next).favorites)
            assertEquals(AttentionReason.OVERDUE,restored.scope("auto",next).dueCondition)
            assertNull(restored.scope("auto",next).selected)
            assertEquals(manual.anchor,restored.scope("manual",next).anchor)
            assertEquals(old,restored.scope("manual",next).selected)
            assertEquals(CalendarDay(2027,3,31),restored.scope("browse",next).anchor)
            val legacy=listOf("root","1","1","2026","12","31","","","","true","false")
            assertEquals(old,restore(legacy)!!.scope("root",next).anchor)
            assertFalse(restore(legacy)!!.scope("root",next).conditions.active)
            val previous=listOf("v2","root","1","1","2026","12","31","2026","12","31","true","true","false")
            val previousState=restore(previous)!!.scope("root",next)
            assertFalse(previousState.conditions.active)
            assertTrue(previousState.favorites)
            assertEquals(old,previousState.selected)
        }
        assertEquals(next,store.scope("auto",next).anchor)
        assertTrue(automatic.followsToday)
    }
    @Test fun gridsAndNavigationMatchCivilDates() {
        for (year in listOf(2024,2025,2026,2028,2100)) for (month in 1..12) {
            val expected=LocalDate.of(year,month,1)
            val actual=CalendarMonth(year,month)
            assertEquals(expected.lengthOfMonth(),actual.days)
            for (first in listOf(Calendar.MONDAY,Calendar.SUNDAY)) {
                val cells=actual.cells(first)
                val offset=(expected.dayOfWeek.value%7+1-first+7)%7
                assertEquals(offset,cells.indexOfFirst {it!=null})
                assertEquals(0,cells.size%7)
                assertEquals((1..actual.days).map {CalendarDay(year,month,it)},cells.filterNotNull())
            }
            for (day in 28..actual.days) for (delta in listOf(-1,1)) {
                val date=expected.withDayOfMonth(day)
                val state=CalendarFilterState(CalendarDay(year,month,day))
                state.view=CalendarView.MONTH
                state.navigate(delta,state.selected)
                val next=date.plusMonths(delta.toLong())
                assertEquals(CalendarDay(next.year,next.monthValue,next.dayOfMonth),state.selected)
            }
        }
    }

    @Test fun localSelectionMatchesTasksAndEventDates() {
        for (zoneId in listOf("UTC","America/Santiago","Pacific/Kiritimati","America/New_York")) {
            val zone=TimeZone.getTimeZone(zoneId)
            // Chilean DST transitions, month ends, leap day and New Year.
            for (date in listOf("2024-02-29","2026-02-28","2026-04-30","2026-05-31",
                "2026-04-04","2026-04-05","2026-09-06","2026-12-31","2027-01-01")) {
                val day=CalendarEventDates.day(date)
                val range=TemporalRanges.day(day,zone)
                for (instant in listOf(range.startInclusive,range.startInclusive+12*3_600_000L,range.endExclusive-1)) {
                    assertEquals(day,CalendarDates.localDay(instant,zone))
                    val node=Node("t","p",null,"Task","",false,0,1,1,false,dueAt=instant)
                    val tree=NodeTreeSnapshot(listOf(node))
                    assertEquals(listOf(node),ContextCalendarIndex(tree,zone).tasksOn(day))
                    assertEquals(listOf(node),FilteredCalendar(CalendarSnapshot(tree,zone),NodeFilter(range),emptyMap()).tasksOn(day))
                }
                assertNotEquals(day,CalendarDates.localDay(range.startInclusive-1,zone))
                assertNotEquals(day,CalendarDates.localDay(range.endExclusive,zone))
                assertEquals(date,CalendarEventDates.format(day))
                val utcDay=CalendarDates.labelInstant(day)
                val local=localTaskInstant(utcDay,12,15,zone)
                assertEquals(day,CalendarDates.localDay(local,zone))
            }
        }
    }
}
