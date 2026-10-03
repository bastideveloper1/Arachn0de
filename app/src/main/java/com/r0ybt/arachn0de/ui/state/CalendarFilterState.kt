package com.r0ybt.arachn0de.ui.state

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import com.r0ybt.arachn0de.domain.model.*
import java.util.TimeZone

internal enum class CalendarView { DAY, WEEK, MONTH }

/** Civil selection and filter IDs only; no Nodes or database entities in Bundle. */
internal class CalendarFilterState(initialDay: CalendarDay) {
    var selected by mutableStateOf(initialDay)
    var view by mutableStateOf(CalendarView.MONTH)
    var timePreset by mutableStateOf("SELECTED")
    var personId by mutableStateOf<String?>(null)
    var completion by mutableStateOf(CompletionFilter.ALL)
    var showFilters by mutableStateOf(false)

    fun anchor(today: CalendarDay): CalendarDay = when (timePreset) {
        "TODAY", "THIS_WEEK", "THIS_MONTH" -> today
        "TOMORROW" -> TemporalRanges.shift(today, 1)
        "NEXT_WEEK" -> TemporalRanges.shift(today, 7)
        "PREVIOUS_MONTH" -> today.calendarMonth.shifted(-1).selecting(today.day)
        "NEXT_MONTH" -> today.calendarMonth.shifted(1).selecting(today.day)
        else -> selected
    }
    fun range(today: CalendarDay, zone: TimeZone, firstDayOfWeek: Int): TemporalRange? {
        if (timePreset == "ALL") return null
        val anchor = anchor(today)
        return when (view) {
            CalendarView.DAY -> TemporalRanges.day(anchor, zone)
            CalendarView.WEEK -> TemporalRanges.week(anchor, zone, firstDayOfWeek)
            CalendarView.MONTH -> TemporalRanges.month(anchor.calendarMonth, zone)
        }
    }
    fun selectView(value: CalendarView, today: CalendarDay) {
        val day = if (value == CalendarView.DAY) today else anchor(today)
        view = value; selected = day
        timePreset = if (value == CalendarView.DAY) "TODAY" else "SELECTED"
    }
    fun current(today: CalendarDay) {
        selected = today
        timePreset = when (view) { CalendarView.DAY -> "TODAY"; CalendarView.WEEK -> "THIS_WEEK"; CalendarView.MONTH -> "THIS_MONTH" }
    }
    fun preset(value: TimeFilter, today: CalendarDay) {
        timePreset = value.name; selected = today
        view = when (value) {
            TimeFilter.TODAY, TimeFilter.TOMORROW -> CalendarView.DAY
            TimeFilter.THIS_WEEK, TimeFilter.NEXT_WEEK -> CalendarView.WEEK
            TimeFilter.THIS_MONTH, TimeFilter.PREVIOUS_MONTH, TimeFilter.NEXT_MONTH -> CalendarView.MONTH
            TimeFilter.ALL -> view
        }
    }
    fun select(day: CalendarDay) { selected = day; timePreset = "SELECTED" }
    fun navigate(offset: Int, today: CalendarDay) {
        val anchor = anchor(today)
        select(when (view) {
            CalendarView.DAY -> TemporalRanges.shift(anchor, offset)
            CalendarView.WEEK -> TemporalRanges.shift(anchor, offset * 7)
            CalendarView.MONTH -> anchor.calendarMonth.shifted(offset).selecting(anchor.day)
        })
    }
    companion object {
        val Saver = listSaver<CalendarFilterState, String>(
            save = { listOf(it.selected.year.toString(), it.selected.month.toString(), it.selected.day.toString(), it.view.name, it.timePreset, it.personId.orEmpty(), it.completion.name, it.showFilters.toString()) },
            restore = { CalendarFilterState(CalendarDay(it[0].toInt(), it[1].toInt(), it[2].toInt())).apply {
                view = CalendarView.valueOf(it[3]); timePreset = it[4]; personId = it[5].ifEmpty { null }; completion = CompletionFilter.valueOf(it[6]); showFilters = it[7].toBoolean()
            } },
        )
    }
}
