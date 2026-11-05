package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.DescriptionParser
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class FunctionalBlockOneUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun progressFollowsOperationStateWithoutAConfirmation() {
        var busy by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {OperationProgress(busy)}}
        compose.onNodeWithTag("operation-progress").assertDoesNotExist()
        compose.runOnIdle {busy=true}
        compose.onNodeWithTag("operation-progress").assertExists()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("operation-progress").assertExists()
        compose.runOnIdle {busy=false}
        compose.onNodeWithTag("operation-progress").assertDoesNotExist()
    }

    @Test fun longCodeDescriptionKeepsFieldBoundedAndActionsVisibleWhenCaretMoves() {
        val marker=DescriptionParser.Format.Code.marker
        val raw=marker+"  línea de código\n".repeat(100)+marker
        var value by mutableStateOf(TextFieldValue(raw,TextRange(raw.length)))
        compose.setContent {Arachn0deTheme {
            Column(Modifier.width(300.dp).background(Color.Magenta).testTag("test-root")) {
                AttachmentDescriptionField(value,{value=it},true,maxLines=4,
                    transformation=AttachmentVisualTransformation(emptyMap()),onReference={})
                Text("Herramientas",Modifier.testTag("test-toolbar"))
                Text("Cancelar · Confirmar",Modifier.testTag("test-actions"))
            }
        }}
        for(offset in listOf(raw.length,marker.length,raw.length/2)) {
            compose.runOnIdle {value=value.copy(selection=TextRange(offset))}
            compose.onNodeWithTag("test-toolbar").assertIsDisplayed()
            compose.onNodeWithTag("test-actions").assertIsDisplayed()
            val field=compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
            val toolbar=compose.onNodeWithTag("test-toolbar").fetchSemanticsNode().boundsInRoot
            assertTrue(field.bottom<=toolbar.top)
            val root=compose.onNodeWithTag("test-root")
            val bounds=root.fetchSemanticsNode().boundsInRoot
            val view=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            try {
                compose.runOnUiThread {view.draw(android.graphics.Canvas(bitmap))}
                assertEquals("The code background must not paint over the toolbar",android.graphics.Color.MAGENTA,
                    bitmap.getPixel((bounds.right-2).toInt(),toolbar.center.y.toInt()))
            } finally {bitmap.recycle()}
        }
    }
}
