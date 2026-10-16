package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class BirthdayFieldUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun yearMonthDayPreserveAncientDatesClampLeapDaysAndSupportClearAndCancellation() {
        var date by mutableStateOf("1920-02-29")
        compose.setContent {Arachn0deTheme {BirthdayField(date,{date=it})}}
        compose.onNodeWithTag("birthday-year").performClick()
        compose.onNodeWithTag("birthday-years").performScrollToNode(hasTestTag("birthday-year:1900"))
        compose.onNodeWithTag("birthday-year:1900").performClick()
        assertEquals("1900-02-28",date)
        compose.onNodeWithTag("birthday-year").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals("1900-02-28",date)
        compose.onNodeWithTag("birthday-year").performClick()
        compose.onNodeWithTag("birthday-years").performScrollToNode(hasTestTag("birthday-year:1980"))
        compose.onNodeWithTag("birthday-year:1980").performClick()
        compose.onNodeWithTag("birthday-day").performClick()
        compose.onNodeWithTag("birthday-day:29").performScrollTo().performClick()
        assertEquals("1980-02-29",date)
        compose.onNodeWithText("Quitar fecha").performClick()
        assertEquals("",date)
        compose.onNodeWithTag("birthday-year").assertTextContains("Año")
    }
    @Test fun selectingTheCurrentYearClampsAFutureMonthAndDayToToday() {
        val today=java.time.LocalDate.now()
        var date by mutableStateOf("1980-12-31")
        compose.setContent {Arachn0deTheme {BirthdayField(date,{date=it})}}
        compose.onNodeWithTag("birthday-year").performClick()
        compose.onNodeWithTag("birthday-years").performScrollToNode(hasTestTag("birthday-year:${today.year}"))
        compose.onNodeWithTag("birthday-year:${today.year}").performClick()
        assertEquals(today.toString(),date)
        compose.onNodeWithTag("birthday-month").performClick()
        if(today.monthValue<12) compose.onNodeWithTag("birthday-month:${today.monthValue+1}").assertDoesNotExist()
    }
}
