package com.r0ybt.arachn0de.ui

import android.app.Application
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

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class OptionalBirthdayUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun removingYearPreservesLeapDayAndClearLeavesNoBirthday() {
        var value by mutableStateOf("2000-02-29")
        compose.setContent {Arachn0deTheme {BirthdayField(value,{value=it})}}
        compose.onNodeWithTag("birthday-year").performClick()
        compose.onNodeWithTag("birthday-no-year").performClick()
        compose.runOnIdle {assertEquals("--02-29",value)}
        compose.onNodeWithTag("birthday-day").assertTextContains("29")
        compose.onNodeWithTag("birthday-year").performClick()
        compose.onNodeWithTag("birthday-years").performScrollToNode(hasTestTag("birthday-year:2000"))
        compose.onNodeWithTag("birthday-year:2000").performClick()
        compose.runOnIdle {assertEquals("2000-02-29",value)}
        compose.onNodeWithText("Quitar fecha").performClick()
        compose.runOnIdle {assertEquals("",value)}
        compose.onNodeWithTag("birthday-month").performClick()
        compose.onNodeWithTag("birthday-month:2").performClick()
        compose.onNodeWithTag("birthday-day").performClick()
        compose.onNodeWithTag("birthday-day:29").performScrollTo().performClick()
        compose.runOnIdle {assertEquals("--02-29",value)}
    }
}
