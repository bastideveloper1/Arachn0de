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
    @Test fun calendarPreservesAncientIsoDateAndSupportsClearAndCancellation() {
        var date by mutableStateOf("1920-02-29")
        compose.setContent {Arachn0deTheme {BirthdayField(date,{date=it})}}
        compose.onNodeWithText("Elegir fecha de nacimiento").performClick()
        compose.onNodeWithText("Seleccionar").performClick();assertEquals("1920-02-29",date)
        compose.onNodeWithText("Elegir fecha de nacimiento").performClick()
        compose.onNodeWithText("Cancelar").performClick();assertEquals("1920-02-29",date)
        compose.onNodeWithText("Elegir fecha de nacimiento").performClick()
        compose.onNodeWithText("Quitar fecha").performClick();assertEquals("",date)
    }
}
