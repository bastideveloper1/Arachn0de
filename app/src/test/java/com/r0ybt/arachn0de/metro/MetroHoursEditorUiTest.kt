package com.r0ybt.arachn0de.metro

import android.app.TimePickerDialog
import android.content.DialogInterface
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28])
class MetroHoursEditorUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {app.database.close()}
    @Test fun hourControlsSaveImmediatelyAndResetOfficialDefaults() {
        val prefs=app.privateContext.getSharedPreferences(MetroHoursConfig.NAME,0)
        val selected=mutableStateOf(MetroHoursConfig.read(prefs))
        compose.setContent {Arachn0deTheme {MetroHoursEditor(selected.value,onDismiss={},onSave={value->
            prefs.edit().putString("hours",value.encode()).commit().also {if(it) selected.value=value}
        })}}
        compose.onNodeWithText("Valores oficiales de referencia").assertExists()
        compose.onNodeWithText("Apertura: 06:00").performClick()
        compose.runOnIdle {
            val picker=org.robolectric.shadows.ShadowDialog.getLatestDialog() as TimePickerDialog
            picker.updateTime(7,0);picker.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        compose.onNodeWithText("Apertura: 07:00").assertExists()
        compose.onAllNodesWithText("Cierre: 23:00")[0].performClick()
        compose.runOnIdle {
            val picker=org.robolectric.shadows.ShadowDialog.getLatestDialog() as TimePickerDialog
            picker.updateTime(22,0);picker.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        compose.onNodeWithText("Valores personalizados").assertExists()
        compose.onNodeWithText("Guardar").performClick()
        assertEquals(MetroHoursPeriod(420,1320),MetroHoursConfig.read(prefs).weekday)
        compose.onNodeWithText("Restablecer horarios predeterminados").performScrollTo().performClick()
        compose.onNodeWithText("Guardar").performClick()
        assertEquals(MetroHoursConfig(),MetroHoursConfig.read(prefs))
    }
}
