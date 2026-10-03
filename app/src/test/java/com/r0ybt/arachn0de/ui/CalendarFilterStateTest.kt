package com.r0ybt.arachn0de.ui

import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.*
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class CalendarFilterStateTest {
    private val today=CalendarDay(2026,10,5)
    private val utc=TimeZone.getTimeZone("UTC")
    @Test fun monthAndWeekNavigationClampAndCrossYearsWhileKeepingFilters() {
        val state=CalendarFilterState(CalendarDay(2026,1,31)).apply { personId="roy"; completion=CompletionFilter.PENDING }
        state.navigate(1,today); assertEquals(CalendarDay(2026,2,28),state.selected)
        state.select(CalendarDay(2026,12,31)); state.navigate(1,today); assertEquals(CalendarDay(2027,1,31),state.selected)
        state.selectView(CalendarView.WEEK,today); state.current(today); state.navigate(-1,today); assertEquals(CalendarDay(2026,9,28),state.selected)
        state.navigate(2,today); assertEquals(CalendarDay(2026,10,12),state.selected)
        assertEquals("roy",state.personId); assertEquals(CompletionFilter.PENDING,state.completion)
        state.current(today); assertEquals(today,state.anchor(today))
    }
    @Test fun everyPresetUsesTheCentralRangeAndRollingTodayWhileManualSelectionStaysFixed() {
        val state=CalendarFilterState(today)
        for (preset in TimeFilter.entries) {
            state.preset(preset,today)
            assertEquals(TemporalRanges.resolve(preset,CalendarDates.labelInstant(today),utc,Locale.FRANCE),state.range(today,utc,Calendar.MONDAY))
        }
        state.selectView(CalendarView.DAY,today)
        val next=TemporalRanges.shift(today,1)
        assertEquals(next,state.anchor(next))
        state.select(today); assertEquals(today,state.anchor(next))
        assertEquals(TemporalRanges.day(today,utc),state.range(next,utc,Calendar.MONDAY))
    }
    @Test fun switchingWeekOrMonthPreservesSelectedDateAndTodayResetsIt() {
        val state=CalendarFilterState(CalendarDay(2026,10,16))
        state.selectView(CalendarView.WEEK,today); assertEquals(CalendarDay(2026,10,16),state.anchor(today))
        assertEquals(TemporalRanges.week(CalendarDay(2026,10,16),utc,Calendar.MONDAY),state.range(today,utc,Calendar.MONDAY))
        state.selectView(CalendarView.MONTH,today); assertEquals(CalendarDay(2026,10,16),state.selected)
        state.selectView(CalendarView.DAY,today); assertEquals(today,state.anchor(today))
    }
    @Test fun clearStateFieldsDoesNotChangeSelectedPeriod() {
        val state=CalendarFilterState(today).apply { preset(TimeFilter.NEXT_WEEK,today); personId="roy"; completion=CompletionFilter.COMPLETED }
        val before=state.range(today,utc,Calendar.MONDAY)
        state.personId=null; state.completion=CompletionFilter.ALL
        assertEquals(before,state.range(today,utc,Calendar.MONDAY))
    }
}
