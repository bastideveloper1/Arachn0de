package com.r0ybt.arachn0de.metro

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test
import com.r0ybt.arachn0de.metro.MetroOperatingHours.Warning.*

class MetroOperatingHoursTest {
    private fun wall(time:String,date:String="2026-10-09")=LocalDateTime.parse("${date}T$time").atZone(ZoneId.of("America/Santiago")).toInstant().toEpochMilli()
    private fun warnings(time:String,duration:Int=10,date:String="2026-10-09")=MetroOperatingHours.warnings(wall(time,date),duration)
    @Test fun normalTripsAndExactOpeningHaveNoNotice() {
        assertTrue(warnings("12:00").isEmpty());assertTrue(warnings("06:00").isEmpty())
        assertTrue(warnings("22:29",20).isEmpty())
    }
    @Test fun closingMarginAndArrivalAreDistinctWithoutDuplicates() {
        assertEquals(listOf(NEAR_CLOSING),warnings("22:30",30))
        assertEquals(listOf(NEAR_CLOSING,MAY_FINISH_LATE),warnings("22:30",31))
        assertEquals(listOf(MAY_FINISH_LATE),warnings("22:00",61))
        assertEquals(listOf(NEAR_CLOSING),warnings("23:00",0))
        assertEquals(listOf(OUTSIDE),warnings("23:00:01"))
        assertEquals(listOf(OUTSIDE),warnings("23:30",Int.MAX_VALUE))
        assertEquals(listOf(OUTSIDE),warnings("00:00"))
    }
    @Test fun weekdaysSaturdaySundayAndVerifiedHolidaysHaveDifferentOpenings() {
        assertEquals(listOf(OUTSIDE),warnings("05:59"))
        assertEquals(listOf(OUTSIDE),warnings("06:29",date="2026-10-10"))
        assertTrue(warnings("06:30",date="2026-10-10").isEmpty())
        assertEquals(listOf(OUTSIDE),warnings("07:29",date="2026-10-11"))
        assertTrue(warnings("07:30",date="2026-10-11").isEmpty())
        for(date in listOf("2026-10-12","2026-04-04","2026-12-25")) {
            assertEquals(listOf(OUTSIDE),warnings("07:29",date=date))
            assertTrue(warnings("07:30",date=date).isEmpty())
        }
        assertEquals(listOf(UNVERIFIED_HOLIDAY),warnings("06:30",date="2027-01-01"))
        assertTrue(warnings("12:00",date="2027-01-01").isEmpty())
    }
    @Test fun santiagoCalendarIgnoresDeviceZoneAndUsesSeasonalOffsetsAndLocalDate() {
        val original=TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(listOf(NEAR_CLOSING),MetroOperatingHours.warnings(Instant.parse("2026-10-10T01:30:00Z").toEpochMilli(),10))
            assertEquals(listOf(NEAR_CLOSING),MetroOperatingHours.warnings(Instant.parse("2026-07-11T02:30:00Z").toEpochMilli(),10))
            assertTrue(warnings("12:00").isEmpty())
        } finally {TimeZone.setDefault(original)}
    }
}
