package com.r0ybt.arachn0de.calendar

import android.app.Application
import com.r0ybt.arachn0de.domain.model.CalendarDay
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class)
class CalendarSpecialDatesTest {
    @Test fun packagedCatalogHasVerifiedFixedMobileAndMultipleCategoriesWithoutExtrapolation() {
        val catalog=CalendarSpecialDates.load(RuntimeEnvironment.getApplication())
        val all=CalendarSpecialVisibility(true,true,true)
        assertEquals(setOf(2026),catalog.holidayYears)
        assertEquals(16,catalog.entries.count {it.kind==SpecialDateKind.HOLIDAY})
        assertEquals(15,catalog.saintDays);assertEquals(10,catalog.commemorations)
        assertTrue(catalog.on(CalendarDay(2026,5,1),all).any {it.name=="Día Nacional del Trabajo"})
        assertTrue(catalog.on(CalendarDay(2026,4,3),all).any {it.name=="Viernes Santo"})
        assertTrue(catalog.on(CalendarDay(2026,4,4),all).any {it.name=="Sábado Santo"})
        assertTrue(catalog.on(CalendarDay(2026,6,21),all).any {it.name=="Día Nacional de los Pueblos Indígenas"})
        assertTrue(catalog.on(CalendarDay(2026,2,3),all).isEmpty())
        assertEquals(setOf(SpecialDateKind.HOLIDAY,SpecialDateKind.SAINT),catalog.on(CalendarDay(2026,7,16),all).map {it.kind}.toSet())
        assertEquals(2,catalog.on(CalendarDay(2026,10,24),all).count {it.kind==SpecialDateKind.OBSERVANCE})
        assertTrue(catalog.on(CalendarDay(2027,1,1),all).none {it.kind==SpecialDateKind.HOLIDAY})
        assertTrue(catalog.on(CalendarDay(2028,2,29),all).isEmpty())
        assertTrue(catalog.on(CalendarDay(2026,7,16),CalendarSpecialVisibility(false,false,false)).isEmpty())
        assertEquals(16,catalog.entries.count {it.kind==SpecialDateKind.HOLIDAY})
    }
    @Test fun verifiedTransferRulesCoverMondayAndEvangelicalFridayCasesWithoutExtendingCoverage() {
        assertEquals(CalendarDay(2027,6,28),CalendarSpecialDates.transferred(CalendarDay(2027,6,29),"monday"))
        assertEquals(CalendarDay(2026,6,29),CalendarSpecialDates.transferred(CalendarDay(2026,6,29),"monday"))
        assertEquals(CalendarDay(2029,10,15),CalendarSpecialDates.transferred(CalendarDay(2029,10,12),"monday"))
        assertEquals(CalendarDay(2025,10,12),CalendarSpecialDates.transferred(CalendarDay(2025,10,12),"monday"))
        assertEquals(CalendarDay(2028,10,27),CalendarSpecialDates.transferred(CalendarDay(2028,10,31),"evangelical"))
        assertEquals(CalendarDay(2029,11,2),CalendarSpecialDates.transferred(CalendarDay(2029,10,31),"evangelical"))
        assertEquals(CalendarDay(2026,10,31),CalendarSpecialDates.transferred(CalendarDay(2026,10,31),"evangelical"))
    }
}
